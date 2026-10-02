package com.cdnhunter.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One timing system for everything in the app that says "working".
 *
 * [SignalLoader] (the four lit bars that stand in for a ping while it is measured), the connect
 * button's travelling line ([ConnectGlyph]) and the dots after "Connecting" all take their pace
 * from [CycleMs] and their unlit/resting weight from [TrackAlpha], so they read as one instrument
 * measuring rather than three unrelated spinners.
 */
internal object LoaderMotion {
    /** One pass of the ping loader. The connect line turns once per this on average; the dots'
     *  wave repeats at this rate. */
    const val CycleMs = 1100

    /** How faint the unlit part of a loader is — the ping bars' resting alpha. */
    const val TrackAlpha = 0.22f

    private const val TWO_PI = 6.2831855f

    /** Mean angular speed implied by [CycleMs], in degrees per second. */
    private const val MEAN_DEG_PER_S = 327.27272f // 360° / 1.1s

    /**
     * Where the line's head is, in degrees, [t] seconds after it started.
     *
     * A steady turn with two slow swells laid over it (periods 1.3s and 2.9s, which never line
     * up), so the line surges, eases and surges again without ever repeating a beat. The swells'
     * combined slope is capped below the mean speed, so the speed never reaches zero: at its
     * slowest the line still moves at about a fifth of its mean pace — it eases, it never stops.
     */
    fun headDegrees(t: Float): Float =
        MEAN_DEG_PER_S * t +
            41f * sin(TWO_PI * t / 1.30f) +
            27f * sin(TWO_PI * t / 2.90f + 1.1f)

    /**
     * How long the line is, in degrees. It stretches as the head surges — peaking a little AFTER
     * the speed does, as a weight trailing behind its own momentum would — and a slower swell
     * (2.1s) rolls the whole range, so short → medium → long → medium → short without a corner.
     */
    fun lengthDegrees(t: Float): Float {
        val surge = 0.5f + 0.5f * cos(TWO_PI * t / 1.30f - 0.9f)
        val roll = 0.5f + 0.5f * sin(TWO_PI * t / 2.10f + 0.4f)
        return 38f + (240f - 38f) * (0.7f * surge + 0.3f * roll)
    }

    /** A gentle once-per-cycle swell in brightness, the loader bars' "lit then released". */
    fun glow(t: Float): Float = 0.86f + 0.14f * cos(TWO_PI * t / (CycleMs / 1000f))
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
 * The three dots after "Connecting", each its own object: a short hop with a touch of scale and
 * opacity, handed from one to the next on [LoaderMotion.CycleMs]. Each dot rests dim and still
 * between its turns, so the word before them never moves and the row never changes width.
 *
 * They are real period glyphs, so they sit on the text's own baseline at any size. With
 * animations off they are three still, fully legible dots.
 */
@Composable
internal fun ActivityDots(
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val cycle: State<Float>? = if (reduce) {
        null
    } else {
        rememberInfiniteTransition(label = "activityDots").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(LoaderMotion.CycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "dotCycle",
        )
    }
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
                    // Each dot's own turn begins DOT_STAGGER after the one before it.
                    val u = (((cycle?.value ?: 0f) - i * DOT_STAGGER) % 1f + 1f) % 1f
                    val w = if (cycle == null) 0.6f else if (u < DOT_TURN) sin(PI.toFloat() * u / DOT_TURN) else 0f
                    translationY = -2.5.dp.toPx() * w
                    alpha = 0.40f + 0.60f * w
                    val s = 0.90f + 0.22f * w
                    scaleX = s
                    scaleY = s
                    // A period sits low in its line box; scale about where the dot actually is.
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                },
            )
        }
    }
}

/** Share of a cycle between one dot's turn and the next dot's. */
private const val DOT_STAGGER = 0.17f

/** Share of a cycle a single dot spends moving; for the rest it rests. */
private const val DOT_TURN = 0.55f
