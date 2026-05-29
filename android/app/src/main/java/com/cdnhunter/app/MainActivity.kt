package com.cdnhunter.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdnhunter.app.ui.AppScreen
import com.cdnhunter.app.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CDNHunterTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val viewModel: ScanViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AppScreen(
        state = state,
        config = config,
        onConfigChange = { viewModel.updateConfig(it) },
        onStart = { viewModel.startScan() },
        onStop = { viewModel.stopScan() },
        onCopyIps = {
            val ips = viewModel.copyIps()
            if (ips.isNotBlank()) {
                clipboardManager.setText(AnnotatedString(ips))
                val count = ips.lines().size
                Toast.makeText(context, "$count IP(s) copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No IPs to copy", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

// ── Theme ───────────────────────────────────────────────────────────────────
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

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
