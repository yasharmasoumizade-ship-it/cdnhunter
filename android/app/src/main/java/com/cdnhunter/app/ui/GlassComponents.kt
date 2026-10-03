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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

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
            Color.White.copy(alpha = 0.26f),
            Color(0xFF0A0B0F).copy(alpha = 0.40f),
        ),
    )

    val borderColor = Color.White.copy(alpha = 0.45f)

    /** Apply the glass background + border to any Modifier, e.g. a Box or Column.
     *  Pass [focused] = true to swap the border to the accent color, e.g. when a
     *  wrapped text field has focus -- this is the single source of the visible
     *  border, so the wrapped field's own border must stay Color.Transparent
     *  (see [textFieldColors]) or a double ring appears. [tintAlpha] darkens the
     *  blurred content underneath -- the auth screens' cards want that (0.35, the
     *  default) to stay legible over bright video, but a single glass layer over
     *  something already dark (the connect button, over a flag) wants much less
     *  or it reads as a muddy tint rather than clear glass; such a caller can pass
     *  a lower value. */
    fun Modifier.glassSurface(
        shape: Shape = Shape,
        focused: Boolean = false,
        hazeState: HazeState? = null,
        tintAlpha: Float = 0.35f,
    ): Modifier = this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        // Haze throws IllegalArgumentException("backgroundColor not
                        // specified") if this is left at its Color.Unspecified default --
                        // it's the color drawn behind the blurred content on platforms/API
                        // levels where real blurring isn't available, so it has to be
                        // opaque. Matches the app's own near-black background.
                        backgroundColor = Color(0xFF0A0B0F),
                        tints = listOf(HazeTint(Color.Black.copy(alpha = tintAlpha))),
                        blurRadius = 22.dp,
                        noiseFactor = 0.08f,
                    ),
                )
            } else {
                Modifier.background(surfaceBrush())
            },
        )
        // Inset look: an inner shadow along the top/left edge instead of a bright
        // outer border, so the card reads as pressed into the background rather
        // than floating above it.
        .drawWithContent {
            drawContent()
            val insetColor = Color.Black.copy(alpha = 0.45f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(insetColor, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.4f, size.height * 0.4f),
                ),
            )
        }
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
