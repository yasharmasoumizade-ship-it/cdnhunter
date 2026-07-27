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
    // Read theme mode from AppSettings; default to System
    val themeSetting = remember { AppSettings.theme(context) }
    val amoledMode = remember { AppSettings.amoledMode(context) }
    
    // Convert string setting to ThemeMode enum
    val themeMode = when (themeSetting) {
        AppSettings.THEME_LIGHT  -> ThemeMode.LIGHT
        AppSettings.THEME_DARK   -> ThemeMode.DARK
        else                     -> ThemeMode.SYSTEM  // default & "system"
    }
    
    // Determine if we should use dark colors
    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    // Pick colors based on dark/light and AMOLED setting
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
    
    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color.White,
        secondary = Color(0xFF8B5CF6),
        onSecondary = Color.White,
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF0A0A0A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0A0A0A),
        error = Color(0xFFDC2626),
        onError = Color.White,
        outline = Color(0xFFD4D4D8),
    )
    
    CompositionLocalProvider(
        LocalThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) darkColorScheme else lightColorScheme,
            content = content
        )
    }
}
