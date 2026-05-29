package com.cdnhunter.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.data.*

// ── Colors ──────────────────────────────────────────────────────────────────
val DarkBg = Color(0xFF000000)
val CardBg = Color(0xFF0A0A0A)
val BorderColor = Color(0xFF222222)
val AccentBlue = Color(0xFF3B82F6)
val AccentPurple = Color(0xFF8B5CF6)
val GreenOk = Color(0xFF22C55E)
val RedFail = Color(0xFFEF4444)
val YellowWarn = Color(0xFFFACC15)
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFF999999)
val TextMuted = Color(0xFF555555)

// ── Main App Screen ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    state: ScanState,
    config: ScanConfig,
    onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyIps: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Results", "Fronting", "Config", "Log")

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopBar(state = state, onCopyIps = onCopyIps)
        },
        bottomBar = {
            BottomActions(
                running = state.running,
                onStart = onStart,
                onStop = onStop,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            StatsStrip(state)
            Spacer(Modifier.height(8.dp))
            ProgressIndicator(state)
            Spacer(Modifier.height(8.dp))
            PhaseSteps(state.phase)
            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = AccentBlue,
                divider = { Divider(color = BorderColor, thickness = 1.dp) }
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontSize = 13.sp, color = if (selectedTab == idx) TextPrimary else TextSecondary) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> OverviewTab(state)
                1 -> ResultsTab(state.results)
                2 -> FrontingTab(state.results)
                3 -> ConfigTab(config, onConfigChange)
                4 -> LogTab(state.logs)
            }
        }
    }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(state: ScanState, onCopyIps: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                        .background(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(AccentBlue, AccentPurple))),
                    contentAlignment = Alignment.Center
                ) { Text("CH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text("CDN Hunter", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                StatusDot(state.running)
                Text(
                    if (state.running) state.phaseDetail.ifBlank { "Scanning..." } else if (state.pct >= 100) "Done" else "Idle",
                    fontSize = 11.sp, color = TextSecondary
                )
            }
        },
        actions = {
            IconButton(onClick = onCopyIps) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    )
}

@Composable
fun StatusDot(running: Boolean) {
    val color by animateColorAsState(if (running) GreenOk else TextMuted, label = "dot")
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

// ── Bottom Actions ──────────────────────────────────────────────────────────
@Composable
fun BottomActions(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(color = CardBg, border = BorderStroke(1.dp, BorderColor)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = !running,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Start Scan") }
            OutlinedButton(
                onClick = onStop,
                enabled = running,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedFail),
                border = BorderStroke(1.dp, if (running) RedFail else BorderColor)
            ) { Text("Stop") }
        }
    }
}

// ── Stats Strip ─────────────────────────────────────────────────────────────
@Composable
fun StatsStrip(state: ScanState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Scanned", "${state.scanned}", TextPrimary, Modifier.weight(1f))
        StatCard("Healthy", "${state.healthy}", GreenOk, Modifier.weight(1f))
        StatCard("Failed", "${state.failed}", RedFail, Modifier.weight(1f))
        StatCard("${state.pct}%", state.source.take(8), AccentBlue, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = CardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = TextMuted, letterSpacing = 0.5.sp)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Progress ────────────────────────────────────────────────────────────────
@Composable
fun ProgressIndicator(state: ScanState) {
    LinearProgressIndicator(
        progress = { state.pct / 100f },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = AccentBlue,
        trackColor = BorderColor,
    )
}

// ── Phase Steps ─────────────────────────────────────────────────────────────
@Composable
fun PhaseSteps(current: ScanPhase) {
    val phases = listOf(ScanPhase.SCANNING, ScanPhase.IP_CHECK, ScanPhase.FRONTING, ScanPhase.THROUGHPUT, ScanPhase.DONE)
    val currentIdx = phases.indexOf(current)

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        phases.forEachIndexed { idx, phase ->
            val color = when {
                idx < currentIdx -> GreenOk
                idx == currentIdx -> AccentBlue
                else -> TextMuted
            }
            val bgColor = when {
                idx < currentIdx -> GreenOk.copy(alpha = 0.15f)
                idx == currentIdx -> AccentBlue.copy(alpha = 0.15f)
                else -> Color.Transparent
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = bgColor,
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Text(if (idx < currentIdx) "✓" else "${idx + 1}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(phase.label, fontSize = 11.sp, color = color)
                }
            }
            if (idx < phases.lastIndex) {
                Text("→", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ── Overview Tab ────────────────────────────────────────────────────────────
@Composable
fun OverviewTab(state: ScanState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Phase banner
        if (state.running && state.phase != ScanPhase.IDLE) {
            item {
                Text(
                    "▶ ${state.phase.label}${if (state.phaseDetail.isNotBlank()) " — ${state.phaseDetail}" else ""}",
                    fontSize = 12.sp, color = AccentBlue, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
        // Recent logs
        items(state.logs.takeLast(10).reversed()) { log ->
            val color = when {
                log.contains("[Phase") -> AccentPurple
                log.startsWith("OK ") -> GreenOk
                log.contains("ERR") || log.contains("STOP") -> RedFail
                log.contains("Done") -> GreenOk
                else -> TextSecondary
            }
            Text("$ $log", fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
        }
        // Live IPs
        if (state.liveIps.isNotEmpty()) {
            item {
                Text("LIVE (${state.liveIps.size})", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(state.liveIps.takeLast(20).reversed()) { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                    Text(r.ip, fontSize = 12.sp, color = if (r.ok) GreenOk else TextMuted, fontFamily = FontFamily.Monospace)
                    Text("${r.ms}ms", fontSize = 10.sp, color = TextMuted)
                    if (r.code > 0) Text("[${r.code}]", fontSize = 10.sp, color = TextMuted)
                }
            }
        }
        if (!state.running && state.logs.isEmpty() && state.liveIps.isEmpty()) {
            item { Text("No activity yet. Start a scan.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(40.dp)) }
        }
    }
}

// ── Results Tab ─────────────────────────────────────────────────────────────
@Composable
fun ResultsTab(results: List<ScanResult>) {
    if (results.isEmpty()) {
        Text("No results yet", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(40.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("IP", Modifier.weight(2.5f), fontSize = 10.sp, color = TextMuted)
                Text("ms", Modifier.weight(1f), fontSize = 10.sp, color = TextMuted)
                Text("CDN", Modifier.weight(1.2f), fontSize = 10.sp, color = TextMuted)
                Text("SNI", Modifier.weight(1.5f), fontSize = 10.sp, color = TextMuted)
                Text("CC", Modifier.weight(0.7f), fontSize = 10.sp, color = TextMuted)
                Text("OK", Modifier.weight(0.6f), fontSize = 10.sp, color = TextMuted)
            }
            Divider(color = BorderColor)
        }
        items(results) { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(r.ip, Modifier.weight(2.5f), fontSize = 11.sp, color = if (r.ok) TextPrimary else TextMuted, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text("${r.ms}", Modifier.weight(1f), fontSize = 11.sp, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                Text(r.cdn.take(6), Modifier.weight(1.2f), fontSize = 11.sp, color = AccentBlue, maxLines = 1)
                Text(r.frontingSni.split(".").getOrElse(0) { "-" }, Modifier.weight(1.5f), fontSize = 10.sp, color = AccentBlue.copy(0.7f), maxLines = 1)
                Text(r.country.ifBlank { "?" }, Modifier.weight(0.7f), fontSize = 11.sp, color = if (r.country == "IR") RedFail else TextSecondary)
                Text(if (r.ok) "✓" else "✗", Modifier.weight(0.6f), fontSize = 12.sp, color = if (r.ok) GreenOk else RedFail)
            }
        }
    }
}

// ── Fronting Tab ────────────────────────────────────────────────────────────
@Composable
fun FrontingTab(results: List<ScanResult>) {
    val frontingIps = results.filter { it.ok && it.frontingOk }
    if (frontingIps.isEmpty()) {
        Text("No fronting data yet.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(40.dp))
        return
    }
    val groups = frontingIps.groupBy { "${it.cdn}||${it.frontingSni}" }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups.entries.toList()) { (_, ips) ->
            val first = ips.first()
            Surface(color = CardBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(first.cdn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Surface(shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(0.2f)) {
                            Text("${ips.size}", fontSize = 11.sp, color = AccentBlue, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("SNI: ${first.frontingSni.ifBlank { "auto" }}", fontSize = 11.sp, color = AccentBlue, fontFamily = FontFamily.Monospace)
                    if (first.frontingHost.isNotBlank() && first.frontingHost != first.frontingSni) {
                        Text("Host: ${first.frontingHost}", fontSize = 11.sp, color = AccentPurple, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ips.forEach { ip ->
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF171717), border = BorderStroke(1.dp, BorderColor)) {
                                Text(ip.ip, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Config Tab ──────────────────────────────────────────────────────────────
@Composable
fun ConfigTab(config: ScanConfig, onConfigChange: (ScanConfig) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // CDN Provider
        var expanded by remember { mutableStateOf(false) }
        ConfigLabel("CDN Provider")
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = config.cdnProvider.label,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = configFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CdnProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.label) },
                        onClick = { onConfigChange(config.copy(cdnProvider = provider)); expanded = false }
                    )
                }
            }
        }

        // Manual inputs
        if (config.cdnProvider == CdnProvider.MANUAL) {
            ConfigLabel("IPs (one per line)")
            OutlinedTextField(value = config.manualIps, onValueChange = { onConfigChange(config.copy(manualIps = it)) },
                modifier = Modifier.fillMaxWidth().height(100.dp), colors = configFieldColors(), placeholder = { Text("1.2.3.4\n5.6.7.8", color = TextMuted) })
        }
        if (config.cdnProvider == CdnProvider.CIDR) {
            ConfigLabel("CIDR Ranges (one per line)")
            OutlinedTextField(value = config.manualCidr, onValueChange = { onConfigChange(config.copy(manualCidr = it)) },
                modifier = Modifier.fillMaxWidth().height(100.dp), colors = configFieldColors(), placeholder = { Text("104.16.0.0/12", color = TextMuted) })
        }

        // Host
        ConfigLabel("Host Header")
        OutlinedTextField(value = config.host, onValueChange = { onConfigChange(config.copy(host = it)) },
            modifier = Modifier.fillMaxWidth(), colors = configFieldColors(), placeholder = { Text("e.g. speed.cloudflare.com", color = TextMuted) })

        // SNI
        ConfigLabel("SNI Override")
        OutlinedTextField(value = config.sni, onValueChange = { onConfigChange(config.copy(sni = it)) },
            modifier = Modifier.fillMaxWidth(), colors = configFieldColors(), placeholder = { Text("e.g. a248.e.akamai.net", color = TextMuted) })

        // Concurrency + Timeout
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                ConfigLabel("Concurrency")
                OutlinedTextField(value = "${config.concurrency}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(concurrency = v.coerceIn(1, 300))) } },
                    modifier = Modifier.fillMaxWidth(), colors = configFieldColors())
            }
            Column(Modifier.weight(1f)) {
                ConfigLabel("Timeout (s)")
                OutlinedTextField(value = "${config.timeout}", onValueChange = { it.toFloatOrNull()?.let { v -> onConfigChange(config.copy(timeout = v.coerceIn(1f, 20f))) } },
                    modifier = Modifier.fillMaxWidth(), colors = configFieldColors())
            }
        }

        // Max IPs + Retries
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                ConfigLabel("Max IPs")
                OutlinedTextField(value = "${config.maxIps}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v.coerceIn(10, 50000))) } },
                    modifier = Modifier.fillMaxWidth(), colors = configFieldColors())
            }
            Column(Modifier.weight(1f)) {
                ConfigLabel("Retries")
                OutlinedTextField(value = "${config.retries}", onValueChange = { it.toIntOrNull()?.let { v -> onConfigChange(config.copy(retries = v.coerceIn(0, 10))) } },
                    modifier = Modifier.fillMaxWidth(), colors = configFieldColors())
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ConfigLabel(text: String) {
    Text(text, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun configFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentBlue,
    focusedContainerColor = CardBg,
    unfocusedContainerColor = CardBg,
)

// ── Log Tab ─────────────────────────────────────────────────────────────────
@Composable
fun LogTab(logs: List<String>) {
    if (logs.isEmpty()) {
        Text("Waiting for activity...", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(40.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(logs.reversed()) { log ->
            val color = when {
                log.contains("[Phase") -> AccentPurple
                log.startsWith("OK ") -> GreenOk
                log.contains("ERR") || log.contains("STOP") -> RedFail
                log.contains("Done") -> GreenOk
                log.contains("Scan started") -> AccentBlue
                else -> TextSecondary
            }
            Text("$ $log", fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

// ── FlowRow compat ──────────────────────────────────────────────────────────
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Simple wrap layout using built-in FlowRow from Compose Foundation
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        content()
    }
}
