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
