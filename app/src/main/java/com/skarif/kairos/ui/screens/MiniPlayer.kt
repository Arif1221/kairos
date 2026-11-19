package com.skarif.kairos.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.cardClick
import com.skarif.kairos.ui.components.sharedCard
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The current song above the dock: tap to open Now Playing (it grows into it), swipe sideways
 * to skip. A thin accent line along the bottom shows progress.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    // Read inside drawBehind, so position ticks redraw the line without recomposing the bar.
    val pos = vm.position.collectAsStateWithLifecycle()
    val s = song ?: return
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    // Settle on every song change, whatever happened to the skip animation.
    androidx.compose.runtime.LaunchedEffect(s.id) { dragX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f)) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .sharedCard("art-current", shape)
            .graphicsLayer {
                translationX = dragX.value
                alpha = 1f - (abs(dragX.value) / (size.width * 0.9f)).coerceIn(0f, 0.6f)
            }
            .clip(shape)
            .background(if (c.dark) Color(0xE6262626) else Color(0xEBFFFFFF))
            .drawBehind {
                // Progress line along the bottom edge.
                val p = pos.value.toFloat() / (now.durationMs.takeIf { it > 0 } ?: s.durationMs).coerceAtLeast(1)
                val y = size.height - 1.5.dp.toPx()
                drawLine(c.line, Offset(24.dp.toPx(), y), Offset(size.width - 24.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
                drawLine(
                    c.accent, Offset(24.dp.toPx(), y),
                    Offset(24.dp.toPx() + (size.width - 48.dp.toPx()) * p.coerceIn(0f, 1f), y), 3.dp.toPx(), StrokeCap.Round,
                )
            }
            .pointerInput(s.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val w = size.width
                        scope.launch {
                            when {
                                dragX.value < -w * 0.25f -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragX.animateTo(-w.toFloat(), tween(160)); vm.next(); dragX.snapTo(w * 0.5f)
                                }
                                dragX.value > w * 0.25f -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragX.animateTo(w.toFloat(), tween(160)); vm.previous(); dragX.snapTo(-w * 0.5f)
                                }
                            }
                            dragX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            }
            .cardClick("Open now playing", onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SongArt(vm, s, 48.dp, corner = 16.dp)
        Column(Modifier.weight(1f)) {
            Text(s.title, style = T.title, color = c.text, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000))
            Text(s.artist, style = T.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CircleButton(
            if (now.isPlaying) Ic.Pause else Ic.Play, if (now.isPlaying) "Pause" else "Play", vm::toggle,
            size = 48.dp, container = c.ink, content = c.inkFg,
        )
        CircleButton(Ic.Next, "Next song", vm::next, size = 44.dp, container = Color.Transparent)
    }
}
