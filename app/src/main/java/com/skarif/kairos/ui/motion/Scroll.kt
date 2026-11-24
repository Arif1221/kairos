package com.skarif.kairos.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * Scrolling, iOS style:
 * - [rememberIosFling]: a fling that glides longer and slows down evenly, like UIScrollView
 *   (Android's spline fling stops sooner and more abruptly).
 * - [RubberBand]: pulling past the top or bottom of a list stretches it with growing resistance
 *   and springs back; a fling that hits the end overshoots a little and bounces back.
 * - [RubberBand.pull] lets headers react to the pull: large titles grow, covers zoom.
 */

/**
 * iOS's normal deceleration is 0.998 per millisecond, an exponential decay of about 2 per second.
 * Compose's exponential decay runs at 4.2 per second times the multiplier, so 0.5 is iOS-like.
 */
private val IosDecay: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = 0.5f)

private class IosFling(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) < 1f) return initialVelocity
        var left = initialVelocity
        var last = 0f
        AnimationState(0f, initialVelocity).animateDecay(decay) {
            val delta = value - last
            val used = scrollBy(delta)
            last = value
            left = velocity
            // Hit the end of the list: hand the leftover speed to the rubber band.
            if (abs(delta - used) > 0.5f) cancelAnimation()
        }
        return left
    }
}

@Composable
fun rememberIosFling(): FlingBehavior = remember { IosFling(IosDecay) }

/** The stretch at the ends of a list. Positive [pull] = pulled down at the top. */
@Stable
class RubberBand internal constructor(private val scope: CoroutineScope) {
    private val offset = Animatable(0f)
    /** Current stretch in pixels; read it in a graphicsLayer/draw lambda. */
    val pull: Float get() = offset.value
    internal var dragging = false

    /** iOS's rubber-band curve: the further you pull, the less it moves. */
    internal fun resist(current: Float, delta: Float, dimension: Float): Float {
        val c = 0.55f
        val x = abs(current) + abs(delta)
        // Inverse of f(x) = (1 - 1/(x*c/d + 1)) * d, applied incrementally.
        val factor = (1f - (x / dimension).coerceIn(0f, 0.95f)) * c
        return delta * factor
    }

    internal fun nudge(by: Float) { scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(offset.value + by) } }

    internal fun release(velocity: Float = 0f) {
        scope.launch { offset.animateTo(0f, Motion.ios(0.45f, 1f), initialVelocity = velocity) }
    }

    /** A fling hit the end: overshoot by its leftover speed, then spring back (iOS bounce). */
    internal fun bounce(velocity: Float) {
        scope.launch {
            offset.snapTo(offset.value)
            offset.animateTo(0f, Motion.ios(0.5f, 0.82f), initialVelocity = (velocity * 0.35f).coerceIn(-6000f, 6000f))
        }
    }
}

@Composable
fun rememberRubberBand(): RubberBand {
    val scope = rememberCoroutineScope()
    return remember { RubberBand(scope) }
}

/**
 * Put this before `verticalScroll` / on a lazy list's modifier. It catches scrolling the list
 * couldn't use (it was already at an end) and turns it into the stretch, drawn as a translation
 * of the whole list. Android's own stretch effect is turned off app-wide in MainActivity.
 */
fun Modifier.rubberBand(band: RubberBand, dimensionPx: Float = 2400f): Modifier = this
    .nestedScroll(object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // While stretched, scrolling back first undoes the stretch.
            val p = band.pull
            if (source != NestedScrollSource.UserInput || p == 0f || available.y == 0f || sign(available.y) == sign(p)) return Offset.Zero
            val take = if (abs(available.y) > abs(p)) -p else available.y
            band.nudge(take)
            return Offset(0f, take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            band.dragging = true
            band.nudge(band.resist(band.pull, available.y, dimensionPx))
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (band.pull != 0f) { band.dragging = false; band.release(available.y); return available }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            band.dragging = false
            if (abs(available.y) > 50f) band.bounce(available.y)
            return available
        }
    })
    .graphicsLayer { translationY = band.pull }
