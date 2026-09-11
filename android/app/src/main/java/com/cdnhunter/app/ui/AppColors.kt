package com.cdnhunter.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Shared design tokens for the app: a monochrome teal/green "galaxy" palette.
 * Used across Onboarding, Login, and Sign Up so all auth-related screens
 * stay visually consistent.
 */
object AppColors {
    val BgDark = Color(0xFF0A0B0F)
    val BgDarkTeal = Color(0xFF0A1512)      // deep teal-black for gradient backgrounds
    val FieldBg = Color(0xFF15171E)
    val FieldBorder = Color(0xFF23262F)

    val Accent = Color(0xFF4DB6AC)          // single teal accent — replaces old blue Accent
    val AccentBright = Color(0xFF6EE7D8)    // lighter teal for highlights/glow

    val TextHi = Color(0xFFF6F7F9)
    val TextMid = Color(0xFF8B8E98)

    val ErrorRed = Color(0xFFEF4444)
    val SuccessGreen = Color(0xFF22C55E)
}
