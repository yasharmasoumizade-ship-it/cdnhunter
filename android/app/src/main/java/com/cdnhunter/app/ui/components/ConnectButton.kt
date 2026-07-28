package com.cdnhunter.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

data class ButtonColors(
    val accentGreen: Color = Color(0xFF00D084),
    val accentGreenDim: Color = Color(0xFF00A366),
    val backgroundDark: Color = Color(0xFF18181b),
    val backgroundDarker: Color = Color(0xFF0d0d0f),
    val textPrimary: Color = Color(0xFFEBEBF0),
    val textMuted: Color = Color(0xFF7e8084),
    val divider: Color = Color(0xFF252529),
)

val AnanasButtonColors = ButtonColors(
    accentGreen = Color(0xFF00D084),
    accentGreenDim = Color(0xFF00A366),
)

/**
 * Ultra-modern Connect button with liquid animation
 * 
 * States:
 * - OFF: Minimal grey button
 * - CONNECTING: Liquid filling animation (flowing inward)
 * - CONNECTED: Subtle floating effect
 */
@Composable
fun PremiumConnectButton(
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    colors: ButtonColors = AnanasButtonColors,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "liquidButton")

    // ── Liquid particles animation (connecting only) ──
    val liquidProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidProgress"
    )

    // ── Floating effect (connected only) ──
    val floatOffsetY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // ── Press interaction ──
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        finishedListener = { if (isPressed) isPressed = false },
        label = "pressScale"
    )

    // ── Color transitions ──
    val iconColor by animateColorAsState(
        targetValue = if (connected) colors.accentGreen else colors.textMuted,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val shadowElev by animateFloatAsState(
        targetValue = when {
            connected -> 16f
            connecting -> 8f
            else -> 4f
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "shadow"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Main button with liquid effect ──
        Box(
            modifier = Modifier
                .size(170.dp)
                .offset(y = if (connected) floatOffsetY.dp else 0.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .shadow(
                    elevation = shadowElev.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = if (connected) colors.accentGreen.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                    spotColor = if (connected) colors.accentGreen.copy(alpha = 0.3f) else Color.Transparent
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = if (connected) {
                            listOf(
                                Color(0xFF1a5a3d).copy(alpha = 0.5f),
                                Color(0xFF0a3d2a)
                            )
                        } else {
                            listOf(
                                Color(0xFF25252a),
                                Color(0xFF15151a)
                            )
                        },
                        center = Offset(85f, 85f),
                        radius = 100f
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = when {
                        connected -> colors.accentGreen.copy(alpha = 0.4f)
                        connecting -> colors.accentGreen.copy(alpha = 0.2f)
                        else -> colors.textMuted.copy(alpha = 0.1f)
                    },
                    shape = CircleShape
                )
                .clickable(enabled = !connecting) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            // ── Liquid animation (only during connecting) ──
            if (connecting) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2

                    // Liquid particles flowing inward
                    repeat(12) { index ->
                        val angle = (index * 30f) * (PI / 180f)
                        val distance = radius * (1f - liquidProgress)
                        val x = centerX + (distance * cos(angle)).toFloat()
                        val y = centerY + (distance * sin(angle)).toFloat()

                        // Particle size decreases as it approaches center
                        val particleSize = 6f * (1f - liquidProgress)
                        val alpha = (1f - liquidProgress).coerceIn(0f, 1f)

                        drawCircle(
                            color = colors.accentGreen.copy(alpha = alpha * 0.8f),
                            radius = particleSize,
                            center = Offset(x, y)
                        )
                    }

                    // Central liquid blob
                    val blobAlpha = 0.3f * liquidProgress
                    val blobRadius = (radius * 0.5f) * liquidProgress
                    drawCircle(
                        color = colors.accentGreen.copy(alpha = blobAlpha),
                        radius = blobRadius,
                        center = Offset(centerX, centerY)
                    )
                }
            }

            // ── Power icon ──
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (connected) "Disconnect" else "Connect",
                tint = iconColor,
                modifier = Modifier
                    .size(75.dp)
                    .scale(if (connecting) 0.85f else 1f)
            )
        }

        // ── Connected state glow ring ──
        if (connected) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .offset(y = floatOffsetY.dp)
            ) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.12f),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
