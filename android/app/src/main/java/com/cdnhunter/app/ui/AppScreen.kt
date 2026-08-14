package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.MihomoBridge
import com.cdnhunter.app.vpn.AppSettings
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

/**
 * The ceiling on one pull-to-refresh ping sweep of Home's server list — the whole
 * sweep, not one probe.
 *
 * Every probe already carries its own 3s connect timeout and they all run concurrently,
 * so a sweep normally finishes in about that long however many servers are listed. This
 * only bites when the list is very long on a network slow enough that the probes queue,
 * and it exists so the pull-to-refresh indicator can never be left spinning: 12s is well
 * clear of a healthy sweep and still short enough that a user who pulled and got nothing
 * gets their list back rather than a stuck spinner.
 */
private const val PING_SWEEP_TIMEOUT_MS = 12_000L

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


// What counts as a country here: every ISO 3166-1 alpha-2 code the platform knows
// (249 of them on this project's JDK), plus the handful of CLDR codes below. No ISO
// code is left without an SVG — diffing `Locale.getISOCountries()` against the 265
// files in `assets/flags/` covers all 249 — so anything this set admits can be drawn.
//
// This replaced a hand-written 43-entry table of Canvas flag shapes that was left
// behind, after the real circle-flags SVGs took over the drawing, purely as the
// "is this a country?" gate. Being 43 codes wide, it was also silently *narrowing*
// detection: a server named "🇻🇳 Vietnam 01" decoded to VN, failed the gate, and
// showed the unknown-country globe even though vn.svg was sitting right there.
//
// Codes outside ISO 3166-1 that are not hypothetical: XK is what the geo providers
// return for Kosovo (see GeoService) and what the post-connect lookup will hand
// straight back to this gate, and 🇪🇺/🇺🇳 are flag emoji a provider can put in a
// server name. Each one is CLDR-named and asset-backed (xk.svg, eu.svg, … are among
// the 16 non-ISO files); the rest of those 16 (SU, YU, FX, XX, CQ) have no name on
// any platform, so admitting them would only produce a flag with no country beside
// it.
private val extraFlagCodes = setOf("XK", "EU", "UN", "AC", "TA", "IC", "EA", "DG", "CP")

private val knownCountryCodes: Set<String> =
    java.util.Locale.getISOCountries().toSet() + extraFlagCodes

// Spellings that mean a country in this set without being its code. "UK" is the one
// that matters: the platform has no name for it (getDisplayCountry hands "UK" back),
// so an exit IP reported as UK used to reach the connect bar as a flag with the raw
// config name beside it.
private val countryCodeAliases = mapOf("UK" to "GB")

/**
 * [raw] as a code this app can name and draw, or null when it is neither — the one
 * gate for a country code, whichever of the three sources it came from (a config's
 * title, a flag emoji, or a geo lookup through the live tunnel).
 */
internal fun canonicalCountryCode(raw: String): String? {
    val code = raw.trim().uppercase()
    if (code.length != 2) return null
    countryCodeAliases[code]?.let { return it }
    return code.takeIf { it in knownCountryCodes }
}

// Real circle-flags SVGs (github.com/HatScripts/circle-flags, MIT — same source
// Hiddify uses via its circle_flags package) bundled under assets/flags/{cc}.svg.
// Rendered through Coil's SVG decoder instead of hand-drawn Canvas shapes.
private var flagImageLoader: coil.ImageLoader? = null
// internal (not private): HomeScreen.kt's connect bar fills its pill with the
// same SVG flags, and top-level `private` in Kotlin is file-scoped.
//
// The loader owns a disk cache of its own, in its own directory, and that is what makes
// a fetched flag a one-time cost. Coil only writes to disk when it has been given a
// DiskCache; with none configured (which is what this was) every flagcdn SVG was
// re-fetched on the next cold start, so a country resolved by name paid the network
// again each launch and showed the bundled fallback until it landed. 32MB is far more
// than the ~90 countries in VPN_FLAG_COUNTRIES can fill as SVG source, so nothing this
// app fetches is ever evicted for space.
//
// respectCacheHeaders(false) because flagcdn serves its SVGs with a short max-age and
// the artwork behind a country code does not change: honouring the header would expire
// a perfectly good flag on a timer and re-fetch it. The cache key is the country (see
// the callers below), so a flag can never be served for the wrong one.
internal fun getFlagImageLoader(context: Context): coil.ImageLoader =
    flagImageLoader ?: coil.ImageLoader.Builder(context)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(java.io.File(context.cacheDir, "flag_cache"))
                .maxSizeBytes(32L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()
        .also { flagImageLoader = it }

/** The badge's cache key for [cc] and this artwork source — see [getFlagImageLoader]. */
private fun flagCacheKey(cc: String, remote: Boolean): String =
    if (remote) "flag-badge-cdn-$cc" else "flag-badge-asset-$cc"


@Composable
// internal (not private): HomeScreen.kt draws flags too, and top-level `private`
// in Kotlin is file-scoped, not package-scoped.
internal fun CountryFlagBadge(countryCode: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    // Through the same gate the name goes through, so the badge and the text beside
    // it can never disagree: a geo provider that reports "UK" draws gb.svg next to
    // "United Kingdom", and a code with no asset draws the globe instead of a
    // silently-empty box from a 404 on `flags/<junk>.svg`.
    val cc = canonicalCountryCode(countryCode)?.lowercase()
    val context = LocalContext.current
    if (cc == null) {
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
        // Well-known exit countries are fetched from flagcdn.com, which draws the
        // real flag's own details (the US canton's 50 stars, Turkey's crescent, the
        // crest on Spain's) that the bundled circle-flags asset simplifies away at
        // this size; everything else keeps drawing the bundled asset. A failed fetch
        // — offline, DNS blocked, flagcdn down — falls back to that same asset, so
        // the badge is never empty and never a spinner: see [remoteFlagUrl].
        val remote = remember(cc) { remoteFlagUrl(cc) }
        var remoteFailed by remember(cc) { mutableStateOf(false) }
        val model = if (remote != null && !remoteFailed) remote else "file:///android_asset/flags/$cc.svg"
        val key = remember(cc, remoteFailed) { flagCacheKey(cc, remote != null && !remoteFailed) }
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(model)
                // One size for every badge in the app, and one aspect ratio inside it.
                // Left to itself Coil rasterises to whatever box asked first, so the
                // 36dp connect-bar badge and the 30dp list badge could end up sharing
                // whichever of the two decoded first — visibly softer in one of them.
                // See [FLAG_BADGE_PX] and [FLAG_ASPECT].
                .size(FLAG_BADGE_PX)
                // Explicit keys, so one country's artwork is one cache entry across
                // every badge that draws it and the disk cache survives a restart with
                // a name that means something. The source is part of the key: remote and
                // bundled artwork for a country are different images.
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .crossfade(true)
                .build(),
            imageLoader = getFlagImageLoader(context),
            contentDescription = null,
            // Crop into a fixed 4:3 box rather than into whatever the flag's own ratio
            // is: every badge then crops the same way, so a 1.9:1 US flag and a square
            // Swiss one fill the circle identically instead of each losing a different
            // share of itself.
            contentScale = ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier
                // No clip of its own: a CircleShape clip on a 4:3 box is an ellipse.
                // The parent Box is already clipped to the circle, which is what crops
                // the overhanging sides of this box.
                .fillMaxHeight()
                // Unbounded, and this is load-bearing: `aspectRatio` only honours the
                // ratio when the size it computes fits the incoming constraints, and a
                // 4:3 box as tall as a square badge is a third wider than it. Bounded,
                // the modifier would silently fall back to the square — which is the
                // per-country shape drift this box exists to remove. Unbounded it keeps
                // the ratio and overhangs, and the parent's circular clip takes the
                // overhang off.
                .wrapContentWidth(unbounded = true)
                .aspectRatio(FLAG_ASPECT, matchHeightConstraintsFirst = true),
            // Assigning true when it is already true is not a state change, so the
            // asset's own failure can't loop this.
            onError = { remoteFailed = true },
            error = null,
        )
        // .server-flag's only decoration: `border: 1px solid rgba(255,255,255,0.06)`.
        // No glass sheen, no diagonal glare, no inner ring — the flag is the real
        // artwork and it reads as itself, flat, exactly as the mockup draws it.
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
        )
    }
}

// Short, list-friendly names for the codes whose platform display name is too long
// or too bureaucratic for the two places a country name is drawn. The connect bar's
// headline is the tighter of the two: on a 360dp phone the text has ~227dp
// (360 − 20 start − 63 end − 36 flag − 14 gap) at 21sp bold, which is a little
// under 20 characters before it ellipsizes. So every name over ~18 characters is
// shortened here, and a few shorter ones whose official form reads as officialese
// ("Myanmar (Burma)", "Congo - Kinshasa") are cut down too.
//
// Everything else falls through to the platform's own English name, so widening
// detection to every code in [knownCountryCodes] can't produce a flag with no
// country beside it: the old 32-entry table returned "" for VN, TW, MD and 200-odd
// others, and the bar then fell back to the raw config name for a server it had
// identified perfectly well.
//
// One consequence worth knowing: the browse search matches these names, so a
// shortened name is also the only spelling that finds that country by typing. That
// is the right trade for names no one would type in full, but it is why the cut
// keeps the distinctive word — "Svalbard", not "Norway's Arctic islands".
private val countryNameOverrides = mapOf(
    "AE" to "UAE",
    "BA" to "Bosnia",
    "BQ" to "Caribbean NL",
    "CC" to "Cocos Islands",
    "CD" to "DR Congo",
    "CF" to "Central Africa",
    "CG" to "Congo",
    "GS" to "South Georgia",
    "HK" to "Hong Kong",
    "HM" to "Heard & McDonald",
    "IO" to "Br. Indian Ocean",
    "MM" to "Myanmar",
    "MO" to "Macao",
    "MP" to "Northern Marianas",
    "PM" to "St. Pierre",
    "PS" to "Palestine",
    "SJ" to "Svalbard",
    "ST" to "São Tomé",
    "TC" to "Turks & Caicos",
    "TF" to "Fr. Southern Terr.",
    "UM" to "U.S. Outlying Is.",
    "VA" to "Vatican",
    "VC" to "St. Vincent",
    "VG" to "British Virgin Is.",
    "VI" to "U.S. Virgin Is.",
)

internal fun countryCodeToName(cc: String): String {
    val code = canonicalCountryCode(cc) ?: return ""
    countryNameOverrides[code]?.let { return it }
    // getDisplayCountry hands the code straight back when the platform has no name
    // for it; treat that as unknown rather than drawing "ZZ" as a country. Every
    // code in [extraFlagCodes] is named by CLDR, but an older Android's CLDR data
    // is not this desktop JDK's, so this stays a check rather than an assumption.
    val name = java.util.Locale("", code).getDisplayCountry(java.util.Locale.ENGLISH)
    return if (name.isBlank() || name == code) "" else name
}

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
    "INDONESIA" to "ID", "THAILAND" to "TH", "LUXEMBOURG" to "LU",
    "QATAR" to "QA", "EGYPT" to "EG", "MOROCCO" to "MA", "NEW ZEALAND" to "NZ",
)

// Two-letter codes never read as a country when they stand alone in a title. The
// bare-code fallback below is a guess from two characters, and once the gate went
// from 43 codes to all 249 it started reading ordinary words as countries: "Fast TV
// 01" became Tuvalu, "SS Tokyo" became South Sudan, "CF Worker DE" became the
// Central African Republic. Every code here is one the 43-entry table never
// accepted, so nothing that used to be detected stops being detected — the flags
// these give up are Ascension, Tuvalu, Cocos and the like, and none of them are
// plausibly what a provider means by putting "tv" or "ws" in a server's name.
//
// The full country name is always matched first, so any of these is still reachable
// by naming it in [countryNameToCode]; this only refuses the two-letter spelling.
private val neverBareCodes = setOf(
    "AC", "AD", "AI", "AS", "CC", "CF", "DO", "GG", "HM", "IO", "LA", "ME", "MS",
    "PA", "PE", "PM", "RE", "SH", "SO", "SS", "ST", "TC", "TM", "TO", "TV", "UM",
    "VI", "WS",
)

// Codes that are both a country worth detecting and an English word, so the bare
// spelling is only read when the title wrote it as a code: uppercase. "IS - Reykjavik"
// and "NO Oslo 01" resolve; "this is fast" and "Fast No 5" no longer turn into
// Iceland and Norway, which they did while any case was accepted. Lowercase codes
// stay readable for everything else ("nl 2 | vless" is still Netherlands) — it is
// only these nine where lowercase is more likely to be prose than a place.
private val capsOnlyBareCodes = setOf("AM", "AT", "BE", "BY", "IN", "IS", "IT", "MY", "NO")

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
            // Codes are compared and stored uppercase ("DE") everywhere else, so
            // hand the decoded pair to the same gate uppercase — lowercasing here
            // made every lookup miss and this function always return null.
            canonicalCountryCode("$c1$c2")?.let { return it }
        }
    }
    return null
}

private fun countryCodeFromTitle(title: String): String? {
    countryCodeFromFlagEmoji(title)?.let { return it }
    // Fold styled/unicode letterforms down to plain ASCII first so the country
    // name matches regardless of the font the title uses. NFKD applies Unicode
    // compatibility decompositions — "𝗚𝗲𝗿𝗺𝗮𝗻𝘆" (mathematical bold),
    // "Ｇｅｒｍａｎｙ" (fullwidth) and "Gérmany" (accented) all fold to a plain
    // "GERMANY" — and stripping the combining marks NFKD leaves behind removes
    // the accents. Without this the ASCII-only strip below turned any non-ASCII
    // styling into blanks and detection returned null.
    val folded = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{Mn}+"), "")
    // Letters and spaces only, but in the title's own case: the country names are
    // matched case-insensitively below, while the bare-code fallback needs to know
    // whether the title wrote "IS" or "is" (see [capsOnlyBareCodes]).
    val words = folded
        .replace(Regex("[^A-Za-z ]"), " ") // strip flag emoji, punctuation, digits
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    val normalized = words.joinToString(" ").uppercase()
    // Try longest names first so "SOUTH KOREA" matches before a stray "KOREA" would.
    for ((name, code) in countryNameToCode.entries.sortedByDescending { it.key.length }) {
        if (normalized.contains(name)) return code
    }
    // Also catch a standalone 2-letter code as its own word, e.g. "DE - Frankfurt 01",
    // minus the ones that are a word before they are a country: [neverBareCodes] is
    // refused outright, [capsOnlyBareCodes] only when the title wrote it in caps.
    for (word in words) {
        if (word.length != 2) continue
        val code = word.uppercase()
        if (code in neverBareCodes) continue
        if (code in capsOnlyBareCodes && word != code) continue
        canonicalCountryCode(code)?.let { return it }
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
    // The name is kept exactly as given, including any flag emoji the user put in it.
    // The remark is also read for a country, and this is the only place that happens:
    // it is the free instant guess a config shows the moment it is added, and it is
    // overwritten by the geo lookup through the live tunnel once connected (see
    // CdnVpnService's post-connect check). The lookup is the authority; the title is
    // what fills the badge until there is one.
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

internal fun formatElapsed(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/**
 * Label for the transport this device is on right now, for Home's network row.
 *
 * Deliberately not the Wi-Fi SSID: reading that needs a location permission on
 * Android 10+ and this app asks for none. The VPN transport itself is skipped so
 * the row keeps naming the underlying network while the tunnel is up (the active
 * network IS the VPN once it's running).
 */
private fun describeActiveNetwork(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val candidates = buildList {
            cm.activeNetwork?.let { add(it) }
            addAll(cm.allNetworks.toList())
        }
        for (network in candidates) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> return "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> return "Mobile data"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> return "Ethernet"
            }
        }
        "No network"
    } catch (e: Exception) {
        ""
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

/**
 * How long a connect request keeps the hero's connecting surface lit on its own,
 * before [CdnVpnService.isConnecting] is expected to have taken over.
 *
 * The gap it covers is the system VPN-permission dialog: `requestVpnPermissionAndConnect()`
 * returns immediately and the service is only started once the user answers, so
 * between the tap and that answer nothing in the service says an attempt is pending.
 * 12s is long enough for a user to read and accept the dialog and short enough that a
 * declined one falls back to idle on its own, without any callback from the activity.
 */
private const val CONNECT_REQUEST_GRACE_MS = 12_000L

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
    // An attempt is in flight. Read from the service rather than inferred from a tap,
    // so it survives Home being left and re-entered and covers the auto-reconnect
    // retries the UI never initiated (see CdnVpnService.isConnecting).
    var connecting by remember { mutableStateOf(CdnVpnService.isConnecting.get()) }
    // When the user last asked for a connection. The service's own flag is only set
    // once startVpn() runs, which is after the system VPN-permission dialog — so for
    // the seconds that dialog is up there is a real request in flight that the
    // service does not know about yet. This timestamp covers exactly that window
    // (see CONNECT_REQUEST_GRACE_MS); everything after it is the service's word.
    var connectRequestedAtMs by remember { mutableStateOf(0L) }
    var activeId   by remember {
        mutableStateOf(
            context.getSharedPreferences("cdnhunter_vpn", 0).getString("active_config_id", "") ?: ""
        )
    }
    // Smart / Manual (see ui/SmartMode.kt). Manual is what the app has always done
    // and stays the default; Smart hands the choice of server to the scoring below.
    var connectMode by remember {
        mutableStateOf(connectModeOf(AppSettings.connectMode(context)))
    }
    // The rolling window Smart mode scores. Fed from the ping monitor further down —
    // it takes no samples of its own, so Smart mode costs nothing while it is off.
    val quality = remember { ServerQualityTracker() }
    // Smart mode's current pick, published only when the chosen server changes.
    var smartPickId by remember { mutableStateOf("") }
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
    // Home's network row: which transport the device is on, and the public IP the
    // outside world sees for it (resolved through the tunnel while it's up).
    var networkName by remember { mutableStateOf(describeActiveNetwork(context)) }
    var publicIp by remember { mutableStateOf("") }

    // The country Home's hero panel washed last time the app ran, read once at launch.
    // It only covers the gap between `configs` arriving from disk and geo resolution
    // filling in the active config's country — see HomeUiState.heroFlagCountry, which is
    // the only thing that reads it, and the LaunchedEffect further down that writes it.
    var lastFlagCountry by remember { mutableStateOf(AppSettings.lastFlagCountry(context)) }

    // A pull-to-refresh ping sweep of Home's browse list is in flight. Drives the
    // PullToRefreshContainer's indicator; cleared by refreshPings() when the last
    // measurement lands or the whole sweep times out.
    var refreshingPings by remember { mutableStateOf(false) }

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
        // Windows for servers that no longer exist would otherwise keep a deleted
        // server eligible for Smart mode's pick.
        quality.retain(configs.map { it.id }.toSet())
        val toEnrich = configs.filter { !it.geoResolved && it.id !in enrichingIds }
        for (cfg in toEnrich) {
            enrichingIds += cfg.id
            try {
                val enriched = enrichConfigGeo(cfg)
                quality.record(cfg.id, enriched.pingMs)
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
            // Attempt-in-flight is the service's own flag, not "tapped and not up
            // yet": a retry the user never asked for has to light the same surface,
            // and a tap that the service rejects must not leave it lit.
            connecting = !vpnRunning && (
                CdnVpnService.isConnecting.get() ||
                    (
                        connectRequestedAtMs > 0L &&
                            System.currentTimeMillis() - connectRequestedAtMs < CONNECT_REQUEST_GRACE_MS
                        )
                )
            if (vpnRunning) connectRequestedAtMs = 0L

            if (connected) {
                if (connectedSinceMs == 0L) connectedSinceMs = System.currentTimeMillis()
                elapsedSec = (System.currentTimeMillis() - connectedSinceMs) / 1000

                val curDown = CdnVpnService.downloadBytes
                val curUp   = CdnVpnService.uploadBytes
                downloadKBps = (curDown - lastDown).coerceAtLeast(0L) / 1024.0
                uploadKBps   = (curUp - lastUp).coerceAtLeast(0L) / 1024.0
                totalDownloadBytes = curDown
                totalUploadBytes   = curUp
                lastDown = curDown; lastUp = curUp

                exitCountryCode = CdnVpnService.exitCountryCode
                exitCity = CdnVpnService.exitCity
                exitGeoConfigId = CdnVpnService.exitGeoConfigId
            } else {
                connectedSinceMs = 0L; elapsedSec = 0L; downloadKBps = 0.0; uploadKBps = 0.0
                totalDownloadBytes = 0L; totalUploadBytes = 0L
                lastDown = CdnVpnService.downloadBytes; lastUp = CdnVpnService.uploadBytes
                exitCountryCode = ""; exitCity = ""; exitGeoConfigId = ""
            }

            delay(1000)
        }
    }
    // Public IP for Home's network row. Re-resolved whenever the tunnel comes up or
    // goes down; the delay lets a fresh tunnel settle before the first request is
    // sent through it, and the lookup is proxied exactly when connected so what's
    // shown is the exit IP rather than this device's own (this app's process is
    // excluded from its own VPN).
    LaunchedEffect(connected) {
        networkName = describeActiveNetwork(context)
        publicIp = ""
        if (connected) delay(2500)
        val resolved = withContext(Dispatchers.IO) {
            try {
                com.cdnhunter.app.engine.GeoService().lookupCurrentIp(proxied = connected)
            } catch (e: Exception) {
                ""
            }
        }
        publicIp = resolved
    }

    // Keeps the network label honest when the phone moves between Wi-Fi and mobile
    // data while Home is open, instead of only refreshing on connect/disconnect.
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                networkName = describeActiveNetwork(context)
            }

            override fun onLost(network: android.net.Network) {
                networkName = describeActiveNetwork(context)
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                caps: android.net.NetworkCapabilities,
            ) {
                networkName = describeActiveNetwork(context)
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Some OEM builds throw here; the label just stays as first resolved.
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
            }
        }
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
                                // Every sample goes into the window Smart mode reads,
                                // including the failures: "answered 40ms, then timed
                                // out three times" is exactly the shape of server the
                                // score is meant to reject, and it is invisible if
                                // only successful probes are kept.
                                quality.record(cfg.id, newPing)
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

    // Smart mode's pick, refreshed on its own slow cadence.
    //
    // Deliberately not derived state: the ping monitor writes a sample per server
    // every 3 seconds, so with 50 saved servers a pick that recomputed on every
    // sample would recompose Home about seventeen times a second for a value that
    // barely moves. This reads the window every 4 seconds instead and only assigns
    // when the chosen server actually changed, so a stable pick costs no
    // recompositions at all.
    //
    // It runs only while the tunnel is DOWN. Once connected, the pick is the server
    // the tunnel is on: re-scoring a live connection could only produce a "better"
    // server the app has no business switching to behind the user's back, and it
    // would make the connect bar rename itself mid-session. Smart mode chooses at
    // connect time; it does not chase.
    LaunchedEffect(connectMode, connected) {
        if (connectMode != ConnectMode.SMART || connected) return@LaunchedEffect
        while (true) {
            val pick = pickBestConfig(configs, quality, preferId = smartPickId.ifBlank { activeId })
            val pickId = pick?.id ?: ""
            if (pickId != smartPickId) smartPickId = pickId
            delay(4000)
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
            CdnVpnService.stop(context); connected = false; connecting = false
            connectRequestedAtMs = 0L
        } else {
            if (connected) { CdnVpnService.stop(context); connected = false }
            // Light the connecting surface on the tap rather than waiting up to a
            // second for the poller to see the service's flag: the permission dialog
            // and mihomo's startup both land after this point, and a hero that stays
            // idle through them reads as a dead button. If the attempt never starts —
            // permission denied, no activity — the poller clears this on its next
            // pass, because it only ever mirrors the service.
            connecting = true
            connectRequestedAtMs = System.currentTimeMillis()
            activeId = cfg.id
            context.getSharedPreferences("cdnhunter_vpn", 0)
                .edit()
                .putString("user_config", cfg.uri)
                .putString("active_config_id", cfg.id)
                .apply()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val act = findActivity(context)
                if (act != null) {
                    act.requestVpnPermissionAndConnect()
                } else {
                    android.widget.Toast.makeText(context, "Couldn't start VPN — please reopen the app", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
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

    // Picking a server by hand IS the manual mode — leaving Smart on while showing
    // the user's own choice in the connect bar would mean the next connect quietly
    // went somewhere else. So the tap sets the server and the mode together.
    fun selectConfigManually(cfg: SavedConfig) {
        if (connectMode != ConnectMode.MANUAL) {
            connectMode = ConnectMode.MANUAL
            AppSettings.setConnectMode(context, AppSettings.MODE_MANUAL)
        }
        selectConfig(cfg)
    }

    // Home's swipe on the power circle. Persisted immediately, so the mode survives
    // the app being killed, and confirmed with the same haptic a connect gets —
    // the gesture has no press state of its own to acknowledge it.
    fun setConnectMode(mode: ConnectMode) {
        if (mode == connectMode) return
        connectMode = mode
        AppSettings.setConnectMode(
            context,
            if (mode == ConnectMode.SMART) AppSettings.MODE_SMART else AppSettings.MODE_MANUAL,
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Smart mode needs something in the bar right away rather than after the
        // next 4-second tick; the loop above takes over from here.
        if (mode == ConnectMode.SMART && !connected) {
            smartPickId = pickBestConfig(configs, quality, preferId = activeId)?.id ?: ""
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

    // The server the user last chose, and the one Smart mode currently rates best.
    val manualConfig = configs.find { it.id == activeId } ?: configs.firstOrNull()
    val smartConfig = configs.find { it.id == smartPickId }
    // What Home shows and the power button acts on. In Smart mode while the tunnel is
    // down that is the pick (falling back to the manual choice until enough has been
    // measured to have one); connected, it is always the server actually in use —
    // activeId is set to the smart pick at connect time, so the two agree.
    val activeConfig =
        if (connectMode == ConnectMode.SMART && !connected) (smartConfig ?: manualConfig)
        else manualConfig

    // Home's power button. Connected, it hangs up. Disconnected in Smart mode it
    // re-scores first, so the tunnel comes up on the best server as of the tap rather
    // than the best server as of the last tick.
    fun togglePower() {
        if (connected) {
            activeConfig?.let { connectConfig(it) }
            return
        }
        val target = if (connectMode == ConnectMode.SMART) {
            pickBestConfig(configs, quality, preferId = smartPickId.ifBlank { activeId })
                ?: activeConfig
        } else {
            activeConfig
        }
        target?.let { cfg ->
            if (connectMode == ConnectMode.SMART) smartPickId = cfg.id
            connectConfig(cfg)
        }
    }

    // The country Home's hero panel is washing right now: the exit node's once the
    // tunnel has reported it for this very config, else the config's own resolved geo.
    // Same precedence as HomeUiState.countryCodeFor, so what gets persisted is exactly
    // what was on screen.
    val heroCountry = activeConfig?.let { cfg ->
        if (connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()) exitCountryCode
        else cfg.countryCode
    }.orEmpty()

    // Persist that country so the next cold start opens on the flag it closed on
    // instead of on a bare panel. Written only when it actually changes, and dropped
    // the moment the last server is deleted — the cache would otherwise keep washing a
    // flag for a server the user no longer has, which is the one thing the EMPTY state
    // is supposed to rule out.
    LaunchedEffect(heroCountry, configs.isEmpty()) {
        if (configs.isEmpty()) {
            if (lastFlagCountry.isNotBlank()) {
                lastFlagCountry = ""
                AppSettings.setLastFlagCountry(context, "")
            }
        } else if (heroCountry.isNotBlank() && !heroCountry.equals(lastFlagCountry, ignoreCase = true)) {
            lastFlagCountry = heroCountry
            AppSettings.setLastFlagCountry(context, heroCountry)
        }
    }

    /**
     * Re-measure every server the browse list is currently showing, for Home's
     * pull-to-refresh.
     *
     * Each result is written into `configs` as it lands, so a row's ping badge updates
     * the moment its own probe answers rather than when the slowest one does — the
     * probes run concurrently for that reason. The active server is skipped while the
     * tunnel is up, for the same reason the background monitor skips it: this process is
     * excluded from its own VPN, so a direct dial of a fronted server's raw address
     * measures a path that is often blocked and would replace a good ping with -1.
     *
     * [PING_SWEEP_TIMEOUT_MS] is the whole sweep's ceiling, not one probe's. Every probe
     * already has its own 3s timeout, so the ceiling only matters for a very long list
     * on a slow network; it exists so the indicator can never be left spinning.
     * `refreshingPings` is cleared in a `finally`, which is also what covers this
     * composable leaving the tree mid-sweep, and it doubles as the re-entry guard: a
     * second pull while a sweep is still running is ignored rather than doubling up on
     * probes per server.
     */
    fun refreshPings(shown: List<SavedConfig>) {
        if (refreshingPings) return
        refreshingPings = true
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(PING_SWEEP_TIMEOUT_MS) {
                    // Fully qualified: `coroutineScope` is also the name of this
                    // composable's own rememberCoroutineScope value, and a val is not
                    // invokable — the suspending builder is what is wanted here, so that
                    // every probe below is a child of this sweep and the timeout reaches
                    // all of them.
                    kotlinx.coroutines.coroutineScope {
                        for (cfg in shown) {
                            if (cfg.id == activeId && connected) continue
                            launch {
                                val ping = withContext(Dispatchers.IO) {
                                    measurePingMs(cfg.address, cfg.port, timeoutMs = 3000)
                                }
                                quality.record(cfg.id, ping)
                                // Read from the current list rather than from `cfg`, which
                                // is a snapshot taken before the sweep started. Back on the
                                // main dispatcher by now, which is where Compose state is
                                // written from everywhere else in this function.
                                configs = configs.map {
                                    if (it.id == cfg.id) it.copy(pingMs = ping) else it
                                }
                            }
                        }
                    }
                }
                saveConfigs(context, configs)
            } finally {
                refreshingPings = false
            }
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
            // Home is a stateless tree in HomeScreen.kt: everything it draws arrives as
            // one HomeUiState snapshot and every tap leaves through a lambda, so this
            // function stays the only owner of connection state.
            AnanasScreen.HOME -> Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    state = HomeUiState(
                        activeConfig = activeConfig,
                        allConfigs = configs,
                        connected = connected,
                        connecting = connecting,
                        mode = connectMode,
                        elapsedSec = elapsedSec,
                        downloadKBps = downloadKBps,
                        uploadKBps = uploadKBps,
                        totalDownloadBytes = totalDownloadBytes,
                        totalUploadBytes = totalUploadBytes,
                        exitCountryCode = exitCountryCode,
                        exitCity = exitCity,
                        exitGeoConfigId = exitGeoConfigId,
                        networkName = networkName,
                        publicIp = publicIp,
                        lastFlagCountry = lastFlagCountry,
                        refreshingPings = refreshingPings,
                    ),
                    onOpenSettings = { navigateTo(AnanasScreen.SETTINGS) },
                    onOpenProfile = { navigateTo(AnanasScreen.PROFILE) },
                    onOpenLocations = { navigateTo(AnanasScreen.LOCATIONS) },
                    onTogglePower = { togglePower() },
                    onSelectConfig = { cfg -> selectConfigManually(cfg) },
                    onAddServer = { showAddMenu = true },
                    onSetMode = { mode -> setConnectMode(mode) },
                    onRefreshPings = { shown -> refreshPings(shown) },
                )
                // Same add-config sheet used on Locations, reused here so the new
                // "+" next to search on Home opens it directly without navigating
                // away to a separate screen.
                AddConfigSheet(
                    expanded = showAddMenu, onToggle = { showAddMenu = !showAddMenu },
                    onScanQr = ::startQrScan, onClipboard = ::addFromClipboard,
                )
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
                    onConnect = { selectConfigManually(it); navigateBack() },
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
            // as a muddy film sitting on top of the flag. A slim bar signals
            // "this one" clearly without discoloring the row's content.
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

    // systemBarsPadding, because the window no longer fits the decor to the system bars
    // (MainActivity's WindowCompat.setDecorFitsSystemWindows(false), which is what lets
    // Home's flag run behind the status bar). The background still fills the whole
    // window — it is applied before the padding — while this screen's own rows stay
    // clear of the clock at the top and the gesture bar at the foot.
    Box(Modifier.fillMaxSize().background(AnanasScreenBg).systemBarsPadding()) {
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

    // systemBarsPadding, because the window no longer fits the decor to the system bars
    // (MainActivity's WindowCompat.setDecorFitsSystemWindows(false), which is what lets
    // Home's flag run behind the status bar). The background still fills the whole
    // window — it is applied before the padding — while this screen's own rows stay
    // clear of the clock at the top and the gesture bar at the foot.
    Box(Modifier.fillMaxSize().background(AnanasScreenBg).systemBarsPadding()) {
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
    // systemBarsPadding, because the window no longer fits the decor to the system bars
    // (MainActivity's WindowCompat.setDecorFitsSystemWindows(false), which is what lets
    // Home's flag run behind the status bar). The background still fills the whole
    // window — it is applied before the padding — while this screen's own rows stay
    // clear of the clock at the top and the gesture bar at the foot.
    Box(Modifier.fillMaxSize().background(AnanasScreenBg).systemBarsPadding()) {
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

    // systemBarsPadding, because the window no longer fits the decor to the system bars
    // (MainActivity's WindowCompat.setDecorFitsSystemWindows(false), which is what lets
    // Home's flag run behind the status bar). The background still fills the whole
    // window — it is applied before the padding — while this screen's own rows stay
    // clear of the clock at the top and the gesture bar at the foot.
    Box(Modifier.fillMaxSize().background(AnanasScreenBg).systemBarsPadding()) {
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

// Home's own composables — power circle, connect bar, server list, usage card —
// all live in HomeScreen.kt.
