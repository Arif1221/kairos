package com.skarif.kairos.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.StatsUi
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ShareCard
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One page of the story: what it says big, the small line above, and the cover it shows. */
private data class StoryPage(val kicker: String, val headline: String, val detail: String, val cover: com.skarif.kairos.data.db.SongEntity?)

/**
 * "Wrapped", any time: your listening for the range Stats is showing, as swipeable story cards.
 * Tap the right side for the next card, left for the previous; each card can be shared as an
 * image. Built from the numbers Stats already has: nothing new is tracked.
 */
@Composable
fun WrappedStory(vm: MainViewModel, st: StatsUi, rangeName: String, onDismiss: () -> Unit) {
    val c = P.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val top = st.top.firstOrNull()
    val pages = buildList {
        add(StoryPage("Your $rangeName", hoursText(st.totalMs), "${st.plays} plays, ${st.uniqueSongs} different songs", top?.song))
        if (top != null) add(StoryPage("Your top song", top.song.title, "${top.song.artist} · ${top.plays} plays", top.song))
        st.topArtists.firstOrNull()?.let { (name, ms) ->
            val cover = st.top.firstOrNull { s -> com.skarif.kairos.data.splitArtists(s.song.artist).any { it.equals(name, true) } }?.song
            add(StoryPage("Your top artist", name, "${hoursText(ms)} together", cover ?: top?.song))
        }
        if (st.peakHour.isNotBlank()) add(StoryPage("When you listen", "A ${st.peakHour} listener", "That's when you most often press play", st.top.getOrNull(1)?.song ?: top?.song))
        if (st.streakDays >= 2) add(StoryPage("Your streak", "${st.streakDays} days in a row", "Music every single day", st.top.getOrNull(2)?.song ?: top?.song))
        add(StoryPage("Your colours", "This is how your $rangeName looked", "Mixed from the covers you played most", top?.song))
    }

    // "Your colours": the average colour of each of your top five covers (a 1x1 scale of each).
    val palette by produceState(emptyList<Color>(), st.top) {
        value = withContext(Dispatchers.Default) {
            st.top.take(5).mapNotNull { t -> vm.thumbnail(t.song)?.let { Color(Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0)) } }
        }
    }
    val pager = rememberPagerState { pages.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onDismiss)
        val bgColors = if (palette.size >= 2) palette else listOf(c.accent, lerp(c.accent, Color.Black, 0.6f))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(bgColors.map { lerp(it, Color.Black, 0.35f) }))) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val page = pages[i]
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(i) {
                            detectTapGestures { o ->
                                scope.launch {
                                    val next = if (o.x > size.width / 2) i + 1 else i - 1
                                    if (next in pages.indices) pager.animateScrollToPage(next) else if (next >= pages.size) onDismiss()
                                }
                            }
                        }
                        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 72.dp),
                ) {
                    Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (page.cover != null && i != pages.lastIndex) {
                            SongArt(vm, page.cover, 200.dp, corner = 28.dp, large = true, modifier = Modifier.graphicsLayer { rotationZ = if (i % 2 == 0) -4f else 4f; shadowElevation = 24.dp.toPx(); shape = RoundedCornerShape(28.dp) })
                        }
                        if (i == pages.lastIndex && palette.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                palette.forEach { col -> Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(col)) }
                            }
                        }
                        Text(page.kicker.uppercase(), style = T.small.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp), color = Color.White.copy(alpha = 0.8f))
                        Text(page.headline, style = T.big.copy(fontSize = 44.sp, lineHeight = 48.sp), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(page.detail, style = T.body, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            // Story progress: one segment per card.
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pages.indices.forEach { k ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (k <= pager.currentPage) 0.95f else 0.3f)))
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircleButton(Ic.Close, "Close", onDismiss, container = Color(0x40000000), content = Color.White)
                CircleButton(Ic.Share, "Share this card", {
                    val page = pages[pager.currentPage]
                    scope.launch {
                        val cover = page.cover?.let { vm.cover(it) }
                        val bmp = withContext(Dispatchers.Default) {
                            ShareCard.render(ctx, cover, (palette.firstOrNull() ?: c.accent).toArgb(), page.kicker, page.headline, page.detail, "My Kairos $rangeName")
                        }
                        ShareCard.share(ctx, bmp, "kairos-$rangeName")
                    }
                }, container = Color.White, content = Color.Black)
            }
        }
    }
}
