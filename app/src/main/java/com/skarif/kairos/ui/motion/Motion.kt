package com.skarif.kairos.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * One motion language for the whole app. Things that move (position, size, shape) use springs,
 * so a gesture can hand its velocity over and nothing ever snaps. Things that only change look
 * (fade, colour) use short tweens.
 */
object Motion {
    /** Material 3 emphasized curves. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Large surfaces: the player opening and closing, pages arriving. No overshoot. */
    fun <T> sheet() = spring<T>(dampingRatio = 1f, stiffness = 380f)

    /** Cards, covers and other mid-size elements moving into place. */
    fun <T> spatial() = spring<T>(dampingRatio = 0.85f, stiffness = 500f)

    /** Small, quick things: icons, chips, press feedback. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

    fun <T> fadeIn() = tween<T>(220, delayMillis = 40, easing = EmphasizedDecelerate)
    fun <T> fadeOut() = tween<T>(140, easing = EmphasizedAccelerate)

    /** Page push and pop. */
    const val PAGE_MS = 420
}

/**
 * One rule for every swipe in the app: closing or opening the player, swiping the cover or the
 * mini player to change song, and swiping a list row (queue, library). A swipe counts when it
 * travels [DISTANCE_DP], or when it is a quick flick of at least [FLING_DP_PER_S] that moved
 * at least [MIN_FLING_DP] the same way. Before, closing the player needed half the screen while a
 * queue row went with the lightest flick.
 */
object Swipe {
    const val DISTANCE_DP = 72f
    const val FLING_DP_PER_S = 800f
    const val MIN_FLING_DP = 16f

    /**
     * Direction the swipe commits to: 1 positive, -1 negative, 0 none.
     * [offsetPx] how far it moved, [velocityPx] its speed in px/s.
     */
    fun decide(offsetPx: Float, velocityPx: Float, density: Float): Int {
        val dist = DISTANCE_DP * density
        val fling = FLING_DP_PER_S * density
        val min = MIN_FLING_DP * density
        return when {
            offsetPx >= dist || (velocityPx >= fling && offsetPx >= min) -> 1
            offsetPx <= -dist || (velocityPx <= -fling && offsetPx <= -min) -> -1
            else -> 0
        }
    }
}
