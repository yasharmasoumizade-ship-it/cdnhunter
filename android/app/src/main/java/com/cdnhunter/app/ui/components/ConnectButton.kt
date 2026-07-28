package com.cdnhunter.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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

// ═══════════════════════════════════════════════════════════════════════
// CONNECT BUTTON — clean, minimal power toggle
//
// Deliberately simple: one background layer, two thin rings when connected,
// one spinner when connecting, one icon. Earlier revisions stacked a
// breathing ring + 3 repeating ripple circles + a radial background glow +
// an icon glow canvas + an icon drop-shadow blur all on top of each other —
// which is what actually reads as "messy" at the center: several
// semi-transparent green layers overlapping never look crisp, no matter how
// each one looks in isolation. This version keeps only what's needed to
// read the three states (off / connecting / connected) at a glance.
// ═══════════════════════════════════════════════════════════════════════

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
 * Ultra-modern Connect button with premium animations
 * - Disconnected: Minimal grey with subtle glow
 * - Connecting: Smooth gradient pulse + modern loader
 * - Connected: Vibrant green with floating effect & breathing aura
 */
@Composable
fun PremiumConnectButton(
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    colors: ButtonColors = AnanasButtonColors,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "modernButton")

    // ── Primary animations ──
    val breatheScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val pulseOpacity by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spinnerRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "spinner"
    )

    // ── Interaction states ──
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        finishedListener = { if (isPressed) isPressed = false },
        label = "pressScale"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF0a3d2a)
            connecting -> Color(0xFF1a2a3a)
            else -> Color(0xFF1a1a1e)
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            connected -> colors.accentGreen.copy(alpha = 0.5f)
            connecting -> colors.accentGreen.copy(alpha = 0.3f)
            else -> colors.textMuted.copy(alpha = 0.2f)
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (connected) colors.accentGreen else colors.textMuted,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val shadowElev by animateFloatAsState(
        targetValue = when {
            connected -> 20f
            connecting -> 12f
            else -> 6f
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "shadow"
    )

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Outer glow aura (connected only) ──
        if (connected) {
            Canvas(
                modifier = Modifier
                    .size(260.dp)
                    .scale(breatheScale)
            ) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.08f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Middle ring (connected breathing) ──
        if (connected) {
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .scale(breatheScale)
            ) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.15f),
                    radius = size.minDimension / 2f - 6.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Connecting: rotating gradient arc ──
        if (connecting) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .rotate(spinnerRotation)
            ) {
                drawArc(
                    color = colors.accentGreen,
                    startAngle = 0f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Main button ──
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .shadow(
                    elevation = shadowElev.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = if (connected) colors.accentGreen.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f),
                    spotColor = if (connected) colors.accentGreen.copy(alpha = 0.4f) else Color.Transparent
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = when {
                            connected -> listOf(
                                Color(0xFF1a5a3d).copy(alpha = 0.6f),
                                buttonBgColor
                            )
                            else -> listOf(
                                Color(0xFF25252a),
                                Color(0xFF15151a)
                            )
                        },
                        center = Offset(85f, 85f),
                        radius = 100f
                    )
                )
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .clickable(enabled = !connecting) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                // Modern loader with smooth spinner
                Box(contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .size(50.dp)
                            .rotate(spinnerRotation)
                    ) {
                        drawArc(
                            color = colors.accentGreen.copy(alpha = 0.8f),
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Connecting",
                        tint = colors.accentGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(60.dp)
                    )
                }
            } else {
                // Power icon with smooth color transition
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (connected) "Disconnect" else "Connect",
                    tint = iconColor,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        // ── Floating particle effect (connected only) ──
        if (connected) {
            repeat(3) { index ->
                val angle = (index * 120).toFloat()
                val distance by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 30f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "particle$index"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = (distance * kotlin.math.cos(Math.toRadians(angle.toDouble())) / 4).dp,
                            y = (distance * kotlin.math.sin(Math.toRadians(angle.toDouble())) / 4).dp
                        )
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.accentGreen.copy(alpha = pulseOpacity * 0.4f))
                )
            }
        }
    }
}
