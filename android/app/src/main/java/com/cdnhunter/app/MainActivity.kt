package com.cdnhunter.app

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.cdnhunter.app.ui.AppScreen
import com.cdnhunter.app.ui.LocalThemeMode
import com.cdnhunter.app.ui.ThemeMode
import com.cdnhunter.app.vpn.AppSettings
import com.cdnhunter.app.vpn.CdnVpnService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Permission granted or denied — try to start VPN anyway
        CdnVpnService.start(this)
    }

    fun requestVpnPermissionAndConnect() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // Already have permission
            CdnVpnService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both system bars are painted the app's chrome colour in themes.xml
        // (#0B0B0D), which means their glyphs have to be the light set: white on that
        // background is 18.4:1, the dark set about 1.2:1. The theme attribute says so
        // too, but some OEM skins derive the status bar's appearance from the phone's
        // own light/dark setting rather than from the app's theme, and this app is
        // always dark regardless of it (see CDNHunterTheme). Stating it here through
        // WindowCompat is what holds on those devices, and it is the only way to set
        // the navigation bar's glyphs at all below API 27, where
        // windowLightNavigationBar does not exist.
        // Draw under both system bars. The home hero is one full-bleed image now — the
        // active server's flag, edge to edge — and "edge to edge" has to include the
        // strip the clock and the battery sit in, or the artwork stops at a horizontal
        // line 28dp down the screen and the whole panel reads as a card again.
        //
        // The status bar is painted transparent in themes.xml to go with this; the
        // navigation bar keeps the app's chrome colour, because nothing at the foot of
        // the screen wants to be seen through. Every screen that is not Home pads itself
        // off the bars with statusBarsPadding()/navigationBarsPadding() — Home does it
        // per row, which is what lets the flag run behind the glyphs while the top bar
        // still clears them.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CDNHunterTheme {
                MainContent(this)
            }
        }
    }
}

@Composable
fun MainContent(activity: MainActivity) {
    AppScreen()
}

@Composable
fun CDNHunterTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The whole UI (every Ananas* color in AppScreen.kt) is hardcoded dark
    // regardless of this theme -- the old Light/Auto option was removed from
    // Settings, because the app was never actually designed to look right in
    // a light scheme. Previously this still switched to lightColorScheme()
    // whenever the PHONE's system theme was light (ThemeMode.SYSTEM ->
    // isSystemInDarkTheme()), silently mismatching the always-dark UI.
    // Material3's default ripple/indication color derives from the active
    // color scheme's content color, so on a light-system phone every tap
    // rendered a dark/near-black ripple box on top of visually dark rows --
    // looked exactly like a rendering glitch ("black box on tap"). Always
    // using the dark scheme here keeps it in sync with the UI that's
    // actually drawn, regardless of the phone's own system theme.
    val amoledMode = remember { AppSettings.amoledMode(context) }
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color.White,
        secondary = Color(0xFF8B5CF6),
        onSecondary = Color.White,
        background = if (amoledMode) Color(0xFF000000) else Color(0xFF0A0A0A),  // pure black vs dark gray
        onBackground = Color(0xFFFAFAFA),
        surface = if (amoledMode) Color(0xFF000000) else Color(0xFF0A0A0A),     // pure black vs dark gray
        onSurface = Color(0xFFFAFAFA),
        error = Color(0xFFEF4444),
        onError = Color.White,
        outline = Color(0xFF222222),
    )

    CompositionLocalProvider(
        LocalThemeMode provides ThemeMode.DARK
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            content = content
        )
    }
}
