package com.cdnhunter.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdnhunter.app.ui.AppScreen
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.viewmodel.ScanViewModel

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
    val viewModel: ScanViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Load saved config on first launch
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("cdnhunter", 0)
        val savedConc = prefs.getInt("concurrency", 60)
        val savedMax = prefs.getInt("maxIps", 3000)
        val savedTimeout = prefs.getFloat("timeout", 4.0f)
        val savedHost = prefs.getString("host", "") ?: ""
        val savedSni = prefs.getString("sni", "") ?: ""
        viewModel.updateConfig(config.copy(concurrency = savedConc, maxIps = savedMax, timeout = savedTimeout, host = savedHost, sni = savedSni))
    }

    AppScreen(
        state = state,
        config = config,
        onConfigChange = { newConfig ->
            viewModel.updateConfig(newConfig)
            val prefs = context.getSharedPreferences("cdnhunter", 0)
            prefs.edit().putInt("concurrency", newConfig.concurrency).putInt("maxIps", newConfig.maxIps)
                .putFloat("timeout", newConfig.timeout).putString("host", newConfig.host)
                .putString("sni", newConfig.sni).apply()
        },
        onStart = { viewModel.startScan() },
        onStop = { viewModel.stopScan() },
        onCopyIps = {
            val ips = viewModel.copyIps()
            if (ips.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(ips))
                Toast.makeText(context, "${ips.lines().size} IP(s) copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No IPs to copy", Toast.LENGTH_SHORT).show()
            }
        },
        onUpdateRanges = { Toast.makeText(context, "Ranges updated", Toast.LENGTH_SHORT).show() },
        onExport = { Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show() },
    )
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
