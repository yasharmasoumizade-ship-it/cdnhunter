package com.cdnhunter.app.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.data.*
import kotlinx.coroutines.launch

// ── Colors (clean, no purple) ───────────────────────────────────────────────
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

// ── Main App Screen ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScreen(
    state: ScanState,
    config: ScanConfig,
    onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {},
) {
    val tabs = listOf("Results", "Fronting", "Live", "Config")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = DarkBg,
        topBar = { TopBar(state = state, onCopyIps = onCopyIps, onUpdateRanges = onUpdateRanges) },
        bottomBar = { BottomActions(running = state.running, onStart = onStart, onStop = onStop) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            StatsStrip(state)
            Spacer(Modifier.height(10.dp))
            ProgressIndicator(state)
            Spacer(Modifier.height(8.dp))
            PhaseSteps(state.phase)
            Spacer(Modifier.height(14.dp))

            // Segmented tab bar
            Surface(color = CardBg, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(3.dp)) {
                    tabs.forEachIndexed { idx, title ->
                        val selected = pagerState.currentPage == idx
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (selected) CardBg2 else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(idx) } }
                        ) {
                            Text(
                                title, modifier = Modifier.padding(vertical = 9.dp),
                                fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) TextPrimary else TextSecondary, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Swipeable pages
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ResultsTab(state.results)
                    1 -> FrontingTab(state.results)
                    2 -> OverviewTab(state)
                    3 -> ConfigTab(config, onConfigChange)
                }
            }
        }
    }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(state: ScanState, onCopyIps: () -> Unit, onUpdateRanges: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) { Text("CH", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Column {
                    Text("CDN Hunter", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        StatusDot(state.running)
                        Text(
                            when {
                                state.running -> state.phaseDetail.ifBlank { "Scanning..." }
                                state.pct >= 100 -> "Complete"
                                else -> "Ready"
                            }, fontSize = 12.sp, color = TextSecondary
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onUpdateRanges) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Update", tint = AccentBlue, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onCopyIps) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    )
}

@Composable
fun StatusDot(running: Boolean) {
    val color by animateColorAsState(if (running) GreenOk else TextMuted, label = "dot")
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
}

// ── Bottom Actions ──────────────────────────────────────────────────────────
@Composable
fun BottomActions(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(color = DarkBg) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onStart, enabled = !running,
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, disabledContainerColor = AccentBlue.copy(0.3f))
            ) { Text("Start Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onStop, enabled = running,
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (running) RedFail else BorderColor)
            ) { Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (running) RedFail else TextMuted) }
        }
    }
}

// ── Stats ───────────────────────────────────────────────────────────────────
@Composable
fun StatsStrip(state: ScanState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Scanned", "${state.scanned}", TextPrimary, Modifier.weight(1f))
        StatCard("Healthy", "${state.healthy}", GreenOk, Modifier.weight(1f))
        StatCard("Failed", "${state.failed}", RedFail, Modifier.weight(1f))
        StatCard("${state.pct}%", state.source, AccentBlue, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = CardBg, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp, 8.dp)) {
            Text(label, fontSize = 10.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Progress ────────────────────────────────────────────────────────────────
@Composable
fun ProgressIndicator(state: ScanState) {
    @Suppress("DEPRECATION")
    LinearProgressIndicator(
        progress = state.pct / 100f,
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
        color = AccentBlue, trackColor = CardBg,
    )
}

// ── Phase Steps ─────────────────────────────────────────────────────────────
@Composable
fun PhaseSteps(current: ScanPhase) {
    val phases = listOf(ScanPhase.SCANNING, ScanPhase.IP_CHECK, ScanPhase.FRONTING, ScanPhase.THROUGHPUT, ScanPhase.DONE)
    val currentIdx = phases.indexOf(current)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        phases.forEachIndexed { idx, phase ->
            val isDone = idx < currentIdx
            val isActive = idx == currentIdx
            val bg = when { isDone -> GreenOk.copy(0.12f); isActive -> AccentBlue.copy(0.12f); else -> CardBg }
            val fg = when { isDone -> GreenOk; isActive -> AccentBlue; else -> TextMuted }
            Surface(shape = RoundedCornerShape(16.dp), color = bg) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isDone) Icon(Icons.Rounded.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(13.dp))
                    Text(phase.label, fontSize = 11.sp, color = fg, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ── Overview/Live Tab ───────────────────────────────────────────────────────
@Composable
fun OverviewTab(state: ScanState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.running && state.phase != ScanPhase.IDLE) {
            item(key = "phase_banner") {
                Surface(color = AccentBlue.copy(0.08f), shape = RoundedCornerShape(8.dp)) {
                    Text("${state.phase.label} — ${state.phaseDetail}", fontSize = 13.sp, color = AccentBlue,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp, 8.dp))
                }
            }
        }
        items(state.logs.takeLast(6).reversed(), key = { it.hashCode() }) { log ->
            val color = when {
                log.startsWith("OK ") -> GreenOk; log.contains("ERR") || log.contains("STOP") -> RedFail
                log.contains("Done") -> GreenOk; else -> TextSecondary
            }
            Text(log, fontSize = 12.sp, color = color, fontFamily = FontFamily.Monospace)
        }
        if (state.liveIps.isNotEmpty()) {
            item(key = "live_header") { Text("Live Results", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp)) }
            items(state.liveIps.takeLast(12).reversed(), key = { it.ip }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                    Spacer(Modifier.width(10.dp))
                    Text(r.ip, fontSize = 13.sp, color = if (r.ok) TextPrimary else TextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text("${r.ms}ms", fontSize = 12.sp, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                }
            }
        }
        if (!state.running && state.logs.isEmpty() && state.liveIps.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Wifi, null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Start a scan to see results", fontSize = 14.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ── Results Tab ─────────────────────────────────────────────────────────────
@Composable
fun ResultsTab(results: List<ScanResult>) {
    if (results.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("No results yet", fontSize = 14.sp, color = TextSecondary)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(results, key = { it.ip }) { r ->
            Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
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
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                        if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

// ── Fronting Tab ────────────────────────────────────────────────────────────
@Composable
fun FrontingTab(results: List<ScanResult>) {
    val frontingIps = results.filter { it.ok && it.frontingOk }
    if (frontingIps.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Shield, null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("No fronting data yet", fontSize = 14.sp, color = TextSecondary)
        }
        return
    }
    val groups = frontingIps.groupBy { "${it.cdn}||${it.frontingSni}" }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(groups.entries.toList(), key = { it.key }) { (_, ips) ->
            val first = ips.first()
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(first.cdn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Surface(shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(0.12f)) {
                            Text("${ips.size}", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(color = CardBg2, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("SNI", fontSize = 11.sp, color = TextMuted)
                                Text(first.frontingSni.ifBlank { "auto" }, fontSize = 12.sp, color = AccentBlue, fontFamily = FontFamily.Monospace)
                            }
                            if (first.frontingHost.isNotBlank() && first.frontingHost != first.frontingSni) {
                                Spacer(Modifier.height(3.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Host", fontSize = 11.sp, color = TextMuted)
                                    Text(first.frontingHost, fontSize = 12.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    IosFlowRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                        ips.forEach { ip ->
                            Surface(shape = RoundedCornerShape(8.dp), color = CardBg2) {
                                Text(ip.ip, fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp, 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Config Tab ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigTab(config: ScanConfig, onConfigChange: (ScanConfig) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "cdn_section") {
            IosSection("SCAN TYPE") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    IosField(value = config.cdnProvider.label, modifier = Modifier.menuAnchor(), readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CdnProvider.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { onConfigChange(config.copy(cdnProvider = p)); expanded = false })
                        }
                    }
                }
            }
        }
        if (config.cdnProvider == CdnProvider.MANUAL) {
            item(key = "manual_ips") {
                IosSection("MANUAL IPs") {
                    IosField(value = config.manualIps, onValueChange = { onConfigChange(config.copy(manualIps = it)) }, placeholder = "1.2.3.4\n5.6.7.8", lines = 4)
                }
            }
        }
        if (config.cdnProvider == CdnProvider.CIDR) {
            item(key = "manual_cidr") {
                IosSection("CIDR RANGES") {
                    IosField(value = config.manualCidr, onValueChange = { onConfigChange(config.copy(manualCidr = it)) }, placeholder = "104.16.0.0/12", lines = 4)
                }
            }
        }
        item(key = "connection") {
            IosSection("CONNECTION") {
                IosField(value = config.host, onValueChange = { onConfigChange(config.copy(host = it)) }, label = "Host Header", placeholder = "speed.cloudflare.com")
                Spacer(Modifier.height(10.dp))
                IosField(value = config.sni, onValueChange = { onConfigChange(config.copy(sni = it)) }, label = "SNI Override", placeholder = "a248.e.akamai.net")
            }
        }
        item(key = "perf") {
            IosSection("PERFORMANCE") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { IosField(value = "${config.concurrency}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v.coerceIn(1, 300))) } }, label = "Concurrency") }
                    Column(Modifier.weight(1f)) { IosField(value = "${config.timeout}", onValueChange = { it.toFloatOrNull()?.let { v -> onConfigChange(config.copy(timeout = v.coerceIn(1f, 20f))) } }, label = "Timeout (s)") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { IosField(value = "${config.maxIps}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v.coerceIn(10, 50000))) } }, label = "Max IPs") }
                    Column(Modifier.weight(1f)) { IosField(value = "${config.retries}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(retries = v.coerceIn(0, 10))) } }, label = "Retries") }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(30.dp)) }
    }
}

// ── iOS Components ──────────────────────────────────────────────────────────
@Composable
fun IosSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosField(value: String, onValueChange: (String) -> Unit = {}, label: String = "", placeholder: String = "", readOnly: Boolean = false, lines: Int = 1, modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null) {
    if (label.isNotBlank()) Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
    OutlinedTextField(
        value = value, onValueChange = onValueChange, readOnly = readOnly,
        modifier = modifier.fillMaxWidth().then(if (lines > 1) Modifier.height((lines * 28 + 24).dp) else Modifier),
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) }, trailingIcon = trailingIcon,
        shape = RoundedCornerShape(10.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = AccentBlue,
            focusedContainerColor = CardBg2, unfocusedContainerColor = CardBg2)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IosFlowRow(modifier: Modifier = Modifier, horizontalSpacing: androidx.compose.ui.unit.Dp = 0.dp, verticalSpacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(horizontalSpacing), verticalArrangement = Arrangement.spacedBy(verticalSpacing)) { content() }
}
