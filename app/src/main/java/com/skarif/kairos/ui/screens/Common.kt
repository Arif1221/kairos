package com.skarif.kairos.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.ArtworkRepository
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.formatTime
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T

/** Album art for a song, loaded off the main thread and cached. */
@Composable
fun SongArt(vm: MainViewModel, song: SongEntity?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 16.dp, large: Boolean = false) {
    val c = P.colors
    val bmp by produceState<Bitmap?>(null, song?.id, large) {
        value = song?.let { if (large) vm.cover(it) else vm.thumbnail(it) }
    }
    val img = bmp
    if (img != null) {
        Image(img.asImageBitmap(), null, modifier.size(size).clip(RoundedCornerShape(corner)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(c.tint), contentAlignment = Alignment.Center) {
            Icon(Ic.Music, null, tint = c.muted, modifier = Modifier.size(size / 2.6f))
        }
    }
}

/** Accent colour taken from a song's art, for tinting cards (Home mixes, Search tiles). */
@Composable
fun artColor(vm: MainViewModel, song: SongEntity?): Color {
    val c = P.colors
    val argb by produceState<Int?>(null, song?.id) {
        value = song?.let { s -> vm.thumbnail(s)?.let { ArtworkRepository.paletteAccent(it) } }
    }
    return argb?.let { Color(ArtworkRepository.tuneAccent(it, c.dark)) } ?: c.accent
}

fun tintOf(card: Color, accent: Color, dark: Boolean): Color = lerp(card, accent, if (dark) 0.28f else 0.22f)

/** Like [rememberCover], but a cached cover shows on the very first frame, with no flash of the old one. */
@Composable
fun rememberCoverNow(vm: MainViewModel, song: SongEntity?): ImageBitmap? {
    val loaded by produceState<Pair<Long, Bitmap?>?>(null, song?.id) { value = song?.let { it.id to vm.cover(it) } }
    val bmp = song?.let { vm.cachedCover(it) ?: loaded?.takeIf { l -> l.first == it.id }?.second }
    return androidx.compose.runtime.remember(bmp) { bmp?.asImageBitmap() }
}

/** Large cover image used by heroes. */
@Composable
fun rememberCover(vm: MainViewModel, song: SongEntity?): ImageBitmap? {
    val bmp by produceState<Bitmap?>(null, song?.id) { value = song?.let { vm.cover(it) } }
    return bmp?.asImageBitmap()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongEntity,
    playing: Boolean,
    vm: MainViewModel,
    number: Int? = null,
    onLongClick: (() -> Unit)? = null,
    swipe: Boolean = true,
    onClick: () -> Unit,
) {
    if (swipe) {
        val favs by vm.favorites.collectAsStateWithLifecycle()
        val fav = song.id in favs
        SwipeActions(
            "Play next", Ic.Queue, { vm.playNext(listOf(song)) },
            if (fav) "Unfavourite" else "Favourite", if (fav) Ic.Heart else Ic.HeartFilled, { vm.toggleFavorite(song.id) },
        ) { SongRowContent(song, playing, vm, number, onLongClick, onClick) }
    } else {
        SongRowContent(song, playing, vm, number, onLongClick, onClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRowContent(song: SongEntity, playing: Boolean, vm: MainViewModel, number: Int?, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val c = P.colors
    val fg = if (playing) c.onAccent else c.text
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (playing) c.accent else Color.Transparent)
            .combinedClickable(
                role = Role.Button, onClickLabel = "Play", onClick = onClick,
                onLongClickLabel = "More options", onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (number != null) {
            Text("$number", Modifier.padding(start = 6.dp), style = T.small.copy(fontSize = 14.sp), color = fg)
        } else {
            SongArt(vm, song, 52.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = T.title, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = T.small, color = if (playing) fg.copy(alpha = 0.8f) else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), style = T.title.copy(fontSize = 15.sp), color = fg)
    }
}

/**
 * Long-press menu: play next, add to a playlist, favourite. With [queueIndex] it acts on that
 * queue entry instead: play it now, move it up next, or remove it.
 */
@Composable
fun SongActionsDialog(vm: MainViewModel, song: SongEntity, onOpenAlbum: (Long) -> Unit, queueIndex: Int? = null, onDismiss: () -> Unit) {
    val c = P.colors
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text(song.title, style = T.h2, color = c.text, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (queueIndex != null) {
                    DialogItem("Play now") { vm.playQueueItem(queueIndex); onDismiss() }
                    // Reordering follows the unshuffled order, so it is only offered with shuffle off.
                    if (!vm.now.value.shuffle) {
                        DialogItem("Play next") { vm.moveToNext(queueIndex); onDismiss() }
                        DialogItem("Move to end of queue") { vm.moveToEnd(queueIndex); onDismiss() }
                    }
                    DialogItem("Remove from queue") { vm.removeQueueItem(queueIndex); onDismiss() }
                } else {
                    DialogItem("Play next") { vm.playNext(listOf(song)); onDismiss() }
                }
                DialogItem(if (song.id in favs) "Remove from favourites" else "Add to favourites") { vm.toggleFavorite(song.id); onDismiss() }
                DialogItem("Go to album") { onOpenAlbum(song.albumId); onDismiss() }
                if (playlists.isNotEmpty()) Text("Add to playlist", Modifier.padding(top = 10.dp, bottom = 4.dp), style = T.small, color = c.muted)
                playlists.forEach { p -> DialogItem(p.name) { vm.addToPlaylist(p.id, listOf(song.id)); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.text) } },
    )
}

@Composable
private fun DialogItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.fillMaxWidth(), style = T.title, color = P.colors.text)
    }
}

/** Seek bar that reads the playback position itself, so only it redraws 5 times a second. */
@Composable
fun LiveWaveformSeek(vm: MainViewModel, durationMs: Long, seed: Long, modifier: Modifier = Modifier, barsHeight: Dp = 66.dp, bars: Int = 26) {
    val pos by vm.position.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    com.skarif.kairos.ui.components.WaveformSeek(pos, durationMs, seed, vm::seekTo, modifier, barsHeight, bars, playing = now.isPlaying)
}

@Composable
fun LiveTime(vm: MainViewModel, durationMs: Long, color: Color) {
    val pos by vm.position.collectAsStateWithLifecycle()
    Text("${formatTime(pos)} / ${formatTime(durationMs)}", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
}

/**
 * Swipe a row right or left to run an action; the row springs back afterwards.
 * Also exposed as accessibility actions, since a swipe can't be done with TalkBack.
 */
@Composable
fun SwipeActions(
    rightLabel: String?,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onRight: (() -> Unit)?,
    leftLabel: String?,
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onLeft: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val c = P.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> onRight?.invoke()
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> onLeft?.invoke()
                else -> Unit
            }
            false // never stay dismissed; the action ran, the row returns
        },
        // Short swipe: a fifth of the row, at most 72 dp, is enough to act.
        positionalThreshold = { total -> minOf(total * 0.2f, 72f * density.density) },
    )
    // A tick under the finger the moment the swipe passes the point where it will act.
    androidx.compose.runtime.LaunchedEffect(state.targetValue) {
        if (state.targetValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = onRight != null,
        enableDismissFromEndToStart = onLeft != null,
        modifier = Modifier.semantics {
            customActions = listOfNotNull(
                if (onRight != null && rightLabel != null) androidx.compose.ui.semantics.CustomAccessibilityAction(rightLabel) { onRight(); true } else null,
                if (onLeft != null && leftLabel != null) androidx.compose.ui.semantics.CustomAccessibilityAction(leftLabel) { onLeft(); true } else null,
            )
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val right = dir == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
            val active = dir != androidx.compose.material3.SwipeToDismissBoxValue.Settled
            // Colour only the strip the row has uncovered, so nothing shows through the row itself.
            val offsetPx = runCatching { state.requireOffset() }.getOrDefault(0f)
            val strip = with(androidx.compose.ui.platform.LocalDensity.current) { kotlin.math.abs(offsetPx).toDp() }
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp)) {
                if (active && strip > 0.dp) {
                    val icon = if (right) rightIcon else leftIcon
                    val fg = if (right) c.onAccent else c.inkFg
                    Box(
                        Modifier.align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                            .width(strip).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                            .background(if (right) c.accent else c.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The icon grows as you pull, and pops once the action is armed.
                        val armed = state.targetValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled
                        val iconScale by androidx.compose.animation.core.animateFloatAsState(
                            if (armed) 1.25f else (0.6f + state.progress * 0.5f).coerceAtMost(1f),
                            androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "swipeIcon",
                        )
                        if (icon != null && strip > 36.dp) {
                            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        }
                    }
                }
            }
        },
    ) { content() }
}
