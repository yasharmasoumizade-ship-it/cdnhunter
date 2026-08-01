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
    val idleBlue: Color = Color(0xFF3B82F6),
    val connectingAmber: Color = Color(0xFFF59E0B),
    val connectedGreen: Color = Color(0xFF0F5132),
    val connectedGreenBright: Color = Color(0xFF15803D),
    val textMuted: Color = Color(0xFF7e8084),
)

val AnanasButtonColors = ButtonColors()

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
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
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

    val stateColor = when {
        connected -> colors.connectedGreenBright
        connecting -> colors.connectingAmber
        else -> colors.idleBlue
    }

    val buttonBgColor by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF0a2e1f)
            connecting -> Color(0xFF2a2013)
            else -> Color(0xFF13202e)
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = stateColor.copy(alpha = if (connected) 0.55f else if (connecting) 0.45f else 0.35f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            connected -> colors.connectedGreenBright
            connecting -> colors.connectingAmber
            else -> colors.idleBlue
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val shadowElev by animateFloatAsState(
        targetValue = when {
            connected -> 18f
            connecting -> 12f
            else -> 6f
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "shadow"
    )

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Ambient aura: one soft radial glow, biased toward the top,
        // breathing slowly in size + opacity together. No stroked ring, no
        // traced outline around the circle's edge -- that's what produced
        // the jagged/torn look, since a Stroke follows the exact geometric
        // path and any softness has to come from alpha alone. A blurred
        // radial gradient behind the button falls off naturally in every
        // direction with no visible edge. ──
        val glowAlphaBase = if (connected) 0.22f else if (connecting) 0.16f else 0.10f
        val glowAlpha = glowAlphaBase + breathe * (glowAlphaBase * 0.55f)
        val glowScale = 1f + breathe * 0.06f

        Canvas(
            modifier = Modifier
                .size(280.dp)
                .scale(glowScale)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height * 0.40f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateColor.copy(alpha = glowAlpha),
                        stateColor.copy(alpha = glowAlpha * 0.35f),
                        stateColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = this.size.minDimension / 2f
                ),
                radius = this.size.minDimension / 2f,
                center = center
            )
        }

        // ── Connecting: rotating gradient arc ──
        if (connecting) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .rotate(spinnerRotation)
            ) {
                drawArc(
                    color = colors.connectingAmber,
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
                    ambientColor = stateColor.copy(alpha = 0.25f),
                    spotColor = stateColor.copy(alpha = 0.35f)
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            stateColor.copy(alpha = 0.18f),
                            buttonBgColor
                        ),
                        center = Offset(85f, 70f),
                        radius = 130f
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
                            color = colors.connectingAmber.copy(alpha = 0.85f),
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Connecting",
                        tint = colors.connectingAmber.copy(alpha = 0.7f),
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
    }
}
