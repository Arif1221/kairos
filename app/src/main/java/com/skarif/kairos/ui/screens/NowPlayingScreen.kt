package com.skarif.kairos.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.db.formatLabel
import kotlinx.coroutines.launch
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.components.sharedCard
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/**
 * The full player's main page. The cover itself is drawn by the player layer underneath, so it
 * can morph from the mini player; this page draws everything on top of it and handles the
 * cover's gestures: sideways to change song, down to close.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingPane(
    vm: MainViewModel,
    ctrl: com.skarif.kairos.ui.player.PlayerController,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit,
    onSleep: () -> Unit,
    onAlbum: (Long) -> Unit,
    onSkip: (dir: Int, velocityX: Float) -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val s = song ?: return
    // Must match the cover rectangle in PlayerSheet.
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.47f).coerceIn(300f, 440f).dp
    val scope = rememberCoroutineScope()
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    var axis by remember { mutableIntStateOf(0) } // 0 undecided, 1 sideways, 2 down
    val liked = s.id in favs

    Column(Modifier.fillMaxSize().verticalScroll(ctrl.mainScroll).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(heroHeight + 38.dp)) {
            Box(Modifier.fillMaxWidth().height(heroHeight).clip(RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(s.id, hasPrev, hasNext) {
                            detectDragGestures(
                                onDragStart = { tracker.resetTracking(); axis = 0 },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity()
                                    if (axis == 2) {
                                        ctrl.settle(v.y)
                                    } else {
                                        val x = ctrl.skipX.value
                                        val w = size.width
                                        val dir = when {
                                            x < -w * 0.18f || (v.x < -900f && x < 0f) -> -1
                                            x > w * 0.18f || (v.x > 900f && x > 0f) -> 1
                                            else -> 0
                                        }
                                        onSkip(dir, v.x)
                                    }
                                },
                                onDragCancel = {
                                    if (axis == 2) ctrl.settle(0f) else onSkip(0, 0f)
                                },
                            ) { change, amount ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                // Lock to one direction from the first movement: change song or close, never both.
                                if (axis == 0) axis = if (kotlin.math.abs(amount.x) >= kotlin.math.abs(amount.y)) 1 else 2
                                if (axis == 1) {
                                    // Resist at the ends of the queue.
                                    val nx = ctrl.skipX.value + amount.x
                                    val edge = (nx > 0 && !hasPrev) || (nx < 0 && !hasNext)
                                    scope.launch { ctrl.skipX.snapTo(if (edge) ctrl.skipX.value + amount.x * 0.3f else nx) }
                                } else {
                                    ctrl.dragBy(amount.y)
                                }
                            }
                        },
                )
                // Scrims keep the buttons and the title readable on any cover, light or dark.
                Box(
                    Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0x80000000), Color.Transparent))),
                )
                Box(
                    Modifier.fillMaxWidth().height(240.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000), Color(0xC7000000)))),
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircleButton(Ic.Down, "Close player", { ctrl.collapse() }, container = Color(0x59000000), content = Color.White)
                    Box(Modifier.weight(1f))
                    CircleButton(
                        Ic.Clock, if (sleep.active) "Sleep timer, on" else "Sleep timer", onSleep,
                        container = if (sleep.active) c.accent else Color(0x59000000), content = if (sleep.active) c.onAccent else Color.White,
                    )
                    CircleButton(Ic.Sliders, "Equalizer", onEqualizer, container = Color(0x59000000), content = Color.White)
                    CircleButton(Ic.Lyrics, "Lyrics", onLyrics, container = Color(0x59000000), content = Color.White)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 34.dp, end = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Ic.Music, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(if (s.year > 0) "${s.year}" else "Album", color = Color.White, style = T.small.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Text(
                        s.album, color = Color.White, style = T.big.copy(fontSize = 38.sp, lineHeight = 40.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open album") { onAlbum(s.albumId) },
                    )
                    Text(
                        "Open album", color = Color.White, style = T.small.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                        modifier = Modifier.clickable(role = Role.Button) { onAlbum(s.albumId) },
                    )
                }
            }
            CircleButton(
                if (liked) Ic.HeartFilled else Ic.Heart,
                if (liked) "Remove from favourites" else "Add to favourites",
                { vm.toggleFavorite(s.id) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp),
                size = 76.dp, container = Color(0xFF151515), content = Color.White, iconSize = 28.dp,
            )
        }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.artist.uppercase(), color = c.text, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = T.small.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                    )
                    Text(
                        s.title, color = c.text, style = T.h2.copy(fontSize = 30.sp, lineHeight = 34.sp), textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500),
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(Ic.Wave, now.output?.let { "${s.formatLabel()} $it" } ?: s.formatLabel())
                    InfoChip(Ic.Clock, formatTime(s.durationMs))
                    if (now.queueSize > 0) InfoChip(Ic.Queue, "${now.queuePosition + 1} of ${now.queueSize}", onQueue)
                }
                LiveWaveformSeek(vm, now.durationMs.takeIf { it > 0 } ?: s.durationMs, s.id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleButton(Ic.Prev, "Previous song", vm::previous, size = 64.dp, iconSize = 22.dp)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(c.ink)
                            .clickable(role = Role.Button, onClick = vm::toggle),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        AnimatedContent(
                            now.isPlaying,
                            transitionSpec = { (fadeIn(tween(160)) + scaleIn(tween(200), 0.7f)) togetherWith fadeOut(tween(120)) },
                            label = "playPause",
                        ) { playing ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (playing) Ic.Pause else Ic.Play, null, tint = c.inkFg, modifier = Modifier.size(20.dp))
                                Text(if (playing) "Pause" else "Play", color = c.inkFg, style = T.title.copy(fontSize = 17.sp))
                            }
                        }
                    }
                    CircleButton(Ic.Next, "Next song", vm::next, size = 64.dp, iconSize = 22.dp)
                }
                Row(Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleButton(Ic.Shuffle, if (now.shuffle) "Shuffle, on" else "Shuffle, off", vm::toggleShuffle,
                        container = if (now.shuffle) c.ink else c.card, content = if (now.shuffle) c.inkFg else c.text)
                    CircleButton(
                        if (now.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat,
                        "Repeat", vm::cycleRepeat,
                        container = if (now.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) c.ink else c.card,
                        content = if (now.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) c.inkFg else c.text,
                    )
                    CircleButton(Ic.Queue, "Queue", onQueue)
                }
            }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
    val c = P.colors
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(16.dp))
        Text(text, style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
    }
}
