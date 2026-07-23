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
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import com.cdnhunter.app.engine.GeoService
import java.io.File
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
    // Geo/ping info — filled in lazily via GeoService + a TCP-connect probe, not persisted.
    val countryCode: String = "",
    val city: String = "",
    val pingMs: Int = -1,
    val geoResolved: Boolean = false,
)

// Measures round-trip time of a raw TCP connect to the server's host:port.
private fun measurePingMs(host: String, port: Int, timeoutMs: Int = 2000): Int {
    return try {
        val started = System.currentTimeMillis()
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
        }
        (System.currentTimeMillis() - started).toInt()
    } catch (e: Exception) {
        -1
    }
}

// Resolves country/city + ping for a single config. Runs on IO dispatcher.
private suspend fun enrichConfigGeo(geo: GeoService, cfg: SavedConfig): SavedConfig =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ping = measurePingMs(cfg.address, cfg.port)
        // If the country code was already pulled from a flag emoji in the config's name,
        // keep it and only fetch city/ping — no need to hit the geo-lookup API for country.
        if (cfg.countryCode.isNotBlank()) {
            val info = try { geo.lookupGeoInfo(cfg.address) } catch (e: Exception) { null }
            return@withContext cfg.copy(pingMs = ping, city = info?.city.orEmpty(), geoResolved = true)
        }
        val info = try { geo.lookupGeoInfo(cfg.address) } catch (e: Exception) { null }
        cfg.copy(
            pingMs = ping,
            countryCode = info?.cc.orEmpty(),
            city = info?.city.orEmpty(),
            geoResolved = true,
        )
    }

// Converts a 2-letter ISO country code into its Unicode flag emoji by mapping each
// letter to its Regional Indicator Symbol (U+1F1E6..U+1F1FF, A..Z). This works for
// EVERY ISO-3166 country automatically — no hardcoded per-country list to maintain,
// unlike the old fixed set of ~10 stripe patterns that silently fell back to a gray
// placeholder for anything else.
private fun countryCodeToFlagEmoji(cc: String): String? {
    val code = cc.trim().uppercase()
    if (code.length != 2) return null
    val first = code[0]
    val second = code[1]
    if (first !in 'A'..'Z' || second !in 'A'..'Z') return null
    val base = 0x1F1E6 // Regional Indicator Symbol Letter A
    val firstCp = base + (first - 'A')
    val secondCp = base + (second - 'A')
    return String(Character.toChars(firstCp)) + String(Character.toChars(secondCp))
}

// Fallback stripe pattern used only when we truly have no country code (e.g. geo
// lookup hasn't resolved yet) — the badge still looks intentional instead of blank.
private val fallbackFlagColors = listOf(AnanasFaint, AnanasMuted, AnanasFaint)

// Square flag badge with rounded corners. Renders the real emoji flag for the given
// ISO country code (covers all countries); falls back to a neutral stripe placeholder
// only when the code is blank/unrecognized (e.g. still resolving).
@Composable
private fun CountryFlagBadge(countryCode: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val emoji = remember(countryCode) { countryCodeToFlagEmoji(countryCode) }
    val corner = size * 0.28f
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF1A1A1E)),
        contentAlignment = Alignment.Center
    ) {
        if (emoji != null) {
            Text(
                emoji,
                fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size.toPx() * 0.62f).toSp() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            // Neutral placeholder stripes while country is unresolved.
            Column(Modifier.fillMaxSize()) {
                fallbackFlagColors.forEach { c ->
                    Box(Modifier.weight(1f).fillMaxWidth().background(c))
                }
            }
        }
        // Thin border for definition
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(corner))
        )
    }
}

private fun pingQualityLabel(ms: Int): String = when {
    ms < 0    -> "—"
    ms < 80   -> "Low load"
    ms < 180  -> "Medium load"
    else      -> "High load"
}

private val countryNames = mapOf(
    "DE" to "Germany", "NL" to "Netherlands", "FR" to "France", "GB" to "United Kingdom",
    "US" to "United States", "CA" to "Canada", "FI" to "Finland", "SE" to "Sweden",
    "NO" to "Norway", "CH" to "Switzerland", "AT" to "Austria", "PL" to "Poland",
    "IT" to "Italy", "ES" to "Spain", "PT" to "Portugal", "IE" to "Ireland",
    "SG" to "Singapore", "JP" to "Japan", "HK" to "Hong Kong", "KR" to "South Korea",
    "AU" to "Australia", "IN" to "India", "AE" to "UAE", "TR" to "Turkey",
    "RU" to "Russia", "UA" to "Ukraine", "RO" to "Romania", "BG" to "Bulgaria",
    "CZ" to "Czechia", "HU" to "Hungary", "GR" to "Greece", "BR" to "Brazil",
)

private fun countryCodeToName(cc: String): String = countryNames[cc.uppercase()] ?: ""

// Detects a flag emoji (pair of regional-indicator symbols, e.g. 🇩🇪) inside a config's
// remark/name — the way Hiddify-style clients embed the country in the config title.
// Returns the 2-letter country code and the remaining text with the flag stripped out.
private val flagEmojiRegex = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
private fun extractFlagFromName(raw: String): Pair<String, String> {
    val match = flagEmojiRegex.find(raw) ?: return "" to raw
    val flag = match.value
    val codepoints = flag.codePoints().toArray()
    val cc = codepoints.joinToString("") { cp -> ((cp - 0x1F1E6) + 'A'.code).toChar().toString() }
    val stripped = raw.replace(flag, "").trim().trim('-', '·', '|', '(', ')').trim()
    return cc to stripped.ifBlank { raw }
}


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

    // Prefer the user-given remark (URI fragment, e.g. "...#🇩🇪 Germany Pro 01") if present
    val remark = try {
        java.net.URI(uri).rawFragment?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }
    val fallbackName = when (proto) {
        "trojan"  -> "Trojan"
        "vless"   -> "VLESS"
        "vmess"   -> "VMess"
        else      -> proto.replaceFirstChar { it.uppercase() }
    } + " · $addr"

    // If the remark carries a flag emoji (Hiddify-style, e.g. "🇩🇪 Germany Pro 01"),
    // pull the country code from it and strip the emoji from the displayed name —
    // this skips the geo-lookup entirely for that config.
    val (flagCc, cleanRemark) = remark?.let { extractFlagFromName(it) } ?: ("" to null)
    val name = cleanRemark ?: fallbackName

    return SavedConfig(
        id = uri.hashCode().toString(),
        uri = uri, displayName = name,
        proto = proto, address = addr, port = port, network = net, sni = sni,
        countryCode = flagCc,
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
    var showAddMenu by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(AnanasScreen.HOME) }
    val vpnPrefs = remember { context.getSharedPreferences("cdnhunter_vpn", 0) }
    var fragmentEnabled by remember { mutableStateOf(vpnPrefs.getBoolean("fragment_enabled", true)) }

    var connectedSinceMs by remember { mutableStateOf(0L) }
    var elapsedSec        by remember { mutableStateOf(0L) }
    var downloadKBps      by remember { mutableStateOf(0.0) }
    var uploadKBps        by remember { mutableStateOf(0.0) }

    val geoService = remember { GeoService() }

    // Enrich configs with country/city/ping whenever the SET of config ids changes
    // (add/remove), not on every write to `configs` itself. The old key
    // (configs.map { it.id }) created a NEW list every recomposition even when only
    // ping/geo fields changed, which re-triggered this effect, which wrote back into
    // `configs`, which re-triggered it again — an effect storm that could hang the UI
    // thread (worst when switching tabs forces a recomposition). Keying on a joined
    // id string only changes identity when configs are actually added/removed.
    val configIdsKey = remember(configs) { configs.map { it.id }.sorted().joinToString(",") }
    val enrichingIds = remember { mutableSetOf<String>() }
    LaunchedEffect(configIdsKey) {
        val toEnrich = configs.filter { !it.geoResolved && it.id !in enrichingIds }
        for (cfg in toEnrich) {
            enrichingIds += cfg.id
            try {
                val enriched = enrichConfigGeo(geoService, cfg)
                configs = configs.map { if (it.id == cfg.id) enriched else it }
            } finally {
                enrichingIds -= cfg.id
            }
        }
    }

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

    // Unwraps a possibly-wrapped Compose Context down to the hosting Activity.
    // context as? MainActivity often fails silently (LocalContext is frequently a
    // ContextWrapper, e.g. themed context), which was skipping VPN permission
    // and causing the connect flow to fail/crash without a clear error.
    fun findActivity(ctx: Context): com.cdnhunter.app.MainActivity? {
        var c = ctx
        while (c is android.content.ContextWrapper) {
            if (c is com.cdnhunter.app.MainActivity) return c
            c = c.baseContext
        }
        return c as? com.cdnhunter.app.MainActivity
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
                val act = findActivity(context)
                if (act != null) {
                    act.requestVpnPermissionAndConnect()
                } else {
                    connecting = false
                    android.widget.Toast.makeText(context, "Couldn't start VPN — please reopen the app", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                connecting = false
                android.widget.Toast.makeText(context, "Failed: ${e.message?.take(40)}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Tapping a server in the Locations list should SELECT it as the active server.
    // If we're already connected, switch the live tunnel to it (reconnect).
    // If we're not connected, just mark it active and go back — don't auto-connect.
    fun selectConfig(cfg: SavedConfig) {
        activeId = cfg.id
        context.getSharedPreferences("cdnhunter_vpn", 0)
            .edit()
            .putString("user_config", cfg.uri)
            .putString("active_config_id", cfg.id)
            .apply()
        if (connected) {
            connectConfig(cfg)
        }
    }

    fun deleteConfig(cfg: SavedConfig) {
        if (cfg.id == activeId && connected) { CdnVpnService.stop(context); AutoIpManager.stop(); connected = false }
        val updated = configs.filter { it.id != cfg.id }
        configs = updated
        saveConfigs(context, updated)
        if (cfg.id == activeId) activeId = ""
    }

    // Shared by clipboard-instant-add and QR-scan-add: parse, save, toast — no dialog.
    fun addConfigFromUri(uri: String, sourceLabel: String) {
        val cfg = parseConfig(uri.trim())
        if (cfg == null) {
            android.widget.Toast.makeText(context, "Invalid config link", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (configs.any { it.uri == cfg.uri }) {
            android.widget.Toast.makeText(context, "Already added", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val updated = configs + cfg
        configs = updated
        saveConfigs(context, updated)
        android.widget.Toast.makeText(context, "Added from $sourceLabel · ${cfg.displayName}", android.widget.Toast.LENGTH_LONG).show()
    }

    fun addFromClipboard() {
        val clipText = clip.getText()?.text
        if (clipText.isNullOrBlank()) {
            android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            addConfigFromUri(clipText, "clipboard")
        }
    }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { addConfigFromUri(it, "QR code") }
    }
    fun startQrScan() {
        qrScanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan a config QR code")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }

    val activeConfig  = configs.find { it.id == activeId } ?: configs.firstOrNull()
    val otherConfigs  = configs.filter { it.id != activeConfig?.id }

    when (screen) {
    AnanasScreen.HOME -> {
        if (configs.isEmpty()) {
            Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
                EmptyHomeState { showAddMenu = true }
                Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                    AddConfigFabMenu(
                        expanded = showAddMenu, onToggle = { showAddMenu = !showAddMenu },
                        onScanQr = ::startQrScan, onClipboard = ::addFromClipboard,
                        onManual = {}
                    )
                }
            }
        } else {
            val sheetState = rememberBottomSheetScaffoldState(
                bottomSheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    skipHiddenState = true
                )
            )
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
                            AnanasIconButton(Icons.Rounded.Person) { screen = AnanasScreen.PROFILE }
                        }

                        // ── Power button + status ────────────────────────────────
                        Column(
                            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp),
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

                    Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                        AddConfigFabMenu(
                            expanded = showAddMenu, onToggle = { showAddMenu = !showAddMenu },
                            onScanQr = ::startQrScan, onClipboard = ::addFromClipboard,
                            onManual = {}
                        )
                    }
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
        onManualAdd = {},
        onScanQr = ::startQrScan,
        onClipboardAdd = ::addFromClipboard,
        onDelete = { deleteConfig(it) }
    )

    AnanasScreen.LOCATIONS -> LocationsScreen(
        configs = configs, activeId = activeId, connected = connected,
        onBack = { screen = AnanasScreen.HOME },
        onConnect = { selectConfig(it); screen = AnanasScreen.HOME },
    )

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

    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        // pulsing outward rings — only while connected
        if (connected) {
            listOf(pulse1, pulse2).forEach { p ->
                Box(
                    Modifier
                        .size(180.dp)
                        .scale(0.85f + p * 0.25f)
                        .clip(CircleShape)
                        .border(1.dp, AnanasAccent.copy(alpha = (1f - p) * 0.7f), CircleShape)
                )
            }
        }

        // thin rotating sweep arc while connected
        if (connected) {
            Canvas(Modifier.size(158.dp).rotate(sweepRotation)) {
                drawArc(
                    color = AnanasAccent,
                    startAngle = 0f, sweepAngle = 26f, useCenter = false,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // core power button — enlarged from reference (126dp) to give more visual weight
        Box(
            Modifier
                .size(144.dp) // 126dp core + 9dp ring-shadow spread on each side
                .clip(CircleShape)
                .background(
                    if (connected) Brush.radialGradient(listOf(AnanasAccent.copy(0.16f), Color.Transparent), radius = 200f)
                    else Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                ),
            contentAlignment = Alignment.Center
        ) {}
        Box(
            Modifier
                .size(126.dp)
                .clip(CircleShape)
                .background(Color(0xFF101210))
                .border(1.5.dp, if (connected) Color(0xFF2A4638) else AnanasBorder2, CircleShape)
                .clickable(enabled = !connecting) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                CircularProgressIndicator(color = AnanasAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            } else {
                Icon(
                    Icons.Rounded.PowerSettingsNew, null,
                    tint = if (connected) AnanasAccent else AnanasMuted,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

// ── Add-config menu: opens as a sliding bottom sheet with QR scan / clipboard ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConfigFabMenu(
    expanded: Boolean, onToggle: () -> Unit,
    onScanQr: () -> Unit, onClipboard: () -> Unit, onManual: () -> Unit,
) {
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = onToggle,
            containerColor = Color(0xFF101012),
            contentColor = AnanasText,
            dragHandle = {
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(AnanasBorder2))
                }
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(
                    "Add a config", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = AnanasTextHi, modifier = Modifier.padding(bottom = 16.dp)
                )
                AddSheetAction("Scan QR code", "Scan a config from another device", Icons.Rounded.QrCodeScanner) {
                    onToggle(); onScanQr()
                }
                Spacer(Modifier.height(10.dp))
                AddSheetAction("Add from clipboard", "Paste a config link you've copied", Icons.Rounded.ContentPaste, highlight = true) {
                    onToggle(); onClipboard()
                }
            }
        }
    }
    FloatingActionButton(
        onClick = onToggle,
        containerColor = AnanasCard2,
        contentColor   = AnanasTextHi,
        shape          = RoundedCornerShape(16.dp)
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Add config",
            tint = AnanasTextHi
        )
    }
}

@Composable
private fun AddSheetAction(title: String, subtitle: String, icon: ImageVector, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) AnanasAccent else AnanasCard2)
            .border(1.dp, if (highlight) Color.Transparent else AnanasBorder2, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(if (highlight) Color.Black.copy(0.12f) else AnanasCard),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (highlight) AnanasBg else AnanasTextHi, modifier = Modifier.size(19.dp)) }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (highlight) AnanasBg else AnanasTextHi)
            Text(subtitle, fontSize = 11.5.sp, color = if (highlight) AnanasBg.copy(0.7f) else AnanasMuted, modifier = Modifier.padding(top = 1.dp))
        }
    }
}


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
                    CountryFlagBadge(cfg.countryCode, 32.dp)
                    Column {
                        Text(cfg.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                        Spacer(Modifier.height(3.dp))
                        if (isActive && connected) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(AnanasAccent))
                                Text("CONNECTED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AnanasAccent, letterSpacing = 0.2.sp)
                            }
                        } else {
                            val sub = if (cfg.pingMs >= 0) "${cfg.pingMs} ms · ${pingQualityLabel(cfg.pingMs)}" else "Tap to connect"
                            Text(sub, fontSize = 11.5.sp, fontWeight = FontWeight.Normal, color = AnanasMuted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(10.dp))
                            .clickable { onShowQr() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.QrCode2, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(18.dp)) }
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(10.dp))
                            .clickable { onCopy() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ContentCopy, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(17.dp)) }
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
    val countryName = remember(cfg.countryCode) { countryCodeToName(cfg.countryCode) }
    val locationLine = when {
        countryName.isNotBlank() && cfg.city.isNotBlank() -> "$countryName · ${cfg.city}"
        countryName.isNotBlank() -> countryName
        !cfg.geoResolved -> "Resolving location…"
        else -> cfg.displayName
    }
    val pingLine = if (cfg.pingMs >= 0) "${cfg.pingMs} ms · ${pingQualityLabel(cfg.pingMs)}" else "—"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnanasCard)
            .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 17.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            CountryFlagBadge(cfg.countryCode, 26.dp)
            Column {
                Text(locationLine, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                Text(
                    if (connected) "${cfg.network.uppercase()} · Active" else pingLine,
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
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            CountryFlagBadge(cfg.countryCode, 22.dp)
            Text(cfg.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE4E5E9))
        }
        Text(
            if (cfg.pingMs >= 0) "${cfg.pingMs}ms" else cfg.network.uppercase(),
            fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = AnanasMuted
        )
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
    onManualAdd: () -> Unit, onScanQr: () -> Unit, onClipboardAdd: () -> Unit,
    onDelete: (SavedConfig) -> Unit,
) {
    var qrConfig by remember { mutableStateOf<SavedConfig?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

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
            }

            if (configs.isEmpty()) {
                EmptyHomeState { showAddMenu = true }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
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

        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            AddConfigFabMenu(
                expanded = showAddMenu, onToggle = { showAddMenu = !showAddMenu },
                onScanQr = onScanQr, onClipboard = onClipboardAdd, onManual = onManualAdd
            )
        }
    }

    qrConfig?.let { cfg ->
        QrCodeDialog(cfg = cfg, onDismiss = { qrConfig = null })
    }
}

// ── Locations — visual reference screen (static demo data, wired later) ────────
@Composable
private fun LocationsScreen(
    configs: List<SavedConfig>, activeId: String, connected: Boolean,
    onBack: () -> Unit, onConnect: (SavedConfig) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(configs, query) {
        if (query.isBlank()) configs
        else configs.filter { it.displayName.contains(query, ignoreCase = true) }
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
                    Text(
                        if (configs.isEmpty()) "No configs yet" else "${configs.size} config${if (configs.size == 1) "" else "s"} saved",
                        fontSize = 11.5.sp, color = AnanasMuted
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AnanasCard2)
                    .border(1.dp, AnanasBorder2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Search, null, tint = AnanasMuted, modifier = Modifier.size(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = AnanasText),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AnanasAccent),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search", fontSize = 13.sp, color = Color(0xFF54565E))
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(18.dp))
            Divider(color = Color(0xFF1C1C20), thickness = 1.dp)
            Spacer(Modifier.height(6.dp))

            if (configs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Public, null, tint = AnanasFaint, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No servers yet", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                    Spacer(Modifier.height(4.dp))
                    Text("Add a config from the home screen", fontSize = 12.sp, color = AnanasMuted)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(filtered, key = { it.id }) { cfg ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onConnect(cfg) }.padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                CountryFlagBadge(cfg.countryCode, 26.dp)
                                Column {
                                    Text(cfg.displayName, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = AnanasText, letterSpacing = (-0.1).sp)
                                    Text(
                                        if (cfg.id == activeId && connected) "Connected"
                                        else if (cfg.id == activeId) "Selected"
                                        else if (cfg.pingMs >= 0) "${cfg.pingMs} ms · ${pingQualityLabel(cfg.pingMs)}"
                                        else "Tap to select",
                                        fontSize = 11.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (cfg.id == activeId && connected) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(AnanasAccent))
                                } else if (cfg.id == activeId) {
                                    Icon(Icons.Rounded.Check, null, tint = AnanasAccent, modifier = Modifier.size(16.dp))
                                } else if (cfg.pingMs >= 0) {
                                    Text("${cfg.pingMs}ms", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted)
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(15.dp))
                            }
                        }
                        Divider(color = AnanasDivider, thickness = 1.dp)
                    }
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

            val context = LocalContext.current
            val clip = LocalClipboardManager.current
            val crashFile = remember { File(context.filesDir, com.cdnhunter.app.CdnHunterApp.CRASH_LOG_FILE) }
            if (crashFile.exists()) {
                Spacer(Modifier.height(26.dp))
                Text("DEBUG", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                        .border(1.dp, AnanasRed.copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AnanasRed.copy(0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.BugReport, null, tint = AnanasRed, modifier = Modifier.size(15.dp))
                        }
                        Column {
                            Text("Last crash log", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                            Text("Found a saved crash report", fontSize = 10.5.sp, color = AnanasMuted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp)).background(AnanasAccent)
                                .clickable {
                                    val text = runCatching { crashFile.readText() }.getOrDefault("")
                                    clip.setText(AnnotatedString(text))
                                    android.widget.Toast.makeText(context, "Crash log copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnanasBg) }
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp)).background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(9.dp))
                                .clickable { runCatching { crashFile.delete() } }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted) }
                    }
                }
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
        modifier.clip(RoundedCornerShape(16.dp)).background(AnanasCard)
            .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp)).padding(14.dp)
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
        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            .background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(11.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(18.dp)) }
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
