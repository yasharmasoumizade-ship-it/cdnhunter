package com.cdnhunter.app.ui

// ── HOME ──────────────────────────────────────────────────────────────────────
// Premium dark VPN home: matte black, one blue accent, hairline borders, large radii. Layout,
// top to bottom:
//   • hero card    — the selected server's flag across the whole card, country name (and city/ping)
//                    on a soft foot scrim, glass menu chip and a connection-status chip on top
//   • search pill  — matte, accent hairline on focus
//   • server list  — flag · name · ping · signal bars · favourite, on a fixed grid; pull to refresh
//   • connect panel— inset floating panel holding the connect button (DISCONNECTED / CONNECTING /
//                    CONNECTED / DISCONNECTING) and, once connected, the public-IP row
//
// Every size, colour, radius and shadow comes from [AppDs] (the design system, further down).
//
// HomeScreen() stays stateless about the VPN: one HomeUiState snapshot plus event lambdas, so
// VpnTab() remains the single owner of connection state. Smart / Manual is switched by swiping
// the connect button up or down, or by its two accessibility actions (SmartMode.kt scores the
// servers). The only state kept here is view state: the search query and the brief
// DISCONNECTING hold between the tap and the tunnel reporting down.
//
// All motion respects the system's "remove animations" setting (see [rememberReduceMotion]).


import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.R
import com.cdnhunter.app.vpn.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random
// dev.chrisbanes.haze removed again -- the masthead is a flat, card-matching fill now
// (on request), not real-blurred glass, so nothing in this file needs it any more.

// ── Typography ───────────────────────────────────────────────────────────────
// Manrope (OFL-licensed, bundled as a variable font in res/font/manrope.ttf) replaces the
// system default everywhere on Home. It is a geometric, close-set sans with a genuinely
// premium feel at both small UI sizes and the large country headline — closer to the
// typeface a paid fintech or VPN product would commission than a system font ever reads.
// One Font() entry per weight actually used, each pinned to its own point on the variable
// font's weight axis via [FontVariation.Settings]: Compose then picks the entry that
// matches a Text's own `fontWeight` automatically, so True Manrope Bold renders where the
// code asks for [FontWeight.Bold] rather than the platform faking it by skewing Medium.
// On API < 26 (this app's floor is 24) the OS ignores the variation axis and falls back to
// the font's own default instance — a readable, if less differentiated, degradation.
private val LuxuryFont = AppFont  // shared with Settings/Profile — see DesignSystem.kt

/**
 * Home's type scale, replacing the ad hoc half-point sizes each composable used to pick for
 * itself (11.5/12.5/13.5/14.5/15.5sp...). Five steps, each with the weight it is always used
 * at, so a size implies a weight instead of the two being chosen separately at each call site.
 */
private val TypeCaption = AppType.Caption    // ping/city/timestamp captions
private val TypeBody = AppType.Body          // list rows, chips, buttons
private val TypeSubtitle = AppType.Subtitle  // usage card title, section heads
private val TypeTitle = AppType.Title        // dialog/sheet titles
private val TypeHeadline = AppType.Headline   // the country name

// ── Palette — navy/blue/green, our own tokens, Windscribe-directed ─────────────
// Shifted off the old neutral-grey slate toward the dark-navy + saturated-blue + green
// language actual Windscribe (github.com/Windscribe/Android-App, mobile module) uses —
// midnightNavy/primaryBlue/mintGreen in their AppColors.kt — but re-tuned as our own
// values rather than their literal hex, and kept inside this file's own token names so
// the rest of the file (which reasons about "RefBg", "RefAccent" etc.) needs no rewiring.
private val RefBg = Color(0xFF0A0A0C)          // was a neutral #0A0B0F; now navy-black

/**
 * The browse card's own base: the same luminance as [RefBg], a degree or two colder.
 *
 * This is the part of the frost that survives everywhere. [panelFrost]'s wash is gone by the
 * middle of the card, but a panel whose black is a colder black than the page's reads as glass
 * over the whole of its height — and at this distance from [RefBg] nothing about it is
 * nameable as a colour, which is the point.
 */
private val RefPanelBg = Color(0xFF01030A)

/**
 * The frost's colour: a pale icy blue, used only in [panelFrost] and never at any real
 * strength — 0.155 at the card's very top edge, under 0.02 within a hundred dp of it.
 *
 * The same family as [RefGlowOn], the room light behind the hero, which is what makes the two
 * halves of the screen look lit by one source: the hero's light falls onto the top of the
 * card, and the card is cold glass catching it.
 */
private val RefFrost = Color(0xFFA6DCFF)

/**
 * The lit edge of the frosted pane — [RefFrost] carried most of the way to white.
 *
 * It lives up here with the palette rather than down in the panel section it is named for
 * because it used to have two users on opposite sides of the file: the card's own top edge
 * ([drawPanelTopEdge]) and the mode pill's hairline, which was docked on that edge and had to be
 * lit by the same white or the seam read as two different materials meeting. The pill is gone
 * and the sole remaining user is the card, but the reason to keep the declaration here is
 * unchanged and worth stating: top-level properties initialise in file order, so a token shared
 * across sections has to be declared before the first of them, and moving it back down is how
 * the next shared use of it becomes a "must be initialized" compile error.
 */
private val PanelEdgeInk = Color(0xFFE8F6FF)

/**
 * The one hairline every small framed surface on this screen carries: brightest at the top,
 * nothing across the middle, faintly dark at the foot.
 *
 * It replaces three separate flat borders — a 0.09 white on the glyph chips, the same again
 * on the search field, and a variant on the mode pill — and the reason to unify them is not
 * tidiness. A flat border is light arriving from everywhere at once, which is the one thing
 * light never does; put four of them on a screen with a single overhead source
 * ([drawHeroAtmosphere], [PowerFaceSheen], [drawPanelTopEdge] all agree the light is above)
 * and every framed object quietly contradicts the room it is in. Graded top-to-bottom, the
 * same 1dp stroke reads as a physical edge catching that light.
 *
 * The peak is 0.16 rather than 0.09 because a gradient's average is what the eye takes as the
 * border's weight; the old flat value, graded, would have read as a fainter frame than before
 * rather than a better one. Deliberately white and not [PanelEdgeInk]: the icy tint is the
 * browse card's own signature, and spending it on every chip would make it mean nothing.
 *
 * Declared here, up in the palette, for the reason in the note above [PanelEdgeInk] — Kotlin
 * initialises file-level properties in source order, and this is used a thousand lines further
 * down.
 */
private val heroEdge = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.16f),
    0.30f to Color.White.copy(alpha = 0.08f),
    0.62f to Color.White.copy(alpha = 0.03f),
    1.00f to Color.Black.copy(alpha = 0.10f),
)
private val RefElev1 = Color(0xFF111114)       // --bg-elev-1 (navy-tinted, was neutral #0F1116)
private val RefElev2 = Color(0xFF17171B)       // --bg-elev-2 (navy-tinted, was neutral #15171E)
private val RefBorder = Color(0xFF26262C)      // --border (navy-tinted, was neutral #23262F)
private val RefTextHi = Color(0xFFF6F7F9)      // --text-hi
private val RefTextMid = Color(0xFF9A9CA6)     // --text-mid
private val RefTextLow = Color(0xFF6C6F7A)     // --text-low (bumped from #656B78 for contrast)

/**
 * The shadow every piece of hero type carries now that most of them have no surface under
 * them.
 *
 * A flag is not a background you can design against: it is an arbitrary image with an
 * arbitrary bright band wherever the country put one, and white type on white cloth is
 * unreadable no matter how heavy the weight. The two honest fixes are a container behind
 * each string or a shadow attached to it; the containers are what this pass removed, so
 * this is the one that is left.
 *
 * Deliberately soft and nearly black rather than tight and grey: an 8dp blur at 0.55 reads
 * as the type sitting slightly above the artwork, while a 2dp hard shadow reads as a
 * letterpress effect. The 2dp downward offset is the same direction as every other light
 * on this screen — see [drawHeroAtmosphere] — so nothing looks lit from two places.
 */
private val HeroInkShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)

/** A heavier cast for text meant to sit directly on the flag with nothing behind it -- no
 *  card, no wash. Deeper offset and wider blur than [HeroInkShadow] so the country/city
 *  headline stays legible over even a bright band of the flag artwork. */
private val HeadlineInkShadow = Shadow(
    color = Color.Black.copy(alpha = 0.70f),  // matched to the reference mockup exactly (was .75)
    offset = Offset(0f, 2f),                  // matched to the reference mockup exactly (was 3f)
    blurRadius = 10f,                         // matched to the reference mockup exactly (was 14f)
)
private val RefAccent = AppDs.Accent      // --accent — more saturated blue, Windscribe-directed (was #3B82F6)
/**
 * The connected/"good" colour: green, on request — a reversal of this file's earlier
 * "no green here" rule (see the note this replaces). Real Windscribe uses a neon green
 * (mintGreen/neonGreen, ~#55FF8A) for exactly this state; this is our own, deliberately
 * a shade deeper and less saturated than that, so it still clears contrast on [RefBg]
 * without the "highlighter on a flag" problem the earlier teal was chosen to avoid —
 * same worry, different colour, tuned down rather than avoided outright this time.
 */
private val RefLive = Color(0xFF34C77A)
/**
 * The room's light when the tunnel is up: blue, not the state's own teal.
 *
 * The disc reports the *state* in [RefLive] — mark and ring — and the light reports
 * that the thing is lit, so making them different hues is what stops the whole top of the
 * screen becoming one teal blob. Vivid and slightly over-bright on purpose: this is the
 * one light in the app allowed to be theatrical. It is thrown by [drawHeroAtmosphere]
 * across the whole backdrop; there is no longer a halo around the button itself.
 */
private val RefGlowOn = Color(0xFF1560E8)      // room light, moved with [RefAccent] (was #2563EB)
// The connecting state carries no colour of its own any more: the working spinner, the disc
// mark and the room's light are all monochrome (see [PowerRing], [phaseLight]). What used to be
// a yellow-orange "working" hue (RefWorking) and its on-white ink (RefWorkingInk) are gone.

/**
 * The same green, dark enough to read *on* white — used for the power button's mark.
 *
 * [RefLive] is tuned to glow on near-black; on the button's white face it is a pale,
 * thin mark that fails contrast. This is the same hue at roughly a third of the
 * lightness, which clears 4.5:1 on the disc's lightest stop.
 */
private val RefLiveInk = Color(0xFF116B36)     // dark-on-white ink for [RefLive]'s new green (was teal #07786B)
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PowerInk = Color(0xFF0C0E14)       // .power-btn svg colour
private val PowerGlyphInk = Color(0xFF0A0A0A)       // always-black power glyph, no phase colour
/** The OFF-state glyph ink. Pure black ([PowerGlyphInk]) read invisible against the button's
 *  own dark [RefElev2] fill — a black icon on a near-black disc gave no sense the button was
 *  even there, let alone tappable. This is a light, slightly cool ink instead: enough contrast
 *  to read as a struck (unlit) bolt at rest without borrowing the bright phase colours the
 *  connecting/connected states use. */
private val PowerGlyphOffInk = Color(0xFFC9D2E3)

/** Darker teal for the bolt glyph once CONNECTED — deeper than [ConnectTeal] so it reads as a
 *  settled, confident colour rather than the brighter, more energetic connecting tone. */
private val ConnectedBoltColor = Color(0xFF1F6E64)

// ── Hero surface ──────────────────────────────────────────────────────────────
// The top of the screen is not a panel any more. It is the page, with the country's flag
// drawn full-bleed across it — edge to edge, and up behind the system status bar, which
// is what [MainActivity]'s `setDecorFitsSystemWindows(false)` and the transparent
// `statusBarColor` in themes.xml are for. There is no card here: no rounded foot, no
// hairline frame, no cast shadow, no chrome-coloured face under the artwork. The flag IS
// the background, and the rows are laid on it.
//
// It is also drawn TALLER than the rows it carries, by [HeroBleed], so the artwork and its
// light pass behind the browse card's top edge instead of stopping at it. The card's first
// [PanelFade] of height is translucent (see [panelTopFade]), so the two read as one
// continuous surface with a long dissolve in the middle rather than as two floating
// pieces — see [HeroBackdrop] and [BrowseCard].
//
// What [ConnPhase] changes is the light on it, and only the light:
//
//   OFF        — white, wide and low: the room is lit, nothing is happening.
//   CONNECTING — white, tighter and stronger than idle, and the power ring's comet turns.
//   CONNECTED  — [RefGlowOn] blue, stronger again, with the crown wash over the top edge
//                at full strength. Blue rather than green because the ring and the mark
//                already carry [RefLive]: state is green, light is blue.
//
// The old *hairline* along the very top edge is gone. It was one 1.5dp line of
// near-full-strength colour across the whole screen with two more running down the sides,
// and against the flag it read as exactly what it was — a drawn seam — rather than as
// light. What replaces it is [drawHeroAtmosphere]'s crown: the same signal as a soft wash
// bleeding in over the top edge, with no edge of its own anywhere in it.


/** The app's chrome colour: `android:navigationBarColor`, and the window behind Compose. */
private val ChromeBg = Color(0xFF0B0B0D)

/**
 * How far the hero's *light* carries on below the last of its rows — i.e. how much of the
 * backdrop the browse card is drawn over. The flag's own reach is [FlagFootRise] and now runs
 * the other way — it stops short of the rows rather than past them.
 *
 * 40dp, and the rule behind the number has not changed even though the number has: a little
 * past [PanelFade], so the horizon bloom is still going where the card has already turned
 * solid, which is what leaves no line anywhere in the transition. The bloom's centre sits
 * exactly on this band's foot ([drawHeroAtmosphere]), so the two have to move together — it was
 * 138dp against a 132dp fade, then 88 against 84, and now 40 against 30. Set it *shorter* than
 * the fade and the last few dp of the dissolve would have nothing behind them; set it much
 * longer and the bloom's centre ends up buried under opaque paint.
 */
private val HeroBleed = 0.dp   // was 88dp (flag reached behind the browse card and hid its bottom corners); 0 now so the card ends where its rows end
                                 // card's masthead ([CardTopRoom], 48dp) with room to spare —
                                 // see [HeroFloatGap] (now decoupled from this) for why the
                                 // two cards still sit [HeroFloatGap] apart in *layout* even
                                 // though the artwork now visually overlaps that gap.

/** How long the phase crossfade takes — the ink, the light, and every surface. */
private const val PHASE_FADE_MS = 520

/** How fast the connecting spinner itself fades out once CONNECTED lands — quicker than
 *  [PHASE_FADE_MS] so it disappears promptly rather than lingering into the connected state. */
private const val SPINNER_FADE_MS = 220


// ── Connected colour ──────────────────────────────────────────────────────────
// One thing says "the tunnel is up": the ambient light turns blue. There is no
// tone system, no per-state palette, no pill, no pending state, and — deliberately
// — nothing painted over the connect bar's flag, so the flag's own colours are the
// real ones whether the tunnel is up or down.
//
// [RefLive] (green, on request — see its own doc comment) is used at full strength in the
// one place the mockup's teal used to live and the flag isn't underneath: the usage ring's
// `conic-gradient(...)`. The separate [RefTeal] token this used to point at is gone —
// one name for "the connected colour" instead of two.

// ── Dimensions — CSS px read as dp (the mockup's device is 390px wide) ─────────
private val ScreenPad = 20.dp        // .header padding: 4px 20px 14px
/**
 * The connect control's whole box: the disc plus the ring band around it.
 *
 * 140dp, up from the mockup's 100dp. It was 100 while it shared a row with the connect
 * pill and half of it overhung the pill's cut edge; centred and alone it is the hero's
 * one action, and at 100dp on the screen's axis it read as a medium-sized icon button
 * floating in a lot of space. 140 is a thumb-sized target — comfortably over the 48dp
 * floor with room for the ring's progress arc to be legible at arm's length — and still
 * leaves the headline above it as the largest *text*, which is the order the hero is
 * built to be read in.
 */
private val PowerSize = 116.dp  // bumped from 96dp on request — bigger, still one thumb-sized target

/** Height of the IP pill that visually grows out of the connect disc — see [IpMergedPill]. */
private val PowerPillHeight = 40.dp

/** The exact x-offset (from the disc's own centre) where the disc's circular edge is precisely
 *  [PowerPillHeight] tall — i.e. where a flat-edged pill of that height touches the circle
 *  exactly at its own top-left and bottom-left corners, with no gap and no overlap needed.
 *  See [IpMergedPill]'s tangent-join note. */
private val PillJoinX: Dp = run {
    val r = (PowerSize / 2).value
    val h = (PowerPillHeight / 2).value
    sqrt(r * r - h * h).dp
}

/** Extra slack pulled into [PillJoinX], on top of the exact tangent point — the disc shrinks a
 *  few percent on press ([POWER_PRESS_SCALE]), and an *exact* tangent computed for its full
 *  size then uncovers a sliver of the pill's own join edge for as long as it's held down. This
 *  is what keeps that edge hidden through the press, not just at rest. */
private val PillSafetyOverlap = 4.dp


private val PanelCorner = 22.dp      // .browse-card border-radius — curved further on request (was 18dp)
private val ListPad = 16.dp          // .server-row / .tab-row horizontal padding
/**
 * The server list's own flag, smaller than the connect bar's.
 *
 * The rows were made more compact, and the flag was the one thing in them with a fixed
 * size: at 36dp it set the row's floor height, so no amount of trimming the padding and
 * the type made the row shorter. 30dp is what lets the row come down to 58dp overall
 * while every part of it — flag, name, ping, load bars — is still full-size text at a
 * legible weight. It is also still well over the 24dp at which a circular flag stops
 * being identifiable.
 */
private val RowFlagSize = 27.dp
// RowCorner / RowGap removed: rows are back to a hairline-divided list (Windscribe-style),
// not individual rounded cards with a gap between them — see [ServerRow].

private val CardCorner = 22.dp       // --radius-lg on .bottom-card — matched to the reference
                                      // mockup's exact 22px hero radius (was 16dp)
private val CardMargin = 16.dp       // .bottom-card margin / bottom (snapped to the 4dp grid, was 14dp)

/**
 * The gap between the hero's flag card and the browse card below it, now that the hero is a
 * free-standing card rather than fused into the browse card's top edge (see [HeroBackdrop]'s
 * section comment). Fixed on its own now (used to be [HeroBleed] + 20dp, which meant the two
 * could never overlap no matter how far [HeroBleed] grew) — [HeroBleed] is deliberately much
 * taller than this now, so the flag card's own artwork extends *past* this gap and behind the
 * browse card's masthead, which is real glass now (see [BrowseCard]) rather than a solid fill,
 * so that overlap actually shows through, blurred.
 */
private val HeroFloatGap = 20.dp
private val RingSize = 50.dp         // .usage-ring
private val RingStroke = 5.dp        // (50px ring − 40px inner disc) / 2
private val TapTarget = 48.dp        // touch floor; the mockup's boxes are 40px
// .tab-row's two trailing controls. The mockup draws them as 34px discs holding
// 18px glyphs; these are bare glyphs — no disc, no tint behind them, like every
// other plain icon button in the app — at the 22dp the rest of the app draws action
// marks (AppScreen's top-bar actions are 22dp, its settings rows 24dp, Home's own
// hamburger and account mark 25dp), inside the same 48dp tap target.
private val ActionGlyph = 22.dp
// ── Mode swipe ────────────────────────────────────────────────────────────────
// How far the finger has to travel on the circle before the mode flips. Compose has
// already eaten ~8dp of touch slop by the time the first drag arrives, so this is
// deliberately short: far enough that a sloppy tap can't trigger it, close enough
// that the gesture completes well inside the button's own [PowerSize].
//
// Nothing on the circle draws this gesture any more. Two chevrons used to sit above
// and below it; on a screen whose top is now one large image they were the only marks
// on it that pointed at nothing the eye was looking for, and the mode they switch is
// already written next to the protocol. The gesture is also offered as two named
// accessibility actions on the button (see [PowerCircle]), which is the part of it a
// chevron could never have carried anyway.
private val ModeSwipeThreshold = 20.dp

// ── Lighting ──────────────────────────────────────────────────────────────────
// The hero is lit rather than tinted. Five plain-gradient layers, in this order:
//
//   1. the crown    — light over the very top edge of the screen, strongest in the first few
//                     percent. This is what says "connected" at a glance.
//   2. a key light  — one broad cone from above right of the power control, which is what makes
//                     the flag read as a lit surface rather than a picture.
//   3. two rim fills— off each side edge, so the artwork lifts off the page at the edges.
//   4. the horizon  — a bloom centred on the hero's foot, where the browse card's translucent
//                     top edge crosses it, so the seam is the brightest part of the transition
//                     rather than a line in it.
//   5. a vignette   — black, radial, centred high: pulls the corners down, holds the eye on the
//                     control, and keeps the status bar's own glyphs legible over the flag.
//
// No [Modifier.blur] and no render effect anywhere on this screen: a blur at these radii costs a
// full offscreen pass per frame and, at these alphas, reads as a smudge rather than as light.
//
// Every ramp is written with six or seven stops rather than three, and that is the difference
// between ambient light and a low-quality gradient: a gradient interpolates linearly between
// stops, so three stops over a 900px radius is three straight ramps meeting at two kinks, and on
// a near-black page the eye finds both the kinks and the 8-bit steps as concentric bands. Extra
// stops cost nothing at draw time.
//
// Idle the light is white and low; connecting it is the same white but tighter and stronger;
// connected [RefGlowOn], stronger still
// with a tighter falloff, so a state change reads as the room changing colour. The ceiling on
// these values is the ink over them — a crown wash past about 0.3 starts eating the contrast of
// the white labels at the top of the screen. The vignette is what keeps the corners under the
// brighter wash from turning grey.
private const val KEY_LIGHT_IDLE = 0.082f
private const val KEY_LIGHT_ON = 0.200f
private const val RIM_LIGHT_IDLE = 0.048f
private const val RIM_LIGHT_ON = 0.120f
private const val CROWN_LIGHT_IDLE = 0.086f
private const val CROWN_LIGHT_ON = 0.272f
// The horizon bloom is off at rest and only a whisper when connected: it used to rise behind
// the disc's foot and read as a halo ringing the connect control. That halo is gone regardless of
// where the disc sits, and the control casts its own shadow, so the bloom has nothing left to do at
// idle — a lit ring around a white disc on near-black is exactly the glow that was asked to go.
// Connected keeps a trace so the room still shifts colour.
private const val HORIZON_LIGHT_IDLE = 0.0f
private const val HORIZON_LIGHT_ON = 0.07f

/**
 * The whole atmosphere, drawn over the flag across the backdrop's full size — see the
 * section comment for what the five layers are and why each one is there. [color] is the
 * phase's own colour, crossfaded by [phaseLight]; [lit] raises every strength and tightens
 * the falloff for the two states that have something to report.
 */
private fun DrawScope.drawHeroAtmosphere(color: Color, lit: Boolean) {
    val key = if (lit) KEY_LIGHT_ON else KEY_LIGHT_IDLE
    val rim = if (lit) RIM_LIGHT_ON else RIM_LIGHT_IDLE
    val crown = if (lit) CROWN_LIGHT_ON else CROWN_LIGHT_IDLE
    val horizon = if (lit) HORIZON_LIGHT_ON else HORIZON_LIGHT_IDLE
    // Lit pulls the mid stop in and the tail down, so the falloff is a shorter, cleaner
    // ramp; idle spreads the same light over more of the screen.
    val mid = if (lit) 0.34f else 0.46f
    val tail = if (lit) 0.74f else 0.88f

    // Seven stops on an eased curve rather than four on a straight one — see the section
    // comment. The two shape values above still set where the light's mass sits; what the
    // extra stops buy is a ramp with no long linear section in it, which is the whole of the
    // difference between this reading as light and reading as a gradient with rings in it.
    fun cone(centre: Offset, radius: Float, peak: Float) = Brush.radialGradient(
        0.00f to color.copy(alpha = peak),
        mid * 0.45f to color.copy(alpha = peak * 0.78f),
        mid to color.copy(alpha = peak * 0.46f),
        (mid + tail) * 0.5f to color.copy(alpha = peak * 0.22f),
        tail to color.copy(alpha = peak * 0.085f),
        tail + (1f - tail) * 0.5f to color.copy(alpha = peak * 0.028f),
        1.00f to Color.Transparent,
        center = centre,
        radius = radius,
    )

    // 1. The crown. Its own stops are the whole point: 100% of the strength in the first
    //    pixel row, still 40% of it 18% down, a trace at the middle, nothing after. That
    //    is a light source above the phone, and there is no value of `y` at which it
    //    steps — which is what the hairline it replaces could never manage.
    val crownEnd = size.height * 0.60f
    drawRect(
        brush = Brush.verticalGradient(
            0.00f to color.copy(alpha = crown),
            0.04f to color.copy(alpha = crown * 0.78f),
            0.09f to color.copy(alpha = crown * 0.55f),
            0.16f to color.copy(alpha = crown * 0.36f),
            0.26f to color.copy(alpha = crown * 0.22f),
            0.40f to color.copy(alpha = crown * 0.11f),
            0.58f to color.copy(alpha = crown * 0.04f),
            1.00f to Color.Transparent,
            startY = 0f,
            endY = crownEnd,
        ),
        size = Size(size.width, crownEnd),
    )
    // 2. The key light: above and right of the connect control, which now sits low, so the
    //    brightest part of the wash falls across the middle of the flag and down onto the
    //    pill's top edge.
    drawRect(cone(Offset(size.width * 0.64f, size.height * 0.16f), size.height * 1.15f, key))
    // 3. The two rims, level with the connect bar and just outside the screen's edges.
    drawRect(cone(Offset(-size.width * 0.14f, size.height * 0.70f), size.width * 0.88f, rim))
    drawRect(cone(Offset(size.width * 1.14f, size.height * 0.66f), size.width * 0.88f, rim))
    // 4. The horizon, centred on the hero's own foot: the light the browse card's first
    //    [PanelFade] are lit from behind by.
    drawRect(cone(Offset(size.width * 0.50f, size.height), size.width * 1.05f, horizon))
    // 5. The vignette. Centred above the middle, so the top corners come down with the
    //    bottom ones and the status bar's glyphs keep something dark under them. Black at
    //    these alphas is the layer most prone to banding on an OLED panel, hence the seven
    //    stops: a straight ramp from 0 to 0.46 over most of the screen's width is exactly
    //    the case where 8-bit quantisation shows as rings.
    drawRect(
        Brush.radialGradient(
            0.00f to Color.Transparent,
            0.30f to Color.Black.copy(alpha = 0.02f),
            0.48f to Color.Black.copy(alpha = 0.06f),
            0.62f to Color.Black.copy(alpha = 0.12f),
            0.75f to Color.Black.copy(alpha = 0.20f),
            0.88f to Color.Black.copy(alpha = 0.31f),
            1.00f to Color.Black.copy(alpha = 0.46f),
            center = Offset(size.width * 0.50f, size.height * 0.40f),
            radius = size.width * 0.98f,
        )
    )
}


// `headerInk` — the phase's colour as a single [Color], crossfaded on [PHASE_FADE_MS] — used to
// live here. Its last caller was the country headline, which now takes a [Brush] instead so it
// can carry the flag's own hues ([headlineBrush]), and a brush is not something a Color helper
// can return. The phase-to-colour rule it held is stated inside that function:
// white in flight, [RefLive] up, the country's tint at rest — connecting no longer carries a
// colour of its own (see [headlineBrush] and [phaseLight]).


/**
 * A cast shadow's ambient and spot halves, as two colours rather than one.
 *
 * [Modifier.shadow] takes both, and giving each the platform default (opaque black at
 * the elevation's own alpha) is what makes an elevated dark surface look like it is
 * sitting in dirty grey fog. The ambient half is the light bouncing around the room —
 * near-black and wide; the spot half is the key light's own shadow, deeper, and the one
 * that gives the surface its direction. Tuned for near-black: the platform's own values
 * put a visible grey halo over this page's gradient. Used by the top bar's glyph chips
 * and by the power disc.
 */
private val HeroShadowAmbient = Color.Black.copy(alpha = 0.62f)
private val HeroShadowSpot = Color.Black.copy(alpha = 0.85f)

/** A lighter pair for cards that float over content rather than sit on it like a button —
 *  [UsageCard]. Cut further, on request, to move toward real Windscribe's own home screen,
 *  which uses no [Modifier.shadow] at all — this keeps a whisper of lift rather than going
 *  fully flat, since "our own design system" doesn't have to be a literal, shadow-free clone. */
private val CardShadowAmbient = Color.Black.copy(alpha = 0.14f)
private val CardShadowSpot = Color.Black.copy(alpha = 0.20f)


/**
 * Daily data-usage cap the Home ring measures against: 5 GB per local day.
 * The ring is driven by [HomeUiState.dailyUsageBytes] (a real, persisted local
 * daily total), not the per-session counter, so the fill reflects the whole day.
 */
private const val USAGE_DAILY_CAP_BYTES = 5L * 1024 * 1024 * 1024

// .device background — more stops than the mockup's four so the ramp has no
// visible banding on an OLED panel at these near-black values.
private val PageGradient = AppDs.PageGradient
// ── Header flag panel ─────────────────────────────────────────────────────────
// The flag is the top of the screen: one image, edge to edge, behind everything the
// header draws. See the file header for why it is Crop and what the three
// layers over it are for.

/**
 * How saturated the header flag is drawn.
 *
 * Flag specifications are ink on cloth, mixed to be seen in daylight from a distance. This used
 * to be pulled a fifth *below* full chroma so an OLED panel would not shout it — but the brief
 * now is a flag that reads vividly and punchy, so it sits a touch *above* full: the country's
 * own colours arrive distinct and confident rather than calmed toward a swatch. It is paired
 * with [HEADER_FLAG_CONTRAST] and both are done with a colour matrix on the image rather than by
 * fading it toward black, which would take the brightness with it and leave the flag looking
 * dirty. [HeroDepthScrim], the shade over the artwork, is untouched — this changes the artwork's
 * colour, not the shade over it.
 */
private const val HEADER_FLAG_SATURATION = 1.06f

/**
 * How much the flag's tones are expanded around mid-grey before [HeroDepthScrim] is applied.
 *
 * A contrast scale just over 1 pushes the darks down and the lights up around a 50% pivot, which
 * is what makes the colour bands read as *distinct* rather than as one even wash — the vivid,
 * "punchy" half of the brief that saturation alone does not buy. Kept modest (1.16) so the flag
 * stays a flag and does not clip its brightest field to flat white. Applied in the same
 * [ColorMatrix] as [HEADER_FLAG_SATURATION]; the scrim over the artwork is unchanged.
 */
private const val HEADER_FLAG_CONTRAST = 1.16f

/**
 * How much the artwork itself gives up before [HeroDepthScrim] is even applied.
 *
 * The flag is on in every state (see [HomeUiState.heroFlagCountry]) and it is the screen's
 * actual background rather than a panel's fill — it runs behind the status bar at the top
 * and behind the browse card's first rows at the foot, with no frame at any edge. A
 * background can afford to be a little more present than a floating image could: at 0.70,
 * with no card outline left to say "this is the hero", the artwork read as a grey
 * suggestion of a flag.
 *
 * 0.94 now. There is only ONE flag layer left (see [HeaderFlag]), so this single value is
 * the whole artwork's presence rather than the brighter half of a pair, and it is set
 * near-opaque on purpose: the brief for this screen is a flag that is unmistakably a flag
 * and *also* works as a backdrop. The legibility of the rows on top of it is not paid for
 * by dimming the artwork; it is paid for by [HeroDepthScrim], which is where it belongs,
 * because that scrim can be shaped — heavy exactly where text lands, light where the flag is
 * just flag.
 */
private const val HEADER_FLAG_ALPHA = 1.0f

/**
 * How far the flag's box stops **short of** the hero's last row.
 *
 * The sign is the point: this used to be `FlagBleed`, 12dp *past* the hero's foot, and it is
 * now above it. The reason is zoom, and zoom on this screen is pure geometry —
 * [ContentScale.Crop] scales the artwork to *cover* its box, so for a 3:2 flag in a box
 * `W × H` the fraction of the flag's width you can see is about `W / (H × 1.5)`. Box height
 * is the only lever there is.
 *
 * 12dp, down from 48. The 48 was clearance for the tab row, which used to be the hero's last
 * row and needed to sit on shade rather than on cloth. That row is inside the browse card now,
 * so there is nothing left down here to protect — and a 48dp rise with nothing on it is just a
 * dark shelf between the artwork and the card. The flag runs to within 12dp of the card's top
 * edge instead, where the card's own translucent fill ([panelTopFade]) takes over.
 */
private val FlagFootRise = 0.dp

/**
 * How far the flag's box runs **past** the seam, down behind the browse card's translucent head.
 *
 * The flag box would otherwise end exactly on the card's top edge, so nothing of the artwork sat
 * behind the card and its translucent top revealed only the floor/atmosphere below it. This gives
 * the flag a small foot *inside* the card's head, so the country's colour bleeds faintly through
 * the card's top edge and then dissolves out (the box's own [HeaderFlagBottomFade] lands in this
 * region). Kept small so the bleed is only near the top; [panelTopFade] goes fully opaque a little
 * below, so the flag is never visible down the body of the card.
 */
private val FlagCardBleed = 32.dp

/**
 * Zoom applied to the flag artwork itself, independent of its box's height.
 *
 * On top of the box's own Crop (see [HeroBackdrop]), this crops in tighter still. 1f is no
 * extra zoom; > 1f zooms in further.
 */
private const val FlagZoom = 1.0f  // brought back down to no-extra-zoom on request

/**
 * The single flag layer's bottom taper, applied inside its own box.
 *
 * Without it the artwork would end on a hard horizontal line. That line is behind the
 * opaque part of the browse card in every normal layout, but "normal layout" is not a
 * guarantee — a short screen, a large font scale or a fast scroll can all expose the last
 * few dp — and a seam that only appears sometimes is worse than one that always does.
 * [HeaderFlagFadeY] is the mask that shapes the layer as a whole; this is the one that
 * makes sure it finishes at nothing.
 */
private val HeaderFlagBottomFade = Brush.verticalGradient(
    0.00f to Color.Black,
    0.86f to Color.Black,
    0.94f to Color.Black.copy(alpha = 0.62f),
    1.00f to Color.Transparent,
)





// The flag crossfade. Enter is longer than exit, per the app's own motion rules, and
// the scale settle runs longer than either so the incoming flag is still easing when
// its fade has finished — that is what makes the change read as one image arriving
// rather than as two frames dissolved together.
private const val FLAG_FADE_IN_MS = 420
private const val FLAG_FADE_OUT_MS = 260
private const val FLAG_SETTLE_MS = 620

// ── Header flag scrim — removed ─────────────────────────────────────────────────
// Used to be a second vertical scrim drawn inside the flag's own masked layer, on top of
// [HeroDepthScrim] which already glasses the whole hero band. The two compounded (their
// alphas multiply, not add) into a much heavier veil than either was tuned for on its own,
// and it read as a flat shadow sitting on the flag rather than as depth. Legibility for the
// glyphs that sit over the artwork now comes from [HeroDepthScrim] alone.

/**
 * The horizontal half of the flag's alpha mask: full from the left edge, held nearly all
 * the way across, and eased only slightly by the right.
 *
 * It no longer reaches zero, and it barely falls at all now. The flag is full-bleed — the
 * artwork is the whole screen, edge to edge — so a mask that fell to nothing at the right
 * would leave a bare vertical strip of page down the side, which is exactly the "floating
 * panel" reading the card outline used to give; and 0.44, which is where this ended
 * before, was enough of a fall to be *seen* as a fall: a flag that visibly gave up on its
 * own right-hand third. Ending at 0.86 keeps a trace of direction — the left stays the
 * heavier side, which is where the light comes from — without the right edge reading as
 * cropped or unfinished. The colour is irrelevant; only the alpha is read,
 * by the [BlendMode.DstIn] pass in [HeaderFlag].
 */
private val HeaderFlagFadeX = Brush.horizontalGradient(
    0.00f to Color.Black,
    0.72f to Color.Black,
    1.00f to Color.Black,
)

/**
 * The vertical half of the mask: full from the very first pixel row, held down the screen,
 * and never taken to nothing.
 *
 * The top has no fade at all any more. It used to start transparent and reach full only
 * 16% down, which was the mask's way of keeping the artwork off the status bar — and with
 * the window now drawing under that bar (MainActivity's `setDecorFitsSystemWindows(false)`)
 * the same stops would have put a pale horizontal band across the top of the screen at
 * exactly the height of the clock: a seam, drawn by the very thing that was there to
 * avoid one. [HeroDepthScrim]'s heavy top stop protects the glyphs instead, by darkening
 * the flag rather than by removing it.
 *
 * The foot no longer reaches zero either, and that is the change that makes the artwork a
 * *background* rather than a panel at the top of one. This layer is the whole screen now
 * (see [HeroBackdrop]), so a mask that ended at Transparent would have put the flag's own
 * bottom edge across the middle of the page — the exact "the flag doesn't cover the
 * screen" reading it was drawn to avoid. It now holds 0.88 to the very last row instead of
 * falling to 0.52, so there is no point down the page where the flag can be said to stop;
 * what keeps the list legible over it is the browse card's own translucent fill
 * ([panelTopFade]) plus [HeroDepthScrim]'s heavier foot, both of which sit *over* the
 * artwork rather than removing it.
 */
private val HeaderFlagFadeY = Brush.verticalGradient(
    0.00f to Color.Black,
    0.60f to Color.Black,
    1.00f to Color.Black,
)

/**
 * What the header shows when there is no flag to show: a neutral slate wash,
 * diagonal so it reads as material rather than as one more of the screen's own
 * horizontal layers. It is never country-specific, which covers all three ways the
 * flag can be absent — the active server's country is unresolved, the country has no
 * bundled asset, or the SVG has not finished decoding. The real flag crossfades over
 * it the moment it lands.
 */
private val HeaderFlagFallback = Brush.linearGradient(
    0.00f to RefElev2,
    0.55f to Color(0xFF1B1F28),
    1.00f to RefBorder,
)

/**
 * The flag panel: the artwork, faded out on three sides by an alpha mask.
 *
 * The artwork is drawn into an offscreen layer, then
 * [HeaderFlagFadeX] and [HeaderFlagFadeY] are multiplied into that layer's alpha with
 * [BlendMode.DstIn]. Masking rather than scrimming the edges is what keeps the header
 * ambient: where the mask is zero the page's own gradient shows at exactly the value it
 * has everywhere else, so the panel has no edges of its own.
 *
 * [countryCode] is the only key. It changes when the user picks another server and,
 * once connected, when the tunnel reports the exit node's real country — both are the
 * same event as far as this panel is concerned, and both crossfade.
 */
@Composable
private fun HeaderFlag(countryCode: String, modifier: Modifier = Modifier) {
    val reduce = appReduceMotion()
    val context = LocalContext.current
    // Saturation and contrast in one matrix: chroma just over full so the colours read as the
    // country's own and confident, then a mild contrast expansion around mid-grey so the bands
    // stay distinct rather than washing into one field. Legibility over the artwork is
    // [HeroDepthScrim]'s job, applied separately over the whole hero band.
    val chroma = remember {
        val m = ColorMatrix().apply { setToSaturation(HEADER_FLAG_SATURATION) }
        val c = HEADER_FLAG_CONTRAST
        val t = (1f - c) * 128f
        m.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        ColorFilter.colorMatrix(m)
    }
    Box(
        modifier
            // The mask multiplies into the layer's alpha, so the layer has to be
            // composited offscreen first — a straight DstIn against the screen would
            // punch a hole through everything already drawn behind the header.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(HeaderFlagFadeX, blendMode = BlendMode.DstIn)
                drawRect(HeaderFlagFadeY, blendMode = BlendMode.DstIn)
            }
    ) {
        AnimatedContent(
            targetState = countryCode,
            transitionSpec = {
                if (reduce) {
                    (fadeIn(snap()) togetherWith fadeOut(snap()))
                        .using(SizeTransform(clip = false))
                } else {
                    (
                        fadeIn(tween(FLAG_FADE_IN_MS, easing = LinearOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 1.0f,
                                animationSpec = tween(FLAG_SETTLE_MS, easing = LinearOutSlowInEasing),
                            )
                        ).togetherWith(
                        fadeOut(tween(FLAG_FADE_OUT_MS)) +
                            scaleOut(targetScale = 0.99f, animationSpec = tween(FLAG_SETTLE_MS)),
                    ).using(SizeTransform(clip = false))
                }
            },
            label = "headerFlag",
            modifier = Modifier.fillMaxSize(),
        ) { code ->
            // Three models for one country, in priority order: a LOCAL bundled hero illustration
            // for the few countries we ship our own artwork for (currently only Sweden — see
            // [localHeroFlagRes]); flagcdn's true-aspect SVG for a well-known exit country; the
            // bundled circle-flags asset for everything else and for every failure of the fetch.
            // Only the bundled circle-flags one needs unmasking and de-bowing — see
            // [rectangularFlag] and FlagArtwork.kt's header. The local override does not fall back
            // to the network: it is always present in the APK.
            val local = remember(code) { localHeroFlagRes(code) }
            val remote = remember(code) { remoteFlagUrl(code) }
            var remoteFailed by remember(code) { mutableStateOf(false) }
            val bundled = remember(code) { rectangularFlag(context, code) }
            val flag = when {
                local != null -> local
                remote != null && !remoteFailed -> remote
                else -> bundled
            }
            if (flag == null) {
                Box(Modifier.fillMaxSize().background(HeaderFlagFallback))
            } else {
                val cc = canonicalCountryCode(code)?.lowercase() ?: code
                val key = when {
                    local != null -> "flag-local-$cc"
                    flag === remote -> "flag-cdn-$cc"
                    else -> "flag-rect-$cc"
                }
                // The bundled/flagcdn flags crop centred. Sweden's LOCAL artwork specifically is
                // a 2:1 landscape illustration whose subject — the Stockholm skyline and ship —
                // sits on the RIGHT half, so a plain centre-crop into this ~1.3:1 landscape box
                // would trim the far buildings off the right. A gentle right bias keeps the whole
                // skyline (and the ship) in frame while still leaving the yellow cross's vertical
                // bar visible; the only thing given up is a sliver of the left blue field. This is
                // keyed to Sweden alone, not to "any local asset": the other local flags (GB, US,
                // FR, DE, NL, IT, TR, QA) are plain flags whose design is already centred in their
                // own frame, so the same right-bias would just push them off-centre the same way —
                // which is what made the Union Jack's cross read as shifted left. Still uniform
                // Crop — nothing is stretched. See [FlagLayer].
                val flagAlignment = if (local != null && canonicalCountryCode(code) == "SE") {
                    BiasAlignment(horizontalBias = 0.15f, verticalBias = 0f)
                } else {
                    Alignment.Center
                }
                // ONE layer, and only one. There used to be two — a full-bleed wash with a
                // sharper, wider plate pinned over its top — which bought a less severe
                // crop at the cost of the same artwork being visibly drawn twice at two
                // alphas. Whatever that won on geometry it lost on honesty: on a light
                // flag the plate's foot read as a second flag ending. The crop is now
                // whatever [ContentScale.Crop] does with this box, and the box is a good
                // deal wider than a full screen because [HeaderFlag] is only as tall as
                // the hero's rows less [FlagFootRise] — see the call in [HeroBackdrop].
                FlagLayer(
                    model = flag,
                    cacheKey = key,
                    alpha = HEADER_FLAG_ALPHA,
                    chroma = chroma,
                    alignment = flagAlignment,
                    onError = { remoteFailed = true },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(HeaderFlagBottomFade, blendMode = BlendMode.DstIn)
                        },
                )
            }
        }
        // [HeaderFlagScrim] removed — it was compounding with [HeroDepthScrim] (the glass
        // layer over the whole hero band) and reading as a heavy shadow across the flag.
        // Legibility now comes from [HeroDepthScrim] alone.
    }
}

/**
 * The one drawn copy of the flag, filling whatever box [modifier] gives it.
 *
 * One rule for both sources: scale uniformly until the box is covered, clip the overhang.
 * [ContentScale.Crop], centred — no forced ratio, no unbounded width. This guarantees the
 * fixed hero box is always fully covered, never a letterbox gap showing [HeroFloor]'s own
 * near-black colour behind an under-filled area. [ContentScale.Fit] ('no crop') was tried
 * on request; it left exactly that gap for any flag wider-than-tall, which read as a broken
 * black bar rather than a design choice, so it was reverted.
 *
 * This is deliberately not FillBounds into a fixed box, which is what it was: that stretched
 * every source to one 4:3 rectangle, so the German bands were squeezed ~7% vertically and the
 * American canton came out visibly narrow — a distortion the eye finds immediately at this
 * size, and one that changed per country. Consistency of *shape* is not worth non-uniform
 * scaling. See FlagArtwork.kt's scaling note, which the badge shares.
 *
 * [cacheKey] is a correctness fix rather than a nicety: Coil keys a request by
 * `data.toString()`, and a ByteBuffer's is
 * "HeapByteBuffer[pos=0 lim=N cap=N]" — two countries whose SVGs happen to be the same byte
 * length would share a cache entry and one would draw the other's flag.
 */
@Composable
private fun FlagLayer(
    model: Any,
    cacheKey: String,
    alpha: Float,
    chroma: ColorFilter,
    alignment: Alignment = Alignment.Center,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    coil.compose.AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data(model)
            .size(FLAG_RENDER_PX)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            // AnimatedContent is already crossfading between two whole layers; a second
            // fade inside the incoming one only makes the first half of that transition
            // look like a load.
            .crossfade(false)
            .build(),
        imageLoader = getFlagImageLoader(context),
        contentDescription = null,
        // Back to Crop, on request — Fit ('no crop') left a real, visible letterbox gap at
        // the top of this fixed-height box for any flag wider-than-tall (most of them): the
        // image only fills the box's WIDTH, so its Fit-scaled height came out shorter than
        // the box, and the leftover gap showed HeroFloor's own near-black colour behind it —
        // exactly the solid "black bar at the top, menu and country name sitting on it"
        // reported. Crop guarantees the box is always fully covered, no gap possible; the
        // trade-off (going back to not always showing 100% of every flag) is the one this
        // reverts.
        contentScale = ContentScale.Crop,
        alignment = alignment,
        alpha = alpha,
        colorFilter = chroma,
        filterQuality = FilterQuality.High,
        // flagcdn unreachable, or no such flag there: fall back to the bundled asset
        // rather than to the neutral wash, which is what every country outside
        // VPN_FLAG_COUNTRIES already draws.
        onError = { onError() },
        error = null,
        modifier = modifier,
    )
}

// ── Glass ─────────────────────────────────────────────────────────────────────
// Gone, and worth a note where it was.
//
// The hero used to lay one glass surface over the flag — a translucent floor, a top-light and
// (until recently) a hairline — and by the end it had exactly one user: the chip around the
// public IP. That chip is now bare text (see [MetaRow]), so the primitive and its three
// tokens went with it. What is left up here is type, one disc, one hand-drawn menu mark, and
// the artwork; the only framed surface on the screen now is the browse card itself.
//
// If something up here ever needs a surface again, the thing to reach for is [heroEdge] over a
// translucent fill — the same lit hairline the browse card's own edge uses.

/**
 * Everything Home draws, snapshotted from VpnTab() on each recomposition.
 *
 * [allConfigs] is every saved server — the browse list groups and filters it;
 * [activeConfig] is the one the power button acts on. Both arrive already
 * loaded: Home never touches disk.
 *
 * [mode] is which way [activeConfig] was arrived at, not a second selection:
 * in [ConnectMode.MANUAL] it is the row the user tapped, in [ConnectMode.SMART]
 * it is whatever VpnTab's own scoring currently rates best. Home only reports the
 * mode and offers the gesture that changes it; the choosing happens in VpnTab.
 */
internal data class HomeUiState(
    val activeConfig: SavedConfig?,
    val allConfigs: List<SavedConfig> = emptyList(),
    val connected: Boolean = false,
    /**
     * A connection attempt is in flight: dialled but not up yet, including the
     * backoff between auto-reconnect retries. Mirrors
     * [com.cdnhunter.app.vpn.CdnVpnService.isConnecting], which the service clears
     * on every path out of an attempt, so it is never both this and [connected].
     */
    val connecting: Boolean = false,
    val mode: ConnectMode = ConnectMode.MANUAL,
    val elapsedSec: Long = 0L,
    val downloadKBps: Double = 0.0,
    val uploadKBps: Double = 0.0,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    /**
     * Bytes moved through the tunnel *today* (local time), as accumulated by
     * [com.cdnhunter.app.vpn.AppSettings.addUsageBytes] and read back on each tick.
     * Unlike [sessionBytes] this survives connect/disconnect and app relaunch, so
     * Home's usage ring shows a real daily figure against [USAGE_DAILY_CAP_BYTES].
     * Best-effort local estimate — it cannot count traffic that flowed while the app
     * process was dead. See the poll loop in AppScreen.kt.
     */
    val dailyUsageBytes: Long = 0L,
    // Geo of the live tunnel's exit node, measured through the tunnel itself
    // after connecting — only trusted for the config it was measured on.
    val exitCountryCode: String = "",
    val exitCity: String = "",
    val exitGeoConfigId: String = "",
    /** "Wi-Fi" / "Mobile data" / "" — the transport this device is on right now. */
    val networkName: String = "",
    /** Public IP: the tunnel's exit IP when connected, this device's when not. */
    val publicIp: String = "",
    /**
     * A public-IP lookup is in flight.
     *
     * The hero's address line has three states, not two, and this is what separates the two
     * that look alike: [publicIp] blank *because we are still asking* shows a neutral "—"
     * placeholder (no status word), blank *because every provider failed* is "Unavailable" with a
     * tap to retry. Without this flag the two would be indistinguishable, which is how a total
     * lookup failure came to look like the still-loading placeholder. See [IpCard].
     */
    val ipLookupPending: Boolean = false,
    /**
     * The country whose flag the hero last washed, as persisted by
     * [com.cdnhunter.app.vpn.AppSettings.lastFlagCountry].
     *
     * A cold start has [allConfigs] loaded from disk before geo resolution has run, so
     * [headerCountryCode] is blank for the first second or two of every launch and the
     * panel would open bare and then flash a flag in. This is the only reason this field
     * exists: it is a display cache, read only by [heroFlagCountry] and only when the
     * live code is not there yet. Nothing routes by it.
     */
    val lastFlagCountry: String = "",
    /** A ping sweep of the browse list is in flight — drives the pull-to-refresh
     *  indicator. See [onRefreshPings] at Home's own call site. */
    val refreshingPings: Boolean = false,
) {
    private fun hasExitGeo(cfg: SavedConfig) =
        connected && exitGeoConfigId == cfg.id && exitCountryCode.isNotBlank()

    /** Exit-node country once the tunnel has reported it, else the local guess. */
    fun countryCodeFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCountryCode else cfg.countryCode

    fun cityFor(cfg: SavedConfig): String =
        if (hasExitGeo(cfg)) exitCity else ""

    /** The country behind the whole header: the active server's, or none. */
    val headerCountryCode: String
        get() = activeConfig?.let { countryCodeFor(it) }.orEmpty()

    /**
     * The country the hero panel washes its top with, in **every** phase — or "" for no
     * wash at all.
     *
     * The rule is about servers, not about connection state: with no saved server the
     * panel is plain chrome, and with at least one it always shows that server's flag,
     * off, connecting or connected alike. A flag that appeared only once the tunnel was
     * up made the panel change character on connect; the same flag in all three states
     * makes connecting a change of *light* on a panel that was already the right
     * country, which is the point of the redesign.
     *
     * Preference order is live-then-cached: [headerCountryCode] is the active config's
     * own country (the exit node's once the tunnel has reported it, so connecting always
     * settles on the true flag rather than leaving the geo guess up), and
     * [lastFlagCountry] only fills the launch-time gap before that resolves. With no
     * configs both are ignored — that is the EMPTY state, and it is deliberately checked
     * first so deleting the last server clears the wash immediately.
     */
    val heroFlagCountry: String
        get() = if (allConfigs.isEmpty()) "" else headerCountryCode.ifBlank { lastFlagCountry }

    /**
     * The one address the network row states, or "" when there isn't one yet.
     *
     * Always just [publicIp] -- deliberately NOT gated on [connecting]. It used to blank
     * to "" for the whole connecting phase, which forced the readout through UNAVAILABLE/
     * CHECKING on every single connect and disconnect, even when a perfectly valid address
     * was already on screen. That meant IpCard never stayed in READY across a transition,
     * so RollingIp's digit-by-digit roll never had a continuous value to animate between --
     * every transition looked like an instant blank-then-refill instead of a roll. Now the
     * previous address (disconnected: this device's own; connected: the prior exit IP) stays
     * up the whole time a fresh lookup is in flight, and is simply replaced in place once
     * [publicIp] resolves to the new one -- which is what lets IpCard stay in IpKind.READY
     * and RollingIp actually animate the digits.
     *
     * Blank means "no address to state" (e.g. first launch, before any lookup has ever
     * resolved); [MetaRow] decides what to say about that, and the answer depends on
     * [ipLookupPending].
     */
    val displayIp: String
        get() = publicIp

    val sessionBytes: Long get() = totalDownloadBytes + totalUploadBytes

    /** Which of the header's three surfaces to draw. */
    val phase: ConnPhase
        get() = when {
            connected -> ConnPhase.CONNECTED
            connecting -> ConnPhase.CONNECTING
            else -> ConnPhase.OFF
        }
}

/**
 * The three states the hero draws, and the only thing that picks between its
 * surfaces. Derived from [HomeUiState.connected] / [HomeUiState.connecting] rather
 * than stored, so there is one source of truth and no fourth state to get stuck in.
 */
internal enum class ConnPhase { OFF, CONNECTING, CONNECTED }

private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

/** Whether [host] is already an address rather than a name to be resolved. */
private fun isIpLiteral(host: String): Boolean {
    val trimmed = host.trim().removeSurrounding("[", "]")
    // Two colons is the shortest possible IPv6 literal ("::"), and no hostname the
    // parser can produce contains one at all.
    return IPV4.matches(trimmed) || trimmed.count { it == ':' } >= 2
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


// ── Hero backdrop ─────────────────────────────────────────────────────────────
// The artwork and the light, and nothing else. Drawn as a sibling *behind* everything rather
// than as the hero's background — sized to wrap its own content (bandHeight tall) rather than
// the whole screen, so [HomeScreen]'s clip + margin around it actually bounds real artwork,
// not empty space. Two heights are at work:
//
//   the flag          — edge to edge *within the card's own margin*, and vertically from the
//                       card's top to [FlagFootRise] *short of* the hero's last row. Height is
//                       zoom here, so a shorter box is a less cropped flag; its last 14%
//                       dissolves rather than stopping.
//   the light + floor — a band [bandHeight] tall at the top: the hero's rows plus [HeroBleed].
//                       [HeroBleed] is what the card's own foot rests [HeroFloatGap] above the
//                       browse card for now (see [HomeScreen]) — it no longer positions a
//                       fused-seam bloom, since [drawHeroAtmosphere] is unused here.
//
// This IS now a card: [HomeScreen] gives it [CardMargin] on both sides and clips it to
// [RoundedCornerShape] on all four corners, floating [HeroFloatGap] above the browse card
// rather than fusing into it. The layers inside still stack the same way, from the back:
// [ChromeBg] over the top band → the flag → [drawHeroAtmosphere] over the band (unused). The
// flag crossfades on [PHASE_FADE_MS], as does the light's colour.

/**
 * What the artwork is composited over: the app's chrome colour under the rows, gone by
 * the foot of the bleed.
 *
 * The taper matters as much as the colour. Opaque all the way down and the backdrop would
 * be a black rectangle behind the browse card's translucent top — i.e. the seam this whole
 * arrangement exists to remove, just moved [HeroBleed] lower. Ending at nothing means the
 * last thing under the card's top edge is the page's own gradient, which is what the rest
 * of the card is over too.
 */
private val HeroFloor = Brush.verticalGradient(
    0.00f to ChromeBg,
    0.62f to ChromeBg,
    0.82f to ChromeBg.copy(alpha = 0.55f),
    1.00f to Color.Transparent,
)

/**
 * The dark glass laid over the artwork, under the light: the layer that turns the flag from a
 * bright field into artwork seen *through* tinted glass, and lets everything on the hero be read
 * on top of an arbitrary country.
 *
 * This is now the ONLY dark layer over the artwork — a second scrim used to live inside the
 * flag's own masked layer as well, and the two compounded (alphas multiply, not add) into a
 * flat shadow far heavier than either was tuned for alone. Removed; this one carries all of
 * it now, covering the whole band, flag or no flag, as an *even* dark veil rather than a
 * bright-through-the-middle one:
 *
 *  - ~0.46 at the top, behind the hamburger and the 34sp headline, where a flag's top stripe is
 *    at its brightest and least negotiable;
 *  - held around 0.35–0.38 through the body, so the whole flag sits behind a consistent moody
 *    tint — the connect disc no longer lives here (it docks on the card's foot now), so there is
 *    no bright window to keep for it;
 *  - back up to ~0.46 at the foot, grounding the seam the card's top edge sits on.
 *
 * Seven stops for a shallow ramp, and the count is the point: a three-stop version of this bands
 * visibly on a dark flag, because 8-bit alpha over near-black has very little room between steps.
 *
 * Black rather than a tinted navy, and it matters: any hue here would sit on top of the flag's
 * own and turn every country slightly the same colour, which is exactly what the removed
 * `--green` did. The cool *frost* tint that sells the glass is a separate, far lighter layer
 * ([HeroGlassFrost]) drawn over this, so the country's own colour survives it.
 */
private val HeroDepthScrim = Brush.verticalGradient(
    // Cut hard, on request: the flag was reading shadowed/effect-laden with this at its old
    // strength stacked under [HeaderFlagScrim] (now removed). What is left is only enough,
    // right at the very top, to keep the status-bar clock and glyphs legible over a bright
    // flag; by 20% down it is essentially gone, and it stays negligible for the rest of the
    // band so the flag's own colour — not a veil over it — is what actually reads.
    0.00f to Color.Black.copy(alpha = 0.28f),
    0.10f to Color.Black.copy(alpha = 0.14f),
    0.20f to Color.Black.copy(alpha = 0.05f),
    1.00f to Color.Black.copy(alpha = 0.05f),
)

/**
 * The cool half of the frosted glass: a very faint icy wash over the whole hero band, brightest
 * along the top where light would catch the pane, falling to almost nothing by the foot.
 *
 * This is what makes [HeroDepthScrim]'s dark veil read as *glass* rather than as a dimmer switch:
 * cold light collecting at the pane's head, the same trick [panelFrost] plays on the browse card,
 * and the same reason there is no blur here. Kept under 0.05 alpha throughout — any stronger and
 * it stops being a frost on the country's colour and starts being its own blue field.
 */
private val HeroGlassFrost = Brush.verticalGradient(
    0.00f to RefFrost.copy(alpha = 0.05f),
    0.30f to RefFrost.copy(alpha = 0.028f),
    0.70f to RefFrost.copy(alpha = 0.012f),
    1.00f to Color.Transparent,
)

/**
 * The other half of the shade: a vignette at the two vertical edges.
 *
 * A purely vertical scrim flattens the hero — every pixel on a row is shaded identically, so
 * the band reads as a photo with a filter on it. Pulling the corners down a little gives the
 * artwork a centre, which is where the headline, the address and the button all are, and it
 * quietly holds the top bar's outermost glyphs off a bright edge of cloth.
 *
 * Very shallow on purpose: 0.22 at the extreme edge, nothing at all across the middle 44%.
 * Anything stronger and it stops being depth and starts being a frame.
 */
private val HeroDepthEdge = Brush.horizontalGradient(
    0.00f to Color.Black.copy(alpha = 0.22f),
    0.12f to Color.Black.copy(alpha = 0.08f),
    0.28f to Color.Transparent,
    0.72f to Color.Transparent,
    0.88f to Color.Black.copy(alpha = 0.08f),
    1.00f to Color.Black.copy(alpha = 0.22f),
)

// ── Flag top fade — removed ─────────────────────────────────────────────────────
// The flag used to darken into the very top of the screen (a six-stop black veil, [108dp]
// deep) so the system status-bar glyphs had a field and the artwork didn't start at full
// chroma against the clock. That field now comes from [HeroDepthScrim]'s heavy head (~0.52
// black at the top), which darkens the whole band evenly as dark glass rather than as a
// separate veil — so the veil, its depth constant and its brush are gone.



/**
 * The vignette's stops, centred a little above the middle of the band — around the power disc.
 *
 * Kept as a list rather than a brush because a radial gradient needs the draw scope's own size
 * for its centre and radius, so the brush can only be built inside [HeroBackdrop]'s draw pass.
 */
private val HeroVignetteStops = listOf(
    Color.Transparent,
    Color.Transparent,
    Color.Black.copy(alpha = 0.10f),
    Color.Black.copy(alpha = 0.30f),
)

// ── Header ────────────────────────────────────────────────────────────────────
// The hero's content only. Everything behind it is [HeroBackdrop]'s, drawn by [HomeScreen] as
// a sibling so it can be taller than this; what this column reports, via [Modifier.onSizeChanged]
// at the call site, is how tall that backdrop needs to be.
//
// One centred column, read top to bottom:
//
//   [the menu]       → [MenuButton], top-left on the flag
//   where am I?      → [CountryHeadline]
//   as what address? → [MetaRow]
//
// The action — [PowerCircle] — is no longer in this column: [HomeScreen] draws it as an overlay
// docked on the browse card's top edge, so this column ends by reserving the flag its upper half
// sits over ([HeroDockWell]).
//
// The list's own controls (add, search) are *not* here either — they are the first row inside
// [BrowseCard], which is where they belong now that the card announces its own top edge.
//
// What changes between the three [HomeUiState.phase] values is the light, not any surface:
// the atmosphere changes colour and tightens ([drawHeroAtmosphere]), the ring reports, the ink
// follows. Nothing slides and the flag wash is on in all three states.
//
// This column is the topmost content now — the black status bar that used to own the system
// inset is gone — so it carries [statusBarsPadding] itself and the backdrop behind it runs on
// to the top of the screen, under the system clock.

/** The open flag between the top row (menu + country plate) and the docked connect disc.
 *
 *  The address and the country name no longer stack down the centre of this column — the
 *  country sits top-right in [CountryHeadline] and the public IP is an overlay card drawn by
 *  [HomeScreen] over the lower-left of the flag. So this column's middle is bare artwork now,
 *  and this token is how much of it shows above the disc's dock well. */
private val HeroFlagSpace = 40.dp

/** The breathing room the hero holds under the status-bar inset, so the country plate sits a
 *  comfortable step below the system clock/battery rather than flush against them. Back to a
 *  normal gap — the black-strip experiment that pulled this down to 5dp was reverted. */
private val HeroTopGap = 22.dp

/** Empty space between the status bar and the top of the floating flag card. */
private val HeroCardTopGap = 8.dp

/** Extra height of the black fade behind the connect dock, above the disc itself. */
private val DockScrimExtra = 24.dp

/** An estimate of the menu+country row's own real height, used only to keep [HomeScreen]'s
 *  fixed [heroHeight] in the right neighbourhood of Header's real layout (see the note there)
 *  — enough to cover [CountryHeadline]'s 34sp line plus its own padding with a little to
 *  spare. */
private val HeroTopRowHeight = 46.dp

/**
 * The flag the hero reserves below the top row for the docked connect disc's *upper half*.
 *
 * The disc no longer lives in this column — [HomeScreen] draws it as an overlay whose centre
 * lands on this column's measured foot, i.e. the browse card's top edge. So the disc straddles
 * the seam: its lower half sits on the card, its upper half floats over the flag. This spacer is
 * that upper half — [PowerSize] / 2 — so the disc has flag around its top and the card begins
 * exactly under its equator.
 */
private val HeroDockWell = PowerSize / 2


// ── Menu button ───────────────────────────────────────────────────────────────
// The one navigation mark left at the top of the screen: an asymmetric hamburger, top-left,
// on the flag rather than in a chrome bar (the black status bar it used to live in is gone).
//
// Three lines, not three *equal* lines: the top runs the full width, each below it shorter, so
// the mark tapers to the left. Bigger than the 20dp icon it replaces, and drawn by hand rather
// than as a Material glyph so the taper and the rounded caps are exactly as drawn. A soft dark
// under-stroke sits a pixel below each white line, so the whole thing holds its edge on a bright
// stripe of an arbitrary flag without needing a chip or a plate under it.

/** The hamburger's drawn size, inside a [TapTarget] touch area. Matched to the reference
 *  mockup's exact 26px lines (was 27dp, close but not exact). */
private val MenuGlyphSize = 20.dp

/** Line weight, and the gap from the mark's centre to its outer lines. Matched to the
 *  reference mockup exactly: 3px line height, 7px pitch between adjacent lines (was
 *  2.5dp/6.5dp). */
private val MenuStroke = 2.dp
private val MenuLineGap = 5.dp

/** How far the shadow line sits below its white line, and its colour. */
private val MenuShadowDrop = 1.dp
private val MenuShadow = Color.Black.copy(alpha = 0.30f)

/** The three line widths as fractions of the mark's width: full, then shorter, then shortest.
 *  Matched to the reference mockup's exact 26/19/13px widths against a 26px mark (was
 *  0.72f/0.48f, which gave 19.4px/13px against the old 27px mark — close but not exact). */
private val MenuLineRatios = listOf(1.0f, 19f / 26f, 13f / 26f)


// ── Mode pill ─────────────────────────────────────────────────────────────────
// Gone. The badge that used to sit on the seam between the hero and the browse card — glass,
// hairline, "Mode · Manual", docked half in and half out of the card's top edge — is deleted:
// the composable, its six tokens and the `dockOnSeam` layout modifier that put it there.
//
// One thing goes with it: [Header] no longer needs a z-index to paint over the card for the pill's
// sake. The connect disc still docks half-in on the seam, so [CardTopRoom] remains a dock well —
// clear glass for the disc's lower half to rest over — but nothing is drawn *into* that well now;
// the card's own header row sits below it.
//
// The mode itself is still changeable, and by the gesture that always did it: a vertical drag
// on the connect disc, up for Smart and down for Manual, plus the two named accessibility
// actions on the same button (see [PowerCircle]). Nothing on the screen states the current
// mode now — that is the trade this removal makes, and it is deliberate: the hero is down to
// one country, one address and one action.

// ── Emboss ────────────────────────────────────────────────────────────────────

/**
 * The bead of light and shade that makes a button a raised object: a specular crown across the
 * top third, nothing through the middle, shade gathering at the foot.
 *
 * Laid *over* whatever fill the button already had rather than replacing it, so a pill and a
 * chip and a toggle can keep their own colour and still be lit identically. Four stops because
 * the crown has to arrive and leave — a two-stop version reads as a tilt, not a curve.
 */
private val EmbossCrown = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.15f),
    0.30f to Color.White.copy(alpha = 0.045f),
    0.58f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.24f),
)

/** How far a button sinks while it is held. */
private const val EMBOSS_PRESS_SCALE = 0.955f

/**
 * The depth treatment every button on Home wears.
 *
 * Five things in the order light actually works: the object shrinks and loses most of its
 * shadow while held, a drop shadow under it, its own fill, [EmbossCrown]'s light and shade over
 * that fill, and [heroEdge]'s lit hairline around the whole rim. The press is what sells the
 * height — a static highlight on its own reads as a gradient rather than as a raised thing.
 */
private fun Modifier.embossed(
    shape: Shape,
    fill: Brush,
    elevation: Dp,
    pressed: Boolean,
    ambientColor: Color = HeroShadowAmbient,
    spotColor: Color = HeroShadowSpot,
): Modifier = this
    .scale(if (pressed) EMBOSS_PRESS_SCALE else 1f)
    .shadow(
        elevation = if (pressed) elevation / 3 else elevation,
        shape = shape,
        clip = false,
        ambientColor = ambientColor,
        spotColor = spotColor,
    )
    .clip(shape)
    .background(fill)
    .background(EmbossCrown)
    .border(1.dp, heroEdge, shape)

/** [UsageCard]'s own material, lit by [Modifier.embossed] like every other raised thing here. */
private val UsageCardFill = Brush.verticalGradient(listOf(RefElev2, RefElev1))

/** [EmptyHint]'s "+" disc: a raised object rather than a drawn ring. */
private val EmptyDiscFill = Brush.verticalGradient(listOf(RefElev2, RefElev1))

// ── Country headline ──────────────────────────────────────────────────────────
// Where the tunnel comes out, top-right on the flag, white on an asymmetric dark plate.
//
// One fact and no others. The city used to lead this line with the country and the
// config's own name under it; the flag behind the whole screen already says which country
// this is, and the server's name is what the selector at the foot and the list below are for.
// What is left is the answer — the country, with the city as one dim caption under it.
//
// It changes on the same crossfade as the flag behind it, because they are the same event
// — the user picks another server, or the tunnel reports the exit node's real country —
// and a country name that cuts while its flag dissolves reads as two things happening.
//
// It is white now, not flag-tinted: it sits straight on the flag artwork with no card or
// wash behind it at all -- [HeadlineInkShadow] alone is what guarantees contrast over any
// flag band, so the name reads as ink sitting directly on the artwork.

/** The plate is a *fixed*, compact box — it never grows or shrinks with the label. It is short and
 *  of a set width; instead of the frame resizing, the label's font steps down as the combined
 *  "Country · City" string lengthens ([headlineFontFor]) so it always fits this one frame. Compact
 *  by intent: a tidy location chip, not a stretched banner. */
private val HeadlinePlateWidth = 224.dp
private val HeadlinePlateHeight = 84.dp


/** The city line sits under the country name at a fixed, smaller step -- it never competes with
 *  the country for the ramp, so it stays legible even when the country name itself is long. */
private val HeadlineCitySize = 13.sp

/** The left-to-right wipe when the name changes: the new label is revealed progressively across
 *  its glyphs rather than swapped or crossfaded. */
private const val REVEAL_MS = 460


// ── Public-IP readout ─────────────────────────────────────────────────────────
// One fact: the address the internet currently sees. Off, it holds this device's own address;
// connected, the exit node's. Keeping it visible in both is the point — the number the user is
// about to change is readable now.
//
// It has moved around — a bare line under the country name, a floating card on the flag, a labelled
// chip — and now lives in the browse card's masthead, on the LEFT where the "+" add-server button
// used to be (see [BrowseCard]). It is stripped to its essence: no card, no border, no background
// wash, no "PUBLIC IP" label, and no copy glyph — just the address itself, drawn straight on the
// glass (its own [HeroInkShadow] carries contrast). Tap the line to copy.
//
// Three states, not two — but none of them is a word for "loading" a value: the address itself, a
// quiet "Checking…" while a lookup is still in flight (the value is simply not known yet, so nothing
// that could look like a malformed address is drawn), or a "—" dash with a retry glyph, tappable to
// ask again. See [HomeUiState.ipLookupPending] for the flag and [GeoService.lookupCurrentIp] for
// what can fail. Only a value validated by [isIpLiteral] is ever shown, so a partial or malformed
// lookup can never render — the address is drawn as ONE complete static string, never per-digit.

/** What the public-IP readout is showing right now — see [IpCard]. */
private data class IpSlot(val value: String, val checking: Boolean) {
    // Ready only for a *well-formed* address. A non-blank-but-malformed value (a half-resolved
    // string, a stale garbage pref) used to sail through as "ready" and render as the ". .0 ."
    // the bug report showed. Now anything that is not a real IPv4/IPv6 literal is treated as
    // not-ready, so the readout shows the dash or the retry instead of a broken string, and a
    // background lookup replaces it with a valid one.
    val ready: Boolean get() = value.isNotBlank() && isIpLiteral(value)
}

/** Which of [IpCard]'s three states is showing. Keyed for the crossfade so the readout fades only
 *  between the three *kinds* of state, not on every value change within [READY]. */
private enum class IpKind { READY, CHECKING, UNAVAILABLE }

/** The IP value's own type size, and the neutral placeholder's. Bold white for a real address; the
 *  smaller dim step for the two placeholders. */
private val IpValueSize = 15.sp
private val IpPlaceholderSize = 13.sp

/** One full up-down cycle of a single dot, in ms — see [IpCheckingDots]. */
private const val DOT_BOUNCE_MS = 600
/** How far each dot travels, up and back down. */
private val DotBounceHeight = 5.dp

/**
 * Three bold dots bouncing up and down in sequence while the IP lookup is in flight — replaces
 * the old "Checking…" text. Each dot runs the same up-down tween on an infinite loop, offset
 * from the next by a third of the cycle, which is what reads as a wave running left to right
 * rather than three dots bobbing in place together. Off (dots sit flat) under reduced motion.
 */
@Composable
private fun IpCheckingDots() {
    val reduce = appReduceMotion()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val infinite = rememberInfiniteTransition(label = "ipDot$i")
            val offsetY by if (reduce) {
                remember { mutableStateOf(0f) }
            } else {
                infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(DOT_BOUNCE_MS, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset((DOT_BOUNCE_MS / 3) * i),
                    ),
                    label = "ipDotVal$i",
                )
            }
            Box(
                Modifier
                    .size(6.dp)
                    .offset(y = -DotBounceHeight * offsetY)
                    .clip(CircleShape)
                    .background(RefTextHi),
            )
        }
    }
}


/**
 * The mirror image of [IpMergedPill] on the disc's other side: "Connecting…" while a tunnel is
 * coming up, "Connected" once it is, hidden entirely at [ConnPhase.OFF]. Rounded cap on the
 * LEFT (its outer end) and a flat right edge that tangent-joins the disc, the exact reverse of
 * the IP pill's own shape — the two together read as one continuous capsule with the disc as
 * its centre, not two unrelated badges that happen to flank it.
 *
 * The flat edge is positioned by its own RIGHT edge, not its left — the text's width isn't
 * known ahead of layout, so [ConnectDockLayout] places it by its right edge rather than the plain
 * `offset` the IP pill uses, which only works for a left-anchored child.
 */





@Composable
private fun IpCard(state: HomeUiState, onRetryIp: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reduce = appReduceMotion()
    val slot = IpSlot(state.displayIp, state.ipLookupPending)
    // Tapping copies a real value, or retries a failed lookup; while a lookup is still in flight
    // there is nothing to do, so the readout is not tappable in that one state.
    val onTap: (() -> Unit)? = when {
        slot.ready -> {
            {
                clipboard.setText(AnnotatedString(slot.value))
                android.widget.Toast
                    .makeText(context, "IP copied", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
        !slot.checking -> onRetryIp
        else -> null
    }
    val tapLabel = if (slot.ready) "Copy IP address" else "Retry IP lookup"
    // Crossfade only between the three *kinds* of state — not on every value change. The kind
    // (ready / checking / unavailable) is the key, so when the address changes while staying ready
    // the outer fade does nothing and the new address simply replaces the old one.
    val kind = when {
        slot.ready -> IpKind.READY
        slot.checking -> IpKind.CHECKING
        else -> IpKind.UNAVAILABLE
    }
    // Minimal: no card, no border, no background, no "PUBLIC IP" label, no copy glyph — just the
    // address itself. The whole line is tappable to copy (or to retry a failed lookup); the text
    // carries its own [HeroInkShadow] so it holds over the card's translucent glass head.
    Row(
        modifier.then(
            if (onTap != null) {
                Modifier.clickable(onClickLabel = tapLabel, onClick = onTap)
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (
                    fadeIn(appMotion(reduce, 260)) togetherWith fadeOut(appMotion(reduce, 140))
                    ).using(SizeTransform(clip = false))
            },
            label = "publicIp",
        ) { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (k) {
                    // The real address, drawn as ONE complete static string. It is only ever shown
                    // once [IpSlot.ready] confirms a well-formed literal (see below), so a partial or
                    // mid-lookup value can never reach the screen. No per-digit odometer any more —
                    // that rolling animation was what rendered the ". 0 . 0 ." the bug report showed
                    // (wheels caught mid-spin between the fixed dots). Tabular figures keep it steady.
                    IpKind.READY -> Text(
                        slot.value,
                        fontSize = IpValueSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(fontFeatureSettings = "tnum", shadow = HeroInkShadow),
                    )
                    // In flight: three bold dots bouncing up and down, not a "Checking…" word —
                    // the value is not known yet, so nothing that could look like a malformed
                    // address is drawn either way, but three dots read as the pill itself
                    // "thinking" rather than needing to be read.
                    IpKind.CHECKING -> IpCheckingDots()
                    // Lookup finished with nothing: a dash and a retry glyph the tap handler wires.
                    IpKind.UNAVAILABLE -> {
                        Text(
                            "—",
                            fontSize = IpPlaceholderSize,
                            fontWeight = FontWeight.Bold,
                            color = RefTextMid,
                            maxLines = 1,
                            style = TextStyle(fontFeatureSettings = "tnum", shadow = HeroInkShadow),
                        )
                        Spacer(Modifier.width(8.dp))          // snapped to the 4dp grid, was 6dp
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/** How long a digit wheel takes to roll to its new value — long enough that the intervening digits
 *  read as a counter turning, short enough to still feel mechanical; collapses to an instant swap
 *  under reduced motion. */
private const val IP_ROLL_MS = 520

/** The height of one odometer cell — the visible window each digit rolls through. A touch taller
 *  than [IpValueSize] so glyphs have head- and foot-room inside the clipped slot. */
private val IpDigitCell = 22.dp

/** How many digit cells the reel strip holds: two full 0–9 runs (20 cells). The extra run is the
 *  headroom the *first* spin needs — a wheel starts a full turn above its target and rolls down into
 *  place, so its position travels through [digit, digit+10], which a single 0–9 strip could not
 *  cover without going blank. */
private const val IpReelCells = 20

/**
 * The IP value as a mechanical odometer: one wheel per character. When the address changes, each
 * digit that differs rolls vertically to its new value through the intervening digits, like a
 * counter wheel, rather than a fade or a one-step swap. Non-digit characters (the dots) are fixed
 * cells. Cells are keyed by position, and tabular figures keep every column the same width so the
 * row does not jitter mid-roll. Only a validated dotted address is ever passed in (see [IpSlot]).
 */
@Composable
private fun RollingIp(value: String, reduce: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value.forEachIndexed { index, ch ->
            key(index) {
                if (ch in '0'..'9') {
                    DigitReel(digit = ch - '0', reduce = reduce, index = index)
                } else {
                    // Dots do not roll — a fixed cell keeps the row's rhythm and gives the wheels
                    // either side of it something to align to.
                    Box(Modifier.height(IpDigitCell), contentAlignment = Alignment.Center) {
                        Text(
                            ch.toString(),
                            fontSize = IpValueSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(shadow = HeroInkShadow),
                        )
                    }
                }
            }
        }
    }
}

/** One odometer wheel. Drives an [Animatable] from a start a full turn above the target, so the very
 *  first appearance spins down into place — and any later change rolls through the intervening
 *  digits. See [RollingIp]. */
@Composable
private fun DigitReel(digit: Int, reduce: Boolean, index: Int) {
    // Start one full turn (10) above the target so the first render rolls down through a complete
    // spin; under reduced motion it simply parks on the digit.
    val pos = remember {
        Animatable(if (reduce) digit.toFloat() else digit.toFloat() + 10f)
    }
    LaunchedEffect(digit, reduce) {
        if (reduce) {
            pos.snapTo(digit.toFloat())
        } else {
            // A short per-wheel stagger so the wheels cascade rather than snapping in lockstep.
            delay((index % 6) * 26L)
            val current = pos.value
            val target = digit.toFloat()
            val next = if (target > current % 10f) {
                (current - current % 10f) + target
            } else {
                (current - current % 10f) + 10f + target
            }
            pos.animateTo(next, tween(IP_ROLL_MS))
            if (pos.value >= 20f) pos.snapTo(pos.value - 10f)
        }
    }
    Box(
        Modifier
            .height(IpDigitCell)
            .clipToBounds(),
    ) {
        Column(
            Modifier.graphicsLayer { translationY = -pos.value * IpDigitCell.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (i in 0 until IpReelCells) {
                Box(Modifier.height(IpDigitCell), contentAlignment = Alignment.Center) {
                    Text(
                        (i % 10).toString(),
                        fontSize = IpValueSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(fontFeatureSettings = "tnum", shadow = HeroInkShadow),
                    )
                }
            }
        }
    }
}

// ── Power circle ──────────────────────────────────────────────────────────────
// The connect control: a disc with a ring around it, and between them the one thing on this
// screen that reports progress rather than a result. Centred on the screen's own axis at
// [PowerSize], with nothing beside it and nothing under it.
//
// The disc is [PowerDiscSize] inside the [PowerSize] box, which leaves an 11dp band for the ring
// — the same ~8% of the diameter the smaller control used, so the ring still reads as a rim on
// the disc rather than as a separate circle near it.
//
// Three faces, one per [ConnPhase], crossfaded on [PHASE_FADE_MS]:
//
//   OFF        — the brushed-white disc, [PowerInk] mark, bare hairline track. A white disc on
//                dark chrome is the highest-contrast thing the screen can draw.
//   CONNECTING — the same white disc and [PowerInk] mark as idle, with one soft teal arc sweeping
//                smoothly around it (see [PowerRing]). The arc is outside the disc on purpose: the
//                face keeps its shape, so the button still looks pressable while it works. No teeth,
//                no gear — a single fading comet that reads as "working" by its motion alone.
//   CONNECTED  — the *same white disc*, with the ring resolved to a solid teal circle and a soft
//                teal halo that breathes slowly around it. The face never fills with colour in any
//                state: a filled disc reads as "press me" in exactly the state where pressing
//                disconnects. What reports "lit" is the ring and its halo, in the groomx teal
//                [ConnectTeal] (green); the room still washes [RefGlowOn] blue behind the hero.
//
// It carries one gesture besides the tap: a vertical drag switches Smart / Manual, as do its two
// named accessibility actions. Settings' "Server choice" row is the drawn control for the same
// setting, so this is no longer the only way to reach it.
//
// The mockup's four-part box-shadow, split by what Compose can draw:
//   0 16px 34px rgba(0,0,0,0.45)      ┐ the cast shadow — Modifier.shadow
//   0 4px 10px rgba(0,0,0,0.25)       ┘
//   inset 0 3px 4px rgba(255,255,255,0.95)  ┐ Compose has no inset box-shadow, so these two
//   inset 0 -10px 14px rgba(0,0,0,0.14)     ┘ are [PowerFaceSheen]: bright top rim, dark foot.

/** The disc itself — now the same size as [PowerSize], since the button is one glass
 *  circle rather than a small solid disc inside a separate glass ring band. */
private val PowerDiscSize = PowerSize

/** The ring's own weight, and how far outside the disc it is drawn.
 *
 *  3dp of stroke rather than the 2.5dp the 84dp disc carried: the ring's whole job is to
 *  be read from wherever the phone is being held, and a hairline that was proportionate
 *  around a small disc reads as a scratch around a 118dp one. The gap stays at 3dp — it
 *  is the space that makes the ring a rim on the disc rather than a second circle near
 *  it, and that reads the same at any diameter. */
private val PowerRingStroke = 6.dp
private val PowerRingGap = 3.dp

/** Used for every animated part of the connect ring — the connecting arc, the
 *  connected ring and its halo. Same green family as [RefLive] now (was a separate teal);
 *  scoped to this control so it never leaks into the blue room light ([RefGlowOn]) behind
 *  the hero. */
private val ConnectTeal = Color(0xFF4AD98A)

/** One full turn of the connecting arc. Slow and even — a premium sweep, not a busy spinner. This
 *  is the app's only indeterminate progress and the only motion that runs unasked. */
private const val CONNECT_SPIN_MS = 900

/** How much of the circle the connecting comet spans, in degrees — a soft quarter-turn head that
 *  fades to nothing at its tail. */
private const val CONNECT_ARC_SWEEP = 96f

/** How far the disc travels down on a press, and how far the light travels with it.
 *
 *  A press is two things happening together: the disc gets slightly smaller and its shadow
 *  gets much shallower. Scale alone is the cheap version — the disc shrinks but keeps casting
 *  a 22dp shadow, so it reads as a picture of a button being scaled rather than as a physical
 *  thing being pushed towards the surface it sits on. Dropping the elevation to 9dp at the
 *  same time is what makes it land. */
private const val POWER_PRESS_SCALE = 0.955f
private val PowerRestElevation = 22.dp
private val PowerPressElevation = 9.dp

/** The hairline on the disc's own edge. See [PowerDiscRim]. */
private val PowerRimStroke = 1.dp



// ── Connect bolt ──────────────────────────────────────────────────────────────
/** The bolt outline as vector path data, baked to absolute coordinates in the [BOLT_VW]×[BOLT_VH]
 *  space. Converted from the source SVG (a bolt-shaped cutout in a flipped, scaled rectangle) by
 *  applying its transform and keeping only the bolt subpath. Mirrors `res/drawable/ic_connect_bolt`
 *  — keep the two in sync if the asset changes. */
private const val CONNECT_BOLT_PATH_DATA =
    "M17.387,0.042 C17.713,0.133 17.882,0.386 17.857,0.75 C17.847,0.954 17.819,1.017 17.482,1.599 C17.047,2.353 16.09,3.983 15.094,5.666 C13.685,8.05 13.713,7.998 13.713,8.176 C13.713,8.38 13.818,8.552 14.0,8.657 L14.141,8.734 L16.458,8.734 C18.962,8.734 18.92,8.73 19.119,8.92 C19.323,9.109 19.4,9.495 19.288,9.765 C19.26,9.828 17.335,12.419 15.003,15.522 C12.675,18.625 10.347,21.724 9.835,22.408 C9.32,23.095 8.843,23.712 8.769,23.783 C8.604,23.944 8.467,24.0 8.317,23.972 C8.145,23.94 8.071,23.842 8.054,23.621 C8.036,23.411 8.078,23.246 8.783,20.743 C10.024,16.325 10.571,14.34 10.571,14.242 C10.571,14.095 10.498,13.958 10.35,13.828 C10.235,13.727 10.21,13.72 9.814,13.699 C9.582,13.685 8.467,13.678 7.328,13.681 C5.536,13.688 5.242,13.681 5.137,13.636 C4.86,13.513 4.684,13.236 4.681,12.917 C4.681,12.696 4.6,12.917 6.82,7.174 C7.03,6.634 7.454,5.529 7.766,4.719 C8.078,3.909 8.446,2.963 8.58,2.616 C8.716,2.269 8.941,1.683 9.081,1.315 C9.397,0.484 9.446,0.393 9.632,0.235 C9.905,0.0 9.723,0.007 13.674,0.007 C16.15,0.004 17.293,0.014 17.387,0.042 Z"

/** The bolt's source viewport — a clean 24x24 unit box (traced from the GROOMX brand artwork,
 *  normalized). Path coordinates above are in this space. */
private const val BOLT_VW = 24f
private const val BOLT_VH = 24f

/** How long the lit fill takes to climb the bolt from foot to tip. Long and linear on purpose:
 *  the fill should read as a slow, steady charge over the whole connect, not a quick flash. If the
 *  tunnel comes up before the climb finishes the fill simply continues to full without a jump; if
 *  it takes longer, the fill waits full. */
private const val BOLT_FILL_MS = 3200

/** How long the fill drains back down on disconnect — shorter than the climb, and eased, so
 *  turning off feels like a quick release rather than the slow charge reversed at the same pace. */
private const val BOLT_DRAIN_MS = 900

/** The bolt outline, baked to absolute coordinates in the [BOLT_VW]×[BOLT_VH] space and parsed once
 *  (the same data the drawable carries). Kept as a [Path] so [PowerBolt] can both fill it and clip a
 *  rising window against it. */
private val ConnectBoltPath: Path =
    PathParser().parsePathString(CONNECT_BOLT_PATH_DATA).toPath().apply {
        fillType = PathFillType.EvenOdd
    }

/** The disc's mark: the imported lightning bolt, drawn as a dark struck base ([trackColor]) with a
 *  lit fill ([fillColor]) revealed from the foot up to [fill] (0 = empty, 1 = full). The base is
 *  always fully drawn so the mark reads as a bolt even at rest; the fill is clipped to a rectangle
 *  rising from the bottom, so the colour climbs the bolt like a charging gauge. A faint white rim
 *  over both keeps the upper facets lit. See [PowerCircle] for how [fill] is animated. */
/** How wide the gap at the top of the ring is, in degrees — the classic broken-ring "power"
 *  symbol shape. */
private const val POWER_GLYPH_GAP_DEG = 44f

/** How far the stem reaches down into the ring, as a fraction of its radius. */
private const val POWER_GLYPH_STEM_FRACTION = 0.78f

/** The disc's mark: a broken ring with a vertical stem through the gap — the classic power
 *  symbol — replacing the former lightning bolt. Two strokes only (a soft halo, then the sharp
 *  glyph on top), no path parsing, no native blur: kept deliberately light. [trackColor] is the
 *  glyph at rest, [fillColor] is lit, [fill] (0..1) crossfades between them and drives the halo. */
private val ConnectingBoltColor = Color(0xFFFF5A36)


/**
 * The connect ring in the band around the disc — a clean, minimal progress ring, not a mechanism.
 * No teeth, no gear, no crosshair; nothing that reads as machined hardware.
 *
 * A faint hairline track is always present, giving the disc a defined rim. Over it, three faces
 * crossfaded on [PHASE_FADE_MS]:
 *
 *   OFF        — the bare track only. Nothing moves.
 *   CONNECTING — one soft green comet ([ConnectTeal]) sweeps smoothly around the track: a
 *                [CONNECT_ARC_SWEEP]° arc that fades from a bright head to a transparent tail,
 *                turning at a steady [CONNECT_SPIN_MS] per revolution. Motion alone says "working".
 *   CONNECTED  — the arc resolves to a solid teal ring, and a wide, soft teal halo fades in and
 *                breathes slowly outside it — the one thing on the screen that keeps living once
 *                the tunnel is up.
 *
 * The sweep is driven by an [Animatable], not an infinite transition: on a phase change it is
 * simply cancelled, so the arc stops cleanly with no snap-back. Reduce-motion: the arc parks as a
 * static head at the top and the halo holds a fixed, un-breathing glow.
 */
/** Angular gap, in degrees, between the two connecting bars while they spin as a pair. */
private const val CONNECT_BAR_GAP_DEG = 10f

/** Length of each connecting bar, in degrees. */
private const val CONNECT_BAR_SWEEP_DEG = 175f


// linear-gradient(160deg, #ffffff 0%, #e7e9ee 55%, #d9dce3 100%)
// The inset disc's flat base colour -- a touch lighter than the panel it sits in so the
// carved well still reads against the background, with the dark/light arcs doing the
// actual depth work. No white "face" anymore: the disc is not a raised object.
// No solid core color at all anymore -- [PowerCircle]'s disc is entirely the glass fill
// from [Glass.glassSurface], so there is nothing to define here.

// The inner rim of the well: a hairline just inside the disc's own edge, dark enough to
// read as the lip of a carved hole rather than a drawn border.
private val PowerWellRim = Color.White.copy(alpha = 0.12f)

private val PowerFace = Brush.linearGradient(
    0.00f to Color.White,
    0.30f to Color(0xFFF4F5F8),
    0.55f to Color(0xFFE7E9EE),
    1.00f to Color(0xFFD9DCE3),
)

// The connecting face used to be tinted amber (PowerWorkingFace); the working state is
// monochrome now, so the disc keeps its plain white [PowerFace] in every phase and only the
// turning comet reports that an attempt is in flight.

/**
 * The disc's edge: white where the light is, and nothing at all at the foot.
 *
 * A white object on near-black does not need a light border to be separated from the page —
 * it needs the opposite, an edge that reads as the curve of the object turning away. So this
 * runs from a bright hairline at the crown to transparent by the middle and back to a faint
 * dark at the foot, which is the same story [PowerFaceSheen] tells across the face. A single
 * flat border colour here, at any alpha, put a visible ring around the disc.
 */
private val PowerDiscRim = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.95f),
    0.22f to Color.White.copy(alpha = 0.38f),
    0.52f to Color.Transparent,
    0.86f to Color.Black.copy(alpha = 0.06f),
    1.00f to Color.Black.copy(alpha = 0.13f),
)

/** The shade that comes up over the face on a press. Weighted to the foot: the disc is being
 *  pushed towards the surface, so what it loses is the room under it. */
private val PowerPressShade = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.03f),
    0.45f to Color.Black.copy(alpha = 0.07f),
    1.00f to Color.Black.copy(alpha = 0.16f),
)

/** The ring's unlit groove — see the draw call in [PowerRing]. */
private val PowerRingTrack = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.19f),
    0.34f to Color.White.copy(alpha = 0.10f),
    0.68f to Color.White.copy(alpha = 0.06f),
    1.00f to Color.White.copy(alpha = 0.12f),
)

/** The disc's own cast shadow, as two colours — see the [Modifier.shadow] call. Deeper
 *  than the panel's, because the disc is the most-elevated thing on the screen and it is
 *  white, so anything less than this reads as the disc floating unattached. */
private val PowerShadowAmbient = Color.Black.copy(alpha = 0.55f)
private val PowerShadowSpot = Color.Black.copy(alpha = 0.88f)

// inset 0 3px 4px rgba(255,255,255,.95) over inset 0 -10px 14px rgba(0,0,0,.14)
private val PowerFaceSheen = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.55f),
    0.06f to Color.White.copy(alpha = 0.10f),
    0.14f to Color.Transparent,
    0.80f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.14f),
)

// ── Browse card ───────────────────────────────────────────────────────────────
// .browse-card: the list's own panel, 28dp top corners, meeting the hero directly — no
// margin between them, and its first [PanelFade] translucent so the flag and the hero's
// horizon light carry on through the tab row (see [panelTopFade]). Over that fill it is
// frosted glass: a colder black than the page ([RefPanelBg]), an icy wash strongest along its
// top edge ([panelFrost]), a specular sweep under that edge ([drawPanelSheen]) and an icy
// hairline on the edge itself ([drawPanelTopEdge]) — four gradients and no blur, for the
// reason in [panelFrost]. It is deliberately not
// a separate floating piece any more: the connect pill is docked immediately above this
// edge and the artwork runs behind both, so the two read as one panel.
//
// The list pulls to refresh, and what it refreshes is the pings: dragging it down
// re-measures every server *currently shown in it* — the tab's own list, filtered by
// whatever is in the search box — rather than everything saved. The rows update one by
// one as their own measurement lands (see VpnTab's `refreshPings`), so a fast server's
// number changes while a dead one is still timing out, and the indicator goes away when
// the last of them finishes or gives up.
//
// The gesture is Material 3's own [PullToRefreshContainer] driven by
// [rememberPullToRefreshState], so it feels like every other Android list: the same
// threshold, the same rubber-banding, the same spinner. The container is placed in a Box
// over the list rather than inside it, which is how the pattern is meant to be assembled
// — the indicator floats above the first row instead of pushing the content down and
// re-laying out the list on every frame of the drag.

// ── List scroll edge ────────────────────────────────────────────────────────────
// The divider between the card's head and its scrolling list, and the screen's one piece of
// scroll elevation. At rest it is a faint hairline; once the list scrolls it brightens and a soft
// shadow grows under it, so the list reads as sliding *under* a fixed head. Driven by an animated
// 0→1 [elevation] from [BrowseCard] (which honours reduced motion), never by the raw scroll offset.

/** The hairline's alpha at rest and fully raised, and the peak alpha of the shadow beneath it. */
private const val LIST_EDGE_ALPHA_REST = 0.055f
private const val LIST_EDGE_ALPHA_RAISED = 0.16f
private const val LIST_EDGE_SHADOW_ALPHA = 0.22f

/** How tall the shadow gradient below the hairline is drawn. */
private val ListEdgeShadowHeight = 10.dp


// PanelFade removed: it sized the fade/frost gradient that used to let the flag show
// through the browse card's top edge. That gradient is gone (see [BrowseCard]'s Column
// background — flat [RefPanelBg] fill now), since the flag stopped being behind this card
// at all once it became its own separate floating card. [drawPanelTopEdge] no longer
// depends on this value; its own comment still mentions the old 0.78 stop for history.

/**
 * Height of the card's masthead band (the search-toggle row) — no longer reserved for the
 * connect disc's overlap (it's 72dp now, doesn't need one), but kept as a fixed band so it
 * has a defined area to apply real glass to (see [BrowseCard]'s masthead Box).
 */
private val CardTopRoom = 48.dp

/** How much the masthead band's own top corners curve — a "matching" curved header per
 *  request, echoing the disc/pill shape it sits under rather than [PanelCorner]'s flatter
 *  card-corner radius. */
private val MastheadCurve = PanelCorner  // was its own, larger 32dp — mismatched with the
                                          // card's own PanelCorner clip and showed as a second,
                                          // visible edge peeking out around the masthead's own
                                          // tighter curve. Same radius as the card now, so the
                                          // two curves coincide and read as one edge.

// PanelFrostFade removed alongside [panelFrost] itself — the icy-glass wash it sized is
// gone now that the card is a flat fill; see the note above [CardTopRoom].


/** How far down the card's top edge the specular sweep in [drawPanelSheen] reaches.
 *
 *  48dp, down from 72 with [PanelFade]. The sheen is the light that appears to be *on* the
 *  glass; a sweep running well past the point where the glass has gone opaque reads as a
 *  gradient in the list instead. */
private val PanelSheenDepth = 48.dp

// panelTopFade / panelFrost removed: both built the gradient that used to let the flag
// show through the browse card's top edge (translucent [RefPanelBg] fading to opaque, plus
// an icy [RefFrost] wash over it). Gone along with the fill they backed — see the comment
// on [BrowseCard]'s Column background — now that the flag is its own separate floating
// card rather than something sitting behind this one. [drawPanelSheen] and
// [drawPanelTopEdge] below still carry the card's top-edge highlight on their own.





/** The search field's type, shared by the input and its placeholder — see [SearchBarChip]. */
private val SearchFieldStyle = TextStyle(
    color = RefTextHi,
    fontSize = TypeBody.first,
    lineHeight = 19.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

// ── Server list ───────────────────────────────────────────────────────────────
// .server-row, Windscribe-style: a [RowFlagSize] circular flag, the country name, an
// optional ping (bars + ms, only when measured), and a favourite heart at the trailing
// edge. No per-row background wash — a hairline divider between rows and a slim leading
// accent bar on the active one do that job instead. See [ServerRow] below.


@Composable
private fun EmptyHint(allEmpty: Boolean, searching: Boolean, onAdd: () -> Unit) {
    val (title, subtitle) = when {
        searching -> "Nothing matches" to "Try another name, city or country"
        allEmpty -> "No servers yet" to "Add a config or import a subscription"
        else -> "Nothing added by hand" to "Pasted and scanned configs land here"
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Add servers",
                onClick = onAdd,
            )
            .padding(horizontal = ListPad, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .embossed(CircleShape, EmptyDiscFill, 8.dp, pressed),
            contentAlignment = Alignment.Center,
        ) {
            PlusGlyph(color = RefTextMid, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(16.dp))          // snapped to the 4dp grid, was 14dp
        Text(title, fontSize = TypeSubtitle.first, fontWeight = TypeSubtitle.second, color = RefTextHi)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = TypeCaption.first, color = RefTextLow)
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
        state.activeConfig == null -> "No server selected"
        else -> "Not connected"
    }
    // No elapsed time here: the session duration is deliberately not shown anywhere on
    // this screen any more (the hero's timer chip went with it), so the title stays the
    // same string in both phases and only [subtext] changes with the connection.
    val title = "Data used today"
    val shape = remember { RoundedCornerShape(CardCorner) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            // The hard [RefBorder] outline is gone, in step with the rest of the app: this card
            // is now separated by its own lift and its lit rim ([Modifier.embossed]) rather than
            // by a drawn line. Deeper than the buttons — it floats over a scrolling list.
            .embossed(shape, UsageCardFill, 4.dp, pressed, CardShadowAmbient, CardShadowSpot)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Choose a server",
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsageRing(
            bytes = state.dailyUsageBytes,
            // Teal while the tunnel is up; a plain grey the rest of the time, so the card
            // never announces a state of its own. The ring measures today's running total
            // against [USAGE_DAILY_CAP_BYTES], not the current session.
            accent = if (state.connected) RefLive else RefTextMid,
        )
        Spacer(Modifier.width(16.dp))              // .bottom-card gap (snapped to the 4dp grid, was 14dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = TypeBody.first,
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
        Spacer(Modifier.width(16.dp))     // snapped to the 4dp grid, was 14dp
        Chevron(size = 16.dp, color = RefTextLow)
    }
}

/**
 * .usage-ring — a 5dp arc over a machined track, with today's total in the middle.
 *
 * Apple-Health-grade rather than a flat conic: the track is lit from the top (a
 * subtle white vertical gradient, brightest where light would fall) so it reads as a
 * groove rather than a drawn line; the fill is a forward sweep gradient that runs from
 * a dim tail to a bright, rounded head; and a wider, fainter underglow sits beneath the
 * head so the arc looks lit, not painted. The sweep animates to [USAGE_DAILY_CAP_BYTES].
 */
@Composable
private fun UsageRing(bytes: Long, accent: Color) {
    val (value, unit) = ringLabel(bytes)
    val reduce = appReduceMotion()
    val fraction = (bytes.toFloat() / USAGE_DAILY_CAP_BYTES.toFloat()).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(
        targetValue = fraction,
        animationSpec = appMotion(reduce, 600),
        label = "usageSweep",
    )
    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // Machined groove: lit from the top, not a flat hairline.
            drawCircle(
                brush = UsageRingTrack,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )

            if (sweep > 0.001f) {
                // Draw from twelve o'clock: rotate the frame so the sweep gradient's start
                // (three o'clock in its own axis) lands at the top, matching the arc.
                rotate(degrees = -90f, pivot = center) {
                    // Underglow — a wider, translucent pass so the head reads as lit.
                    drawArc(
                        color = accent.copy(alpha = 0.18f),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = usageSweepBrush(accent, sweep, center),
                        startAngle = 0f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        // Numeral over unit, not "1.8 MB" on one line: at this diameter the one-line form either
        // wraps at the ring's inner wall or has to shrink past legibility. The two are sized apart
        // so the stack reads as one measurement rather than as two stacked words.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = accent,
                maxLines = 1,
            )
            Text(
                unit,
                fontSize = 8.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = accent.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}

/** Lit-from-top track for the usage ring — a groove, not a drawn circle. Mirrors
 *  [PowerRingTrack] so the two rings on Home read as the same machined family. */
private val UsageRingTrack = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.16f),
    0.38f to Color.White.copy(alpha = 0.08f),
    0.70f to Color.White.copy(alpha = 0.05f),
    1.00f to Color.White.copy(alpha = 0.10f),
)

/**
 * The usage arc's colour along its length: a dim tail climbing to a bright, opaque head.
 * A [Brush.sweepGradient] is the only brush whose axis matches the arc; its fractions run
 * once round from three o'clock (which the caller has rotated to the top), so the drawn
 * arc — from 0° for [sweep] of the circle — occupies the first `sweep` of them, and the
 * stops are placed as fractions of that. The transparent stop just past the head is never
 * drawn but stops the head's colour bleeding back round the gap into the tail (sweep
 * gradients wrap).
 */
private fun usageSweepBrush(accent: Color, sweep: Float, center: Offset): Brush {
    val end = sweep.coerceIn(0.001f, 1f)
    return Brush.sweepGradient(
        0f to accent.copy(alpha = 0.38f),
        end * 0.55f to accent.copy(alpha = 0.85f),
        end to accent,
        (end + 0.0015f).coerceAtMost(1f) to Color.Transparent,
        center = center,
    )
}

// ── Glyphs ────────────────────────────────────────────────────────────────────
// The mockup draws its chevron, account mark and wifi mark as inline SVG on a
// 24-unit grid at stroke-width 2–2.4. Material's equivalents are heavier and, for
// the account mark, filled, so these three are drawn on the same grid: `unit`
// below is one mockup unit, so the path numbers stay recognisable. The connect
// mark is a hand-drawn lightning bolt ([PowerBolt]) on the same 24-unit grid — a
// filled, bevelled glyph rather than Material's flat power symbol.

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

// ══ HOME · DESIGN SYSTEM ══════════════════════════════════════════════════════════
// One small set of tokens; every card, button, field and row below is built from it, so no
// component carries a radius, padding or colour of its own.
//
//   Colour      matte black surfaces, ONE blue accent. Green/amber/red exist only as signal
//               semantics (bars, status dot) — never as decoration.
//   Type        Manrope (AppFont) on the AppType scale; the hero name has its own size ramp.
//   Spacing     4dp grid: S1 4 · S2 8 · S3 12 · S4 16 · S5 20 · S6 24.
//   Radius      RMd 16 (rows, chips) · RLg 22 (connect button) · RXl 28 (hero, panel) · pill (search).
//   Elevation   two soft, low-alpha shadows only — hero and bottom panel. Everything else is a
//               hairline border on a slightly lighter surface.
//   Icons       IconSm 16 · IconMd 20, one stroke weight (2dp) for hand-drawn marks.
//   States      rest → pressed (surface steps lighter, buttons sink 2.5%) → active (accent hairline).
/** The bottom panel sits a shade darker than the page, so it reads as recessed. */
private val PanelFill = Color(0xFF0E0E11)

/** The four states the connect control and status chip draw. [ConnPhase] has no disconnecting
 *  phase — the service tears down and reports OFF — so HomeScreen holds DISCONNECTING for the
 *  gap between the tap and the tunnel actually reporting down. */
private enum class ConnVisual { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

private fun ConnVisual.title(): String = when (this) {
    ConnVisual.DISCONNECTED -> "Connect"
    ConnVisual.CONNECTING -> "Connecting"
    ConnVisual.CONNECTED -> "Connected"
    ConnVisual.DISCONNECTING -> "Disconnecting"
}

private fun ConnVisual.caption(): String = when (this) {
    ConnVisual.DISCONNECTED -> "Not protected"
    ConnVisual.CONNECTING -> "Tap to cancel"
    ConnVisual.CONNECTED -> "Tap to disconnect"
    ConnVisual.DISCONNECTING -> "One moment"
}

/** How long DISCONNECTING stays up after the tunnel reports down, so the state is readable. */
private const val DISCONNECT_HOLD_MS = 500L

/** Give up on DISCONNECTING if the tunnel never reports down (the tap did not take). */
private const val DISCONNECT_TIMEOUT_MS = 6_000L

private val HeroMinHeight = 168.dp
private val HeroMaxHeight = 232.dp

/** Darkening over the flag: a little at the top for the menu chip, a lot at the foot for the name. */
private val HeroScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.34f),
    0.28f to Color.Black.copy(alpha = 0.05f),
    0.50f to Color.Transparent,
    0.78f to Color.Black.copy(alpha = 0.38f),
    1.00f to Color.Black.copy(alpha = 0.80f),
)

private fun heroNameSize(name: String): TextUnit = when {
    name.length <= 12 -> 32.sp
    name.length <= 18 -> 26.sp
    else -> 22.sp
}

// ── Home ──────────────────────────────────────────────────────────────────────
// Top to bottom: hero card (the selected server's flag, full width) → search pill → server list
// → floating connect panel. HomeScreen stays stateless about the VPN: one HomeUiState plus event
// lambdas. The only state kept here is view state: the search query and the DISCONNECTING hold.
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onAddServer: () -> Unit,
    onSetMode: (ConnectMode) -> Unit,
    /** Ask for the public IP again — the address row is a tap target when the lookup failed. */
    onRetryIp: () -> Unit,
    /** Re-measure the pings of exactly the rows currently listed (pull-to-refresh). */
    onRefreshPings: (List<SavedConfig>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    // The ORDER is frozen per (set of servers, query) rather than recomputed on every change to
    // `allConfigs`: the live ping monitor replaces the whole list every few seconds, and sorting
    // on that would make a row jump under the user's finger when its ping ticks.
    val serverIds = state.allConfigs.map { it.id }.toSet()
    val orderedIds = remember(serverIds, query) {
        state.allConfigs.matching(query).byLatency().map { it.id }
    }
    val configById = state.allConfigs.associateBy { it.id }
    val servers = remember(orderedIds, state.allConfigs) {
        orderedIds.mapNotNull { configById[it] }
    }
    val activeId = state.activeConfig?.id

    var disconnecting by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase, disconnecting) {
        if (disconnecting) {
            delay(if (state.phase == ConnPhase.OFF) DISCONNECT_HOLD_MS else DISCONNECT_TIMEOUT_MS)
            disconnecting = false
        }
    }
    val visual = when {
        state.phase == ConnPhase.CONNECTING -> ConnVisual.CONNECTING
        disconnecting -> ConnVisual.DISCONNECTING
        state.phase == ConnPhase.CONNECTED -> ConnVisual.CONNECTED
        else -> ConnVisual.DISCONNECTED
    }

    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.25f).dp
        .coerceIn(HeroMinHeight, HeroMaxHeight)

    ProvideTextStyle(TextStyle(fontFamily = LuxuryFont)) {
        Box(modifier.fillMaxSize().background(PageGradient)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                HeroCard(
                    state = state,
                    visual = visual,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .padding(start = AppDs.S4, end = AppDs.S4, top = AppDs.S2)
                        .height(heroHeight),
                )
                Spacer(Modifier.height(AppDs.S3))
                ServerSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = AppDs.S4),
                )
                Spacer(Modifier.height(AppDs.S2))
                ServerList(
                    state = state,
                    servers = servers,
                    activeId = activeId,
                    query = query,
                    onSelectConfig = onSelectConfig,
                    onAddServer = onAddServer,
                    onRefreshPings = onRefreshPings,
                    modifier = Modifier.weight(1f),
                )
                ConnectPanel(
                    state = state,
                    visual = visual,
                    enabled = state.activeConfig != null,
                    onClick = {
                        if (state.phase == ConnPhase.CONNECTED) disconnecting = true
                        onTogglePower()
                    },
                    onSwipeUp = { onSetMode(ConnectMode.SMART) },
                    onSwipeDown = { onSetMode(ConnectMode.MANUAL) },
                    onRetryIp = onRetryIp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = AppDs.S3, end = AppDs.S3, top = AppDs.S2, bottom = AppDs.S2),
                )
            }
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────
// The selected server's flag fills the whole card edge to edge (Crop, clipped to the card's own
// radius). A scrim darkens only the top strip and the foot, where the menu chip and the country
// name sit; the middle of the flag is left at its own colour.
@Composable
private fun HeroCard(
    state: HomeUiState,
    visual: ConnVisual,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val shape = RoundedCornerShape(AppDs.RXl)

    val cfg = state.activeConfig
    val liveCountry = countryCodeToName(state.headerCountryCode)
    // The config's name is the fallback only when the country is unknown.
    val liveName = liveCountry.ifBlank {
        cfg?.let { c -> c.displayName.ifBlank { c.address } } ?: "No server"
    }
    val liveCity = cfg?.let { state.cityFor(it) }.orEmpty()
    // Held while connecting so the headline does not flicker as the exit geo resolves.
    var stableName by remember { mutableStateOf(liveName) }
    var stableCity by remember { mutableStateOf(liveCity) }
    LaunchedEffect(liveName, liveCity, state.phase) {
        if (state.phase != ConnPhase.CONNECTING) {
            stableName = liveName
            stableCity = liveCity
        }
    }
    val holding = state.phase == ConnPhase.CONNECTING
    val name = if (holding) stableName else liveName
    val city = if (holding) stableCity else liveCity
    val ping = cfg?.pingMs ?: -1
    val caption = listOfNotNull(
        city.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
        ping.takeIf { it >= 0 }?.let { "$it ms" },
    ).joinToString(" · ")

    val flagCountry = state.heroFlagCountry
    var lastFlagCountry by remember { mutableStateOf(flagCountry) }
    if (flagCountry.isNotBlank()) lastFlagCountry = flagCountry
    val flagAlpha by animateFloatAsState(
        targetValue = if (flagCountry.isNotBlank()) 1f else 0f,
        animationSpec = appMotion(reduce, PHASE_FADE_MS),
        label = "heroFlag",
    )

    Box(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = AppDs.ElevHero,
                shape = shape,
                clip = false,
                ambientColor = AppDs.ShadowAmbient,
                spotColor = AppDs.ShadowSpot,
            )
            .clip(shape)
            .background(AppDs.Surface),
    ) {
        if (flagAlpha > 0.01f) {
            HeaderFlag(
                countryCode = lastFlagCountry,
                modifier = Modifier.fillMaxSize().alpha(flagAlpha),
            )
        }
        Box(Modifier.fillMaxSize().background(HeroScrim))
        // The card's own edge, lit from above like every other framed surface on Home.
        Box(Modifier.fillMaxSize().border(1.dp, heroEdge, shape))

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).padding(AppDs.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(onClick = onOpenSettings)
            Spacer(Modifier.weight(1f))
            HeroStatusChip(visual)
        }

        AnimatedContent(
            targetState = name to caption,
            transitionSpec = {
                if (reduce) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    (fadeIn(tween(240, delayMillis = 60)) +
                        slideInVertically(tween(280)) { it / 5 }) togetherWith fadeOut(tween(120))
                }
            },
            label = "heroHeadline",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = AppDs.S5, end = AppDs.S5, bottom = AppDs.S4),
        ) { (n, c) ->
            Column {
                Text(
                    n,
                    fontSize = heroNameSize(n),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = HeadlineInkShadow),
                )
                if (c.isNotEmpty()) {
                    Text(
                        c,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = HeroInkShadow),
                    )
                }
            }
        }
    }
}

/** Connection state as a small glass chip on the flag: a dot and one word. */
@Composable
private fun HeroStatusChip(visual: ConnVisual, modifier: Modifier = Modifier) {
    val reduce = appReduceMotion()
    val shape = RoundedCornerShape(50)
    val dot by animateColorAsState(
        targetValue = when (visual) {
            ConnVisual.DISCONNECTED -> AppDs.TextMid
            ConnVisual.CONNECTING -> AppDs.Accent
            ConnVisual.CONNECTED -> RefLive
            ConnVisual.DISCONNECTING -> AppDs.TextMid
        },
        animationSpec = appMotion(reduce, 260),
        label = "statusDot",
    )
    Row(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, AppDs.Border, shape)
            .padding(horizontal = AppDs.S3, vertical = AppDs.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(AppDs.S2))
        AnimatedContent(
            targetState = when (visual) {
                ConnVisual.DISCONNECTED -> "Not connected"
                else -> visual.title()
            },
            transitionSpec = {
                fadeIn(appMotion(reduce, 160)) togetherWith fadeOut(appMotion(reduce, 100))
            },
            label = "statusLabel",
        ) { label ->
            Text(
                label,
                fontSize = TypeCaption.first,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ── Menu button ───────────────────────────────────────────────────────────────
// The tapered three-line mark on a glass chip, so it holds its edge on any flag without the old
// under-stroke. Same press language as the rest of Home: no ripple, a quick sink and a soft return.
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animatePressScale(pressed, AppDs.PressScaleSmall)
    val shape = RoundedCornerShape(AppDs.RMd)
    Box(
        modifier
            .size(AppDs.Control)
            .scale(scale)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, AppDs.Border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Menu",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MenuGlyphSize)) {
            val stroke = MenuStroke.toPx()
            val gap = MenuLineGap.toPx()
            val cy = size.height / 2f
            listOf(cy - gap, cy, cy + gap).forEachIndexed { i, y ->
                drawLine(
                    Color.White,
                    Offset(stroke / 2f, y),
                    Offset(size.width * MenuLineRatios[i] - stroke / 2f, y),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

// ── Search ────────────────────────────────────────────────────────────────────
// A matte pill under the hero. Focus is one quiet change: the border and the icon take the accent
// and the fill lifts a step — no glow.
@Composable
private fun ServerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val fill by animateColorAsState(
        if (focused) AppDs.SurfaceRaised else AppDs.Surface, appMotion(reduce, 180), label = "searchFill",
    )
    val border by animateColorAsState(
        if (focused) AppDs.Accent.copy(alpha = 0.55f) else AppDs.Border, appMotion(reduce, 180), label = "searchBorder",
    )
    val iconTint by animateColorAsState(
        if (focused) AppDs.Accent else AppDs.TextMid, appMotion(reduce, 180), label = "searchIcon",
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(AppDs.SearchHeight)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .padding(start = AppDs.S4, end = if (query.isEmpty()) AppDs.S4 else AppDs.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(AppDs.IconMd),
        )
        Spacer(Modifier.width(AppDs.S3))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Search location or server",
                    style = SearchFieldStyle,
                    color = AppDs.TextLow,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = SearchFieldStyle,
                cursorBrush = SolidColor(AppDs.Accent),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(AppDs.ClearTap)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Clear search") { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = AppDs.TextMid,
                    modifier = Modifier.size(AppDs.IconSm),
                )
            }
        }
    }
}

// ── Server list ───────────────────────────────────────────────────────────────
// Lives directly on the page (no card of its own). Pull-to-refresh re-measures the pings of the
// rows currently shown; the sweep's completion, not a timer, is what retracts the spinner.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerList(
    state: HomeUiState,
    servers: List<SavedConfig>,
    activeId: String?,
    query: String,
    onSelectConfig: (SavedConfig) -> Unit,
    onAddServer: () -> Unit,
    onRefreshPings: (List<SavedConfig>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    // Gesture → work: the state flips itself to refreshing when the drag passes the threshold.
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { onRefreshPings(servers) }
    }
    // Work → indicator: VpnTab clears refreshingPings when the sweep ends; the 10s cap only
    // guards against a stuck indicator.
    LaunchedEffect(state.refreshingPings) {
        if (state.refreshingPings) {
            pullState.startRefresh()
            delay(10_000L)
            pullState.endRefresh()
        } else {
            pullState.endRefresh()
        }
    }
    val listState = rememberLazyListState()
    val favContext = LocalContext.current
    var favoriteIds by remember { mutableStateOf(AppSettings.favoriteServers(favContext)) }

    Box(
        modifier
            .fillMaxWidth()
            .nestedScroll(pullState.nestedScrollConnection)
            // The spinner is parked above the box at rest; clipping keeps it invisible until pulled.
            .clipToBounds(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = AppDs.S1, bottom = AppDs.S6),
        ) {
            if (servers.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        allEmpty = state.allConfigs.isEmpty(),
                        searching = query.isNotBlank(),
                        onAdd = onAddServer,
                    )
                }
            }
            itemsIndexed(servers, key = { _, cfg -> cfg.id }) { _, cfg ->
                val title = state.rowTitle(cfg)
                ServerRow(
                    title = title,
                    countryCode = state.countryCodeFor(cfg),
                    pingMs = cfg.pingMs,
                    isActive = cfg.id == activeId,
                    isFavorite = cfg.id in favoriteIds,
                    onToggleFavorite = {
                        favoriteIds =
                            if (cfg.id in favoriteIds) favoriteIds - cfg.id else favoriteIds + cfg.id
                        AppSettings.setFavoriteServers(favContext, favoriteIds)
                    },
                    onClick = { onSelectConfig(cfg) },
                )
            }
        }
        PullToRefreshContainer(
            state = pullState,
            containerColor = RefElev2,
            contentColor = RefTextHi,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // The list dissolves into the page above the connect panel instead of being cut flat.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(AppDs.S6)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AppDs.Bg))),
        )
    }
}

/**
 * One server: flag · name · ping · signal · favourite, on a fixed grid. Transparent at rest with a
 * hairline divider inset past the flag; pressed steps the surface lighter; the active server sits
 * on a raised surface with an accent hairline (both cross-fade, so choosing a server reads as a
 * change of state rather than a jump).
 */
@Composable
private fun ServerRow(
    title: String,
    countryCode: String,
    pingMs: Int,
    isActive: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val reduce = appReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(AppDs.RMd)
    // Same RGB at alpha 0 rather than Color.Transparent, so the fade does not pass through black.
    val fill by animateColorAsState(
        targetValue = when {
            pressed -> AppDs.SurfacePressed
            isActive -> AppDs.Surface
            else -> AppDs.Surface.copy(alpha = 0f)
        },
        animationSpec = appMotion(reduce, 120),
        label = "rowFill",
    )
    val edge by animateColorAsState(
        targetValue = AppDs.Accent.copy(alpha = if (isActive) 0.40f else 0f),
        animationSpec = appMotion(reduce, 220),
        label = "rowEdge",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDs.S3)
            .heightIn(min = AppDs.ServerRowHeight)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Use $title",
                onClick = onClick,
            )
            .drawBehind {
                if (!isActive) {
                    val start = (AppDs.S3 + AppDs.ServerFlag + AppDs.S3).toPx()
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(
                        color = AppDs.Hairline,
                        start = Offset(start, y),
                        end = Offset(size.width - AppDs.S3.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(start = AppDs.S3, end = AppDs.S1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagBadge(countryCode, AppDs.ServerFlag, Modifier.border(1.dp, AppDs.Border, CircleShape))
        Spacer(Modifier.width(AppDs.S3))
        Text(
            title,
            fontSize = TypeSubtitle.first,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            color = AppDs.TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AppDs.S3))
        Text(
            if (pingMs >= 0) "$pingMs ms" else "—",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AppDs.TextMid,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(AppDs.PingWidth),
        )
        Spacer(Modifier.width(AppDs.S3))
        PingSignalBars(pingMs)
        Spacer(Modifier.width(AppDs.S1))
        FavoriteButton(title = title, isFavorite = isFavorite, onToggle = onToggleFavorite)
    }
}

/**
 * Four 3dp bars, 5/8/11/14dp tall. How many light up follows the measured ping; green is the
 * healthy tier and amber/red the two degraded ones (the only semantic colours on Home).
 */
@Composable
private fun PingSignalBars(pingMs: Int, modifier: Modifier = Modifier) {
    val filled = when {
        pingMs < 0 -> 0
        pingMs < 50 -> 4
        pingMs < 100 -> 3
        pingMs < 180 -> 2
        else -> 1
    }
    val on = when {
        pingMs < 0 -> AppDs.TextLow
        filled >= 3 -> RefLive
        filled == 2 -> RefLoadMed
        else -> RefLoadHigh
    }
    Row(
        modifier.semantics {
            contentDescription = if (pingMs < 0) "Signal not measured" else "Signal $filled of 4"
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp).forEachIndexed { index, height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (index < filled) on else Color.White.copy(alpha = 0.12f)),
            )
        }
    }
}

@Composable
private fun FavoriteButton(title: String, isFavorite: Boolean, onToggle: () -> Unit) {
    val reduce = appReduceMotion()
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (previous != isFavorite) {
            previous = isFavorite
            if (!reduce && isFavorite) {
                pop.animateTo(1.22f, tween(90))
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val tint by animateColorAsState(
        if (isFavorite) AppDs.Accent else AppDs.TextLow, appMotion(reduce, 160), label = "favTint",
    )
    Box(
        Modifier
            .size(AppDs.Control)
            .clip(CircleShape)
            .clickable(onClickLabel = "Toggle favorite", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove $title from favorites" else "Add $title to favorites",
            tint = tint,
            modifier = Modifier
                .size(AppDs.IconMd)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
        )
    }
}

// ── Connect panel ─────────────────────────────────────────────────────────────
// The bottom floating panel: inset from the screen's edges, softly rounded on every corner, matte
// and a shade darker than the page so it reads as recessed rather than stuck on. Its hairline
// takes a trace of the accent while a tunnel is coming up or up. The public-IP row unfolds inside
// it once connected (tap to copy; retry if the lookup failed — see [IpCard]).
@Composable
private fun ConnectPanel(
    state: HomeUiState,
    visual: ConnVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onRetryIp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val shape = RoundedCornerShape(AppDs.RXl)
    val edge by animateColorAsState(
        targetValue = when (visual) {
            ConnVisual.CONNECTED -> AppDs.Accent.copy(alpha = 0.28f)
            ConnVisual.CONNECTING -> AppDs.Accent.copy(alpha = 0.18f)
            else -> AppDs.Hairline
        },
        animationSpec = appMotion(reduce, 320),
        label = "panelEdge",
    )
    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = AppDs.ElevPanel,
                shape = shape,
                clip = false,
                ambientColor = AppDs.ShadowAmbient,
                spotColor = AppDs.ShadowSpot,
            )
            .clip(shape)
            .background(PanelFill)
            .border(1.dp, edge, shape)
            .padding(AppDs.S3),
    ) {
        ConnectButton(
            visual = visual,
            enabled = enabled,
            onClick = onClick,
            onSwipeUp = onSwipeUp,
            onSwipeDown = onSwipeDown,
        )
        AnimatedVisibility(
            visible = visual == ConnVisual.CONNECTED,
            enter = expandVertically(appMotion(reduce, 260)) + fadeIn(appMotion(reduce, 260)),
            exit = shrinkVertically(appMotion(reduce, 200)) + fadeOut(appMotion(reduce, 140)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = AppDs.S2, end = AppDs.S2, top = AppDs.S3, bottom = AppDs.S1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "IP address",
                    fontSize = TypeCaption.first,
                    fontWeight = TypeCaption.second,
                    color = AppDs.TextLow,
                )
                Spacer(Modifier.weight(1f))
                IpCard(state = state, onRetryIp = onRetryIp)
            }
        }
    }
}

/**
 * The connect control: a wide matte button with a round icon well at its left and the state in
 * words beside it. The four states each change the icon, the words, the edge and the motion:
 *
 *   DISCONNECTED   — bolt in white on a charcoal well, neutral hairline.
 *   CONNECTING     — bolt pulses in the accent while an accent arc turns around the well; the
 *                    edge takes the accent.
 *   CONNECTED      — the well fills with the accent and the bolt turns white; a faint accent tint
 *                    settles over the button.
 *   DISCONNECTING  — the arc turns the other way, the bolt dims, taps are ignored until it lands.
 *
 * Tap toggles the tunnel; a vertical drag switches Smart / Manual (also exposed as two named
 * accessibility actions), exactly as the old disc did.
 */
@Composable
private fun ConnectButton(
    visual: ConnVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(AppDs.RLg)
    val busy = visual == ConnVisual.CONNECTING || visual == ConnVisual.DISCONNECTING

    val scale = animatePressScale(pressed)
    val live by animateFloatAsState(
        if (visual == ConnVisual.CONNECTED) 1f else 0f, appMotion(reduce, 280), label = "connectLive",
    )
    val working by animateFloatAsState(
        if (busy) 1f else 0f, appMotion(reduce, 200), label = "connectBusy",
    )
    val fill by animateColorAsState(
        if (pressed) AppDs.SurfacePressed else AppDs.SurfaceRaised, appMotion(reduce, 100), label = "connectFill",
    )
    val edge by animateColorAsState(
        targetValue = when (visual) {
            ConnVisual.DISCONNECTED -> AppDs.Border
            ConnVisual.CONNECTING -> AppDs.Accent.copy(alpha = 0.55f)
            ConnVisual.CONNECTED -> AppDs.Accent.copy(alpha = 0.70f)
            ConnVisual.DISCONNECTING -> AppDs.Border
        },
        animationSpec = appMotion(reduce, 260),
        label = "connectEdge",
    )
    val boltColor by animateColorAsState(
        targetValue = when (visual) {
            ConnVisual.DISCONNECTED -> AppDs.TextHi
            ConnVisual.CONNECTING -> AppDs.Accent
            ConnVisual.CONNECTED -> Color.White
            ConnVisual.DISCONNECTING -> AppDs.TextMid
        },
        animationSpec = appMotion(reduce, 220),
        label = "connectBolt",
    )

    // The arc's rotation. Driven only while busy, and cancelled by the effect's key on any state
    // change, so nothing runs (or keeps the display refreshing) while the tunnel is idle or up.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(busy, reduce) {
        if (busy && !reduce) {
            while (true) {
                spin.snapTo(0f)
                spin.animateTo(360f, tween(CONNECT_SPIN_MS, easing = LinearEasing))
            }
        }
    }

    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { ModeSwipeThreshold.toPx() } }
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val swipeDown by rememberUpdatedState(onSwipeDown)
    val label = when (visual) {
        ConnVisual.CONNECTED -> "Disconnect"
        ConnVisual.CONNECTING -> "Cancel connecting"
        ConnVisual.DISCONNECTING -> "Disconnecting"
        ConnVisual.DISCONNECTED -> "Connect"
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(AppDs.ConnectHeight)
            .scale(scale)
            .clip(shape)
            .background(fill)
            .background(AppDs.ButtonTopLight)
            .background(AppDs.Accent.copy(alpha = 0.10f * live))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * 0.45f))),
                shape,
            )
            .pointerInput(threshold) {
                var travel = 0f
                detectVerticalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragCancel = { travel = 0f },
                    onDragEnd = {
                        when {
                            travel <= -threshold -> swipeUp()
                            travel >= threshold -> swipeDown()
                        }
                        travel = 0f
                    },
                ) { _, delta -> travel += delta }
            }
            .clickable(
                enabled = enabled && visual != ConnVisual.DISCONNECTING,
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction("Switch to Smart mode") { swipeUp(); true },
                    CustomAccessibilityAction("Switch to Manual mode") { swipeDown(); true },
                )
            }
            .padding(horizontal = AppDs.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectWell(
            visual = visual,
            live = live,
            working = working,
            boltColor = boltColor,
            spinDegrees = { spin.value },
        )
        Spacer(Modifier.width(AppDs.S4))
        AnimatedContent(
            targetState = visual,
            transitionSpec = {
                fadeIn(appMotion(reduce, 160)) togetherWith fadeOut(appMotion(reduce, 100))
            },
            label = "connectLabel",
            modifier = Modifier.weight(1f),
        ) { v ->
            Column {
                Text(
                    v.title(),
                    fontSize = TypeTitle.first,
                    fontWeight = TypeTitle.second,
                    color = AppDs.TextHi,
                    maxLines = 1,
                )
                Text(
                    v.caption(),
                    fontSize = TypeCaption.first,
                    fontWeight = TypeCaption.second,
                    color = AppDs.TextMid,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The icon well: charcoal disc, accent fill when live, a turning arc while busy, and the bolt. */
@Composable
private fun ConnectWell(
    visual: ConnVisual,
    live: Float,
    working: Float,
    boltColor: Color,
    spinDegrees: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(AppDs.ConnectWell)) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension / 2f
        drawCircle(AppDs.SurfacePressed)
        drawCircle(AppDs.Border, radius = radius - stroke / 2f, style = Stroke(stroke))
        if (live > 0.01f) {
            drawCircle(AppDs.Accent.copy(alpha = live), radius = radius * (0.88f + 0.12f * live))
        }
        if (working > 0.01f) {
            val turn = if (visual == ConnVisual.DISCONNECTING) -spinDegrees() else spinDegrees()
            val arc = if (visual == ConnVisual.DISCONNECTING) AppDs.TextMid else AppDs.Accent
            rotate(degrees = turn, pivot = center) {
                drawArc(
                    color = arc.copy(alpha = working),
                    startAngle = -90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // The bolt pulses gently while connecting; every other state draws it steady.
        val pulse = if (visual == ConnVisual.CONNECTING && working > 0.01f) {
            0.65f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(spinDegrees() / 57.29578f))
        } else {
            1f
        }
        val bounds = ConnectBoltPath.getBounds()
        val fit = size.minDimension * 0.46f / maxOf(bounds.width, bounds.height)
        translate(
            left = (size.width - bounds.width * fit) / 2f - bounds.left * fit,
            top = (size.height - bounds.height * fit) / 2f - bounds.top * fit,
        ) {
            scale(scale = fit, pivot = Offset.Zero) {
                drawPath(
                    path = ConnectBoltPath,
                    color = boltColor.copy(alpha = boltColor.alpha * pulse),
                    style = Fill,
                )
            }
        }
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {}, onRetryIp = {},
        onRefreshPings = {},
    )
}

@Preview(name = "Home · smart, off", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenSmartPreview() {
    HomeScreen(
        state = HomeUiState(
            // Smart mode's own pick: the fastest of the six, chosen for the user.
            activeConfig = previewConfigs.first(),
            allConfigs = previewConfigs,
            mode = ConnectMode.SMART,
            networkName = "Wi-Fi",
            publicIp = "139.162.191.1",
        ),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {}, onRetryIp = {},
        onRefreshPings = {},
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
            mode = ConnectMode.SMART,
            networkName = "Wi-Fi",
            // Still this device's own address: the exit IP is only resolved once the
            // tunnel is actually up.
            publicIp = "139.162.191.1",
        ),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {}, onRetryIp = {},
        onRefreshPings = {},
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
            mode = ConnectMode.SMART,
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {}, onRetryIp = {},
        onRefreshPings = {},
    )
}

@Preview(name = "Home · no servers", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(activeConfig = null, allConfigs = emptyList()),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {}, onRetryIp = {},
        onRefreshPings = {},
    )
}

