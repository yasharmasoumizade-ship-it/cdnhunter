package com.cdnhunter.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * The app's one motion system: SIGNAL -> FLOW -> CONNECTION -> RESOLVE.
 *
 * Four durations, three curves, no springs. Nothing here overshoots: every curve is a cubic-bezier
 * that arrives and stops. Durations follow the `motion` skill's token table (100 / 150 / 200 / 300)
 * with one documented exception for the VPN lifecycle:
 *
 *   MICRO       100  press feedback, hover-weight colour shifts.
 *   STANDARD    200  normal enter / state change (a border, a fill, a fade-in).
 *   EXIT        150  anything leaving: faster than it came.
 *   EMPHASIS    300  an important change of state (a screen, a hero).
 *   CONNECTION  420  the connect lifecycle only (bolt -> check, pulse): optical exception, the one
 *                    moment the app is allowed to take its time.
 *
 * Loading has five fixed meanings and they are never swapped (see [LoaderMotion]):
 * page data = [DotLoader], VPN connecting = [ConnectGlyph]'s weighted line, "Connecting" text =
 * [ActivityDots], a ping or a not-yet-known IP = [SignalLoader], pull to refresh = [RefreshArrow].
 *
 * Enter uses [EaseOut], exit [EaseIn], a value that moves between two places [EaseInOut]. Under
 * reduced motion every spec built here is a cut ([snap]); callers that also translate or scale
 * must drop that part and keep only a fade.
 */
internal object Motion {
    const val Micro = 100
    const val Standard = 200
    const val Exit = 150
    const val Emphasis = 300
    const val Connection = 420

    /** One lap of the light that travels round an active border (see [perimeterLight]). */
    const val PerimeterMs = 3400

    /** Arrives fast, settles slowly. For things entering. */
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Leaves slowly, finishes fast. For things exiting. */
    val EaseIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

    /** Symmetric. For a value travelling between two resting places (a toggle thumb, a segment). */
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** List stagger: at most this long between rows, and the whole cascade never runs past [StaggerCapMs]. */
    const val StaggerMs = 30
    const val StaggerCapMs = 200

    fun <T> enter(reduce: Boolean, ms: Int = Standard, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(ms, delayMs, EaseOut)

    fun <T> exit(reduce: Boolean, ms: Int = Exit): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(ms, 0, EaseIn)

    fun <T> inOut(reduce: Boolean, ms: Int = Standard): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(ms, 0, EaseInOut)

    /** Delay for row [index] of an entering list: 30ms apart, capped so a long list is never theatrical. */
    fun stagger(index: Int): Int = (index * StaggerMs).coerceAtMost(StaggerCapMs)
}

/**
 * Haptics, in three strengths and no more. Each is one call from an event handler or from an effect
 * keyed on a real state change — never from inside an animation — and each goes through the
 * system's own haptic setting, so a user who has turned touch feedback off hears nothing from here.
 */
internal object AppHaptics {
    /** A light tick: a tap, a selection, a switch. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** The tunnel is really up. Only ever called on the transition into CONNECTED. */
    fun success(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    /** An attempt really failed. */
    fun failure(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}
