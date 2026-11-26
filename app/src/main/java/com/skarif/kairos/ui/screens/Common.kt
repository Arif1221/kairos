package com.skarif.kairos.ui.screens

import androidx.compose.foundation.basicMarquee
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.data.ArtworkRepository
import com.skarif.kairos.data.db.SongEntity
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.pressScale
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
    // Straight from the memory cache when it's there, so cards coming back on screen don't pop in.
    return rememberCoverNow(vm, song)
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
    /** Pass from the list's screen so a long list doesn't collect favourites once per row. */
    favourite: Boolean? = null,
    onClick: () -> Unit,
) {
    if (swipe) {
        val fav = favourite ?: (song.id in vm.favorites.collectAsStateWithLifecycle().value)
        SwipeActions(
            "Play next", Ic.Queue, { vm.playNext(listOf(song)); vm.notify("${song.title} plays next") },
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
    // Same press as buttons and cards (down instantly, bounce back), a little smaller for a full-width row.
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item))
            .background(if (playing) c.accent else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction, indication = androidx.compose.material3.ripple(),
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
        Text(formatTime(song.durationMs), style = T.num, color = if (playing) fg else c.muted)
    }
}

/**
 * Long-press menu: play next, add to a playlist, favourite. With [queueIndex] it acts on that
 * queue entry instead: play it now, move it up next, or remove it.
 */
@Composable
fun SongActionsDialog(
    vm: MainViewModel, song: SongEntity, onOpenAlbum: (Long) -> Unit, queueIndex: Int? = null,
    /** Set when opened from a playlist: (playlist id, position of this song in it). */
    inPlaylist: Pair<Long, Int>? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    /** Extra items shown first (the player adds radio, share, speed and song info). */
    extra: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = P.colors
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hiddenIds.collectAsStateWithLifecycle()
    val snoozed by vm.snoozedIds.collectAsStateWithLifecycle()
    val excluded by vm.tasteExcluded.collectAsStateWithLifecycle()
    com.skarif.kairos.ui.components.KairosDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text(song.title, style = T.h2, color = c.text, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                extra?.invoke(onDismiss)
                if (inPlaylist != null) {
                    val (pid, pos) = inPlaylist
                    val size = playlists.firstOrNull { it.id == pid }?.songs?.size ?: 0
                    DialogItem("Remove from this playlist") { vm.removeFromPlaylist(pid, pos); onDismiss() }
                    if (pos > 0) DialogItem("Move up") { vm.moveInPlaylist(pid, pos, pos - 1); onDismiss() }
                    if (pos < size - 1) DialogItem("Move down") { vm.moveInPlaylist(pid, pos, pos + 1); onDismiss() }
                }
                if (queueIndex != null) {
                    DialogItem("Play now") { vm.playQueueItem(queueIndex); onDismiss() }
                    // Reordering follows the unshuffled order, so it is only offered with shuffle off.
                    if (!vm.now.value.shuffle) {
                        DialogItem("Play next") { vm.moveToNext(queueIndex); onDismiss() }
                        DialogItem("Move to end of queue") { vm.moveToEnd(queueIndex); onDismiss() }
                    }
                    DialogItem("Remove from queue") { vm.removeQueueItem(queueIndex); onDismiss() }
                } else {
                    DialogItem("Play next") { vm.playNext(listOf(song)); vm.notify("Plays next"); onDismiss() }
                }
                DialogItem(if (song.id in favs) "Remove from favourites" else "Add to favourites") { vm.toggleFavorite(song.id); onDismiss() }
                DialogItem("Go to album") { onOpenAlbum(song.albumId); onDismiss() }
                if (onOpenArtist != null) com.skarif.kairos.data.splitArtists(song.artist).forEach { a ->
                    DialogItem("Go to $a") { onOpenArtist(a); onDismiss() }
                }
                // Like Spotify's hide and snooze: keeps the song out of mixes, shuffle-all and autoplay.
                if (song.id in hidden) DialogItem("Show in mixes again") { vm.setHidden(song.id, false); onDismiss() }
                else DialogItem("Hide from mixes and autoplay") { vm.setHidden(song.id, true); onDismiss() }
                if (song.id in snoozed) DialogItem("Stop snoozing") { vm.unsnooze(song.id); onDismiss() }
                else if (song.id !in hidden) DialogItem("Snooze for 30 days") { vm.snooze(song.id); onDismiss() }
                if (song.id in excluded) DialogItem("Use for recommendations again") { vm.setTasteExcluded(song.id, false); onDismiss() }
                else DialogItem("Don't use for recommendations") { vm.setTasteExcluded(song.id, true); onDismiss() }
                if (playlists.isNotEmpty()) Text("Add to playlist", Modifier.padding(top = 10.dp, bottom = 4.dp), style = T.small, color = c.muted)
                playlists.forEach { p -> DialogItem(p.name) { vm.addToPlaylistNamed(p.id, listOf(song.id)); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.text) } },
    )
}

@Composable
fun DialogItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.fillMaxWidth(), style = T.title, color = P.colors.text)
    }
}

/**
 * Room to leave at the bottom of a pushed page (not a tab) so its last item can scroll clear of
 * the mini player and its fade. 0 when no song is loaded. Provided by MainActivity.
 */
val LocalBottomClearance = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** The navigation bar's height, for lists that draw edge to edge instead of padding the screen. */
@Composable
fun navBarBottom(): Dp =
    androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Whether the full player is on screen (false while it is built but hidden). */
val LocalPlayerVisible = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> { androidx.compose.runtime.mutableStateOf(true) }

/**
 * Scrolls a long title, but only while the player is on screen. A marquee asks for a frame every
 * vsync; running it on the hidden, pre-built player kept the whole app drawing nonstop.
 */
@Composable
fun Modifier.liveMarquee(): Modifier =
    if (LocalPlayerVisible.current.value) basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000, repeatDelayMillis = 2500) else this

/**
 * Playback position that moves every frame while playing. The player reports it every 200 ms;
 * in between it is extrapolated from the frame clock, so anything driven by it glides.
 */
@Composable
fun rememberLivePosition(vm: MainViewModel): androidx.compose.runtime.State<Long> {
    val reported = vm.position.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    // When the player last reported, in the frame clock's time base (System.nanoTime).
    val reportedAt = androidx.compose.runtime.remember { longArrayOf(System.nanoTime()) }
    LaunchedEffect(reported.value) { reportedAt[0] = System.nanoTime() }
    // The player is built ahead of time while hidden; don't run a per-frame clock for it then.
    val visible = LocalPlayerVisible.current.value
    return produceState(reported.value, now.isPlaying, visible) {
        if (!now.isPlaying || !visible) {
            androidx.compose.runtime.snapshotFlow { reported.value }.collect { value = it }
            return@produceState
        }
        // A steady clock that runs at real speed and is nudged gently towards the player's
        // reports, so it never stalls or jumps between them. Big gaps (a seek) snap.
        var shown = value.toDouble()
        var last: Long = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { t: Long ->
                shown += (t - last) / 1_000_000.0
                last = t
                val truth = reported.value + (t - reportedAt[0]) / 1_000_000.0
                val err = truth - shown
                shown = if (kotlin.math.abs(err) > 500) truth else shown + err * 0.06
                value = shown.toLong()
            }
        }
    }
}

/** Seek bar that reads the playback position itself, so only it redraws 5 times a second. */
@Composable
fun LiveWaveformSeek(vm: MainViewModel, durationMs: Long, seed: Long, modifier: Modifier = Modifier, barsHeight: Dp = 66.dp, bars: Int = 26) {
    val pos = rememberLivePosition(vm)
    val now by vm.now.collectAsStateWithLifecycle()
    // The bars move only while playing *and* on screen; otherwise their frame clock stops.
    val visible = LocalPlayerVisible.current.value
    com.skarif.kairos.ui.components.WaveformSeek(pos, durationMs, seed, vm::seekTo, modifier, barsHeight, bars, playing = now.isPlaying && visible)
}

@Composable
fun LiveTime(vm: MainViewModel, durationMs: Long, color: Color) {
    val pos by vm.position.collectAsStateWithLifecycle()
    Text("${formatTime(pos)} / ${formatTime(durationMs)}", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
}

/**
 * Swipe a row right or left to run an action; the row springs back afterwards. Uses the shared
 * swipe rule ([com.skarif.kairos.ui.motion.Swipe]): 72 dp or a quick flick, the same as closing
 * the player or swiping the cover. (Material's SwipeToDismissBox acted on the lightest flick.)
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offset = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val distancePx = com.skarif.kairos.ui.motion.Swipe.DISTANCE_DP * density.density
    // A tick under the finger the moment the swipe passes the point where it will act.
    val armed = kotlin.math.abs(offset.value) >= distancePx
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { d ->
        val next = offset.value + d
        // Only towards sides that have an action; resist past the commit point.
        val allowed = when {
            next > 0 && onRight == null -> 0f
            next < 0 && onLeft == null -> 0f
            kotlin.math.abs(next) > distancePx * 1.6f -> offset.value + d * 0.25f
            else -> next
        }
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { offset.snapTo(allowed) }
    }
    Box(
        Modifier.semantics {
            customActions = listOfNotNull(
                if (onRight != null && rightLabel != null) androidx.compose.ui.semantics.CustomAccessibilityAction(rightLabel) { onRight(); true } else null,
                if (onLeft != null && leftLabel != null) androidx.compose.ui.semantics.CustomAccessibilityAction(leftLabel) { onLeft(); true } else null,
            )
        },
    ) {
        // The strip the row has uncovered, coloured, with the action's icon.
        val o = offset.value
        if (o != 0f) {
            val right = o > 0
            val strip = with(density) { kotlin.math.abs(o).toDp() }
            Box(Modifier.matchParentSize().padding(horizontal = 12.dp, vertical = 2.dp)) {
                val icon = if (right) rightIcon else leftIcon
                Box(
                    Modifier.align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(strip).fillMaxHeight().clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item))
                        .background(if (right) c.accent else c.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        if (armed) 1.25f else (0.6f + kotlin.math.abs(o) / distancePx * 0.4f).coerceAtMost(1f),
                        androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "swipeIcon",
                    )
                    // Icon first; the action's name appears once there is room, so you know what a swipe will do.
                    val fg = if (right) c.onAccent else c.inkFg
                    val label = if (right) rightLabel else leftLabel
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (icon != null && strip > 36.dp) {
                            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        }
                        if (label != null && strip > 120.dp) Text(label, style = T.label.copy(fontSize = 13.sp), color = fg, maxLines = 1)
                    }
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    dragState, androidx.compose.foundation.gestures.Orientation.Horizontal,
                    enabled = onRight != null || onLeft != null,
                    onDragStopped = { v ->
                        when (com.skarif.kairos.ui.motion.Swipe.decide(offset.value, v, density.density)) {
                            1 -> onRight?.invoke()
                            -1 -> onLeft?.invoke()
                        }
                        offset.animateTo(0f, com.skarif.kairos.ui.motion.Motion.spatial(), initialVelocity = v)
                    },
                ),
        ) { content() }
    }
}

/**
 * The message bar: what just happened, with Undo when it can be undone. Disappears after
 * 4 seconds (6 with Undo), or when swiped away or tapped.
 */
@Composable
fun NoticeBar(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = P.colors
    val notice by vm.notice.collectAsStateWithLifecycle()
    val n = notice
    LaunchedEffect(n?.id) {
        if (n != null) { kotlinx.coroutines.delay(if (n.undo != null) 6_000 else 4_000); vm.dismissNotice(n.id) }
    }
    androidx.compose.animation.AnimatedVisibility(
        n != null, modifier,
        enter = androidx.compose.animation.slideInVertically(com.skarif.kairos.ui.motion.Motion.spatial()) { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(com.skarif.kairos.ui.motion.Motion.spatial()) { it } + androidx.compose.animation.fadeOut(),
    ) {
        // Keep showing the last message while it animates out.
        val shown = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(n) }
        if (n != null) shown.value = n
        val m = shown.value ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                .clip(RoundedCornerShape(com.skarif.kairos.ui.theme.Radius.item)).background(c.ink)
                .clickable(onClickLabel = "Dismiss") { vm.dismissNotice(m.id) }
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .androidx_semantics_live(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.text, Modifier.weight(1f).padding(vertical = 10.dp), style = T.body, color = c.inkFg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val undo = m.undo
            if (undo != null) {
                TextButton(onClick = { undo(); vm.dismissNotice(m.id) }) {
                    Text("Undo", style = T.label, color = c.accent)
                }
            }
        }
    }
}

/** Screen readers announce the message when it appears. */
private fun Modifier.androidx_semantics_live(): Modifier =
    semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
