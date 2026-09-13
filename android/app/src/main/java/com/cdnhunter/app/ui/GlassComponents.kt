package com.cdnhunter.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared glassmorphism styling: a translucent, frosted-glass surface with a thin
 * glowing border. Used for auth text fields and cards across Onboarding, Login,
 * and Sign Up so the video background stays visible through the UI.
 */
object Glass {
    val Shape: Shape = RoundedCornerShape(16.dp)
    val FieldShape: Shape = RoundedCornerShape(14.dp)
    val CardShape: Shape = RoundedCornerShape(28.dp)

    fun surfaceBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.16f),
            Color(0xFF0A0B0F).copy(alpha = 0.55f),
        ),
    )

    val borderColor = Color.White.copy(alpha = 0.35f)

    /** Apply the glass background + border to any Modifier, e.g. a Box or Column.
     *  Pass [focused] = true to swap the border to the accent color, e.g. when a
     *  wrapped text field has focus -- this is the single source of the visible
     *  border, so the wrapped field's own border must stay Color.Transparent
     *  (see [textFieldColors]) or a double ring appears. */
    fun Modifier.glassSurface(shape: Shape = Shape, focused: Boolean = false): Modifier = this
        .clip(shape)
        .background(surfaceBrush())
        .border(1.dp, if (focused) AppColors.Accent.copy(alpha = 0.7f) else borderColor, shape)

    /** Text field colors tuned for the glass look: transparent fill, soft border.
     *  Both border colors are transparent here because the visible border comes
     *  from [glassSurface]'s own Modifier.border() -- letting OutlinedTextField
     *  draw its own border too produced a double-border artifact (an extra green
     *  ring outside the glass card whenever a field gained focus). */
    @Composable
    fun textFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedLeadingIconColor = AppColors.Accent,
        unfocusedLeadingIconColor = AppColors.TextMid,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.TextMid,
        focusedTextColor = AppColors.TextHi,
        unfocusedTextColor = AppColors.TextHi.copy(alpha = 0.85f),
        cursorColor = AppColors.Accent,
        focusedContainerColor = Color.White.copy(alpha = 0.10f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.30f),
    )
}

/**
 * A rotating teal-gradient ring spinner, used everywhere the app shows a loading
 * state (button spinners, the auth video-loading screen) instead of the plain
 * Material CircularProgressIndicator -- gives loading moments a consistent,
 * on-brand look with a soft glow instead of a flat single-color arc.
 */
@Composable
fun GlowSpinner(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp, strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glowSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "rotation",
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer(rotationZ = rotation),
    ) {
        val sweep = androidx.compose.ui.graphics.Brush.sweepGradient(
            colors = listOf(
                AppColors.Accent.copy(alpha = 0f),
                AppColors.Accent.copy(alpha = 0.35f),
                AppColors.AccentBright,
            ),
        )
        drawArc(
            brush = sweep,
            startAngle = 0f,
            sweepAngle = 300f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

