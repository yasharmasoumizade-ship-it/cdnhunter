package com.cdnhunter.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How many pieces the trailing light is cut into; each is a step brighter than the one behind it. */
private const val PIECES = 8

/**
 * Where a stretch of a closed outline of length [total] lies, written into [out] as
 * (start, stop) pairs. A stretch that runs past the end of the outline wraps round to its start
 * and so comes back as two pairs. Returns how many pairs were written (1 or 2).
 *
 * This is the whole of the "no jump at the seam" guarantee, kept as plain arithmetic so it can be
 * tested without a device.
 */
internal fun wrapSegment(start: Float, stop: Float, total: Float, out: FloatArray): Int {
    var from = start % total
    if (from < 0f) from += total
    val to = from + (stop - start)
    return if (to <= total) {
        out[0] = from
        out[1] = to
        1
    } else {
        out[0] = from
        out[1] = total
        out[2] = 0f
        out[3] = to - total
        2
    }
}

/** Brightness of piece [index] of [PIECES], 0 at the dim tail to [peak] at the head. */
internal fun pieceAlpha(index: Int, peak: Float): Float {
    val f = (index + 1f) / PIECES
    return peak * f * f
}

/**
 * A small light that travels round the element's own rounded outline, one lap per
 * [Motion.PerimeterMs]. It marks the one thing that is active, focused or selected, in place of a
 * steady glow: at any instant only a short stretch of the border is brighter, with a soft tail
 * behind it, and the rest of the border is whatever the element already draws.
 *
 * The light follows the real outline ([cornerRadius] must match the element's shape), so it goes
 * round the corners without a jump. It is drawn in the draw phase from one animated value, so
 * nothing recomposes while it runs, and nothing at all runs while [enabled] is false. With
 * animations off it is not drawn: the border simply stays still.
 *
 * Put it after the element's `.border(...)` and inside its `.clip(...)`.
 */
@Composable
internal fun Modifier.perimeterLight(
    cornerRadius: Dp,
    enabled: Boolean,
    color: Color = AppDs.AccentSoft,
    strokeWidth: Dp = 1.5.dp,
    peakAlpha: Float = 0.9f,
    fraction: Float = 0.2f,
): Modifier {
    if (!enabled || appReduceMotion()) return this
    val lap = rememberInfiniteTransition(label = "perimeterLight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeat(),
        label = "perimeterLap",
    )
    return drawWithCache {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val radius = (cornerRadius.toPx() - inset).coerceIn(0f, minOf(size.width, size.height) / 2f - inset)
        val outline = Path().apply {
            addRoundRect(
                RoundRect(
                    left = inset,
                    top = inset,
                    right = size.width - inset,
                    bottom = size.height - inset,
                    cornerRadius = CornerRadius(radius),
                ),
            )
        }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val total = measure.length
        val piece = Path()
        val ranges = FloatArray(4)
        val core = Stroke(width = stroke)
        val halo = Stroke(width = stroke * 3f)
        onDrawWithContent {
            drawContent()
            if (total <= 0f) return@onDrawWithContent
            val length = total * fraction
            val step = length / PIECES
            val head = lap.value * total
            for (i in 0 until PIECES) {
                val from = head - length + i * step
                val count = wrapSegment(from, from + step, total, ranges)
                val a = pieceAlpha(i, peakAlpha)
                for (k in 0 until count) {
                    piece.reset()
                    measure.getSegment(ranges[k * 2], ranges[k * 2 + 1], piece, true)
                    drawPath(piece, color.copy(alpha = a * 0.16f), style = halo)
                    drawPath(piece, color.copy(alpha = a), style = core)
                }
            }
        }
    }
}

private fun infiniteRepeat() = infiniteRepeatable<Float>(
    animation = tween(Motion.PerimeterMs, easing = LinearEasing),
)
