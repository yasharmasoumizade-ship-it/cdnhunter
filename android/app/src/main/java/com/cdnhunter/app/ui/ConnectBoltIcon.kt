package com.cdnhunter.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class BoltConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

private fun buildBoltPath(): Path = Path().apply {
    moveTo(17.387f, 0.042f)
    cubicTo(17.713f, 0.133f, 17.882f, 0.386f, 17.857f, 0.75f)
    cubicTo(17.847f, 0.954f, 17.819f, 1.017f, 17.482f, 1.599f)
    cubicTo(17.047f, 2.353f, 16.09f, 3.983f, 15.094f, 5.666f)
    cubicTo(13.685f, 8.05f, 13.713f, 7.998f, 13.713f, 8.176f)
    cubicTo(13.713f, 8.38f, 13.818f, 8.552f, 14.0f, 8.657f)
    lineTo(14.141f, 8.734f)
    lineTo(16.458f, 8.734f)
    cubicTo(18.962f, 8.734f, 18.92f, 8.73f, 19.119f, 8.92f)
    cubicTo(19.323f, 9.109f, 19.4f, 9.495f, 19.288f, 9.765f)
    cubicTo(19.26f, 9.828f, 17.335f, 12.419f, 15.003f, 15.522f)
    cubicTo(12.675f, 18.625f, 10.347f, 21.724f, 9.835f, 22.408f)
    cubicTo(9.32f, 23.095f, 8.843f, 23.712f, 8.769f, 23.783f)
    cubicTo(8.604f, 23.944f, 8.467f, 24.0f, 8.317f, 23.972f)
    cubicTo(8.145f, 23.94f, 8.071f, 23.842f, 8.054f, 23.621f)
    cubicTo(8.036f, 23.411f, 8.078f, 23.246f, 8.783f, 20.743f)
    cubicTo(10.024f, 16.325f, 10.571f, 14.34f, 10.571f, 14.242f)
    cubicTo(10.571f, 14.095f, 10.498f, 13.958f, 10.35f, 13.828f)
    cubicTo(10.235f, 13.727f, 10.21f, 13.72f, 9.814f, 13.699f)
    cubicTo(9.582f, 13.685f, 8.467f, 13.678f, 7.328f, 13.681f)
    cubicTo(5.536f, 13.688f, 5.242f, 13.681f, 5.137f, 13.636f)
    cubicTo(4.86f, 13.513f, 4.684f, 13.236f, 4.681f, 12.917f)
    cubicTo(4.681f, 12.696f, 4.6f, 12.917f, 6.82f, 7.174f)
    cubicTo(7.03f, 6.634f, 7.454f, 5.529f, 7.766f, 4.719f)
    cubicTo(8.078f, 3.909f, 8.446f, 2.963f, 8.58f, 2.616f)
    cubicTo(8.716f, 2.269f, 8.941f, 1.683f, 9.081f, 1.315f)
    cubicTo(9.397f, 0.484f, 9.446f, 0.393f, 9.632f, 0.235f)
    cubicTo(9.905f, 0.0f, 9.723f, 0.007f, 13.674f, 0.007f)
    cubicTo(16.15f, 0.004f, 17.293f, 0.014f, 17.387f, 0.042f)
    close()
}

@Composable
fun ConnectBoltIcon(
    state: BoltConnectionState,
    modifier: Modifier = Modifier,
    connectedColor: Color = Color(0xFF5DCAA5),
    connectingColor: Color = Color(0xFF5DCAA5),
    disconnectedColor: Color = Color(0xFF5F5E5A),
) {
    val boltPath = remember { buildBoltPath() }

    val infiniteTransition = rememberInfiniteTransition(label = "bolt_connecting")
    val drawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "draw_progress"
    )
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )

    val targetFillAlpha = when (state) {
        BoltConnectionState.DISCONNECTED -> 0f
        BoltConnectionState.CONNECTING -> flicker
        BoltConnectionState.CONNECTED -> 1f
    }
    val fillAlpha by animateFloatAsState(
        targetValue = targetFillAlpha,
        animationSpec = tween(durationMillis = 220),
        label = "fill_alpha"
    )

    val targetColor = when (state) {
        BoltConnectionState.DISCONNECTED -> disconnectedColor
        BoltConnectionState.CONNECTING -> connectingColor
        BoltConnectionState.CONNECTED -> connectedColor
    }

    val targetGlowAlpha = if (state == BoltConnectionState.CONNECTED) 0.35f else 0f
    val glowAlpha by animateFloatAsState(
        targetValue = targetGlowAlpha,
        animationSpec = tween(durationMillis = 400),
        label = "glow_alpha"
    )

    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        val pathBounds = boltPath.getBounds()
        val offsetX = (size.width - pathBounds.width * scale) / 2f - pathBounds.left * scale
        val offsetY = (size.height - pathBounds.height * scale) / 2f - pathBounds.top * scale

        translate(left = offsetX, top = offsetY) {
            scale(scale = scale, pivot = Offset.Zero) {

                if (glowAlpha > 0f) {
                    drawPath(
                        path = boltPath,
                        color = connectedColor.copy(alpha = glowAlpha),
                        style = Fill
                    )
                }

                drawPath(
                    path = boltPath,
                    color = targetColor,
                    style = Stroke(
                        width = 1.6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                if (state == BoltConnectionState.CONNECTING) {
                    val measure = PathMeasure()
                    measure.setPath(boltPath, false)
                    val length = measure.length
                    val segment = Path()
                    measure.getSegment(
                        startDistance = 0f,
                        stopDistance = length * drawProgress,
                        destination = segment,
                        startWithMoveTo = true
                    )
                    drawPath(
                        path = segment,
                        color = connectingColor,
                        style = Stroke(
                            width = 2.4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawPath(
                        path = boltPath,
                        color = connectingColor.copy(alpha = fillAlpha * 0.55f),
                        style = Fill
                    )
                } else if (fillAlpha > 0f) {
                    drawPath(
                        path = boltPath,
                        color = targetColor.copy(alpha = fillAlpha),
                        style = Fill
                    )
                }
            }
        }
    }
}
