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
            Color.White.copy(alpha = 0.16f),
            Color(0xFF0A0B0F).copy(alpha = 0.55f),
        ),
    )

    val borderColor = Color.White.copy(alpha = 0.28f)

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
