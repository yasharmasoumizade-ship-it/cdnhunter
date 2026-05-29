package com.cdnhunter.app.ui

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ConfigGenerator
import com.cdnhunter.app.engine.GeoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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



// ══════════════════════════════════════════════════════════════════════════════
//  MAIN APP — Sidebar + Scan page with 3 swipeable tabs (Results/Fronting/Region)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScreen(
    state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {}, onExport: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(NavScreen.SCAN) }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = CardBg) {
            // Header
            Box(Modifier.fillMaxWidth().padding(24.dp, 32.dp, 24.dp, 16.dp)) {
                Column {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AccentBlue), contentAlignment = Alignment.Center) {
                        Text("CH", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("CDN Hunter", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("v3.0 • Scan & Config", fontSize = 13.sp, color = TextSecondary)
                }
            }
            Divider(color = BorderColor)
            Spacer(Modifier.height(8.dp))
            NavScreen.entries.forEach { screen ->
                val icon = when (screen) {
                    NavScreen.SCAN -> Icons.Rounded.Radar
                    NavScreen.CONFIG_GEN -> Icons.Rounded.Code
                    NavScreen.EXPORT -> Icons.Rounded.Share
                    NavScreen.PROFILES -> Icons.Rounded.Speed
                    NavScreen.LOG -> Icons.Rounded.Terminal
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
    }) {
        Scaffold(containerColor = DarkBg, topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Rounded.Menu, "Menu", tint = TextPrimary) } },
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(currentScreen.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (state.running) { StatusDot(); Text(state.phaseDetail.take(20), fontSize = 11.sp, color = TextSecondary) }
                } },
                actions = { IconButton(onClick = onCopyIps) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(20.dp)) } }
            )
        }, bottomBar = { if (currentScreen == NavScreen.SCAN) BottomActions(state.running, onStart, onStop) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (currentScreen) {
                    NavScreen.SCAN -> ScanMainScreen(state, config, onConfigChange)
                    NavScreen.CONFIG_GEN -> ConfigGenScreen(state.results)
                    NavScreen.EXPORT -> ExportScreen(state.results)
                    NavScreen.PROFILES -> ProfilesScreen(onConfigChange, onStart)
                    NavScreen.LOG -> LogScreen(state.logs)
                    NavScreen.SETTINGS -> SettingsScreen(config, onConfigChange, onUpdateRanges)
                }
            }
        }
    }
}

@Composable fun StatusDot() { Box(Modifier.size(7.dp).clip(CircleShape).background(GreenOk)) }

@Composable
fun BottomActions(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(color = DarkBg) { Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onStart, enabled = !running, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, disabledContainerColor = AccentBlue.copy(0.3f))) { Text("Start", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (running) RedFail else BorderColor)) { Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (running) RedFail else TextMuted) }
    } }
}



// ══════════════════════════════════════════════════════════════════════════════
//  SCAN MAIN — Stats + Phases + 3 swipeable tabs (Results / Fronting / Region)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanMainScreen(state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit) {
    val tabs = listOf("Results", "Fronting", "Region")
    val pagerState = rememberPagerState(0) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCard(Icons.Rounded.Radar, "${state.scanned}", "Scanned", TextPrimary, Modifier.weight(1f))
            GlassCard(Icons.Rounded.CheckCircle, "${state.healthy}", "Healthy", GreenOk, Modifier.weight(1f))
            GlassCard(Icons.Rounded.Cancel, "${state.failed}", "Failed", RedFail, Modifier.weight(1f))
            GlassCard(Icons.Rounded.Speed, "${state.pct}%", state.source, AccentBlue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        @Suppress("DEPRECATION") LinearProgressIndicator(progress = state.pct / 100f, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = AccentBlue, trackColor = CardBg)
        Spacer(Modifier.height(8.dp))
        PhaseSteps(state.phase)
        Spacer(Modifier.height(12.dp))

        // Segmented tab control
        Surface(color = CardBg, shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(3.dp)) {
                tabs.forEachIndexed { idx, t ->
                    val sel = pagerState.currentPage == idx
                    Surface(modifier = Modifier.weight(1f), color = if (sel) CardBg2 else Color.Transparent, shape = RoundedCornerShape(8.dp),
                        onClick = { scope.launch { pagerState.animateScrollToPage(idx) } }) {
                        Text(t, Modifier.padding(vertical = 9.dp), fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) TextPrimary else TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Swipeable pages
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ResultsTab(state.results)
                1 -> FrontingTab(state.results)
                2 -> RegionTab(state.results)
            }
        }
    }
}

@Composable
fun GlassCard(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = color.copy(0.08f), border = BorderStroke(0.5.dp, color.copy(0.2f))) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color.copy(0.7f), modifier = Modifier.size(14.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
fun PhaseSteps(current: ScanPhase) {
    val phases = ScanPhase.entries.filter { it != ScanPhase.IDLE }
    val idx = phases.indexOf(current)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        phases.forEachIndexed { i, p ->
            val done = i < idx; val active = i == idx
            Surface(shape = RoundedCornerShape(16.dp), color = when { done -> GreenOk.copy(0.12f); active -> AccentBlue.copy(0.12f); else -> CardBg }) {
                Row(Modifier.padding(10.dp, 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (done) Icon(Icons.Rounded.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(13.dp))
                    Text(p.label, fontSize = 11.sp, color = when { done -> GreenOk; active -> AccentBlue; else -> TextMuted })
                }
            }
        }
    }
}



// ══════════════════════════════════════════════════════════════════════════════
//  RESULTS TAB — tap to copy
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun ResultsTab(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    if (results.isEmpty()) { EmptyState(Icons.Rounded.Search, "No results yet"); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(results, key = { it.ip }) { r ->
            var copied by remember { mutableStateOf(false) }
            val bg by animateColorAsState(if (copied) GreenOk.copy(0.08f) else CardBg, tween(300), label = "")
            LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }
            Surface(color = bg, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, if (copied) GreenOk.copy(0.4f) else BorderColor),
                onClick = { clip.setText(AnnotatedString(r.ip)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }) {
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
                    if (copied) Text("✓", fontSize = 16.sp, color = GreenOk, fontWeight = FontWeight.Bold)
                    else Column(horizontalAlignment = Alignment.End) {
                        Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                        if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  FRONTING TAB
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun FrontingTab(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    val fronting = results.filter { it.ok && it.frontingOk }
    if (fronting.isEmpty()) { EmptyState(Icons.Rounded.Shield, "No fronting data yet"); return }
    val groups = fronting.groupBy { "${it.cdn}||${it.frontingSni}" }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(groups.entries.toList(), key = { it.key }) { (_, ips) ->
            val f = ips.first()
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.cdn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(0.12f)) { Text("${ips.size}", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(7.dp, 2.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Surface(color = CardBg2, shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(10.dp)) {
                    Text("SNI: ${f.frontingSni.ifBlank { "auto" }}", fontSize = 12.sp, color = AccentBlue, fontFamily = FontFamily.Monospace)
                    if (f.frontingHost.isNotBlank() && f.frontingHost != f.frontingSni) Text("Host: ${f.frontingHost}", fontSize = 12.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                } }
                Spacer(Modifier.height(8.dp))
                IosFlowRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) { ips.forEach { r ->
                    var c by remember { mutableStateOf(false) }
                    LaunchedEffect(c) { if (c) { delay(1000); c = false } }
                    Surface(shape = RoundedCornerShape(8.dp), color = if (c) GreenOk.copy(0.1f) else CardBg2,
                        onClick = { clip.setText(AnnotatedString(r.ip)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); c = true }) {
                        Text(if (c) "✓ ${r.ip}" else r.ip, fontSize = 13.sp, color = if (c) GreenOk else TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp, 6.dp))
                    }
                } }
            } }
        }
    }
}



// ══════════════════════════════════════════════════════════════════════════════
//  REGION TAB — tap IP to show map (like ExpressVPN)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun RegionTab(results: List<ScanResult>) {
    val healthy = results.filter { it.ok }
    if (healthy.isEmpty()) { EmptyState(Icons.Rounded.Map, "No region data yet"); return }
    var selectedIp by remember { mutableStateOf<ScanResult?>(null) }
    val geoService = remember { GeoService() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // If IP selected, show map
        AnimatedVisibility(visible = selectedIp != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            selectedIp?.let { ip ->
                var geo by remember(ip.ip) { mutableStateOf<GeoService.GeoInfo?>(null) }
                var loading by remember(ip.ip) { mutableStateOf(true) }
                LaunchedEffect(ip.ip) { loading = true; geo = withContext(Dispatchers.IO) { geoService.lookupGeoInfo(ip.ip) }; loading = false }

                Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        // Header with close
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ip.ip, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = { selectedIp = null }, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, "Close", tint = TextMuted, modifier = Modifier.size(18.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))

                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = AccentBlue, strokeWidth = 2.dp)
                        } else if (geo != null && geo!!.lat != 0.0) {
                            // Map view (OpenStreetMap)
                            Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(180.dp)) {
                                AndroidView(factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewClient = WebViewClient()
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=${geo!!.lon-0.5},${geo!!.lat-0.3},${geo!!.lon+0.5},${geo!!.lat+0.3}&layer=mapnik&marker=${geo!!.lat},${geo!!.lon}")
                                    }
                                }, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.height(10.dp))
                            // Location info
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                InfoChip(Icons.Rounded.Flag, geo!!.cc, AccentBlue)
                                if (geo!!.city.isNotBlank()) InfoChip(Icons.Rounded.LocationCity, geo!!.city, AccentTeal)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (geo!!.isp.isNotBlank()) InfoChip(Icons.Rounded.Dns, geo!!.isp.take(25), TextSecondary)
                                InfoChip(Icons.Rounded.MyLocation, "${geo!!.lat.format(2)}, ${geo!!.lon.format(2)}", TextMuted)
                            }
                        } else {
                            Text("Location not available", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        // IP list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(healthy, key = { it.ip }) { r ->
                val isSelected = selectedIp?.ip == r.ip
                Surface(color = if (isSelected) AccentBlue.copy(0.08f) else CardBg, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, if (isSelected) AccentBlue.copy(0.4f) else BorderColor),
                    onClick = { selectedIp = if (isSelected) null else r }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, null, tint = if (r.country == "IR") RedFail else AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.ip, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            Text("${r.cdn} • ${r.country.ifBlank { "?" }}", fontSize = 11.sp, color = TextSecondary)
                        }
                        Text("${r.ms}ms", fontSize = 12.sp, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.08f)) {
        Row(Modifier.padding(8.dp, 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)



// ══════════════════════════════════════════════════════════════════════════════
//  LOG SCREEN (in sidebar)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LogScreen(logs: List<String>) {
    if (logs.isEmpty()) { EmptyState(Icons.Rounded.Terminal, "No activity yet"); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(logs.reversed()) { log ->
            val c = when { log.startsWith("OK ") -> GreenOk; log.contains("ERR") || log.contains("STOP") -> RedFail; log.contains("Done") -> GreenOk; log.contains("[Phase") -> AccentBlue; else -> TextSecondary }
            Text("$ $log", fontSize = 12.sp, color = c, fontFamily = FontFamily.Monospace)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CONFIG GENERATOR
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigGenScreen(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    val fronting = results.filter { it.ok && it.frontingOk }
    var selectedIp by remember { mutableStateOf(fronting.firstOrNull()?.ip ?: "") }
    var selectedType by remember { mutableStateOf(ProxyType.VLESS) }
    var sni by remember { mutableStateOf(fronting.firstOrNull()?.frontingSni ?: "") }
    var host by remember { mutableStateOf(fronting.firstOrNull()?.frontingHost ?: "") }
    var uuid by remember { mutableStateOf("auto") }
    var config by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Config Generator", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("Generate proxy config from scanned IPs", fontSize = 13.sp, color = TextSecondary) }
        if (fronting.isNotEmpty()) { item { IosSection("SELECT IP") { fronting.take(8).forEach { r ->
            Surface(color = if (selectedIp == r.ip) AccentBlue.copy(0.12f) else Color.Transparent, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (selectedIp == r.ip) AccentBlue else BorderColor),
                onClick = { selectedIp = r.ip; sni = r.frontingSni; host = r.frontingHost }) {
                Row(Modifier.fillMaxWidth().padding(10.dp)) { Text(r.ip, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)); Text("${r.ms}ms", fontSize = 11.sp, color = GreenOk) }
            }; Spacer(Modifier.height(4.dp))
        } } } } else { item { IosSection("IP") { IosField(value = selectedIp, onValueChange = { selectedIp = it }, placeholder = "Enter IP") } } }
        item { IosSection("TYPE") { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProxyType.entries.forEach { t -> Surface(shape = RoundedCornerShape(8.dp), color = if (selectedType == t) AccentBlue.copy(0.15f) else CardBg2,
                border = BorderStroke(1.dp, if (selectedType == t) AccentBlue else BorderColor), onClick = { selectedType = t }) {
                Text(t.label, fontSize = 12.sp, color = if (selectedType == t) AccentBlue else TextSecondary, modifier = Modifier.padding(10.dp, 6.dp))
            } }
        } } }
        item { IosSection("DETAILS") { IosField(sni, { sni = it }, "SNI", "a248.e.akamai.net"); Spacer(Modifier.height(8.dp)); IosField(host, { host = it }, "Host"); Spacer(Modifier.height(8.dp)); IosField(uuid, { uuid = it }, "UUID", "auto = random") } }
        item { Button(onClick = { config = ConfigGenerator.generate(ProxyConfig(selectedType, selectedIp, 443, sni, host, uuid)) },
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("Generate", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) } }
        if (config.isNotBlank()) { item { Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Config", fontSize = 13.sp, color = TextSecondary)
                Surface(shape = RoundedCornerShape(8.dp), color = if (copied) GreenOk.copy(0.15f) else AccentBlue.copy(0.12f),
                    onClick = { clip.setText(AnnotatedString(config)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }) { Text(if (copied) "✓" else "Copy", fontSize = 12.sp, color = if (copied) GreenOk else AccentBlue, modifier = Modifier.padding(10.dp, 5.dp)) } }
            Spacer(Modifier.height(8.dp)); Text(config, fontSize = 11.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
        } }; LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } } } }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  EXPORT / PROFILES / SETTINGS (same as before but compact)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun ExportScreen(results: List<ScanResult>) {
    val clip = LocalClipboardManager.current; val haptic = LocalHapticFeedback.current
    val h = results.filter { it.ok }; val f = results.filter { it.frontingOk }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Export", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("${h.size} healthy, ${f.size} fronting", fontSize = 13.sp, color = TextSecondary) }
        item { ExportCard("All IPs", "${h.size}", Icons.Rounded.ContentCopy) { clip.setText(AnnotatedString(h.joinToString("\n") { it.ip })); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
        item { ExportCard("Fronting Only", "${f.size}", Icons.Rounded.Shield) { clip.setText(AnnotatedString(f.joinToString("\n") { it.ip })); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
        item { ExportCard("JSON", "Full", Icons.Rounded.Code) { clip.setText(AnnotatedString(h.joinToString(",\n", "[\n", "\n]") { """  {"ip":"${it.ip}","ms":${it.ms},"cdn":"${it.cdn}","fronting":${it.frontingOk}}""" })); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
        item { ExportCard("v2ray Links", "VLESS", Icons.Rounded.VpnKey) { clip.setText(AnnotatedString(f.joinToString("\n") { ConfigGenerator.generate(ProxyConfig(ProxyType.VLESS, it.ip, 443, it.frontingSni, it.frontingHost)) })); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
        if (h.isEmpty()) { item { EmptyState(Icons.Rounded.FolderOpen, "Run a scan first") } }
    }
}
@Composable fun ExportCard(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    var done by remember { mutableStateOf(false) }; LaunchedEffect(done) { if (done) { delay(1500); done = false } }
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp), onClick = { onClick(); done = true }) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, color = TextPrimary); Text(sub, fontSize = 12.sp, color = TextSecondary) }
        if (done) Text("✓", color = GreenOk, fontWeight = FontWeight.Bold) else Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
    } }
}

@Composable
fun ProfilesScreen(onConfigChange: (ScanConfig) -> Unit, onStart: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Scan Profiles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text("One-tap presets", fontSize = 13.sp, color = TextSecondary); Spacer(Modifier.height(8.dp)) }
        items(ScanProfile.entries.toList()) { p -> Surface(color = CardBg, shape = RoundedCornerShape(14.dp), onClick = { onConfigChange(p.config); onStart() }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val ic = when { p.name.contains("QUICK") -> Icons.Rounded.FlashOn; p.name.contains("DEEP") -> Icons.Rounded.Explore; else -> Icons.Rounded.Speed }
                Surface(shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(0.1f)) { Icon(ic, null, tint = AccentBlue, modifier = Modifier.padding(10.dp).size(20.dp)) }
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(p.label, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold); Text(p.desc, fontSize = 12.sp, color = TextSecondary) }
                Icon(Icons.Rounded.PlayArrow, null, tint = GreenOk)
            }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class) @Composable
fun SettingsScreen(config: ScanConfig, onConfigChange: (ScanConfig) -> Unit, onUpdateRanges: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        item { IosSection("CDN") { var ex by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = ex, onExpandedChange = { ex = it }) { IosField(config.cdnProvider.label, modifier = Modifier.menuAnchor(), readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ex) })
                ExposedDropdownMenu(ex, { ex = false }) { CdnProvider.entries.forEach { DropdownMenuItem(text = { Text(it.label) }, onClick = { onConfigChange(config.copy(cdnProvider = it)); ex = false }) } } } } }
        item { IosSection("CONNECTION") { IosField(config.host, { onConfigChange(config.copy(host = it)) }, "Host", "speed.cloudflare.com"); Spacer(Modifier.height(8.dp)); IosField(config.sni, { onConfigChange(config.copy(sni = it)) }, "SNI", "a248.e.akamai.net") } }
        item { IosSection("PERFORMANCE") { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { IosField("${config.concurrency}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v.coerceIn(1, 300))) } }, "Workers") }
            Column(Modifier.weight(1f)) { IosField("${config.timeout}", { it.toFloatOrNull()?.let { v -> onConfigChange(config.copy(timeout = v.coerceIn(1f, 20f))) } }, "Timeout") } }
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { IosField("${config.maxIps}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v.coerceIn(10, 50000))) } }, "Max IPs") }
            Column(Modifier.weight(1f)) { IosField("${config.retries}", { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(retries = v.coerceIn(0, 10))) } }, "Retries") } } } }
        item { Surface(color = CardBg, shape = RoundedCornerShape(12.dp), onClick = onUpdateRanges) { Row(Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Rounded.Refresh, null, tint = AccentBlue); Spacer(Modifier.width(12.dp)); Text("Update Ranges", fontSize = 14.sp, color = TextPrimary); Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted) } } }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable fun EmptyState(icon: ImageVector, text: String) { Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = TextMuted, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(10.dp)); Text(text, fontSize = 14.sp, color = TextSecondary) } }
@Composable fun IosSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column { Text(title, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)); Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp)) { content() } } } }
@OptIn(ExperimentalMaterial3Api::class) @Composable
fun IosField(value: String, onValueChange: (String) -> Unit = {}, label: String = "", placeholder: String = "", readOnly: Boolean = false, lines: Int = 1, modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null) {
    if (label.isNotBlank()) Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
    OutlinedTextField(value = value, onValueChange = onValueChange, readOnly = readOnly, modifier = modifier.fillMaxWidth().then(if (lines > 1) Modifier.height((lines * 28 + 24).dp) else Modifier),
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) }, trailingIcon = trailingIcon, shape = RoundedCornerShape(10.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue, focusedContainerColor = CardBg2, unfocusedContainerColor = CardBg2))
}
@OptIn(ExperimentalLayoutApi::class) @Composable
fun IosFlowRow(modifier: Modifier = Modifier, horizontalSpacing: androidx.compose.ui.unit.Dp = 0.dp, verticalSpacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(horizontalSpacing), verticalArrangement = Arrangement.spacedBy(verticalSpacing)) { content() }
}
