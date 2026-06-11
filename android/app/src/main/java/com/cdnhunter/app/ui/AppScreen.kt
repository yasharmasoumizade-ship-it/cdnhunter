package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun isDarkMode(): Boolean = when (LocalThemeMode.current) {
    ThemeMode.DARK   -> true
    ThemeMode.LIGHT  -> false
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}

private enum class Tab(val label: String) {
    VPN("VPN"), SCANNER("Scanner"), RESULTS("Results"), TOOLS("Tools")
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

    LaunchedEffect(state.phase, state.results.size) {
        if (state.phase == ScanPhase.DONE && state.results.isNotEmpty()) {
            pagerState.animateScrollToPage(Tab.RESULTS.ordinal)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalThemeMode provides themeMode) {
    Box(
        Modifier.fillMaxSize()
            .background(if (isDarkMode()) Brush.verticalGradient(listOf(Color(0xFF0D1018), Color(0xFF111318), DarkBg))
                        else Brush.verticalGradient(listOf(Color(0xFFF5F0E8), Color(0xFFFAF6EE), LightBg)))
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        when (Tab.entries[page]) {
                            Tab.VPN      -> VpnTab(autoIpEnabled)
                            Tab.SCANNER  -> ScannerTab(state, config, onConfigChange, onStart, onStop)
                            Tab.RESULTS  -> ResultsTab(state.results)
                            Tab.TOOLS    -> ToolsTab(state.results, config, onConfigChange, onStart, onCopyIps, onUpdateRanges, onExport, themeMode, autoIpEnabled, { autoIpEnabled = it }) { m -> themeMode = m; uiPrefs.edit().putString("theme_mode", m.name).apply() }
                        }
                    }
                }
            }
            BottomNavBar(Tab.entries[pagerState.currentPage]) { tab ->
                coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            }
        }
    }
    } // CompositionLocalProvider
}

// ── Bottom Nav ────────────────────────────────────────────────────────────────
@Composable
private fun BottomNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    val icons = mapOf(
        Tab.VPN     to Icons.Rounded.Bolt,
        Tab.SCANNER to Icons.Rounded.MyLocation,
        Tab.RESULTS to Icons.Rounded.FormatListBulleted,
        Tab.TOOLS   to Icons.Rounded.Tune
    )
    val bgColor = if (isDarkMode()) CardBg.copy(0.85f) else LightCardBg.copy(0.9f)
    val selectedColor = if (isDarkMode()) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val unselectedColor = if (isDarkMode()) Color(0xFF4B5563) else Color(0xFFBBBBBB)
    
    Box(Modifier.fillMaxWidth().background(bgColor)) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val color = if (selected) selectedColor else unselectedColor
                Column(
                    Modifier.clickable { onSelect(tab) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icons[tab]!!, null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.sp, color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ── VPN TAB ───────────────────────────────────────────────────────────────────
@Composable
private fun VpnTab(autoIpEnabled: Boolean = false) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    var configs   by remember { mutableStateOf(loadConfigs(context)) }
    var connected by remember { mutableStateOf(CdnVpnService.isRunning.get()) }
    var connecting by remember { mutableStateOf(false) }
    var activeId  by remember {
        mutableStateOf(
            context.getSharedPreferences("cdnhunter_vpn", 0).getString("active_config_id", "") ?: ""
        )
    }
    var expandedId by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    // Poll VPN status
    LaunchedEffect(Unit) {
        while (true) {
            val vpnRunning = CdnVpnService.isRunning.get()
            connected = if (AutoIpManager.enabled.get()) {
                vpnRunning && AutoIpManager.currentIp.isNotBlank()
            } else {
                vpnRunning
            }
            if (connected) connecting = false
            if (vpnRunning && autoIpEnabled && !AutoIpManager.enabled.get()) {
                AutoIpManager.start(context)
                android.util.Log.i("VpnTab", "Auto-starting AutoIP")
            }
            delay(1000)
        }
    }
    LaunchedEffect(connecting) {
        if (connecting) { delay(15000); if (!CdnVpnService.isRunning.get()) connecting = false }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(8.dp))

                        // ── Config list ────────────────────────────────────────────────
            if (configs.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddCircleOutline, null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("No configs yet", fontSize = 16.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("Tap + to add a trojan/vless/vmess config", fontSize = 13.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    userScrollEnabled = true
                ) {
                    items(configs, key = { it.id }) { cfg ->
                        val isActive   = cfg.id == activeId
                        val isExpanded = cfg.id == expandedId

                        ConfigCard(
                            cfg         = cfg,
                            isActive    = isActive,
                            isExpanded  = isExpanded,
                            connected   = connected && isActive,
                            connecting  = connecting && isActive,
                            onTap       = { expandedId = if (isExpanded) "" else cfg.id },
                            onConnect   = {
                                if (connected && isActive) {
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
                            },
                            onDelete    = {
                                if (isActive && connected) { CdnVpnService.stop(context); connected = false }
                                val updated = configs.filter { it.id != cfg.id }
                                configs = updated
                                saveConfigs(context, updated)
                                if (isActive) activeId = ""
                            }
                        )
                    }
                }
            }
        }

        // ── FAB ─────────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.padding(16.dp),
                containerColor = AccentBlue,
                contentColor   = Color.White,
                shape          = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add config")
            }
        }
    }

    // ── Add config dialog ────────────────────────────────────────────────────
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
