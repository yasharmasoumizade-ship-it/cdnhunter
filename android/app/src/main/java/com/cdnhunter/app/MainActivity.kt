package com.cdnhunter.app

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.cdnhunter.app.ui.AppScreen
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
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color.White,
        secondary = Color(0xFF8B5CF6),
        onSecondary = Color.White,
        background = Color.Black,
        onBackground = Color(0xFFFAFAFA),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFFAFAFA),
        error = Color(0xFFEF4444),
        onError = Color.White,
        outline = Color(0xFF222222),
    )
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}
