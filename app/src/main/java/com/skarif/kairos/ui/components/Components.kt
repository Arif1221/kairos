package com.skarif.kairos.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skarif.kairos.ui.theme.P
import com.skarif.kairos.ui.theme.T
import kotlin.math.roundToInt
import kotlin.random.Random

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Shrinks slightly while pressed: the tactile feedback every tappable element uses. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.93f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    container: Color = P.colors.card,
    content: Color = P.colors.text,
    iconSize: Dp = 20.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(container, tween(250), label = "circleBg")
    val fg by animateColorAsState(content, tween(250), label = "circleFg")
    Box(
        modifier
            .size(size)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, animationSpec = tween(180), label = "icon") { ic ->
            Icon(ic, contentDescription, tint = fg, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Boolean = false,
    big: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = P.colors
    val bg = when {
        accent -> c.accent
        selected -> c.ink
        else -> c.card
    }
    val fg = when {
        accent -> c.onAccent
        selected -> c.inkFg
        else -> c.text
    }
    val interaction = remember { MutableInteractionSource() }
    val bgA by animateColorAsState(bg, tween(250), label = "pillBg")
    val fgA by animateColorAsState(fg, tween(250), label = "pillFg")
    Row(
        modifier
            .height(if (big) 60.dp else 46.dp)
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(50))
            .background(bgA)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fgA, modifier = Modifier.size(18.dp))
        Text(text, color = fgA, style = if (big) T.title.copy(fontSize = 17.sp) else T.label, maxLines = 1)
    }
}

/** Rounded content card from the design. */
@Composable
fun Card(modifier: Modifier = Modifier, color: Color = P.colors.card, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(color)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * The album cover, heavily blurred, behind every screen, with a light or dark wash and the dot grid.
 * Android 12+ blurs live. Older phones get a 12-pixel copy stretched up, which looks the same at this blur.
 */
@Composable
fun ScreenBackground(art: Bitmap?, blur: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = P.colors
    Box(modifier.fillMaxSize().background(c.bg)) {
        if (blur && art != null) {
            // A 48 px copy, stretched and lightly blurred, looks the same as blurring the full image
            // at a fraction of the GPU cost. Crossfades when the song changes.
            Crossfade(art, animationSpec = tween(700), label = "bg") { bmp ->
                val tiny = remember(bmp) { Bitmap.createScaledBitmap(bmp, 48, 48, true).asImageBitmap() }
                Image(
                    tiny, null,
                    Modifier.fillMaxSize().scale(1.2f).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                )
            }
            Box(Modifier.fillMaxSize().background(c.veil))
        }
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                // Build the dot grid once per size, not on every frame.
                val step = 20.dp.toPx()
                val r = 1.1.dp.toPx()
                val dots = Path()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        dots.addOval(Rect(x - r, y - r, x + r, y + r))
                        x += step
                    }
                    y += step
                }
                onDrawBehind { drawPath(dots, Color(0x337F7F7F)) }
            },
        )
        content()
    }
}

/**
 * The seek bar as a bar chart of the song (design reference: the finance chart card).
 * The played part fills continuously across the bars, a black playhead marks the position,
 * and while playing the bars near the playhead gently pulse.
 * Bar heights are a stable pattern per song; a real waveform needs decoding the file.
 */
@Composable
fun WaveformSeek(
    positionMs: Long,
    durationMs: Long,
    seed: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barsHeight: Dp = 66.dp,
    bars: Int = 26,
    playing: Boolean = false,
) {
    val c = P.colors
    val heights = remember(seed, bars) { val r = Random(seed); List(bars) { 0.3f + r.nextFloat() * 0.7f } }
    val target = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Position arrives every 200 ms; animate between updates so the fill glides instead of ticking.
    val frac by animateFloatAsState(target, tween(220, easing = LinearEasing), label = "seek")
    // The pulse only runs while playing, so a paused screen does not redraw 60 times a second.
    val phase = if (playing) {
        rememberInfiniteTransition(label = "pulse")
            .animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    } else {
        null
    }
    val motion = if (playing) 1f else 0f
    var widthPx by remember { mutableIntStateOf(0) }
    var tipW by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val tipScale by animateFloatAsState(if (dragging) 1.15f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "tip")
    fun seekAt(x: Float) {
        if (durationMs > 0 && widthPx > 0) onSeek(((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong())
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(c.accent)
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barsHeight)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(durationMs) { detectTapGestures { seekAt(it.x) } }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; seekAt(it.x) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> seekAt(change.position.x) }
                }
                .semantics {
                    contentDescription = "Seek bar"
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                    setProgress { v -> onSeek((v * durationMs).toLong()); true }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (bars - 1)) / bars
                val playX = frac * size.width
                val radius = CornerRadius(minOf(w / 2, 8.dp.toPx()))
                val hatch = c.onAccent.copy(alpha = 0.42f)
                val solid = c.onAccent.copy(alpha = 0.92f)
                val step = 6.dp.toPx()
                val rects = heights.mapIndexed { i, h ->
                    val left = i * (w + gap)
                    // Bars within ~3 of the playhead breathe while music plays.
                    val near = (1f - kotlin.math.abs((left + w / 2) - playX) / (w * 3.5f)).coerceIn(0f, 1f)
                    val wobble = 1f + 0.14f * motion * near * kotlin.math.sin((phase?.value ?: 0f) + i * 0.9f)
                    val bh = (size.height * h * wobble).coerceAtMost(size.height)
                    RoundRect(left, size.height - bh, left + w, size.height, radius)
                }
                // Unplayed: hatched.
                rects.forEach { r ->
                    clipPath(Path().apply { addRoundRect(r) }) {
                        var x = r.left - r.height
                        while (x < r.right) {
                            drawLine(hatch, Offset(x, size.height), Offset(x + r.height, r.top), strokeWidth = 2.dp.toPx())
                            x += step
                        }
                    }
                }
                // Played: solid, clipped at the exact position so it fills smoothly through a bar.
                clipRect(right = playX) {
                    rects.forEach { r -> drawPath(Path().apply { addRoundRect(r) }, solid) }
                }
                // Playhead.
                val ph = 4.dp.toPx()
                drawRoundRect(
                    Color(0xFF151515), Offset((playX - ph / 2).coerceIn(0f, size.width - ph), 0f),
                    Size(ph, size.height), CornerRadius(ph / 2),
                )
            }
            if (widthPx > 0) {
                val tipX = ((frac * widthPx) - tipW / 2f).roundToInt().coerceIn(0, (widthPx - tipW).coerceAtLeast(0))
                Text(
                    formatTime(positionMs),
                    style = T.small.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF151515),
                    modifier = Modifier
                        .onSizeChanged { tipW = it.width }
                        .offset { IntOffset(tipX, (-22).dp.roundToPx()) }
                        .graphicsLayer { scaleX = tipScale; scaleY = tipScale }
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
            Text("−" + formatTime((durationMs - positionMs).coerceAtLeast(0)), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    val c = P.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = c.text)
            if (subtitle != null) Text(subtitle, style = T.small, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = c.line, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = P.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(c.line.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(animateColorAsState(if (on) c.ink else Color.Transparent, tween(250), label = "seg").value)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.label.copy(fontSize = 14.sp), color = animateColorAsState(if (on) c.inkFg else c.text, tween(250), label = "segT").value, maxLines = 1)
            }
        }
    }
}

@Composable
fun RadioCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val c = P.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(2.dp, if (selected) c.accent else Color.Transparent, RoundedCornerShape(24.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) c.accent else c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = if (enabled) c.text else c.muted)
            Text(description, style = T.small, color = c.muted)
        }
    }
}

/** Back button, centred title, optional action on the right (52 dp each side). */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Ic.Back, "Back", onBack)
        Text(title, Modifier.weight(1f), style = T.title, color = P.colors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) action() else Box(Modifier.size(52.dp))
    }
}

/** Row of filled and dashed pills, the design's progress meter. */
@Composable
fun PillMeter(filled: Int, total: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val shape = RoundedCornerShape(11.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .then(
                        if (i < filled) Modifier.clip(shape).background(color)
                        else Modifier.border(1.5.dp, color.copy(alpha = 0.45f), shape),
                    ),
            )
        }
    }
}

/** Bars with hatched fill; the selected bar is solid black with a tooltip (finance reference). */
@Composable
fun HatchedBars(
    values: List<Float>,
    labels: List<String>,
    selected: Int,
    tooltip: String?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp,
) {
    val c = P.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(chartHeight), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).fillMaxSize().selectable(selected = on, role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = labels.getOrElse(i) { "" } },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Canvas(Modifier.fillMaxWidth().fillMaxSize()) {
                        val h = (size.height * v.coerceIn(0.04f, 1f))
                        val top = size.height - h
                        val r = CornerRadius(10.dp.toPx())
                        if (on) {
                            drawRoundRect(Color(0xFF151515), Offset(0f, top), Size(size.width, h), r)
                        } else {
                            val path = Path().apply { addRoundRect(RoundRect(0f, top, size.width, size.height, r)) }
                            clipPath(path) {
                                var x = -h
                                while (x < size.width) {
                                    drawLine(c.onAccent.copy(alpha = 0.42f), Offset(x, size.height), Offset(x + h, top), strokeWidth = 2.dp.toPx())
                                    x += 6.dp.toPx()
                                }
                            }
                        }
                    }
                    if (on && tooltip != null) {
                        Text(
                            tooltip, style = T.small.copy(fontWeight = FontWeight.Bold), color = Color(0xFF151515),
                            modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), style = T.small.copy(fontWeight = if (i == selected) FontWeight.ExtraBold else FontWeight.Medium),
                    color = c.onAccent, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}
