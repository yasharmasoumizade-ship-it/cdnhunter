package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
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
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ConfigGenerator
import com.cdnhunter.app.engine.GeoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


// ── Theme Colors ────────────────────────────────────────────────────────────
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

private enum class Tab(val label: String, val icon: @Composable () -> Unit) {
    HOME("Home", { Icon(Icons.Rounded.Home, null, modifier = Modifier.size(22.dp)) }),
    RESULTS("Results", { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(22.dp)) }),
    CONFIG("Config", { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(22.dp)) }),
    TOOLS("Tools", { Icon(Icons.Rounded.Build, null, modifier = Modifier.size(22.dp)) }),
}


// ══════════════════════════════════════════════════════════════════════════════
//  MAIN APP — Bottom Navigation (Home / Results / Config / Tools)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppScreen(
    state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {}, onExport: () -> Unit = {},
) {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF060B1A), Color(0xFF0A0E21), DarkBg)))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                when (currentTab) {
                    Tab.HOME -> HomeScreen(state, config, onConfigChange, onStart, onStop)
                    Tab.RESULTS -> ResultsScreen(state.results)
                    Tab.CONFIG -> ConfigScreen(state.results)
                    Tab.TOOLS -> ToolsScreen(state.results, config, onConfigChange, onStart, onCopyIps, onUpdateRanges, onExport)
                }
            }
            BottomNavBar(currentTab) { currentTab = it }
        }
    }
}


// ── Bottom Nav Bar ──────────────────────────────────────────────────────────
@Composable
private fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().background(CardBg.copy(0.85f))) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val color = if (selected) AccentBlue else TextMuted
                val icons = mapOf(Tab.HOME to Icons.Rounded.Home, Tab.RESULTS to Icons.Rounded.CheckCircle, Tab.CONFIG to Icons.Rounded.Code, Tab.TOOLS to Icons.Rounded.Build)
                Column(Modifier.clickable { onSelect(tab) }.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icons[tab]!!, null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  HOME — Big circular scan button + stats + phase indicator
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeScreen(state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
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
        @Suppress("DEPRECATION") LinearProgressIndicator(progress = state.pct / 100f, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = AccentBlue, trackColor = CardBg)
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
                // Manual IP input
                if (config.cdnProvider == CdnProvider.MANUAL) {
                    Spacer(Modifier.height(10.dp))
                    ConfigField(config.manualIps, { onConfigChange(config.copy(manualIps = it)) }, "IPs: 1.2.3.4, 5.6.7.8")
                }
                // Manual CIDR input
                if (config.cdnProvider == CdnProvider.CIDR) {
                    Spacer(Modifier.height(10.dp))
                    ConfigField(config.manualCidr, { onConfigChange(config.copy(manualCidr = it)) }, "CIDR: 104.16.0.0/12")
                }
                // Max IPs + Workers
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ConfigField("${config.maxIps}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v.coerceIn(10, 50000))) } }, "Max IPs") }
                    Box(Modifier.weight(1f)) { ConfigField("${config.concurrency}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v.coerceIn(1, 500))) } }, "Workers") }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  RESULTS — Card per IP, tap-to-copy, haptic, green flash, inline region map
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ResultsScreen(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    var selectedIp by remember { mutableStateOf<ScanResult?>(null) }
    val geoService = remember { GeoService() }

    if (results.isEmpty()) { EmptyState(Icons.Rounded.Search, "No results yet. Start a scan!"); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(results, key = { it.ip }) { r ->
            var copied by remember { mutableStateOf(false) }
            val bg by animateColorAsState(if (copied) GreenOk.copy(0.12f) else CardBg.copy(0.7f), tween(300), label = "")
            LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }
            val isExpanded = selectedIp?.ip == r.ip

            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, if (copied) GreenOk.copy(0.4f) else Color(0xFF38383A).copy(0.3f), RoundedCornerShape(16.dp))
                    .clickable { clip.setText(AnnotatedString(r.ip)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.ip, fontSize = 14.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(r.cdn, fontSize = 11.sp, color = AccentBlue)
                                if (r.country.isNotBlank()) Text(r.country, fontSize = 11.sp, color = if (r.country == "IR") RedFail else TextSecondary)
                            }
                        }
                        if (copied) Text("✓", fontSize = 18.sp, color = GreenOk, fontWeight = FontWeight.Bold)
                        else Column(horizontalAlignment = Alignment.End) {
                            Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                            if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                // Region expand button
                if (r.ok) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg2.copy(0.5f))
                        .clickable { selectedIp = if (isExpanded) null else r }.padding(8.dp, 6.dp), contentAlignment = Alignment.Center) {
                        Text(if (isExpanded) "▲ Hide Region" else "▼ Show Region", fontSize = 11.sp, color = AccentTeal)
                    }
                }
                // Inline region map
                AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    RegionMapInline(r, geoService)
                }
            }
        }
    }
}


// ── Region Map Inline (Canvas-based, no WebView) ────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionMapInline(r: ScanResult, geoService: GeoService) {
    var geo by remember(r.ip) { mutableStateOf<GeoService.GeoInfo?>(null) }
    var loading by remember(r.ip) { mutableStateOf(true) }
    LaunchedEffect(r.ip) { loading = true; geo = withContext(Dispatchers.IO) { geoService.lookupGeoInfo(r.ip) }; loading = false }

    GlassBox(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp) }
            } else if (geo != null && geo!!.lat != 0.0) {
                // Canvas radar map
                Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A1628)), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height; val gc = Color(0xFF1A3A5C)
                        for (i in 1..6) { drawLine(gc, Offset(0f, h * i / 7), Offset(w, h * i / 7), 0.5f) }
                        for (i in 1..8) { drawLine(gc, Offset(w * i / 9, 0f), Offset(w * i / 9, h), 0.5f) }
                        // Crosshair
                        drawLine(AccentBlue.copy(0.3f), Offset(w / 2, 0f), Offset(w / 2, h), 1f)
                        drawLine(AccentBlue.copy(0.3f), Offset(0f, h / 2), Offset(w, h / 2), 1f)
                    }
                    val pulse by rememberInfiniteTransition(label = "mp").animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "mpf")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(40.dp).scale(pulse), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentBlue.copy(0.1f)))
                            Box(Modifier.size(24.dp).clip(CircleShape).background(AccentBlue.copy(0.25f)))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(AccentBlue))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${geo!!.lat.fmt(2)}°, ${geo!!.lon.fmt(2)}°", fontSize = 10.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(Icons.Rounded.Flag, geo!!.cc, AccentBlue)
                    if (geo!!.city.isNotBlank()) InfoChip(Icons.Rounded.LocationCity, geo!!.city, AccentTeal)
                    if (geo!!.isp.isNotBlank()) InfoChip(Icons.Rounded.Dns, geo!!.isp.take(22), TextSecondary)
                    InfoChip(Icons.Rounded.MyLocation, "${geo!!.lat.fmt(2)}, ${geo!!.lon.fmt(2)}", TextMuted)
                }
            } else { Text("Location unavailable", fontSize = 12.sp, color = TextMuted) }
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  CONFIG TAB — Proxy config generator (VLESS/VMess/Trojan/Sing-Box/Clash)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    val fronting = results.filter { it.ok && it.frontingOk }
    var selectedIp by remember { mutableStateOf(fronting.firstOrNull()?.ip ?: "") }
    var selectedType by remember { mutableStateOf(ProxyType.VLESS) }
    var sni by remember { mutableStateOf(fronting.firstOrNull()?.frontingSni ?: "") }
    var host by remember { mutableStateOf(fronting.firstOrNull()?.frontingHost ?: "") }
    var uuid by remember { mutableStateOf("auto") }
    var config by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text("Config Generator", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        // IP selection
        if (fronting.isNotEmpty()) { item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("SELECT IP", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            fronting.take(6).forEach { r ->
                val sel = selectedIp == r.ip
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (sel) AccentBlue.copy(0.15f) else Color.Transparent)
                    .border(1.dp, if (sel) AccentBlue else Color(0xFF38383A), RoundedCornerShape(10.dp))
                    .clickable { selectedIp = r.ip; sni = r.frontingSni; host = r.frontingHost }.padding(10.dp)) {
                    Row { Text(r.ip, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)); Text("${r.ms}ms", fontSize = 11.sp, color = GreenOk) }
                }
                Spacer(Modifier.height(4.dp))
            }
        } } } } else { item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("IP ADDRESS", fontSize = 11.sp, color = TextSecondary); Spacer(Modifier.height(6.dp))
            ConfigField(selectedIp, { selectedIp = it }, "Enter IP address")
        } } } }

        // Type selector
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("PROTOCOL", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProxyType.entries.forEach { t ->
                    val sel = selectedType == t
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) AccentBlue.copy(0.2f) else CardBg2)
                        .border(1.dp, if (sel) AccentBlue else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { selectedType = t }.padding(10.dp, 7.dp)) {
                        Text(t.label, fontSize = 12.sp, color = if (sel) AccentBlue else TextSecondary)
                    }
                }
            }
        } } }
        // Details
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("DETAILS", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ConfigField(sni, { sni = it }, "SNI (e.g. a248.e.akamai.net)")
            Spacer(Modifier.height(6.dp))
            ConfigField(host, { host = it }, "Host header (your domain)")
            Spacer(Modifier.height(6.dp))
            ConfigField(uuid, { uuid = it }, "UUID (auto = random)")
            Spacer(Modifier.height(6.dp))
            var domain by remember { mutableStateOf("") }
            ConfigField(domain, { domain = it; if (host.isBlank()) host = it }, "Your Domain (e.g. mysite.com)")
        } } }
        // Generate button
        item { Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(AccentBlue, Color(0xFF0050A0))))
            .clickable { config = ConfigGenerator.generate(ProxyConfig(selectedType, selectedIp, 443, sni, host, uuid)) }, contentAlignment = Alignment.Center) {
            Text("Generate Config", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        } }
        // Output
        if (config.isNotBlank()) { item {
            GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Output", fontSize = 13.sp, color = TextSecondary)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (copied) GreenOk.copy(0.15f) else AccentBlue.copy(0.12f))
                        .clickable { clip.setText(AnnotatedString(config)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }.padding(10.dp, 5.dp)) {
                        Text(if (copied) "✓ Copied" else "Copy", fontSize = 12.sp, color = if (copied) GreenOk else AccentBlue)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(config, fontSize = 11.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
            } }
            LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
        } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  TOOLS TAB — Export, Scan Profiles, Update Ranges
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ToolsScreen(
    results: List<ScanResult>, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onCopyIps: () -> Unit, onUpdateRanges: () -> Unit, onExport: () -> Unit,
) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text("Tools", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        // Export section
        item { GlassBox(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text("EXPORT", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton("Copy All", Icons.Rounded.ContentCopy, AccentBlue, Modifier.weight(1f)) {
                    clip.setText(AnnotatedString(healthy.joinToString("\n") { it.ip })); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ToolButton("JSON", Icons.Rounded.DataObject, AccentTeal, Modifier.weight(1f)) {
                    val json = healthy.joinToString(",\n") { """  {"ip":"${it.ip}","ms":${it.ms},"cdn":"${it.cdn}","cc":"${it.country}"}""" }
                    clip.setText(AnnotatedString("[\n$json\n]")); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                ToolButton("v2ray", Icons.Rounded.Link, GreenOk, Modifier.weight(1f)) {
                    val lines = healthy.filter { it.frontingOk }.joinToString("\n") { ConfigGenerator.generate(ProxyConfig(ProxyType.VLESS, it.ip, 443, it.frontingSni, it.frontingHost)) }
                    clip.setText(AnnotatedString(lines)); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    Column { Text("Update CDN Ranges", fontSize = 14.sp, color = TextPrimary); Text("Fetch latest IP ranges from CDN providers", fontSize = 11.sp, color = TextSecondary) }
                }
            }
        } } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════
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
private fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.08f)).padding(8.dp, 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
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

@Composable
private fun ConfigField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg2).padding(2.dp)) {
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

private fun Double.fmt(digits: Int) = "%.${digits}f".format(this)
