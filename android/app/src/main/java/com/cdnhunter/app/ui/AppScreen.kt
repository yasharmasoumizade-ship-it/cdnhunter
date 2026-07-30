package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import android.net.VpnService
import androidx.compose.ui.platform.LocalContext
import com.cdnhunter.app.engine.GeoService
import java.io.File
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.MihomoBridge
import com.cdnhunter.app.vpn.AppSettings
import com.cdnhunter.app.ui.components.TrafficChartCard
import com.cdnhunter.app.ui.components.DetailedTrafficBreakdown
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

// Resolves country/city + ping for a single config. Runs on IO dispatcher.
// Geo is looked up against cfg.sni (the TLS SNI / real destination host) when
// present, falling back to cfg.address only if there's no sni. address is
// often just the tunnel/CDN entry point the client connects to -- for
// reality/ECH/domain-fronted configs the actual backend server is identified
// by SNI, and geo-IP on the front address would report the tunnel hop's
// country instead of the real server's.
private suspend fun enrichConfigGeo(geo: GeoService, cfg: SavedConfig): SavedConfig =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ping = measurePingMs(cfg.address, cfg.port)
        // If the config's own title already named a country (e.g. "CF GERMANY" ->
        // DE), that beats the on-device IP lookup below: for CDN-fronted domains,
        // an IP-based lookup on the SNI/address frequently reports the CDN edge's
        // location (e.g. a US Cloudflare PoP) instead of the real backend server
        // the title is naming. The lookup is only used to FILL IN a guess when the
        // title didn't name a country, never to override one that's already there.
        // The one check that's actually reliable for CDN-fronted configs -- through
        // the live tunnel, after a real connection -- runs separately (see
        // CdnVpnService) and is free to overwrite either of these once it succeeds.
        val info = if (cfg.countryCode.isBlank()) {
            try { geo.lookupGeoInfo(cfg.sni.ifBlank { cfg.address }) } catch (e: Exception) { null }
        } else null
        cfg.copy(
            pingMs = ping,
            countryCode = cfg.countryCode.ifBlank { info?.cc.orEmpty() },
            city = cfg.city.ifBlank { info?.city.orEmpty() },
            geoResolved = true,
        )
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
        CountryCodeBadge(countryCode, size)
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
private fun countryCodeFromTitle(title: String): String? {
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
            countryCode = parts[1],
            city = parts[2],
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
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
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
@OptIn(ExperimentalMaterial3Api::class)
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
    
    // Hoisted OUTSIDE AnimatedContent so it survives navigation away and back.
    // Re-creating this fresh every recomposition tore down and rebuilt the
    // sheet's swipeable-state, sometimes mid-animation-frame — causing freezes
    // when returning to Home. Now it's only created once and reused.
    val homeSheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    
    // Auto-collapse Quick Switch after 4 seconds of inactivity
    LaunchedEffect(homeSheetState.bottomSheetState.currentValue) {
        if (homeSheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
            kotlinx.coroutines.delay(4000)  // 4 seconds
            if (homeSheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
                homeSheetState.bottomSheetState.partialExpand()
            }
        }
    }
    
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

    val geoService = remember { GeoService() }

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
                val enriched = enrichConfigGeo(geoService, cfg)
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
                            val newPing = measurePingMs(cfg.address, cfg.port, timeoutMs = 3000)
                            if (newPing != cfg.pingMs) {
                                configs = configs.map { if (it.id == cfg.id) it.copy(pingMs = newPing) else it }
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
    val otherConfigs  = configs.filter { it.id != activeConfig?.id }

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
            Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
                EmptyHomeState { navigateTo(AnanasScreen.LOCATIONS) }
            }
        } else {
            BottomSheetScaffold(
                scaffoldState = homeSheetState,
                sheetPeekHeight = if (otherConfigs.isNotEmpty()) 58.dp else 0.dp,
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
                                    modifier = Modifier.clickable { navigateTo(AnanasScreen.LOCATIONS) }
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
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnanasIconButton(Icons.Rounded.Menu) { navigateTo(AnanasScreen.SETTINGS) }
                            AnanasIconButton(Icons.Rounded.Person) { navigateTo(AnanasScreen.PROFILE) }
                        }

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
                                when { connected -> "Protected"; connecting -> "Connecting\u2026"; else -> "Not protected" },
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.2).sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (connected) formatElapsed(elapsedSec) else "Tap to connect",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AnanasMuted, letterSpacing = 0.3.sp
                            )
                        }

                        Column(
                            Modifier.weight(1f).padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            activeConfig?.let { cfg ->
                                    val downloadTotal = if (connected) formatBytes(totalDownloadBytes) else null
                                    val uploadTotal   = if (connected) formatBytes(totalUploadBytes)   else null
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(AnanasCard)
                                            .border(1.5.dp, AnanasBorder, RoundedCornerShape(16.dp))
                                            .clickable { navigateTo(AnanasScreen.LOCATIONS) }
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
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
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(Icons.Rounded.ArrowDownward, null, tint = AnanasMuted, modifier = Modifier.size(12.dp))
                                                        Text("DOWNLOAD", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                                    }
                                                    Text(downloadTotal ?: "0 B", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.5).sp)
                                                }
                                                Box(Modifier.width(1.dp).height(36.dp).background(AnanasDivider))
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(Icons.Rounded.ArrowUpward, null, tint = AnanasMuted, modifier = Modifier.size(12.dp))
                                                        Text("UPLOAD", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 0.4.sp)
                                                    }
                                                    Text(uploadTotal ?: "0 B", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AnanasTextHi, letterSpacing = (-0.5).sp)
                                                }
                                            }
                                        }
                                    }
                                
                                // Traffic charts - detailed breakdown with sparklines. Already
                                // has its own internal HorizontalPager for left/right swiping
                                // between traffic stat pages (see TrafficCharts.kt) -- it just
                                // needs to sit directly below the main card without requiring
                                // a scroll-down first, which is what moving this out of a
                                // LazyColumn's item {} into a plain Column achieves.
                                if (connected) {
                                    DetailedTrafficBreakdown(
                                        downloadBytes = totalDownloadBytes,
                                        uploadBytes = totalUploadBytes,
                                        downloadHistory = downloadHistory,
                                        uploadHistory = uploadHistory,
                                        currentDownloadKbps = downloadKBps.toFloat(),
                                        currentUploadKbps = uploadKBps.toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

// ── Power button: pulsing rings + rotating sweep arc (ANANAS reference) ────────
@Composable
private fun PowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "power")

    val rippleProgress1 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing)), label = "ripple1"
    )
    val rippleProgress2 by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), initialStartOffset = StartOffset(1100)),
        label = "ripple2"
    )
    val scanRotation by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "scan"
    )
    val breathe by infinite.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 200, easing = EaseInOutQuad),
        finishedListener = { if (isPressed) isPressed = false },
        label = "press"
    )

    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        if (connected) {
            Canvas(Modifier.size(280.dp)) {
                val maxRadius = this.size.minDimension / 2f
                val radius = maxRadius * (0.40f + rippleProgress1 * 0.60f)
                val alpha = (1f - rippleProgress1).coerceIn(0f, 1f) * 0.55f
                drawCircle(color = AnanasAccent.copy(alpha = alpha), radius = radius,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            Canvas(Modifier.size(280.dp)) {
                val maxRadius = this.size.minDimension / 2f
                val radius = maxRadius * (0.40f + rippleProgress2 * 0.60f)
                val alpha = (1f - rippleProgress2).coerceIn(0f, 1f) * 0.65f
                drawCircle(color = AnanasAccent.copy(alpha = alpha), radius = radius,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        if (connecting) {
            Canvas(Modifier.size(240.dp).rotate(scanRotation)) {
                drawArc(color = AnanasAccent, startAngle = 0f, sweepAngle = 35f, useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = AnanasAccent.copy(alpha = 0.4f), startAngle = 180f, sweepAngle = 35f, useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier.size(210.dp).scale(scale).clip(CircleShape)
                .background(
                    if (connected) Brush.radialGradient(
                        colors = listOf(AnanasAccent.copy(alpha = 0.12f * breathe), Color.Transparent), radius = 180f
                    ) else Brush.radialGradient(colors = listOf(Color.Transparent, Color.Transparent))
                ),
            contentAlignment = Alignment.Center
        ) {}
        Box(
            Modifier.size(200.dp).scale(scale).clip(CircleShape)
                .background(
                    if (connected)
                        Brush.linearGradient(colors = listOf(Color(0xFF1c1c1f), Color(0xFF0a0a0c)),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(200f, 200f))
                    else
                        Brush.linearGradient(colors = listOf(Color(0xFF18181b), Color(0xFF0d0d0f)),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(200f, 200f))
                )
                .clickable(enabled = !connecting) { isPressed = true; onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                CircularProgressIndicator(color = AnanasAccent, strokeWidth = 2.2.dp, modifier = Modifier.size(44.dp))
            } else {
                Icon(Icons.Rounded.PowerSettingsNew, null,
                    tint = if (connected) AnanasAccent else Color(0xFF7e8084),
                    modifier = Modifier.size(64.dp))
            }
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

// ── Server List Item (improved styling, clean location display) ──────────────
@Composable
private fun ServerListItem(
    cfg: SavedConfig, activeId: String, connected: Boolean,
    onConnect: (SavedConfig) -> Unit, onDelete: (SavedConfig) -> Unit
) {
    val isActive = cfg.id == activeId
    val badgeColor = when (cfg.proto.lowercase()) {
        "trojan" -> Color(0xFF4ADE9C)
        "vless"  -> Color(0xFF64D2FF)
        "vmess"  -> Color(0xFFFFD60A)
        else     -> Color(0xFF8B8B8B)
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Server", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Remove ${cfg.displayName}?", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = { onDelete(cfg); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A))
                ) { Text("Cancel", fontSize = 11.sp) }
            }
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onConnect(cfg) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
        border = BorderStroke(
            1.5.dp,
            if (isActive) Color(0xFF4ADE9C).copy(alpha = 0.5f) else Color(0xFF2A2A2E)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isActive) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .align(Alignment.Start)
                    .background(Color(0xFF4ADE9C), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            )
        }
        
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isActive) 15.dp else 16.dp,
                    top = 14.dp,
                    end = 14.dp,
                    bottom = 14.dp
                )
        ) {
            // Header row with country + name + actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Country code badge (no flag)
                    CountryCodeBadge(cfg.countryCode, 36.dp)
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = cfg.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFAFAFA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        
                        if (isActive && connected) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE9C))
                                )
                                Text(
                                    "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE9C),
                                    letterSpacing = 0.2.sp
                                )
                            }
                        } else {
                            val statusText = if (cfg.pingMs >= 0) {
                                "${cfg.pingMs} ms"
                            } else {
                                "Ready to connect"
                            }
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                color = Color(0xFF8B8B8B),
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
                
                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { /* Show QR */ },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF252529))
                            .border(1.5.dp, Color(0xFF2F2F33), RoundedCornerShape(9.dp))
                    ) {
                        Icon(Icons.Rounded.QrCode2, null, tint = Color(0xFFC0C0C0), modifier = Modifier.size(16.dp))
                    }
                    
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF252529))
                            .border(1.5.dp, Color(0xFF2F2F33), RoundedCornerShape(9.dp))
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            // Protocol tags
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cfg.proto.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        letterSpacing = 0.3.sp
                    )
                }
                
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF3A3A3F).copy(alpha = 0.6f))
                        .border(1.dp, Color(0xFF4A4A4F), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cfg.network.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA0A0A0),
                        letterSpacing = 0.2.sp
                    )
                }
            }
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
    showAddMenu: Boolean, onToggleAddMenu: () -> Unit,
    onScanQr: () -> Unit, onClipboard: () -> Unit,
) {
    AddConfigSheet(expanded = showAddMenu, onToggle = onToggleAddMenu, onScanQr = onScanQr, onClipboard = onClipboard)

    var query by remember { mutableStateOf("") }
    val filtered = remember(configs, query) {
        if (query.isBlank()) configs
        else configs.filter { it.displayName.contains(query, ignoreCase = true) }
    }
    
    // Separate Main (manual) vs Imported (subscription)
    val mainConfigs = remember(filtered) {
        filtered.filter { !it.isImported }
    }
    val importedConfigs = remember(filtered) {
        filtered.filter { it.isImported }
    }
    val importedBySubscription = remember(importedConfigs) {
        importedConfigs.groupBy { it.subscriptionId }
    }

    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
                Column(Modifier.weight(1f)) {
                    Text("Locations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, letterSpacing = (-0.3).sp)
                    Text(
                        if (configs.isEmpty()) "No configs yet" else "${configs.size} config${if (configs.size == 1) "" else "s"} saved",
                        fontSize = 11.5.sp, color = AnanasMuted
                    )
                }
                AnanasIconButton(Icons.Rounded.Add, onToggleAddMenu)
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
                    Text("Tap + above to add one", fontSize = 12.sp, color = AnanasMuted)
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.dp)) {
                    // ========== MAIN SECTION ==========
                    if (mainConfigs.isNotEmpty()) {
                        item(key = "main_header") {
                            LocationSectionHeader("MAIN", "Manually added")
                        }
                        items(mainConfigs, key = { it.id }) { cfg ->
                            ServerListItem(cfg, activeId, connected, onConnect, onDelete)
                        }
                    }
                    
                    // ========== IMPORTED SECTION ==========
                    if (importedConfigs.isNotEmpty()) {
                        item(key = "imported_header") {
                            LocationSectionHeader("IMPORTED SUBSCRIPTIONS")
                        }
                        
                        // For each subscription group
                        for ((subId, cfgs) in importedBySubscription) {
                            if (subId != null && cfgs.isNotEmpty()) {
                                val subName = cfgs.first().subscriptionName ?: "Subscription"
                                
                                item(key = "sub_header_$subId") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            subName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AnanasTextHi
                                        )
                                        Text(
                                            "${cfgs.size} servers",
                                            fontSize = 10.sp,
                                            color = AnanasMuted
                                        )
                                    }
                                }
                                
                                items(cfgs, key = { it.id }) { cfg ->
                                    ServerListItem(cfg, activeId, connected, onConnect, onDelete)
                                }
                            }
                        }
                    }
                }
            }
        }
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

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column {
                    SettingsRow(Icons.Rounded.VerifiedUser, "Protocol", "VLESS", AnanasAccent, showChevron = true)
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
                    Icons.Rounded.Lock, "Kill switch", "Block traffic if VPN disconnects unexpectedly",
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
                    SettingsRow(Icons.Rounded.CallSplit, "Split tunneling", summary, AnanasAccent, showChevron = true, onClick = onSplitTunnelClick)
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    mtuMode = "auto"
                                    showCustomInput = false
                                    AppSettings.setMtu(context, 1500)
                                    AppSettings.setMtuPreset(context, "auto")
                                },
                                modifier = Modifier.height(34.dp).width(60.dp).clip(RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mtuMode == "auto") AnanasAccent else AnanasCard2,
                                    contentColor = if (mtuMode == "auto") Color.White else AnanasText
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Auto", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { showCustomInput = !showCustomInput; mtuMode = "custom" },
                                modifier = Modifier.height(34.dp).width(70.dp).clip(RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mtuMode == "custom") AnanasAccent else AnanasCard2,
                                    contentColor = if (mtuMode == "custom") Color.White else AnanasText
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Custom", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
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

            Spacer(Modifier.height(10.dp))
            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)),
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
                        Switch(
                            checked = customDnsEnabled,
                            onCheckedChange = {
                                customDnsEnabled = it
                                showDnsInputs = it
                                AppSettings.setCustomDnsEnabled(context, it)
                            },
                            modifier = Modifier.scale(1.15f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AnanasAccent,
                                uncheckedThumbColor = AnanasMuted.copy(alpha = 0.6f),
                                uncheckedTrackColor = AnanasCard2
                            )
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

            Spacer(Modifier.height(26.dp))
            Text("APPEARANCE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column(Modifier.padding(16.dp)) {
                    var theme by remember { mutableStateOf(AppSettings.theme(context)) }

                    Text("Theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AnanasTextHi, modifier = Modifier.padding(bottom = 12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (themeOption in listOf("Light", "Dark", "Auto")) {
                            Button(
                                onClick = {
                                    theme = themeOption.lowercase()
                                    AppSettings.setTheme(context, theme)
                                    (context as? android.app.Activity)?.recreate()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (theme == themeOption.lowercase()) AnanasAccent else AnanasCard2,
                                    contentColor = if (theme == themeOption.lowercase()) Color.White else AnanasText
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text(themeOption, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("AD BLOCKING", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AnanasMuted, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AnanasBorder, RoundedCornerShape(14.dp)),
                color = AnanasCard,
            ) {
                Column {
                    var adBlockerEnabled by remember { mutableStateOf(AppSettings.adBlockerEnabled(context)) }
                    
                    SettingsToggleRow(
                        Icons.Rounded.Block, "Block Ads", "Blocks ad and tracker domains",
                        adBlockerEnabled, {
                            adBlockerEnabled = it
                            AppSettings.setAdBlockerEnabled(context, it)
                        }
                    )
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(AnanasCard2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Terminal, null, tint = AnanasText.copy(0.85f), modifier = Modifier.size(15.dp))
                    }
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
private fun SettingsRow(icon: ImageVector, label: String, value: String?, iconTint: Color, showChevron: Boolean, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, AnanasBorder, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it },
        color = AnanasCard,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Icon container with colored background
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = iconTint.copy(alpha = 0.15f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            icon, null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Label column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AnanasTextHi
                    )
                    if (value != null) {
                        Text(
                            value,
                            fontSize = 12.sp,
                            color = AnanasMuted,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
            
            if (showChevron) {
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = AnanasMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(icon: ImageVector, label: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, AnanasBorder, RoundedCornerShape(12.dp)),
        color = AnanasCard,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Icon container with accent color
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = AnanasAccent.copy(alpha = 0.15f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            icon, null,
                            tint = AnanasAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Label column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AnanasTextHi
                    )
                    Text(
                        desc,
                        fontSize = 12.sp,
                        color = AnanasMuted,
                        modifier = Modifier.padding(top = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Modern switch with better styling
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(1.15f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AnanasAccent,
                    uncheckedThumbColor = AnanasMuted.copy(alpha = 0.6f),
                    uncheckedTrackColor = AnanasCard2
                )
            )
        }
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
                AnanasIconButton(Icons.Rounded.ChevronLeft, onBack)
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
