package com.cdnhunter.app.ui

// ── HOME ──────────────────────────────────────────────────────────────────────
// Windscribe-style home screen, rebuilt as a stateless tree: HomeScreen() takes
// one HomeUiState snapshot plus event lambdas and holds no VPN state of its own,
// so VpnTab() remains the single owner of connection state and this file stays
// previewable on its own (see the @Preview pair at the bottom).
//
// Layout, top to bottom:
//   • top bar      — hamburger → Settings (left); 72dp aurora power button with
//                    an ON / … / OFF status label (right)
//   • server header— large "City + server name", flag, protocol pill, elapsed
//   • traffic card — one card, ONE flat pager: server info / download / upload
//   • quick switch — inline top-5 list of the other servers in the same group
// over a dark vertical gradient with a burgundy radial bloom behind the header.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.ui.components.TrafficChartCard

// ── Design tokens ─────────────────────────────────────────────────────────────
// One 4dp grid for the whole screen. The previous layout mixed 18/11/13.5dp
// values that never lined up with each other or with anything else in the app.
private val ScreenEdge = 16.dp          // cards / lists inset from the screen
private val HeaderEdge = 20.dp          // full-bleed header's own text inset
private val CardCorner = 20.dp
private val MinTouchTarget = 48.dp      // accessibility floor for every tap here
private val PowerButtonSize = 72.dp
private const val QUICK_SWITCH_LIMIT = 5

// Chart accents reuse the existing palette instead of re-declaring the hexes:
// AccentTeal == 0xFF64D2FF, YellowWarn == 0xFFFFD60A (see AppScreen.kt).
private val DownloadAccent = AccentTeal
private val UploadAccent = YellowWarn
private val ConnectingAccent = YellowWarn

/**
 * Everything Home draws, snapshotted from VpnTab() on each recomposition.
 *
 * Deliberately NOT annotated @Immutable: [downloadHistory] / [uploadHistory] are
 * the live snapshot lists the traffic poller appends to every second, so
 * promising immutability here would be a lie the compiler would act on.
 */
internal data class HomeUiState(
    val activeConfig: SavedConfig?,
    /** Other servers in the active server's own group — subscription or manual. */
    val quickSwitchConfigs: List<SavedConfig>,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val elapsedSec: Long = 0L,
    val downloadKBps: Double = 0.0,
    val uploadKBps: Double = 0.0,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val downloadHistory: List<Float> = emptyList(),
    val uploadHistory: List<Float> = emptyList(),
    // Geo of the live tunnel's exit node, measured through the tunnel itself
    // after connecting — only trusted for the config it was measured on.
    val exitCountryCode: String = "",
    val exitCity: String = "",
    val exitGeoConfigId: String = "",
) {
    private fun hasExitGeo(cfg: SavedConfig) =
        connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()

    /** Exit-node country once the tunnel has reported it, else the local guess. */
    fun countryCodeFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCountryCode else cfg.countryCode

    fun cityFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCity else cfg.city
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
    onQuickSwitch: (SavedConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cfg = state.activeConfig
    if (cfg == null) {
        // No servers saved yet: there is nothing to power on, and every block
        // below would render as an empty placeholder.
        Box(modifier.fillMaxSize().background(AnanasScreenBg)) {
            EmptyHomeState(onAdd = onOpenLocations)
        }
        return
    }

    Box(modifier.fillMaxSize().homeBackground()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                connected = state.connected,
                connecting = state.connecting,
                onOpenSettings = onOpenSettings,
                onTogglePower = onTogglePower,
            )
            // Scrolls instead of clipping: header + card + five quick-switch rows
            // is taller than a short phone screen, and the old fixed Column just
            // cut the last rows off.
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ServerHeader(cfg = cfg, state = state)
                TrafficCard(
                    cfg = cfg,
                    state = state,
                    onOpenLocations = onOpenLocations,
                    modifier = Modifier.padding(horizontal = ScreenEdge),
                )
                if (state.quickSwitchConfigs.isNotEmpty()) {
                    QuickSwitchCard(
                        configs = state.quickSwitchConfigs,
                        onSelect = onQuickSwitch,
                        onSeeAll = onOpenLocations,
                        modifier = Modifier.padding(horizontal = ScreenEdge),
                    )
                }
                Spacer(Modifier.height(ScreenEdge))
            }
        }
    }
}

/**
 * Dark vertical gradient plus a burgundy bloom centred on the top edge, behind
 * the server header. Both are painted in the draw phase, where the real measured
 * size is available — the previous version had to measure the screen into a state
 * variable first, and its off-canvas centre put the bloom outside the canvas.
 */
private fun Modifier.homeBackground(): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF0A0A0C), Color(0xFF050507), Color(0xFF030304))
        )
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF3D0A14).copy(alpha = 0.55f),
                Color(0xFF1A060A).copy(alpha = 0.30f),
                Color.Transparent,
            ),
            center = Offset(size.width / 2f, 0f),
            radius = size.width * 0.85f,
        )
    )
}

@Composable
private fun HomeTopBar(
    connected: Boolean,
    connecting: Boolean,
    onOpenSettings: () -> Unit,
    onTogglePower: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // A no-op under this non-edge-to-edge theme (the window already
            // excludes the status bar) but kept so the row stays correct if the
            // theme ever goes edge-to-edge; the explicit top padding is what
            // actually holds the row off the top edge today.
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Menu,
                contentDescription = "Settings",
                tint = AnanasText,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SmallPowerButton(
                connected = connected,
                connecting = connecting,
                onClick = onTogglePower,
            )
            Spacer(Modifier.height(4.dp))
            ConnectionStatusLabel(connected = connected, connecting = connecting)
        }
    }
}

@Composable
private fun ConnectionStatusLabel(connected: Boolean, connecting: Boolean) {
    val color = when {
        connected -> AnanasAccent
        connecting -> ConnectingAccent
        else -> AnanasMuted
    }
    val label = when {
        connected -> "ON"
        connecting -> "…"
        else -> "OFF"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── Power button ──────────────────────────────────────────────────────────────
// The 72dp top-bar version of the aurora ring. AuroraCanvasGlow expresses every
// layer as a fraction of ringSize, so the same aurora renders correctly here and
// at the 280dp hero size (PowerButton, kept in AppScreen.kt).
@Composable
private fun SmallPowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "smallPwr")
    val t1 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t1"
    )
    val t2 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "t2"
    )
    val t3 by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing)), label = "t3"
    )
    val breathe by infinite.animateFloat(
        0.95f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    val colorA by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF10B981)
            connecting -> Color(0xFFFFC93C)
            else -> Color(0xFF3B82F6)
        },
        animationSpec = tween(950), label = "colorA",
    )
    val colorB by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF34D399)
            connecting -> YellowWarn
            else -> Color(0xFF8B5CF6)
        },
        animationSpec = tween(950), label = "colorB",
    )
    val colorC by animateColorAsState(
        targetValue = when {
            connected -> Color(0xFF6EE7B7)
            connecting -> Color(0xFFFF9500)
            else -> Color(0xFF60A5FA)
        },
        animationSpec = tween(950), label = "colorC",
    )

    Box(Modifier.size(PowerButtonSize), contentAlignment = Alignment.Center) {
        AuroraCanvasGlow(
            colorA = colorA, colorB = colorB, colorC = colorC,
            t1 = t1, t2 = t2, t3 = t3, breathe = breathe,
            modifier = Modifier.size(PowerButtonSize),
            ringSize = PowerButtonSize,
        )
        Box(
            Modifier
                .size(PowerButtonSize * 0.72f)
                .clip(CircleShape)
                .background(AnanasCard)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                // State-aware so TalkBack announces what the tap will actually do.
                contentDescription = if (connected) "Disconnect" else "Connect",
                tint = when {
                    connected -> AnanasAccent
                    connecting -> Color(0xFFFFC93C)
                    else -> AnanasText
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Server header ─────────────────────────────────────────────────────────────
// "Frankfurt  Sausage Party": city in heavy weight, the server's own name light
// beside it, on the warm end of the burgundy bloom.
@Composable
private fun ServerHeader(cfg: SavedConfig, state: HomeUiState) {
    val countryCode = state.countryCodeFor(cfg)
    val city = state.cityFor(cfg)
    val cityLabel = city.ifBlank { countryCodeToName(countryCode).ifBlank { countryCode } }
    // Skip the second half when it would just repeat the first (a config named
    // after its own country used to render as "Germany  Germany").
    val showServerName = cfg.displayName.isNotBlank() &&
        !cfg.displayName.equals(cityLabel, ignoreCase = true)

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3D0A14).copy(alpha = 0.35f), Color.Transparent)
                    )
                )
            }
            .padding(horizontal = HeaderEdge, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = AnanasTextHi)) {
                    append(cityLabel)
                }
                if (showServerName) {
                    append("  ")
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Light,
                            color = AnanasText.copy(alpha = 0.85f),
                        )
                    ) {
                        append(cfg.displayName)
                    }
                }
            },
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
            // Long subscription names used to run off the screen unbounded.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CountryFlagBadge(countryCode, 18.dp)
            ProtocolPill(cfg)
            Text(
                when {
                    state.connected -> formatElapsed(state.elapsedSec)
                    state.connecting -> "Connecting…"
                    else -> "Tap to connect"
                },
                fontSize = 12.sp,
                color = AnanasMuted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Protocol tag. Shows the actual protocol (vless / trojan / vmess) — the previous
 * version showed `network`, i.e. the transport, so a perfectly ordinary VLESS
 * server over websocket rendered as "WS".
 */
@Composable
private fun ProtocolPill(cfg: SavedConfig) {
    val label = cfg.proto.uppercase().ifBlank { cfg.network.uppercase() }.ifBlank { "VLESS" }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AnanasBorder.copy(alpha = 0.8f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AnanasMuted,
            letterSpacing = 0.3.sp,
        )
    }
}

// ── Traffic card ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrafficCard(
    cfg: SavedConfig,
    state: HomeUiState,
    onOpenLocations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Status-coloured glow that breathes in step with the power button, layered
    // over the card's warm gold gradient rather than replacing it.
    val glow = rememberInfiniteTransition(label = "cardGlow")
    val breathe by glow.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cardGlowBreathe",
    )
    val glowColor by animateColorAsState(
        targetValue = when {
            state.connected -> Color(0xFF34D9A8)
            state.connecting -> Color(0xFFFFC93C)
            else -> Color(0xFF3B6FFF)
        },
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "cardGlowColor",
    )

    // Three pages once connected (info / download / upload), one when idle. Flat
    // on purpose: this was once a pager nested inside another pager's page, which
    // Compose Foundation does not support and which crashed on open.
    val pagerState = rememberPagerState(pageCount = { if (state.connected) 3 else 1 })

    // Page 0's structure never changes — only the text inside it — so its natural
    // height is constant. Measure it once and pin the pager to it in both states;
    // flipping between wrapContentHeight() and a hand-picked height is what made
    // the card visibly resize the moment the tunnel came up.
    var infoPageHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val pagerHeight =
        if (infoPageHeightPx > 0) with(density) { infoPageHeightPx.toDp() } else 196.dp

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                // Sizes come from the draw phase, so every radius is a real
                // fraction of the card instead of a hard-coded pixel count that
                // only looked right at one screen density.
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color(0xFF211C12), AnanasCard, Color(0xFF12100C))
                        )
                    )
                    // Calm gold sheen, top-left corner only.
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFD4AF6A).copy(alpha = 0.07f),
                                Color(0xFFD4AF6A).copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                            center = Offset.Zero,
                            radius = size.minDimension * 0.70f,
                        )
                    )
                    // Status glow, bottom-right corner, kept faint so the gold
                    // stays the dominant colour.
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.10f * breathe),
                                glowColor.copy(alpha = 0.045f * breathe),
                                Color.Transparent,
                            ),
                            center = Offset(size.width, size.height),
                            radius = size.minDimension * 0.85f,
                        )
                    )
                }
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.height(pagerHeight)) { page ->
                when (page) {
                    0 -> ServerInfoPage(
                        cfg = cfg,
                        state = state,
                        onClick = onOpenLocations,
                        modifier = Modifier.onSizeChanged {
                            if (it.height > 0) infoPageHeightPx = it.height
                        },
                    )

                    1 -> TrafficChartPage(
                        title = "DOWNLOAD",
                        icon = Icons.Rounded.ArrowDownward,
                        history = state.downloadHistory,
                        currentKBps = state.downloadKBps,
                        totalBytes = state.totalDownloadBytes,
                        accent = DownloadAccent,
                        isDownload = true,
                    )
                    else -> TrafficChartPage(
                        title = "UPLOAD",
                        icon = Icons.Rounded.ArrowUpward,
                        history = state.uploadHistory,
                        currentKBps = state.uploadKBps,
                        totalBytes = state.totalUploadBytes,
                        accent = UploadAccent,
                        isDownload = false,
                    )
                }
            }
        }
        if (state.connected) {
            PagerDots(selected = pagerState.currentPage, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ServerInfoPage(
    cfg: SavedConfig,
    state: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countryCode = state.countryCodeFor(cfg)
    val city = state.cityFor(cfg)
    val countryName = countryCodeToName(countryCode)
    val locationLine = when {
        countryName.isNotBlank() && city.isNotBlank() -> "$countryName · $city"
        countryName.isNotBlank() -> countryName
        !cfg.geoResolved -> "Resolving location…"
        else -> cfg.displayName
    }
    val statusLine = when {
        state.connected -> "${cfg.proto.uppercase()} · Active"
        cfg.pingMs >= 0 -> "${cfg.pingMs} ms · ${pingQualityLabel(cfg.pingMs)}"
        else -> "—"
    }

    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose another server", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CountryFlagBadge(countryCode, 32.dp)
                Column {
                    Text(
                        locationLine,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AnanasText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLine,
                        fontSize = 11.sp,
                        color = AnanasMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = AnanasFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        HorizontalDivider(color = AnanasDivider, thickness = 1.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TrafficTotal(
                icon = Icons.Rounded.ArrowDownward,
                label = "DOWNLOAD",
                value = if (state.connected) formatBytes(state.totalDownloadBytes) else "0 B",
                modifier = Modifier.weight(1f),
            )
            TrafficTotal(
                icon = Icons.Rounded.ArrowUpward,
                label = "UPLOAD",
                value = if (state.connected) formatBytes(state.totalUploadBytes) else "0 B",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrafficTotal(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AnanasMuted, modifier = Modifier.size(12.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnanasMuted,
                letterSpacing = 0.4.sp,
            )
        }
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AnanasTextHi,
            letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
private fun TrafficChartPage(
    title: String,
    icon: ImageVector,
    history: List<Float>,
    currentKBps: Double,
    totalBytes: Long,
    accent: Color,
    isDownload: Boolean,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        TrafficChartCard(
            title = title,
            icon = icon,
            dataPoints = history,
            currentValue = currentKBps.toFloat(),
            totalBytes = totalBytes,
            accentColor = accent,
            isDownload = isDownload,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PagerDots(selected: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(3) { index ->
            val isSelected = index == selected
            val color = when (index) {
                0 -> AnanasAccent
                1 -> DownloadAccent
                else -> UploadAccent
            }
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (isSelected) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color else AnanasBorder2)
            )
        }
    }
}

// ── Quick switch ──────────────────────────────────────────────────────────────
// Inline list of the top few alternatives, in place of the swipeable bottom sheet
// the old Home had. A plain Column, not a LazyColumn: the list is capped at five
// rows and sits inside a scrolling parent, where a lazy list of its own would
// have no bounded height to work with.
@Composable
private fun QuickSwitchCard(
    configs: List<SavedConfig>,
    onSelect: (SavedConfig) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(configs) { configs.take(QUICK_SWITCH_LIMIT) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(AnanasCard)
            .padding(horizontal = ScreenEdge)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "QUICK SWITCH",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnanasMuted,
                letterSpacing = 0.4.sp,
            )

            // Was a bare Text with .clickable — a ~16dp-tall tap target. Now a
            // full-height, rounded target that clears the 48dp minimum.
            Box(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "See all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnanasAccent,
                )
            }
        }
        shown.forEachIndexed { index, cfg ->
            QuickSwitchRow(
                cfg = cfg,
                onClick = { onSelect(cfg) },
                showDivider = index < shown.lastIndex,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickSwitchRow(cfg: SavedConfig, onClick: () -> Unit, showDivider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Connect to ${cfg.displayName}", onClick = onClick)
            .heightIn(min = MinTouchTarget)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountryFlagBadge(cfg.countryCode, 26.dp)
            Text(
                cfg.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AnanasText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Signal bars as well as the number, so the row matches how load is shown
        // in the Locations list instead of inventing a second visual language.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PingBars(cfg.pingMs)
            Text(
                if (cfg.pingMs >= 0) "${cfg.pingMs} ms" else cfg.network.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AnanasMuted,
            )
        }
    }
    if (showDivider) HorizontalDivider(color = AnanasDivider, thickness = 1.dp)
}

@Composable
private fun EmptyHomeState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(AnanasCard)
                    .border(1.dp, AnanasBorder, CircleShape)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add a config",
                    tint = AnanasMuted,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "No configs yet",
                fontSize = 15.sp,
                color = AnanasTextHi,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap + to add a trojan / vless / vmess config",
                fontSize = 12.sp,
                color = AnanasMuted,
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────
// The whole point of the stateless split: Home renders from a plain data class,
// so both states are inspectable without a VPN, a tunnel or a device.
private fun previewConfig(name: String, cc: String, city: String, ping: Int) = SavedConfig(
    id = name, uri = "vless://preview", displayName = name, proto = "vless",
    address = "example.com", port = 443, network = "ws", sni = "example.com",
    countryCode = cc, city = city, pingMs = ping, geoResolved = true,
)

private val previewQuickSwitch = listOf(
    previewConfig("Tulip Mania", "NL", "Amsterdam", 91),
    previewConfig("Bagel Bay", "US", "New York", 172),
    previewConfig("Sakura Line", "JP", "Osaka", 63),
)

@Preview(name = "Home · idle", widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenIdlePreview() {
    HomeScreen(
        state = HomeUiState(
            activeConfig = previewConfig("Sausage Party", "DE", "Frankfurt", 64),
            quickSwitchConfigs = previewQuickSwitch,
        ),
        onOpenSettings = {}, onOpenLocations = {}, onTogglePower = {}, onQuickSwitch = {},
    )
}

@Preview(name = "Home · connected", widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenConnectedPreview() {
    val active = previewConfig("Sausage Party", "DE", "Frankfurt", 64)
    HomeScreen(
        state = HomeUiState(
            activeConfig = active,
            quickSwitchConfigs = previewQuickSwitch,
            connected = true,
            elapsedSec = 3725L,
            downloadKBps = 812.0,
            uploadKBps = 96.0,
            totalDownloadBytes = 734_003_200L,
            totalUploadBytes = 41_943_040L,
            downloadHistory = listOf(120f, 300f, 640f, 812f, 500f),
            uploadHistory = listOf(20f, 60f, 96f, 74f),
            exitCountryCode = "DE",
            exitCity = "Frankfurt",
            exitGeoConfigId = active.id,
        ),
        onOpenSettings = {}, onOpenLocations = {}, onTogglePower = {}, onQuickSwitch = {},
    )
}

@Preview(name = "Home · no configs", widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(activeConfig = null, quickSwitchConfigs = emptyList()),
        onOpenSettings = {}, onOpenLocations = {}, onTogglePower = {}, onQuickSwitch = {},
    )
}
