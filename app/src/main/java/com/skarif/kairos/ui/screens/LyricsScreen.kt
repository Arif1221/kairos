package com.skarif.kairos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.lyrics.Lyrics
import com.skarif.kairos.ui.LyricsState
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlin.math.abs

// Follow the theme: light glass and dark text in the light theme, the reverse in the dark one.
private val Glass: Color @Composable get() = if (P.colors.dark) Color(0x33FFFFFF) else Color(0x1F000000)
private val Dim: Color @Composable get() = P.colors.muted
private val Fg: Color @Composable get() = P.colors.text

/**
 * Immersive lyrics: the cover blurred behind a dark veil, white text, and a karaoke fill that
 * sweeps across the current line. Timing controls hide behind the options button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsPane(vm: MainViewModel, onBack: () -> Unit, onQueue: () -> Unit, onCollapse: () -> Unit) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val state by vm.lyrics.collectAsStateWithLifecycle()
    val offset by vm.lyricsOffset.collectAsStateWithLifecycle()
    // Keep the screen awake while reading along, if that setting is on.
    val keepOn = vm.settings.collectAsStateWithLifecycle().value.keepScreenOnLyrics
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    var options by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircleButton(Ic.Down, "Back to now playing", onBack, size = 44.dp, container = Glass, content = Fg)
                // The player layer draws the cover here (it shrinks from the big hero into this spot).
                Spacer(Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "", style = T.title.copy(fontSize = 16.sp), color = Fg, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
                    )
                    Text(song?.artist ?: "", style = T.small.copy(fontSize = 12.sp), color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val synced = (state as? LyricsState.Ready)?.lyrics?.synced == true
                if (synced) {
                    CircleButton(
                        if (options) Ic.Close else Ic.Sliders, if (options) "Hide timing" else "Lyrics timing", { options = !options },
                        size = 44.dp, container = if (options) c.ink else Glass, content = if (options) c.inkFg else Fg,
                    )
                }
            }

            AnimatedVisibility(options, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Glass)
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val source = (state as? LyricsState.Ready)?.lyrics?.let { if (it.source == Lyrics.SOURCE_EMBEDDED) "Embedded" else ".lrc file" }
                    Text(
                        (source?.let { "$it, " } ?: "") + if (offset == 0L) "in sync" else (if (offset > 0) "Later by " else "Earlier by ") + "%.1f s".format(abs(offset) / 1000f),
                        Modifier.weight(1f), style = T.title.copy(fontSize = 14.sp), color = Fg,
                    )
                    SmallPill("−0.1 s", "Show lyrics earlier") { vm.nudgeLyrics(-100) }
                    SmallPill("+0.1 s", "Show lyrics later") { vm.nudgeLyrics(100) }
                    if (offset != 0L) SmallPill("Reset", "Reset lyrics timing") { vm.nudgeLyrics(-offset) }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    // Lines fade out at the top and bottom edges instead of being cut off.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent, 0.12f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                when (val st = state) {
                    LyricsState.Loading -> Message("Looking for lyrics…")
                    LyricsState.None -> Message(
                        "No lyrics for this song yet. Add them to the file's tags, or put a .lrc file with the " +
                            "same name next to the song and allow that folder in Settings.",
                    )
                    is LyricsState.Ready -> LyricsList(vm, st.lyrics, offset, c.accent) { t -> vm.seekTo(t) }
                }
            }

            // Compact transport.
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val s = song
                if (s != null) LiveWaveformSeek(vm, now.durationMs.takeIf { it > 0 } ?: s.durationMs, s.id, barsHeight = 28.dp, bars = 32)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Ic.Queue, "Queue", onQueue, size = 46.dp, container = Glass, content = Fg)
                    Box(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(Ic.Prev, "Previous song", vm::previous, size = 46.dp, container = Glass, content = Fg)
                        CircleButton(
                            if (now.isPlaying) Ic.Pause else Ic.Play, if (now.isPlaying) "Pause" else "Play", vm::toggle,
                            size = 56.dp, container = c.ink, content = c.inkFg,
                        )
                        CircleButton(Ic.Next, "Next song", vm::next, size = 46.dp, container = Glass, content = Fg)
                    }
                }
            }
        }
    }
}

/** Synced: centres the current line and fills it as it is sung; tap a line to jump there. */
@Composable
private fun LyricsList(vm: MainViewModel, lyrics: Lyrics, offsetMs: Long, accent: Color, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    val position = rememberLivePosition(vm)
    // Positive offset means the lyrics should show later, so compare against an earlier position.
    val current by remember(lyrics, offsetMs) {
        derivedStateOf { if (lyrics.synced) lyrics.indexAt(position.value - offsetMs) else -1 }
    }
    var viewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(current) {
        if (current < 0) return@LaunchedEffect
        // Glide to the new line with one eased motion when it is on screen; jump only when it isn't.
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current + if (lyrics.synced) 0 else 1 }
        if (info != null) list.animateScrollBy((info.offset - viewport / 3).toFloat(), tween(600, easing = FastOutSlowInEasing))
        else list.animateScrollToItem(current, scrollOffset = -(viewport / 3))
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it.height },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 220.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!lyrics.synced) {
            item { Text("These lyrics aren't time-synced.", style = T.small, color = Dim) }
        }
        itemsIndexed(lyrics.lines) { i, line ->
            val text = line.text.ifEmpty { "♪" }
            if (!lyrics.synced) {
                Text(text, Modifier.fillMaxWidth(), style = T.body.copy(fontSize = 21.sp, lineHeight = 30.sp), color = Fg)
                return@itemsIndexed
            }
            val isCur = i == current
            val alpha by animateFloatAsState(when { isCur -> 1f; i < current -> 0.35f; else -> 0.55f }, tween(500, easing = FastOutSlowInEasing), label = "lineAlpha")
            val scale by animateFloatAsState(if (isCur) 1f else 0.78f, tween(500, easing = FastOutSlowInEasing), label = "lineScale")
            var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
            val start = line.timeMs + offsetMs
            val end = lyrics.lines.getOrNull(i + 1)?.timeMs?.plus(offsetMs) ?: (start + 4000)
            Text(
                text,
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        this.alpha = alpha
                        if (isCur) compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .then(if (isCur) Modifier.karaoke(position, start, end) { layout } else Modifier)
                    .clickable(role = Role.Button, onClickLabel = "Play from this line") { onSeek(start) }
                    .then(if (isCur) Modifier.semantics { heading() } else Modifier),
                // One weight for every line: switching weight re-wraps the text and makes it jump.
                style = T.h1.copy(fontSize = 35.sp, lineHeight = 41.sp, fontWeight = FontWeight.ExtraBold),
                color = Fg,
                onTextLayout = { layout = it },
            )
        }
    }
}

/**
 * Sung part stays white, the rest is dimmed. Position is read in the draw phase, so the sweep
 * redraws every tick without recomposing the list.
 */
private fun Modifier.karaoke(
    position: State<Long>, startMs: Long, endMs: Long, layout: () -> androidx.compose.ui.text.TextLayoutResult?,
): Modifier = drawWithContent {
    drawContent()
    val l = layout() ?: return@drawWithContent
    // Fill the wrapped lines one after another, start to finish, like reading.
    val span = (endMs - startMs).coerceAtLeast(1).toFloat()
    val p = ((position.value - startMs) / span).coerceIn(0f, 1f)
    val widths = FloatArray(l.lineCount) { l.getLineRight(it) - l.getLineLeft(it) }
    var fill = widths.sum() * p
    val feather = 36.dp.toPx()
    for (i in 0 until l.lineCount) {
        val left = l.getLineLeft(i)
        val right = l.getLineRight(i)
        val top = l.getLineTop(i)
        val h = l.getLineBottom(i) - top
        val covered = fill.coerceIn(0f, widths[i])
        fill -= widths[i]
        if (covered >= widths[i]) continue
        val x = left + covered
        if (covered > 0f) {
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = x - feather, endX = x),
                topLeft = Offset(x - feather, top), size = Size(feather, h), blendMode = BlendMode.DstOut, alpha = 0.6f,
            )
        }
        drawRect(Color.Black, topLeft = Offset(x, top), size = Size(size.width - x, h), blendMode = BlendMode.DstOut, alpha = 0.6f)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(24.dp), style = T.body, color = Dim)
}

@Composable
private fun SmallPill(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Glass)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.small.copy(fontWeight = FontWeight.Bold), color = Fg)
    }
}
