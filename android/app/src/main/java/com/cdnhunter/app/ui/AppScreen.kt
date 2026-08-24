package com.cdnhunter.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import com.cdnhunter.app.vpn.CdnVpnService
import com.cdnhunter.app.vpn.ConfigUriParser
import com.cdnhunter.app.vpn.MihomoBridge
import com.cdnhunter.app.vpn.AppSettings
import com.cdnhunter.app.vpn.SecurePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import androidx.core.graphics.drawable.toBitmap

// ── ANANAS Home/Connected reference palette ──────────────────────────────────
// The one palette. A separate dark/light pair (DarkBg/CardBg/AccentBlue…,
// LightBg/LightCardBg/LightBorder…) used to sit above this block from before the
// app settled on a single dark treatment; nothing read any of it, so it is gone
// rather than sitting here looking like a theme someone could switch to.
// Retargeted to the canonical flat "Windscribe-style" spec: near-black bg, hairline
// borders, single BLUE accent (no glass, no green). Symbol names are kept so the
// retint propagates through Settings/Profile/Locations/AccountCard/SheetScreen in place.
val AnanasBg       = Color(0xFF060709)   // App background
val AnanasScreenBg = Color(0xFF0A0B0F)   // Slightly lighter page wash
val AnanasCard     = Color(0xFF131316)   // Card surface
val AnanasCard2    = Color(0xFF1A1B22)   // Raised element
val AnanasBorder   = Color(0xFF23262F)   // Hairline border
val AnanasBorder2  = Color(0xFF2A2E38)   // Alternative (raised) border
val AnanasDivider  = Color(0xFF1C1F27)   // Divider
val AnanasAccent   = Color(0xFF4D7FFF)   // Blue accent
val AnanasAccentLight = Color(0xFF6E97FF)
val AnanasSettingsIcon = Color(0xFF9BA0AC)   // text-mid, soft gray for settings row icons
val AnanasAmber    = Color(0xFFE0B23B)   // Warm amber (premium/warn)
val AnanasRed      = Color(0xFFEF4444)   // Error red
val AnanasBlue     = Color(0xFF4D7FFF)   // Unified to the blue accent
val AnanasPurple   = Color(0xFF4D7FFF)   // Unified to the blue accent (no off-palette purple)
// The "on / active / selected" indicator color for controls (MinimalToggle track,
// SegmentedControl selected segment, SelectDot). Deliberately NOT the green accent:
// a green "on" state on a VPN app reads as a status/connection light, which these
// controls are not. Blue keeps the active state legible and on-theme without that
// false connotation.
val AnanasToggleOn = AnanasBlue
val AnanasTextHi   = Color(0xFFF6F7F9)   // Primary text
val AnanasText     = Color(0xFFE3E6EC)   // Secondary text
val AnanasMuted    = Color(0xFF9BA0AC)   // Muted / caption text (text-mid)
val AnanasFaint    = Color(0xFF656B78)   // Faint text (text-low)

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
        val ping = measurePingMs(cfg.address, cfg.port)
        // Best-effort on-device geo so a server's row and the header plate can show a
        // country AND city before that server has ever been connected. This resolves
        // the config's own host directly, so for a CDN-fronted address it reports the
        // edge node's location rather than the true backend — it is a GUESS, and the
        // accurate through-the-tunnel check (probeAccurateGeoViaLiveTunnel) overwrites
        // both fields once the server is actually connected. Only still-blank fields
        // are filled so a prior accurate result is never clobbered, and a failed
        // lookup just leaves them as they were.
        val geo = try {
            com.cdnhunter.app.engine.GeoService().lookupGeoInfo(cfg.address)
        } catch (e: Exception) {
            null
        }
        cfg.copy(
            pingMs = ping,
            countryCode = if (cfg.countryCode.isBlank() && geo != null) geo.cc else cfg.countryCode,
            city = if (cfg.city.isBlank() && geo != null) geo.city else cfg.city,
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
                // One size for every badge in the app. Left to itself Coil rasterises to
                // whatever box asked first, so the 36dp connect-bar badge and the 30dp
                // list badge could end up sharing whichever of the two decoded first —
                // visibly softer in one of them. See [FLAG_BADGE_PX].
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
            // Crop straight into the badge's own square box: scale uniformly until the
            // circle is covered, lose the overhang. The flag keeps its own proportions,
            // whatever they are (a 1.9:1 US flagcdn SVG, a square bundled asset), which
            // is the point — a 4:3 box in between made every badge crop the same way at
            // the cost of stretching most sources to get there, and a warped flag reads
            // as wrong long before an inconsistent crop does.
            contentScale = ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            // No clip of its own: the parent Box is already clipped to the circle, which
            // is what takes off whatever the crop leaves hanging over the sides.
            modifier = Modifier.fillMaxSize(),
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
    val prefs = SecurePrefs.vpn(context)
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
    SecurePrefs.vpn(context)
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
fun AppScreen(onSignOut: () -> Unit = {}) {
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
            VpnTab(onSignOut = onSignOut) // full-bleed root screen; owns internal navigation (Home/Locations/My Configs/Settings/Profile)
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

/**
 * The public-IP lookup's attempt ladder: one entry per attempt, each the wait *after* that
 * attempt fails. Three attempts over ~6s, then the hero offers a manual retry.
 *
 * Bounded deliberately. Every entry is a round trip to a third-party address service, and on the
 * networks this app exists for those are exactly the hosts most likely to be unreachable — an
 * unbounded poll would spend the radio on a request that is not going to succeed. The last
 * entry's value is never waited on.
 */
private val IP_LOOKUP_BACKOFF_MS = longArrayOf(1_200L, 2_500L, 0L)

// After the fast ladder above gives up, keep trying quietly at this slower cadence
// so a tunnel whose proxy was still warming up (or a briefly flaky path) fills the
// address in on its own instead of forcing a manual retry tap. Bounded so it can't
// poll forever on a genuinely blocked network.
private const val IP_LOOKUP_SLOW_RETRIES = 5
private const val IP_LOOKUP_SLOW_INTERVAL_MS = 8_000L

// ── VPN TAB (Home / Connected — ANANAS reference) ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VpnTab(onSignOut: () -> Unit) {
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
            SecurePrefs.vpn(context).getString("active_config_id", "") ?: ""
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

    // Single source of truth for account/subscription display data (see AppUiState.kt).
    // Settings' AccountCard and ProfileScreen both read this one value rather than each
    // hard-coding their own copy of the name/email/plan.
    val account = remember { currentAccountUiState() }
    
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
    var userIp by remember { mutableStateOf(AppSettings.lastPublicIp(context)) }
    var publicIp by remember { mutableStateOf(userIp) }
    // A lookup is in flight. Home distinguishes "still asking" from "every provider failed",
    // because the second one is a tap target that retries and the first one must not be.
    var ipLookupPending by remember { mutableStateOf(true) }
    // Bumped to ask again after the automatic attempts have all failed. Part of the
    // LaunchedEffect's key, so a bump restarts the whole attempt ladder.
    var ipRetryTick by remember { mutableStateOf(0) }

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
    // Public IP for Home's hero. Re-resolved whenever the tunnel comes up or goes down, and
    // whenever Home asks again (ipRetryTick). The lookup is proxied exactly when connected so
    // what's shown is the exit IP rather than this device's own — this app's process is excluded
    // from its own VPN, so an unproxied lookup while connected reports the ISP's address.
    //
    // Three attempts, not one. GeoService.lookupCurrentIp returns "" when all of its providers
    // fail, which on a censored or captive network is a normal outcome rather than an error, and
    // a single attempt keyed only on `connected` left the hero with a permanently blank address
    // for the rest of the session. The delays let a fresh tunnel settle and then give a flaky
    // path two more chances; after that the address line becomes a retry target (see
    // HomeUiState.ipLookupPending and MetaRow).
    LaunchedEffect(connected, ipRetryTick) {
        networkName = describeActiveNetwork(context)
        // Only drop to the neutral "-" placeholder when there is no address already on
        // screen. If a valid IP is already showing (from before this connect/disconnect),
        // keep it there while the new one resolves in the background -- RollingIp then
        // animates straight from the old value to the new one instead of the readout
        // blanking out and refilling, which is what made every transition look like an
        // instant snap rather than a roll.
        if (publicIp.isBlank()) {
            ipLookupPending = true
        }
        try {
            if (connected) delay(2500)
            for ((attempt, backoffMs) in IP_LOOKUP_BACKOFF_MS.withIndex()) {
                val resolved = withContext(Dispatchers.IO) {
                    try {
                        com.cdnhunter.app.engine.GeoService().lookupCurrentIp(proxied = connected)
                    } catch (e: Exception) {
                        ""
                    }
                }
                if (resolved.isNotBlank()) {
                    publicIp = resolved
                    if (!connected) {
                        userIp = resolved
                        AppSettings.setLastPublicIp(context, resolved)
                    }
                    return@LaunchedEffect
                }
                if (attempt < IP_LOOKUP_BACKOFF_MS.lastIndex) delay(backoffMs)
            }
        } finally {
            // Also runs when the effect is cancelled by a connect/disconnect or a retry, so the
            // hero never keeps saying "Checking…" for a lookup that no longer exists.
            ipLookupPending = false
        }
        // The fast ladder above is exhausted and nothing resolved — but on a freshly
        // established tunnel the mixed-port proxy can still be warming up, and on a
        // flaky path the providers may just need another moment. Rather than leaving
        // the address blank until the user manually taps retry, keep trying quietly
        // in the background at a slower cadence. ipLookupPending stays false so the
        // retry affordance is still shown, but in practice the IP now fills itself in.
        // This loop is cancelled automatically the moment connect state flips or the
        // user taps retry (both re-key this effect).
        for (i in 0 until IP_LOOKUP_SLOW_RETRIES) {
            delay(IP_LOOKUP_SLOW_INTERVAL_MS)
            val resolved = withContext(Dispatchers.IO) {
                try {
                    com.cdnhunter.app.engine.GeoService().lookupCurrentIp(proxied = connected)
                } catch (e: Exception) {
                    ""
                }
            }
            if (resolved.isNotBlank()) {
                publicIp = resolved
                if (!connected) {
                    userIp = resolved
                    AppSettings.setLastPublicIp(context, resolved)
                }
                return@LaunchedEffect
            }
        }
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
            SecurePrefs.vpn(context)
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
        SecurePrefs.vpn(context)
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
                        ipLookupPending = ipLookupPending,
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
                    onRetryIp = { ipRetryTick++ },
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
                onBack = { navigateBack() },
                mode = connectMode,
                onSetMode = { m -> setConnectMode(m) },
                account = account,
            )

            AnanasScreen.PROFILE -> ProfileScreen(onBack = { navigateBack() }, account = account, onSignOut = onSignOut)
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
            containerColor = AnanasCard,
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
    val shape = remember { RoundedCornerShape(14.dp) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .sheetRaised(
                shape = shape,
                pressed = pressed,
                fill = if (highlight) SheetAccentFill else SheetControlFill,
                pressedFill = if (highlight) SheetAccentPressedFill else SheetControlPressedFill,
                elevation = if (highlight) 10.dp else 6.dp,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(if (highlight) Color.White.copy(0.18f) else AnanasCard),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (highlight) Color.White else AnanasTextHi, modifier = Modifier.size(19.dp)) }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (highlight) Color.White else AnanasTextHi)
            Text(subtitle, fontSize = 11.5.sp, color = if (highlight) Color.White.copy(0.7f) else AnanasMuted, modifier = Modifier.padding(top = 1.dp))
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
        pingMs < 150 -> 3 to AnanasAccent
        pingMs < 350 -> 2 to AnanasAmber
        else         -> 1 to AnanasRed
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
                .sheetSurface(RoundedCornerShape(22.dp), elevation = 24.dp)
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
            val copyInteraction = remember { MutableInteractionSource() }
            val copyPressed by copyInteraction.collectIsPressedAsState()
            Row(
                Modifier.fillMaxWidth()
                    .sheetRaised(RoundedCornerShape(14.dp), copyPressed)
                    .clickable(interactionSource = copyInteraction, indication = null) {
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
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
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

            // Minimal search field — no card and no box, just the glyph and the line of type.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Search, null, tint = AnanasFaint, modifier = Modifier.size(16.dp))
                // Box(CenterStart) + SheetSearchStyle is the caret fix: the field's own line box
                // is taller than its glyphs, so without both the caret sat high of the text.
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search", style = SheetSearchStyle, color = AnanasFaint, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = SheetSearchStyle,
                        cursorBrush = SolidColor(AnanasAccent),
                    )
                }
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
            shape = RoundedCornerShape(20.dp),
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

// ── Sheet design system ───────────────────────────────────────────────────────
// The material the secondary screens share — Settings, Profile, split tunneling. Home has its
// own language because it draws over a flag; these screens are a flat near-black page, so the
// grid carries them: one gutter, one card corner, one row height, one tinted icon tile.

/** Page gutter (spec: screen horizontal padding 16.dp). */
private val SheetPad = 16.dp

/** Card corner (spec: card/row 14.dp). */
private val SheetCardCorner = 14.dp

/** Row floor: fits a two-line row, and keeps one-line rows in a group the same height. */
private val SheetRowHeight = 58.dp

// ── Depth, in place of borders ────────────────────────────────────────────────
// These surfaces used to be drawn with a 1dp hairline each; nine outlines on one scroll is nine
// competing rectangles, and on a page this dark the line is the loudest thing in frame. What
// replaces them is Home's light model — gradient fill, lit top edge, shaded foot, real shadow.

/** Pure black: the page is near-black already, so a tinted shadow only muddies it. */
private val SheetShadow = Color(0xFF000000)

/** Depth of the specular band and the foot shade — absolute dp, so a 58dp row and a 300dp
 *  card are lit by the same size of highlight. */
private val SheetCrownDepth = 9.dp

/** The band of light along a surface's top edge. Flattened out per the spec (no glass). */
private val SheetCrown = Brush.verticalGradient(
    0.00f to Color.Transparent,
    1.00f to Color.Transparent,
)

/** The shade at a surface's foot. Flattened out per the spec (no glass). */
private val SheetFoot = Brush.verticalGradient(
    0.00f to Color.Transparent,
    1.00f to Color.Transparent,
)

/** A card's material: flat [AnanasCard] per the spec CARD recipe. */
private val SheetCardFill = Brush.verticalGradient(listOf(AnanasCard, AnanasCard))

/** A control's material — flat raised surface ([AnanasCard2]). */
private val SheetControlFill = Brush.verticalGradient(
    listOf(AnanasCard2, AnanasCard2),
)

private val SheetControlPressedFill = Brush.verticalGradient(
    listOf(AnanasCard, AnanasCard),
)

/** Shallower than Home's 0.955: a big scale on a 34dp target reads as a glitch. */
private const val SHEET_PRESS_SCALE = 0.965f

/**
 * A raised, borderless surface: drop shadow, gradient fill, lit top edge, shaded foot.
 *
 * The bands are drawn in [Modifier.drawBehind] *after* the fill, so they paint over the
 * background and under the content.
 */
private fun Modifier.sheetSurface(
    shape: Shape,
    fill: Brush = SheetCardFill,
    elevation: Dp = 10.dp,
): Modifier = this
    // No drop shadow. An opaque-black elevation shadow is invisible on this near-black page
    // (black on black), so it added no depth where cards actually sit — but on the brighter
    // surfaces at the top of every sheet (the glass header, the page wash, a glass tile) that
    // same shadow rendered as a hard rounded halo *behind* the card, reading as a stray ghost
    // box. The visible depth here is the light model below: gradient fill, lit top edge
    // ([SheetCrown]), shaded foot ([SheetFoot]) — [elevation] is retained only so callers that
    // pass it stay source-compatible.
    .clip(shape)
    .background(fill)
    // Hairline border per the spec CARD recipe — the flat surfaces read by their edge now
    // that the glass light model (crown/foot) is gone.
    .border(1.dp, AnanasBorder, shape)
    .drawBehind {
        val band = SheetCrownDepth.toPx().coerceAtMost(size.height / 2f)
        drawRect(brush = SheetCrown, size = Size(size.width, band))
        drawRect(
            brush = SheetFoot,
            topLeft = Offset(0f, size.height - band),
            size = Size(size.width, band),
        )
    }

/** [Modifier.sheetSurface] for something pressed: it shrinks and loses most of its shadow,
 *  which is what sells the height. */
private fun Modifier.sheetRaised(
    shape: Shape,
    pressed: Boolean,
    fill: Brush = SheetControlFill,
    pressedFill: Brush = SheetControlPressedFill,
    elevation: Dp = 6.dp,
): Modifier = this
    .scale(if (pressed) SHEET_PRESS_SCALE else 1f)
    .sheetSurface(
        shape = shape,
        fill = if (pressed) pressedFill else fill,
        elevation = if (pressed) elevation / 3 else elevation,
    )

/** A well's material — flat, darker than the card it is cut into. */
private val SheetWellFill = Brush.verticalGradient(
    listOf(Color(0xFF0D0E12), Color(0xFF0D0E12)),
)

/** The shade cast *into* a well by its own top edge, which is what makes it read as a hole. */
private val SheetWellShade = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.34f),
    0.45f to Color.Black.copy(alpha = 0.10f),
    1.00f to Color.Transparent,
)

/** How thick a field's focus/error rule is. 1.5dp: the only edge a field ever draws. */
private val SheetFieldRuleDepth = 1.5.dp

/** The accent rule down the leading edge of an informational note. */
private val SheetNoteRuleWidth = 3.dp

/** The accent tint over a lit segment in a [SegmentedControl]. */
private val SheetSegmentTint = Brush.verticalGradient(
    listOf(AnanasAccent.copy(alpha = 0.20f), AnanasAccent.copy(alpha = 0.11f)),
)

/** An accent [PillButton]'s material — flat accent fill, pressed a shade darker. */
private val SheetAccentFill = Brush.verticalGradient(listOf(AnanasAccent, AnanasAccent))
private val SheetAccentPressedFill = Brush.verticalGradient(listOf(Color(0xFF3D6BE6), Color(0xFF3D6BE6)))

/** A tile riding inside [SheetScreen]'s header — flat raised surface. */
private val SheetGlassTileFill = Brush.verticalGradient(
    listOf(AnanasCard2, AnanasCard2),
)

/** The avatar's ring, and the disc it sits behind — see [AvatarRing]. The ring is brightest
 *  at the top left, so the avatar catches the same light as everything else. */
private val SheetAvatarRing = Brush.linearGradient(
    listOf(AnanasAccent.copy(alpha = 0.55f), AnanasAccent.copy(alpha = 0.10f)),
)
private val SheetAvatarWell = Brush.verticalGradient(
    listOf(Color(0xFF14151B), Color(0xFF14151B)),
)

/** Profile's plan card: the one warm surface in the app, flat per the spec. */
private val SheetPlanFill = Brush.verticalGradient(
    listOf(Color(0xFF1B1712), Color(0xFF1B1712)),
)

/** [MinimalToggle]'s thumb, lit from above so it reads as a bead in a groove, not a flat dot. */
private val SheetThumbFill = Brush.verticalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFDDE0E6)),
)

/**
 * Every text input on these screens shares this, and the reason is the caret.
 *
 * A `BasicTextField` lays its line out inside the font's own ascent/descent, which on Roboto is
 * taller than the glyphs, and draws the caret to that taller box — so in a top-aligning container
 * the text sits low and the caret high. All three of these are needed to fix it:
 * `includeFontPadding = false`, an explicit `lineHeight` with [LineHeightStyle.Alignment.Center],
 * and `Trim.None` (the default trims exactly the space being relied on). The container centres it
 * — see [InlineField] and the Locations search field.
 *
 * `tnum` because these fields hold addresses and MTUs: figures should not shuffle sideways as an
 * octet is typed.
 */
private val SheetFieldStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 19.sp,
    fontWeight = FontWeight.Medium,
    color = AnanasTextHi,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** [SheetFieldStyle] for a search field: text rather than figures, so no `tnum`, and a shade
 *  smaller since it sits next to a 16dp glyph rather than in a box of its own. */
private val SheetSearchStyle = TextStyle(
    fontSize = 13.5.sp,
    lineHeight = 18.sp,
    color = AnanasText,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Rounded at the bottom only — see [Modifier.sheetHeaderPanel]. */
private val SheetHeaderShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)

/** The header sits flat on the app background per the spec HEADER recipe (no glass). */
private val SheetHeaderGlass = Brush.verticalGradient(
    listOf(AnanasScreenBg, AnanasScreenBg),
)

/** No accent wash over the header any more — flat. */
private val SheetHeaderTint = Brush.verticalGradient(
    0.00f to Color.Transparent,
    1.00f to Color.Transparent,
)

/** The lit bottom rim — the glass's thickness where it ends, and the one edge on the panel
 *  that is meant to be seen. Drawn inside the clip, so it follows the corner curve. */
private val SheetHeaderRim = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.62f to Color.White.copy(alpha = 0.01f),
    1.00f to Color.White.copy(alpha = 0.04f),
)

/** How deep that rim runs. */
private val SheetHeaderRimDepth = 6.dp

/** How much air is left between the panel's last row and its bottom edge. */
private val SheetHeaderFootRoom = 22.dp

/** How far the panel's shadow reaches on to the page below it. */
private val SheetHeaderLift = 6.dp

/**
 * The glass panel every secondary screen opens with: full-bleed, rounded only at the bottom, its
 * top running off the screen behind the status bar — so the one edge the eye can read is the lit
 * curve below the content, and the panel reads as a sheet sliding down from above the device.
 *
 * The status-bar inset is applied *inside* the fill, which is what lets the glass reach under the
 * clock while its content stays clear of it. Shared by [SheetScreen] and [SplitTunnelScreen].
 */
private fun Modifier.sheetHeaderPanel(): Modifier = this
    .fillMaxWidth()
    // No drop shadow: on the page below, the opaque-black shadow of the panel's rounded bottom
    // edge rendered as a stray rounded outline hovering just above the first content card. The
    // panel already ends in a lit rim ([SheetHeaderRim]) drawn inside the clip — that curved
    // highlight is the one edge meant to be seen, and it reads as depth without a ghost band.
    .clip(SheetHeaderShape)
    .background(SheetHeaderGlass)
    .background(SheetHeaderTint)
    .drawBehind {
        val rim = SheetHeaderRimDepth.toPx()
        drawRect(
            brush = SheetHeaderRim,
            topLeft = Offset(0f, size.height - rim),
            size = Size(size.width, rim),
        )
    }
    .statusBarsPadding()
    .padding(horizontal = SheetPad)

/**
 * The frame every secondary screen sits in: the page wash, the glass header panel, and a scroll.
 *
 * The title is 30sp ExtraBold on its own line rather than 16sp beside the chevron — the same
 * move the hero makes with its country name. The screen states what it is once, in the largest
 * ink on the page, and everything under it can then be quiet.
 *
 * [headerContent] is whatever else belongs *inside* the glass: Settings puts its account row
 * there, Profile its avatar block. Anything passed here shares the panel with the title instead
 * of becoming another card on the page — see [sheetHeaderPanel].
 */
@Composable
private fun SheetScreen(
    title: String,
    onBack: () -> Unit,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        // (The old full-width SheetPageWash box that used to sit here is gone: being a
        // square-cornered rectangle behind the round-bottomed header, its corners poked
        // out below the header curve and read as a faint ghost band above the first card.
        // The header panel already carries its own top light via SheetHeaderGlass +
        // SheetHeaderRim inside its clip, so nothing is lost by dropping the stray box.)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.sheetHeaderPanel()) {
                // Title on the SAME row as the back chevron. The chevron is a plain icon
                // (no disc/border) so the two read as one line: "‹ Settings".
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (headerContent == null) 4.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainBackButton(onClick = onBack)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        color = AnanasTextHi,
                    )
                }
                headerContent?.invoke(this)
                Spacer(Modifier.height(SheetHeaderFootRoom))
            }
            Column(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = SheetPad),
            ) {
                content()
                Spacer(Modifier.height(44.dp))
            }
        }
    }
}

/**
 * A group heading: "CONNECTION", "NETWORK", "DNS". 11sp Bold at 1.6sp of tracking — the same
 * ratio Home's phase eyebrow uses. The top space belongs to the composable, not to the call
 * site, so groups on this page are always separated by the same amount.
 */
@Composable
private fun SectionLabel(text: String, top: Dp = 26.dp) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = AnanasMuted,
        modifier = Modifier.padding(top = top, bottom = 10.dp, start = 4.dp),
    )
}

/** The card a group of rows sits in — clipped, so the rows' own ripples stop at the corner.
 *  Borderless; what separates it from the page is [Modifier.sheetSurface]. */
@Composable
private fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SheetCardCorner)
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(AnanasCard)
            .border(1.dp, AnanasBorder, shape),
        content = content,
    )
}

/** The line between two rows in a [CardGroup], inset to where the row's text begins so the
 *  icons read as one column. 62dp = [SheetPad] shy of the card edge, plus the tile and its gap. */
@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).height(1.dp).background(AnanasBorder))
}

/** A row's icon: the glyph on its own tinted rounded square rather than loose on the card, which
 *  is what makes the icon column unmistakable and matches Profile's menu. */
@Composable
private fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(34.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp)) }
}

/**
 * A text input, built out of [BasicTextField] rather than Material's `TextField` — the stock
 * field's floating label, 56dp floor, indicator line and container colour are four decisions
 * this page has already made differently.
 *
 * *Recessed* rather than raised: the light bands run the other way round ([SheetWellFill], shade
 * at the top), so a field reads as a hole in the card while a button reads as an object on it.
 * The only edge it draws is the focus/error underline — a state indicator, not a box outline.
 *
 * Presentation only: [onValueChange] fires on every keystroke as before, validation stays at the
 * call site.
 */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = remember { RoundedCornerShape(12.dp) }
    val underline by animateColorAsState(
        targetValue = when {
            error != null -> AnanasRed
            focused -> AnanasAccent
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "fieldEdge",
    )
    Column(modifier.fillMaxWidth()) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = AnanasMuted)
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(SheetWellFill)
                .drawBehind {
                    val band = SheetCrownDepth.toPx().coerceAtMost(size.height / 2f)
                    drawRect(brush = SheetWellShade, size = Size(size.width, band))
                    val rule = SheetFieldRuleDepth.toPx()
                    drawRect(
                        color = underline,
                        topLeft = Offset(0f, size.height - rule),
                        size = Size(size.width, rule),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            // Centres the caret, not just the text: see [SheetFieldStyle].
            contentAlignment = Alignment.CenterStart,
        ) {
            // Under the field, so the caret and the typed value draw over it. Same metrics as the
            // field, or the placeholder sits on a different baseline to the value replacing it.
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = SheetFieldStyle,
                    color = AnanasFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = SheetFieldStyle,
                cursorBrush = SolidColor(AnanasAccent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(5.dp))
            Text(error, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = AnanasRed)
        }
    }
}

/**
 * A two-or-three way choice as one control: a tracked capsule with the selected segment lit.
 *
 * `options` is (stored value, shown label) — the key is what goes to [AppSettings] — so the
 * control cannot drift from the persisted value.
 *
 * The track is a well and the selected segment a raised tile inside it: now that the outline is
 * gone, the choice that is on is the one standing out of the groove.
 */
@Composable
private fun SegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Off by default so the MTU row's control stays as wide as its two short words.
    // On, each segment takes an equal share of the row — for a full-width control
    // whose labels are sentences rather than words.
    equalWeight: Boolean = false,
) {
    val trackShape = remember { RoundedCornerShape(12.dp) }
    val segShape = remember { RoundedCornerShape(10.dp) }
    Row(
        modifier
            .clip(trackShape)
            .background(Color(0xFF0F1116))
            .border(1.dp, AnanasBorder, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            val interaction = remember { MutableInteractionSource() }
            val ink by animateColorAsState(
                targetValue = if (on) AnanasToggleOn else AnanasMuted,
                animationSpec = tween(160), label = "segInk",
            )
            Box(
                (if (equalWeight) Modifier.weight(1f) else Modifier)
                    .clip(segShape)
                    .background(if (on) AnanasToggleOn.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = ink,
                )
            }
        }
    }
}

/**
 * A small text action inside a row — "Copy", "Clear". `accent = true` fills it and inverts the
 * ink, for the one action that is the point of the row. Both are raised objects that sink on
 * press ([sheetRaised]); the lift replaces the outline the neutral one used to wear.
 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, accent: Boolean = false) {
    val shape = remember { RoundedCornerShape(10.dp) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .sheetRaised(
                shape = shape,
                pressed = pressed,
                fill = if (accent) SheetAccentFill else SheetControlFill,
                pressedFill = if (accent) SheetAccentPressedFill else SheetControlPressedFill,
                elevation = if (accent) 8.dp else 5.dp,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) Color.White else AnanasText,
        )
    }
}

/**
 * The account block at the top of Settings: avatar, name, plan, chevron. It rides inside
 * [SheetScreen]'s glass header rather than sitting on the page as a card of its own, and is
 * separated by a lift rather than an outline.
 */
@Composable
private fun AccountCard(account: AccountUiState, onClick: () -> Unit) {
    val shape = remember { RoundedCornerShape(SheetCardCorner) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth()
            .sheetRaised(shape, pressed, fill = SheetGlassTileFill, elevation = 8.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarRing(size = 46.dp, initials = account.initials, initialsSize = 15.sp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(account.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = AnanasTextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(AnanasAmber.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AnanasAmber, letterSpacing = 0.6.sp)
                }
                Text(account.expiresLabel, fontSize = 11.5.sp, color = AnanasMuted)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(18.dp))
    }
}

/**
 * The avatar, at whatever size is asked for: a gradient ring with a dark disc and the initials
 * inside it. One composable so Settings' 46dp version and Profile's larger one cannot drift.
 */
@Composable
private fun AvatarRing(size: Dp, initials: String, initialsSize: TextUnit, ringWidth: Dp = 1.5.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(SheetAvatarRing).padding(ringWidth),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(SheetAvatarWell),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, fontSize = initialsSize, fontWeight = FontWeight.Bold, color = AnanasAccent)
        }
    }
}

// ── Settings — ANANAS reference (replaces old Tools/ScannerTab entirely) ───────

/**
 * Which server the power button acts on — [ConnectMode.SMART] or [ConnectMode.MANUAL] — as a row
 * in the CONNECTION group, so the mode has a visible entry point rather than only the gesture.
 *
 * The write goes out through [onSetMode], which is VpnTab's own `setConnectMode`: the same path
 * Home's swipe takes, persisted to [AppSettings] and taking effect on the next connect. Nothing
 * about the mode's behaviour changes here.
 */
@Composable
private fun ModeChoiceRow(mode: ConnectMode, onSetMode: (ConnectMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SheetRowHeight)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AutoAwesome, AnanasAccent)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Server choice",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = AnanasTextHi,
                )
                Text(
                    when (mode) {
                        ConnectMode.SMART -> "Best-measuring server, picked for you"
                        ConnectMode.MANUAL -> "The one you tap in the list"
                    },
                    fontSize = 11.5.sp,
                    color = AnanasMuted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
            }
        }
        SegmentedControl(
            options = ConnectMode.values().map { it.name to it.label },
            selected = mode.name,
            onSelect = { key -> onSetMode(ConnectMode.valueOf(key)) },
        )
    }
}

@Composable
private fun SettingsScreen(
    onProfileClick: () -> Unit = {},
    onSplitTunnelClick: () -> Unit = {},
    onBack: () -> Unit = {},
    // The live value and setter from VpnTab, which owns both — so this control drives exactly
    // the same state Home's gesture does, with no second copy to keep in sync.
    mode: ConnectMode = ConnectMode.MANUAL,
    onSetMode: (ConnectMode) -> Unit = {},
    account: AccountUiState,
) {
    val context = LocalContext.current
    var autoReconnect by remember { mutableStateOf(AppSettings.autoReconnectEnabled(context)) }
    // Backed by the same "kill_switch_enabled" key CdnVpnService reads
    // (isKillSwitchEnabled()) before deciding whether to hold a dead TUN up
    // after an unexpected disconnect -- this toggle is now the actual, live
    // switch for that behavior, not a decorative local-only state.
    var killSwitch by remember { mutableStateOf(AppSettings.killSwitchEnabled(context)) }

    SheetScreen(
        title = "Settings",
        onBack = onBack,
        headerContent = { AccountCard(account = account, onClick = onProfileClick) },
    ) {
        SectionLabel("CONNECTION", top = 8.dp)
        CardGroup {
            // Server choice, first in the group because it decides what every other row here
            // applies to. This is the only visible entry point to Smart mode: the hero's mode
            // pill was removed in the redesign, which left the horizontal swipe on the power
            // disc as the sole way to change it — discoverable by nobody.
            ModeChoiceRow(mode = mode, onSetMode = onSetMode)
            RowDivider()
            SettingsRow(Icons.Rounded.VerifiedUser, "Protocol", "VLESS", AnanasBlue, showChevron = true)
            RowDivider()
            SettingsToggleRow(
                Icons.Rounded.Autorenew, "Auto-reconnect", "Reconnect if connection drops",
                autoReconnect, {
                    autoReconnect = it
                    AppSettings.setAutoReconnectEnabled(context, it)
                },
                tint = AnanasAccent,
            )
            RowDivider()
            SettingsToggleRow(
                Icons.Rounded.Lock, "Kill switch", "Block traffic on disconnect",
                killSwitch, {
                    killSwitch = it
                    AppSettings.setKillSwitchEnabled(context, it)
                },
                tint = AnanasAmber,
            )
            RowDivider()
            run {
                val splitApps = AppSettings.splitTunnelApps(context)
                val splitMode = AppSettings.splitTunnelMode(context)
                val summary = when {
                    splitApps.isEmpty() -> null
                    splitMode == "include" -> "${splitApps.size} app${if (splitApps.size == 1) "" else "s"} only"
                    else -> "${splitApps.size} app${if (splitApps.size == 1) "" else "s"} excluded"
                }
                SettingsRow(
                    Icons.Rounded.CallSplit, "Split tunneling", summary, AnanasPurple,
                    showChevron = true, onClick = onSplitTunnelClick,
                )
            }
            RowDivider()
            run {
                var adBlockEnabled by remember { mutableStateOf(AppSettings.adBlockerEnabled(context)) }
                SettingsToggleRow(
                    Icons.Rounded.Block, "Ad blocker", "Block ads & tracking domains",
                    adBlockEnabled, {
                        adBlockEnabled = it
                        AppSettings.setAdBlockerEnabled(context, it)
                    },
                    tint = AnanasRed,
                )
            }
        }

        SectionLabel("NETWORK")
        CardGroup {
            var mtuMode by remember { mutableStateOf(AppSettings.mtuPreset(context)) }
            var customMtuText by remember { mutableStateOf(AppSettings.mtu(context).toString()) }
            var showCustomInput by remember { mutableStateOf(mtuMode == "custom") }
            var allowLan by remember { mutableStateOf(AppSettings.allowLan(context)) }
            var ipv6Enabled by remember { mutableStateOf(AppSettings.ipv6Enabled(context)) }
            var useDoh by remember { mutableStateOf(AppSettings.useDoh(context)) }

            // MTU is the one row on the page that carries a choice rather than a switch, so
            // it carries a segmented control where the others carry a toggle. Everything
            // else about the row — tile, label column, height — is the same, which is what
            // lets a group hold two kinds of control without looking assembled.
            Row(
                Modifier.fillMaxWidth().heightIn(min = SheetRowHeight)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Tune, AnanasBlue)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "MTU",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp,
                            color = AnanasTextHi,
                        )
                        Text(
                            if (mtuMode == "auto") "Automatic · 1500 bytes" else "$customMtuText bytes",
                            fontSize = 11.5.sp,
                            color = AnanasMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                SegmentedControl(
                    options = listOf("auto" to "Auto", "custom" to "Custom"),
                    selected = mtuMode,
                    onSelect = { key ->
                        if (key == "auto") {
                            mtuMode = "auto"
                            showCustomInput = false
                            AppSettings.setMtu(context, 1500)
                            AppSettings.setMtuPreset(context, "auto")
                        } else {
                            mtuMode = "custom"
                            showCustomInput = true
                        }
                    },
                )
            }

            if (showCustomInput) {
                RowDivider()
                InlineField(
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
                    label = "MTU (576–9000)",
                    placeholder = "1500",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    keyboardType = KeyboardType.Number,
                    // Only complains once there is something to complain about: an empty
                    // field mid-edit is not an error, a number outside the range is. The
                    // value is persisted only while valid, exactly as before.
                    error = when {
                        customMtuText.isBlank() -> null
                        customMtuText.toIntOrNull() == null -> "Digits only"
                        customMtuText.toInt() !in 576..9000 -> "Out of range"
                        else -> null
                    },
                )
            }

            RowDivider()
            SettingsToggleRow(
                Icons.Rounded.Router, "Allow LAN", "Access local network devices",
                allowLan, {
                    allowLan = it
                    AppSettings.setAllowLan(context, it)
                },
                tint = AnanasSettingsIcon,
            )
            RowDivider()
            SettingsToggleRow(
                Icons.Rounded.Language, "IPv6", "Route IPv6 traffic through VPN",
                ipv6Enabled, {
                    ipv6Enabled = it
                    AppSettings.setIpv6Enabled(context, it)
                },
                tint = AnanasBlue,
            )
            RowDivider()
            SettingsToggleRow(
                Icons.Rounded.Security, "DNS over HTTPS", "Encrypt DNS queries with DoH",
                useDoh, {
                    useDoh = it
                    AppSettings.setUseDoh(context, it)
                    // Notify VPN service of settings change
                    android.widget.Toast.makeText(
                        context,
                        if (it) "DoH enabled (reconnect to apply)" else "DoH disabled (reconnect to apply)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
                tint = AnanasAccent,
            )
        }

        SectionLabel("DNS")
        CardGroup {
            var customDnsEnabled by remember { mutableStateOf(AppSettings.customDnsEnabled(context)) }
            var primaryDns by remember { mutableStateOf(AppSettings.primaryDns(context)) }
            var secondaryDns by remember { mutableStateOf(AppSettings.secondaryDns(context)) }
            var showDnsInputs by remember { mutableStateOf(customDnsEnabled) }

            SettingsToggleRow(
                Icons.Rounded.Dns, "Custom DNS", "Use your own resolvers",
                customDnsEnabled, {
                    customDnsEnabled = it
                    showDnsInputs = it
                    AppSettings.setCustomDnsEnabled(context, it)
                },
                tint = AnanasPurple,
            )

            // The resolvers, only once the toggle is on: two fields and a note, in the same
            // card as the switch that revealed them rather than in a card of their own.
            if (showDnsInputs) {
                RowDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    val isPrimaryValid = AppSettings.isValidDnsServer(primaryDns)
                    InlineField(
                        value = primaryDns,
                        onValueChange = {
                            primaryDns = it
                            if (AppSettings.isValidDnsServer(it)) {
                                AppSettings.setPrimaryDns(context, it)
                            }
                        },
                        label = "PRIMARY",
                        placeholder = "8.8.8.8 or https://8.8.8.8/dns-query",
                        keyboardType = KeyboardType.Uri,
                        error = if (!isPrimaryValid && primaryDns.isNotBlank()) "Not a valid resolver" else null,
                    )
                    Spacer(Modifier.height(14.dp))

                    val isSecondaryValid = secondaryDns.isBlank() || AppSettings.isValidDnsServer(secondaryDns)
                    InlineField(
                        value = secondaryDns,
                        onValueChange = {
                            secondaryDns = it
                            if (it.isBlank() || AppSettings.isValidDnsServer(it)) {
                                AppSettings.setSecondaryDns(context, it)
                            }
                        },
                        label = "SECONDARY · OPTIONAL",
                        placeholder = "8.8.4.4 or quic://dns.google:853",
                        keyboardType = KeyboardType.Uri,
                        error = if (!isSecondaryValid && secondaryDns.isNotBlank()) "Not a valid resolver" else null,
                    )

                    // The note about what the tunnel does with DNS regardless of what is
                    // typed above. A tinted panel marked with an accent rule down its leading
                    // edge rather than ringed: it is a note, and a note that is boxed reads as
                    // another control the user is meant to be able to change.
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(AnanasAccent.copy(alpha = 0.07f))
                            .drawBehind {
                                drawRect(
                                    color = AnanasAccent.copy(alpha = 0.55f),
                                    size = Size(SheetNoteRuleWidth.toPx(), size.height),
                                )
                            }
                            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Rounded.Shield, null, tint = AnanasAccent, modifier = Modifier.size(14.dp))
                            Text(
                                "DNS leak protection",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp,
                                color = AnanasAccent,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "Every query is hijacked into the tunnel",
                            "DoH is the recommended transport",
                            "Formats: IP, IP:port, https:// or quic://",
                        ).forEach { line ->
                            Text(
                                "· $line",
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                color = AnanasMuted,
                            )
                        }
                    }
                }
            }
        }

        val clip = LocalClipboardManager.current

        SectionLabel("DIAGNOSTICS")
        CardGroup {
            Row(
                Modifier.fillMaxWidth().heightIn(min = SheetRowHeight)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Terminal, AnanasAccent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Connection log",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp,
                            color = AnanasTextHi,
                        )
                        Text(
                            if (CdnVpnService.lastError.isNotBlank()) {
                                "Last error: ${CdnVpnService.lastError.take(40)}"
                            } else {
                                "No errors on last connect"
                            },
                            fontSize = 11.5.sp,
                            color = if (CdnVpnService.lastError.isNotBlank()) AnanasRed else AnanasMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                PillButton(
                    "Copy",
                    onClick = {
                        val text = "lastError:\n${CdnVpnService.lastError}\n\ndebugLog:\n${CdnVpnService.debugLog}\n\nprotectLog:\n${MihomoBridge.protectLog()}\n\ncoreLog:\n${MihomoBridge.coreLog()}"
                        clip.setText(AnnotatedString(text))
                        android.widget.Toast.makeText(context, "Connection log copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
            }

            val crashFile = remember { File(context.filesDir, com.cdnhunter.app.CdnHunterApp.CRASH_LOG_FILE) }
            // Whether the file is there is read once into state, so "Clear" actually makes
            // the row go away — `crashFile.exists()` in the condition is not something
            // Compose can observe, so the old row stayed on screen after deleting it.
            var hasCrashLog by remember { mutableStateOf(crashFile.exists()) }
            if (hasCrashLog) {
                RowDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = SheetRowHeight)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.BugReport, AnanasRed)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Last crash log",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.1).sp,
                                color = AnanasTextHi,
                            )
                            Text(
                                "A saved report is on the device",
                                fontSize = 11.5.sp,
                                color = AnanasMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(
                            "Copy",
                            onClick = {
                                val text = runCatching { crashFile.readText() }.getOrDefault("")
                                clip.setText(AnnotatedString(text))
                                android.widget.Toast.makeText(context, "Crash log copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            accent = true,
                        )
                        PillButton(
                            "Clear",
                            onClick = {
                                runCatching { crashFile.delete() }
                                hasCrashLog = crashFile.exists()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A row that opens something, or just states a value: tile, label, optional value under it,
 *  optional chevron. */
@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String?, iconTint: Color, showChevron: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .heightIn(min = SheetRowHeight)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            IconTile(icon, iconTint)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = AnanasTextHi,
                )
                if (value != null) {
                    Text(
                        value,
                        fontSize = 11.5.sp,
                        color = AnanasMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (showChevron) {
            Icon(Icons.Rounded.ChevronRight, null, tint = AnanasFaint, modifier = Modifier.size(18.dp))
        }
    }
}

/** The same row with a switch on the right instead of a chevron. `tint` is the colour of the
 *  thing being switched, so a group of five switches can be scanned rather than read. */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tint: Color = AnanasSettingsIcon,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SheetRowHeight)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            IconTile(icon, tint)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = AnanasTextHi,
                )
                Text(
                    desc,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = AnanasMuted,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // Bespoke toggle — narrower capsule, soft drop shadow under the thumb,
        // no Material ripple halo on tap. See MinimalToggle() below.
        MinimalToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// Hand-built toggle instead of Material3's Switch: a narrower capsule track
// (44x24 vs Material's wider default), a thumb that reads as a lit object sitting
// in a groove, smooth 180ms slide + color crossfade, and — critically — no ripple
// halo on tap (Switch always draws one, which read as a stray flash on this dark
// background).
@Composable
private fun MinimalToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) AnanasToggleOn else AnanasCard2,
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
            // The track is a groove, so it is shaded at the top rather than lit there —
            // the opposite of every raised surface on these screens, which is what makes
            // the thumb read as standing above it.
            .drawBehind { drawRect(brush = SheetWellShade, size = Size(size.width, size.height / 2f)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset, top = 2.dp)
                .size(20.dp)
                .shadow(5.dp, CircleShape, clip = false, ambientColor = SheetShadow, spotColor = SheetShadow)
                .clip(CircleShape)
                .background(SheetThumbFill)
        )
    }
}

// ── Profile — visual reference screen (static placeholder, wired later) ────────
// The same frame as Settings, and for the same reason: the person lives in the glass header
// (see [SheetScreen]'s headerContent), so both screens open with one panel and continue into
// the same gutter, card corner and section labels. Contents are still placeholder; wiring
// this to a real account is separate work.
@Composable
private fun ProfileScreen(onBack: () -> Unit, account: AccountUiState, onSignOut: () -> Unit) {
    SheetScreen(
        title = "Profile",
        onBack = onBack,
        headerContent = {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarRing(size = 62.dp, initials = account.initials, initialsSize = 20.sp, ringWidth = 2.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = AnanasTextHi,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(account.email, fontSize = 12.5.sp, color = AnanasMuted, maxLines = 1)
                }
            }
        },
    ) {
        SectionLabel("SUBSCRIPTION", top = 8.dp)
        Column(
            Modifier.fillMaxWidth()
                .sheetSurface(RoundedCornerShape(SheetCardCorner), SheetPlanFill)
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTile(Icons.Rounded.WorkspacePremium, AnanasAmber)
                    Text(
                        account.planName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.1).sp,
                        color = AnanasAmber,
                    )
                }
                Text("Renews Aug 10", fontSize = 11.5.sp, color = AnanasMuted)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E0C0A))) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(account.periodProgress).clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(AnanasAmber.copy(alpha = 0.75f), AnanasAmber))),
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(account.daysRemainingLabel, fontSize = 11.5.sp, color = AnanasMuted)
        }

        SectionLabel("ACCOUNT")
        CardGroup {
            SettingsRow(Icons.Rounded.Diamond, "Upgrade plan", null, AnanasAmber, showChevron = true)
            RowDivider()
            SettingsRow(Icons.Rounded.History, "Payment history", null, AnanasBlue, showChevron = true)
            RowDivider()
            // The one destructive row in the app, so it is the one row whose label is not
            // [AnanasTextHi] — the tile alone would not be enough to slow a thumb down.
            // Sign-out is wired through an onSignOut lambda from AppScreen → MainActivity,
            // which calls FirebaseAuth.signOut() and flips `signedIn` back to false so the
            // app returns to AuthScreen.
            Row(
                Modifier.fillMaxWidth().clickable { onSignOut() }.heightIn(min = SheetRowHeight)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile(Icons.Rounded.Logout, AnanasRed)
                Text(
                    "Sign out",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    color = AnanasRed,
                )
            }
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

    // Deliberately not built on SheetScreen, even though it wears the same clothes:
    // SheetScreen scrolls its whole body, and an app list is a LazyColumn that has to
    // own the leftover height itself (this device may have 200 apps installed). So the
    // header is laid out fixed and the list takes weight(1f) — but the panel itself is
    // the shared [Modifier.sheetHeaderPanel], so it is the same glass as the other screens.
    Box(Modifier.fillMaxSize().background(AnanasScreenBg)) {
        // (No stray SheetPageWash box here either — see the note in SheetScreen. The
        // header panel's own glass + rim provide the top light without a square-cornered
        // rectangle poking out behind the rounded header.)
        // The status-bar inset lives inside the glass panel, so only the bottom inset is
        // taken here — the list must end above the navigation bar, not under it.
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(Modifier.sheetHeaderPanel()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainBackButton(onClick = onBack)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "Split tunneling",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        color = AnanasTextHi,
                    )
                }
                // The two modes are easy to invert in your head, so the screen says which
                // one is live in a sentence instead of leaving it to the labels.
                Text(
                    if (mode == "include") "The VPN carries only the apps you pick. Everything else goes direct."
                    else "The apps you pick go direct. Everything else goes through the VPN.",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnanasMuted,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
                // Switching modes doesn't clear the selection -- the same app list just
                // gets reinterpreted under the new mode, matching how most VPN apps with
                // this feature behave (Windscribe included).
                SectionLabel("MODE", top = 20.dp)
                SegmentedControl(
                    options = listOf("exclude" to "Exclude selected", "include" to "Only selected"),
                    selected = mode,
                    onSelect = { persist(selected, it) },
                    modifier = Modifier.fillMaxWidth(),
                    equalWeight = true,
                )
                Spacer(Modifier.height(SheetHeaderFootRoom))
            }

            Column(Modifier.padding(horizontal = SheetPad)) {
                SectionLabel(
                    if (selected.isEmpty()) "APPS" else "APPS · ${selected.size} SELECTED",
                    top = 20.dp,
                )
                InlineField(
                    value = search,
                    onValueChange = { search = it },
                    label = "SEARCH",
                    placeholder = "App name",
                )
                Spacer(Modifier.height(14.dp))
            }

            if (apps == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AnanasAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isBlank()) "No apps found on this device" else "No app matches “$search”",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AnanasFaint,
                    )
                }
            } else {
                // One card holding the whole list, the way CardGroup holds a settings
                // group — the rows scroll inside the frame rather than the frame
                // scrolling with them.
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = SheetPad)
                        .sheetSurface(RoundedCornerShape(SheetCardCorner)),
                ) {
                    itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                        val isChecked = selected.contains(app.packageName)
                        if (index > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    persist(if (isChecked) selected - app.packageName else selected + app.packageName, mode)
                                }
                                .padding(horizontal = 14.dp)
                                .heightIn(min = SheetRowHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap(width = 84, height = 84).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)),
                                )
                            } else {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(AnanasCard2))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AnanasTextHi,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    app.packageName,
                                    fontSize = 11.5.sp,
                                    color = AnanasMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SelectDot(selected = isChecked)
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                Spacer(Modifier.height(SheetPad))
            }
        }
    }
}

/**
 * The multi-select mark on an app row. A filled accent disc with a tick when the app is
 * picked, a hollow ring when it is not — the same two-state language as the toggles in
 * Settings, at the size a list row can carry, and without Material's checkbox bringing
 * its own palette and ripple into these cards.
 */
@Composable
private fun SelectDot(selected: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (selected) AnanasToggleOn else Color.Transparent,
        animationSpec = tween(140), label = "dotFill",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) AnanasToggleOn else AnanasBorder2,
        animationSpec = tween(140), label = "dotEdge",
    )
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(fill).border(1.5.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Icon button (top bar) ───────────────────────────────────────────────────────
@Composable
private fun AnanasIconButton(icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(38.dp)
            .sheetRaised(CircleShape, pressed, elevation = 5.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = AnanasText, modifier = Modifier.size(22.dp)) }
}

// A back affordance with no chip/disc/border around it — just the chevron itself, so the
// screen title can sit flush beside it. Keeps a full 40dp touch target (via the Box size)
// even though nothing is drawn behind the glyph, and dims briefly on press for feedback.
@Composable
private fun PlainBackButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(120), label = "backPress")
    Box(
        modifier
            .size(44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Back",
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = "Back",
            tint = AnanasText.copy(alpha = alpha),
            modifier = Modifier.size(28.dp),
        )
    }
}

// Home's own composables — power circle, connect bar, server list, usage card —
// all live in HomeScreen.kt.
