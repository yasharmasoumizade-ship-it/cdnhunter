package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.VpnService
import androidx.compose.ui.platform.LocalContext
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ConfigGenerator
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// == Theme Colors ==
val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF1C1C1E)
val CardBg2 = Color(0xFF2C2C2E)
val AccentBlue = Color(0xFF0A84FF)
val AccentTeal = Color(0xFF64D2FF)
val GreenOk = Color(0xFF30D158)
val RedFail = Color(0xFFFF453A)
val YellowWarn = Color(0xFFFFD60A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextMuted = Color(0xFF48484A)

private enum class Tab(val label: String) {
    VPN("VPN"), SCANNER("Scanner"), RESULTS("Results"), TOOLS("Tools")
}

// == MAIN APP ==
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScreen(
    state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {}, onExport: () -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = 0) { Tab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    // Auto-switch to Results tab when scan completes
    LaunchedEffect(state.phase, state.results.size) {
        if (state.phase == ScanPhase.DONE && state.results.isNotEmpty()) {
            pagerState.animateScrollToPage(Tab.RESULTS.ordinal)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF060B1A), Color(0xFF0A0E21), DarkBg)))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (Tab.entries[page]) {
                            Tab.VPN -> VpnTab()
                            Tab.SCANNER -> ScannerTab(state, config, onConfigChange, onStart, onStop)
                            Tab.RESULTS -> ResultsTab(state.results)
                            Tab.TOOLS -> ToolsTab(state.results, config, onConfigChange, onStart, onCopyIps, onUpdateRanges, onExport)
                        }
                    }
                }
            }
            BottomNavBar(Tab.entries[pagerState.currentPage]) { tab ->
                coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            }
        }
    }
}

// == Bottom Nav Bar ==
@Composable
private fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val icons = mapOf(
        Tab.VPN to Icons.Rounded.Shield,
        Tab.SCANNER to Icons.Rounded.Radar,
        Tab.RESULTS to Icons.Rounded.List,
        Tab.TOOLS to Icons.Rounded.Build
    )
    Box(Modifier.fillMaxWidth().background(CardBg.copy(0.85f))) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val color = if (selected) AccentBlue else TextMuted
                Column(Modifier.clickable { onSelect(tab) }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icons[tab]!!, null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// == VPN TAB ==
@Composable
private fun VpnTab() {
    val context = LocalContext.current
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var connected by remember { mutableStateOf(CdnVpnService.isRunning.get()) }
    var connecting by remember { mutableStateOf(false) }
    var configUri by remember { mutableStateOf("") }
    var autoIp by remember { mutableStateOf(true) }
    var fragment by remember {
        mutableStateOf(context.getSharedPreferences("cdnhunter_vpn", 0).getBoolean("fragment_enabled", true))
    }
    var parsedInfo by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            connected = CdnVpnService.isRunning.get()
            if (connected) connecting = false
            delay(1000)
        }
    }
    LaunchedEffect(connecting) {
        if (connecting) {
            delay(15000)
            if (!CdnVpnService.isRunning.get()) {
                connecting = false
            }
        }
    }

    val statusText = when {
        connecting -> "Connecting..."
        connected -> "Connected"
        else -> "Disconnected"
    }
    val buttonColor = when {
        connecting -> AccentBlue
        connected -> GreenOk
        else -> AccentBlue
    }
    val downloadBytes = if (connected) CdnVpnService.downloadBytes else 0L
    val uploadBytes = if (connected) CdnVpnService.uploadBytes else 0L

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("VPN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center) {
            if (connecting) {
                val pulse by rememberInfiniteTransition(label = "vpn_pulse").animateFloat(
                    1f, 1.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "vpn_pf"
                )
                Box(Modifier.size(150.dp).scale(pulse).clip(CircleShape).background(buttonColor.copy(0.15f)))
            }
            Box(
                Modifier.size(120.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(buttonColor, buttonColor.copy(0.7f))))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (connecting) {
                            CdnVpnService.stop(context)
                            connecting = false
                            connected = false
                            return@clickable
                        }
                        if (connected) {
                            CdnVpnService.stop(context)
                            connected = false
                        } else {
                            if (configUri.isBlank()) {
                                android.widget.Toast.makeText(context, "Paste a config first", android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            context.getSharedPreferences("cdnhunter_vpn", 0).edit()
                                .putString("user_config", configUri).apply()
                            connecting = true
                            try {
                                val mainActivity = context as? com.cdnhunter.app.MainActivity
                                if (mainActivity != null) {
                                    mainActivity.requestVpnPermissionAndConnect()
                                } else {
                                    CdnVpnService.start(context)
                                }
                            } catch (e: Exception) {
                                connecting = false
                                android.widget.Toast.makeText(context, "Failed: ${e.message?.take(40)}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (connected) Icons.Rounded.Shield else Icons.Rounded.PowerSettingsNew,
                    null, tint = Color.White, modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(statusText, fontSize = 16.sp, color = when { connected -> GreenOk; connecting -> YellowWarn; else -> TextSecondary }, fontWeight = FontWeight.Medium)
        if (connecting) {
            Spacer(Modifier.height(8.dp))
            Text("Tap to cancel", fontSize = 11.sp, color = TextMuted)
        }
        if (CdnVpnService.lastError.isNotBlank() && !connected && !connecting) {
            Spacer(Modifier.height(8.dp))
            Text(CdnVpnService.lastError, fontSize = 11.sp, color = RedFail)
        }
        Spacer(Modifier.height(32.dp))

        GlassBox(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("IMPORT CONFIG", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ConfigField(configUri, { configUri = it }, "trojan/vless/vmess URI")
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(AccentBlue)
                            .clickable {
                                val clipText = try { clip.getText()?.text ?: "" } catch (_: Exception) { "" }
                                if (clipText.startsWith("trojan://") || clipText.startsWith("vless://") || clipText.startsWith("vmess://")) {
                                    configUri = clipText
                                }
                                if (configUri.isNotBlank()) {
                                    val ob = ConfigUriParser.parseToOutbound(configUri)
                                    if (ob != null) {
                                        val proto = ob.optString("protocol", "?")
                                        val settings = ob.optJSONObject("settings")
                                        val addr = settings?.optJSONArray("vnext")?.optJSONObject(0)?.optString("address")
                                            ?: settings?.optJSONArray("servers")?.optJSONObject(0)?.optString("address") ?: "?"
                                        val port = settings?.optJSONArray("vnext")?.optJSONObject(0)?.optInt("port", 443)
                                            ?: settings?.optJSONArray("servers")?.optJSONObject(0)?.optInt("port", 443) ?: 443
                                        val ss = ob.optJSONObject("streamSettings")
                                        val sni = ss?.optJSONObject("tlsSettings")?.optString("serverName", "") ?: ""
                                        val net = ss?.optString("network", "tcp") ?: "tcp"
                                        parsedInfo = "$proto | $net | $addr:$port" + if (sni.isNotBlank()) " | SNI: $sni" else ""
                                        context.getSharedPreferences("cdnhunter_vpn", 0).edit()
                                            .putString("user_config", configUri).apply()
                                    } else {
                                        parsedInfo = "Invalid config"
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (parsedInfo.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(parsedInfo, fontSize = 12.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassBox(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-IP", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Use best scanned IP automatically", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = autoIp, onCheckedChange = { autoIp = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, uncheckedTrackColor = CardBg2)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassBox(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fragment (DPI bypass)", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Splits TLS hello. Turn OFF for xhttp/gRPC if it won't connect", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = fragment,
                    onCheckedChange = {
                        fragment = it
                        context.getSharedPreferences("cdnhunter_vpn", 0).edit()
                            .putBoolean("fragment_enabled", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, uncheckedTrackColor = CardBg2)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficCard("Download", downloadBytes, AccentTeal, Modifier.weight(1f))
            TrafficCard("Upload", uploadBytes, AccentBlue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // Show Config button (for debugging)
        var showConfig by remember { mutableStateOf(false) }
        var generatedConfig by remember { mutableStateOf("") }
        GlassBox(Modifier.fillMaxWidth().clickable {
            val prefs = context.getSharedPreferences("cdnhunter_vpn", 0)
            val saved = prefs.getString("user_config", "") ?: ""
            if (saved.isNotBlank()) {
                generatedConfig = com.cdnhunter.app.vpn.VpnConfigBuilder.buildConfig(context)
            }
            showConfig = !showConfig
        }) {
            Column(Modifier.padding(14.dp)) {
                Text("Show Generated Config", fontSize = 13.sp, color = AccentTeal, fontWeight = FontWeight.Medium)
                if (showConfig && generatedConfig.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(generatedConfig, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Show Xray runtime log (for debugging connection failures)
        var showLog by remember { mutableStateOf(false) }
        var xrayLogText by remember { mutableStateOf("") }
        GlassBox(Modifier.fillMaxWidth().clickable {
            xrayLogText = CdnVpnService.xrayLog
            showLog = !showLog
        }) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Xray Log", fontSize = 13.sp, color = YellowWarn, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(CardBg2).clickable {
                        clip.setText(AnnotatedString(CdnVpnService.xrayLog.ifBlank { "(empty)" }))
                    }.padding(8.dp, 4.dp)) {
                        Text("Copy", fontSize = 11.sp, color = AccentTeal)
                    }
                }
                if (showLog) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        xrayLogText.ifBlank { "No log yet. Connect first, then tap to refresh." },
                        fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TrafficCard(label: String, bytes: Long, color: Color, modifier: Modifier) {
    GlassBox(modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(formatBytes(bytes), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

// == SCANNER TAB ==
@Composable
private fun ScannerTab(state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("CDN Hunter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(if (state.running) state.phaseDetail.take(30) else "Ready to scan", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(32.dp))
        // Big scan button
        Box(contentAlignment = Alignment.Center) {
            if (state.running) {
                val pulse by rememberInfiniteTransition(label = "r").animateFloat(1f, 1.2f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "rf")
                Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(AccentBlue.copy(0.1f)))
            }
            Box(
                Modifier.size(120.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(AccentBlue, Color(0xFF0050A0))))
                    .clickable { if (state.running) onStop() else onStart() },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (state.running) Icons.Rounded.Stop else Icons.Rounded.Radar, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (state.running) "Tap to Stop" else "Tap to Start", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp))

        // Stats cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard("${state.scanned}", "Scanned", AccentBlue, Modifier.weight(1f))
            GlassCard("${state.healthy}", "Healthy", GreenOk, Modifier.weight(1f))
            GlassCard("${state.failed}", "Failed", RedFail, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        // Progress
        @Suppress("DEPRECATION")
        LinearProgressIndicator(progress = state.pct / 100f, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = AccentBlue, trackColor = CardBg)
        Spacer(Modifier.height(4.dp))
        Text("${state.pct}% • ${state.source}", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(16.dp))
        // Phase indicator
        PhaseIndicator(state.phase)
        Spacer(Modifier.height(16.dp))
        // Provider selector
        GlassBox(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("CDN Provider", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CdnProvider.entries.forEach { p ->
                        val sel = config.cdnProvider == p
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) AccentBlue.copy(0.2f) else CardBg2).clickable { onConfigChange(config.copy(cdnProvider = p)) }.padding(10.dp, 6.dp)) {
                            Text(p.label, fontSize = 12.sp, color = if (sel) AccentBlue else TextSecondary)
                        }
                    }
                }
                if (config.cdnProvider == CdnProvider.MANUAL) {
                    Spacer(Modifier.height(10.dp))
                    ConfigField(config.manualIps, { onConfigChange(config.copy(manualIps = it)) }, "IPs: 1.2.3.4, 5.6.7.8")
                }
                if (config.cdnProvider == CdnProvider.CIDR) {
                    Spacer(Modifier.height(10.dp))
                    ConfigField(config.manualCidr, { onConfigChange(config.copy(manualCidr = it)) }, "CIDR: 104.16.0.0/12")
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ConfigField("${config.maxIps}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v)) } }, "Max IPs") }
                    Box(Modifier.weight(1f)) { ConfigField("${config.concurrency}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v)) } }, "Workers") }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// == RESULTS TAB ==
@Composable
private fun ResultsTab(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }

    if (results.isEmpty()) {
        EmptyState(Icons.Rounded.Search, "No results yet. Start a scan!")
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)
        ) {
            items(results, key = { it.ip }) { r ->
                var copied by remember { mutableStateOf(false) }
                val bg by animateColorAsState(if (copied) GreenOk.copy(0.12f) else CardBg.copy(0.7f), tween(300), label = "")
                LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }

                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
                        .border(1.dp, if (copied) GreenOk.copy(0.4f) else Color(0xFF38383A).copy(0.3f), RoundedCornerShape(16.dp))
                        .clickable {
                            clip.setText(AnnotatedString(r.ip))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copied = true
                        }
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.ip, fontSize = 14.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            // Region as text only: "CDN . Country - City"
                            val regionText = buildString {
                                append(r.cdn)
                                if (r.country.isNotBlank()) {
                                    append(" \u2022 ${r.country}")
                                    if (r.city.isNotBlank()) append(" - ${r.city}")
                                }
                            }
                            Text(regionText, fontSize = 11.sp, color = AccentBlue)
                        }
                        if (copied) {
                            Text("\u2713", fontSize = 18.sp, color = GreenOk, fontWeight = FontWeight.Bold)
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                                if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // FAB to copy all healthy IPs
        if (healthy.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    clip.setText(AnnotatedString(healthy.joinToString("\n") { it.ip }))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all IPs")
            }
        }
    }
}

// == TOOLS TAB ==
@Composable
private fun ToolsTab(
    results: List<ScanResult>, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onCopyIps: () -> Unit, onUpdateRanges: () -> Unit, onExport: () -> Unit,
) {
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text("Tools", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        // Export section
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("EXPORT", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Copy All", Icons.Rounded.ContentCopy, AccentBlue, Modifier.weight(1f)) {
                    clip.setText(AnnotatedString(healthy.joinToString("\n") { it.ip }))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ToolButton("JSON", Icons.Rounded.DataObject, AccentTeal, Modifier.weight(1f)) {
                    val json = healthy.joinToString(",\n") { """  {"ip":"${it.ip}","ms":${it.ms},"cdn":"${it.cdn}","cc":"${it.country}"}""" }
                    clip.setText(AnnotatedString("[\n$json\n]"))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ToolButton("v2ray", Icons.Rounded.Link, GreenOk, Modifier.weight(1f)) {
                    val lines = healthy.filter { it.frontingOk }.joinToString("\n") { ConfigGenerator.generate(ProxyConfig(ProxyType.VLESS, it.ip, 443, it.frontingSni, it.frontingHost)) }
                    clip.setText(AnnotatedString(lines))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${healthy.size} healthy IPs available", fontSize = 11.sp, color = TextMuted)
        } } }
        // Scan Profiles
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("SCAN PROFILES", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            listOf(ScanProfile.QUICK, ScanProfile.NORMAL, ScanProfile.DEEP).forEach { profile ->
                val sel = config.maxIps == profile.config.maxIps && config.concurrency == profile.config.concurrency
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) AccentBlue.copy(0.12f) else CardBg2)
                    .border(1.dp, if (sel) AccentBlue.copy(0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onConfigChange(profile.config) }.padding(12.dp)) {
                    Column {
                        Text(profile.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (sel) AccentBlue else TextPrimary)
                        Text(profile.desc, fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        } } }
        // Update Ranges
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("MAINTENANCE", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg2)
                .clickable { onUpdateRanges() }.padding(14.dp, 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Update CDN Ranges", fontSize = 14.sp, color = TextPrimary)
                        Text("Fetch latest IP ranges from CDN providers", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        } } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// == SHARED COMPONENTS ==
@Composable
private fun GlassCard(value: String, label: String, color: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(0.08f)).border(1.dp, color.copy(0.15f), RoundedCornerShape(16.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun GlassBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(CardBg.copy(0.7f)).border(1.dp, Color(0xFF38383A).copy(0.3f), RoundedCornerShape(18.dp)), content = content)
}

@Composable
private fun PhaseIndicator(current: ScanPhase) {
    val phases = ScanPhase.entries.filter { it != ScanPhase.IDLE }
    val idx = phases.indexOf(current)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        phases.forEachIndexed { i, p ->
            val done = i < idx; val active = i == idx
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(when { done -> GreenOk.copy(0.12f); active -> AccentBlue.copy(0.15f); else -> CardBg2 }).padding(10.dp, 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) Icon(Icons.Rounded.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(12.dp))
                    Text(p.label, fontSize = 11.sp, color = when { done -> GreenOk; active -> AccentBlue; else -> TextMuted })
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(0.1f)).clickable { onClick() }.padding(12.dp, 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg2)) {
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = TextMuted) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace), singleLine = true)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 14.sp, color = TextSecondary)
        }
    }
}
