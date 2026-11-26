package com.skarif.kairos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A soft light around the screen's edges in the album's colour. Used twice:
 * - a new song starts: a full glow that fades (the colours arriving with the song);
 * - the "Kairos moment": a lighter one when the chorus first hits.
 * Draw-only: one gradient per edge while it is showing, nothing at all otherwise.
 */
@Stable
class EdgeGlow(private val scope: CoroutineScope) {
    internal val amount = Animatable(0f)

    fun flash(strength: Float = 1f) {
        scope.launch {
            amount.animateTo(strength.coerceIn(0f, 1f), tween(220, easing = LinearEasing))
            amount.animateTo(0f, tween(1100, easing = LinearEasing))
        }
    }
}

val LocalEdgeGlow = staticCompositionLocalOf<EdgeGlow?> { null }

@Composable
fun rememberEdgeGlow(scope: CoroutineScope): EdgeGlow = remember { EdgeGlow(scope) }

/** Draws [glow] in [color]; put it last in the root box so it sits over everything. */
@Composable
fun EdgeGlowOverlay(glow: EdgeGlow, color: Color) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val a = glow.amount.value
            if (a <= 0.001f) return@drawBehind
            val w = 34.dp.toPx()
            val col = color.copy(alpha = 0.55f * a)
            val clear = color.copy(alpha = 0f)
            drawRect(Brush.verticalGradient(listOf(col, clear), 0f, w), Offset.Zero, Size(size.width, w))
            drawRect(Brush.verticalGradient(listOf(clear, col), size.height - w, size.height), Offset(0f, size.height - w), Size(size.width, w))
            drawRect(Brush.horizontalGradient(listOf(col, clear), 0f, w), Offset.Zero, Size(w, size.height))
            drawRect(Brush.horizontalGradient(listOf(clear, col), size.width - w, size.width), Offset(size.width - w, 0f), Size(w, size.height))
        },
    )
}
