package com.cdnhunter.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.floor

/**
 * The app's loading languages, and which one means what (the `Motion` tokens below them apply to
 * all of it — cubic-bezier curves only, no springs, nothing overshoots):
 *
 *   PAGE / SECTION DATA          [DotLoader]       three dots, floating in turn
 *   VPN CONNECTION               [ConnectGlyph]    one weighted line (the curves in this object)
 *   "CONNECTING" TEXT            [ActivityDots]    the same dot wave, set on the text's baseline
 *   NETWORK MEASUREMENT          [SignalLoader]    the four ping bars — also "IP not here yet"
 *   PULL TO REFRESH              [RefreshArrow]    a circular arrow that follows the pull
 *
 * They share one pace ([CycleMs]) and one resting weight ([TrackAlpha]) so they read as a single
 * instrument, but each is used for exactly one meaning and none stands in for another.
 */
internal object LoaderMotion {
    /** One pass of the ping loader, one revolution of the connect line, one wave of the dots. */
    const val CycleMs = 1100

    /** How faint the unlit part of a loader is — the ping bars' resting alpha. */
    const val TrackAlpha = 0.22f

    private const val TWO_PI = 6.2831855f

    /** One revolution, in seconds. */
    private const val REV_S = CycleMs / 1000f

    /**
     * How much of each revolution rides the ease-in-out curve; the rest is a steady turn. At 0.8
     * the slowest instant is a fifth of the mean pace, so the line eases to a crawl and never stops.
     */
    private const val WEIGHT = 0.8f

    private const val MIN_LENGTH = 40f
    private const val MAX_LENGTH = 230f

    /** Where in a revolution the line is longest — just after the speed peaks, like a weight
     *  trailing behind its own momentum. */
    private const val LENGTH_PEAK = 0.58f

    /**
     * Where the line's head is, in degrees, [t] seconds after it started. Each revolution is
     * slow → fast → slow on the app's ease-in-out bezier, blended with a steady turn so it never
     * stalls. Continuous across revolutions, and the same every time: nothing random.
     */
    fun headDegrees(t: Float): Float {
        val u = t / REV_S
        val rev = floor(u)
        val f = u - rev
        return 360f * (rev + (1f - WEIGHT) * f + WEIGHT * Motion.EaseInOut.transform(f))
    }

    /**
     * How long the line is, in degrees: short → medium → long → medium → short once per
     * revolution, on the same bezier (rising to [LENGTH_PEAK], easing back after it). Every other
     * revolution stretches a little less, so the rhythm is intentional rather than metronomic.
     */
    fun lengthDegrees(t: Float): Float {
        val u = t / REV_S
        val rev = floor(u)
        val f = u - rev
        val bump = if (f < LENGTH_PEAK) {
            Motion.EaseInOut.transform(f / LENGTH_PEAK)
        } else {
            1f - Motion.EaseInOut.transform((f - LENGTH_PEAK) / (1f - LENGTH_PEAK))
        }
        val reach = if (rev.toInt() % 2 == 0) 1f else 0.82f
        return MIN_LENGTH + (MAX_LENGTH - MIN_LENGTH) * bump * reach
    }

    /** A gentle once-per-cycle swell in brightness, the loader bars' "lit then released". */
    fun glow(t: Float): Float = 0.86f + 0.14f * cos(TWO_PI * t / REV_S)
}

/**
 * Seconds since [active] last became true, advancing once per frame while it is, and holding its
 * value otherwise (so a line that restarts continues from where it was). It is a plain state
 * object meant to be READ IN A DRAW OR GRAPHICS-LAYER LAMBDA: nothing recomposes per frame, and
 * when [active] is false or animations are off no frame callback is registered at all.
 */
@Composable
internal fun rememberLoaderClock(active: Boolean): State<Float> {
    val reduce = appReduceMotion()
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, reduce) {
        if (active && !reduce) {
            val origin = withFrameNanos { it } - (clock.floatValue * 1_000_000_000.0).toLong()
            while (true) {
                withFrameNanos { now -> clock.floatValue = (now - origin) / 1_000_000_000f }
            }
        }
    }
    return clock
}

/**
 * One dot wave for every set of three dots in the app. Each dot takes a short turn — it rises
 * quickly on the ease-out curve, then settles slowly on the ease-in-out curve — and the turn is
 * handed to the next dot [Stagger] later. Between turns a dot rests, so the wave has a beat.
 * Floating, not bouncing: the curves arrive and stop, nothing overshoots.
 */
internal object DotWave {
    /** Share of a cycle between one dot's turn and the next dot's. */
    const val Stagger = 0.17f

    /** Share of a cycle a single dot spends moving; for the rest it rests. */
    const val Turn = 0.58f

    /** Share of a turn spent rising; the remainder is the slower settle. */
    private const val Rise = 0.38f

    /** 0..1 height of dot [index] at [cycle] (0..1, repeating). */
    fun lift(cycle: Float, index: Int): Float {
        val u = ((cycle - index * Stagger) % 1f + 1f) % 1f
        if (u >= Turn) return 0f
        val f = u / Turn
        return if (f < Rise) {
            Motion.EaseOut.transform(f / Rise)
        } else {
            1f - Motion.EaseInOut.transform((f - Rise) / (1f - Rise))
        }
    }

    fun alpha(lift: Float): Float = 0.40f + 0.60f * lift

    fun scale(lift: Float): Float = 0.90f + 0.22f * lift
}

/**
 * The dot wave's clock: 0..1 once per [LoaderMotion.CycleMs], or null when animations are off (the
 * dots then sit still). A State meant to be read in a draw or graphics-layer lambda, so nothing
 * recomposes per frame.
 */
@Composable
internal fun rememberDotCycle(): State<Float>? =
    if (appReduceMotion()) {
        null
    } else {
        rememberInfiniteTransition(label = "dotCycle").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LoaderMotion.CycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dotCycleValue",
        )
    }

/** The three sizes of [DotLoader]. Same identity, different scale. */
internal enum class DotLoaderSize(val dot: Dp, val gap: Dp, val lift: Dp) {
    /** Inline in a button or a line of text. */
    Small(3.dp, 3.dp, 2.dp),

    /** A section or a page waiting for its data. */
    Medium(5.dp, 4.dp, 3.dp),

    /** A whole screen with nothing else on it. */
    Large(7.dp, 6.dp, 5.dp),
}

/**
 * THE page loader: three dots floating up in turn. Use it wherever a page, or the main content of
 * one, is waiting for data — and nowhere a different loader already means something (a ping or
 * an IP being measured, the VPN connecting, a pull to refresh).
 *
 * The box is a fixed size for its [dotSize], so swapping it for the content never moves anything.
 */
@Composable
internal fun DotLoader(
    modifier: Modifier = Modifier,
    dotSize: DotLoaderSize = DotLoaderSize.Medium,
    color: Color = AppDs.TextHi,
    description: String = "Loading",
) {
    val cycle = rememberDotCycle()
    Canvas(
        modifier
            .size(dotSize.dot * 3 + dotSize.gap * 2, dotSize.dot + dotSize.lift)
            .semantics { contentDescription = description },
    ) {
        val d = dotSize.dot.toPx()
        val g = dotSize.gap.toPx()
        val lift = dotSize.lift.toPx()
        for (i in 0..2) {
            val w = if (cycle == null) 0.5f else DotWave.lift(cycle.value, i)
            val a = if (cycle == null) 0.7f else DotWave.alpha(w)
            val sc = if (cycle == null) 1f else DotWave.scale(w)
            drawCircle(
                color = color.copy(alpha = color.alpha * a),
                radius = d / 2f * sc,
                center = Offset(i * (d + g) + d / 2f, size.height - d / 2f - lift * w),
            )
        }
    }
}

/**
 * The three dots after "Connecting": the same [DotWave] as [DotLoader], set as period glyphs so
 * they sit on the text's own baseline at any size. Each rests dim and still between its turns, so
 * the word before them never moves and the row never changes width. With animations off they are
 * three still, fully legible dots.
 */
@Composable
internal fun ActivityDots(
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val cycle = rememberDotCycle()
    Row(modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.Bottom) {
        for (i in 0..2) {
            Text(
                ".",
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val w = if (cycle == null) 0.6f else DotWave.lift(cycle.value, i)
                    translationY = -2.5.dp.toPx() * w
                    alpha = DotWave.alpha(w)
                    val s = DotWave.scale(w)
                    scaleX = s
                    scaleY = s
                    // A period sits low in its line box; scale about where the dot actually is.
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                },
            )
        }
    }
}
