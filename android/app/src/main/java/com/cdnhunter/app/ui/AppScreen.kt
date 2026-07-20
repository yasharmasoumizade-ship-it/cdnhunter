package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
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
import android.content.Context
import android.net.VpnService
import androidx.compose.ui.platform.LocalContext
import com.cdnhunter.app.data.*
import com.cdnhunter.app.engine.ConfigGenerator
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.AutoIpManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Theme ────────────────────────────────────────────────────────────────────
// Dark theme
val DarkBg        = Color(0xFF111318)
val CardBg        = Color(0xFF1A1D24)
val CardBg2       = Color(0xFF23272F)
val AccentBlue    = Color(0xFF4B7BEC)
val AccentTeal    = Color(0xFF64D2FF)
val GreenOk       = Color(0xFF30D158)
val RedFail       = Color(0xFFFF453A)
val YellowWarn    = Color(0xFFFFD60A)
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextMuted     = Color(0xFF48484A)

// Light theme
val LightBg       = Color(0xFFF5F0E8)
val LightCardBg   = Color(0xFFFFFDF7)
val LightCardBg2  = Color(0xFFEDE8DC)
val LightTextPrimary = Color(0xFF1C1C1E)
val LightTextSecondary = Color(0xFF6E6E73)
val LightTextMuted = Color(0xFFAEAEB2)
val LightBorder   = Color(0xFFE5E5EA)
val GreenBorder   = Color(0xFF34C759)

// ── ANANAS Home/Connected reference palette ──────────────────────────────────
val AnanasBg       = Color(0xFF050505)
val AnanasScreenBg = Color(0xFF0B0B0D)
val AnanasCard     = Color(0xFF131316)
val AnanasCard2    = Color(0xFF151519)
val AnanasBorder   = Color(0xFF1E1F24)
val AnanasBorder2  = Color(0xFF232328)
val AnanasDivider  = Color(0xFF17171B)
val AnanasAccent   = Color(0xFF4ADE9C)
val AnanasAmber    = Color(0xFFE6A23C)
val AnanasRed      = Color(0xFFE0605C)
val AnanasTextHi   = Color(0xFFFAFAFA)
val AnanasText     = Color(0xFFF0F0F2)
val AnanasMuted    = Color(0xFF6E7078)
val AnanasFaint    = Color(0xFF3A3C44)
val AnanasVless    = Color(0xFF64D2FF)

@Composable
fun isDarkMode(): Boolean = when (LocalThemeMode.current) {
    ThemeMode.DARK   -> true
    ThemeMode.LIGHT  -> false
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}

private enum class Tab(val label: String) {
    VPN("VPN"), SETTINGS("Settings")
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
val LocalThemeMode = androidx.compose.runtime.staticCompositionLocalOf { ThemeMode.LIGHT }

// ── Saved config data class ───────────────────────────────────────────────────
data class SavedConfig(
    val id: String,
    val uri: String,
    val displayName: String,
    val proto: String,
    val address: String,
    val port: Int,
    val network: String,
    val sni: String,
)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun parseConfig(uri: String): SavedConfig? {
    val ob = ConfigUriParser.parseToOutbound(uri) ?: return null
    val proto = ob.optString("protocol", "?")
    val settings = ob.optJSONObject("settings")
    val addr = settings?.optJSONArray("vnext")?.optJSONObject(0)?.optString("address")
        ?: settings?.optJSONArray("servers")?.optJSONObject(0)?.optString("address") ?: "?"
    val port = settings?.optJSONArray("vnext")?.optJSONObject(0)?.optInt("port", 443)
        ?: settings?.optJSONArray("servers")?.optJSONObject(0)?.optInt("port", 443) ?: 443
    val ss = ob.optJSONObject("streamSettings")
    val sni = ss?.optJSONObject("tlsSettings")?.optString("serverName", "") ?: ""
    val net = ss?.optString("network", "tcp") ?: "tcp"
    val name = when (proto) {
        "trojan"  -> "Trojan"
        "vless"   -> "VLESS"
        "vmess"   -> "VMess"
        else      -> proto.replaceFirstChar { it.uppercase() }
    } + " · $addr"
    return SavedConfig(
        id = uri.hashCode().toString(),
        uri = uri, displayName = name,
        proto = proto, address = addr, port = port, network = net, sni = sni
    )
}

private fun loadConfigs(context: Context): List<SavedConfig> {
    val prefs = context.getSharedPreferences("cdnhunter_vpn", 0)
    val raw = prefs.getString("saved_configs", "") ?: ""
    if (raw.isBlank()) {
        // Migrate legacy single config
        val legacy = prefs.getString("user_config", "") ?: ""
        if (legacy.isNotBlank()) {
            val cfg = parseConfig(legacy)
            if (cfg != null) return listOf(cfg)
        }
        return emptyList()
    }
    return raw.split("\n").mapNotNull { parseConfig(it.trim()) }
}

private fun saveConfigs(context: Context, configs: List<SavedConfig>) {
    // Prevent crash with max 50 configs
    val limited = configs.take(50)
    context.getSharedPreferences("cdnhunter_vpn", 0)
        .edit().putString("saved_configs", limited.joinToString("\n") { it.uri }).apply()
}

private fun formatElapsed(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatSpeed(kbps: Double): Pair<String, String> =
    if (kbps >= 1024.0) "%.1f".format(kbps / 1024.0) to "MB/s"
    else "%.0f".format(kbps) to "KB/s"

// ── MAIN APP ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScreen(
    state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onCopyIps: () -> Unit,
    onUpdateRanges: () -> Unit = {}, onExport: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("cdnhunter_ui", 0) }
    var themeMode by remember { mutableStateOf(ThemeMode.valueOf(uiPrefs.getString("theme_mode", "LIGHT") ?: "LIGHT")) }
    var autoIpEnabled by remember { mutableStateOf(AutoIpManager.enabled.get()) }
    val pagerState = rememberPagerState(initialPage = 0) { Tab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeMode provides themeMode) {
    val onVpnTab = Tab.entries[pagerState.currentPage] == Tab.VPN
    Box(
        Modifier.fillMaxSize()
            .background(
                when {
                    onVpnTab      -> Brush.verticalGradient(listOf(AnanasBg, AnanasScreenBg, AnanasBg))
                    isDarkMode()  -> Brush.verticalGradient(listOf(Color(0xFF0D1018), Color(0xFF111318), DarkBg))
                    else          -> Brush.verticalGradient(listOf(Color(0xFFF5F0E8), Color(0xFFFAF6EE), LightBg))
                }
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (Tab.entries[page]) {
                        Tab.VPN      -> VpnTab(autoIpEnabled) // full-bleed, owns its own edge padding
                        Tab.SETTINGS -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            ToolsTab(state.results, config, onConfigChange, onStart, onCopyIps, onUpdateRanges, onExport, themeMode, autoIpEnabled, { autoIpEnabled = it }) { m -> themeMode = m; uiPrefs.edit().putString("theme_mode", m.name).apply() }
                        }
                    }
                }
            }
            BottomNavBar(Tab.entries[pagerState.currentPage], forceDark = onVpnTab) { tab ->
                coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            }
        }
    }
    } // CompositionLocalProvider
}

// ── Bottom Nav ────────────────────────────────────────────────────────────────
@Composable
private fun BottomNavBar(current: Tab, forceDark: Boolean = false, onSelect: (Tab) -> Unit) {
    val icons = mapOf(
        Tab.VPN      to Icons.Rounded.Bolt,
        Tab.SETTINGS to Icons.Rounded.Tune
    )
    val dark = forceDark || isDarkMode()
    val selectedColor = if (forceDark) AnanasAccent else AccentBlue
    val unselectedColor = if (forceDark) AnanasMuted else if (dark) Color(0xFF4B5563) else Color(0xFFBBBBBB)
    val bgColor = if (forceDark) AnanasCard2.copy(alpha = 0.97f) else if (dark) Color(0xFF1A1D24).copy(alpha = 0.95f) else Color(0xFFFFFDF7).copy(alpha = 0.95f)
    val borderColor = if (forceDark) AnanasBorder2 else if (dark) Color(0xFF2C2F38) else Color(0xFFE0DDD5)
    val selectedBg = if (forceDark) AnanasAccent.copy(0.14f) else AccentBlue.copy(0.12f)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Shadow layer
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(if (dark) Color(0xFF000000).copy(0.3f) else Color(0xFF000000).copy(0.08f))
                .padding(bottom = 2.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(30.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val color = if (selected) selectedColor else unselectedColor
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) selectedBg else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icons[tab]!!, null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(tab.label, fontSize = 10.sp, color = color,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}


// ── VPN TAB (Home / Connected — ANANAS reference) ──────────────────────────────
@Composable
private fun VpnTab(autoIpEnabled: Boolean = false) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val clip    = LocalClipboardManager.current

    var configs    by remember { mutableStateOf(loadConfigs(context)) }
    var connected  by remember { mutableStateOf(CdnVpnService.isRunning.get()) }
    var connecting by remember { mutableStateOf(false) }
    var activeId   by remember {
        mutableStateOf(
            context.getSharedPreferences("cdnhunter_vpn", 0).getString("active_config_id", "") ?: ""
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }

    var connectedSinceMs by remember { mutableStateOf(0L) }
    var elapsedSec        by remember { mutableStateOf(0L) }
    var downloadKBps      by remember { mutableStateOf(0.0) }
    var uploadKBps        by remember { mutableStateOf(0.0) }

    // Poll VPN status + derive live throughput from CdnVpnService's cumulative byte counters
    LaunchedEffect(Unit) {
        var lastDown = CdnVpnService.downloadBytes
        var lastUp   = CdnVpnService.uploadBytes
        while (true) {
            val vpnRunning = CdnVpnService.isRunning.get()
            connected = if (autoIpEnabled) {
                vpnRunning && AutoIpManager.currentIp.isNotBlank()
            } else vpnRunning

            if (connected) {
                connecting = false
                if (connectedSinceMs == 0L) connectedSinceMs = System.currentTimeMillis()
                elapsedSec = (System.currentTimeMillis() - connectedSinceMs) / 1000

                val curDown = CdnVpnService.downloadBytes
                val curUp   = CdnVpnService.uploadBytes
                downloadKBps = (curDown - lastDown).coerceAtLeast(0L) / 1024.0
                uploadKBps   = (curUp - lastUp).coerceAtLeast(0L) / 1024.0
                lastDown = curDown; lastUp = curUp
            } else {
                connectedSinceMs = 0L; elapsedSec = 0L; downloadKBps = 0.0; uploadKBps = 0.0
                lastDown = CdnVpnService.downloadBytes; lastUp = CdnVpnService.uploadBytes
            }

            if (vpnRunning && autoIpEnabled && !AutoIpManager.enabled.get()) {
                delay(3000)
                if (CdnVpnService.isRunning.get() && !AutoIpManager.enabled.get()) {
                    AutoIpManager.start(context)
                }
            }
            delay(1000)
        }
    }
    LaunchedEffect(connecting) {
        if (connecting) { delay(15000); if (!CdnVpnService.isRunning.get()) connecting = false }
    }

    fun connectConfig(cfg: SavedConfig) {
        if (connected && cfg.id == activeId) {
            CdnVpnService.stop(context); AutoIpManager.stop(); connected = false
        } else {
            if (connected) { CdnVpnService.stop(context); AutoIpManager.stop(); connected = false }
            activeId = cfg.id
            context.getSharedPreferences("cdnhunter_vpn", 0)
                .edit()
                .putString("user_config", cfg.uri)
                .putString("active_config_id", cfg.id)
                .apply()
            connecting = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val act = context as? com.cdnhunter.app.MainActivity
                if (act != null) act.requestVpnPermissionAndConnect()
                else CdnVpnService.start(context)
            } catch (e: Exception) {
                connecting = false
                android.widget.Toast.makeText(context, "Failed: ${e.message?.take(40)}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteConfig(cfg: SavedConfig) {
        if (cfg.id == activeId && connected) { CdnVpnService.stop(context); AutoIpManager.stop(); connected = false }
        val updated = configs.filter { it.id != cfg.id }
        configs = updated
        saveConfigs(context, updated)
        if (cfg.id == activeId) activeId = ""
    }

    val activeConfig  = configs.find { it.id == activeId } ?: configs.firstOrNull()
    val otherConfigs  = configs.filter { it.id != activeConfig?.id }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        if (configs.isEmpty()) {
            EmptyHomeState { showAddDialog = true }
        } else {
            Column(Modifier.fillMaxSize()) {
                // ── Top bar ──────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnanasIconButton(Icons.Rounded.Menu) { /* TODO: side drawer */ }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Rounded.Shield, null, tint = AnanasTextHi, modifier = Modifier.size(16.dp))
                        Text("ANANAS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.2).sp)
                    }
                    AnanasIconButton(Icons.Rounded.Person) { /* TODO: profile */ }
                }

                // ── Power button + status ────────────────────────────────
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PowerButton(
                        connected = connected,
                        connecting = connecting,
                        onClick = { activeConfig?.let { connectConfig(it) } }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when { connected -> "Protected"; connecting -> "Connecting…"; else -> "Not protected" },
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (connected) formatElapsed(elapsedSec) else "Tap to connect",
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AnanasMuted, letterSpacing = 0.3.sp
                    )
                }

                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    activeConfig?.let { cfg ->
                        item(key = "active-${cfg.id}") {
                            ServerRow(
                                cfg = cfg, isActive = true, connected = connected,
                                onClick = { connectConfig(cfg) },
                                onCopy  = { clip.setText(AnnotatedString(cfg.uri)) }
                            )
                        }
                    }
                    item(key = "stats") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatBox(Icons.Rounded.ArrowDownward, "DOWNLOAD", downloadKBps, AnanasAccent, Modifier.weight(1f))
                            StatBox(Icons.Rounded.ArrowUpward, "UPLOAD", uploadKBps, AnanasText, Modifier.weight(1f))
                        }
                    }
                    if (otherConfigs.isNotEmpty()) {
                        item(key = "quick-switch-hdr") {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("QUICK SWITCH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                            }
                        }
                        items(otherConfigs, key = { "row-${it.id}" }) { cfg ->
                            ServerRow(
                                cfg = cfg, isActive = false, connected = false,
                                onClick = { connectConfig(cfg) },
                                onCopy  = { clip.setText(AnnotatedString(cfg.uri)) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = AnanasCard2,
            contentColor   = AnanasTextHi,
            shape          = RoundedCornerShape(16.dp)
        ) { Icon(Icons.Rounded.Add, contentDescription = "Add config") }
    }

    if (showAddDialog) {
        AddConfigDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { uri ->
                val cfg = parseConfig(uri)
                if (cfg != null) {
                    val updated = configs + cfg
                    configs = updated
                    saveConfigs(context, updated)
                    showAddDialog = false
                } else {
                    android.widget.Toast.makeText(context, "Invalid config URI", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// ── Power button: pulsing rings + rotating sweep arc (ANANAS reference) ────────
@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "power")
    val pulse1 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "pulse1"
    )
    val pulse2 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2500, delayMillis = 1250, easing = LinearEasing)), label = "pulse2"
    )
    val sweepRotation by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "sweep"
    )

    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        // ambient glow
        Box(
            Modifier.size(160.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(AnanasAccent.copy(0.14f), Color.Transparent)))
        )

        // pulsing outward rings — only while connected
        if (connected) {
            listOf(pulse1, pulse2).forEach { p ->
                Box(
                    Modifier
                        .size(160.dp)
                        .scale(0.75f + p * 0.45f)
                        .clip(CircleShape)
                        .border(1.dp, AnanasAccent.copy(alpha = (1f - p) * 0.6f), CircleShape)
                )
            }
        }

        // static guide rings
        Box(Modifier.size(140.dp).clip(CircleShape).border(1.dp, Color(0xFF1C1C20), CircleShape))
        Box(Modifier.size(106.dp).clip(CircleShape).border(1.dp, Color(0xFF1C1C20), CircleShape))

        // thin rotating sweep arc while connected
        if (connected) {
            Canvas(Modifier.size(140.dp).rotate(sweepRotation)) {
                drawArc(
                    color = AnanasAccent,
                    startAngle = 0f, sweepAngle = 26f, useCenter = false,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // core power button
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color(0xFF101210))
                .border(1.5.dp, if (connected) Color(0xFF2A4638) else AnanasBorder2, CircleShape)
                .clickable(enabled = !connecting) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                CircularProgressIndicator(color = AnanasAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    Icons.Rounded.PowerSettingsNew, null,
                    tint = if (connected) AnanasAccent else AnanasMuted,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

// ── Server row: active/selected card + quick-switch rows ───────────────────────
@Composable
private fun ServerRow(
    cfg: SavedConfig, isActive: Boolean, connected: Boolean,
    onClick: () -> Unit, onCopy: () -> Unit,
) {
    val badgeColor = when (cfg.proto.lowercase()) {
        "trojan" -> AnanasAccent
        "vless"  -> AnanasVless
        "vmess"  -> AnanasAmber
        else     -> AnanasMuted
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnanasCard)
            .border(1.dp, if (isActive) AnanasBorder2 else AnanasBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        if (isActive) {
            Box(
                Modifier.fillMaxHeight().width(3.dp).align(Alignment.CenterStart)
                    .background(AnanasAccent, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
        }
        Column(Modifier.padding(start = if (isActive) 19.dp else 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(badgeColor.copy(0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cfg.proto.take(1).uppercase(), color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column {
                        Text(cfg.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                        Spacer(Modifier.height(3.dp))
                        if (isActive && connected) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(AnanasAccent))
                                Text("CONNECTED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AnanasAccent, letterSpacing = 0.2.sp)
                            }
                        } else {
                            Text("Tap to connect", fontSize = 11.5.sp, fontWeight = FontWeight.Normal, color = AnanasMuted)
                        }
                    }
                }
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                        .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(9.dp))
                        .clickable { onCopy() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ContentCopy, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(15.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(cfg.proto.uppercase(), cfg.network.uppercase()).forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(AnanasCard2)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) { Text(tag, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.2.sp) }
                }
            }
        }
    }
}

// ── Download / Upload stat box ──────────────────────────────────────────────────
@Composable
private fun StatBox(icon: ImageVector, label: String, kbps: Double, accentColor: Color, modifier: Modifier) {
    val (value, unit) = formatSpeed(kbps)
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(AnanasCard)
            .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(12.dp))
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.3.sp)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AnanasMuted)
            }
        }
    }
}

// ── Icon button (top bar) ───────────────────────────────────────────────────────
@Composable
private fun AnanasIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(16.dp)) }
}

// ── Empty state (ANANAS styled) ─────────────────────────────────────────────────
@Composable
private fun EmptyHomeState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(AnanasCard)
                    .border(1.dp, AnanasBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, null, tint = AnanasMuted, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("No configs yet", fontSize = 15.sp, color = AnanasTextHi, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to add a trojan / vless / vmess config", fontSize = 12.sp, color = AnanasMuted)
        }
    }
}
// ── (legacy, unused after ANANAS restyle — safe to delete) ─────────────────────
// ── Config Card ───────────────────────────────────────────────────────────────
@Composable
private fun ConfigCard(
    cfg: SavedConfig,
    isActive: Boolean,
    isExpanded: Boolean,
    connected: Boolean,
    connecting: Boolean,
    onTap: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val dark = isDarkMode()
    val borderColor = when {
        connected  -> AccentBlue.copy(if (dark) 0.6f else 0.8f)
        connecting -> YellowWarn.copy(0.5f)
        isActive   -> AccentBlue.copy(0.5f)
        else       -> if (dark) Color(0xFF38383A).copy(0.3f) else Color(0xFFDDDDDD)
    }
    val bgColor = when {
        connected  -> AccentBlue.copy(if (dark) 0.08f else 0.05f)
        connecting -> YellowWarn.copy(0.05f)
        isActive   -> AccentBlue.copy(if (dark) 0.06f else 0.04f)
        else       -> if (dark) CardBg.copy(0.7f) else LightCardBg
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onTap() }
    ) {
        // ── Row 1: icon + name + status dot ──
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Protocol badge
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) AccentBlue.copy(0.15f) else if (isDarkMode()) CardBg2 else LightCardBg2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    cfg.proto.take(2).uppercase(),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (isActive) AccentBlue else TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cfg.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkMode()) TextPrimary else LightTextPrimary)
                Text(
                    buildString {
                        append(cfg.network.uppercase())
                        if (cfg.sni.isNotBlank()) append(" · ${cfg.sni}")
                        append(" · :${cfg.port}")
                    },
                    fontSize = 11.sp, color = TextSecondary
                )
            }
            // Status indicator
            when {
                connecting -> {
                    val pulse by rememberInfiniteTransition(label = "csp").animateFloat(
                        0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "cspf"
                    )
                    Box(Modifier.size(10.dp).clip(CircleShape).background(YellowWarn.copy(pulse)))
                }
                connected  -> Box(Modifier.size(10.dp).clip(CircleShape).background(AccentBlue))
                else       -> Box(Modifier.size(10.dp).clip(CircleShape).background(TextMuted))
            }
        }

        // ── Row 2: expanded connect button ────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut()
        ) {
            Column {
                Divider(color = Color(0xFF38383A).copy(0.3f), thickness = 0.5.dp)
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Connect / Disconnect button
                    val dark = isDarkMode()
                    val btnBg = when {
                        connected  -> if (dark) Color(0xFF2A0A0A) else Color(0xFFFFEEEE)
                        connecting -> if (dark) Color(0xFF1A1800) else Color(0xFFFFF8E1)
                        else       -> if (dark) Color(0xFF0F1A2E) else Color(0xFFEEF2FF)
                    }
                    val btnBorder = when {
                        connected  -> RedFail.copy(0.4f)
                        connecting -> YellowWarn.copy(0.4f)
                        else       -> AccentBlue.copy(0.4f)
                    }
                    val btnTextColor = when {
                        connected  -> RedFail
                        connecting -> if (dark) Color(0xFFFFD700) else Color(0xFFB8860B)
                        else       -> AccentBlue
                    }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(btnBg)
                            .border(1.dp, btnBorder, RoundedCornerShape(14.dp))
                            .clickable { onConnect() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connecting) {
                                val rot by rememberInfiniteTransition(label = "cb").animateFloat(
                                    0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "cbr"
                                )
                                Icon(Icons.Rounded.Sync, null, tint = btnTextColor,
                                    modifier = Modifier.size(16.dp).rotate(rot))
                            } else {
                                Icon(
                                    if (connected) Icons.Rounded.PowerSettingsNew else Icons.Rounded.Bolt,
                                    null, tint = btnTextColor, modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                when { connected -> "Disconnect"; connecting -> "Connecting..."; else -> "Connect" },
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = btnTextColor
                            )
                        }
                    }
                    // Delete button
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(RedFail.copy(0.08f))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = RedFail, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Add Config Dialog ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConfigDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val clip    = LocalClipboardManager.current
    var uri     by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CardBg,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text("Add Config", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste a trojan://, vless://, or vmess:// URI", fontSize = 13.sp, color = TextSecondary)
                // URI input
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg2)
                ) {
                    TextField(
                        value = uri,
                        onValueChange = {
                            uri = it
                            val cfg = if (it.isNotBlank()) parseConfig(it.trim()) else null
                            preview = if (cfg != null) "${cfg.proto.uppercase()} · ${cfg.address}:${cfg.port}" else if (it.isNotBlank()) "Invalid URI" else ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("trojan://...", fontSize = 12.sp, color = TextMuted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            cursorColor             = AccentBlue,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 4
                    )
                }
                // Paste from clipboard button
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(AccentBlue.copy(0.12f))
                        .clickable {
                            val t = try { clip.getText()?.text ?: "" } catch (_: Exception) { "" }
                            if (t.startsWith("trojan://") || t.startsWith("vless://") || t.startsWith("vmess://")) {
                                uri = t
                                val cfg = parseConfig(t.trim())
                                preview = if (cfg != null) "${cfg.proto.uppercase()} · ${cfg.address}:${cfg.port}" else "Invalid URI"
                            } else {
                                android.widget.Toast.makeText(
                                    /* context via preview string hack below */ null as android.content.Context?,
                                    "No valid URI in clipboard", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ContentPaste, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Text("Paste from clipboard", fontSize = 12.sp, color = AccentBlue)
                    }
                }
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        fontSize = 12.sp,
                        color = if (preview == "Invalid URI") RedFail else AccentTeal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(AccentBlue)
                    .clickable { if (uri.isNotBlank()) onAdd(uri.trim()) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Add", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(CardBg2)
                    .clickable { onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Cancel", fontSize = 14.sp, color = TextSecondary)
            }
        }
    )
}

// ── SCANNER TAB ───────────────────────────────────────────────────────────────
@Composable
private fun ScannerTab(state: ScanState, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    val dark = isDarkMode()

    // Set default concurrency to 100 silently
    LaunchedEffect(Unit) {
        if (config.concurrency != 100) onConfigChange(config.copy(concurrency = 100))
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))

        // ── Header ─────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scanner", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = if (dark) TextPrimary else LightTextPrimary)
                Box(Modifier.height(20.dp)) {
                    Text(
                        if (state.running) state.phaseDetail.take(32) else "Ready to scan",
                        fontSize = 13.sp,
                        color = if (state.running) AccentBlue else if (dark) TextSecondary else LightTextSecondary
                    )
                }
            }
            // Scan button top-right
            if (state.running) {
                val pulse by rememberInfiniteTransition(label = "sp").animateFloat(
                    1f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "spf"
                )
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).scale(pulse).clip(CircleShape).background(RedFail.copy(0.1f)))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(RedFail, Color(0xFF8A1B1B))))
                            .clickable { onStop() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Stop, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            } else {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(AccentBlue, Color(0xFF1A4FAD))))
                            .clickable { onStart() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Radar, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Stats ──────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCard("${state.scanned}", "Scanned", AccentBlue, Modifier.weight(1f))
            GlassCard("${state.healthy}", "Healthy", GreenOk, Modifier.weight(1f))
            GlassCard("${state.failed}", "Failed", RedFail, Modifier.weight(1f))
        }

        if (state.running) {
            Spacer(Modifier.height(12.dp))
            @Suppress("DEPRECATION")
            LinearProgressIndicator(
                progress = state.pct / 100f,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = AccentBlue,
                trackColor = if (dark) CardBg2 else LightCardBg2
            )
            Spacer(Modifier.height(4.dp))
            Text("${state.pct}%", fontSize = 11.sp,
                color = if (dark) TextSecondary else LightTextSecondary)
        }

        Spacer(Modifier.height(16.dp))

        // ── CDN Provider ───────────────────────────────────────────────────
        GlassBox(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("CDN PROVIDER", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (dark) TextSecondary else LightTextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CdnProvider.entries.forEach { p ->
                        val sel = config.cdnProvider == p
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (sel) AccentBlue.copy(0.15f) else if (dark) CardBg2 else LightCardBg2)
                                .border(1.5.dp, if (sel) AccentBlue.copy(0.7f) else if (dark) Color.Transparent else LightBorder, RoundedCornerShape(12.dp))
                                .clickable { onConfigChange(config.copy(cdnProvider = p)) }
                                .padding(14.dp, 8.dp)
                        ) {
                            Text(p.label, fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) AccentBlue else if (dark) TextSecondary else LightTextSecondary)
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
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Max IPs ────────────────────────────────────────────────────────
        GlassBox(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Max IPs to scan", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (dark) TextPrimary else LightTextPrimary)
                    Text("Higher = slower but more results", fontSize = 11.sp,
                        color = if (dark) TextSecondary else LightTextSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(90.dp)) {
                    ConfigField("${config.maxIps}", {
                        it.toIntOrNull()?.let { v -> onConfigChange(config.copy(maxIps = v)) }
                    }, "3000")
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}


// ── RESULTS TAB ───────────────────────────────────────────────────────────────
@Composable
private fun ResultsTab(results: List<ScanResult>) {
    val clip   = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }

    if (results.isEmpty()) { EmptyState(Icons.Rounded.Search, "No results yet. Start a scan!"); return }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(results, key = { it.ip }) { r ->
                var copied by remember { mutableStateOf(false) }
                val bg by animateColorAsState(if (copied) GreenOk.copy(0.12f) else if (isDarkMode()) CardBg.copy(0.7f) else LightCardBg, tween(300), label = "")
                LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
                        .border(1.5.dp, if (copied) GreenOk.copy(0.4f) else if (isDarkMode()) Color(0xFF38383A).copy(0.3f) else LightBorder, RoundedCornerShape(16.dp))
                        .clickable { clip.setText(AnnotatedString(r.ip)); haptic.performHapticFeedback(HapticFeedbackType.LongPress); copied = true }
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.ok) GreenOk else RedFail))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.ip, fontSize = 14.sp, color = if (isDarkMode()) TextPrimary else LightTextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                            val regionText = buildString {
                                append(r.cdn)
                                if (r.country.isNotBlank()) { append(" • ${r.country}"); if (r.city.isNotBlank()) append(" - ${r.city}") }
                            }
                            Text(regionText, fontSize = 11.sp, color = AccentBlue)
                        }
                        if (copied) Text("✓", fontSize = 18.sp, color = GreenOk, fontWeight = FontWeight.Bold)
                        else Column(horizontalAlignment = Alignment.End) {
                            Text("${r.ms}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = when { r.ms < 200 -> GreenOk; r.ms < 400 -> YellowWarn; else -> RedFail })
                            if (r.kbps > 0) Text("${r.kbps.toInt()} kB/s", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
        if (healthy.isNotEmpty()) {
            FloatingActionButton(
                onClick = { clip.setText(AnnotatedString(healthy.joinToString("\n") { it.ip })); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = AccentBlue, contentColor = Color.White
            ) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all IPs") }
        }
    }
}

// ── TOOLS TAB ─────────────────────────────────────────────────────────────────
@Composable
private fun ToolsTab(
    results: List<ScanResult>, config: ScanConfig, onConfigChange: (ScanConfig) -> Unit,
    onStart: () -> Unit, onCopyIps: () -> Unit, onUpdateRanges: () -> Unit, onExport: () -> Unit,
    currentTheme: ThemeMode = ThemeMode.LIGHT,
    autoIpState: Boolean = false,
    onAutoIpChange: (Boolean) -> Unit = {},
    onThemeChange: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val clip    = LocalClipboardManager.current
    val haptic  = LocalHapticFeedback.current
    val healthy = results.filter { it.ok }
    var fragment by remember {
        mutableStateOf(context.getSharedPreferences("cdnhunter_vpn", 0).getBoolean("fragment_enabled", true))
    }
    val autoIp = autoIpState
    var showXrayLog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)) {

        item { Text("Tools", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode()) TextPrimary else LightTextPrimary) }

        // ── Appearance ────────────────────────────────────────────────────
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("APPEARANCE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkMode()) TextSecondary else LightTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                            Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                            Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.SettingsBrightness)
                        ).forEach { (mode, label, icon) ->
                            val sel = currentTheme == mode
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) AccentBlue.copy(0.15f) else if (isDarkMode()) CardBg2 else LightCardBg2)
                                    .border(1.dp, if (sel) AccentBlue.copy(0.7f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { onThemeChange(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(icon, null, tint = if (sel) AccentBlue else if (isDarkMode()) TextSecondary else LightTextSecondary, modifier = Modifier.size(18.dp))
                                    Text(label, fontSize = 11.sp, color = if (sel) AccentBlue else if (isDarkMode()) TextSecondary else LightTextSecondary, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── VPN Settings ──────────────────────────────────────────────────
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("VPN SETTINGS", fontSize = 11.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fragment (DPI bypass)", fontSize = 14.sp, color = if (isDarkMode()) TextPrimary else LightTextPrimary, fontWeight = FontWeight.Medium)
                            Text("Splits TLS hello. OFF for xhttp/gRPC", fontSize = 11.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary)
                        }
                        Switch(
                            checked = fragment,
                            onCheckedChange = {
                                fragment = it
                                context.getSharedPreferences("cdnhunter_vpn", 0).edit().putBoolean("fragment_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, uncheckedTrackColor = if (isDarkMode()) CardBg2 else LightCardBg2)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Divider(color = Color(0xFF38383A).copy(0.3f), thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-IP", fontSize = 14.sp, color = if (isDarkMode()) TextPrimary else LightTextPrimary, fontWeight = FontWeight.Medium)
                            Text("Scan + pick best IP, switch if slow", fontSize = 11.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary)
                        }
                        Switch(
                            checked = autoIp,
                            onCheckedChange = {
                                onAutoIpChange(it)
                                if (it && CdnVpnService.isRunning.get()) AutoIpManager.start(context) else AutoIpManager.stop()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, uncheckedTrackColor = if (isDarkMode()) CardBg2 else LightCardBg2)
                        )
                    }
                    if (autoIp) {
                        Spacer(Modifier.height(10.dp))
                        Divider(color = Color(0xFF38383A).copy(0.3f), thickness = 0.5.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Status: ${AutoIpManager.status}", fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.Medium)
                        if (AutoIpManager.currentIp.isNotBlank())
                            Text("Active: ${AutoIpManager.currentIp}", fontSize = 11.sp, color = AccentTeal, fontFamily = FontFamily.Monospace)
                        if (AutoIpManager.ipPool.isNotEmpty())
                            Text("Pool: ${AutoIpManager.ipPool.size} IPs", fontSize = 10.sp, color = if (isDarkMode()) TextMuted else LightTextMuted)
                    }
                }
            }
        }

        // ── Xray Log ──────────────────────────────────────────────────────
        item {
            GlassBox(Modifier.fillMaxWidth().clickable { showXrayLog = !showXrayLog }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Terminal, null, tint = YellowWarn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Xray Log", fontSize = 13.sp, color = YellowWarn, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (isDarkMode()) CardBg2 else LightCardBg2)
                                .border(1.dp, if (isDarkMode()) Color.Transparent else LightBorder, RoundedCornerShape(8.dp))
                                .clickable { clip.setText(AnnotatedString(CdnVpnService.xrayLog.ifBlank { "(empty)" })) }
                                .padding(8.dp, 4.dp)
                        ) { Text("Copy", fontSize = 11.sp, color = AccentTeal) }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (showXrayLog) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            null, tint = TextSecondary, modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(visible = showXrayLog) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                CdnVpnService.xrayLog.ifBlank { "No log yet." },
                                fontSize = 10.sp, color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }

        // ── Maintenance ───────────────────────────────────────────────────
        item {
            GlassBox(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("MAINTENANCE", fontSize = 11.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkMode()) CardBg2 else LightCardBg2)
                            .border(1.5.dp, if (isDarkMode()) Color.Transparent else LightBorder, RoundedCornerShape(12.dp))
                            .clickable { onUpdateRanges() }
                            .padding(14.dp, 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Update CDN Ranges", fontSize = 14.sp, color = if (isDarkMode()) TextPrimary else LightTextPrimary)
                                Text("Fetch latest IP ranges from CDN providers", fontSize = 11.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Shared Components ─────────────────────────────────────────────────────────
@Composable
private fun GlassCard(value: String, label: String, color: Color, modifier: Modifier) {
    val bgColor = if (isDarkMode()) color.copy(0.08f) else color.copy(0.05f)
    val borderColor = if (isDarkMode()) color.copy(0.15f) else color.copy(0.1f)
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = if (isDarkMode()) TextSecondary else LightTextSecondary)
        }
    }
}

@Composable
private fun GlassBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bgColor = if (isDarkMode()) CardBg.copy(0.7f) else LightCardBg
    val borderColor = if (isDarkMode()) Color(0xFF38383A).copy(0.4f) else Color(0xFFDDDDDD)
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun PhaseIndicator(current: ScanPhase) {
    val phases = ScanPhase.entries.filter { it != ScanPhase.IDLE }
    val idx = phases.indexOf(current)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        phases.forEachIndexed { i, p ->
            val done = i < idx; val active = i == idx
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(when { done -> GreenOk.copy(0.12f); active -> AccentBlue.copy(0.15f); else -> if (isDarkMode()) CardBg2 else LightCardBg2 }).border(1.dp, when { done -> GreenOk.copy(0.2f); active -> AccentBlue.copy(0.3f); else -> if (isDarkMode()) Color.Transparent else LightBorder }, RoundedCornerShape(20.dp)).padding(10.dp, 6.dp)) {
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
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isDarkMode()) CardBg2 else LightCardBg2).border(1.dp, if (isDarkMode()) Color.Transparent else LightBorder, RoundedCornerShape(10.dp))) {
        TextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = if (isDarkMode()) TextMuted else LightTextMuted) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedTextColor = if (isDarkMode()) TextPrimary else LightTextPrimary, unfocusedTextColor = if (isDarkMode()) TextPrimary else LightTextPrimary, cursorColor = AccentBlue,
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
// Thu Jun 11 14:43:19 +0330 2026
