package com.skarif.kairos.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skarif.kairos.ui.MainViewModel
import com.skarif.kairos.ui.components.CircleButton
import com.skarif.kairos.ui.components.Ic
import com.skarif.kairos.ui.components.ScreenBackground
import com.skarif.kairos.ui.motion.Motion
import com.skarif.kairos.ui.screens.LyricsPane
import com.skarif.kairos.ui.screens.NowPlayingPane
import com.skarif.kairos.ui.screens.QueuePane
import com.skarif.kairos.ui.screens.rememberCoverNow
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerPage { MAIN, LYRICS, QUEUE }

/**
 * The player is one surface with a single [progress]: 0 is the mini player above the dock,
 * 1 is full screen. Fingers, buttons and the back gesture all move the same number, so the
 * mini player, the cover and the full player are always one connected thing.
 */
@Stable
class PlayerController(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var page by mutableStateOf(PlayerPage.MAIN)
    /** Sideways offset of the cover while swiping between songs. */
    val skipX = Animatable(0f)
    /** Scroll of the full player, so the cover scrolls with the page. */
    val mainScroll = ScrollState(0)
    /** Pixels between collapsed and expanded, so a drag of N pixels moves the sheet N pixels. */
    internal var rangePx = 1f

    val isOpen: Boolean get() = progress.targetValue > 0.5f

    /** True while a finger is moving the player. */
    var dragging by mutableStateOf(false)
        private set

    // Only one animation drives the player at a time; a new one replaces the old.
    private var job: Job? = null
    private fun run(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }

    fun expand(to: PlayerPage = PlayerPage.MAIN) {
        page = to
        run { progress.animateTo(1f, Motion.sheet()) }
    }

    fun collapse() = run {
        progress.animateTo(0f, Motion.sheet())
        page = PlayerPage.MAIN
        mainScroll.scrollTo(0)
    }

    fun dragBy(dyPx: Float) {
        dragging = true
        job?.cancel()
        // Undispatched: the snap happens now, in order, never after a later settle().
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.snapTo((progress.value - dyPx / rangePx).coerceIn(0f, 1f))
        }
    }

    /** Ends a drag: a flick decides, otherwise whichever side is closer. */
    fun settle(velocityY: Float) {
        dragging = false
        val target = when {
            velocityY > 900f -> 0f
            velocityY < -900f -> 1f
            progress.value > 0.5f -> 1f
            else -> 0f
        }
        run {
            progress.animateTo(target, Motion.sheet(), initialVelocity = -velocityY / rangePx)
            if (target == 0f) { page = PlayerPage.MAIN; mainScroll.scrollTo(0) }
        }
    }
}

val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("No player") }

/** Places a child at a rectangle computed at layout time, so moving it never recomposes. */
private fun Modifier.placeAt(rect: () -> Rect): Modifier = layout { measurable, constraints ->
    val r = rect()
    val w = r.width.roundToInt().coerceAtLeast(1)
    val h = r.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The player layer. Sits above every page; [miniBottomPx] is how far above the screen's bottom
 * edge the mini player rests (higher when the dock is showing).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheet(
    vm: MainViewModel,
    ctrl: PlayerController,
    miniBottomPx: Float,
    onAlbum: (Long) -> Unit,
    onSleep: () -> Unit,
    onEqualizer: () -> Unit,
    onSaveQueue: () -> Unit,
) {
    val c = P.colors
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val art by vm.artwork.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = song
    LaunchedEffect(s == null) { if (s == null) ctrl.progress.snapTo(0f) }
    if (s == null) return

    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val heroHeightPx = with(density) { (LocalConfiguration.current.screenHeightDp * 0.47f).coerceIn(300f, 440f).dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val prevSong = now.queue.getOrNull(now.queuePosition - 1)?.let { vm.song(it.songId) }
    val nextSong = now.queue.getOrNull(now.queuePosition + 1)?.let { vm.song(it.songId) }
    val curImg = rememberCoverNow(vm, s)
    val artImg = remember(art) { art?.asImageBitmap() }
    val prevImg = rememberCoverNow(vm, prevSong)
    val nextImg = rememberCoverNow(vm, nextSong)
    // The neighbour that slid in is now the current cover, so snapping back shows the same picture.
    LaunchedEffect(s.id) { ctrl.skipX.snapTo(0f) }

    val open by remember { derivedStateOf { ctrl.progress.value > 0.001f } }
    // Safety net: if anything ever leaves the player resting half open, finish the move.
    LaunchedEffect(ctrl) {
        androidx.compose.runtime.snapshotFlow { Triple(ctrl.progress.isRunning, ctrl.dragging, ctrl.progress.value) }
            .collect { (running, dragging, v) -> if (!running && !dragging && v > 0.001f && v < 0.999f) ctrl.settle(0f) }
    }
    BackHandler(enabled = ctrl.isOpen) {
        if (ctrl.page != PlayerPage.MAIN) ctrl.page = PlayerPage.MAIN else ctrl.collapse()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val px = { d: Float -> with(density) { d.dp.toPx() } }
        val miniH = px(64f)
        val inset = px(14f)
        val miniTop = h - miniBottomPx - miniH
        ctrl.rangePx = miniTop.coerceAtLeast(1f)

        fun sheetRect(p: Float) = Rect(mix(inset, 0f, p), mix(miniTop, 0f, p), mix(w - inset, w, p), mix(miniTop + miniH, h, p))

        // Where the cover sits on each page of the full player; animated when the page changes.
        fun pageTarget(page: PlayerPage) = when (page) {
            PlayerPage.MAIN -> Rect(0f, 0f, w, heroHeightPx)
            PlayerPage.LYRICS -> Rect(px(74f), statusTop + px(14f), px(74f + 40f), statusTop + px(54f))
            PlayerPage.QUEUE -> Rect(px(30f), statusTop + px(82f), px(30f + 56f), statusTop + px(138f))
        }
        fun pageCorners(page: PlayerPage) = when (page) {
            PlayerPage.MAIN -> Offset(0f, px(46f)) // (top, bottom)
            PlayerPage.LYRICS -> Offset(px(12f), px(12f))
            PlayerPage.QUEUE -> Offset(px(18f), px(18f))
        }
        val pageRect = remember { Animatable(pageTarget(ctrl.page), Rect.VectorConverter) }
        val pageRadius = remember { Animatable(pageCorners(ctrl.page), Offset.VectorConverter) }
        LaunchedEffect(ctrl.page, w, h) {
            launch { pageRect.animateTo(pageTarget(ctrl.page), Motion.sheet()) }
            launch { pageRadius.animateTo(pageCorners(ctrl.page), Motion.sheet()) }
        }

        fun artRect(): Rect {
            val p = ctrl.progress.value
            val sr = sheetRect(0f)
            val mini = Rect(sr.left + px(8f), sr.top + px(8f), sr.left + px(56f), sr.top + px(56f))
            val full = pageRect.value.let { r ->
                if (ctrl.page == PlayerPage.MAIN) r.translate(0f, -ctrl.mainScroll.value.toFloat()) else r
            }
            return lerp(mini, full, p)
        }

        val miniBg = if (c.dark) Color(0xF0262626) else Color(0xF5FFFFFF)

        // 1. The surface: a floating card when collapsed, the whole screen when open.
        Box(
            Modifier
                .placeAt { sheetRect(ctrl.progress.value) }
                .graphicsLayer {
                    val p = ctrl.progress.value
                    shape = RoundedCornerShape(mix(30f, 0f, p).dp)
                    clip = true
                    shadowElevation = mix(10f, 0f, p).dp.toPx()
                }
                .drawBehind { drawRect(lerp(miniBg, c.bg, ctrl.progress.value)) }
                .draggable(
                    rememberDraggableState { ctrl.dragBy(it) },
                    Orientation.Vertical,
                    enabled = !ctrl.isOpen || ctrl.progress.isRunning,
                    onDragStopped = { v -> ctrl.settle(v) },
                )
                .clickable(
                    enabled = !ctrl.isOpen, role = Role.Button, onClickLabel = "Open player",
                    interactionSource = null, indication = null,
                ) { ctrl.expand() },
        ) {
            if (open) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ctrl.progress.value }) {
                    ScreenBackground(art, settings.blurBackground) {}
                }
            }
            MiniContent(vm, ctrl, s.title, s.artist, now.isPlaying, now.durationMs.takeIf { it > 0 } ?: s.durationMs)
        }

        // 2. The cover, morphing between the mini player, the hero and each page's thumbnail.
        // Neighbours sit either side for swiping between songs.
        @Composable
        fun cover(img: ImageBitmap?, offset: Int, desc: String?) {
            Box(
                Modifier
                    .placeAt { artRect() }
                    .graphicsLayer {
                        val p = ctrl.progress.value
                        val corners = pageRadius.value
                        val miniR = 16.dp.toPx()
                        val top = mix(miniR, corners.x, p)
                        val bottom = mix(miniR, corners.y, p)
                        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                        clip = true
                        translationX = if (offset == 0) ctrl.skipX.value else ctrl.skipX.value + offset * size.width
                        if (offset != 0) alpha = (0.55f + 0.45f * (1f - abs(translationX) / size.width)).coerceIn(0f, 1f)
                    }
                    .background(c.accent),
            ) {
                if (img != null) Image(img, desc, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        val pagerOn = ctrl.page == PlayerPage.MAIN && open
        if (pagerOn && prevSong != null) cover(prevImg, -1, null)
        if (pagerOn && nextSong != null) cover(nextImg, 1, null)
        cover(curImg ?: artImg, 0, "${s.album} cover")

        // 3. The full player's pages, fading in over the last part of the opening.
        if (open) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = ctrl.progress.value
                    alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    translationY = (1f - p) * 64.dp.toPx()
                },
            ) {
                AnimatedContent(
                    ctrl.page,
                    transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) },
                    label = "playerPage",
                ) { page ->
                    when (page) {
                        PlayerPage.MAIN -> NowPlayingPane(
                            vm, ctrl,
                            onLyrics = { ctrl.page = PlayerPage.LYRICS },
                            onQueue = { ctrl.page = PlayerPage.QUEUE },
                            onEqualizer = onEqualizer, onSleep = onSleep,
                            onAlbum = { id -> ctrl.collapse(); onAlbum(id) },
                            onSkip = { dir, vx ->
                                // Finish the swipe: slide the neighbour fully in, then change song.
                                scope.launch {
                                    val go = if (dir < 0) nextSong != null else prevSong != null
                                    if (go && dir != 0) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        ctrl.skipX.animateTo(dir * w, tween(220, easing = Motion.Emphasized), initialVelocity = vx)
                                        if (dir < 0) vm.next() else vm.previousTrack()
                                    } else {
                                        ctrl.skipX.animateTo(0f, Motion.spatial(), initialVelocity = vx)
                                    }
                                }
                            },
                            hasPrev = prevSong != null, hasNext = nextSong != null,
                        )
                        PlayerPage.LYRICS -> LyricsPane(vm, onBack = { ctrl.page = PlayerPage.MAIN }, onQueue = { ctrl.page = PlayerPage.QUEUE }, onCollapse = { ctrl.collapse() })
                        PlayerPage.QUEUE -> QueuePane(
                            vm, onBack = { ctrl.page = PlayerPage.MAIN },
                            onSaveAsPlaylist = { ctrl.collapse(); onSaveQueue() },
                            onOpenAlbum = { id -> ctrl.collapse(); onAlbum(id) },
                        )
                    }
                }
            }
        }
    }
}

/** Collapsed row: title, artist, play/pause, next, and a thin progress line. Fades out as it opens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniContent(vm: MainViewModel, ctrl: PlayerController, title: String, artist: String, playing: Boolean, durationMs: Long) {
    val c = P.colors
    val pos = vm.position.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var nudge by remember { mutableFloatStateOf(0f) }
    val nudgeAnim = remember { Animatable(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                alpha = (1f - ctrl.progress.value * 4f).coerceIn(0f, 1f)
                translationX = nudgeAnim.value
            }
            .pointerInput(Unit) {
                // Swipe the mini player sideways to change song.
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dir = when { nudge < -size.width * 0.15f -> -1; nudge > size.width * 0.15f -> 1; else -> 0 }
                        nudge = 0f
                        scope.launch {
                            if (dir != 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (dir < 0) vm.next() else vm.previousTrack()
                            }
                            nudgeAnim.animateTo(0f, Motion.spatial())
                        }
                    },
                    onDragCancel = { nudge = 0f; scope.launch { nudgeAnim.animateTo(0f, Motion.spatial()) } },
                ) { change, amount ->
                    change.consume()
                    nudge += amount
                    scope.launch { nudgeAnim.snapTo(nudge * 0.5f) }
                }
            }
            .drawBehind {
                val p = pos.value.toFloat() / durationMs.coerceAtLeast(1)
                val y = size.height - 2.dp.toPx()
                val l = 22.dp.toPx()
                drawLine(c.line, Offset(l, y), Offset(size.width - l, y), 3.dp.toPx(), StrokeCap.Round)
                drawLine(c.accent, Offset(l, y), Offset(l + (size.width - 2 * l) * p.coerceIn(0f, 1f), y), 3.dp.toPx(), StrokeCap.Round)
            },
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.width(48.dp)) // the cover is drawn here by the cover layer
            Column(Modifier.weight(1f)) {
                Text(title, style = T.title, color = c.text, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000))
                Text(artist, style = T.small.copy(fontSize = 13.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CircleButton(if (playing) Ic.Pause else Ic.Play, if (playing) "Pause" else "Play", vm::toggle, size = 46.dp, container = c.ink, content = c.inkFg)
            CircleButton(Ic.Next, "Next song", vm::next, size = 42.dp, container = Color.Transparent)
        }
    }
}
