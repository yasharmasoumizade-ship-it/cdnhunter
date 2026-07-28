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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════
// HELPER: Linear interpolation for smooth color transitions
// ═══════════════════════════════════════════════════════════════════════
fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

// ═══════════════════════════════════════════════════════════════════════
// CONNECT BUTTON - Premium animated power toggle
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
 * Premium Connect Button with smooth animations and state transitions
 *
 * States:
 * - OFF (grey power icon)
 * - CONNECTING (rotating arc + progress)
 * - ON (green power icon + breathing rings)
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
    
    // ── Animation States ──
    
    // Ring pulse (when connected) - breathing effect
    val breathePulse by infinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    
    // Outer ring scale (connected state)
    val outerRingScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerRing"
    )
    
    // Connecting spinner rotation
    val spinnerRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "spinner"
    )
    
    // Connecting pulse opacity
    val pulseOpacity by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Click press animation
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 180, easing = EaseInOutQuad),
        finishedListener = { if (isPressed) isPressed = false },
        label = "pressScale"
    )
    
    // ── Icon transformation animations ──
    
    // Icon color transition (grey → green)
    val iconColorTransition by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "iconColor"
    )
    
    // Icon glow when transitioning to connected
    val iconGlowAlpha by animateFloatAsState(
        targetValue = if (connected) 0f else -0.2f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "iconGlow"
    )
    
    // Icon rotation during state change (360 spin)
    val iconRotation by animateFloatAsState(
        targetValue = if (connected) 360f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "iconRotate"
    )
    
    // Icon scale pulse when connecting → connected transition
    val iconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconScale"
    )
    
    // Icon shadow intensity
    val iconShadowAlpha by animateFloatAsState(
        targetValue = if (connected) 0.6f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "iconShadow"
    )
    
    // Background glow intensity
    val bgGlowIntensity by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "bgGlow"
    )
    
    Box(
        modifier = modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── LAYER 1: Outer breathing rings (when connected) ──
        if (connected) {
            // Primary outer ring
            Canvas(
                modifier = Modifier
                    .size(280.dp)
                    .scale(outerRingScale)
            ) {
                val radius = size.minDimension / 2f - 12.dp.toPx()
                val strokeWidth = 1.2.dp.toPx()
                
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.25f),
                    radius = radius,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            // Secondary breathing ring (inner)
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .scale(breathePulse)
            ) {
                val radius = size.minDimension / 2f - 10.dp.toPx()
                drawCircle(
                    color = colors.accentGreen.copy(alpha = 0.15f * breathePulse),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            // Radial ripple effect
            repeat(3) { index ->
                val rippleProgress by infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearOutSlowInEasing),
                        initialStartOffset = StartOffset(index * 300)
                    ),
                    label = "ripple_$index"
                )
                
                Canvas(
                    modifier = Modifier.size(280.dp)
                ) {
                    val maxRadius = size.minDimension / 2f
                    val radius = maxRadius * (0.3f + rippleProgress * 0.7f)
                    val alpha = (1f - rippleProgress) * 0.4f
                    
                    drawCircle(
                        color = colors.accentGreen.copy(alpha = alpha),
                        radius = radius,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }
        
        // ── LAYER 2: Connecting spinner ──
        if (connecting) {
            // Outer rotating ring
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .rotate(spinnerRotation)
            ) {
                // Draw multi-segment arc for smooth rotation
                val segmentAngle = 40f
                val startAngle = 0f
                
                drawArc(
                    color = colors.accentGreen,
                    startAngle = startAngle,
                    sweepAngle = segmentAngle,
                    useCenter = false,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )
                
                drawArc(
                    color = colors.accentGreen.copy(alpha = 0.5f),
                    startAngle = startAngle + 180f,
                    sweepAngle = 30f,
                    useCenter = false,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            // Connecting dots animation
            repeat(3) { dot ->
                val dotScale by infinite.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        initialStartOffset = StartOffset(dot * 200)
                    ),
                    label = "dot_$dot"
                )
                
                Canvas(
                    modifier = Modifier.size(140.dp)
                ) {
                    val angle = (dot * 120f) * PI / 180f
                    val x = center.x + cos(angle) * 50.dp.toPx() * dotScale
                    val y = center.y + sin(angle) * 50.dp.toPx() * dotScale
                    val dotSize = 4.5.dp.toPx()
                    
                    drawCircle(
                        color = colors.accentGreen.copy(alpha = 0.6f * dotScale),
                        radius = dotSize,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        
        // ── LAYER 3: Center glow (when connected) ──
        if (connected && !connecting) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.accentGreen.copy(alpha = 0.08f * breathePulse * bgGlowIntensity),
                                Color.Transparent
                            ),
                            radius = 150f
                        )
                    )
            )
        }
        
        // Icon glow layer (behind icon, during transition)
        if (iconGlowAlpha > 0f) {
            Canvas(
                modifier = Modifier
                    .size(120.dp)
                    .scale(1.3f)
            ) {
                drawCircle(
                    color = colors.accentGreen.copy(alpha = iconGlowAlpha * 0.4f),
                    radius = size.minDimension / 2f
                )
            }
        }
        
        // ── LAYER 4: Main button background ──
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .shadow(
                    elevation = if (connected) 16.dp else 8.dp,
                    shape = CircleShape,
                    clip = false
                )
                .background(
                    brush = if (connected) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1a1a1e),
                                Color(0xFF0f0f12)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(170f, 170f)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF16161a),
                                Color(0xFF0d0d10)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(170f, 170f)
                        )
                    }
                )
                .border(
                    width = 1.5.dp,
                    color = if (connected) {
                        colors.accentGreen.copy(alpha = 0.3f)
                    } else {
                        colors.textMuted.copy(alpha = 0.15f)
                    },
                    shape = CircleShape
                )
                .clickable(enabled = !connecting) {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                // Connecting state: spinner + text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(pulseOpacity)
                ) {
                    CircularProgressIndicator(
                        color = colors.accentGreen,
                        strokeWidth = 2.4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connecting",
                        fontSize = 10.sp,
                        color = colors.textMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Connected or Off: power icon with COLOR TRANSITION
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(iconScale)
                        .rotate(iconRotation),
                    contentAlignment = Alignment.Center
                ) {
                    // Icon shadow (glow when connected)
                    if (iconShadowAlpha > 0f) {
                        Canvas(
                            modifier = Modifier
                                .size(85.dp)
                                .blur(12.dp)
                        ) {
                            drawCircle(
                                color = colors.accentGreen.copy(
                                    alpha = iconShadowAlpha * 0.5f
                                ),
                                radius = size.minDimension / 2f
                            )
                        }
                    }
                    
                    // Animated icon with color transition
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (connected) "Disconnect" else "Connect",
                        tint = Color(
                            red = lerp(
                                colors.textMuted.red,
                                colors.accentGreen.red,
                                iconColorTransition
                            ),
                            green = lerp(
                                colors.textMuted.green,
                                colors.accentGreen.green,
                                iconColorTransition
                            ),
                            blue = lerp(
                                colors.textMuted.blue,
                                colors.accentGreen.blue,
                                iconColorTransition
                            ),
                            alpha = 1f
                        ),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }
        
        // ── Status indicator ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
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
