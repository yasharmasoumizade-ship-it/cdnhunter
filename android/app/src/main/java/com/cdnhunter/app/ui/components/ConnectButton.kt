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
 * Connect button with three states:
 * - OFF: muted grey power icon, no rings
 * - CONNECTING: single rotating arc + spinner + label
 * - ON: green power icon, two thin static-alpha breathing rings
 */
@Composable
fun PremiumConnectButton(
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    colors: ButtonColors = AnanasButtonColors,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "powerButton")

    // Single breathing scale shared by both rings — one source of motion
    // instead of several rings/ripples drifting out of sync with each other.
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val spinnerRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "spinner"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(150, easing = EaseInOutQuad),
        finishedListener = { if (isPressed) isPressed = false },
        label = "pressScale"
    )

    // One clean color crossfade for the icon — no per-channel manual lerp,
    // no rotation spin, no separate glow/shadow layers riding along with it.
    val iconColor by animateColorAsState(
        targetValue = if (connected) colors.accentGreen else colors.textMuted,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "iconColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (connected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Rings (connected only) — two thin strokes, one shared breathing motion ──
        if (connected) {
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .scale(breathe)
            ) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.22f),
                    radius = size.minDimension / 2f - 10.dp.toPx(),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Canvas(modifier = Modifier.size(200.dp)) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.14f),
                    radius = size.minDimension / 2f - 8.dp.toPx(),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Connecting: one rotating arc, nothing else ──
        if (connecting) {
            Canvas(
                modifier = Modifier
                    .size(200.dp)
                    .rotate(spinnerRotation)
            ) {
                drawArc(
                    color = colors.accentGreen,
                    startAngle = 0f,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── Main button ──
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .shadow(elevation = if (connected) 10.dp else 6.dp, shape = CircleShape, clip = false)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF18181c), Color(0xFF0e0e11)),
                        start = Offset(0f, 0f),
                        end = Offset(170f, 170f)
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = if (connected) colors.accentGreen.copy(alpha = 0.35f) else colors.textMuted.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .clickable(enabled = !connecting) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = colors.accentGreen,
                        strokeWidth = 2.4.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connecting", fontSize = 10.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (connected) "Disconnect" else "Connect",
                    tint = iconColor,
                    modifier = Modifier
                        .size(72.dp)
                        .scale(iconScale)
                )
            }
        }

        // ── Status label ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            Text(
                text = when {
                    connected -> "CONNECTED"
                    connecting -> "..."
                    else -> "DISCONNECTED"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = when {
                    connected -> colors.accentGreen
                    connecting -> colors.accentGreen.copy(alpha = 0.6f)
                    else -> colors.textMuted
                }
            )
        }
    }
}
