package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import android.net.VpnService
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import java.io.File
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.MihomoBridge
import com.cdnhunter.app.vpn.AppSettings
import com.cdnhunter.app.ui.components.TrafficChartCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import androidx.core.graphics.drawable.toBitmap

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
// Modernized Material Design 3 Inspired Colors - Professional & Cohesive
val AnanasBg       = Color(0xFF050505)   // Near black background
val AnanasScreenBg = Color(0xFF0B0B0D)   // Slightly lighter
val AnanasCard     = Color(0xFF131316)   // Card surface
val AnanasCard2    = Color(0xFF151519)   // Secondary card
val AnanasBorder   = Color(0xFF1E1F24)   // Border colors
val AnanasBorder2  = Color(0xFF232328)   // Alternative border
val AnanasDivider  = Color(0xFF17171B)   // Divider
val AnanasAccent   = Color(0xFF10B981)   // Modern green (improved from 0xFF4ADE9C)
val AnanasAccentLight = Color(0xFF34D399)
val AnanasAccentDark  = Color(0xFF059669)
val AnanasSettingsIcon = Color(0xFFAEB0B8)   // Soft muted gray for settings row icons (was near-white)
val AnanasAmber    = Color(0xFFD97706)   // Warm amber
val AnanasRed      = Color(0xFFEF4444)   // Modern red
val AnanasBlue     = Color(0xFF3B82F6)   // Modern blue
val AnanasPurple   = Color(0xFF8B5CF6)   // Modern purple
val AnanasTextHi   = Color(0xFFFAFAFA)   // Primary text
val AnanasText     = Color(0xFFF0F0F2)   // Secondary text
val AnanasMuted    = Color(0xFF6E7078)   // Muted text
val AnanasFaint    = Color(0xFF3A3C44)   // Faint text
val AnanasVless    = Color(0xFF64D2FF)   // VLESS color

@Composable
fun isDarkMode(): Boolean = when (LocalThemeMode.current) {
    ThemeMode.DARK   -> true
    ThemeMode.LIGHT  -> false
    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }
val LocalThemeMode = androidx.compose.runtime.compositionLocalOf { ThemeMode.SYSTEM }

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
    // Geo/ping info — filled in lazily via GeoService + a TCP-connect probe, then
    // persisted (see saveConfigs/loadConfigs) so it isn't re-resolved on every app start.
    val countryCode: String = "",
    val city: String = "",
    val pingMs: Int = -1,
    val geoResolved: Boolean = false,
    // Set once the ACCURATE (through-the-live-tunnel) check has resolved this
    // config — see the coroutine in CdnVpnService.startVpn() after a successful
    // connect. Distinct from geoResolved (the quick on-device title/SNI guess,
    // which is wrong for CDN-fronted/reality configs) so we know which configs
    // still need the accurate check and don't redo it every reconnect.
    val accurateGeoResolved: Boolean = false,
    
    // Subscription tracking
    val isImported: Boolean = false,         // Is this from a subscription?
    val subscriptionId: String? = null,      // Which subscription (if imported)?
    val subscriptionName: String? = null,    // Subscription display name (if imported)
)

// Measures round-trip time of a raw TCP connect to the server's host:port. DNS
// resolution happens first and is NOT included in the timed window -- v2rayNG
// and other clients report the connect RTT to the resolved server IP itself,
// not "however long the whole lookup+connect took", so timing from before
// resolution would report inflated numbers that don't match what other apps
// show for the same server.
private fun measurePingMs(host: String, port: Int, timeoutMs: Int = 2000): Int {
    return try {
        val addr = java.net.InetAddress.getByName(host)
        val started = System.currentTimeMillis()
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(addr, port), timeoutMs)
        }
        (System.currentTimeMillis() - started).toInt()
    } catch (e: Exception) {
        -1
    }
}

// Measures ping for a single config. Runs on IO dispatcher.
// Country/flag no longer comes from an on-device IP/DNS lookup here at all —
// that used to resolve the config's SNI/address hostname directly (before any
// tunnel exists), which for CDN-fronted/reality domains reports the CDN
// edge's location, not the real backend server's. There are now only two
// sources of truth for the flag: the config's own title (see
// countryCodeFromTitle, applied at parse time) as the free instant guess,
// and the real exit IP seen through the live tunnel once actually connected
// (see CdnVpnService's post-connect check) as the authoritative correction.
private suspend fun enrichConfigGeo(cfg: SavedConfig): SavedConfig =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        cfg.copy(pingMs = measurePingMs(cfg.address, cfg.port), geoResolved = true)
    }

// Periodic ping monitor — continuously measures latency every 3 seconds
// Similar to v2rayng's live ping display. Updates the config in-memory as ping changes.
private suspend fun monitorPingContinuously(
    cfg: SavedConfig,
    onPingUpdate: (SavedConfig) -> Unit,
    cancelCheck: () -> Boolean = { false }
) {
    while (!cancelCheck()) {
        val newPing = measurePingMs(cfg.address, cfg.port, timeoutMs = 3000)
        if (newPing != cfg.pingMs) {
            val updated = cfg.copy(pingMs = newPing)
            onPingUpdate(updated)
        }
        delay(3000)  // Update ping every 3 seconds
    }
}

// Note: the accurate (through-the-tunnel) geo check runs inside CdnVpnService
// itself, right after a real connection succeeds — see the coroutine launched
// after isRunning.set(true) in CdnVpnService.startVpn(), which calls
// GeoService().lookupCurrentExitGeoInfo() against the live tunnel and persists
// the result via persistAccurateGeo(). No separate UI-side probe needed.


// Covers all commonly-seen VPN server countries; falls back to a neutral pattern for
// anything not listed instead of failing to render.
private enum class FlagShape { STRIPES_H, STRIPES_V, NORDIC_CROSS, UNION_JACK, DISC_CENTER, SINGLE }
private data class FlagSpec(
    val shape: FlagShape,
    val colors: List<Color> = emptyList(),
    val bg: Color = Color(0xFFF2F2F2),
    val fg: Color = Color(0xFFE0605C),
)

private val FRED = Color(0xFFE0605C)
private val FBLUE = Color(0xFF3A6CC8)
private val FDARKBLUE = Color(0xFF1D2C5B)
private val FWHITE = Color(0xFFF2F2F2)
private val FYELLOW = Color(0xFFE6A23C)
private val FGREEN = Color(0xFF3AAA5C)
private val FBLACK = Color(0xFF1A1A1A)

private val flagSpecs = mapOf(
    "DE" to FlagSpec(FlagShape.STRIPES_H, listOf(FBLACK, FRED, FYELLOW)),
    "FR" to FlagSpec(FlagShape.STRIPES_V, listOf(FBLUE, FWHITE, FRED)),
    "IT" to FlagSpec(FlagShape.STRIPES_V, listOf(FGREEN, FWHITE, FRED)),
    "IE" to FlagSpec(FlagShape.STRIPES_V, listOf(FGREEN, FWHITE, FYELLOW)),
    "NL" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FBLUE)),
    "RU" to FlagSpec(FlagShape.STRIPES_H, listOf(FWHITE, FBLUE, FRED)),
    "AT" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FRED)),
    "PL" to FlagSpec(FlagShape.STRIPES_H, listOf(FWHITE, FRED, FWHITE)),
    "ID" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FWHITE)),
    "BE" to FlagSpec(FlagShape.STRIPES_V, listOf(FBLACK, FYELLOW, FRED)),
    "RO" to FlagSpec(FlagShape.STRIPES_V, listOf(FBLUE, FYELLOW, FRED)),
    "BG" to FlagSpec(FlagShape.STRIPES_H, listOf(FWHITE, FGREEN, FRED)),
    "HU" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FGREEN)),
    "IN" to FlagSpec(FlagShape.STRIPES_H, listOf(FYELLOW, FWHITE, FGREEN)),
    "AE" to FlagSpec(FlagShape.STRIPES_H, listOf(FGREEN, FWHITE, FBLACK)),
    "EG" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FBLACK)),
    "YE" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FBLACK)),
    "US" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FDARKBLUE)),
    "TH" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FBLUE)),
    "LU" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FBLUE)),
    "SE" to FlagSpec(FlagShape.NORDIC_CROSS, bg = FBLUE, fg = FYELLOW),
    "FI" to FlagSpec(FlagShape.NORDIC_CROSS, bg = FWHITE, fg = FBLUE),
    "NO" to FlagSpec(FlagShape.NORDIC_CROSS, bg = FRED, fg = FWHITE),
    "DK" to FlagSpec(FlagShape.NORDIC_CROSS, bg = FRED, fg = FWHITE),
    "IS" to FlagSpec(FlagShape.NORDIC_CROSS, bg = FBLUE, fg = FWHITE),
    "GB" to FlagSpec(FlagShape.UNION_JACK),
    "JP" to FlagSpec(FlagShape.DISC_CENTER, bg = FWHITE, fg = FRED),
    "KR" to FlagSpec(FlagShape.DISC_CENTER, bg = FWHITE, fg = FRED),
    "BD" to FlagSpec(FlagShape.DISC_CENTER, bg = FGREEN, fg = FRED),
    "PW" to FlagSpec(FlagShape.DISC_CENTER, bg = FBLUE, fg = FYELLOW),
    "TR" to FlagSpec(FlagShape.SINGLE, bg = FRED),
    "CH" to FlagSpec(FlagShape.SINGLE, bg = FRED),
    "MA" to FlagSpec(FlagShape.SINGLE, bg = FRED),
    "QA" to FlagSpec(FlagShape.SINGLE, bg = Color(0xFF8B1538)),
    "SG" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FWHITE, FWHITE)),
    "HK" to FlagSpec(FlagShape.SINGLE, bg = FRED),
    "CA" to FlagSpec(FlagShape.SINGLE, bg = FRED),
    "ES" to FlagSpec(FlagShape.STRIPES_H, listOf(FRED, FYELLOW, FRED)),
    "PT" to FlagSpec(FlagShape.STRIPES_V, listOf(FGREEN, FRED, FRED)),
    "BR" to FlagSpec(FlagShape.SINGLE, bg = FGREEN),
    "AU" to FlagSpec(FlagShape.SINGLE, bg = FDARKBLUE),
    "NZ" to FlagSpec(FlagShape.SINGLE, bg = FDARKBLUE),
    "IR" to FlagSpec(FlagShape.STRIPES_H, listOf(FGREEN, FWHITE, FRED)),
)

// Deterministic fallback for anything not curated above — consistent per country
// code (not literally accurate), better than a blank gray box.
private fun fallbackSpecFor(cc: String): FlagSpec {
    val palette = listOf(FBLUE, FRED, FYELLOW, FGREEN, FWHITE, Color(0xFF8B5CF6))
    val seed = cc.uppercase().sumOf { it.code }
    val c1 = palette[seed % palette.size]
    val c2 = palette[(seed / 7 + 1) % palette.size]
    val c3 = palette[(seed / 13 + 2) % palette.size]
    return FlagSpec(if (seed % 2 == 0) FlagShape.STRIPES_H else FlagShape.STRIPES_V, listOf(c1, c2, c3))
}
private fun flagSpecFor(cc: String): FlagSpec {
    if (cc.isBlank()) return FlagSpec(FlagShape.STRIPES_H, listOf(AnanasFaint, AnanasMuted, AnanasFaint))
    return flagSpecs[cc.uppercase()] ?: fallbackSpecFor(cc)
}

// Real circle-flags SVGs (github.com/HatScripts/circle-flags, MIT — same source
// Hiddify uses via its circle_flags package) bundled under assets/flags/{cc}.svg.
// Rendered through Coil's SVG decoder instead of hand-drawn Canvas shapes.
private var flagImageLoader: coil.ImageLoader? = null
private fun getFlagImageLoader(context: Context): coil.ImageLoader =
    flagImageLoader ?: coil.ImageLoader.Builder(context)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .build()
        .also { flagImageLoader = it }

@Composable
private fun CountryFlagBadge(countryCode: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val cc = countryCode.lowercase().trim()
    val context = LocalContext.current
    if (cc.isBlank()) {
        // No country could be determined (neither from the config's title nor,
        // once connected, the live tunnel) — a globe icon reads as "unknown"
        // much more clearly than an empty lettered box.
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1c1c1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Public, null,
                tint = AnanasFaint,
                modifier = Modifier.size(size * 0.55f)
            )
        }
        return
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1c1c1f)),
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data("file:///android_asset/flags/$cc.svg")
                .crossfade(true)
                .build(),
            imageLoader = getFlagImageLoader(context),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            error = null,
        )
        // Glass highlight: soft diagonal sheen + inner ring, so the flag reads
        // as a polished glossy sphere rather than a flat cropped image.
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1f, 1f)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
    }
}

private fun pingQualityLabel(ms: Int): String = when {
    ms < 0    -> "—"
    ms < 80   -> "Low load"
    ms < 180  -> "Medium load"
    else      -> "High load"
}

// 0 filled/gray = no ping response, 1 filled/red = high load, 2 filled/amber =
// medium load, 3 filled/green = low load -- same tiers as pingQualityLabel.
@Composable
private fun PingBars(pingMs: Int, modifier: Modifier = Modifier, barWidth: Dp = 3.dp, gap: Dp = 2.dp) {
    val filled = when {
        pingMs < 0   -> 0
        pingMs < 80  -> 3
        pingMs < 180 -> 2
        else         -> 1
    }
    val color = when {
        pingMs < 0   -> AnanasFaint
        filled == 3  -> AnanasAccent
        filled == 2  -> AnanasAmber
        else         -> AnanasRed
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        val heights = listOf(6.dp, 10.dp, 14.dp)
        for (i in 0 until 3) {
            Box(
                Modifier
                    .width(barWidth)
                    .height(heights[i])
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < filled) color else AnanasFaint.copy(alpha = 0.4f))
            )
        }
    }
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

// Common country name/abbreviation -> ISO code, matched case-insensitively against
// words in a config's title (e.g. "GERMANY", "Germany Pro 01", "DE-Frankfurt").
// This is the fast, no-network initial guess shown immediately when a config is
// added — refined afterward by the real through-the-tunnel check once connected
// (see probeAccurateGeoViaLiveTunnel below), never treated as final truth.
private val countryNameToCode = mapOf(
    "GERMANY" to "DE", "DEUTSCHLAND" to "DE",
    "FRANCE" to "FR", "ITALY" to "IT", "ITALIA" to "IT",
    "NETHERLANDS" to "NL", "HOLLAND" to "NL",
    "UNITED KINGDOM" to "GB", "UK" to "GB", "BRITAIN" to "GB", "ENGLAND" to "GB",
    "UNITED STATES" to "US", "USA" to "US", "AMERICA" to "US",
    "TURKEY" to "TR", "TURKIYE" to "TR",
    "CANADA" to "CA", "FINLAND" to "FI", "SWEDEN" to "SE", "NORWAY" to "NO", "DENMARK" to "DK",
    "ICELAND" to "IS", "JAPAN" to "JP", "SINGAPORE" to "SG",
    "UAE" to "AE", "EMIRATES" to "AE", "DUBAI" to "AE",
    "IRAN" to "IR", "SPAIN" to "ES", "ESPANA" to "ES", "POLAND" to "PL",
    "RUSSIA" to "RU", "SWITZERLAND" to "CH", "AUSTRALIA" to "AU",
    "BRAZIL" to "BR", "BRASIL" to "BR", "INDIA" to "IN", "SOUTH KOREA" to "KR", "KOREA" to "KR",
    "HONG KONG" to "HK", "AUSTRIA" to "AT", "BELGIUM" to "BE", "ROMANIA" to "RO",
    "BULGARIA" to "BG", "HUNGARY" to "HU", "IRELAND" to "IE", "PORTUGAL" to "PT",
    "IRELAND" to "IE", "INDONESIA" to "ID", "THAILAND" to "TH", "LUXEMBOURG" to "LU",
    "QATAR" to "QA", "EGYPT" to "EG", "MOROCCO" to "MA", "NEW ZEALAND" to "NZ",
)
// A flag emoji is a pair of Unicode Regional Indicator Symbols (U+1F1E6..
// U+1F1FF, one per A-Z). Many subscription providers name servers with
// ONLY a flag emoji ("\ud83c\udde9\ud83c\uddea 01") and no readable country name at all --
// the text-based matching below can never catch those, which is why
// imported subscription servers often showed no flag.
private fun countryCodeFromFlagEmoji(title: String): String? {
    val codePoints = title.codePoints().toArray()
    for (i in 0 until codePoints.size - 1) {
        val a = codePoints[i]
        val b = codePoints[i + 1]
        if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) {
            val c1 = 'A' + (a - 0x1F1E6)
            val c2 = 'A' + (b - 0x1F1E6)
            val code = "$c1$c2".lowercase()
            if (flagSpecs.containsKey(code)) return code
        }
    }
    return null
}

private fun countryCodeFromTitle(title: String): String? {
    countryCodeFromFlagEmoji(title)?.let { return it }
    val normalized = title.uppercase()
        .replace(Regex("[^A-Z ]"), " ") // strip flag emoji, punctuation, digits
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return null
    // Try longest names first so "SOUTH KOREA" matches before a stray "KOREA" would.
    for ((name, code) in countryNameToCode.entries.sortedByDescending { it.key.length }) {
        if (normalized.contains(name)) return code
    }
    // Also catch a standalone 2-letter ISO code as its own word, e.g. "DE - Frankfurt 01".
    val words = normalized.split(" ")
    for (w in words) {
        if (w.length == 2 && flagSpecs.containsKey(w)) return w
    }
    return null
}

private fun parseConfig(uri: String): SavedConfig? {
    val proxy = com.cdnhunter.app.vpn.ConfigUriParser.parseToProxy(uri) ?: return null
    val proto = (proxy["type"] as? String) ?: "?"
    val addr = (proxy["server"] as? String) ?: "?"
    val port = (proxy["port"] as? Int) ?: 443
    val sni = (proxy["servername"] as? String) ?: ""
    val net = (proxy["network"] as? String) ?: "tcp"

    // Prefer the user-given remark (URI fragment, e.g. "...#Germany Pro 01") if present.
    // The name is kept exactly as given — including any flag emoji the user put in
    // it — we just never read that emoji for anything. Like Hiddify, the country
    // shown on the flag badge always comes from a geo-lookup on the server's real
    // IP (see enrichConfigGeo), never from parsing text in the config's title.
    val remark = try {
        java.net.URI(uri).rawFragment?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }
    val fallbackName = when (proto) {
        "trojan"  -> "Trojan"
        "vless"   -> "VLESS"
        "vmess"   -> "VMess"
        else      -> proto.replaceFirstChar { ch -> ch.uppercase() }
    } + " · $addr"
    val name = remark ?: fallbackName
    val titleGuessCc = remark?.let { countryCodeFromTitle(it) } ?: ""

    return SavedConfig(
        id = uri.hashCode().toString(),
        uri = uri, displayName = name,
        proto = proto, address = addr, port = port, network = net, sni = sni,
        countryCode = titleGuessCc,
    )
}

// Each saved line is
// "uri\u0001countryCode\u0001city\u0001pingMs\u0001geoResolved\u0001accurateGeoResolved
//  \u0001isImported\u0001subscriptionId\u0001subscriptionName" —
// the \u0001 separator can't appear in a URI or in geo text, so this is safe
// without escaping. Persisting the geo fields (not just the uri) means a
// resolved config's flag/ping survives an app restart instead of re-resolving
// every single time the app is reopened.
private const val CONFIG_FIELD_SEP = "\u0001"

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
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split(CONFIG_FIELD_SEP)
        val uri = parts.getOrNull(0)?.trim().orEmpty()
        if (uri.isBlank()) return@mapNotNull null
        val base = parseConfig(uri) ?: return@mapNotNull null
        if (parts.size < 5) return@mapNotNull base // old format, no cached geo yet
        base.copy(
            // A blank persisted value (every config saved before title-based
            // detection existed) must NOT clobber the fresh guess parseConfig just
            // computed from the title above — that was silently keeping old saved
            // configs flag-less forever even after the config's title clearly named
            // a country. A non-blank persisted value (set by the accurate live-tunnel
            // check) always wins over the guess, as it should.
            countryCode = parts[1].ifBlank { base.countryCode },
            city = parts[2].ifBlank { base.city },
            pingMs = parts[3].toIntOrNull() ?: -1,
            geoResolved = parts[4] == "1",
            // Older saves (5 fields) never ran the accurate probe — default false
            // so they get picked up by it once instead of being silently skipped.
            accurateGeoResolved = parts.getOrNull(5) == "1",
            // Older saves (6 fields, from before subscription import existed) had
            // no isImported/subscription* — default to "not imported", not null-string.
            isImported = parts.getOrNull(6) == "1",
            subscriptionId = parts.getOrNull(7)?.takeIf { it.isNotEmpty() },
            subscriptionName = parts.getOrNull(8)?.takeIf { it.isNotEmpty() },
        )
    }
}

private fun saveConfigs(context: Context, configs: List<SavedConfig>) {
    // Prevent crash with max 50 configs
    val limited = configs.take(50)
    val serialized = limited.joinToString("\n") { cfg ->
        listOf(
            cfg.uri, cfg.countryCode, cfg.city, cfg.pingMs.toString(),
            if (cfg.geoResolved) "1" else "0",
            if (cfg.accurateGeoResolved) "1" else "0",
            if (cfg.isImported) "1" else "0",
            cfg.subscriptionId.orEmpty(),
            cfg.subscriptionName.orEmpty(),
        ).joinToString(CONFIG_FIELD_SEP)
    }
    context.getSharedPreferences("cdnhunter_vpn", 0)
        .edit().putString("saved_configs", serialized).apply()
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

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else      -> "$bytes B"
    }
}

// ── MAIN APP ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Read theme from AppSettings and recompose when it changes
    var themeSetting by remember { mutableStateOf(AppSettings.theme(context)) }
    var amoledSetting by remember { mutableStateOf(AppSettings.amoledMode(context)) }
    
    // Convert to ThemeMode enum
    val themeMode = when (themeSetting) {
        AppSettings.THEME_LIGHT  -> ThemeMode.LIGHT
        AppSettings.THEME_DARK   -> ThemeMode.DARK
        else                     -> ThemeMode.SYSTEM
    }
    
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(AnanasBg, AnanasScreenBg, AnanasBg)))
    ) {
        CompositionLocalProvider(
            LocalThemeMode provides themeMode
        ) {
            VpnTab() // full-bleed root screen; owns internal navigation (Home/Locations/My Configs/Settings/Profile)
        }
    }
}

// ── ANANAS navigation (Home ⇄ Locations / My Configs / Settings / Profile) ─────
private enum class AnanasScreen { HOME, LOCATIONS, SETTINGS, PROFILE, SPLIT_TUNNEL }

// ── VPN TAB (Home / Connected — ANANAS reference) ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VpnTab() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    
    // Navigation stack for proper back button handling
    val navigationStack = remember { mutableStateListOf(AnanasScreen.HOME) }
    val currentScreen = navigationStack.lastOrNull() ?: AnanasScreen.HOME
    
    fun navigateTo(screen: AnanasScreen) {
        if (navigationStack.lastOrNull() != screen) {
            navigationStack.add(screen)
        }
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }
    
    // System back button: pop from stack, or exit if at HOME
    androidx.activity.compose.BackHandler(enabled = currentScreen != AnanasScreen.HOME || navigationStack.size > 1) {
        navigateBack()
    }

    // The Home Quick Switch bottom sheet is gone — Quick Switch is now an inline
    // list inside the Home column (Windscribe-style layout), so the hoisted
    // BottomSheetScaffold state and its auto-collapse timer that used to live
    // here no longer have anything to drive.

    val vpnPrefs = remember { context.getSharedPreferences("cdnhunter_vpn", 0) }

    var connectedSinceMs by remember { mutableStateOf(0L) }
    var elapsedSec        by remember { mutableStateOf(0L) }
    var downloadKBps      by remember { mutableStateOf(0.0) }
    var uploadKBps        by remember { mutableStateOf(0.0) }
    // Cumulative bytes for the whole session — from the moment this connection
    // came up until it's disconnected, like NPV's usage counter. mihomo's own
    // statistic.DefaultManager totals reset to 0 on every fresh Start(), so
    // these track exactly one connection's lifetime, not an all-time total.
    var totalDownloadBytes by remember { mutableStateOf(0L) }
    var totalUploadBytes   by remember { mutableStateOf(0L) }
    var exitCountryCode by remember { mutableStateOf("") }
    var exitCity by remember { mutableStateOf("") }
    var exitGeoConfigId by remember { mutableStateOf("") }
    // Rolling history of recent speed samples (KB/s), used to draw the live
    // sparkline chart inside each stat card. Capped so it never grows unbounded.
    val downloadHistory = remember { mutableStateListOf<Float>() }
    val uploadHistory = remember { mutableStateListOf<Float>() }
    val maxHistoryPoints = 40

    // Country/flag no longer needs a GeoService instance here — see enrichConfigGeo.

    // Enrich configs with country/city/ping whenever the SET of config ids changes
    // (add/remove), not on every write to `configs` itself. The old key
    // (configs.map { it.id }) created a NEW list every recomposition even when only
    // ping/geo fields changed, which re-triggered this effect, which wrote back into
    // `configs`, which re-triggered it again — an effect storm that could hang the UI
    // thread (worst when switching tabs forces a recomposition). Keying on a joined
    // id string only changes identity when configs are actually added/removed.
    val configCountAndIds = remember(configs.size, configs.firstOrNull()?.id, configs.lastOrNull()?.id) {
        Triple(configs.size, configs.firstOrNull()?.id, configs.lastOrNull()?.id)
    }
    val enrichingIds = remember { mutableSetOf<String>() }
    LaunchedEffect(configCountAndIds) {
        val toEnrich = configs.filter { !it.geoResolved && it.id !in enrichingIds }
        for (cfg in toEnrich) {
            enrichingIds += cfg.id
            try {
                val enriched = enrichConfigGeo(cfg)
                configs = configs.map { if (it.id == cfg.id) enriched else it }
                saveConfigs(context, configs)
            } finally {
                enrichingIds -= cfg.id
            }
        }
    }

    // Accurate geo for the active config now runs only AFTER a real connection is
    // already up, through that same live tunnel (see the "connected" poller below
    // and probeAccurateGeoViaLiveTunnel) — never as a separate speculative mihomo
    // instance beforehand. That background probe used to run concurrently with
    // whatever the user did next; if they hit connect while it was starting, the
    // real connect's own start() could race a probe instance that was still mid-
    // startup on the same process-wide mihomo core, which looked like "connect
    // does nothing, like the port's already taken." There's only ever one mihomo
    // instance running at any point now.


    // Poll VPN status + derive live throughput from CdnVpnService's cumulative byte counters
    LaunchedEffect(Unit) {
        var lastDown = CdnVpnService.downloadBytes
        var lastUp   = CdnVpnService.uploadBytes
        while (true) {
            val vpnRunning = CdnVpnService.isRunning.get()
            connected = vpnRunning

            if (connected) {
                connecting = false
                if (connectedSinceMs == 0L) connectedSinceMs = System.currentTimeMillis()
                elapsedSec = (System.currentTimeMillis() - connectedSinceMs) / 1000

                val curDown = CdnVpnService.downloadBytes
                val curUp   = CdnVpnService.uploadBytes
                downloadKBps = (curDown - lastDown).coerceAtLeast(0L) / 1024.0
                uploadKBps   = (curUp - lastUp).coerceAtLeast(0L) / 1024.0
                totalDownloadBytes = curDown
                totalUploadBytes   = curUp
                lastDown = curDown; lastUp = curUp

                downloadHistory.add(downloadKBps.toFloat())
                if (downloadHistory.size > maxHistoryPoints) downloadHistory.removeAt(0)
                uploadHistory.add(uploadKBps.toFloat())
                if (uploadHistory.size > maxHistoryPoints) uploadHistory.removeAt(0)

                exitCountryCode = CdnVpnService.exitCountryCode
                exitCity = CdnVpnService.exitCity
                exitGeoConfigId = CdnVpnService.exitGeoConfigId
            } else {
                connectedSinceMs = 0L; elapsedSec = 0L; downloadKBps = 0.0; uploadKBps = 0.0
                totalDownloadBytes = 0L; totalUploadBytes = 0L
                downloadHistory.clear(); uploadHistory.clear()
                lastDown = CdnVpnService.downloadBytes; lastUp = CdnVpnService.uploadBytes
                exitCountryCode = ""; exitCity = ""; exitGeoConfigId = ""
            }

            delay(1000)
        }
    }
    LaunchedEffect(connecting) {
        if (connecting) { delay(15000); if (!CdnVpnService.isRunning.get()) connecting = false }
    }

    // Continuous ping monitoring — live update like v2rayng
    // Updates all configs' ping values every 3 seconds in background
    val pingMonitorJobs = remember { mutableMapOf<String, kotlinx.coroutines.Job>() }
    LaunchedEffect(configs.size) {
        // Start ping monitoring for new configs
        for (cfg in configs) {
            if (pingMonitorJobs[cfg.id] == null) {
                pingMonitorJobs[cfg.id] = launch {
                    try {
                        while (this.isActive && configs.find { it.id == cfg.id } != null) {
                            // Skip re-measuring the currently active, connected server.
                            // measurePingMs dials the raw backend address directly from
                            // this app's own process (excluded from the VPN itself --
                            // see addDisallowedApplication in CdnVpnService). For a
                            // CDN-fronted/reality server that's often exactly the
                            // address that's blocked or throttled when reached
                            // directly, which is the whole reason it needs fronting
                            // in the first place. Repeatedly failing that direct
                            // probe once connected kept overwriting a perfectly good
                            // last-known ping with -1, which is why the ping badge
                            // visibly disappeared right after connecting.
                            val isActiveConnected = cfg.id == activeId && connected
                            if (!isActiveConnected) {
                                val newPing = measurePingMs(cfg.address, cfg.port, timeoutMs = 3000)
                                if (newPing != cfg.pingMs) {
                                    configs = configs.map { if (it.id == cfg.id) it.copy(pingMs = newPing) else it }
                                }
                            }
                            delay(3000)
                        }
                    } finally {
                        pingMonitorJobs.remove(cfg.id)
                    }
                }
            }
        }
        
        // Cancel ping jobs for removed configs
        pingMonitorJobs.forEach { (id, job) ->
            if (configs.find { it.id == id } == null) {
                job.cancel()
                pingMonitorJobs.remove(id)
            }
        }
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
            CdnVpnService.stop(context); connected = false
        } else {
            if (connected) { CdnVpnService.stop(context); connected = false }
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
        if (cfg.id == activeId && connected) { CdnVpnService.stop(context); connected = false }
        val updated = configs.filter { it.id != cfg.id }
        configs = updated
        saveConfigs(context, updated)
        if (cfg.id == activeId) activeId = ""
    }

    // Deletes every config imported from one subscription in one go — the
    // subscription group row is the only delete affordance for imported
    // servers now (see ServerListItem/SubscriptionGroupRow); individual
    // imported rows don't get their own swipe-delete.
    fun deleteSubscription(subId: String) {
        val toRemove = configs.filter { it.subscriptionId == subId }
        if (toRemove.any { it.id == activeId } && connected) { CdnVpnService.stop(context); connected = false }
        val updated = configs.filter { it.subscriptionId != subId }
        configs = updated
        saveConfigs(context, updated)
        if (toRemove.any { it.id == activeId }) activeId = ""
    }

    // Shared by clipboard-instant-add and QR-scan-add: parse, save, toast — no dialog.
    // An http(s):// link is treated as a subscription (fetched, base64-decoded if
    // needed, one proxy per line) rather than a single config — that's the only
    // thing that made "Invalid config link" fire for subscription links before:
    // parseConfig only ever understood vless/trojan/vmess/ss/socks5 URIs directly.
    fun addConfigFromUri(uri: String, sourceLabel: String) {
        val trimmed = uri.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            android.widget.Toast.makeText(context, "Fetching subscription…", android.widget.Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val subId = java.util.UUID.randomUUID().toString()
                val subName = try { java.net.URI(trimmed).host ?: "Subscription" } catch (e: Exception) { "Subscription" }
                val added = withContext(Dispatchers.IO) {
                    try {
                        val response = java.net.URL(trimmed).readText(Charsets.UTF_8)
                        // Subscriptions are commonly the whole body base64-encoded
                        // (V2RayN/Clash convention); fall back to raw text if it isn't.
                        val decoded = try {
                            String(java.util.Base64.getDecoder().decode(response.trim()), Charsets.UTF_8)
                        } catch (e: Exception) {
                            response
                        }
                        decoded.split("\n", "\r\n")
                            .map { it.trim() }
                            .filter {
                                it.startsWith("vless://") || it.startsWith("trojan://") ||
                                    it.startsWith("vmess://") || it.startsWith("ss://") ||
                                    it.startsWith("socks5://")
                            }
                            // Reuse the SAME per-line parser as manual add (ConfigUriParser-backed,
                            // captures every proxy field) rather than a stripped-down duplicate —
                            // otherwise imported servers would be missing uuid/cipher/tls/reality
                            // fields and simply fail to connect.
                            .mapNotNull { line -> parseConfig(line) }
                            .map { it.copy(isImported = true, subscriptionId = subId, subscriptionName = subName) }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                if (added.isEmpty()) {
                    android.widget.Toast.makeText(context, "No valid servers found in subscription", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                val existingUris = configs.map { it.uri }.toSet()
                val newOnes = added.filter { it.uri !in existingUris }
                if (newOnes.isEmpty()) {
                    android.widget.Toast.makeText(context, "Already added", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                configs = configs + newOnes
                saveConfigs(context, configs)
                android.widget.Toast.makeText(context, "Added ${newOnes.size} server(s) from subscription", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        val cfg = parseConfig(trimmed)
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
    val otherConfigs  = remember(configs, activeConfig?.id) {
        val active = activeConfig
        when {
            active == null -> emptyList()
            active.isImported && active.subscriptionId != null ->
                // Connected via a subscription -- only show that subscription's other servers.
                configs.filter { it.id != active.id && it.isImported && it.subscriptionId == active.subscriptionId }
            else ->
                // Connected via a manually-added (Main) config -- only show other Main configs.
                configs.filter { it.id != active.id && !it.isImported }
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val durationMs = 250  // Faster transitions
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMs, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
            ) + fadeIn(animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) togetherWith 
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMs, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
            ) + fadeOut(animationSpec = tween(durationMs / 2, easing = FastOutSlowInEasing))
        },
        label = "screenTransition"
    ) { targetScreen ->
        when (targetScreen) {
    AnanasScreen.HOME -> {
        if (configs.isEmpty()) {
            // ── Empty state ────────────────────────────────────────────────────────────
            Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
                EmptyHomeState { navigateTo(AnanasScreen.LOCATIONS) }
            }
        } else {
            // ── Windscribe-style layout ────────────────────────────────────────────────
            // Background: dark gradient — near-black top that deepens toward bottom,
            // with a subtle burgundy/dark-red radial tint behind the header area to
            // match the Windscribe look (server name area glows warmer than the rest).
            // The radial's center has to be a real pixel offset, so the screen size is
            // measured here instead of guessed — an off-canvas center (the old
            // Float.MAX_VALUE / 2f) put the whole tint outside the drawn area.
            var homeBgSizePx by remember { mutableStateOf(IntSize.Zero) }
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { homeBgSizePx = it }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0A0C), Color(0xFF050507), Color(0xFF030304))
                        )
                    )
                    // Burgundy warm radial behind the server header
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3D0A14).copy(alpha = 0.55f),
                                Color(0xFF1A060A).copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(homeBgSizePx.width / 2f, 0f),
                            radius = 900f,
                        )
                    )
            ) {
                Column(Modifier.fillMaxSize()) {

                    // ── Top bar ────────────────────────────────────────────────────────
                    // Hamburger (Settings) left, PowerButton top-right (small, 72dp).
                    // statusBarsPadding() is a no-op with the current non-edge-to-edge
                    // theme (the window already excludes the status bar), so the explicit
                    // top padding is what actually keeps the row off the top edge — same
                    // 22.dp the old centred top row used.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp)
                            .padding(top = 18.dp, bottom = 6.dp)
                    ) {
                        // Left: hamburger → Settings
                        AnanasIconButton(Icons.Rounded.Menu, Modifier.align(Alignment.CenterStart)) {
                            navigateTo(AnanasScreen.SETTINGS)
                        }
                        // Right: mini PowerButton (72dp) + status dot below it
                        Column(
                            Modifier.align(Alignment.CenterEnd),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SmallPowerButton(
                                connected = connected,
                                connecting = connecting,
                                onClick = { activeConfig?.let { connectConfig(it) } }
                            )
                            Spacer(Modifier.height(4.dp))
                            // Status dot row: ● Connected / ● Connecting / ○ Off
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                connected -> AnanasAccent
                                                connecting -> Color(0xFFFFD60A)
                                                else -> AnanasMuted
                                            }
                                        )
                                )
                                Text(
                                    when { connected -> "ON"; connecting -> "…"; else -> "OFF" },
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = when { connected -> AnanasAccent; connecting -> Color(0xFFFFD60A); else -> AnanasMuted },
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // ── Server header (Windscribe style) ──────────────────────────────
                    // City Bold + server/region name regular, large — matches Windscribe's
                    // "Frankfurt  Sausage Party" treatment.
                    activeConfig?.let { cfg ->
                        val displayCc = if (connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank())
                            exitCountryCode else cfg.countryCode
                        val displayCity = if (connected && exitGeoConfigId == cfg.id && exitCity.isNotBlank())
                            exitCity else cfg.city

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF3D0A14).copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            // City bold + server name regular on same row, large text
                            val cityPart = displayCity.ifBlank { countryCodeToName(displayCc).ifBlank { displayCc } }
                            val serverPart = cfg.displayName
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = AnanasTextHi)) {
                                        append(cityPart)
                                    }
                                    append("  ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Light, color = AnanasText.copy(alpha = 0.85f))) {
                                        append(serverPart)
                                    }
                                },
                                fontSize = 26.sp,
                                lineHeight = 30.sp,
                                letterSpacing = (-0.4).sp
                            )
                            Spacer(Modifier.height(8.dp))
                            // ISP-style row: status color badge + IP or "Connecting..."
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Country flag small
                                CountryFlagBadge(displayCc, 18.dp)
                                // Protocol tag pill
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AnanasBorder.copy(alpha = 0.8f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        cfg.network.uppercase().ifBlank { "VLESS" },
                                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        color = AnanasMuted, letterSpacing = 0.3.sp
                                    )
                                }
                                // Elapsed / status text
                                Text(
                                    when {
                                        connected -> formatElapsed(elapsedSec)
                                        connecting -> "Connecting…"
                                        else -> "Tap ▷ to connect"
                                    },
                                    fontSize = 12.sp, color = AnanasMuted, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // ── Server info card (traffic) ─────────────────────────────────────
                    Column(
                        Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        activeConfig?.let { cfg ->
                            val downloadTotal = if (connected) formatBytes(totalDownloadBytes) else null
                            val uploadTotal   = if (connected) formatBytes(totalUploadBytes)   else null
                            // Soft status-color glow for the card, matching PowerButton's
                            // exact color formula (colorA/colorB) and breathing gently in
                            // sync with it -- layered ON TOP of the existing gold gradient
                            // below (kept as-is, per request), not replacing it. Low alpha
                            // so it reads as a subtle tint, not a competing color scheme.
                            val cardGlowInfinite = rememberInfiniteTransition(label = "cardGlow")
                            val cardGlowBreathe by cardGlowInfinite.animateFloat(
                                0.6f, 1f,
                                infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "cardGlowBreathe"
                            )
                            val cardGlowColor by animateColorAsState(
                                targetValue = when {
                                    connected -> Color(0xFF34D9A8)
                                    connecting -> Color(0xFFFFC93C)
                                    else -> Color(0xFF3B6FFF)
                                },
                                animationSpec = tween(950, easing = FastOutSlowInEasing), label = "cardGlowColor"
                            )
                            // One unified card, same size/style whether showing server
                            // info, or one of 3 traffic sub-pages (download/upload/scale)
                            // -- 4 pages total, all on ONE flat pager. This used to be a
                            // pager-inside-a-pager (server info | nested 3-page traffic
                            // pager), which is what crashed: a Pager nested inside another
                            // Pager's page is unsupported/unstable in Compose Foundation,
                            // regardless of how the outer height was constrained. Flat is
                            // both simpler and the only version that doesn't crash.
                            val cardPagerState = rememberPagerState(pageCount = { if (connected) 3 else 1 })
                            // Page 0 (server info) has the same structure regardless of
                            // connected state -- only the text inside changes -- so its
                            // natural height is constant. Measure it once, then pin the
                            // pager to that exact value in BOTH states instead of
                            // switching between wrapContentHeight() (disconnected) and a
                            // hand-picked 210dp (connected) that didn't actually match it,
                            // which is what caused the card to visibly resize on connect.
                            var page0HeightPx by remember { mutableStateOf(0) }
                            val density = LocalDensity.current
                            val pagerHeightModifier = if (page0HeightPx > 0)
                                Modifier.height(with(density) { page0HeightPx.toDp() })
                            else Modifier.height(210.dp) // bounded fallback until measured
                            var cardSizePx by remember { mutableStateOf(IntSize.Zero) }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { cardSizePx = it }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF211C12),
                                                AnanasCard,
                                                Color(0xFF12100C),
                                            ),
                                        )
                                    )
                                    // Calm gold sheen from the top-left corner only —
                                    // warm and subtle, not a bright/flashy highlight.
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFD4AF6A).copy(alpha = 0.07f),
                                                Color(0xFFD4AF6A).copy(alpha = 0.03f),
                                                Color.Transparent
                                            ),
                                            center = Offset(0f, 0f),
                                            radius = 420f,
                                        )
                                    )
                                    // Status-color glow from the bottom-right corner,
                                    // gently breathing in sync with the connect button's
                                    // own glow -- kept faint (low alpha) so the gold
                                    // gradient above stays the dominant visual. Uses the
                                    // card's actual measured size (not an infinite-
                                    // coordinate trick) so the corner position is a real,
                                    // correct pixel offset.
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                cardGlowColor.copy(alpha = 0.10f * cardGlowBreathe),
                                                cardGlowColor.copy(alpha = 0.045f * cardGlowBreathe),
                                                Color.Transparent
                                            ),
                                            center = Offset(cardSizePx.width.toFloat(), cardSizePx.height.toFloat()),
                                            radius = 480f,
                                        )
                                    )
                            ) {
                                // Fixed height so the card doesn't visually grow/shrink
                                // when swiping between pages, or when connecting/
                                // disconnecting -- see page0HeightPx above.
                                HorizontalPager(
                                    state = cardPagerState,
                                    modifier = pagerHeightModifier
                                ) { page ->
                                    when (page) {
                                        0 -> Column(
                                            Modifier.fillMaxWidth()
                                                .onSizeChanged { if (it.height > 0) page0HeightPx = it.height }
                                                .clickable { navigateTo(AnanasScreen.LOCATIONS) }
                                                .padding(horizontal = 18.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            // Server row
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                                                ) {
                                                    CountryFlagBadge(
                                                        if (connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()) exitCountryCode else cfg.countryCode,
                                                        32.dp
                                                    )
                                                    Column {
                                                        val useExitGeo = connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()
                                                        val effectiveCc = if (useExitGeo) exitCountryCode else cfg.countryCode
                                                        val effectiveCity = if (useExitGeo) exitCity else cfg.city
                                                        val countryName = countryCodeToName(effectiveCc)
                                                        val locationLine = when {
                                                            countryName.isNotBlank() && effectiveCity.isNotBlank() -> "$countryName · $effectiveCity"
                                                            countryName.isNotBlank() -> countryName
                                                            !cfg.geoResolved -> "Resolving location…"
                                                            else -> cfg.displayName
                                                        }
                                                        val pingLine = if (cfg.pingMs >= 0) "${cfg.pingMs} ms · ${pingQualityLabel(cfg.pingMs)}" else "—"
                                                        Text(locationLine, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                                                        Text(
                                                            if (connected) "${cfg.network.uppercase()} · Active" else pingLine,
                                                            fontSize = 11.sp, color = AnanasMuted, modifier = Modifier.padding(top = 1.dp)
                                                        )
                                                    }
                                                }
                                                Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(16.dp))
                                            }
                                            // Divider
                                            Box(Modifier.fillMaxWidth().height(1.dp).background(AnanasDivider))
                                            // Stats row
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(Icons.Rounded.ArrowDownward, null, tint = AnanasMuted, modifier = Modifier.size(12.dp))
                                                        Text("DOWNLOAD", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                                    }
                                                    Text(downloadTotal ?: "0 B", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.5).sp)
                                                }
                                                Column(
                                                    Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(Icons.Rounded.ArrowUpward, null, tint = AnanasMuted, modifier = Modifier.size(12.dp))
                                                        Text("UPLOAD", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                                    }
                                                    Text(uploadTotal ?: "0 B", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.5).sp)
                                                }
                                            }
                                        }
                                        // Pages 1-3: traffic sub-pages, now direct siblings of
                                        // page 0 on the SAME flat pager instead of a nested
                                        // pager-inside-a-pager (which crashed regardless of how
                                        // the outer height was constrained).
                                        1 -> Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                                            TrafficChartCard(
                                                title = "DOWNLOAD",
                                                icon = Icons.Rounded.ArrowDownward,
                                                dataPoints = downloadHistory,
                                                currentValue = downloadKBps.toFloat(),
                                                totalBytes = totalDownloadBytes,
                                                accentColor = Color(0xFF64D2FF),
                                                isDownload = true,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        else -> Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                                            TrafficChartCard(
                                                title = "UPLOAD",
                                                icon = Icons.Rounded.ArrowUpward,
                                                dataPoints = uploadHistory,
                                                currentValue = uploadKBps.toFloat(),
                                                totalBytes = totalUploadBytes,
                                                accentColor = Color(0xFFFFD60A),
                                                isDownload = false,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                            if (connected) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(3) { index ->
                                        val isSelected = cardPagerState.currentPage == index
                                        val dotColor = when (index) {
                                            0 -> AnanasAccent
                                            1 -> Color(0xFF64D2FF)
                                            else -> Color(0xFFFFD60A)
                                        }
                                        Box(
                                            Modifier
                                                .padding(horizontal = 3.dp)
                                                .size(if (isSelected) 7.dp else 6.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) dotColor else AnanasBorder2)
                                        )
                                    }
                                }
                            }
                        }

                        // ── Quick Switch inline list ───────────────────────────────────
                        if (otherConfigs.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AnanasCard)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("QUICK SWITCH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                    Text(
                                        "See all",
                                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = AnanasAccent,
                                        modifier = Modifier.clickable { navigateTo(AnanasScreen.LOCATIONS) }
                                    )
                                }
                                otherConfigs.take(5).forEachIndexed { idx, cfg ->
                                    QuickSwitchRow(
                                        cfg = cfg,
                                        onClick = { connectConfig(cfg) },
                                        showDivider = idx < minOf(otherConfigs.size, 5) - 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                } // Column
            } // Box
        }
    }
    AnanasScreen.LOCATIONS -> {
        // Re-sync from disk every time this screen is entered — a defensive
        // measure so the Locations list can never show "no configs" while
        // configs actually exist on disk, regardless of what happened to the
        // in-memory `configs` state via recomposition.
        LaunchedEffect(Unit) {
            val onDisk = loadConfigs(context)
            if (onDisk != configs) configs = onDisk
        }
        LocationsScreen(
            configs = configs, activeId = activeId, connected = connected,
            onBack = { navigateBack() },
            onConnect = { selectConfig(it); navigateBack() },
            onDelete = { deleteConfig(it) },
            onDeleteSubscription = { deleteSubscription(it) },
            showAddMenu = showAddMenu, onToggleAddMenu = { showAddMenu = !showAddMenu },
            onScanQr = ::startQrScan, onClipboard = ::addFromClipboard,
        )
    }

    AnanasScreen.SETTINGS -> SettingsScreen(
        onProfileClick = { navigateTo(AnanasScreen.PROFILE) },
        onSplitTunnelClick = { navigateTo(AnanasScreen.SPLIT_TUNNEL) },
        onBack = { navigateBack() }
    )

    AnanasScreen.PROFILE -> ProfileScreen(onBack = { navigateBack() })
    AnanasScreen.SPLIT_TUNNEL -> SplitTunnelScreen(onBack = { navigateBack() })
        }
    }
}

// ── Small Power Button: same aurora ring, 72dp — sits top-right ──────────────
@Composable
private fun SmallPowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val size = 72.dp
    val iconSize = 22.dp

    val infinite = rememberInfiniteTransition(label = "smallPwrInfinite")
    val t1 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t1s")
    val t2 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "t2s")
    val t3 by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing)), label = "t3s")
    val breathe by infinite.animateFloat(
        0.95f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breatheS"
    )
    val colorA by animateColorAsState(
        targetValue = when { connected -> Color(0xFF10B981); connecting -> Color(0xFFFFC93C); else -> Color(0xFF3B82F6) },
        animationSpec = tween(950), label = "colAs"
    )
    val colorB by animateColorAsState(
        targetValue = when { connected -> Color(0xFF34D399); connecting -> Color(0xFFFFD60A); else -> Color(0xFF8B5CF6) },
        animationSpec = tween(950), label = "colBs"
    )
    val colorC by animateColorAsState(
        targetValue = when { connected -> Color(0xFF6EE7B7); connecting -> Color(0xFFFF9500); else -> Color(0xFF60A5FA) },
        animationSpec = tween(950), label = "colCs"
    )

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        AuroraCanvasGlow(
            colorA = colorA, colorB = colorB, colorC = colorC,
            t1 = t1, t2 = t2, t3 = t3, breathe = breathe,
            modifier = Modifier.size(size),
            ringSize = size,
        )
        Box(
            Modifier
                .size(size * 0.72f)
                .clip(CircleShape)
                .background(AnanasCard)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = "Connect",
                tint = when { connected -> AnanasAccent; connecting -> Color(0xFFFFC93C); else -> AnanasText },
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// ── Power button: aurora ribbon hugging the button's own edge ──────────────────
// Not on Home anymore — the Windscribe-style layout puts SmallPowerButton in the
// top bar instead. Kept as the full-size version of the same aurora (and the
// only one that uses AuroraShaderGlow on API 33+) in case Home goes back to a
// hero button.
@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    // Multiple independent time counters at incommensurate rates. Because their
    // periods (7s, 11s, 13s, 17s) are all prime relative to each other the
    // combined pattern doesn't repeat for 7*11*13*17 ≈ 17,000 seconds -- it
    // never visibly restarts. No single "rotation" uniform means no visible
    // full-circle sweep, so there's no point where you see the aurora "lapping"
    // back to where it started and producing an obvious loop artifact.
    val infinite = rememberInfiniteTransition(label = "power")
    val t1 by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t1")
    val t2 by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "t2")
    val t3 by infinite.animateFloat(0f, 1f,
        infiniteRepeatable(tween(13000, easing = LinearEasing)), label = "t3")
    // Raised the floor: 0.82->0.96 read as faint for most of its cycle since
    // the whole glow is multiplied by this. 0.95->1.0 keeps the same gentle
    // pulse but the glow now stays essentially full-bright throughout instead
    // of visibly dimming.
    val breathe by infinite.animateFloat(
        0.95f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    val colorA by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF34D9A8)
            connecting -> Color(0xFFFFC93C)
            else -> Color(0xFF3B6FFF)
        },
        animationSpec = tween(950, easing = FastOutSlowInEasing), label = "colorA"
    )
    val colorB by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF3FA8E0)
            connecting -> Color(0xFFFF5A5A)
            else -> Color(0xFF8A5CFF)
        },
        animationSpec = tween(950, easing = FastOutSlowInEasing), label = "colorB"
    )
    val colorC by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF39D8DD)
            connecting -> Color(0xFFFF8C4B)
            else -> Color(0xFF4D4CFF)
        },
        animationSpec = tween(950, easing = FastOutSlowInEasing), label = "colorC"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            AuroraShaderGlow(
                colorA = colorA, colorB = colorB, colorC = colorC,
                t1 = t1, t2 = t2, t3 = t3, breathe = breathe,
                modifier = Modifier.size(280.dp)
            )
        } else {
            AuroraCanvasGlow(
                colorA = colorA, colorB = colorB, colorC = colorC,
                t1 = t1, t2 = t2, t3 = t3, breathe = breathe,
                modifier = Modifier.size(280.dp)
            )
        }
        Box(
            Modifier.size(200.dp).clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1c1c1f), Color(0xFF0a0a0c)),
                        start = Offset(0f, 0f), end = Offset(200f, 200f)
                    )
                )
                .clickable(
                    enabled = !connecting,
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, null,
                tint = colorA,
                modifier = Modifier.size(64.dp))
        }
    }
}

private const val AURORA_AGSL = """
    uniform float2 resolution;
    uniform float t1;
    uniform float t2;
    uniform float t3;
    uniform float breathe;
    uniform float4 colorA;
    uniform float4 colorB;
    uniform float4 colorC;

    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord / resolution) * 2.0 - 1.0;
        uv.x *= resolution.x / resolution.y;

        float dist = length(uv);
        float angle = atan(uv.y, uv.x);

        float a1 = angle * 3.0 + t1 * 6.28;
        float a2 = angle * 5.0 - t2 * 6.28 * 0.7;
        float a3 = angle * 7.3 + t3 * 6.28 * 1.1;
        float a4 = angle * 11.0 + (t1 - t2) * 6.28 * 0.4;
        float a5 = angle * 2.0 - t3 * 6.28 * 0.3 + t1 * 2.1;
        float noise = sin(a1)*0.024 + sin(a2)*0.018 + sin(a3)*0.012
                    + sin(a4)*0.008 + sin(a5)*0.016;

        float sweepRaw = 0.5 + 0.5 * sin(angle + t1*6.28*0.5 + noise*4.0);
        float sweep = smoothstep(0.0, 1.0, sweepRaw);
        float3 aurora;
        if (sweep < 0.5) {
            aurora = mix(colorA.rgb, colorB.rgb, smoothstep(0.0, 1.0, sweep*2.0));
        } else {
            aurora = mix(colorB.rgb, colorC.rgb, smoothstep(0.0, 1.0, (sweep-0.5)*2.0));
        }
        float shimmer = 0.92 + 0.08 * sin(angle*4.0 + t2*6.28*0.8 + noise*2.0);
        aurora *= shimmer;

        float ringRadius = 0.64 + noise * 0.045;
        // Thinner core (0.038 -> 0.024) reads as a defined line instead of a
        // soft haze; pow exponent raised 0.85 -> 0.6 so the mask climbs to
        // full strength faster across that thinner band instead of trailing
        // off gradually, which is what "کمرنگ" (faint) actually looks like
        // up close on a thin ring.
        float ringWidth = 0.024;
        float ringDist = abs(dist - ringRadius);
        float ringMask = smoothstep(ringWidth*1.5, 0.0, ringDist);
        ringMask = pow(ringMask, 0.6);

        float glowFalloff = smoothstep(1.0, ringRadius - 0.05, dist);
        float glowMask = pow(glowFalloff, 2.8);

        // glowMask*0.55 -> *1.0: the outward glow was the main source of
        // faintness, barely lifting above the dark button background at 0.55.
        // clamped with min() since ringMask+glowMask can now exceed 1.
        float finalAlpha = min(1.0, (ringMask + glowMask) * breathe);
        float buttonCutout = smoothstep(ringRadius - 0.012, ringRadius + 0.008, dist);

        // Push the aurora color 20% over white-point before the alpha cutout
        // (half4 clamps on output) for a hotter, more saturated core on the
        // ring than the flat colorA/B/C mix gives at alpha 1.0 alone.
        return half4(aurora * 1.2 * finalAlpha * buttonCutout, finalAlpha * buttonCutout);
    }
"""

@Composable
private fun AuroraShaderGlow(
    colorA: Color, colorB: Color, colorC: Color,
    t1: Float, t2: Float, t3: Float, breathe: Float,
    modifier: Modifier = Modifier
) {
    val shader = remember { android.graphics.RuntimeShader(AURORA_AGSL) }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        shader.setFloatUniform("resolution", w, h)
        shader.setFloatUniform("t1", t1)
        shader.setFloatUniform("t2", t2)
        shader.setFloatUniform("t3", t3)
        shader.setFloatUniform("breathe", breathe)
        shader.setFloatUniform("colorA", colorA.red, colorA.green, colorA.blue, 1f)
        shader.setFloatUniform("colorB", colorB.red, colorB.green, colorB.blue, 1f)
        shader.setFloatUniform("colorC", colorC.red, colorC.green, colorC.blue, 1f)
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply { this.shader = shader }
            drawRect(0f, 0f, w, h, paint)
        }
    }
}

@Composable
private fun AuroraCanvasGlow(
    colorA: Color, colorB: Color, colorC: Color,
    t1: Float, t2: Float, t3: Float, breathe: Float,
    modifier: Modifier = Modifier,
    // Diameter of the outermost glow layer. Every layer below is expressed as a
    // fraction of it so the same aurora renders correctly at any diameter -- the
    // 280dp hero button and the 72dp top-bar one. The default reproduces the
    // original hard-coded 278/252/224dp + 40/24/3dp stroke values exactly.
    ringSize: Dp = 278.dp,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Less blur (26dp -> 16dp) so this outer layer doesn't wash out into a
        // faint haze, alpha raised to nearly full so it actually contributes
        // visible light instead of a barely-there tint.
        Canvas(Modifier.size(ringSize).blur(ringSize * 0.0576f)) {
            val stroke = (ringSize * 0.1439f).toPx()
            drawArc(
                brush = Brush.sweepGradient(listOf(colorA, colorB, colorC, colorA)),
                startAngle = t1 * 360f, sweepAngle = 360f, useCenter = false,
                alpha = 0.8f * breathe,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke/2, stroke/2),
                size = Size(size.width - stroke, size.height - stroke)
            )
        }
        Canvas(Modifier.size(ringSize * 0.9065f).blur(ringSize * 0.0288f)) {
            val stroke = (ringSize * 0.0863f).toPx()
            drawArc(
                brush = Brush.sweepGradient(listOf(colorB, colorC, colorA, colorB)),
                startAngle = -t2 * 360f + 40f, sweepAngle = 300f, useCenter = false,
                alpha = 0.9f * breathe,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke/2, stroke/2),
                size = Size(size.width - stroke, size.height - stroke)
            )
        }
        // Sharp core ring: no blur, thinner stroke (3dp), full alpha -- the
        // one layer meant to read as a crisp line rather than glow.
        Canvas(Modifier.size(ringSize * 0.8058f)) {
            val stroke = (ringSize * 0.0108f).coerceAtLeast(1.5.dp).toPx()
            drawArc(
                brush = Brush.sweepGradient(listOf(colorA, colorB, colorA)),
                startAngle = t3 * 360f, sweepAngle = 360f, useCenter = false,
                alpha = breathe,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke/2, stroke/2),
                size = Size(size.width - stroke, size.height - stroke)
            )
        }
    }
}



// ── Add-config sheet: sliding bottom sheet with QR scan / clipboard ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConfigSheet(
    expanded: Boolean, onToggle: () -> Unit,
    onScanQr: () -> Unit, onClipboard: () -> Unit,
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


// ── Location Section Header (improved styling, no emoji) ──────────────────────
@Composable
private fun LocationSectionHeader(title: String, subtitle: String = "") {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = AnanasTextHi
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = AnanasMuted,
                letterSpacing = 0.3.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ── Country Badge Placeholder (no flag emoji) ────────────────────────────────
@Composable
private fun CountryCodeBadge(countryCode: String, size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2A2A2A),
                        Color(0xFF1F1F1F)
                    )
                )
            )
            .border(1.5.dp, Color(0xFF3A3A3A), RoundedCornerShape(size * 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = countryCode.uppercase().take(2),
            fontSize = (size * 0.4f).value.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFAAAAAA),
            letterSpacing = 0.5.sp
        )
    }
}

// ── Server List Item — minimal flat row (Windscribe-style) ───────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListItem(
    cfg: SavedConfig, activeId: String, connected: Boolean,
    onConnect: (SavedConfig) -> Unit, onDelete: (SavedConfig) -> Unit,
    indented: Boolean = false, showDeleteSwipe: Boolean = true,
) {
    val isActive = cfg.id == activeId
    var removed by remember(cfg.id) { mutableStateOf(false) }

    LaunchedEffect(removed) {
        if (removed) {
            delay(180) // let the collapse animation play before the row actually leaves the list
            onDelete(cfg)
        }
    }

    // Custom swipe instead of Material3's SwipeToDismissBox: that component kept
    // showing its red backgroundContent at REST (not just while actively being
    // dragged) — a real, reported Material3 layout quirk where the foreground
    // content's resting offset doesn't reliably settle at exactly zero. Driving
    // the background's own alpha directly off a real drag offset we own removes
    // any possibility of that: at offsetX == 0 the background is provably
    // invisible, not just "supposed to be covered by the row on top".
    val offsetX = remember(cfg.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxSwipePx = with(density) { -84.dp.toPx() }

    val rowContent = @Composable {
        Box(Modifier.fillMaxWidth()) {
            // Thin left accent bar instead of a full-row color wash. A flat
            // green tint across the whole row (flag, text, everything) read
            // as a muddy halo sitting on top of the flag's own glass sheen.
            // A slim bar signals "this one" clearly without discoloring the
            // row's content.
            if (isActive) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .align(Alignment.CenterStart)
                        .background(AnanasAccent, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onConnect(cfg) }
                    .padding(start = if (indented) 42.dp else 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CountryFlagBadge(cfg.countryCode, 30.dp)

                Column(Modifier.weight(1f)) {
                    Text(
                        cfg.displayName,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                        color = if (isActive) AnanasTextHi else AnanasText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (isActive && connected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(AnanasAccent))
                            Text(
                                "CONNECTED", fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                color = AnanasAccent, letterSpacing = 0.4.sp
                            )
                        }
                    }
                }

                // Static quality indicator (3-bar signal icon), not a live ping readout —
                // Windscribe-style: reflects the last-known tier, doesn't imply an
                // active measurement is happening on every recomposition.
                if (cfg.pingMs >= 0) SignalBars(cfg.pingMs)
            }
        }
    }

    AnimatedVisibility(
        visible = !removed,
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
    ) {
        if (showDeleteSwipe) {
            Box(Modifier.fillMaxWidth()) {
                // Only ever drawn (and only ever above alpha 0) while offsetX is
                // actually non-zero — never a static/always-on layer.
                val revealFraction = (offsetX.value / maxSwipePx).coerceIn(0f, 1f)
                if (revealFraction > 0f) {
                    Row(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.85f * revealFraction))
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Delete, null,
                            tint = Color.White.copy(alpha = revealFraction),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                coroutineScope.launch {
                                    offsetX.snapTo((offsetX.value + delta).coerceIn(maxSwipePx * 1.15f, 0f))
                                }
                            },
                            onDragStopped = {
                                coroutineScope.launch {
                                    if (offsetX.value < maxSwipePx * 0.55f) {
                                        removed = true
                                    } else {
                                        offsetX.animateTo(0f, animationSpec = tween(200))
                                    }
                                }
                            }
                        )
                ) {
                    rowContent()
                }
            }
        } else {
            // Imported (subscription) rows: no per-row delete at all — the
            // whole subscription is deleted from its one group-header icon.
            rowContent()
        }
    }
}

// Windscribe-style 3-bar signal/quality indicator — replaces a live "X ms"
// readout with a static, glanceable tier: all 3 bars green (good), 2 bars
// yellow (medium), 1 bar red (poor). No text, no per-frame measurement.
@Composable
private fun SignalBars(pingMs: Int) {
    val (filled, color) = when {
        pingMs < 150 -> 3 to Color(0xFF4ADE9C)
        pingMs < 350 -> 2 to Color(0xFFFFD60A)
        else         -> 1 to Color(0xFFEF4444)
    }
    val barHeights = listOf(6.dp, 9.dp, 12.dp)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        barHeights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < filled) color else AnanasBorder2)
            )
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
                    CountryFlagBadge(cfg.countryCode, 38.dp)
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(sub, fontSize = 11.5.sp, fontWeight = FontWeight.Normal, color = AnanasMuted)
                                if (cfg.geoResolved) PingBars(cfg.pingMs)
                            }
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
private fun SelectedServerSummaryCard(
    cfg: SavedConfig, connected: Boolean, onClick: () -> Unit,
    exitCountryCode: String = "", exitCity: String = "", exitGeoConfigId: String = "",
) {
    // Once connected, prefer the tunnel-verified real exit location over the
    // pre-connect estimate (cfg.countryCode/cfg.city, resolved by looking up
    // the config's hostname directly — wrong for any domain behind a CDN,
    // since that reports the CDN edge's location, not the real backend
    // server's). CdnVpnService populates exitCountryCode/exitCity by asking a
    // geo-IP service through the active tunnel itself once connected; only
    // trust it when exitGeoConfigId matches this config, so a lookup from a
    // previous connection can never be shown against the wrong server.
    val useExitGeo = connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()
    val effectiveCc = if (useExitGeo) exitCountryCode else cfg.countryCode
    val effectiveCity = if (useExitGeo) exitCity else cfg.city
    val countryName = remember(effectiveCc) { countryCodeToName(effectiveCc) }
    val locationLine = when {
        countryName.isNotBlank() && effectiveCity.isNotBlank() -> "$countryName · $effectiveCity"
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
            CountryFlagBadge(effectiveCc, 32.dp)
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
            CountryFlagBadge(cfg.countryCode, 26.dp)
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

// ── Locations — visual reference screen (static demo data, wired later) ────────
@Composable
private fun LocationsScreen(
    configs: List<SavedConfig>, activeId: String, connected: Boolean,
    onBack: () -> Unit, onConnect: (SavedConfig) -> Unit, onDelete: (SavedConfig) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    showAddMenu: Boolean, onToggleAddMenu: () -> Unit,
    onScanQr: () -> Unit, onClipboard: () -> Unit,
) {
    AddConfigSheet(expanded = showAddMenu, onToggle = onToggleAddMenu, onScanQr = onScanQr, onClipboard = onClipboard)

    var query by remember { mutableStateOf("") }
    val filtered = remember(configs, query) {
        if (query.isBlank()) configs
        else configs.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    val mainConfigs = remember(filtered) { filtered.filter { !it.isImported } }
    val importedConfigs = remember(filtered) { filtered.filter { it.isImported } }
    // Each subscription collapses into a single group row; tapping it expands
    // in place to show that subscription's servers, one at a time (Windscribe-
    // style), instead of every subscription's servers always being on-screen.
    val subscriptionGroups = remember(importedConfigs) {
        importedConfigs.groupBy { it.subscriptionId }
            .filterKeys { it != null }
            .map { (subId, cfgs) -> subId!! to cfgs }
    }
    var expandedSubId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text("Locations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
                    Text(
                        if (configs.isEmpty()) "No configs yet" else "${configs.size} config${if (configs.size == 1) "" else "s"} saved",
                        fontSize = 11.5.sp, color = AnanasMuted
                    )
                }
                AnanasIconButton(Icons.Rounded.Add, onClick = onToggleAddMenu)
            }

            // Minimal search field — no card/border, just an underline, Windscribe-style.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Search, null, tint = AnanasFaint, modifier = Modifier.size(16.dp))
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

            if (configs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Public, null, tint = AnanasFaint, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No servers yet", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap + above to add one", fontSize = 12.sp, color = AnanasMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(mainConfigs, key = { it.id }) { cfg ->
                        ServerListItem(cfg, activeId, connected, onConnect, onDelete)
                    }

                    items(subscriptionGroups, key = { "sub_${it.first}" }) { (subId, cfgs) ->
                        val subName = cfgs.first().subscriptionName ?: "Subscription"
                        val isExpanded = expandedSubId == subId
                        Column {
                            SubscriptionGroupRow(
                                name = subName,
                                count = cfgs.size,
                                expanded = isExpanded,
                                onClick = { expandedSubId = if (isExpanded) null else subId },
                                onDelete = { onDeleteSubscription(subId) },
                            )
                            AnimatedVisibility(visible = isExpanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                Column {
                                    cfgs.forEach { cfg ->
                                        ServerListItem(cfg, activeId, connected, onConnect, onDelete, indented = true, showDeleteSwipe = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Collapsed subscription group — tap to expand in place. Flat row, no card, just
// a chevron rotation and a muted count, Windscribe-style.
@Composable
private fun SubscriptionGroupRow(name: String, count: Int, expanded: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val rowInteraction = remember { MutableInteractionSource() }
    val deleteInteraction = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = rowInteraction, indication = null) { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(AnanasCard2),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Cloud, null, tint = AnanasMuted, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
            Text("$count server${if (count == 1) "" else "s"}", fontSize = 11.sp, color = AnanasMuted)
        }
        // The only delete affordance for an entire subscription — individual
        // imported rows don't have their own. indication = null on purpose:
        // a plain clickable's default Material ripple can render as a flat
        // black box depending on the active color scheme, which read as a
        // rendering glitch rather than a button. Opens a confirmation dialog
        // instead of deleting immediately.
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = deleteInteraction, indication = null) { showDeleteConfirm = true },
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                Icons.Rounded.Delete, null, tint = AnanasMuted,
                modifier = Modifier.padding(start = 2.dp).size(16.dp)
            )
        }
        Icon(
            Icons.Rounded.ChevronRight, null, tint = AnanasFaint,
            modifier = Modifier.size(18.dp).rotate(chevronRotation)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AnanasCard,
            shape = RoundedCornerShape(18.dp),
            icon = {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(AnanasRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Delete, null, tint = AnanasRed, modifier = Modifier.size(20.dp)) }
            },
            title = { Text("Remove subscription?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi) },
            text = {
                Text(
                    "\"$name\" and all $count of its server${if (count == 1) "" else "s"} will be removed. This can't be undone.",
                    fontSize = 13.sp, color = AnanasMuted
                )
            },
            confirmButton = {
                Text(
                    "Remove", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = AnanasRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            showDeleteConfirm = false
                            onDelete()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    "Cancel", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            showDeleteConfirm = false
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            },
        )
    }
}

// ── Settings — ANANAS reference (replaces old Tools/ScannerTab entirely) ───────
@Composable
private fun SettingsScreen(
    onProfileClick: () -> Unit = {}, onSplitTunnelClick: () -> Unit = {}, onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val vpnPrefs = remember { context.getSharedPreferences("cdnhunter_vpn", 0) }
    var autoReconnect by remember { mutableStateOf(AppSettings.autoReconnectEnabled(context)) }
    // Backed by the same "kill_switch_enabled" key CdnVpnService reads
    // (isKillSwitchEnabled()) before deciding whether to hold a dead TUN up
    // after an unexpected disconnect -- this toggle is now the actual, live
    // switch for that behavior, not a decorative local-only state.
    var killSwitch by remember { mutableStateOf(AppSettings.killSwitchEnabled(context)) }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onClick = onBack)
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

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column {
                    SettingsRow(Icons.Rounded.VerifiedUser, "Protocol", "VLESS", AnanasSettingsIcon, showChevron = true)
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsToggleRow(
                    Icons.Rounded.Autorenew, "Auto-reconnect", "Reconnect if connection drops",
                    autoReconnect, {
                        autoReconnect = it
                        AppSettings.setAutoReconnectEnabled(context, it)
                    }
                )
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                SettingsToggleRow(
                    Icons.Rounded.Lock, "Kill switch", "Block traffic on disconnect",
                    killSwitch, {
                        killSwitch = it
                        AppSettings.setKillSwitchEnabled(context, it)
                    }
                )
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                run {
                    val splitApps = AppSettings.splitTunnelApps(context)
                    val splitMode = AppSettings.splitTunnelMode(context)
                    val summary = when {
                        splitApps.isEmpty() -> null
                        splitMode == "include" -> "${splitApps.size} app${if (splitApps.size == 1) "" else "s"} only"
                        else -> "${splitApps.size} app${if (splitApps.size == 1) "" else "s"} excluded"
                    }
                    SettingsRow(Icons.Rounded.CallSplit, "Split tunneling", summary, AnanasSettingsIcon, showChevron = true, onClick = onSplitTunnelClick)
                }
                Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                run {
                    var adBlockEnabled by remember { mutableStateOf(AppSettings.adBlockerEnabled(context)) }
                    SettingsToggleRow(
                        Icons.Rounded.Block, "Ad blocker", "Block ads & tracking domains",
                        adBlockEnabled, {
                            adBlockEnabled = it
                            AppSettings.setAdBlockerEnabled(context, it)
                        }
                    )
                }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("NETWORK", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column {
                    var mtuMode by remember { mutableStateOf(AppSettings.mtuPreset(context)) }
                    var customMtuText by remember { mutableStateOf(AppSettings.mtu(context).toString()) }
                    var showCustomInput by remember { mutableStateOf(mtuMode == "custom") }
                    var allowLan by remember { mutableStateOf(AppSettings.allowLan(context)) }
                    var ipv6Enabled by remember { mutableStateOf(AppSettings.ipv6Enabled(context)) }
                    var useDoh by remember { mutableStateOf(AppSettings.useDoh(context)) }

                    // MTU Section
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MTU Size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                            Text("Default: 1500 bytes", fontSize = 11.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(
                                "Auto", fontSize = 12.sp,
                                fontWeight = if (mtuMode == "auto") FontWeight.Bold else FontWeight.Medium,
                                color = if (mtuMode == "auto") AnanasAccent else AnanasMuted,
                                modifier = Modifier.clickable {
                                    mtuMode = "auto"
                                    showCustomInput = false
                                    AppSettings.setMtu(context, 1500)
                                    AppSettings.setMtuPreset(context, "auto")
                                }.padding(vertical = 4.dp)
                            )
                            Text(
                                "Custom", fontSize = 12.sp,
                                fontWeight = if (mtuMode == "custom") FontWeight.Bold else FontWeight.Medium,
                                color = if (mtuMode == "custom") AnanasAccent else AnanasMuted,
                                modifier = Modifier.clickable {
                                    showCustomInput = !showCustomInput; mtuMode = "custom"
                                }.padding(vertical = 4.dp)
                            )
                        }
                    }

                    if (showCustomInput) {
                        Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                        TextField(
                            value = customMtuText,
                            onValueChange = {
                                customMtuText = it
                                it.toIntOrNull()?.let { value ->
                                    if (value in 576..9000) {
                                        AppSettings.setMtu(context, value)
                                        AppSettings.setMtuPreset(context, "custom")
                                    }
                                }
                            },
                            label = { Text("MTU (576-9000)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = AnanasCard2,
                                unfocusedContainerColor = AnanasCard2
                            ),
                            singleLine = true
                        )
                    }

                    Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsToggleRow(
                        Icons.Rounded.Router, "Allow LAN", "Access local network devices",
                        allowLan, {
                            allowLan = it
                            AppSettings.setAllowLan(context, it)
                        }
                    )
                    Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsToggleRow(
                        Icons.Rounded.Language, "IPv6", "Route IPv6 traffic through VPN",
                        ipv6Enabled, {
                            ipv6Enabled = it
                            AppSettings.setIpv6Enabled(context, it)
                        }
                    )
                    Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsToggleRow(
                        Icons.Rounded.Security, "DNS over HTTPS", "Encrypt DNS queries with DoH",
                        useDoh, {
                            useDoh = it
                            AppSettings.setUseDoh(context, it)
                            // Notify VPN service of settings change
                            android.widget.Toast.makeText(
                                context, 
                                if (it) "DoH enabled (reconnect to apply)" else "DoH disabled (reconnect to apply)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("DNS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column {
                    var customDnsEnabled by remember { mutableStateOf(AppSettings.customDnsEnabled(context)) }
                    var primaryDns by remember { mutableStateOf(AppSettings.primaryDns(context)) }
                    var secondaryDns by remember { mutableStateOf(AppSettings.secondaryDns(context)) }
                    var showDnsInputs by remember { mutableStateOf(customDnsEnabled) }

                    // Custom DNS Toggle
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Custom DNS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                            Text("Use your own DNS servers", fontSize = 11.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
                        }
                        MinimalToggle(
                            checked = customDnsEnabled,
                            onCheckedChange = {
                                customDnsEnabled = it
                                showDnsInputs = it
                                AppSettings.setCustomDnsEnabled(context, it)
                            }
                        )
                    }

                    // DNS Inputs (shown when enabled)
                    if (showDnsInputs) {
                        Divider(color = AnanasDivider, thickness = 1.dp, modifier = Modifier.padding(horizontal = 14.dp))
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            // Primary DNS
                            val isPrimaryValid = AppSettings.isValidDnsServer(primaryDns)
                            TextField(
                                value = primaryDns,
                                onValueChange = {
                                    primaryDns = it
                                    if (AppSettings.isValidDnsServer(it)) {
                                        AppSettings.setPrimaryDns(context, it)
                                    }
                                },
                                label = { Text("Primary DNS", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = AnanasCard2,
                                    unfocusedContainerColor = AnanasCard2,
                                    focusedIndicatorColor = if (isPrimaryValid) AnanasAccent else AnanasRed,
                                    unfocusedIndicatorColor = if (isPrimaryValid) AnanasBorder else AnanasRed.copy(0.5f)
                                ),
                                singleLine = true,
                                isError = !isPrimaryValid && primaryDns.isNotBlank(),
                                placeholder = { Text("8.8.8.8 or https://8.8.8.8/dns-query", fontSize = 9.sp, color = AnanasMuted.copy(0.5f)) }
                            )
                            if (!isPrimaryValid && primaryDns.isNotBlank()) {
                                Text("Invalid DNS format", fontSize = 9.sp, color = AnanasRed, modifier = Modifier.padding(top = 2.dp))
                            }
                            
                            // Secondary DNS
                            val isSecondaryValid = secondaryDns.isBlank() || AppSettings.isValidDnsServer(secondaryDns)
                            TextField(
                                value = secondaryDns,
                                onValueChange = {
                                    secondaryDns = it
                                    if (it.isBlank() || AppSettings.isValidDnsServer(it)) {
                                        AppSettings.setSecondaryDns(context, it)
                                    }
                                },
                                label = { Text("Secondary DNS (optional)", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = AnanasCard2,
                                    unfocusedContainerColor = AnanasCard2,
                                    focusedIndicatorColor = if (isSecondaryValid) AnanasAccent else AnanasRed,
                                    unfocusedIndicatorColor = if (isSecondaryValid) AnanasBorder else AnanasRed.copy(0.5f)
                                ),
                                singleLine = true,
                                isError = !isSecondaryValid && secondaryDns.isNotBlank(),
                                placeholder = { Text("8.8.4.4 or quic://dns.google:853", fontSize = 9.sp, color = AnanasMuted.copy(0.5f)) }
                            )
                            if (!isSecondaryValid && secondaryDns.isNotBlank()) {
                                Text("Invalid DNS format", fontSize = 9.sp, color = AnanasRed, modifier = Modifier.padding(top = 2.dp))
                            }
                            
                            // DNS Leak Protection Info
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AnanasCard2.copy(0.5f)).padding(10.dp)
                            ) {
                                Column {
                                    Text("DNS Leak Protection", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasAccent)
                                    Text("• All DNS queries are hijacked and routed through the tunnel", fontSize = 8.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 3.dp))
                                    Text("• DoH (HTTPS) is recommended for security", fontSize = 8.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
                                    Text("• Formats: IP, IP:port, https://... or quic://...", fontSize = 8.5.sp, color = AnanasMuted, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }

            val clip = LocalClipboardManager.current

            Spacer(Modifier.height(26.dp))
            Text("DEBUG", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                    .border(1.dp, AnanasBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Terminal, null, tint = AnanasAccent, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Connection log", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AnanasText)
                        Text(
                            if (CdnVpnService.lastError.isNotBlank()) "Last error: ${CdnVpnService.lastError.take(40)}" else "No errors on last connect",
                            fontSize = 10.5.sp, color = if (CdnVpnService.lastError.isNotBlank()) AnanasRed else AnanasMuted
                        )
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(AnanasCard2).border(1.dp, AnanasBorder2, RoundedCornerShape(9.dp))
                        .clickable {
                            val text = "lastError:\n${CdnVpnService.lastError}\n\ndebugLog:\n${CdnVpnService.debugLog}\n\nprotectLog:\n${MihomoBridge.protectLog()}\n\ncoreLog:\n${MihomoBridge.coreLog()}"
                            clip.setText(AnnotatedString(text))
                            android.widget.Toast.makeText(context, "Connection log copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnanasText) }
            }

            val crashFile = remember { File(context.filesDir, com.cdnhunter.app.CdnHunterApp.CRASH_LOG_FILE) }
            if (crashFile.exists()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AnanasCard)
                        .border(1.dp, AnanasRed.copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.BugReport, null, tint = AnanasRed, modifier = Modifier.size(22.dp))
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
private fun SettingsRow(icon: ImageVector, label: String, value: String?, iconTint: Color, showChevron: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                if (value != null) {
                    Text(value, fontSize = 12.sp, color = AnanasMuted, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        if (showChevron) {
            Icon(Icons.Rounded.ChevronRight, null, tint = AnanasMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(icon: ImageVector, label: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, null, tint = AnanasSettingsIcon, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi)
                Text(
                    desc,
                    fontSize = 11.5.sp,
                    color = AnanasMuted,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bespoke toggle — narrower capsule, soft drop shadow under the thumb,
        // no Material ripple halo on tap. See MinimalToggle() below.
        MinimalToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// Hand-built toggle instead of Material3's Switch: a narrower capsule track
// (44x24 vs Material's wider default), a plain white circular thumb carrying
// its own soft drop shadow for a touch of depth, smooth 180ms slide + color
// crossfade, and — critically — no ripple halo on tap (Switch always draws
// one, which read as a stray flash on this dark background).
@Composable
private fun MinimalToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) AnanasAccent else AnanasCard2,
        animationSpec = tween(180), label = "toggleTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing), label = "toggleThumb"
    )
    Box(
        modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset, top = 2.dp)
                .size(20.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White)
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
                AnanasIconButton(Icons.Rounded.ChevronLeft, onClick = onBack)
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

private data class InstalledAppInfo(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable?)

@Composable
private fun SplitTunnelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppSettings.splitTunnelMode(context)) }
    var selected by remember { mutableStateOf(AppSettings.splitTunnelApps(context)) }
    var search by remember { mutableStateOf("") }
    // Loading installed apps (PackageManager.getInstalledApplications) can
    // take a noticeable moment on a device with many apps -- do it off the
    // main thread once, not on every recomposition.
    var apps by remember { mutableStateOf<List<InstalledAppInfo>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName } // excluding ourselves is pointless -- see establishTun()
                .mapNotNull { appInfo ->
                    try {
                        InstalledAppInfo(
                            packageName = appInfo.packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                            icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (_: Exception) { null }
                        )
                    } catch (_: Exception) { null }
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    val filtered = remember(apps, search) {
        val list = apps ?: emptyList()
        if (search.isBlank()) list else list.filter { it.label.contains(search, ignoreCase = true) }
    }

    fun persist(newSelected: Set<String>, newMode: String) {
        selected = newSelected
        mode = newMode
        AppSettings.setSplitTunnelApps(context, newSelected)
        AppSettings.setSplitTunnelMode(context, newMode)
    }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onClick = onBack)
                Text("Split tunneling", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
            }

            // Mode picker: exclude (VPN off just for selected apps, on for
            // everything else -- e.g. excluding a banking app) vs include
            // (VPN ONLY for selected apps, off for everything else).
            // Switching modes doesn't clear the selection -- the same app
            // list just gets reinterpreted under the new mode, matching how
            // most VPN apps with this feature behave (Windscribe included).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("exclude" to "Exclude selected", "include" to "Only selected").forEach { (value, label) ->
                    val isSelected = mode == value
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AnanasAccent.copy(0.16f) else AnanasCard)
                            .border(1.dp, if (isSelected) AnanasAccent.copy(0.5f) else AnanasBorder, RoundedCornerShape(12.dp))
                            .clickable { persist(selected, value) }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (isSelected) AnanasAccent else AnanasMuted)
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp)).background(AnanasCard).border(1.dp, AnanasBorder, RoundedCornerShape(12.dp))
            ) {
                TextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Search apps", fontSize = 13.sp, color = AnanasFaint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AnanasFaint, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AnanasText, unfocusedTextColor = AnanasText,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (apps == null) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AnanasAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isChecked = selected.contains(app.packageName)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    persist(if (isChecked) selected - app.packageName else selected + app.packageName, mode)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap(width = 84, height = 84).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AnanasCard2))
                            }
                            Text(app.label, fontSize = 13.5.sp, color = AnanasText, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    persist(if (checked) selected + app.packageName else selected - app.packageName, mode)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AnanasAccent, uncheckedColor = AnanasFaint)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
@Composable
private fun StatBox(icon: ImageVector, label: String, accentColor: Color, sessionTotal: String?, history: List<Float>, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.08f),
                        accentColor.copy(alpha = 0.04f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                )
            )
            .border(1.2.dp, accentColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            // ── Header: Icon + Label ──
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon with background glow
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = accentColor, 
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    label, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = AnanasMuted.copy(alpha = 0.9f),
                    letterSpacing = 0.5.sp
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // ── Total value ──
            Text(
                sessionTotal ?: "0 B",
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold, 
                color = accentColor,
                letterSpacing = (-0.5).sp
            )
            
            // ── Sparkline with gradient ──
            SpeedSparkline(
                history = history, 
                color = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(top = 10.dp)
            )
        }
    }
}

// Smooth sparkline: a gradient-filled area under a curved line through the recent
// speed samples. Auto-scales to the current data's own max so small and large
// transfers both look proportionate rather than flat or clipped.
@Composable
private fun SpeedSparkline(history: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (history.size < 2) {
            // Flat baseline while there's not enough data yet — still looks intentional.
            drawLine(
                color.copy(alpha = 0.25f),
                Offset(0f, h - 1f), Offset(w, h - 1f),
                strokeWidth = 2f
            )
            return@Canvas
        }
        val maxVal = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = w / (history.size - 1).toFloat()
        val points = history.mapIndexed { i, v ->
            val x = i * stepX
            val y = h - (v / maxVal) * (h * 0.85f) - h * 0.05f
            Offset(x, y)
        }

        // Smooth path through the points using quadratic mid-point interpolation.
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                quadraticBezierTo(p0.x, p0.y, midX, midY)
            }
            lineTo(points.last().x, points.last().y)
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.0f)),
                startY = 0f, endY = h
            )
        )
        drawPath(
            linePath,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ── Icon button (top bar) ───────────────────────────────────────────────────────
@Composable
private fun AnanasIconButton(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = AnanasText, modifier = Modifier.size(22.dp)) }
}

// ── Empty state (ANANAS styled) ─────────────────────────────────────────────────
@Composable
private fun EmptyHomeState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(AnanasCard)
                    .border(1.dp, AnanasBorder, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, null, tint = AnanasMuted, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("No configs yet", fontSize = 15.sp, color = AnanasTextHi, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to add a trojan / vless / vmess config", fontSize = 12.sp, color = AnanasMuted)
        }
    }
}
