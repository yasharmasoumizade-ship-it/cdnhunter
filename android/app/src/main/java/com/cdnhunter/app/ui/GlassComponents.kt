package com.cdnhunter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    fun surfaceBrush(): Brush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.05f),
        ),
    )

    val borderColor = Color.White.copy(alpha = 0.18f)

    /** Apply the glass background + border to any Modifier, e.g. a Box or Column. */
    fun Modifier.glassSurface(shape: Shape = Shape): Modifier = this
        .clip(shape)
        .background(surfaceBrush())
        .border(1.dp, borderColor, shape)

    /** Text field colors tuned for the glass look: transparent fill, soft border. */
    @Composable
    fun textFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AppColors.Accent.copy(alpha = 0.7f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
        focusedLeadingIconColor = AppColors.Accent,
        unfocusedLeadingIconColor = AppColors.TextMid,
        focusedLabelColor = AppColors.Accent,
        unfocusedLabelColor = AppColors.TextMid,
        focusedTextColor = AppColors.TextHi,
        unfocusedTextColor = AppColors.TextHi.copy(alpha = 0.85f),
        cursorColor = AppColors.Accent,
        focusedContainerColor = Color.White.copy(alpha = 0.06f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
    )
}
