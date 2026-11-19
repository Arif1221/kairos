package com.skarif.kairos.ui.components

import android.graphics.Bitmap
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
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = content, modifier = Modifier.size(iconSize))
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
    Row(
        modifier
            .height(if (big) 60.dp else 46.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, style = if (big) T.title.copy(fontSize = 17.sp) else T.label)
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
            if (Build.VERSION.SDK_INT >= 31) {
                val img = remember(art) { art.asImageBitmap() }
                Image(
                    img, null,
                    Modifier.fillMaxSize().scale(1.3f).blur(60.dp, BlurredEdgeTreatment.Unbounded),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val tiny = remember(art) { Bitmap.createScaledBitmap(art, 12, 12, true).asImageBitmap() }
                Image(tiny, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            }
            Box(Modifier.fillMaxSize().background(c.veil))
        }
        Canvas(Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            val r = 1.1.dp.toPx()
            val dot = Color(0x337F7F7F)
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(dot, r, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

/**
 * The seek bar as a bar chart of the song (design reference: the finance chart card).
 * Played bars are solid, the rest hatched, the current bar black with a time tooltip.
 * Bar heights are a stable pattern per song for now; a real waveform needs decoding the file.
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
) {
    val c = P.colors
    val heights = remember(seed, bars) { val r = Random(seed); List(bars) { 0.3f + r.nextFloat() * 0.7f } }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var widthPx by remember { mutableIntStateOf(0) }
    var tipW by remember { mutableIntStateOf(0) }
    fun seekAt(x: Float) {
        if (durationMs > 0 && widthPx > 0) onSeek(((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong())
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(c.accent)
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barsHeight)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(durationMs) { detectTapGestures { seekAt(it.x) } }
                .pointerInput(durationMs) { detectHorizontalDragGestures { change, _ -> seekAt(change.position.x) } }
                .semantics {
                    contentDescription = "Seek bar"
                    progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                    setProgress { v -> onSeek((v * durationMs).toLong()); true }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (bars - 1)) / bars
                val cur = (frac * bars).toInt().coerceAtMost(bars - 1)
                val hatch = c.onAccent.copy(alpha = 0.42f)
                heights.forEachIndexed { i, h ->
                    val bh = size.height * h
                    val left = i * (w + gap)
                    val top = size.height - bh
                    val radius = CornerRadius(minOf(w / 2, 8.dp.toPx()))
                    when {
                        i == cur -> drawRoundRect(Color(0xFF151515), Offset(left, top), Size(w, bh), radius)
                        i < cur -> drawRoundRect(c.onAccent.copy(alpha = 0.9f), Offset(left, top), Size(w, bh), radius)
                        else -> {
                            val path = Path().apply { addRoundRect(RoundRect(left, top, left + w, size.height, radius)) }
                            clipPath(path) {
                                val step = 6.dp.toPx()
                                var x = left - bh
                                while (x < left + w) {
                                    drawLine(hatch, Offset(x, size.height), Offset(x + bh, top), strokeWidth = 2.dp.toPx())
                                    x += step
                                }
                            }
                        }
                    }
                }
            }
            if (widthPx > 0) {
                val tipX = ((frac * widthPx) - tipW / 2f).roundToInt().coerceIn(0, (widthPx - tipW).coerceAtLeast(0))
                Text(
                    formatTime(positionMs),
                    style = T.small.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF151515),
                    modifier = Modifier
                        .onSizeChanged { tipW = it.width }
                        .offset { IntOffset(tipX, (-18).dp.roundToPx()) }
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
            Text(formatTime(durationMs), style = T.small.copy(fontWeight = FontWeight.SemiBold), color = c.onAccent)
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
                    .background(if (on) c.ink else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.label.copy(fontSize = 14.sp), color = if (on) c.inkFg else c.text)
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
