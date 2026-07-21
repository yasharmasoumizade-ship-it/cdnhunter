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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
val DarkBg        = Color(0xFF0B0B0D)
val CardBg        = Color(0xFF131316)
val CardBg2       = Color(0xFF1E1F24)
val AccentBlue    = Color(0xFF4ADE9C)
val AccentTeal    = Color(0xFF64D2FF)
val GreenOk       = Color(0xFF30D158)
val RedFail       = Color(0xFFFF453A)
val YellowWarn    = Color(0xFFFFD60A)
val TextPrimary   = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFF6E7078)
val TextMuted     = Color(0xFF3A3C44)

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
    var autoIpEnabled by remember { mutableStateOf(AutoIpManager.enabled.get()) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(AnanasBg, AnanasScreenBg, AnanasBg)))
    ) {
        VpnTab(autoIpEnabled) // full-bleed root screen; owns internal navigation (Home/Locations/My Configs/Settings/Profile)
    }
}

// ── ANANAS navigation (Home ⇄ Locations / My Configs / Settings / Profile) ─────
private enum class AnanasScreen { HOME, LOCATIONS, MY_CONFIGS, SETTINGS, PROFILE }

// ── VPN TAB (Home / Connected — ANANAS reference) ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
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
    var screen by remember { mutableStateOf(AnanasScreen.HOME) }
    val vpnPrefs = remember { context.getSharedPreferences("cdnhunter_vpn", 0) }
    var fragmentEnabled by remember { mutableStateOf(vpnPrefs.getBoolean("fragment_enabled", true)) }

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

    when (screen) {
    AnanasScreen.HOME -> {
        if (configs.isEmpty()) {
            Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
                EmptyHomeState { showAddDialog = true }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    containerColor = AnanasCard2, contentColor = AnanasTextHi, shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Rounded.Add, contentDescription = "Add config") }
            }
        } else {
            val sheetState = rememberBottomSheetScaffoldState()
            BottomSheetScaffold(
                scaffoldState = sheetState,
                sheetPeekHeight = if (otherConfigs.isNotEmpty()) 76.dp else 0.dp,
                sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                sheetContainerColor = Color(0xFF101012),
                sheetContentColor = AnanasText,
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = 12.dp,
                sheetSwipeEnabled = otherConfigs.isNotEmpty(),
                sheetDragHandle = {
                    if (otherConfigs.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AnanasBorder2))
                        }
                    }
                },
                containerColor = AnanasScreenBg,
                modifier = Modifier.fillMaxSize(),
                sheetContent = {
                    if (otherConfigs.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 28.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("QUICK SWITCH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                Text(
                                    "See all", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = AnanasAccent,
                                    modifier = Modifier.clickable { screen = AnanasScreen.MY_CONFIGS }
                                )
                            }
                            otherConfigs.forEachIndexed { idx, cfg ->
                                QuickSwitchRow(cfg = cfg, onClick = { connectConfig(cfg) }, showDivider = idx < otherConfigs.lastIndex)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(Modifier.fillMaxSize().padding(innerPadding).background(AnanasScreenBg)) {
                    Column(Modifier.fillMaxSize()) {
                        // ── Top bar ──────────────────────────────────────────────
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnanasIconButton(Icons.Rounded.Menu) { screen = AnanasScreen.SETTINGS }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Rounded.Shield, null, tint = AnanasTextHi, modifier = Modifier.size(16.dp))
                                Text("ANANAS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.2).sp)
                            }
                            AnanasIconButton(Icons.Rounded.Person) { screen = AnanasScreen.PROFILE }
                        }

                        // ── Power button + status ────────────────────────────────
                        Column(
                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
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
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            activeConfig?.let { cfg ->
                                item(key = "active-${cfg.id}") {
                                    SelectedServerSummaryCard(
                                        cfg = cfg, connected = connected,
                                        onClick = { screen = AnanasScreen.LOCATIONS }
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
            }
        }
    }

    AnanasScreen.MY_CONFIGS -> MyConfigsScreen(
        configs = configs, activeId = activeId, connected = connected,
        onBack = { screen = AnanasScreen.HOME },
        onConnect = { connectConfig(it) },
        onCopy = { cfg ->
            clip.setText(AnnotatedString(cfg.uri))
            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        },
        onAdd = { showAddDialog = true },
        onDelete = { deleteConfig(it) }
    )

    AnanasScreen.LOCATIONS -> LocationsScreen(onBack = { screen = AnanasScreen.HOME })

    AnanasScreen.SETTINGS -> SettingsScreen(
        fragmentEnabled = fragmentEnabled,
        onFragmentChange = {
            fragmentEnabled = it
            vpnPrefs.edit().putBoolean("fragment_enabled", it).apply()
        },
        onProfileClick = { screen = AnanasScreen.PROFILE },
        onBack = { screen = AnanasScreen.HOME }
    )

    AnanasScreen.PROFILE -> ProfileScreen(onBack = { screen = AnanasScreen.HOME })
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
        // pulsing outward rings — only while connected (exact ref: 160x160, 1px #4ade9c border)
        if (connected) {
            listOf(pulse1, pulse2).forEach { p ->
                Box(
                    Modifier
                        .size(160.dp)
                        .scale(0.85f + p * 0.25f)
                        .clip(CircleShape)
                        .border(1.dp, AnanasAccent.copy(alpha = (1f - p) * 0.7f), CircleShape)
                )
            }
        }

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

        // core power button — exact ref: 112dp, border 1.5dp #2a4638, glow shadow 0 0 40px rgba(74,222,156,.12)
        Box(
            Modifier
                .size(128.dp) // 112dp core + 8dp ring-shadow spread on each side (matches ref's "0 0 0 8px #0b0b0d" collar)
                .clip(CircleShape)
                .background(
                    if (connected) Brush.radialGradient(listOf(AnanasAccent.copy(0.16f), Color.Transparent), radius = 200f)
                    else Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                ),
            contentAlignment = Alignment.Center
        ) {}
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
    onClick: () -> Unit, onCopy: () -> Unit, onShowQr: () -> Unit = {},
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
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(9.dp))
                            .clickable { onShowQr() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.QrCode2, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(16.dp)) }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
                            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(9.dp))
                            .clickable { onCopy() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ContentCopy, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(15.dp)) }
                }
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

// ── Selected-server summary card (Home, top) — simple row + chevron to Locations ─
@Composable
private fun SelectedServerSummaryCard(cfg: SavedConfig, connected: Boolean, onClick: () -> Unit) {
    val badgeColor = when (cfg.proto.lowercase()) {
        "trojan" -> AnanasAccent; "vless" -> AnanasVless; "vmess" -> AnanasAmber; else -> AnanasMuted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AnanasCard)
            .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(badgeColor.copy(0.16f)), contentAlignment = Alignment.Center) {
                Text(cfg.proto.take(1).uppercase(), color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column {
                Text(cfg.displayName, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                Text(
                    if (connected) "${cfg.network.uppercase()} · Active" else "Tap to browse locations",
                    fontSize = 11.sp, color = AnanasMuted, modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(16.dp))
    }
}

// ── Quick-switch row (inside the bordered Quick Switch card) ───────────────────
@Composable
private fun QuickSwitchRow(cfg: SavedConfig, onClick: () -> Unit, showDivider: Boolean = true) {
    val badgeColor = when (cfg.proto.lowercase()) {
        "trojan" -> AnanasAccent; "vless" -> AnanasVless; "vmess" -> AnanasAmber; else -> AnanasMuted
    }
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(badgeColor.copy(0.16f)), contentAlignment = Alignment.Center) {
                Text(cfg.proto.take(1).uppercase(), color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Text(cfg.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE4E5E9))
        }
        Text(cfg.network.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = AnanasMuted)
    }
    if (showDivider) Divider(color = AnanasDivider, thickness = 1.dp)
}

// ── QR code: generate + dialog (v2rayNG-style config sharing) ──────────────────
private fun generateQrBitmap(text: String, sizePx: Int = 560): android.graphics.Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun QrCodeDialog(cfg: SavedConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clip = LocalClipboardManager.current
    val qrBitmap = remember(cfg.uri) { generateQrBitmap(cfg.uri) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(AnanasCard)
                .border(1.dp, AnanasBorder2, RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(cfg.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
            Text("Scan with another device", fontSize = 11.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))

            Box(
                Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(qrBitmap.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(196.dp))
                } else {
                    CircularProgressIndicator(color = AnanasAccent)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AnanasCard2)
                    .border(1.dp, AnanasBorder2, RoundedCornerShape(14.dp))
                    .clickable {
                        clip.setText(AnnotatedString(cfg.uri))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.ContentCopy, null, tint = AnanasText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasText)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Close", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = AnanasMuted,
                modifier = Modifier.padding(top = 6.dp).clickable { onDismiss() }
            )
        }
    }
}

// ── My Configs — full functional list screen ────────────────────────────────────
@Composable
private fun MyConfigsScreen(
    configs: List<SavedConfig>, activeId: String, connected: Boolean,
    onBack: () -> Unit, onConnect: (SavedConfig) -> Unit, onCopy: (SavedConfig) -> Unit,
    onAdd: () -> Unit, onDelete: (SavedConfig) -> Unit,
) {
    var qrConfig by remember { mutableStateOf<SavedConfig?>(null) }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
                    Column {
                        Text("My configs", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
                        val activeCount = if (configs.any { it.id == activeId } && connected) 1 else 0
                        Text("${configs.size} configs · $activeCount active", fontSize = 11.5.sp, color = AnanasMuted)
                    }
                }
                AnanasIconButton(Icons.Rounded.Add, onAdd)
            }

            if (configs.isEmpty()) {
                EmptyHomeState(onAdd)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(configs, key = { it.id }) { cfg ->
                        val isActive = cfg.id == activeId
                        ServerRow(
                            cfg = cfg, isActive = isActive, connected = connected && isActive,
                            onClick = { onConnect(cfg) }, onCopy = { onCopy(cfg) },
                            onShowQr = { qrConfig = cfg }
                        )
                    }
                }
            }
        }
    }

    qrConfig?.let { cfg ->
        QrCodeDialog(cfg = cfg, onDismiss = { qrConfig = null })
    }
}

// ── Locations — visual reference screen (static demo data, wired later) ────────
private data class DemoCountry(val name: String, val servers: Int, val ms: Int, val color: Color)
@Composable
private fun LocationsScreen(onBack: () -> Unit) {
    val demo = remember {
        listOf(
            DemoCountry("Germany", 14, 17, AnanasAccent),
            DemoCountry("France", 9, 29, AnanasMuted),
            DemoCountry("Netherlands", 11, 34, AnanasMuted),
            DemoCountry("Turkey", 18, 61, AnanasMuted),
        )
    }
    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
                Column {
                    Text("Locations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
                    Text("128 servers across 32 countries", fontSize = 11.5.sp, color = AnanasMuted)
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AnanasCard2)
                    .border(1.dp, AnanasBorder2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Search, null, tint = AnanasMuted, modifier = Modifier.size(16.dp))
                Text("Search", fontSize = 13.sp, color = Color(0xFF54565E))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi,
                    modifier = Modifier.padding(bottom = 10.dp).border(0.dp, Color.Transparent))
                Text("Fastest", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AnanasMuted)
                Text("Saved", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AnanasMuted)
            }
            Divider(color = Color(0xFF1C1C20), thickness = 1.dp)
            Spacer(Modifier.height(6.dp))

            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                items(demo) { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF1A1A1E)))
                            Column {
                                Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = AnanasText, letterSpacing = (-0.1).sp)
                                Text("${c.servers} servers", fontSize = 11.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 1.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${c.ms}ms", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.color)
                            Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(15.dp))
                        }
                    }
                    Divider(color = AnanasDivider, thickness = 1.dp)
                }
            }
        }
    }
}

// ── Settings — ANANAS reference (replaces old Tools/ScannerTab entirely) ───────
@Composable
private fun SettingsScreen(
    fragmentEnabled: Boolean, onFragmentChange: (Boolean) -> Unit,
    onProfileClick: () -> Unit = {}, onBack: () -> Unit = {},
) {
    var autoReconnect by remember { mutableStateOf(true) }
    var killSwitch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
                Text("Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
            }

            // Profile summary card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                    .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp))
                    .clickable { onProfileClick() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(AnanasCard2).border(1.5.dp, Color(0xFF2A2C31), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("YM", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasAccent) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Yashar M.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(AnanasAmber.copy(0.16f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AnanasAmber, letterSpacing = 0.3.sp)
                        }
                        Text("· Expires in 21 days", fontSize = 11.sp, color = AnanasMuted)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(26.dp))
            Text("CONNECTION", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                    .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(Icons.Rounded.VerifiedUser, "Protocol", "VLESS", AnanasAccent, showChevron = true)
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsToggleRow(
                    Icons.Rounded.Security, "TLS fragmentation", "Bypass deep packet inspection",
                    fragmentEnabled, onFragmentChange
                )
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsToggleRow(
                    Icons.Rounded.Autorenew, "Auto-reconnect", "Reconnect if connection drops",
                    autoReconnect, { autoReconnect = it }
                )
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsToggleRow(
                    Icons.Rounded.Lock, "Kill switch", "Block traffic if VPN disconnects",
                    killSwitch, { killSwitch = it }
                )
            }

            Spacer(Modifier.height(26.dp))
            Text("GENERAL", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                    .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(Icons.Rounded.NotificationsNone, "Notifications", null, AnanasMuted, showChevron = true)
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsRow(Icons.Rounded.Language, "Language", "English", AnanasMuted, showChevron = true)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String?, iconTint: Color, showChevron: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AnanasCard2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
            }
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value != null) Text(value, fontSize = 12.5.sp, color = AnanasMuted)
            if (showChevron) Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(icon: ImageVector, label: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AnanasCard2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(15.dp))
            }
            Column {
                Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                Text(desc, fontSize = 10.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AnanasAccent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF6B6B70), uncheckedTrackColor = AnanasCard2, uncheckedBorderColor = AnanasBorder2
            )
        )
    }
}

// ── Profile — visual reference screen (static placeholder, wired later) ────────
@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
                Text("Profile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
            }

            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(AnanasCard2).border(2.dp, Color(0xFF2A2C31), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("YM", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AnanasAccent) }
                Spacer(Modifier.height(12.dp))
                Text("Yashar M.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                Text("yashar@ananasvpn.com", fontSize = 12.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF161310))
                    .border(1.dp, Color(0xFF3A2F1E), RoundedCornerShape(16.dp)).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = AnanasAmber, modifier = Modifier.size(14.dp))
                        Text("Pro plan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasAmber)
                    }
                    Text("Renews Aug 10", fontSize = 11.sp, color = AnanasMuted)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E0C0A))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).clip(RoundedCornerShape(8.dp)).background(AnanasAmber))
                }
                Spacer(Modifier.height(8.dp))
                Text("21 of 30 days remaining", fontSize = 11.sp, color = AnanasMuted)
            }
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("6" to "Configs", "142 GB" to "Used total", "98" to "Sessions").forEach { (v, l) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(AnanasCard)
                            .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)).padding(13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(v, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                        Text(l, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            @Composable fun MenuRow(icon: ImageVector, label: String, tint: Color, labelColor: Color, iconBg: Color, showChevron: Boolean = true) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                        }
                        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = labelColor)
                    }
                    if (showChevron) Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(15.dp))
                }
            }
            MenuRow(Icons.Rounded.Diamond, "Upgrade plan", AnanasAmber, AnanasText, AnanasCard2)
            Divider(color = AnanasDivider, thickness = 1.dp)
            MenuRow(Icons.Rounded.History, "Payment history", AnanasMuted, AnanasText, AnanasCard2)
            Divider(color = AnanasDivider, thickness = 1.dp)
            MenuRow(Icons.Rounded.Logout, "Sign out", AnanasRed, AnanasRed, Color(0xFF1C1416), showChevron = false)
        }
    }
}
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

