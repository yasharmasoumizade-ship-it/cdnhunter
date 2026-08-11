package com.cdnhunter.app.ui

// ── HOME ──────────────────────────────────────────────────────────────────────
// Rebuilt from design-reference/vpn-home-v3-clean-bg.html — the HTML/CSS mockup
// kept in this repo as a visual reference only (nothing in the build reads it).
// The mockup frames the screen in a 390px-wide device, so its CSS pixels map 1:1
// onto dp here: every number below is the mockup's own, and the trailing comment
// names the rule it came from so any of them can be re-checked instead of
// re-guessed.
//
// Layout, top to bottom:
//   • page        — near-black vertical gradient, #0d0e12 → #060709 by 70%
//   • hero glow   — one large ambient bloom centred on the power button, and the
//                   active country's own flag behind it as a low-alpha watermark.
//                   The bloom's tone is the only thing the connection state
//                   repaints: burgundy off, violet connecting, blue/purple on
//   • top bar     — hamburger → Settings, account glyph → Profile
//   • status row  — OFF/ON pill, "VLESS · 443", chevron → Settings
//   • connect bar — 88dp pill carrying the active server's city and country, with
//                   a 100dp white power circle whose centre sits exactly on the
//                   bar's right edge; the bar is cut away behind it, leaving 8dp
//                   of clearance, and the circle's ring carries the glow
//   • network row — wifi glyph, transport label, public IP (tap to copy)
//   • browse card — 28dp-topped panel: Main / Custom Config pills, a search
//                   toggle that expands a field, then one row per server
//                   (flag, country · city, ping, three load bars)
//   • usage card  — floats over the list bottom: session-traffic ring, live
//                   speed, chevron → Locations
//
// HomeScreen() stays stateless about the VPN: it takes one HomeUiState snapshot
// plus event lambdas, so VpnTab() remains the single owner of connection state.
// The only state kept here is view state nothing else needs — which tab is
// selected, whether search is open, and the query.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette — the mockup's :root custom properties, verbatim ───────────────────
private val RefBg = Color(0xFF060709)          // --bg
private val RefElev1 = Color(0xFF0F1116)       // --bg-elev-1
private val RefElev2 = Color(0xFF15171E)       // --bg-elev-2
private val RefBorder = Color(0xFF23262F)      // --border
private val RefTextHi = Color(0xFFF6F7F9)      // --text-hi
private val RefTextMid = Color(0xFF9BA0AC)     // --text-mid
private val RefTextLow = Color(0xFF656B78)     // --text-low
private val RefAccent = Color(0xFF4D7FFF)      // --accent
private val RefGreen = Color(0xFF34D17A)       // --green
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PillInk = Color(0xFF05070C)        // .tab-pill.active text colour
private val PowerInk = Color(0xFF0C0E14)       // .power-btn svg colour

// ── Connection tone ───────────────────────────────────────────────────────────
// Windscribe holds one colour language across the whole screen: a near-black page
// carrying a single large ambient glow behind the power button. Connecting and
// connected *do not* repaint the UI — the page, the pill, the white power face
// and the list are byte-for-byte the same in every state. All that moves is the
// tone of that one glow and the light in the button's ring:
//
//   off        burgundy / red      — the resting tone
//   connecting violet              — visibly between the two, so the change reads
//   connected  blue / purple       — the same glow, cooled
//
// Each field is animated (see [rememberConnTone]) so the shift is a 700ms fade
// from one tone to the next rather than a cut.
private val ToneOffPrimary = Color(0xFF8E1E3C)      // burgundy core
private val ToneOffSecondary = Color(0xFFC42B48)    // red lift, up and left
private val ToneOffRing = Color(0xFFFF5C77)
private val ToneBusyPrimary = Color(0xFF5A2C9C)
private val ToneBusySecondary = Color(0xFF8B3FD0)
private val ToneBusyRing = Color(0xFFBB8CFF)
private val ToneOnPrimary = Color(0xFF3244E4)       // blue core
private val ToneOnSecondary = Color(0xFF7B3FE4)     // purple lift
private val ToneOnRing = Color(0xFF8DA2FF)

/** The three colours the connection state is allowed to touch. */
private data class ConnTone(val primary: Color, val secondary: Color, val ring: Color)

@Composable
private fun rememberConnTone(connected: Boolean, connecting: Boolean): ConnTone {
    val primaryTarget = when {
        connected -> ToneOnPrimary
        connecting -> ToneBusyPrimary
        else -> ToneOffPrimary
    }
    val secondaryTarget = when {
        connected -> ToneOnSecondary
        connecting -> ToneBusySecondary
        else -> ToneOffSecondary
    }
    val ringTarget = when {
        connected -> ToneOnRing
        connecting -> ToneBusyRing
        else -> ToneOffRing
    }
    val primary by animateColorAsState(primaryTarget, tween(700, easing = FastOutSlowInEasing), label = "tonePrimary")
    val secondary by animateColorAsState(secondaryTarget, tween(700, easing = FastOutSlowInEasing), label = "toneSecondary")
    val ring by animateColorAsState(ringTarget, tween(700, easing = FastOutSlowInEasing), label = "toneRing")
    return ConnTone(primary = primary, secondary = secondary, ring = ring)
}

// ── Dimensions — CSS px read as dp (the mockup's device is 390px wide) ─────────
private val ScreenPad = 20.dp        // .header padding: 4px 20px 14px
private val BarHeight = 88.dp        // .connect-bar height
private val PowerSize = 100.dp       // .power-btn, .power-btn-wrap
// The mockup masks the bar with `circle 52px at 100% 50%` around a 50dp-radius
// button, i.e. a 2dp hairline of space. At arm's length that reads as a printing
// error rather than as a gap, so the cut here clears the button by 8dp — the one
// number deliberately off the mockup, and the better spacing between the location
// card and the button.
private val PowerCut = 58.dp
private val PanelCorner = 28.dp      // .browse-card border-radius
private val ListPad = 18.dp          // .server-row / .tab-row horizontal padding
private val FlagSize = 36.dp         // .server-flag
private val DividerStart = 52.dp     // .server-row::after left
private val CardCorner = 20.dp       // --radius-lg on .bottom-card
private val CardMargin = 14.dp       // .bottom-card margin / bottom
private val RingSize = 50.dp         // .usage-ring
private val RingStroke = 5.dp        // (50px ring − 40px inner disc) / 2
private val TapTarget = 48.dp        // touch floor; the mockup's boxes are 40px

// ── Hero backdrop geometry ────────────────────────────────────────────────────
// The flag watermark and the ambient glow are both centred on the power button,
// which is the visual anchor of the screen. The button's centre sits
//   x: ScreenPad + PowerSize/2 in from the right edge  (= 70dp)
//   y: 4 (header top pad) + 40 (top bar) + 4 + 34 (status row) + 12 = 94dp
//      below the status bar, plus PowerSize/2 to reach its middle  (= 144dp)
// so the backdrop is laid out from the header's top-right corner and shifted onto
// that point. Both layers fade to nothing well before their own edges, which is
// what lets the ±few dp of slack in the status row's measured height not matter.
private val HeroSize = 380.dp                // the glow's full extent
private val HeroFlagSize = 250.dp            // the flag disc inside it
private val PowerRowCenterY = 144.dp
private val HeroOffsetX = HeroSize / 2 - (ScreenPad + PowerSize / 2)
private val HeroOffsetY = PowerRowCenterY - HeroSize / 2
/** Low enough to read as a watermark, high enough that the flag's colours land. */
private const val FLAG_WATERMARK_ALPHA = 0.24f

/**
 * The mockup's ring is 24% filled and labelled 2.4 GB, i.e. a 10 GB full sweep.
 * Here it measures the current session's traffic against that same scale.
 */
private const val USAGE_RING_SCALE_BYTES = 10L * 1024 * 1024 * 1024

// .device background — more stops than the mockup's four so the ramp has no
// visible banding on an OLED panel at these near-black values.
private val PageGradient = Brush.verticalGradient(
    0.00f to Color(0xFF0D0E12),
    0.08f to Color(0xFF0C0D11),
    0.16f to Color(0xFF0B0C10),
    0.24f to Color(0xFF0A0B0F),
    0.34f to Color(0xFF090A0D),
    0.46f to Color(0xFF08090C),
    0.58f to Color(0xFF07080A),
    0.70f to RefBg,
    1.00f to RefBg,
)
// .connect-bar background: top-lit, so the pill reads as a raised surface rather
// than a flat cut-out. The flag no longer lives in here (it is the hero
// watermark behind the button now), which is what lets the fill stay this plain.
private val BarSurface = Brush.verticalGradient(
    0.00f to Color(0xFF1A1D25),
    0.55f to RefElev1,
    1.00f to Color(0xFF0B0D11),
)
// inset 0 1px 0 rgba(255,255,255,.06), plus a matching foot so the bottom edge
// doesn't read as brighter than the top.
private val BarSheen = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.07f),
    0.05f to Color.White.copy(alpha = 0.02f),
    0.12f to Color.Transparent,
    0.88f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.16f),
)

/**
 * Everything Home draws, snapshotted from VpnTab() on each recomposition.
 *
 * [allConfigs] is every saved server — the browse list groups and filters it;
 * [activeConfig] is the one the power button acts on. Both arrive already
 * loaded: Home never touches disk.
 */
internal data class HomeUiState(
    val activeConfig: SavedConfig?,
    val allConfigs: List<SavedConfig> = emptyList(),
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val elapsedSec: Long = 0L,
    val downloadKBps: Double = 0.0,
    val uploadKBps: Double = 0.0,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    // Geo of the live tunnel's exit node, measured through the tunnel itself
    // after connecting — only trusted for the config it was measured on.
    val exitCountryCode: String = "",
    val exitCity: String = "",
    val exitGeoConfigId: String = "",
    /** "Wi-Fi" / "Mobile data" / "" — the transport this device is on right now. */
    val networkName: String = "",
    /** Public IP: the tunnel's exit IP when connected, this device's when not. */
    val publicIp: String = "",
) {
    private fun hasExitGeo(cfg: SavedConfig) =
        connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()

    /** Exit-node country once the tunnel has reported it, else the local guess. */
    fun countryCodeFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCountryCode else cfg.countryCode

    fun cityFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCity else cfg.city

    val sessionBytes: Long get() = totalDownloadBytes + totalUploadBytes
}

/**
 * The mockup's two tab pills. "Main" is everything the app knows about;
 * "Custom Config" is the servers added by hand rather than pulled from a
 * subscription (SavedConfig.isImported).
 */
private enum class HomeTab(val label: String) {
    MAIN("Main"),
    CUSTOM("Custom Config"),
}

private fun HomeUiState.configsFor(tab: HomeTab): List<SavedConfig> = when (tab) {
    HomeTab.MAIN -> allConfigs
    HomeTab.CUSTOM -> allConfigs.filter { !it.isImported }
}

private fun List<SavedConfig>.matching(query: String): List<SavedConfig> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.displayName.contains(q, ignoreCase = true) ||
            it.city.contains(q, ignoreCase = true) ||
            it.countryCode.contains(q, ignoreCase = true) ||
            countryCodeToName(it.countryCode).contains(q, ignoreCase = true)
    }
}

/** Fastest first, like the mockup's 12/34/41/52/58 ms list; unmeasured last. */
private fun List<SavedConfig>.byLatency(): List<SavedConfig> =
    sortedBy { if (it.pingMs < 0) Int.MAX_VALUE else it.pingMs }

/** ".server-name" — "Germany · Falkenstein", falling back to the config's name. */
private fun HomeUiState.rowTitle(cfg: SavedConfig): String {
    val geo = listOf(countryCodeToName(countryCodeFor(cfg)), cityFor(cfg))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    return geo.ifBlank { cfg.displayName.ifBlank { cfg.address } }
}

/** ".server-sub" — the ping, plus the config's own name when the title is geo. */
private fun HomeUiState.rowSubtitle(cfg: SavedConfig): String {
    val ping = if (cfg.pingMs >= 0) "${cfg.pingMs} ms" else "not measured"
    val name = cfg.displayName.takeIf {
        it.isNotBlank() && !it.equals(rowTitle(cfg), ignoreCase = true)
    }
    return if (name != null) "$ping · $name" else ping
}

private fun protocolLabel(cfg: SavedConfig?): String {
    if (cfg == null) return "No server selected"
    val proto = cfg.proto.uppercase().ifBlank { cfg.network.uppercase() }.ifBlank { "VLESS" }
    return "$proto · ${cfg.port}"
}

/** "2.4" to "GB" — the ring's own one-decimal label, split so the unit can wrap. */
private fun ringLabel(bytes: Long): Pair<String, String> {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f".format(gb) to "GB"
        mb >= 1.0 -> "%.1f".format(mb) to "MB"
        kb >= 1.0 -> "%.0f".format(kb) to "KB"
        else -> bytes.toString() to "B"
    }
}

private fun speedLabel(kbps: Double): String =
    if (kbps >= 1024.0) "%.1f MB/s".format(kbps / 1024.0) else "%.0f KB/s".format(kbps)

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(HomeTab.MAIN) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val servers = remember(state.allConfigs, tab, query) {
        state.configsFor(tab).matching(query).byLatency()
    }
    val activeId = state.activeConfig?.id
    val tone = rememberConnTone(connected = state.connected, connecting = state.connecting)

    Box(modifier.fillMaxSize().background(PageGradient)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                tone = tone,
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
            )
            BrowseCard(
                state = state,
                tone = tone,
                servers = servers,
                activeId = activeId,
                tab = tab,
                query = query,
                searchOpen = searchOpen,
                onSelectTab = { tab = it },
                onQueryChange = { query = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onSelectConfig = onSelectConfig,
                onOpenLocations = onOpenLocations,
                modifier = Modifier.weight(1f),
            )
        }

        // .bottom-card: sticky at the foot of the scroller, over the list
        UsageCard(
            state = state,
            tone = tone,
            onClick = onOpenLocations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = CardMargin)
                .padding(bottom = CardMargin),
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
// Three layers, back to front: the hero backdrop (glow + flag watermark), the
// black wash that keeps the top bar legible over it, then the rows themselves.
// The gaps between rows are the mockup's own margins (4 / 12 / 22dp), spelled
// out one by one rather than smoothed into a single rhythm.
@Composable
private fun Header(
    state: HomeUiState,
    tone: ConnTone,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
) {
    val heroCountry = state.activeConfig?.let { state.countryCodeFor(it) }.orEmpty()
    Box(Modifier.fillMaxWidth()) {
        // matchParentSize: the backdrop is 380dp of deliberate overspill, and this
        // keeps it from dragging the header's own height out with it. What spills
        // below is covered by the browse card, which is opaque.
        Box(Modifier.matchParentSize()) {
            HeroBackdrop(
                countryCode = heroCountry,
                tone = tone,
                connected = state.connected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .offset(x = HeroOffsetX, y = HeroOffsetY),
            )
        }
        Box(Modifier.matchParentSize().drawBehind { drawHeaderScrim() })
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = ScreenPad, end = ScreenPad, top = 4.dp, bottom = 14.dp)
        ) {
            TopBar(onOpenSettings = onOpenSettings, onOpenProfile = onOpenProfile)
            Spacer(Modifier.height(4.dp))          // .status-row margin-top
            StatusRow(state = state, tone = tone, onOpenSettings = onOpenSettings)
            Spacer(Modifier.height(12.dp))         // .power-row margin-top
            ConnectRow(
                state = state,
                tone = tone,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
            )
            Spacer(Modifier.height(22.dp))         // .network-row margin-top
            NetworkRow(state = state)
        }
    }
}

/**
 * The glow and the flag: everything behind the connect control.
 *
 * The glow is two offset radial blooms rather than one, which is what stops it
 * reading as a flat disc of colour — [ConnTone.primary] centred on the button and
 * [ConnTone.secondary] lifted up and to the left, so the light has a direction.
 * Both are drawn well outside this box's own bounds and fade to fully transparent
 * before they get there.
 */
@Composable
private fun HeroBackdrop(countryCode: String, tone: ConnTone, connected: Boolean, modifier: Modifier = Modifier) {
    // requiredSize, not size: this sits inside a box the height of the header, and
    // plain size() would let those constraints squash 380dp down to whatever the
    // header happens to measure.
    Box(modifier.requiredSize(HeroSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawToneGlow(tone, connected) }
        if (countryCode.isNotBlank()) {
            FlagWatermark(countryCode, Modifier.size(HeroFlagSize))
        }
    }
}

private fun DrawScope.drawToneGlow(tone: ConnTone, connected: Boolean) {
    if (connected) {
        // A plain, elegant dark-green wash rising from the bottom — replaces the
        // multi-tone radial bloom entirely once actually connected. No animation,
        // just a static gradient.
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.35f to ConnectedGreenDeep.copy(alpha = 0.10f),
                0.70f to ConnectedGreenDeep.copy(alpha = 0.30f),
                1.00f to ConnectedGreenDeep.copy(alpha = 0.46f),
            ),
        )
        return
    }
    val mid = center
    val radius = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to tone.primary.copy(alpha = 0.36f),
            0.22f to tone.primary.copy(alpha = 0.26f),
            0.44f to tone.primary.copy(alpha = 0.14f),
            0.68f to tone.primary.copy(alpha = 0.05f),
            1.00f to Color.Transparent,
            center = mid,
            radius = radius,
        ),
        radius = radius,
        center = mid,
    )
    val liftCenter = Offset(mid.x - radius * 0.40f, mid.y - radius * 0.32f)
    val liftRadius = radius * 0.86f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to tone.secondary.copy(alpha = 0.26f),
            0.34f to tone.secondary.copy(alpha = 0.15f),
            0.66f to tone.secondary.copy(alpha = 0.05f),
            1.00f to Color.Transparent,
            center = liftCenter,
            radius = liftRadius,
        ),
        radius = liftRadius,
        center = liftCenter,
    )
}

// The one dark, elegant green used for the connected-state wash — deliberately
// muted (not the bright accent green used for text/badges elsewhere), so a
// full-bleed wash of it reads as calm rather than a flat color block.
private val ConnectedGreenDeep = Color(0xFF0B3D2E)

/**
 * The active country's real flag, sitting directly behind the power button.
 *
 * Same asset the list rows use — assets/flags/{cc}.svg from HatScripts/circle-flags,
 * i.e. the country's actual vector artwork in its actual colours (Germany renders
 * black/red/gold, not an approximation), circular because every flag in the app is.
 * Blown up to [HeroFlagSize] and dropped to [FLAG_WATERMARK_ALPHA], with a radial
 * DstIn mask so the disc dissolves into the page instead of ending on a hard rim —
 * that fade is also why it can run off the right edge of the screen without
 * looking sliced.
 */
@Composable
private fun FlagWatermark(countryCode: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data("file:///android_asset/flags/${countryCode.lowercase().trim()}.svg")
            .crossfade(true)
            .build(),
        imageLoader = getFlagImageLoader(context),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer {
                alpha = FLAG_WATERMARK_ALPHA
                // The mask below multiplies this layer's alpha, so the layer has to
                // exist as its own surface first.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color.White,
                        0.46f to Color.White,
                        0.74f to Color.White.copy(alpha = 0.55f),
                        1.00f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                    blendMode = BlendMode.DstIn,
                )
            },
        error = null,
    )
}

/** .header::before — a black wash that fades out by 75% of the header. */
private fun DrawScope.drawHeaderScrim() {
    drawRect(
        Brush.verticalGradient(
            0.00f to Color.Black.copy(alpha = 0.45f),
            0.24f to Color.Black.copy(alpha = 0.28f),
            0.48f to Color.Black.copy(alpha = 0.12f),
            0.75f to Color.Transparent,
            1.00f to Color.Transparent,
        )
    )
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onOpenProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        // The mockup's tap boxes are 40px; these are 48dp for reach and nudged
        // back out by 4dp so the glyphs still sit on the mockup's margins.
        GlyphButton(
            onClick = onOpenSettings,
            label = "Settings",
            modifier = Modifier.offset(x = (-4).dp),
        ) {
            Icon(
                Icons.Rounded.Menu,
                contentDescription = "Settings",
                tint = RefTextHi,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        GlyphButton(
            onClick = onOpenProfile,
            label = "Account",
            modifier = Modifier.offset(x = 4.dp),
        ) {
            AccountGlyph(color = RefTextHi, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun GlyphButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(TapTarget)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Status row ────────────────────────────────────────────────────────────────
@Composable
private fun StatusRow(state: HomeUiState, tone: ConnTone, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(connected = state.connected, connecting = state.connecting, tone = tone)
        Spacer(Modifier.width(10.dp))              // .status-row gap
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Protocol settings", onClick = onOpenSettings)
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                protocolLabel(state.activeConfig),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = RefTextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))           // .protocol-line gap
            Chevron(size = 14.dp, color = RefTextLow)
        }
    }
}

/**
 * .status-pill — grey at rest, and once the tunnel is up or coming up it picks up
 * [ConnTone.ring], the same light the power button's ring is showing. It is not a
 * second accent colour: it is the same one, so the screen never has two stories
 * about what state it is in.
 */
@Composable
private fun StatusPill(connected: Boolean, connecting: Boolean, tone: ConnTone) {
    val label = when {
        connected -> "ON"
        connecting -> "···"
        else -> "OFF"
    }
    val lit = connected || connecting
    val fill by animateColorAsState(
        if (lit) tone.ring.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
        label = "pillFill",
    )
    val edge by animateColorAsState(
        if (lit) tone.ring.copy(alpha = 0.34f) else RefBorder,
        label = "pillEdge",
    )
    val ink by animateColorAsState(if (lit) tone.ring else RefTextMid, label = "pillInk")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, edge, RoundedCornerShape(50))
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,                // .05em at 11.5px
            color = ink,
            maxLines = 1,
        )
    }
}

// ── Connect bar + power circle ────────────────────────────────────────────────
// .power-row: the bar stops 50dp short of the right edge and the 100dp circle is
// pulled back over it (CSS margin-left: -50px), so the circle's centre lands
// exactly on the bar's right edge and its outer half overhangs the row.
@Composable
private fun ConnectRow(
    state: HomeUiState,
    tone: ConnTone,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(PowerSize)) {
        ConnectBar(
            state = state,
            tone = tone,
            onClick = onOpenLocations,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = PowerSize / 2)
                .fillMaxWidth()
                .height(BarHeight),
        )
        PowerCircle(
            connected = state.connected,
            connecting = state.connecting,
            enabled = state.activeConfig != null,
            tone = tone,
            onClick = onTogglePower,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ConnectBar(
    state: HomeUiState,
    tone: ConnTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shape = remember(density) { connectBarShape(with(density) { PowerCut.toPx() }) }
    val cfg = state.activeConfig
    val countryCode = cfg?.let { state.countryCodeFor(it) }.orEmpty()
    val city = cfg?.let { state.cityFor(it) }.orEmpty()
    val country = countryCodeToName(countryCode)
    val headline = city.ifBlank {
        country.ifBlank { cfg?.let { c -> c.displayName.ifBlank { c.address } } ?: "No server" }
    }
    // The second line names the country, then the server itself — but only the
    // parts that aren't already the headline: a config named after its own city
    // would otherwise read "Frankfurt · Frankfurt".
    val sub = remember(headline, country, cfg?.displayName) {
        buildList {
            if (country.isNotBlank() && !country.equals(headline, ignoreCase = true)) add(country)
            cfg?.displayName
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(headline, ignoreCase = true) &&
                        !it.equals(country, ignoreCase = true)
                }
                ?.let { add(it) }
        }.joinToString(" · ")
    }

    Box(
        modifier
            .shadow(14.dp, shape)                  // --shadow-card, one step deeper
            .clip(shape)
            .background(BarSurface)
            .border(1.dp, RefBorder, shape)
            .clickable(onClickLabel = "Choose a server", onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The country's own flag fills the whole pill — same asset the list rows
        // and the hero watermark use — so the bar reads as belonging to that
        // server, not just tinted by an abstract color. A dark shade gradient on
        // top keeps the location text on the left legible against it.
        if (countryCode.isNotBlank()) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/flags/${countryCode.lowercase().trim()}.svg")
                    .crossfade(true)
                    .build(),
                imageLoader = getFlagImageLoader(LocalContext.current),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0.00f to BarSurface,
                    0.20f to BarSurface.copy(alpha = 0.92f),
                    0.42f to BarSurface.copy(alpha = 0.55f),
                    0.62f to BarSurface.copy(alpha = 0.12f),
                    0.80f to Color.Transparent,
                )
            )
        )
        if (state.connected) {
            // Plain, elegant dark-green wash rising bottom-to-top — the only thing
            // that marks "connected" on the bar now, in place of the old tone tint.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.40f to ConnectedGreenDeep.copy(alpha = 0.16f),
                        1.00f to ConnectedGreenDeep.copy(alpha = 0.52f),
                    )
                )
            )
        } else {
            // A breath of the connection tone bleeding in from the button end, so the
            // pill belongs to the same light as the circle instead of sitting beside it.
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.52f to Color.Transparent,
                        0.82f to tone.primary.copy(alpha = 0.10f),
                        1.00f to tone.primary.copy(alpha = 0.24f),
                    )
                )
            )
        }
        Box(Modifier.matchParentSize().background(BarSheen))
        Column(
            // .connect-bar padding is 0 20px; the extra end room keeps long names
            // clear of the circular cut, which is now 8dp wider than the button.
            Modifier.padding(start = ScreenPad, end = 62.dp)
        ) {
            Text(
                headline,
                fontSize = 21.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = RefTextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    sub,
                    fontSize = 13.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                    color = RefTextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The pill, minus a circle cut out of its right edge — the Compose equivalent of
 * the mockup's `mask-image: radial-gradient(circle 52px at 100% 50%, …)`. The cut
 * is centred on the power circle's centre and [PowerCut] wide, which is what
 * leaves the ring of space between the two.
 */
private fun connectBarShape(cutRadiusPx: Float): Shape = GenericShape { size, _ ->
    val pill = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        )
    }
    val cut = Path().apply {
        addOval(Rect(center = Offset(size.width, size.height / 2f), radius = cutRadiusPx))
    }
    op(pill, cut, PathOperation.Difference)
}

// ── Power circle ──────────────────────────────────────────────────────────────
// .power-btn — a 100dp brushed-white disc, and it stays brushed white in every
// state. What changes is the ring of light around it, drawn by [PowerHalo] in the
// current [ConnTone]: dim burgundy at rest, a spinning violet arc while the tunnel
// comes up, a steady breathing blue-purple once it is up. Compose has no inset
// box-shadow, so the mockup's two inner shadows are drawn as an overlay — a bright
// top rim and a soft dark foot.
@Composable
private fun PowerCircle(
    connected: Boolean,
    connecting: Boolean,
    enabled: Boolean,
    tone: ConnTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,               // .power-btn:active
        label = "powerPress",
    )
    val ink = if (enabled) PowerInk else PowerInk.copy(alpha = 0.30f)
    Box(modifier.size(PowerSize), contentAlignment = Alignment.Center) {
        // Flat white disc + cast shadow only — no halo, no ring, no breathing glow.
        // The HTML reference button (.power-glow{display:none}) is a plain glossy
        // white circle in every state; the only thing that ever moves is the press
        // scale.
        Box(
            Modifier
                .matchParentSize()
                .scale(scale)
                .shadow(22.dp, CircleShape)        // 0 16px 34px rgba(0,0,0,.45)
                .clip(CircleShape)
                .background(PowerFace)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = if (connected) "Disconnect" else "Connect",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().background(PowerFaceSheen))
            if (connecting) {
                // A plain, quiet spinner — no colour, no glow — while the tunnel
                // is coming up.
                CircularProgressIndicator(
                    color = ink,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (connected) "Disconnect" else "Connect",
                    tint = ink,
                    modifier = Modifier.size(48.dp),  // ≈ the mockup's 54dp stroke svg
                )
            }
        }
    }
}

// linear-gradient(160deg, #ffffff 0%, #e7e9ee 55%, #d9dce3 100%)
private val PowerFace = Brush.linearGradient(
    0.00f to Color.White,
    0.30f to Color(0xFFF4F5F8),
    0.55f to Color(0xFFE7E9EE),
    1.00f to Color(0xFFD9DCE3),
)

// inset 0 3px 4px rgba(255,255,255,.95) over inset 0 -10px 14px rgba(0,0,0,.14)
private val PowerFaceSheen = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.55f),
    0.06f to Color.White.copy(alpha = 0.10f),
    0.14f to Color.Transparent,
    0.80f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.14f),
)

// ── Network row ───────────────────────────────────────────────────────────────
// .network-row: transport on the left, the public IP hard right. The IP copies to
// the clipboard on tap — no visual affordance, the mockup doesn't show one.
@Composable
private fun NetworkRow(state: HomeUiState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ip = state.publicIp
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WifiGlyph(color = RefTextHi, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))               // .network-left gap
        Text(
            state.networkName.ifBlank { "No network" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = RefTextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            ip.ifBlank { "—" },
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = RefTextHi,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"),  // tabular-nums
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    enabled = ip.isNotBlank(),
                    onClickLabel = "Copy IP address",
                ) {
                    clipboard.setText(AnnotatedString(ip))
                    android.widget.Toast
                        .makeText(context, "IP copied", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

// ── Browse card ───────────────────────────────────────────────────────────────
// .browse-card: the list's own panel, 28dp top corners and a light hairline along
// that top edge, sitting 10dp below the header.
@Composable
private fun BrowseCard(
    state: HomeUiState,
    tone: ConnTone,
    servers: List<SavedConfig>,
    activeId: String?,
    tab: HomeTab,
    query: String,
    searchOpen: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onOpenLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp)                  // .browse-card margin-top
            .clip(RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner))
            .background(RefBg)
            .drawBehind { drawPanelTopEdge() }
    ) {
        TabPillRow(
            selected = tab,
            searchOpen = searchOpen,
            onSelect = onSelectTab,
            onToggleSearch = onToggleSearch,
        )
        SearchField(visible = searchOpen, query = query, onQueryChange = onQueryChange)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            // Enough room that the last row clears the floating usage card.
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            if (servers.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        allEmpty = state.allConfigs.isEmpty(),
                        searching = query.isNotBlank(),
                        tab = tab,
                        onAdd = onOpenLocations,
                    )
                }
            }
            itemsIndexed(servers, key = { _, cfg -> cfg.id }) { index, cfg ->
                val isActive = cfg.id == activeId
                ServerRow(
                    title = state.rowTitle(cfg),
                    subtitle = state.rowSubtitle(cfg),
                    countryCode = state.countryCodeFor(cfg),
                    pingMs = cfg.pingMs,
                    isActive = isActive,
                    // The active server's dot carries the same light as the power
                    // ring, so "this is the one that's up" is one colour, not two.
                    dotColor = if (state.connected && isActive) tone.ring else RefTextMid,
                    showDivider = index < servers.lastIndex,
                    onClick = { onSelectConfig(cfg) },
                )
            }
        }
    }
}

/** .browse-card border-top: only the top edge and its two corner arcs. */
private fun DrawScope.drawPanelTopEdge() {
    val hairline = 1.dp.toPx()
    val radius = PanelCorner.toPx()
    clipRect(top = 0f, bottom = radius + hairline) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.14f),
            topLeft = Offset(hairline / 2f, hairline / 2f),
            size = Size(size.width - hairline, size.height - hairline),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = hairline),
        )
    }
}

// ── Tabs + search ─────────────────────────────────────────────────────────────
@Composable
private fun TabPillRow(
    selected: HomeTab,
    searchOpen: Boolean,
    onSelect: (HomeTab) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ListPad, vertical = 14.dp),   // .tab-row
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabs = HomeTab.values()
        tabs.forEachIndexed { index, tab ->
            TabPill(label = tab.label, selected = tab == selected, onClick = { onSelect(tab) })
            if (index < tabs.lastIndex) Spacer(Modifier.width(10.dp))  // .tab-pills gap
        }
        Spacer(Modifier.weight(1f))
        SearchToggle(open = searchOpen, onClick = onToggleSearch)
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    // .tab-pill transitions colour and background over .18s
    val fill by animateColorAsState(
        if (selected) Color.White else Color.Transparent,
        tween(180),
        label = "pillFill",
    )
    val ink by animateColorAsState(
        if (selected) PillInk else RefTextMid,
        tween(180),
        label = "pillInk",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(fill)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** .search-btn — a 34dp disc that tints accent-blue while the field is open. */
@Composable
private fun SearchToggle(open: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        if (open) RefAccent.copy(alpha = 0.16f) else Color.Transparent,
        tween(180),
        label = "searchFill",
    )
    val ink by animateColorAsState(if (open) RefAccent else RefTextMid, tween(180), label = "searchInk")
    Box(
        Modifier
            .size(TapTarget)
            .clip(CircleShape)
            .clickable(
                onClickLabel = if (open) "Close search" else "Search servers",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** .search-bar-wrap — hidden until the circle is toggled, then expands downward. */
@Composable
private fun SearchField(visible: Boolean, query: String, onQueryChange: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(280)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(280)) + fadeOut(tween(220)),
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ListPad)
                .padding(bottom = 12.dp)           // .search-bar margin
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.045f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = RefTextMid,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))          // .search-bar gap
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search location or server",
                        fontSize = 14.5.sp,
                        color = RefTextLow,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = RefTextHi, fontSize = 14.5.sp),
                    cursorBrush = SolidColor(RefAccent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
    }
}

// ── Server list ───────────────────────────────────────────────────────────────
// .server-row: 36dp circular flag, name over ping, three load bars, and a
// hairline that starts past the flag (left:52px) on every row but the last.
@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    countryCode: String,
    pingMs: Int,
    isActive: Boolean,
    dotColor: Color,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // The mockup has no selected state; the active server gets the
            // faintest tint and a dot so it can still be told apart.
            .background(if (isActive) Color.White.copy(alpha = 0.03f) else Color.Transparent)
            .clickable(onClickLabel = "Use $title", onClick = onClick)
            .drawBehind {
                if (showDivider) {
                    val hairline = 1.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.09f),
                        start = Offset(DividerStart.toPx(), size.height - hairline),
                        end = Offset(size.width - ListPad.toPx(), size.height - hairline),
                        strokeWidth = hairline,
                    )
                }
            }
            .padding(horizontal = ListPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagBadge(countryCode, FlagSize)
        Spacer(Modifier.width(14.dp))              // .server-row gap
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.5.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = RefTextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                fontSize = 12.5.sp,
                color = RefTextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        LoadBars(pingMs)
    }
}

/**
 * .load-bars — three 3dp bars, 6/9/12dp tall. The mockup only paints them green
 * or amber; how many light up follows the app's own ping tiers (<80ms, <180ms,
 * worse), so the row still says how good the server is, not just what colour it is.
 */
@Composable
private fun LoadBars(pingMs: Int) {
    val filled = when {
        pingMs < 0 -> 0
        pingMs < 80 -> 3
        pingMs < 180 -> 2
        else -> 1
    }
    val color = when {
        pingMs < 0 -> RefBorder
        filled == 3 -> RefGreen
        filled == 2 -> RefLoadMed
        else -> RefLoadHigh
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(6.dp, 9.dp, 12.dp).forEachIndexed { index, height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) color else RefBorder)
            )
        }
    }
}

@Composable
private fun EmptyHint(allEmpty: Boolean, searching: Boolean, tab: HomeTab, onAdd: () -> Unit) {
    val (title, subtitle) = when {
        searching -> "Nothing matches" to "Try another name, city or country"
        allEmpty -> "No servers yet" to "Add a config or import a subscription"
        tab == HomeTab.CUSTOM -> "Nothing added by hand" to "Pasted and scanned configs land here"
        else -> "No servers yet" to "Add a config or import a subscription"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Add servers", onClick = onAdd)
            .padding(horizontal = ListPad, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).border(1.dp, RefBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PlusGlyph(color = RefTextMid, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = RefTextHi)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.5.sp, color = RefTextLow)
    }
}

// ── Usage card ────────────────────────────────────────────────────────────────
// .bottom-card: a floating strip over the list — traffic ring, two lines, chevron.
// The mockup's copy is a monthly quota; the app only knows the live session, so
// that is what the ring and the lines report.
@Composable
private fun UsageCard(
    state: HomeUiState,
    tone: ConnTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtext = when {
        state.connected ->
            "↓ ${speedLabel(state.downloadKBps)}   ↑ ${speedLabel(state.uploadKBps)}"
        state.connecting -> "Bringing the tunnel up"
        state.activeConfig == null -> "No server selected"
        else -> "Not connected"
    }
    val title = if (state.connected) {
        "Data used · ${formatElapsed(state.elapsedSec)}"
    } else {
        "Data used this session"
    }
    Row(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(CardCorner))     // --shadow-card
            .clip(RoundedCornerShape(CardCorner))
            .background(Brush.verticalGradient(listOf(RefElev2, RefElev1)))
            .border(1.dp, RefBorder, RoundedCornerShape(CardCorner))
            .clickable(onClickLabel = "Choose a server", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsageRing(
            bytes = state.sessionBytes,
            // Lit in the connection's own tone while the tunnel is up; a plain grey
            // the rest of the time, so the card never announces a state of its own.
            accent = if (state.connected) tone.ring else RefTextMid,
        )
        Spacer(Modifier.width(14.dp))              // .bottom-card gap
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                color = RefTextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtext,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RefTextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        Chevron(size = 16.dp, color = RefTextLow)
    }
}

/** .usage-ring — a 5dp arc over a track, with the session total in the middle. */
@Composable
private fun UsageRing(bytes: Long, accent: Color) {
    val (value, unit) = ringLabel(bytes)
    val fraction = (bytes.toFloat() / USAGE_RING_SCALE_BYTES.toFloat()).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(fraction, tween(600), label = "usageSweep")
    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            drawCircle(
                color = RefBorder,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
            if (sweep > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontSize = 9.5.sp,
                lineHeight = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
            Text(
                unit,
                fontSize = 9.5.sp,
                lineHeight = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

// ── Glyphs ────────────────────────────────────────────────────────────────────
// The mockup draws its chevron, account mark and wifi mark as inline SVG on a
// 24-unit grid at stroke-width 2–2.4. Material's equivalents are heavier and, for
// the account mark, filled, so these three are drawn on the same grid: `unit`
// below is one mockup unit, so the path numbers stay recognisable. The power mark
// is the exception — that one is Material's own Icons.Rounded.PowerSettingsNew,
// because a hand-drawn ring is exactly where the opening ended up on the wrong
// side of the circle once already.

@Composable
private fun Chevron(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = 2f * unit
        // M9 18l6-6-6-6
        drawLine(
            color,
            Offset(9f * unit, 18f * unit),
            Offset(15f * unit, 12f * unit),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(15f * unit, 12f * unit),
            Offset(9f * unit, 6f * unit),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** circle cx12 cy8 r4 over M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8 — head and shoulders. */
@Composable
private fun AccountGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2.4f * unit
        drawCircle(
            color = color,
            radius = 4f * unit - stroke / 2f,
            center = Offset(12f * unit, 8f * unit),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(4f * unit, 13f * unit),
            size = Size(16f * unit, 16f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Two signal arcs and a dot — the mockup's .network-left mark. */
@Composable
private fun WifiGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2f * unit
        // M5 12.5a11 11 0 0 1 14 0 — an r=11 arc centred just below the glyph
        drawArc(
            color = color,
            startAngle = 230.5f,
            sweepAngle = 79f,
            useCenter = false,
            topLeft = Offset(1f * unit, 9.98f * unit),
            size = Size(22f * unit, 22f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // M8 15.5a7 7 0 0 1 8 0
        drawArc(
            color = color,
            startAngle = 235.2f,
            sweepAngle = 69.6f,
            useCenter = false,
            topLeft = Offset(5f * unit, 14.25f * unit),
            size = Size(14f * unit, 14f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawCircle(color = color, radius = 1.3f * unit, center = Offset(12f * unit, 19f * unit))
    }
}

@Composable
private fun PlusGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(color, Offset(stroke / 2f, cy), Offset(size.width - stroke / 2f, cy), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, stroke / 2f), Offset(cx, size.height - stroke / 2f), stroke, StrokeCap.Round)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────
// The point of keeping Home stateless: every state renders from a plain data
// class, with no VPN, tunnel or device involved.
private fun previewConfig(
    name: String,
    cc: String,
    city: String,
    ping: Int,
    imported: Boolean = true,
) = SavedConfig(
    id = "$name-$cc", uri = "vless://preview", displayName = name, proto = "vless",
    address = "example.com", port = 443, network = "ws", sni = "example.com",
    countryCode = cc, city = city, pingMs = ping, geoResolved = true,
    isImported = imported, subscriptionId = if (imported) "sub" else null,
)

private val previewConfigs = listOf(
    previewConfig("Falkenstein", "DE", "Falkenstein", 12),
    previewConfig("Tulip Mania", "NL", "Amsterdam", 34),
    previewConfig("Paris Express", "FR", "Paris", 41),
    previewConfig("Stockholm Line", "SE", "Stockholm", 152),
    previewConfig("Helsinki Ice", "FI", "Helsinki", 58, imported = false),
    previewConfig("Fjord Runner", "NO", "Oslo", -1, imported = false),
)

@Preview(name = "Home · off", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenIdlePreview() {
    HomeScreen(
        state = HomeUiState(
            activeConfig = previewConfigs.first(),
            allConfigs = previewConfigs,
            networkName = "Mobile network",
            publicIp = "139.162.191.1",
        ),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {},
    )
}

@Preview(name = "Home · connecting", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenConnectingPreview() {
    HomeScreen(
        state = HomeUiState(
            activeConfig = previewConfigs.first(),
            allConfigs = previewConfigs,
            connecting = true,
            networkName = "Wi-Fi",
            publicIp = "139.162.191.1",
        ),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {},
    )
}

@Preview(name = "Home · connected", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenConnectedPreview() {
    val active = previewConfigs.first()
    HomeScreen(
        state = HomeUiState(
            activeConfig = active,
            allConfigs = previewConfigs,
            connected = true,
            elapsedSec = 3725L,
            downloadKBps = 812.0,
            uploadKBps = 96.0,
            totalDownloadBytes = 2_400_000_000L,
            totalUploadBytes = 176_000_000L,
            exitCountryCode = "DE",
            exitCity = "Frankfurt",
            exitGeoConfigId = active.id,
            networkName = "Wi-Fi",
            publicIp = "45.83.220.14",
        ),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {},
    )
}

@Preview(name = "Home · no servers", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(activeConfig = null, allConfigs = emptyList()),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {},
    )
}
