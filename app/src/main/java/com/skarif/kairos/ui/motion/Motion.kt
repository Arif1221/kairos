package com.skarif.kairos.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import kotlin.math.PI

/**
 * Kairos's motion system, modelled on iOS and pushed a little further.
 *
 * Like iOS:
 * - Everything that moves is a spring, described the way SwiftUI does: a **response** (seconds
 *   for one oscillation, how quick it feels) and a **damping fraction** (1 = no overshoot).
 *   Springs keep the finger's velocity when a gesture lets go and can be interrupted at any point,
 *   so nothing ever snaps or waits for an animation to finish.
 * - Pages push in from the right edge over the old page, which slides back a third and dims.
 * - Opening the player pushes the app behind it back, like an iOS sheet.
 * - Buttons and cards press down instantly and spring back with a little life.
 * - The cover shrinks back when paused, like Apple Music.
 *
 * Better than iOS:
 * - Back is interactive from **either** edge: Android's predictive back scrubs the real pop
 *   animation under your thumb (iOS only allows the left edge).
 * - Switching tabs crossfades with a slight zoom instead of cutting.
 * - Lists animate items moving, appearing and disappearing when you filter or reorder.
 * - One swipe rule for every gesture ([Swipe]).
 *
 * "Remove animations" in Android's accessibility settings turns all of this off: Compose scales
 * springs and tweens by the system animator scale.
 */
object Motion {
    /**
     * A spring from iOS-style parameters. stiffness = (2π / response)², and the damping
     * fraction is Compose's damping ratio, so `ios(0.55, 0.825)` is SwiftUI's default spring.
     */
    fun <T> ios(response: Float, damping: Float, threshold: T? = null) = spring(
        dampingRatio = damping,
        stiffness = (2 * PI / response).let { (it * it).toFloat() },
        visibilityThreshold = threshold,
    )

    // ---- The springs. Names say how they feel, not where they are used.

    /** Large surfaces: pages, the player sheet. Calm, no overshoot (SwiftUI .smooth). */
    fun <T> sheet() = ios<T>(0.5f, 1f)

    /** Cards, covers and other mid-size things moving into place (SwiftUI default). */
    fun <T> spatial() = ios<T>(0.45f, 0.86f)

    /** Small, quick things: icons, chips (SwiftUI .snappy). */
    fun <T> snappy() = ios<T>(0.3f, 0.85f)

    /** A visible bounce, for moments that should feel alive: release after a press, play/pause. */
    fun <T> bouncy() = ios<T>(0.4f, 0.62f)

    /** Following a finger: nearly instant, no wobble. */
    fun <T> interactive() = ios<T>(0.15f, 0.9f)

    /** Pages, with a pixel threshold so they settle crisply instead of creeping the last pixel. */
    private val pageSpring = ios(0.42f, 1f, IntOffset(1, 1))

    // Fades and colour changes are not physical, so they are short curves, not springs.
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(220, delayMillis = 40, easing = EmphasizedDecelerate)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(140, easing = EmphasizedAccelerate)

    /** Kept for screens that time things against the page transition. */
    const val PAGE_MS = 420

    // ---- Page transitions (NavHost).

    /**
     * Push, iOS style: the new page comes in from the right edge, the old one slides back a
     * third and fades. Pages here are see-through over one shared background, so the old page
     * fades out quickly instead of dimming, so the two never show text over text.
     */
    val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(pageSpring) { it } + fadeIn(tween(160, easing = EmphasizedDecelerate))
    }
    val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(pageSpring) { -it / 3 } + fadeOut(tween(180, easing = EmphasizedAccelerate))
    }

    /** Pop: the mirror image. With predictive back, these run under your thumb as you swipe. */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(pageSpring) { -it / 3 } + fadeIn(tween(260, delayMillis = 60, easing = EmphasizedDecelerate))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(pageSpring) { it } + fadeOut(tween(220, delayMillis = 60, easing = EmphasizedAccelerate))
    }

    /** Tabs: a quick crossfade with a slight zoom, so switching never cuts. */
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(180, delayMillis = 90, easing = EmphasizedDecelerate)) + scaleIn(ios(0.45f, 1f), initialScale = 0.985f)
    }
    /** Gone before the new tab starts to show, so two see-through pages never overlap. */
    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(90, easing = EmphasizedAccelerate))
    }
}

/**
 * One rule for every swipe in the app: closing or opening the player, swiping the cover or the
 * mini player to change song, and swiping a list row (queue, library). A swipe counts when it
 * travels [DISTANCE_DP], or when it is a quick flick of at least [FLING_DP_PER_S] that moved
 * at least [MIN_FLING_DP] the same way.
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
