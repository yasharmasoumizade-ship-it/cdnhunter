package com.cdnhunter.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The connect mark's four moods. These mirror the app's REAL connection state — the glyph never
 * advances on a timer of its own: it only ever moves toward the phase it is handed.
 */
internal enum class GlyphPhase { Idle, Connecting, Connected, Disconnecting, Error }

/** The bright arc's length while the tunnel is coming up or going down. */
private const val ARC_SWEEP = 110f

/** One revolution of the arc. Slow enough to read as signal flow rather than "loading". */
private const val RING_SPIN_MS = 1100

/** Half a breath — the ring swells and settles over twice this. */
private const val BREATHE_HALF_MS = 700

private const val PULSE_MS = 560

private fun glyphSpec(
    reduce: Boolean,
    ms: Int,
    delayMs: Int = 0,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<Float> = if (reduce) snap() else tween(ms, delayMs, easing)

/**
 * The app's signature micro-interaction: the lightning bolt becomes a thin ring while the tunnel
 * is coming up, the ring completes and becomes a check once it is up, and the whole thing plays
 * backwards on the way down.
 *
 *   Idle           bolt.
 *   Connecting     bolt → ring; a short arc with a bright head travels round it while the ring
 *                  breathes. Motion says "traffic is moving", not "the app is busy".
 *   Connected      the arc closes into a full ring → the ring becomes a check (drawn, not faded
 *                  in) → one soft pulse leaves it.
 *   Disconnecting  the reverse: the check un-draws, the ring reopens and turns the other way.
 *   Error          the arc closes into a still ring that takes the error colour and gives one
 *                  short shake; the caller then moves to [GlyphPhase.Idle] and it returns to bolt.
 *
 * Everything is a few draw calls on one canvas, and every animated value is read in the draw
 * phase, so no frame of any of this recomposes. Nothing runs while idle or connected. With
 * animations off the glyph still shows each state, just without the travel: the arc is static
 * and every change is a cut.
 *
 * Fixed-size and drawn inside its own bounds — the caller's layout never moves.
 */
@Composable
internal fun ConnectGlyph(
    phase: GlyphPhase,
    boltPath: Path,
    modifier: Modifier = Modifier,
    boltColor: Color = AppDs.Bone,
    ringColor: Color = AppDs.AccentSoft,
    checkColor: Color = AppDs.OnAccent,
    errorColor: Color = AppDs.Error,
) {
    val reduce = appReduceMotion()
    val first = remember { phase }

    val bolt = remember { Animatable(if (first == GlyphPhase.Idle) 1f else 0f) }
    val ring = remember {
        Animatable(
            if (first == GlyphPhase.Connecting || first == GlyphPhase.Disconnecting || first == GlyphPhase.Error) 1f else 0f,
        )
    }
    val sweep = remember { Animatable(if (first == GlyphPhase.Error) 360f else ARC_SWEEP) }
    val check = remember { Animatable(if (first == GlyphPhase.Connected) 1f else 0f) }
    val fault = remember { Animatable(if (first == GlyphPhase.Error) 1f else 0f) }
    val spin = remember { Animatable(0f) }
    val breathe = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) } // 1 = finished: nothing is drawn
    val shake = remember { Animatable(0f) }

    // One effect, keyed on the phase: a change cancels whatever was running (loops included) and
    // every value continues from where it is, so an interrupted transition never jumps.
    LaunchedEffect(phase, reduce) {
        when (phase) {
            GlyphPhase.Idle -> {
                launch { check.animateTo(0f, glyphSpec(reduce, 160)) }
                launch { ring.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { breathe.animateTo(0f, glyphSpec(reduce, 200)) }
                launch { bolt.animateTo(1f, glyphSpec(reduce, 260, delayMs = 100)) }
            }

            GlyphPhase.Connecting -> {
                launch { bolt.animateTo(0f, glyphSpec(reduce, 150)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 160)) }
                launch { sweep.animateTo(ARC_SWEEP, glyphSpec(reduce, 260)) }
                launch { ring.animateTo(1f, glyphSpec(reduce, 240, delayMs = 60)) }
                if (!reduce) {
                    launch {
                        while (true) {
                            spin.snapTo(spin.value % 360f)
                            spin.animateTo(spin.value + 360f, tween(RING_SPIN_MS, easing = LinearEasing))
                        }
                    }
                    launch {
                        while (true) {
                            breathe.animateTo(1f, tween(BREATHE_HALF_MS, easing = EaseInOutSine))
                            breathe.animateTo(0f, tween(BREATHE_HALF_MS, easing = EaseInOutSine))
                        }
                    }
                }
            }

            GlyphPhase.Connected -> {
                val cameFromAnotherGlyph = bolt.value > 0.01f || ring.value > 0.01f
                launch { breathe.animateTo(0f, glyphSpec(reduce, 200)) }
                if (cameFromAnotherGlyph) {
                    // 1 · the ring completes its motion.
                    launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                    launch { fault.animateTo(0f, glyphSpec(reduce, 120)) }
                    ring.animateTo(1f, glyphSpec(reduce, 100))
                    sweep.animateTo(360f, glyphSpec(reduce, 260))
                    // 2 · the ring becomes a check, and one soft pulse leaves it.
                    launch { ring.animateTo(0f, glyphSpec(reduce, 220)) }
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
                // The reverse of connecting: the check un-draws, the ring reopens and turns back.
                launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { fault.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 200)) }
                ring.animateTo(1f, glyphSpec(reduce, 200, delayMs = 60))
                sweep.animateTo(ARC_SWEEP, glyphSpec(reduce, 300))
                if (!reduce) {
                    launch {
                        while (true) {
                            spin.snapTo(spin.value % 360f)
                            spin.animateTo(spin.value - 360f, tween(RING_SPIN_MS, easing = LinearEasing))
                        }
                    }
                }
            }

            GlyphPhase.Error -> {
                launch { bolt.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { check.animateTo(0f, glyphSpec(reduce, 120)) }
                launch { breathe.animateTo(0f, glyphSpec(reduce, 160)) }
                launch { ring.animateTo(1f, glyphSpec(reduce, 160)) }
                launch { fault.animateTo(1f, glyphSpec(reduce, 220)) }
                launch { sweep.animateTo(360f, glyphSpec(reduce, 280)) }
                if (!reduce) {
                    for (x in floatArrayOf(-1f, 1f, -0.6f, 0.3f, 0f)) {
                        shake.animateTo(x, tween(55, easing = LinearEasing))
                    }
                }
            }
        }
    }

    val bounds = remember(boltPath) { boltPath.getBounds() }

    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val stroke = 2.dp.toPx()

        translate(left = shake.value * 2.5.dp.toPx()) {
            // ── ring ───────────────────────────────────────────────────────────────────
            val ringA = ring.value
            if (ringA > 0.01f) {
                val r = s * 0.31f
                val tint = lerp(ringColor, errorColor, fault.value)
                val done = ((sweep.value - ARC_SWEEP) / (360f - ARC_SWEEP)).coerceIn(0f, 1f)
                val ringScale = (0.78f + 0.22f * ringA) * (1f + 0.05f * breathe.value)
                scale(ringScale, pivot = c) {
                    drawCircle(tint.copy(alpha = 0.22f * ringA), r, c, style = Stroke(stroke))
                    rotate(spin.value, pivot = c) {
                        val topLeft = Offset(c.x - r, c.y - r)
                        val box = Size(r * 2f, r * 2f)
                        // The long, faint tail …
                        drawArc(
                            color = tint.copy(alpha = (0.45f + 0.55f * done) * ringA),
                            startAngle = -90f,
                            sweepAngle = sweep.value,
                            useCenter = false,
                            topLeft = topLeft,
                            size = box,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        if (done < 0.99f) {
                            // … the brighter head …
                            val head = sweep.value * 0.4f
                            drawArc(
                                color = tint.copy(alpha = ringA * (1f - done)),
                                startAngle = -90f + sweep.value - head,
                                sweepAngle = head,
                                useCenter = false,
                                topLeft = topLeft,
                                size = box,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                            // … and the small highlight riding the leading edge.
                            val a = Math.toRadians((-90f + sweep.value).toDouble())
                            drawCircle(
                                color = Color.White.copy(alpha = ringA * (1f - done)),
                                radius = stroke * 0.9f,
                                center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                            )
                        }
                    }
                }
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
