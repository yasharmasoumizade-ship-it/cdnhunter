package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ConfigGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colors ──────────────────────────────────────────────────────────────────
val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF1C1C1E)
val CardBg2 = Color(0xFF2C2C2E)
val BorderColor = Color(0xFF38383A)
val AccentBlue = Color(0xFF0A84FF)
val AccentTeal = Color(0xFF64D2FF)
val GreenOk = Color(0xFF30D158)
val RedFail = Color(0xFFFF453A)
val YellowWarn = Color(0xFFFFD60A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextMuted = Color(0xFF48484A)


// ── Main App with Drawer ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {}, onExport: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(NavScreen.SCAN) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = CardBg) {
                // Drawer header
                Box(Modifier.fillMaxWidth().padding(24.dp, 32.dp, 24.dp, 16.dp)) {
                    Column {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentBlue), contentAlignment = Alignment.Center) {
                            Text("CH", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("CDN Hunter", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("v2.2 • Scan & Fronting", fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Divider(color = BorderColor)
                Spacer(Modifier.height(8.dp))

                // Nav items
                NavScreen.entries.forEach { screen ->
                    val icon = when (screen) {
                        NavScreen.SCAN -> Icons.Rounded.Radar
                        NavScreen.RESULTS -> Icons.Rounded.List
                        NavScreen.CONFIG_GEN -> Icons.Rounded.Code
                        NavScreen.EXPORT -> Icons.Rounded.Share
                        NavScreen.PROFILES -> Icons.Rounded.Speed
                        NavScreen.SETTINGS -> Icons.Rounded.Settings
                    }
                    NavigationDrawerItem(
                        icon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
                        label = { Text(screen.label, fontSize = 14.sp) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = AccentBlue.copy(0.12f),
                            selectedTextColor = AccentBlue, selectedIconColor = AccentBlue,
                            unselectedTextColor = TextSecondary, unselectedIconColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = DarkBg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, "Menu", tint = TextPrimary)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(currentScreen.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            if (state.running) {
                                StatusDot(true)
                                Text(state.phaseDetail.ifBlank { "..." }, fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == NavScreen.SCAN || currentScreen == NavScreen.RESULTS) {
                            IconButton(onClick = onCopyIps) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
                        }
                    }
                )
            },
            bottomBar = {
                if (currentScreen == NavScreen.SCAN) BottomActions(state.running, onStart, onStop)
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (currentScreen) {
                    NavScreen.SCAN -> ScanScreen(state, config, onConfigChange)
                    NavScreen.RESULTS -> ResultsScreen(state.results)
                    NavScreen.CONFIG_GEN -> ConfigGenScreen(state.results)
                    NavScreen.EXPORT -> ExportScreen(state.results, onExport)
                    NavScreen.PROFILES -> ProfilesScreen(onConfigChange, onStart)
                    NavScreen.SETTINGS -> SettingsScreen(config, onConfigChange, onUpdateRanges)
                }
            }
        }
    }
}

@Composable
fun StatusDot(running: Boolean) {
    val color by animateColorAsState(if (running) GreenOk else TextMuted, label = "d")
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}

@Composable
fun BottomActions(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(color = DarkBg) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStart, enabled = !running, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, disabledContainerColor = AccentBlue.copy(0.3f))
            ) { Text("Start Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (running) RedFail else BorderColor)
            ) { Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (running) RedFail else TextMuted) }
        }
    }
}


// ── Scan Screen ─────────────────────────────────────────────────────────────
@Composable
fun ScanScreen(state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Stats
        item(key = "stats") { GlassStatsStrip(state) }
        item(key = "progress") { ProgressIndicator(state) }
        item(key = "phases") { PhaseSteps(state.phase) }

        // Live feed
        if (state.running && state.phase != ScanPhase.IDLE) {
            item(key = "banner") {
                Surface(color = AccentBlue.copy(0.08f), shape = RoundedCornerShape(10.dp)) {
                    Text("${state.phase.label} — ${state.phaseDetail}", fontSize = 13.sp, color = AccentBlue,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp, 8.dp))
                }
            }
        }
        items(state.logs.takeLast(5).reversed(), key = { it.hashCode() }) { log ->
            val c = when { log.startsWith("OK ") -> GreenOk; log.contains("ERR") -> RedFail; log.contains("Done") -> GreenOk; else -> TextSecondary }
            Text(log, fontSize = 12.sp, color = c, fontFamily = FontFamily.Monospace)
        }
        if (state.liveIps.isNotEmpty()) {
            item(key = "lhdr") { Text("Live Results (${state.liveIps.size})", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium) }
            items(state.liveIps.takeLast(10).reversed(), key = { it.ip }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                    Spacer(Modifier.width(10.dp))
                    Text(r.ip, fontSize = 13.sp, color = if (r.ok) TextPrimary else TextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text("${r.ms}ms", fontSize = 12.sp, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                }
            }
        }
        if (!state.running && state.liveIps.isEmpty() && state.logs.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Wifi, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Tap Start Scan", fontSize = 14.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun GlassStatsStrip(state: ScanState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassCard(Icons.Rounded.Radar, "Scanned", "${state.scanned}", TextPrimary, Modifier.weight(1f))
        GlassCard(Icons.Rounded.CheckCircle, "Healthy", "${state.healthy}", GreenOk, Modifier.weight(1f))
        GlassCard(Icons.Rounded.Cancel, "Failed", "${state.failed}", RedFail, Modifier.weight(1f))
        GlassCard(Icons.Rounded.Speed, "${state.pct}%", state.source, AccentBlue, Modifier.weight(1f))
    }
}

@Composable
fun GlassCard(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = color.copy(0.08f), border = BorderStroke(0.5.dp, color.copy(0.2f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color.copy(0.7f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ProgressIndicator(state: ScanState) {
    @Suppress("DEPRECATION")
    LinearProgressIndicator(progress = state.pct / 100f, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = AccentBlue, trackColor = CardBg)
}

@Composable
fun PhaseSteps(current: ScanPhase) {
    val phases = listOf(ScanPhase.SCANNING, ScanPhase.IP_CHECK, ScanPhase.FRONTING, ScanPhase.THROUGHPUT, ScanPhase.DONE)
    val currentIdx = phases.indexOf(current)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        phases.forEachIndexed { idx, phase ->
            val done = idx < currentIdx; val active = idx == currentIdx
            val bg = when { done -> GreenOk.copy(0.12f); active -> AccentBlue.copy(0.12f); else -> CardBg }
            val fg = when { done -> GreenOk; active -> AccentBlue; else -> TextMuted }
            Surface(shape = RoundedCornerShape(16.dp), color = bg) {
                Row(Modifier.padding(10.dp, 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) Icon(Icons.Rounded.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(13.dp))
                    Text(phase.label, fontSize = 11.sp, color = fg, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}


// ── Results Screen (tap to copy) ────────────────────────────────────────────
@Composable
fun ResultsScreen(results: List<ScanResult>) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    if (results.isEmpty()) { EmptyState(Icons.Rounded.Search, "No results yet"); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(results, key = { it.ip }) { r ->
            var copied by remember { mutableStateOf(false) }
            val bg by animateColorAsState(if (copied) GreenOk.copy(0.08f) else CardBg, tween(300), label = "bg")
            val border by animateColorAsState(if (copied) GreenOk else Color.Transparent, tween(300), label = "br")
            LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }
            Surface(color = bg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, border),
                onClick = { clipboardManager.setText(AnnotatedString(r.ip)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.ip, fontSize = 14.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.cdn, fontSize = 11.sp, color = AccentBlue)
                            if (r.frontingSni.isNotBlank()) Text(r.frontingSni.substringBefore("."), fontSize = 11.sp, color = AccentTeal)
                            if (r.country.isNotBlank()) Text(r.country, fontSize = 11.sp, color = if (r.country == "IR") RedFail else TextSecondary)
                        }
                    }
                    if (copied) Text("Copied!", fontSize = 12.sp, color = GreenOk, fontWeight = FontWeight.Medium)
                    else Column(horizontalAlignment = Alignment.End) {
                        Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                        if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

// ── Config Generator Screen ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigGenScreen(results: List<ScanResult>) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val frontingIps = results.filter { it.ok && it.frontingOk }
    var selectedIp by remember { mutableStateOf(frontingIps.firstOrNull()?.ip ?: "") }
    var selectedType by remember { mutableStateOf(ProxyType.VLESS) }
    var sni by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf("auto") }
    var generatedConfig by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Config Generator", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Generate proxy config from scanned IPs", fontSize = 13.sp, color = TextSecondary)
        }

        if (frontingIps.isEmpty()) {
            item { Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
                Text("Run a scan first to get fronting IPs", fontSize = 13.sp, color = YellowWarn, modifier = Modifier.padding(16.dp))
            } }
        }

        item {
            IosSection("SELECT IP") {
                if (frontingIps.isNotEmpty()) {
                    frontingIps.take(10).forEach { r ->
                        Surface(
                            color = if (selectedIp == r.ip) AccentBlue.copy(0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (selectedIp == r.ip) AccentBlue else BorderColor),
                            onClick = { selectedIp = r.ip; sni = r.frontingSni; host = r.frontingHost }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.ip, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("${r.ms}ms", fontSize = 11.sp, color = GreenOk)
                                Spacer(Modifier.width(8.dp))
                                Text(r.cdn, fontSize = 11.sp, color = AccentBlue)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                } else {
                    IosField(value = selectedIp, onValueChange = { selectedIp = it }, placeholder = "Enter IP manually")
                }
            }
        }

        item {
            IosSection("PROXY TYPE") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProxyType.entries.forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedType == type) AccentBlue.copy(0.15f) else CardBg2,
                            border = BorderStroke(1.dp, if (selectedType == type) AccentBlue else BorderColor),
                            onClick = { selectedType = type }
                        ) { Text(type.label, fontSize = 12.sp, color = if (selectedType == type) AccentBlue else TextSecondary, modifier = Modifier.padding(10.dp, 6.dp)) }
                    }
                }
            }
        }

        item {
            IosSection("SETTINGS") {
                IosField(value = sni, onValueChange = { sni = it }, label = "SNI", placeholder = "a248.e.akamai.net")
                Spacer(Modifier.height(8.dp))
                IosField(value = host, onValueChange = { host = it }, label = "Host", placeholder = "host header")
                Spacer(Modifier.height(8.dp))
                IosField(value = uuid, onValueChange = { uuid = it }, label = "UUID", placeholder = "auto = random")
            }
        }

        item {
            Button(onClick = {
                val cfg = ProxyConfig(type = selectedType, ip = selectedIp, sni = sni, host = host, uuid = uuid)
                generatedConfig = ConfigGenerator.generate(cfg)
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Generate Config", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        if (generatedConfig.isNotBlank()) {
            item {
                Surface(color = CardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Generated Config", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Surface(shape = RoundedCornerShape(8.dp), color = if (copied) GreenOk.copy(0.15f) else AccentBlue.copy(0.12f),
                                onClick = { clipboardManager.setText(AnnotatedString(generatedConfig)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }
                            ) { Text(if (copied) "✓ Copied" else "Copy", fontSize = 12.sp, color = if (copied) GreenOk else AccentBlue, modifier = Modifier.padding(10.dp, 5.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(generatedConfig, fontSize = 11.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                    }
                }
                LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}


// ── Export Screen ───────────────────────────────────────────────────────────
@Composable
fun ExportScreen(results: List<ScanResult>, onExport: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }
    val fronting = results.filter { it.frontingOk }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Export & Share", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${healthy.size} healthy, ${fronting.size} fronting IPs", fontSize = 13.sp, color = TextSecondary)
        }
        item {
            ExportCard("Copy All Healthy IPs", "${healthy.size} IPs", Icons.Rounded.ContentCopy) {
                clipboardManager.setText(AnnotatedString(healthy.joinToString("\n") { it.ip }))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        item {
            ExportCard("Copy Fronting IPs Only", "${fronting.size} IPs", Icons.Rounded.Shield) {
                clipboardManager.setText(AnnotatedString(fronting.joinToString("\n") { it.ip }))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        item {
            ExportCard("Copy as JSON", "Full details", Icons.Rounded.Code) {
                val json = healthy.joinToString(",\n") { """  {"ip":"${it.ip}","ms":${it.ms},"cdn":"${it.cdn}","fronting":${it.frontingOk},"sni":"${it.frontingSni}"}""" }
                clipboardManager.setText(AnnotatedString("[\n$json\n]"))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        item {
            ExportCard("Copy as CSV", "For spreadsheets", Icons.Rounded.TableChart) {
                val csv = "ip,ms,cdn,fronting,sni,country\n" + healthy.joinToString("\n") { "${it.ip},${it.ms},${it.cdn},${it.frontingOk},${it.frontingSni},${it.country}" }
                clipboardManager.setText(AnnotatedString(csv))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        item {
            ExportCard("Copy v2ray Configs", "All fronting IPs", Icons.Rounded.VpnKey) {
                val configs = fronting.joinToString("\n\n") { r ->
                    val cfg = ProxyConfig(type = ProxyType.VLESS, ip = r.ip, sni = r.frontingSni, host = r.frontingHost)
                    ConfigGenerator.generate(cfg)
                }
                clipboardManager.setText(AnnotatedString(configs))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        if (healthy.isEmpty()) {
            item { EmptyState(Icons.Rounded.FolderOpen, "Run a scan first") }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun ExportCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { delay(1500); done = false } }
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp), onClick = { onClick(); done = true }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            if (done) Text("✓", fontSize = 16.sp, color = GreenOk, fontWeight = FontWeight.Bold)
            else Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
        }
    }
}

// ── Profiles Screen ─────────────────────────────────────────────────────────
@Composable
fun ProfilesScreen(onConfigChange: (ScanConfig) -> Unit, onStart: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Scan Profiles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("One-tap presets for common scans", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
        }
        items(ScanProfile.entries.toList()) { profile ->
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
                onClick = { onConfigChange(profile.config); onStart() }
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        profile.name.contains("QUICK") -> Icons.Rounded.FlashOn
                        profile.name.contains("DEEP") -> Icons.Rounded.Explore
                        profile.name.contains("CF") -> Icons.Rounded.Cloud
                        else -> Icons.Rounded.Speed
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(0.1f)) {
                        Icon(icon, null, tint = AccentBlue, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.label, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(profile.desc, fontSize = 12.sp, color = TextSecondary)
                        Text("${profile.config.maxIps} IPs • conc ${profile.config.concurrency}", fontSize = 11.sp, color = TextMuted)
                    }
                    Icon(Icons.Rounded.PlayArrow, null, tint = GreenOk, modifier = Modifier.size(24.dp))
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Settings Screen ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: ScanConfig, onConfigChange: (ScanConfig) -> Unit, onUpdateRanges: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        item {
            IosSection("CDN PROVIDER") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    IosField(value = config.cdnProvider.label, modifier = Modifier.menuAnchor(), readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CdnProvider.entries.forEach { p -> DropdownMenuItem(text = { Text(p.label) }, onClick = { onConfigChange(config.copy(cdnProvider = p)); expanded = false }) }
                    }
                }
            }
        }
        if (config.cdnProvider == CdnProvider.MANUAL) {
            item { IosSection("MANUAL IPs") { IosField(value = config.manualIps, onValueChange = { onConfigChange(config.copy(manualIps = it)) }, placeholder = "1.2.3.4", lines = 4) } }
        }
        if (config.cdnProvider == CdnProvider.CIDR) {
            item { IosSection("CIDR RANGES") { IosField(value = config.manualCidr, onValueChange = { onConfigChange(config.copy(manualCidr = it)) }, placeholder = "104.16.0.0/12", lines = 4) } }
        }
        item {
            IosSection("CONNECTION") {
                IosField(value = config.host, onValueChange = { onConfigChange(config.copy(host = it)) }, label = "Host Header", placeholder = "speed.cloudflare.com")
                Spacer(Modifier.height(10.dp))
                IosField(value = config.sni, onValueChange = { onConfigChange(config.copy(sni = it)) }, label = "SNI", placeholder = "a248.e.akamai.net")
            }
        }
        item {
            IosSection("PERFORMANCE") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { IosField(value = "${config.concurrency}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v.coerceIn(1, 300))) } }, label = "Workers") }
                    Column(Modifier.weight(1f)) { IosField(value = "${config.timeout}", onValueChange = { it.toFloatOrNull()?.let { v -> onConfigChange(config.copy(timeout = v.coerceIn(1f, 20f))) } }, label = "Timeout") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { IosField(value = "${config.maxIps}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v.coerceIn(10, 50000))) } }, label = "Max IPs") }
                    Column(Modifier.weight(1f)) { IosField(value = "${config.retries}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(retries = v.coerceIn(0, 10))) } }, label = "Retries") }
                }
            }
        }
        item {
            IosSection("RANGES") {
                Surface(color = CardBg2, shape = RoundedCornerShape(10.dp), onClick = onUpdateRanges) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, null, tint = AccentBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Update CDN Ranges", fontSize = 14.sp, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ── Shared Components ───────────────────────────────────────────────────────
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
fun IosSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp)) { content() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosField(value: String, onValueChange: (String) -> Unit = {}, label: String = "", placeholder: String = "", readOnly: Boolean = false, lines: Int = 1, modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null) {
    if (label.isNotBlank()) Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
    OutlinedTextField(value = value, onValueChange = onValueChange, readOnly = readOnly,
        modifier = modifier.fillMaxWidth().then(if (lines > 1) Modifier.height((lines * 28 + 24).dp) else Modifier),
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) }, trailingIcon = trailingIcon,
        shape = RoundedCornerShape(10.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue,
            focusedContainerColor = CardBg2, unfocusedContainerColor = CardBg2))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IosFlowRow(modifier: Modifier = Modifier, horizontalSpacing: androidx.compose.ui.unit.Dp = 0.dp, verticalSpacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(horizontalSpacing), verticalArrangement = Arrangement.spacedBy(verticalSpacing)) { content() }
}
