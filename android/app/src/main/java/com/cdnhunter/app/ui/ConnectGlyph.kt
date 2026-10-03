package com.cdnhunter.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * The connect mark's four moods. These mirror the app's REAL connection state — the glyph never
 * advances on a timer of its own: it only ever moves toward the phase it is handed.
 */
internal enum class GlyphPhase { Idle, Connecting, Connected, Disconnecting, Error }

/** Where the line sits on the ring, as a share of the glyph. */
private const val RING_RADIUS = 0.31f

/** The line's weight. One width for all of it — the weight is in the motion, not the stroke. */
private val LineWidth = 2.4.dp

/** How much slower the line runs on the way down: the same line, in a quieter mode. */
private const val DISCONNECT_PACE = 0.7f

private const val PULSE_MS = 560

private fun glyphSpec(
    reduce: Boolean,
    ms: Int,
    delayMs: Int = 0,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<Float> = if (reduce) snap() else tween(ms, delayMs, easing)

/**
 * The app's signature micro-interaction: the lightning bolt becomes ONE weighted line travelling
 * round a small loop while the tunnel is coming up, the line closes and becomes a check once it
 * is up, and the whole thing plays backwards on the way down.
 *
 *   Idle           bolt.
 *   Connecting     bolt → line. The line is a single stroke whose speed and length both breathe
 *                  ([LoaderMotion.headDegrees] / [LoaderMotion.lengthDegrees]): it surges, eases to
 *                  a crawl, never stops, stretches as it speeds up and shortens as it slows. Bone,
 *                  one thin rounded stroke, no glow: rotation and length run on their own curves.
 *   Connected      the line closes into a full loop → the loop becomes a check (drawn, not faded
 *                  in) → one soft pulse leaves it.
 *   Disconnecting  the reverse: the check un-draws, the loop opens back into the line, which
 *                  turns the other way at a lower pace.
 *   Error          the line closes into a still loop that takes the error colour -- a controlled
 *                  interruption, no shake; the caller then moves to [GlyphPhase.Idle] and it returns to bolt.
 *
 * It is a few draw calls on one canvas and every animated value is read in the draw phase, so no
 * frame recomposes. Nothing runs while idle or connected (the clock is not even registered), and
 * the only per-frame work is a handful of sines. With animations off the line is still and every
 * change is a cut.
 *
 * Fixed-size and drawn inside its own bounds — the caller's layout never moves.
 */
@Composable
internal fun ConnectGlyph(
    phase: GlyphPhase,
    boltPath: Path,
    modifier: Modifier = Modifier,
    boltColor: Color = AppDs.Bone,
    ringColor: Color = AppDs.Bone,
    checkColor: Color = AppDs.OnAccent,
    errorColor: Color = AppDs.Error,
) {
    val reduce = appReduceMotion()
    val first = remember { phase }

    val bolt = remember { Animatable(if (first == GlyphPhase.Idle) 1f else 0f) }
    // How present the line is (0 = not drawn, 1 = full), and how far it has closed into a loop
    // (0 = its travelling length, 1 = a complete circle).
    val line = remember {
        Animatable(
            if (first == GlyphPhase.Connecting || first == GlyphPhase.Disconnecting || first == GlyphPhase.Error) 1f else 0f,
        )
    }
    val closed = remember { Animatable(if (first == GlyphPhase.Error) 1f else 0f) }
    val check = remember { Animatable(if (first == GlyphPhase.Connected) 1f else 0f) }
    val fault = remember { Animatable(if (first == GlyphPhase.Error) 1f else 0f) }
    val pulse = remember { Animatable(1f) } // 1 = finished: nothing is drawn
    val clock = rememberLoaderClock(active = phase == GlyphPhase.Connecting || phase == GlyphPhase.Disconnecting)
    val density = LocalDensity.current
    val lineStroke = remember(density) { Stroke(width = with(density) { LineWidth.toPx() }, cap = StrokeCap.Round) }

    // One effect, keyed on the phase: a change cancels whatever was running (loops included) and
    // every value continues from where it is, so an interrupted transition never jumps.
    LaunchedEffect(phase, reduce) {
        when (phase) {
            GlyphPhase.Idle -> {
                launch { check.animateTo(0f, glyphSpec(reduce, 160)) }
                launch { line.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { closed.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { bolt.animateTo(1f, glyphSpec(reduce, 260, delayMs = 100)) }
            }

            GlyphPhase.Connecting -> {
                // The bolt hands over to the line: the bolt shrinks and fades while the line draws
                // itself out of nothing, so there is never an empty well.
                launch { bolt.animateTo(0f, glyphSpec(reduce, 150)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 160)) }
                launch { closed.animateTo(0f, glyphSpec(reduce, 260)) }
                launch { line.animateTo(1f, glyphSpec(reduce, 260, delayMs = 40)) }
            }

            GlyphPhase.Connected -> {
                val cameFromAnotherGlyph = bolt.value > 0.01f || line.value > 0.01f
                if (cameFromAnotherGlyph) {
                    // 1 · the line closes into a loop.
                    launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                    launch { fault.animateTo(0f, glyphSpec(reduce, 120)) }
                    line.animateTo(1f, glyphSpec(reduce, 100))
                    closed.animateTo(1f, glyphSpec(reduce, 260))
                    // 2 · the loop becomes a check, and one soft pulse leaves it.
                    launch { line.animateTo(0f, glyphSpec(reduce, 220)) }
                    if (!reduce) {
                        launch {
                            pulse.snapTo(0f)
                            pulse.animateTo(1f, tween(PULSE_MS, delayMillis = 140, easing = LinearOutSlowInEasing))
                        }
                    }
                    check.animateTo(1f, glyphSpec(reduce, 300, delayMs = 30))
                } else {
                    check.snapTo(1f)
                }
            }

            GlyphPhase.Disconnecting -> {
                // The reverse of connecting: the check un-draws, the loop opens back into the line.
                // A line cancelled mid-travel dips out and back in, because it turns the other way
                // from here and would otherwise jump.
                if (line.value > 0.01f && closed.value < 0.5f) line.animateTo(0f, glyphSpec(reduce, 90))
                launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 200)) }
                line.animateTo(1f, glyphSpec(reduce, 200, delayMs = 40))
                closed.animateTo(0f, glyphSpec(reduce, 300))
            }

            GlyphPhase.Error -> {
                launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { line.animateTo(1f, glyphSpec(reduce, 160)) }
                launch { fault.animateTo(1f, glyphSpec(reduce, 220)) }
                launch { closed.animateTo(1f, glyphSpec(reduce, 280)) }
            }
        }
    }

    val bounds = remember(boltPath) { boltPath.getBounds() }
    val disconnecting = phase == GlyphPhase.Disconnecting

    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)

        run {
            // ── the line ───────────────────────────────────────────────────────────────
            val present = line.value
            if (present > 0.01f) {
                val r = s * RING_RADIUS
                val tint = lerp(ringColor, errorColor, fault.value)
                val t = clock.value * (if (disconnecting) DISCONNECT_PACE else 1f)
                // Clockwise on the way up (the line trails behind its head), anticlockwise on the way down.
                val headAngle = -90f + LoaderMotion.headDegrees(t) * (if (disconnecting) -1f else 1f)
                // It draws itself out of nothing, and closes into a loop when asked to.
                val travel = LoaderMotion.lengthDegrees(t) * present
                val length = travel + (360f - travel) * closed.value
                drawArc(
                    color = tint.copy(alpha = present),
                    startAngle = if (disconnecting) headAngle else headAngle - length,
                    sweepAngle = length,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = lineStroke,
                )
            }

            // ── bolt ───────────────────────────────────────────────────────────────────
            val boltA = bolt.value
            if (boltA > 0.01f) {
                val fit = s * 0.46f / maxOf(bounds.width, bounds.height)
                translate(top = (1f - boltA) * -3.dp.toPx()) {
                    scale(0.6f + 0.4f * boltA, pivot = c) {
                        translate(
                            left = (size.width - bounds.width * fit) / 2f - bounds.left * fit,
                            top = (size.height - bounds.height * fit) / 2f - bounds.top * fit,
                        ) {
                            scale(scale = fit, pivot = Offset.Zero) {
                                drawPath(boltPath, boltColor.copy(alpha = boltColor.alpha * boltA), style = Fill)
                            }
                        }
                    }
                }
            }

            // ── check ──────────────────────────────────────────────────────────────────
            val p = check.value
            if (p > 0.01f) {
                val a = Offset(c.x - s * 0.17f, c.y + s * 0.01f)
                val b = Offset(c.x - s * 0.05f, c.y + s * 0.13f)
                val d = Offset(c.x + s * 0.19f, c.y - s * 0.12f)
                val len1 = distance(a, b)
                val len2 = distance(b, d)
                val drawn = p * (len1 + len2)
                val w = 2.6.dp.toPx()
                scale(0.9f + 0.1f * p, pivot = c) {
                    val t1 = (drawn / len1).coerceIn(0f, 1f)
                    drawLine(checkColor, a, along(a, b, t1), w, StrokeCap.Round)
                    if (drawn > len1) {
                        val t2 = ((drawn - len1) / len2).coerceIn(0f, 1f)
                        drawLine(checkColor, b, along(b, d, t2), w, StrokeCap.Round)
                    }
                }
            }

            // ── the one pulse ──────────────────────────────────────────────────────────
            val t = pulse.value
            if (t < 0.99f) {
                drawCircle(
                    color = checkColor.copy(alpha = 0.35f * (1f - t)),
                    radius = s * (0.20f + 0.26f * t),
                    center = c,
                    style = Stroke(1.5.dp.toPx()),
                )
            }
        }
    }
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

private fun along(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
