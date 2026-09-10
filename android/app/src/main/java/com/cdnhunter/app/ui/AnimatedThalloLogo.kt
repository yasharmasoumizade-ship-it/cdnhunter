package com.cdnhunter.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Animated Thallo "T" logo with bottom-to-top teal fill animation.
 *
 * The "T" is drawn as a vector shape with geometric proportions (bold, uniform stroke,
 * slightly rounded corners). The fill animates from bottom to top in a continuous 1.8s loop,
 * creating a liquid-filling effect suitable for loading/splash screens.
 */
@Composable
fun AnimatedThalloLogo(
    modifier: Modifier = Modifier,
    tealColor: Color = Color(0xFF14B8A6) // Tailwind teal-500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logoFill")
    val fillProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f) // Material emphasized easing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "fillProgress"
    )

    Box(modifier = modifier.size(200.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            // Define "T" shape with geometric proportions
            // Bold, uniform stroke width with slightly rounded corners
            val strokeWidth = width * 0.22f // Thick stroke for bold appearance
            val cornerRadius = strokeWidth * 0.12f // Subtle rounding

            // Create the "T" path
            val tPath = Path().apply {
                // Horizontal top bar
                val barHeight = strokeWidth
                val barTop = height * 0.15f
                addRoundRect(
                    RoundRect(
                        left = width * 0.1f,
                        top = barTop,
                        right = width * 0.9f,
                        bottom = barTop + barHeight,
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                )

                // Vertical stem (centered under the horizontal bar)
                val stemLeft = (width - strokeWidth) / 2f
                val stemTop = barTop
                val stemBottom = height * 0.85f
                addRoundRect(
                    RoundRect(
                        left = stemLeft,
                        top = stemTop,
                        right = stemLeft + strokeWidth,
                        bottom = stemBottom,
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                )
            }

            // Create clip path that reveals from bottom to top
            val clipPath = Path().apply {
                val revealHeight = height * fillProgress
                addRect(
                    Rect(
                        left = 0f,
                        top = height - revealHeight,
                        right = width,
                        bottom = height
                    )
                )
            }

            // Draw the filled portion (clipped to reveal bottom-to-top)
            clipPath(clipPath) {
                drawPath(
                    path = tPath,
                    color = tealColor
                )
            }
        }
    }
}
