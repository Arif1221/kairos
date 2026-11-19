package com.skarif.kairos.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
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
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlin.math.abs

@Composable
fun LyricsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val state by vm.lyrics.collectAsStateWithLifecycle()
    val offset by vm.lyricsOffset.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    ScreenBackground(art, settings.blurBackground) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircleButton(Ic.Down, "Back to now playing", onBack)
                Row(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.card).padding(start = 6.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val bmp = art
                    if (bmp != null) {
                        val img = remember(bmp) { bmp.asImageBitmap() }
                        Image(img, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent))
                    }
                    Column {
                        Text(song?.title ?: "", style = T.title.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song?.artist ?: "", style = T.small.copy(fontSize = 12.sp), color = c.muted, maxLines = 1)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val st = state) {
                    LyricsState.Loading -> Message("Looking for lyrics…")
                    LyricsState.None -> Message(
                        "No lyrics for this song yet. Add them to the file's tags, or put a .lrc file with the " +
                            "same name next to the song and allow that folder in Settings.",
                    )
                    is LyricsState.Ready -> LyricsList(vm, st.lyrics, offset) { t -> vm.seekTo(t) }
                }
            }

            Column(
                Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(c.card.copy(alpha = 0.72f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val s = song
                if (s != null) {
                    LiveWaveformSeek(vm, now.durationMs.takeIf { it > 0 } ?: s.durationMs, s.id, barsHeight = 34.dp, bars = 28)
                }
                val synced = (state as? LyricsState.Ready)?.lyrics?.synced == true
                if (synced) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Timing  " + if (offset == 0L) "in sync" else (if (offset > 0) "+" else "−") + "%.1f s".format(abs(offset) / 1000f),
                            Modifier.weight(1f), style = T.title.copy(fontSize = 14.sp), color = c.text,
                        )
                        SmallPill("−0.1 s", "Show lyrics earlier") { vm.nudgeLyrics(-100) }
                        SmallPill("+0.1 s", "Show lyrics later") { vm.nudgeLyrics(100) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val source = (state as? LyricsState.Ready)?.lyrics?.let {
                        (if (it.source == Lyrics.SOURCE_EMBEDDED) "Embedded" else ".lrc file") + if (it.synced) ", synced" else ", not synced"
                    }
                    Text(source ?: "", Modifier.weight(1f), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircleButton(Ic.Prev, "Previous song", vm::previous)
                        CircleButton(if (now.isPlaying) Ic.Pause else Ic.Play, if (now.isPlaying) "Pause" else "Play", vm::toggle, container = c.ink, content = c.inkFg)
                        CircleButton(Ic.Next, "Next song", vm::next)
                    }
                }
            }
        }
    }
}

/** Synced: highlights and centres the current line; tap a line to jump there. Plain: just text. */
@Composable
private fun LyricsList(vm: MainViewModel, lyrics: Lyrics, offsetMs: Long, onSeek: (Long) -> Unit) {
    val c = P.colors
    val list = rememberLazyListState()
    val position = vm.position.collectAsStateWithLifecycle()
    // Only the line index matters to the list; derivedStateOf stops 5-per-second recompositions.
    // Positive offset means the lyrics should show later, so compare against an earlier position.
    val current by remember(lyrics, offsetMs) {
        derivedStateOf { if (lyrics.synced) lyrics.indexAt(position.value - offsetMs) else -1 }
    }
    var viewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(current) {
        // Keep the current line about a third of the way down, so upcoming lines stay visible.
        if (current >= 0) list.animateScrollToItem(current, scrollOffset = -(viewport / 3))
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it.height },
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!lyrics.synced) {
            item { Text("These lyrics aren't time-synced.", style = T.small, color = c.muted) }
        }
        itemsIndexed(lyrics.lines) { i, line ->
            val isCur = i == current
            val text = line.text.ifEmpty { "♪" }
            if (!lyrics.synced) {
                Text(text, Modifier.fillMaxWidth(), style = T.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = c.text)
            } else {
                val alpha by animateFloatAsState(
                    when { isCur -> 1f; i < current -> 0.38f; else -> 0.62f }, tween(350), label = "lineAlpha",
                )
                val scale by animateFloatAsState(if (isCur) 1f else 0.86f, spring(dampingRatio = 0.75f, stiffness = 300f), label = "lineScale")
                Text(
                    text,
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            this.alpha = alpha
                        }
                        .clickable(role = Role.Button, onClickLabel = "Play from this line") { onSeek(line.timeMs + offsetMs) }
                        .then(if (isCur) Modifier.semantics { heading() } else Modifier),
                    style = T.h1.copy(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = if (isCur) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = c.text,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(22.dp), style = T.body, color = P.colors.muted)
}

@Composable
private fun SmallPill(text: String, description: String, onClick: () -> Unit) {
    val c = P.colors
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.small.copy(fontWeight = FontWeight.Bold), color = c.text)
    }
}
