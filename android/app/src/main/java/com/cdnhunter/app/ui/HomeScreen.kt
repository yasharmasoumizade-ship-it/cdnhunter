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
//   • top bar     — hamburger → Settings, account glyph → Profile
//   • status row  — OFF/ON pill, "VLESS · 443", chevron → Settings
//   • connect bar — 88dp pill carrying the active server's city and country over
//                   the country's own flag, full-bleed, with a 100dp white power
//                   circle whose centre sits exactly on the bar's right edge; the
//                   bar is cut away behind it, leaving a 3dp ring of clearance
//   • network row — wifi glyph, transport label, public IP (tap to copy)
//   • browse card — 28dp-topped panel: Main / Custom pills, + and search buttons,
//                   then one row per server
//                   (flag, country · city, ping, three load bars)
//   • usage card  — floats over the list bottom: session-traffic ring, live
//                   speed, chevron → Locations
//
// HomeScreen() stays stateless about the VPN: it takes one HomeUiState snapshot
// plus event lambdas, so VpnTab() remains the single owner of connection state.
// The only state kept here is view state nothing else needs — which tab is
// selected, whether search is open, and the query.
//
// The flag is the connect bar's background and nothing else's: `.connect-bar-flag`
// fills the whole pill, full width and full height, under `.connect-bar-shade`.
// There is no flag disc behind the power button and no flag badge inside the bar.
// The power circle itself is a flat brushed-white disc in every state — the
// mockup's `.power-glow` is `display:none`.
//
// The only light on this screen is ambient: three soft directional sources — top,
// left and right — that fall onto the connect control. They are static gradient
// brushes, never [Modifier.blur], so the light stays crisp instead of smudged.
// Idle they are white; connected they turn green, tighter and a shade stronger.
//
// Connected is teal (--teal, #35d6b8) where the mockup says teal — the status
// pill (`.status-pill.on`), the usage ring's accent, the active row's dot — plus a
// dark-teal wash rising up the connect bar. The ambient light is the one green
// (--green, #34d17a), because a light source reads as light, not as a UI colour.
// Nothing tints while the tunnel is merely coming up.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
private val RefTeal = Color(0xFF35D6B8)        // --teal
private val RefGreen = Color(0xFF34D17A)       // --green
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PillInk = Color(0xFF05070C)        // .tab-pill.active text colour
private val PowerInk = Color(0xFF0C0E14)       // .power-btn svg colour

// ── Connected colour ──────────────────────────────────────────────────────────
// One colour says "the tunnel is up", and it is the mockup's own --teal. There is
// no tone system, no per-state palette and nothing that repaints while a
// connection is merely pending: coming up shows a spinner on the power face and
// the pill's "···" label, and that is all.
//
// [RefTeal] is used at full strength where the mockup uses it — the status pill's
// `.status-pill.on`, the usage ring's `conic-gradient(var(--teal) …)` and the
// active row's dot. The connect bar needs the same signal across a much larger
// area, and a full-bleed wash of a colour that bright reads as a warning light, so
// the bar gets the dark form of the same hue instead.
private val ConnectedTealDeep = Color(0xFF0B3D38)

// ── Dimensions — CSS px read as dp (the mockup's device is 390px wide) ─────────
private val ScreenPad = 20.dp        // .header padding: 4px 20px 14px
private val BarHeight = 88.dp        // .connect-bar height
private val PowerSize = 100.dp       // .power-btn, .power-btn-wrap
// The mockup masks the bar with `circle 52px at 100% 50%` around a 50dp-radius
// button, i.e. a 2dp hairline of space. 3dp is that hairline, one dp wider so it
// survives rounding on a low-density panel: the button still reads as fused to the
// bar's edge rather than parked next to it.
private val PowerCut = 53.dp
private val PanelCorner = 28.dp      // .browse-card border-radius
private val ListPad = 18.dp          // .server-row / .tab-row horizontal padding
private val FlagSize = 36.dp         // .server-flag
private val DividerStart = 52.dp     // .server-row::after left
private val CardCorner = 20.dp       // --radius-lg on .bottom-card
private val CardMargin = 14.dp       // .bottom-card margin / bottom
private val RingSize = 50.dp         // .usage-ring
private val RingStroke = 5.dp        // (50px ring − 40px inner disc) / 2
private val TapTarget = 48.dp        // touch floor; the mockup's boxes are 40px
// .tab-row's two trailing controls. The mockup draws them as 34px discs holding
// 18px glyphs, which is the smallest icon anywhere in the app; the rest of it
// draws action glyphs at 22–24dp (AppScreen's top-bar actions are 22dp, its
// settings rows 24dp, Home's own hamburger and account mark 25dp). These follow
// that instead of the mockup: a 22dp glyph on a 40dp disc, inside the same 48dp
// tap target.
private val ActionDisc = 40.dp
private val ActionGlyph = 22.dp

// ── Ambient light ─────────────────────────────────────────────────────────────
// Three soft light sources — above the screen, off its left edge, off its right
// edge — aimed at the connect control, so the flag reads as lit rather than as a
// flat fill. All three are plain gradient brushes: no [Modifier.blur], no render
// effect, nothing sampled per frame, which is also what keeps the connected state
// crisp instead of smudged (a blur at this radius is exactly what reads as muddy).
//
// Idle the light is white. Connected it is [RefGreen], with tighter falloff and a
// little more strength: the same three sources, sharpened.
private const val AMBIENT_TOP_IDLE = 0.085f
private const val AMBIENT_SIDE_IDLE = 0.055f
private const val AMBIENT_TOP_ON = 0.115f
private const val AMBIENT_SIDE_ON = 0.075f

/** The screen-level half of the light: what falls around the connect control. */
private fun DrawScope.drawAmbientLight(color: Color, connected: Boolean) {
    val top = if (connected) AMBIENT_TOP_ON else AMBIENT_TOP_IDLE
    val side = if (connected) AMBIENT_SIDE_ON else AMBIENT_SIDE_IDLE
    // Connected pulls the mid stop in and the tail down, so the falloff is a
    // shorter, cleaner ramp; idle spreads the same light over more of the header.
    val mid = if (connected) 0.34f else 0.46f
    val tail = if (connected) 0.74f else 0.88f

    fun cone(centre: Offset, radius: Float, peak: Float) = Brush.radialGradient(
        0.00f to color.copy(alpha = peak),
        mid to color.copy(alpha = peak * 0.42f),
        tail to color.copy(alpha = peak * 0.06f),
        1.00f to Color.Transparent,
        center = centre,
        radius = radius,
    )

    // Above: centred over the bar/button seam, so the brightest part of the wash
    // lands on the pill's top edge.
    drawRect(cone(Offset(size.width * 0.55f, -size.height * 0.30f), size.height * 1.30f, top))
    // Left and right: level with the connect bar, just outside the screen.
    drawRect(cone(Offset(-size.width * 0.16f, size.height * 0.56f), size.width * 0.80f, side))
    drawRect(cone(Offset(size.width * 1.16f, size.height * 0.52f), size.width * 0.80f, side))
}

/**
 * The bar-level half: the same three directions, read as highlights on the pill
 * itself rather than as a wash behind it. Top light first, then the two edges.
 */
private fun barTopLight(color: Color, connected: Boolean) = Brush.verticalGradient(
    0.00f to color.copy(alpha = if (connected) 0.20f else 0.15f),
    0.06f to color.copy(alpha = if (connected) 0.09f else 0.07f),
    (if (connected) 0.30f else 0.42f) to color.copy(alpha = 0.015f),
    1.00f to Color.Transparent,
)

private fun barEdgeLight(color: Color, connected: Boolean) = Brush.horizontalGradient(
    0.00f to color.copy(alpha = if (connected) 0.13f else 0.10f),
    (if (connected) 0.16f else 0.24f) to Color.Transparent,
    (if (connected) 0.84f else 0.76f) to Color.Transparent,
    1.00f to color.copy(alpha = if (connected) 0.15f else 0.11f),
)

/**
 * The country's flag as a rectangle, for the connect bar's full-bleed background.
 *
 * The bundled assets are HatScripts/circle-flags: a full 512×512 flag drawn under
 * `<mask id="a"><circle r="256"/></mask>`, which is why the artwork itself is
 * complete but everything outside the inscribed circle is transparent. Cropping
 * that circle into an 88dp-tall, ~300dp-wide pill lands entirely inside one band —
 * for Germany, the red one, which is the whole "the bar is solid red" bug.
 *
 * Dropping the mask element and the `mask="url(#a)"` reference gives back the
 * rectangular flag the paths already describe: black/red/gold bands corner to
 * corner. 248 of the 265 assets carry that exact mask; the other 17 have none and
 * pass through untouched. Anything unreadable falls back to the asset as shipped,
 * so a missing or oddly-shaped flag degrades to the circular version rather than
 * to nothing.
 */
private val CIRCLE_MASK = Regex("<mask\\s+id=\"a\">.*?</mask>", RegexOption.DOT_MATCHES_ALL)
private val CIRCLE_MASK_REF = Regex("\\s*mask=\"url\\(#a\\)\"")

private fun rectangularFlag(context: android.content.Context, countryCode: String): Any? {
    val cc = countryCode.lowercase().trim()
    if (cc.isBlank()) return null
    val path = "flags/$cc.svg"
    return runCatching {
        val svg = context.assets.open(path).use { it.readBytes().decodeToString() }
        val flat = svg.replace(CIRCLE_MASK, "").replace(CIRCLE_MASK_REF, "")
        java.nio.ByteBuffer.wrap(flat.toByteArray())
    }.getOrElse { "file:///android_asset/$path" }
}

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
// than a flat cut-out. This is the floor the flag sits on — where the flag's own
// artwork is opaque only the sheen above it shows, so the gradient matters most at
// the left end, under the shade layer that keeps the location text legible.
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
 * The mockup's two tab pills. "Main" is intentionally always empty (just the
 * add button lives there) -- "Custom" holds every server the app knows about,
 * subscription-imported or manually added alike.
 */
private enum class HomeTab(val label: String) {
    MAIN("Main"),
    CUSTOM("Custom"),
}

private fun HomeUiState.configsFor(tab: HomeTab): List<SavedConfig> = when (tab) {
    HomeTab.MAIN -> emptyList()
    HomeTab.CUSTOM -> allConfigs
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
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(HomeTab.MAIN) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val servers = remember(state.allConfigs, tab, query) {
        state.configsFor(tab).matching(query).byLatency()
    }
    val activeId = state.activeConfig?.id

    Box(modifier.fillMaxSize().background(PageGradient)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
            )
            BrowseCard(
                state = state,
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
                onAddServer = onAddServer,
                modifier = Modifier.weight(1f),
            )
        }

        // .bottom-card: sticky at the foot of the scroller, over the list
        UsageCard(
            state = state,
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
// Three layers: the black wash of `.header::before` over the page gradient, the
// ambient light above it (see [drawAmbientLight]), then the rows themselves. The
// gaps between rows are the mockup's own margins (4 / 12 / 22dp), spelled out one
// by one rather than smoothed into a single rhythm.
@Composable
private fun Header(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
) {
    // White idle, green connected — the light's own colour, animated so toggling
    // the tunnel reads as the room changing colour rather than as a repaint.
    val ambient by animateColorAsState(
        if (state.connected) RefGreen else Color.White,
        tween(520),
        label = "ambientLight",
    )
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().drawBehind { drawHeaderScrim() })
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawAmbientLight(ambient, state.connected) }
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = ScreenPad, end = ScreenPad, top = 4.dp, bottom = 14.dp)
        ) {
            TopBar(onOpenSettings = onOpenSettings, onOpenProfile = onOpenProfile)
            Spacer(Modifier.height(4.dp))          // .status-row margin-top
            StatusRow(state = state, onOpenSettings = onOpenSettings)
            Spacer(Modifier.height(12.dp))         // .power-row margin-top
            ConnectRow(
                state = state,
                ambient = ambient,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
            )
            Spacer(Modifier.height(22.dp))         // .network-row margin-top
            NetworkRow(state = state)
        }
    }
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
private fun StatusRow(state: HomeUiState, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusPill(connected = state.connected, connecting = state.connecting)
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
 * .status-pill — the mockup's own two-state pill and nothing more.
 *
 * Off is the base rule: `background: rgba(255,255,255,0.06)`, `border: 1px solid
 * var(--border)`, `color: var(--text-mid)`. On is `.status-pill.on` verbatim —
 * teal fill, teal border, teal text — and it applies only once the tunnel is
 * actually up. Coming up keeps the base style and only swaps the label to "···",
 * so a pending connection never claims to be connected.
 */
@Composable
private fun StatusPill(connected: Boolean, connecting: Boolean) {
    val label = when {
        connected -> "ON"
        connecting -> "···"
        else -> "OFF"
    }
    // .status-pill.on: rgba(53,214,184,0.14) over rgba(53,214,184,0.3), text #35d6b8
    val fill by animateColorAsState(
        if (connected) RefTeal.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
        label = "pillFill",
    )
    val edge by animateColorAsState(
        if (connected) RefTeal.copy(alpha = 0.30f) else RefBorder,
        label = "pillEdge",
    )
    val ink by animateColorAsState(if (connected) RefTeal else RefTextMid, label = "pillInk")
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
    ambient: Color,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(PowerSize)) {
        ConnectBar(
            state = state,
            ambient = ambient,
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
            onClick = onTogglePower,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ConnectBar(
    state: HomeUiState,
    ambient: Color,
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
        // `.connect-bar-flag`: the country's own flag as the pill's background,
        // full width and full height. The asset is a circle-flag, so it is flattened
        // to a rectangle first ([rectangularFlag]) and then stretched to the pill —
        // FillBounds, not Crop, because cropping a circular flag into a 3.4:1 box
        // shows one band of it and nothing else. Nothing sits on the flag but the
        // shade gradient below, which is `.connect-bar-shade`: it keeps the location
        // text legible while the flag reads at full strength toward the button end.
        if (countryCode.isNotBlank()) {
            val context = LocalContext.current
            val flag = remember(countryCode) { rectangularFlag(context, countryCode) }
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(flag)
                    // Coil keys a request by `data.toString()`, and a ByteBuffer's is
                    // "HeapByteBuffer[pos=0 lim=N cap=N]" — two countries whose SVGs
                    // happen to be the same byte length would share a cache entry and
                    // one would draw the other's flag. Key by the country instead.
                    .memoryCacheKey("flag-rect-${countryCode.lowercase().trim()}")
                    .diskCacheKey("flag-rect-${countryCode.lowercase().trim()}")
                    .crossfade(true)
                    .build(),
                imageLoader = getFlagImageLoader(context),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0.00f to RefElev1,
                    0.20f to RefElev1.copy(alpha = 0.92f),
                    0.42f to RefElev1.copy(alpha = 0.55f),
                    0.62f to RefElev1.copy(alpha = 0.12f),
                    0.80f to Color.Transparent,
                )
            )
        )
        if (state.connected) {
            // The bar's whole connected signal: a dark-teal wash rising bottom to
            // top, the large-area form of the status pill's teal. Static — nothing
            // on this screen animates to say "connected".
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.40f to ConnectedTealDeep.copy(alpha = 0.16f),
                        1.00f to ConnectedTealDeep.copy(alpha = 0.52f),
                    )
                )
            )
        }
        Box(Modifier.matchParentSize().background(BarSheen))
        // The bar's share of the ambient light: top first, then the two edges, in
        // the same colour and with the same falloff as the wash behind it.
        Box(Modifier.matchParentSize().background(barTopLight(ambient, state.connected)))
        Box(Modifier.matchParentSize().background(barEdgeLight(ambient, state.connected)))
        // `.location-block` (z-index:2) — the text, and nothing else: no badge, no
        // second read of the flag. The end inset clears the circular cut by 10dp so
        // the last glyph never crosses the missing material.
        Column(
            // .connect-bar padding is 0 20px
            Modifier
                .fillMaxWidth()
                .padding(start = ScreenPad, end = PowerCut + 10.dp),
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
// .power-btn — a 100dp brushed-white disc that looks the same in every state.
// Nothing rings it, nothing glows behind it: the mockup's `.power-glow` is
// `display:none`, so the only light here is the button's own drop shadow and the
// white gradient of its face. The one thing that ever moves is the press scale,
// plus a plain colourless spinner while the tunnel comes up.
//
// The mockup's four-part box-shadow, split by what Compose can draw:
//   0 16px 34px rgba(0,0,0,0.45)      ┐ the cast shadow — Modifier.shadow
//   0 4px 10px rgba(0,0,0,0.25)       ┘
//   inset 0 3px 4px rgba(255,255,255,0.95)  ┐ Compose has no inset box-shadow, so
//   inset 0 -10px 14px rgba(0,0,0,0.14)     ┘ these two are [PowerFaceSheen], a
//                                             bright top rim over a soft dark foot.
@Composable
private fun PowerCircle(
    connected: Boolean,
    connecting: Boolean,
    enabled: Boolean,
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
    onAddServer: () -> Unit,
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
            onAdd = onAddServer,
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
                        onAdd = onAddServer,
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
                    // The active server's dot in the same teal as the status pill,
                    // so "this is the one that's up" is one colour, not two.
                    dotColor = if (state.connected && isActive) RefTeal else RefTextMid,
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
    onAdd: () -> Unit,
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
        AddServerButton(onClick = onAdd)
        Spacer(Modifier.width(6.dp))
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

/** A plain "+" disc, matching SearchToggle's tap target/disc sizing, for adding a server. */
@Composable
private fun AddServerButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(TapTarget)
            .clip(CircleShape)
            .clickable(onClickLabel = "Add server", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(ActionDisc).clip(CircleShape).background(RefAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = RefAccent,
                modifier = Modifier.size(ActionGlyph),
            )
        }
    }
}

/** .search-btn — a 40dp disc that tints accent-blue while the field is open. */
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
            Modifier.size(ActionDisc).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(ActionGlyph),
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
            // The mockup's ring is `conic-gradient(var(--teal) …)`, so teal while the
            // tunnel is up; a plain grey the rest of the time, so the card never
            // announces a state of its own.
            accent = if (state.connected) RefTeal else RefTextMid,
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {},
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {},
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {},
    )
}

@Preview(name = "Home · no servers", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(activeConfig = null, allConfigs = emptyList()),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {},
    )
}
