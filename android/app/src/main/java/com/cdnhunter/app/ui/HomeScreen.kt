package com.cdnhunter.app.ui

// ── HOME ──────────────────────────────────────────────────────────────────────
// Rebuilt from design-reference/vpn-home-v3-clean-bg.html — a visual reference kept in the repo,
// never read by the build. The mockup frames a 390px device, so its CSS pixels map 1:1 onto dp
// and the trailing comments below name the rule each number came from.
//
// Layout, top to bottom:
//   • page        — near-black vertical gradient
//   • header flag — the active server's country as an ambient wash behind the whole hero
//   • top bar     — hamburger → Settings, account glyph → Profile
//   • hero        — one centred column: country headline, public IP (tap to copy) and session
//                   clock, then the power disc alone on the screen's own axis
//   • browse card — the server list, with its own controls (Main/Custom, add, search) as the
//                   first row inside it
//   • usage card  — floats over the list bottom: traffic ring, live speed, chevron → Locations
//
// The hero states one fact per line, in the order a user asks for them, and nothing twice: the
// country is the headline and does not repeat in the server row, the city is a caption under it.
//
// Smart / Manual is Home's other axis, orthogonal to which server is selected: Manual acts on
// the row the user tapped, Smart on whichever saved server currently measures best (SmartMode.kt
// scores latency, jitter and dropped probes over a rolling window). It is switched by swiping the
// power circle up or down, by its two accessibility actions, or from Settings' "Server choice".
//
// HomeScreen() stays stateless about the VPN: one HomeUiState snapshot plus event lambdas, so
// VpnTab() remains the single owner of connection state. The only state kept here is view state
// nothing else needs — selected tab, whether search is open, the query.
//
// The flag is light rather than a picture: one image across the whole header, drawn Crop so no
// source is warped on one axis (a square asset, a 5:3 flagcdn SVG and a 19:10 one all keep their
// proportions), then faded by an alpha mask ([HeaderFlagFadeX], [HeaderFlagFadeY]) rather than by
// a coat of paint — where the mask eases, the page's own gradient shows through, so the artwork
// has no edges of its own. Between artwork and mask sit a slight desaturation
// ([HEADER_FLAG_SATURATION]) and a vertical scrim ([HeaderFlagScrim]) shaped to be heavy only
// where text lands: the status bar at the top, the band the card's first rows sit over at the
// foot, light through the middle. Worst case is a white flag level with the top bar, where the
// scrim puts it near #6b6b6c — which [RefTextHi] and the white power disc clear, and the dimmer
// inks do not, which is what [HeroDepthScrim] and the glass chips are for. No flag to draw
// (country unresolved, asset missing, still decoding) falls back to [HeaderFlagFallback].
//
// Choosing another server crossfades the flag rather than cutting to it: 420ms in over 260ms out.
//
// The only light is ambient and there is deliberately very little of it — three soft directional
// sources onto the power control at a few percent, as static gradient brushes rather than
// [Modifier.blur], white idle and blue connected.
//
// There is no green here. Connected is one colour, [RefLive], stated in four places: the
// headline ink, the power ring, the power mark and the usage ring's accent. No ON/OFF pill and no
// pending state — the screen is either connected or it isn't. That teal is the header's ink and
// nothing below it; the top bar's glyphs are navigation, not state, and stay white either way.
//
// All motion respects the system's "remove animations" setting (see [rememberReduceMotion]).


import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import com.cdnhunter.app.vpn.AppSettings
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ── Palette — the mockup's :root custom properties, verbatim ───────────────────
private val RefBg = Color(0xFF0A0B0F)          // --bg (canonical, matches Auth)

/**
 * The browse card's own base: the same luminance as [RefBg], a degree or two colder.
 *
 * This is the part of the frost that survives everywhere. [panelFrost]'s wash is gone by the
 * middle of the card, but a panel whose black is a colder black than the page's reads as glass
 * over the whole of its height — and at this distance from [RefBg] nothing about it is
 * nameable as a colour, which is the point.
 */
private val RefPanelBg = Color(0xFF020305)

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
private val RefElev1 = Color(0xFF0F1116)       // --bg-elev-1
private val RefElev2 = Color(0xFF15171E)       // --bg-elev-2
private val RefBorder = Color(0xFF23262F)      // --border
private val RefTextHi = Color(0xFFF6F7F9)      // --text-hi
private val RefTextMid = Color(0xFF9BA0AC)     // --text-mid
private val RefTextLow = Color(0xFF656B78)     // --text-low

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
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 3f),
    blurRadius = 14f,
)
private val RefAccent = Color(0xFF3B82F6)      // --accent (canonical, matches Auth)
private val RefTeal = Color(0xFF35D6B8)        // --teal
/**
 * The connected colour: a deep, refined teal. There is no green on this screen.
 *
 * The mockup's `--green` (#34D17A) is gone entirely — at 118dp of lit ring plus a crown
 * wash it read as a highlighter rather than as a state, and against a
 * flag it turned every country into a swamp. This is [RefTeal]'s hue held a little deeper
 * and a shade less bright, which still clears 4.5:1 on [RefBg] for the ring and the
 * caption but sits back into the page instead of shouting off it. Deliberately the same
 * family as the usage ring's accent, so "live" is one colour everywhere on the screen
 * rather than two.
 */
private val RefLive = Color(0xFF22B9A2)
/**
 * The room's light when the tunnel is up: blue, not the state's own teal.
 *
 * The disc reports the *state* in [RefLive] — mark and ring — and the light reports
 * that the thing is lit, so making them different hues is what stops the whole top of the
 * screen becoming one teal blob. Vivid and slightly over-bright on purpose: this is the
 * one light in the app allowed to be theatrical. It is thrown by [drawHeroAtmosphere]
 * across the whole backdrop; there is no longer a halo around the button itself.
 */
private val RefGlowOn = Color(0xFF2563EB)
// The connecting state carries no colour of its own any more: the working spinner, the disc
// mark and the room's light are all monochrome (see [PowerRing], [phaseLight]). What used to be
// a yellow-orange "working" hue (RefWorking) and its on-white ink (RefWorkingInk) are gone.

/**
 * The same teal, dark enough to read *on* white — used for the power button's mark.
 *
 * [RefLive] is tuned to glow on near-black; on the button's white face it is a pale,
 * thin mark that fails contrast. This is the same hue at roughly a third of the
 * lightness, which clears 4.5:1 on the disc's lightest stop.
 */
private val RefLiveInk = Color(0xFF07786B)
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PowerInk = Color(0xFF0C0E14)       // .power-btn svg colour

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
//                at full strength. Blue rather than teal because the ring and the mark
//                already carry [RefLive]: state is teal, light is blue.
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
private val HeroBleed = 180.dp

/**
 * What the backdrop measures on the first frame only, before the header's rows have been
 * measured once (see [HomeScreen]'s `heroContentPx`). Within a few dp of the real value on
 * a normal phone at default font scale, so nothing visibly moves when the measurement
 * lands.
 */
private val HeroBackdropFallback = 340.dp

/** How long the phase crossfade takes — the ink, the light, and every surface. */
private const val PHASE_FADE_MS = 520


// ── Connected colour ──────────────────────────────────────────────────────────
// One thing says "the tunnel is up": the ambient light turns blue. There is no
// tone system, no per-state palette, no pill, no pending state, and — deliberately
// — nothing painted over the connect bar's flag, so the flag's own colours are the
// real ones whether the tunnel is up or down.
//
// [RefTeal] is used at full strength in the one place the mockup uses it and the flag
// isn't underneath: the usage ring's `conic-gradient(var(--teal) …)`.

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
private val PowerSize = 140.dp
private val PanelCorner = 28.dp      // .browse-card border-radius
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
/**
 * Where each row's hairline starts: [ListPad] + [RowFlagSize], so the divider begins
 * exactly at the flag's trailing edge and the flags read as one unbroken column down the
 * list. The mockup's literal 52px was that same relationship at the old 36dp flag; it is
 * written as the sum now so shrinking the flag again can't leave the line floating in the
 * middle of it.
 */
private val DividerStart = ListPad + RowFlagSize

private val CardCorner = 20.dp       // --radius-lg on .bottom-card
private val CardMargin = 14.dp       // .bottom-card margin / bottom
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


/**
 * Whether the device has animations turned off — developer options' "Animation off",
 * Battery Saver, or Settings → Accessibility → "Remove animations" all set the same
 * animator duration scale to zero.
 *
 * Every animation on this screen reads this and collapses to [snap] when it is true:
 * an ambient light that fades, a mode word that swaps and a chevron that brightens
 * are all decoration, and decoration is exactly what that setting turns off.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !android.animation.ValueAnimator.areAnimatorsEnabled()
        } else {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }
    }
}

/** [tween] normally, an instant cut when the device has animations off. */
private fun <T> motionSpec(reduce: Boolean, durationMs: Int): FiniteAnimationSpec<T> =
    if (reduce) snap() else tween(durationMs)

// `headerInk` — the phase's colour as a single [Color], crossfaded on [PHASE_FADE_MS] — used to
// live here. Its last caller was the country headline, which now takes a [Brush] instead so it
// can carry the flag's own hues ([headlineBrush]), and a brush is not something a Color helper
// can return. The phase-to-colour rule it held is stated inside that function:
// white in flight, [RefLive] up, the country's tint at rest — connecting no longer carries a
// colour of its own (see [headlineBrush] and [phaseLight]).

/**
 * The colour of the light in the room for [phase]: white idle, white while connecting, blue up.
 *
 * Connecting is deliberately the *same* white as idle — the room does not change colour while
 * an attempt is in flight, only the ring's turning comet says work is happening. What separates
 * connecting from idle is `lit`, which tightens and strengthens the same white wash. Connected
 * colour and the *state's* colour deliberately disagree. Teal is the state — it is the
 * ring, the headline and the mark on the disc — but a teal room over a
 * flag drained the warm half of the world's flags, and a saturated room light on
 * near-black is the hardest thing on this palette to keep clean at low alpha. Blue reads
 * as light rather than as a tint, so the artwork keeps its own colour and the teal is left
 * to say what the tunnel is doing.
 */
@Composable
private fun phaseLight(phase: ConnPhase): Color {
    val reduce = rememberReduceMotion()
    val target = when (phase) {
        ConnPhase.OFF -> Color.White
        ConnPhase.CONNECTING -> Color.White
        ConnPhase.CONNECTED -> RefGlowOn
    }
    val color by animateColorAsState(target, motionSpec(reduce, PHASE_FADE_MS), label = "phaseLight")
    return color
}

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


/**
 * Daily data-usage cap the Home ring measures against: 5 GB per local day.
 * The ring is driven by [HomeUiState.dailyUsageBytes] (a real, persisted local
 * daily total), not the per-session counter, so the fill reflects the whole day.
 */
private const val USAGE_DAILY_CAP_BYTES = 5L * 1024 * 1024 * 1024

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
 * dirty. The shadow treatment ([HeaderFlagScrim]) is untouched — this changes the artwork's
 * colour, not the shade over it.
 */
private const val HEADER_FLAG_SATURATION = 1.06f

/**
 * How much the flag's tones are expanded around mid-grey before the scrim is applied.
 *
 * A contrast scale just over 1 pushes the darks down and the lights up around a 50% pivot, which
 * is what makes the colour bands read as *distinct* rather than as one even wash — the vivid,
 * "punchy" half of the brief that saturation alone does not buy. Kept modest (1.16) so the flag
 * stays a flag and does not clip its brightest field to flat white. Applied in the same
 * [ColorMatrix] as [HEADER_FLAG_SATURATION]; the scrim over the artwork is unchanged.
 */
private const val HEADER_FLAG_CONTRAST = 1.16f

/**
 * How much the artwork itself gives up before [HeaderFlagScrim] is even applied.
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
 * by dimming the artwork; it is paid for by the scrim, which is where it belongs, because
 * the scrim can be shaped — heavy exactly where text lands, light where the flag is just
 * flag. See [HeaderFlagScrim].
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

/**
 * A soft vertical scrim, drawn *inside* the masked flag layer, so it tapers away exactly where
 * the flag does — it darkens the artwork, never the page.
 *
 * Shaped so it is heavy only where text actually lands: a little at the head, lighter through
 * the middle where the flag is allowed to be a flag, heavier again at the foot under the browse
 * card's first rows. The artwork itself stays near-opaque ([HEADER_FLAG_ALPHA]) and this is what
 * buys legibility back — dimming the whole flag to protect two bands is what used to make it
 * read as grey.
 *
 * The head is much lighter than it was (0.18 against 0.58) because it is no longer alone up
 * there: the flag now runs to the very top of the screen, behind the system clock, and the
 * even dark glass over the whole band ([HeroDepthScrim], heaviest at its head) is what gives
 * the system status-bar glyphs their field now that the opaque black bar is gone.
 */
private val HeaderFlagScrim = Brush.verticalGradient(
    // Darkened on request: the flag now reads as a deeper, more tinted backdrop. Every stop
    // carries more black than before (was 0.18/0.08/0.04/0.08/0.20) so the artwork sits further
    // back under glass while still legibly a flag — the middle is where it breathes, the top and
    // foot (where the clock and the browse-card head land) are heaviest.
    0.00f to Color.Black.copy(alpha = 0.44f),
    0.20f to Color.Black.copy(alpha = 0.32f),
    0.50f to Color.Black.copy(alpha = 0.26f),
    0.80f to Color.Black.copy(alpha = 0.34f),
    1.00f to Color.Black.copy(alpha = 0.50f),
)

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
 * avoid one. [HeaderFlagScrim]'s heavy top stop protects the glyphs instead, by darkening
 * the flag rather than by removing it.
 *
 * The foot no longer reaches zero either, and that is the change that makes the artwork a
 * *background* rather than a panel at the top of one. This layer is the whole screen now
 * (see [HeroBackdrop]), so a mask that ended at Transparent would have put the flag's own
 * bottom edge across the middle of the page — the exact "the flag doesn't cover the
 * screen" reading it was drawn to avoid. It now holds 0.88 to the very last row instead of
 * falling to 0.52, so there is no point down the page where the flag can be said to stop;
 * what keeps the list legible over it is the browse card's own translucent fill
 * ([panelTopFade]) plus [HeaderFlagScrim]'s heavier foot, both of which sit *over* the
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
 * The flag panel: the artwork and its scrim, faded out on three sides by an alpha mask.
 *
 * The artwork and [HeaderFlagScrim] are drawn into an offscreen layer, then
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
    val reduce = rememberReduceMotion()
    val context = LocalContext.current
    // Saturation and contrast in one matrix: chroma just over full so the colours read as the
    // country's own and confident, then a mild contrast expansion around mid-grey so the bands
    // stay distinct rather than washing into one field. The scrim over the artwork is separate
    // and unchanged — see [HeaderFlagScrim].
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
                                initialScale = 1.04f,
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
                // The bundled/flagcdn flags crop centred. Sweden's local artwork is a 2:1 landscape
                // illustration whose subject — the Stockholm skyline and ship — sits on the RIGHT
                // half, so a plain centre-crop into this ~1.3:1 landscape box would trim the far
                // buildings off the right. A gentle right bias keeps the whole skyline (and the
                // ship) in frame while still leaving the yellow cross's vertical bar visible; the
                // only thing given up is a sliver of the left blue field. Still uniform Crop —
                // nothing is stretched. See [FlagLayer].
                val flagAlignment = if (local != null) {
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
        // Inside the masked layer, so it darkens the flag and tapers away with it.
        Box(Modifier.matchParentSize().background(HeaderFlagScrim))
    }
}

/**
 * The one drawn copy of the flag, filling whatever box [modifier] gives it.
 *
 * One rule for both sources: scale uniformly until the box is covered, clip
 * the overhang. [ContentScale.Crop] against the box, and nothing between the image and that
 * box — no forced ratio, no unbounded width. The flag's own proportions are what get drawn,
 * whatever the source's are (a square bundled asset, a 5:3 German flagcdn SVG, a 19:10
 * American one) and whatever the box's are on this particular phone.
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
    /**
     * Ask for the public IP again. The lookup is a network call that can come back empty on a
     * censored or captive network, and the hero's address line is a tap target when it does —
     * see [MetaRow]. The caller owns [HomeUiState.ipLookupPending].
     */
    onRetryIp: () -> Unit,
    /**
     * Re-measure the ping of every server currently listed, in place. Called by the
     * browse list's pull-to-refresh gesture with exactly the rows the user can see —
     * the tab's servers, after the search filter — so refreshing a search result set
     * does not sweep the whole library. The caller owns
     * [HomeUiState.refreshingPings], which is what dismisses the indicator.
     */
    onRefreshPings: (List<SavedConfig>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // The search toggle lives in the card's own header row now (opposite the public-IP readout),
    // so its action is defined here and handed down to [BrowseCard].
    val toggleSearch: () -> Unit = {
        searchOpen = !searchOpen
        if (!searchOpen) query = ""
    }

    // Every server the app knows about, filtered by the search box and sorted by latency.
    // The Main/Custom tab selector is gone, so there is one list and it is all of them.
    //
    // The ORDER is frozen per (set of servers, query) rather than recomputed on every
    // change to `allConfigs`. The live ping monitor replaces the whole configs list
    // every few seconds (a new list identity even though only pingMs changed); sorting
    // on that identity re-sorted the rows on each sample, so a server whose ping ticked
    // up would jump position under the user's finger. Here the latency sort runs once
    // for a given id-set + query, yielding a fixed id order; each recomposition then
    // just re-projects the latest config objects (with fresh ping values) onto that
    // frozen order. Adding/removing a server or changing the query recomputes it.
    val serverIds = state.allConfigs.map { it.id }.toSet()
    val orderedIds = remember(serverIds, query) {
        state.allConfigs.matching(query).byLatency().map { it.id }
    }
    val configById = state.allConfigs.associateBy { it.id }
    val servers = remember(orderedIds, state.allConfigs) {
        orderedIds.mapNotNull { configById[it] }
    }
    val activeId = state.activeConfig?.id

    // How tall the hero measured, in px. The backdrop behind it is drawn [HeroBleed] taller
    // (see [HeroBackdrop]); the connect disc is docked on the hero's foot at this height.
    var heroContentPx by remember { mutableStateOf(0) }
    val heroHeight = with(LocalDensity.current) {
        if (heroContentPx > 0) heroContentPx.toDp() else HeroBackdropFallback
    }

    Box(modifier.fillMaxSize().background(PageGradient)) {
        // Behind everything: the flag under dark glass, and the light.
        HeroBackdrop(
            state = state,
            heroHeight = heroHeight,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            // The hero: hamburger, country, address. Its measured height is where the card
            // begins and where the connect disc docks — the card rises to meet the disc's foot.
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.onSizeChanged { heroContentPx = it.height },
            )
            BrowseCard(
                state = state,
                servers = servers,
                activeId = activeId,
                query = query,
                searchOpen = searchOpen,
                onQueryChange = { query = it },
                onSelectConfig = onSelectConfig,
                onAddServer = onAddServer,
                onToggleSearch = toggleSearch,
                onRefreshPings = onRefreshPings,
                onRetryIp = onRetryIp,
                modifier = Modifier.weight(1f),
            )
        }

        // The public IP and the list's add/search controls all live in the card's own top row now
        // (see [BrowseCard]) — the IP on the left where the "+" button used to be, search on the right.

        // The connect disc, docked on the seam: its centre sits on [heroHeight] — the Header's
        // foot, which is the browse card's top edge — so its lower half rests on the card's head
        // (a dock well, [CardTopRoom]) and its upper half floats over the flag. Drawn after the
        // card, so it is the topmost layer. The mode is still switched by a vertical drag on it
        // (up = Smart, down = Manual), plus the two named accessibility actions.
        PowerCircle(
            mode = state.mode,
            phase = state.phase,
            enabled = state.activeConfig != null,
            onClick = onTogglePower,
            onSwipeUp = { onSetMode(ConnectMode.SMART) },
            onSwipeDown = { onSetMode(ConnectMode.MANUAL) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (heroHeight - PowerSize / 2).coerceAtLeast(0.dp)),
        )

        // The public IP no longer rides the flag. It now lives in the browse card's own top row,
        // on the left where the "+" add-server button used to be (see [BrowseCard]).

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

// ── Hero backdrop ─────────────────────────────────────────────────────────────
// The artwork and the light, and nothing else. Drawn as a sibling *behind* everything rather
// than as the hero's background, at the size of the whole screen. Two heights are at work:
//
//   the flag          — edge to edge, and vertically from under the status bar to [FlagFootRise]
//                       *short of* the hero's last row. Height is zoom here, so a shorter box is
//                       a less cropped flag; its last 14% dissolves rather than stopping.
//   the light + floor — a band [bandHeight] tall at the top: the hero's rows plus [HeroBleed].
//                       The atmosphere's geometry is written in fractions of its own size (the
//                       horizon bloom sits at `size.height`, on the hero's foot, which is what
//                       fuses hero and card), so letting it fill the screen would drop that
//                       bloom to the bottom of the page.
//
// No clip, no border, no shadow — a rounded foot, a hairline and a cast shadow are what a card
// is, and this is not one. The layers stack, from the back: [ChromeBg] over the top band (so the
// artwork is never composited against nothing mid-crossfade, while the band's foot stays
// translucent for the card to sit over) → the flag → [drawHeroAtmosphere] over the band. The flag
// crossfades on [PHASE_FADE_MS], as does the light's colour.
@Composable
private fun HeroBackdrop(state: HomeUiState, heroHeight: Dp, modifier: Modifier = Modifier) {
    // The two heights this composable is made of, and they now run in opposite directions —
    // see the section comment. The light's band reaches [HeroBleed] *past* the hero's rows;
    // the flag stops [FlagFootRise] *short* of them, which is what un-zooms it.
    val bandHeight = heroHeight + HeroBleed
    val flagHeight = (heroHeight - FlagFootRise + FlagCardBleed).coerceAtLeast(0.dp)
    val reduce = rememberReduceMotion()
    val phase = state.phase
    // The wash is gated on there being a country to draw, not on the phase — see
    // [HomeUiState.heroFlagCountry]. Held while it fades out so the artwork does not
    // vanish on the frame the last server is deleted.
    val flagCountry = state.heroFlagCountry
    var lastFlagCountry by remember { mutableStateOf(flagCountry) }
    if (flagCountry.isNotBlank()) lastFlagCountry = flagCountry
    val flagAlpha by animateFloatAsState(
        targetValue = if (flagCountry.isNotBlank()) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "heroFlag",
    )
    // White idle, white while connecting, blue connected — the light's own colour, animated so
    // changing state reads as the room changing colour rather than as a repaint.
    val ambient = phaseLight(phase)
    // Only the *connected* room is lit. Connecting no longer raises the wash — the turning comet
    // on the ring is the sole "working" cue, so there is no glow while an attempt is in flight.
    val lit = phase == ConnPhase.CONNECTED

    Box(modifier) {
        // The floor under the artwork, over the band only: it fades out across the bleed so
        // the card's own translucent top is not backed by opaque chrome. Without it, a flag
        // crossfading at 40% alpha would show the page gradient through itself.
        Box(Modifier.fillMaxWidth().height(bandHeight).background(HeroFloor))
        if (flagAlpha > 0.01f) {
            // The flag's box is the hero's rows *minus* [FlagFootRise] — not the light's
            // band, and certainly not the screen. [ContentScale.Crop] scales to *cover* this
            // box, so the box's shape is the flag's zoom: every dp of height taken off here
            // is width handed back to the artwork. See [FlagFootRise] for the arithmetic and
            // for the trade it makes at the hero's foot.
            HeaderFlag(
                countryCode = lastFlagCountry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(flagHeight)
                    .alpha(flagAlpha),
            )
        }
        // Shade, then light, in that order — see [HeroDepthScrim]. Both cover the whole
        // band rather than the flag's box, so the shading does not stop where the artwork
        // does and leave the card's edge on a differently-lit patch.
        Box(
            Modifier
                .fillMaxWidth()
                .height(bandHeight)
                .drawBehind {
                    drawRect(HeroDepthScrim)
                    drawRect(HeroDepthEdge)
                    // A vignette centred on the power disc: gives the artwork a middle, which
                    // is where all of the hero's ink is.
                    drawRect(
                        Brush.radialGradient(
                            colors = HeroVignetteStops,
                            center = Offset(size.width / 2f, size.height * 0.44f),
                            radius = size.width * 0.98f,
                        )
                    )
                    // The frosted glass itself: a cold, even wash over the whole band, so the
                    // flag reads as artwork seen *through* dark tinted glass rather than as a
                    // plain bright field. It is a colour, not a blur — see [panelFrost] for
                    // why blur is deliberately avoided on this screen. No cast shadow at the
                    // foot any more: the card announces its own edge ([drawPanelTopEdge]) and
                    // the docked disc casts its own, so the flag no longer needs a dark fade
                    // above the seam.
                    drawRect(HeroGlassFrost)
                }
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(bandHeight)
                .drawBehind { drawHeroAtmosphere(ambient, lit) }
        )
    }
}

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
 * Deliberately *not* the same job as [HeaderFlagScrim], and the two do not double up by
 * accident. That one lives inside the flag's own masked layer and is about the artwork —
 * keeping a saturated field from shouting, and tapering its head and foot. This one covers the
 * whole band, flag or no flag, and is now an *even* dark veil rather than a bright-through-the-
 * middle one:
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
    0.00f to Color.Black.copy(alpha = 0.52f),
    0.14f to Color.Black.copy(alpha = 0.42f),
    0.34f to Color.Black.copy(alpha = 0.36f),
    0.55f to Color.Black.copy(alpha = 0.35f),
    0.74f to Color.Black.copy(alpha = 0.38f),
    0.90f to Color.Black.copy(alpha = 0.42f),
    1.00f to Color.Black.copy(alpha = 0.46f),
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
private val HeroFlagSpace = 72.dp

/** The breathing room the hero holds under the status-bar inset, so the country plate sits a
 *  comfortable step below the system clock/battery rather than flush against them. */
private val HeroTopGap = 10.dp

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

/**
 * Small read-only status glyphs for the two protections the person cares most about at a
 * glance — Kill Switch and Ad Blocker. Lit up (full opacity + a soft glow) when the
 * corresponding [AppSettings] flag is on, dimmed to a faint outline when it is off. Not
 * clickable: this is a status readout, not a settings shortcut — the person still toggles
 * these from Settings.
 */
@Composable
private fun StatusFeatureIcons(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val killSwitchOn = remember { AppSettings.killSwitchEnabled(context) }
    val adBlockerOn = remember { AppSettings.adBlockerEnabled(context) }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusFeatureIcon(
            icon = Icons.Rounded.WifiOff,
            label = "Kill Switch",
            active = killSwitchOn,
        )
        StatusFeatureIcon(
            icon = Icons.Rounded.FrontHand,
            label = "Ad Blocker",
            active = adBlockerOn,
        )
    }
}

@Composable
private fun StatusFeatureIcon(icon: ImageVector, label: String, active: Boolean) {
    val tint = if (active) AnanasTeal else Color.White.copy(alpha = 0.75f)
    Box(
        Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            // Teal glow behind the icon when active — same teal as Settings toggles.
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.00f to AnanasTeal.copy(alpha = 0.50f),
                                0.42f to AnanasTeal.copy(alpha = 0.22f),
                                0.72f to AnanasTeal.copy(alpha = 0.06f),
                                1.00f to Color.Transparent,
                            ),
                            radius = size.minDimension * 0.60f,
                        )
                    },
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Header(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            // This column is the topmost content now — the black status bar that used to own
            // the system inset is gone — so it carries [statusBarsPadding] itself and the
            // hamburger sits directly on the flag under the system clock.
            .statusBarsPadding()
            // A clear gap under the system icons so the country plate never rides up against the
            // status bar clock/battery — the plate is the topmost content and, flush to the inset,
            // its ink was crowding the system glyphs. This holds it a comfortable step below them.
            .padding(top = HeroTopGap)
            // Left margin only. The right edge runs flush to the screen so the country plate's
            // fade-from-right bleeds off the bezel rather than floating in an inset gutter.
            .padding(start = ScreenPad),
    ) {
        // The top row of the flag: the menu held to the left, the country name to the right on
        // its own dark plate. Both ride the flag rather than a chrome bar. Aligned to the top so
        // the tall country plate does not drag the short menu mark down with it.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            MenuButton(
                onClick = onOpenSettings,
                // Nudged out a hair so the glyph sits on the screen margin.
                modifier = Modifier.offset(x = (-2).dp),
            )
            Spacer(Modifier.weight(1f))
            CountryHeadline(state)
        }
        // The Kill Switch / Ad Blocker status glyphs used to ride the flag here. They now live in
        // the browse card's masthead (see [BrowseCard]) so nothing crowds the flag or the connect
        // disc that docks on the hero's foot — and, with that block gone, the hero measures shorter,
        // so the disc and the card it docks on both sit higher up the screen.
        // The open flag under the top row. The public-IP readout is no longer here — it now floats
        // below-right of the connect disc (drawn as an overlay by [HomeScreen]), so this stays clear
        // flag under the country plate.
        Spacer(Modifier.height(HeroFlagSpace))
        // Reserve the docked disc's upper half over the flag. The disc itself is drawn by
        // [HomeScreen] as an overlay, centred on this column's measured foot — which is the
        // browse card's top edge — so its lower half rests on the card.
        Spacer(Modifier.height(HeroDockWell))
    }
}

// ── Menu button ───────────────────────────────────────────────────────────────
// The one navigation mark left at the top of the screen: an asymmetric hamburger, top-left,
// on the flag rather than in a chrome bar (the black status bar it used to live in is gone).
//
// Three lines, not three *equal* lines: the top runs the full width, each below it shorter, so
// the mark tapers to the left. Bigger than the 20dp icon it replaces, and drawn by hand rather
// than as a Material glyph so the taper and the rounded caps are exactly as drawn. A soft dark
// under-stroke sits a pixel below each white line, so the whole thing holds its edge on a bright
// stripe of an arbitrary flag without needing a chip or a plate under it.

/** The hamburger's drawn size, inside a [TapTarget] touch area. Larger than the old glyph. */
private val MenuGlyphSize = 27.dp

/** Line weight, and the gap from the mark's centre to its outer lines. */
private val MenuStroke = 2.5.dp
private val MenuLineGap = 6.5.dp

/** How far the shadow line sits below its white line, and its colour. */
private val MenuShadowDrop = 1.dp
private val MenuShadow = Color.Black.copy(alpha = 0.30f)

/** The three line widths as fractions of the mark's width: full, then shorter, then shortest. */
private val MenuLineRatios = listOf(1.0f, 0.72f, 0.48f)

/** How far the mark sinks while held — a touch deeper than a plate button since it has no
 *  fill or shadow of its own to lose, so the scale carries the whole press on its own. */
private const val MenuPressScale = 0.88f

@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The old bug: a default bounded ripple on a 48dp CircleShape tap target whose glyph is
    // CenterStart. The ripple filled the whole circle, so its centre sat ~10dp to the RIGHT of
    // the left-aligned hamburger — the press "shifted right". Fix: drop the ripple (indication =
    // null) and feed back with a scale on the glyph itself, which pivots about the glyph's own
    // centre, so the press reads exactly under the finger. Same down-fast / up-sprung asymmetry
    // as the power disc, so the one navigation mark presses like every other button on Home.
    val scale by animateFloatAsState(
        targetValue = if (pressed) MenuPressScale else 1f,
        animationSpec = if (reduce) {
            snap()
        } else if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "menuPress",
    )
    Box(
        modifier
            .size(TapTarget)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Menu",
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.size(MenuGlyphSize).scale(scale)) {
            val stroke = MenuStroke.toPx()
            val gap = MenuLineGap.toPx()
            val drop = MenuShadowDrop.toPx()
            val cy = size.height / 2f
            val ys = listOf(cy - gap, cy, cy + gap)
            // Shadow pass first, then the white lines over it, so the mark reads lit.
            ys.forEachIndexed { i, y ->
                val w = size.width * MenuLineRatios[i]
                drawLine(MenuShadow, Offset(0f, y + drop), Offset(w, y + drop), stroke, StrokeCap.Round)
            }
            ys.forEachIndexed { i, y ->
                val w = size.width * MenuLineRatios[i]
                drawLine(Color.White, Offset(0f, y), Offset(w, y), stroke, StrokeCap.Round)
            }
        }
    }
}

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
): Modifier = this
    .scale(if (pressed) EMBOSS_PRESS_SCALE else 1f)
    .shadow(
        elevation = if (pressed) elevation / 3 else elevation,
        shape = shape,
        clip = false,
        ambientColor = HeroShadowAmbient,
        spotColor = HeroShadowSpot,
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

/** The label's font, chosen by the *combined* "Country · City" length so the fixed compact plate
 *  never has to resize: the text adapts, the frame does not. Sized up on request — the country and
 *  city both read larger now — so each step is a few sp above the old ramp; it still steps down for
 *  a long pairing so the fixed plate is never overrun (the backstop past that is ellipsis). */
private fun headlineFontFor(label: String): TextUnit = when {
    label.length <= 13 -> 26.sp
    label.length <= 19 -> 21.sp
    label.length <= 26 -> 18.sp
    else -> 15.sp
}

/** The city line sits under the country name at a fixed, smaller step -- it never competes with
 *  the country for the ramp, so it stays legible even when the country name itself is long. */
private val HeadlineCitySize = 14.sp

/** The left-to-right wipe when the name changes: the new label is revealed progressively across
 *  its glyphs rather than swapped or crossfaded. */
private const val REVEAL_MS = 460

@Composable
private fun CountryHeadline(state: HomeUiState, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val cfg = state.activeConfig
    val liveCountry = countryCodeToName(state.headerCountryCode)
    // The config's name is the fallback only when the country is unknown — an empty
    // headline would be worse than a technical one.
    val liveName = liveCountry.ifBlank {
        cfg?.let { c -> c.displayName.ifBlank { c.address } } ?: "No server"
    }
    val liveCity = cfg?.let { state.cityFor(it) }.orEmpty()

    var stableName by remember { mutableStateOf(liveName) }
    var stableCity by remember { mutableStateOf(liveCity) }
    LaunchedEffect(liveName, liveCity, state.phase) {
        if (state.phase != ConnPhase.CONNECTING) {
            stableName = liveName
            stableCity = liveCity
        }
    }
    val connecting = state.phase == ConnPhase.CONNECTING
    val name = if (connecting) stableName else liveName
    val city = if (connecting) stableCity else liveCity

    // Country and city on ONE line, joined by a middot: "Sweden · Stockholm". The join is only
    // added when both halves exist and differ, so a missing city can never leave a dangling "· "
    // and a city that duplicates the name is not repeated. The plain string is what the font ramp
    // sizes against and what keys the reveal; the two halves are drawn with different weights.
    val hasCity = name.isNotBlank() && city.isNotBlank() && !city.equals(name, ignoreCase = true)
    val plainLabel = when {
        hasCity -> "$name · $city"
        name.isNotBlank() -> name
        else -> city
    }
    // Country BOLD, city REGULAR weight — the two are differentiated by weight, not size, so both
    // read at the (now larger) ramp size while the eye still separates the place from its city.
    val label = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name.ifBlank { city }) }
        if (hasCity) {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.62f))) {
                append(" · ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(city) }
        }
    }

    // The wipe: one Animatable driven from 0 (nothing shown) to 1 (fully revealed), reset and
    // replayed whenever the label changes. Under reduced motion it simply parks at 1 — no wipe.
    // Keyed on the plain string, so the held value during CONNECTING does not re-trigger it.
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(plainLabel, reduce) {
        if (reduce) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(REVEAL_MS))
        }
    }

    val labelSize = headlineFontFor(name.ifBlank { city })
    Box(
        modifier
            // Fixed compact frame — the plate never resizes with the text (the font adapts instead).
            .width(HeadlinePlateWidth)
            .height(HeadlinePlateHeight)
            // No card, no wash -- just the text sitting straight on the flag. Legibility comes
            // entirely from [HeadlineInkShadow] now, not from a plate behind it.
            .padding(start = 24.dp, end = 18.dp, top = 14.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Country and city stacked, not joined by a middot -- the country reads first and large,
        // the city sits directly under it at a fixed smaller step. Same left-to-right wipe as
        // before, now clipping the whole column instead of a single line.
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.drawWithContent {
                clipRect(right = size.width * reveal.value) { this@drawWithContent.drawContent() }
            },
        ) {
            Text(
                name.ifBlank { city },
                fontSize = labelSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.End,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = HeadlineInkShadow),
            )
            if (hasCity) {
                Text(
                    city,
                    fontSize = HeadlineCitySize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    textAlign = TextAlign.End,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = HeadlineInkShadow),
                )
            }
        }
    }
}

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

@Composable
private fun IpCard(state: HomeUiState, onRetryIp: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reduce = rememberReduceMotion()
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
                    fadeIn(motionSpec(reduce, 260)) togetherWith fadeOut(motionSpec(reduce, 140))
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
                    // In flight: a simple, quiet loading word — never digits. The value is not known
                    // yet, so nothing that could look like a malformed address is drawn.
                    IpKind.CHECKING -> Text(
                        "Checking…",
                        fontSize = IpPlaceholderSize,
                        fontWeight = FontWeight.Medium,
                        color = RefTextMid,
                        maxLines = 1,
                        style = TextStyle(shadow = HeroInkShadow),
                    )
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
                        Spacer(Modifier.width(6.dp))
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
//                disconnects. What reports "lit" is the ring and its halo, in the thallo teal
//                [ConnectTeal]; the room still washes [RefGlowOn] blue behind the hero.
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

/** The disc itself, inside [PowerSize]'s box — the rest of the box is the ring band. */
private val PowerDiscSize = 118.dp

/** The ring's own weight, and how far outside the disc it is drawn.
 *
 *  3dp of stroke rather than the 2.5dp the 84dp disc carried: the ring's whole job is to
 *  be read from wherever the phone is being held, and a hairline that was proportionate
 *  around a small disc reads as a scratch around a 118dp one. The gap stays at 3dp — it
 *  is the space that makes the ring a rim on the disc rather than a second circle near
 *  it, and that reads the same at any diameter. */
private val PowerRingStroke = 3.dp
private val PowerRingGap = 3.dp

/** The thallo teal, used for every animated part of the connect ring — the connecting arc, the
 *  connected ring and its halo. Matches the wordmark; scoped to this control so it never leaks into
 *  the blue room light ([RefGlowOn]) behind the hero. */
private val ConnectTeal = Color(0xFF4DB6AC)

/** One full turn of the connecting arc. Slow and even — a premium sweep, not a busy spinner. This
 *  is the app's only indeterminate progress and the only motion that runs unasked. */
private const val CONNECT_SPIN_MS = 1400

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

@Composable
private fun PowerCircle(
    mode: ConnectMode,
    phase: ConnPhase,
    enabled: Boolean,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = phase == ConnPhase.CONNECTED
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduce) {
            snap()
        } else if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
        },
        label = "powerSink",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) POWER_PRESS_SCALE else 1f,
        animationSpec = if (reduce) {
            snap()
        } else if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "powerPress",
    )

    val fillTarget = when (phase) {
        ConnPhase.OFF -> 0f
        ConnPhase.CONNECTING -> 1f
        ConnPhase.CONNECTED -> 1f
    }
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = if (reduce) {
            snap()
        } else {
            tween(
                durationMillis = if (fillTarget == 0f) BOLT_DRAIN_MS else BOLT_FILL_MS,
                easing = if (fillTarget == 0f) FastOutSlowInEasing else LinearEasing,
            )
        },
        label = "powerBoltFill",
    )
    val markStrong = enabled || phase != ConnPhase.OFF
    // Idle bolt is a struck base, but pure ink over the dark well went muddy. Lift it with a touch
    // of cool slate and more presence so the OFF/idle mark reads clearly instead of dark-on-dark.
    val boltBase = lerp(PowerInk, Color(0xFF2B3446), 0.30f)
    val boltTrack = if (markStrong) boltBase.copy(alpha = 0.72f) else boltBase.copy(alpha = 0.46f)
    val boltFill = if (connected) RefGlowOn else RefGlowOn.copy(alpha = if (markStrong) 0.9f else 0.3f)

    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { ModeSwipeThreshold.toPx() } }
    val label = when {
        connected -> "Disconnect"
        phase == ConnPhase.CONNECTING -> "Cancel connecting"
        else -> "Connect"
    }

    val infinite = rememberInfiniteTransition(label = "powerBreathe")
    val breathe by if (reduce) {
        remember { mutableStateOf(0f) }
    } else {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "powerBreatheVal",
        )
    }
    val ambientDepth = if (connected) breathe * 0.25f else 0f

    Box(modifier.size(PowerSize), contentAlignment = Alignment.Center) {
        // Connected glow: a soft teal radial that pools below the disc, fading in/out with
        // the connected state. No animation loop — just a crossfade — so it costs nothing
        // on weak devices. The bloom is drawn BEHIND the ring and disc, on the Box itself.
        if (connected) {
            val glowAlpha = 0.55f
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.00f to ConnectTeal.copy(alpha = glowAlpha),
                                0.40f to ConnectTeal.copy(alpha = glowAlpha * 0.45f),
                                0.70f to ConnectTeal.copy(alpha = glowAlpha * 0.12f),
                                1.00f to Color.Transparent,
                                center = Offset(size.width / 2f, size.height * 0.78f),
                                radius = size.width * 0.72f,
                            ),
                        )
                    },
            )
        }
        PowerRing(phase = phase, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .size(PowerDiscSize)
                .scale(scale)
                .clip(CircleShape)
                .background(PowerWellBg)
                .pointerInput(mode, threshold) {
                    var travel = 0f
                    detectVerticalDragGestures(
                        onDragStart = { travel = 0f },
                        onDragCancel = { travel = 0f },
                        onDragEnd = {
                            when {
                                travel <= -threshold -> onSwipeUp()
                                travel >= threshold -> onSwipeDown()
                            }
                            travel = 0f
                        },
                    ) { _, delta -> travel += delta }
                }
                .clickable(
                    enabled = enabled,
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
                        CustomAccessibilityAction("Switch to Smart mode") {
                            onSwipeUp(); true
                        },
                        CustomAccessibilityAction("Switch to Manual mode") {
                            onSwipeDown(); true
                        },
                    )
                }
                .drawWithCache {
                    val depth = (0.65f + sink * 0.35f + ambientDepth * 0.10f).coerceIn(0f, 1f)
                    val darkArc = Brush.radialGradient(
                        0.72f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.50f * depth),
                        center = Offset(size.width * 0.30f, size.height * 0.28f),
                        radius = size.minDimension * 0.92f,
                    )
                    val lightArc = Brush.radialGradient(
                        0.72f to Color.Transparent,
                        1.00f to Color.White.copy(alpha = 0.10f * depth),
                        center = Offset(size.width * 0.74f, size.height * 0.76f),
                        radius = size.minDimension * 0.92f,
                    )
                    val innerRim = Brush.radialGradient(
                        0.90f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.35f * depth),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.5f,
                    )
                    onDrawBehind {
                        drawCircle(darkArc)
                        drawCircle(lightArc)
                        drawCircle(innerRim)
                    }
                }
                .border(PowerRimStroke, PowerWellRim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PowerBolt(
                trackColor = boltTrack,
                fillColor = boltFill,
                fill = fill,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

// ── Connect bolt ──────────────────────────────────────────────────────────────
/** The bolt outline as vector path data, baked to absolute coordinates in the [BOLT_VW]×[BOLT_VH]
 *  space. Converted from the source SVG (a bolt-shaped cutout in a flipped, scaled rectangle) by
 *  applying its transform and keeping only the bolt subpath. Mirrors `res/drawable/ic_connect_bolt`
 *  — keep the two in sync if the asset changes. */
private const val CONNECT_BOLT_PATH_DATA =
    "M701.70,3.90 C703.60,5.90 705.40,9.20 705.80,11.20 C706.70,16.10 703.90,23.00 699.90,25.90 L696.70,28.20 L644.20,28.70 C603.40,29.10 591.20,29.50 589.40,30.50 C583.50,33.90 581.40,43.30 585.20,49.50 C588.80,55.40 590.90,55.70 622.90,55.70 C655.10,55.70 656.70,56.00 660.60,62.70 C664.50,69.30 662.60,77.10 655.80,82.10 C653.10,84.20 651.70,84.20 592.80,84.70 C536.70,85.20 532.40,85.30 529.80,87.00 C524.60,90.40 522.90,98.10 525.80,104.40 C529.20,111.40 527.40,111.10 579.90,111.70 L627.30,112.20 L624.60,117.50 C620.60,125.50 613.10,133.10 605.40,136.90 L598.70,140.20 L540.70,140.80 L482.70,141.30 L470.00,144.00 C440.00,150.30 416.10,163.60 395.00,185.50 C379.10,202.10 367.30,221.90 360.40,243.70 C354.90,261.10 354.40,264.70 346.20,348.70 C336.50,448.10 336.60,447.60 331.60,462.70 C318.70,501.20 289.10,532.60 253.10,545.80 C236.00,552.10 230.40,552.90 199.20,553.40 C160.70,554.10 154.90,552.70 142.60,540.20 C129.60,526.90 127.60,511.00 135.60,482.70 C137.50,475.80 141.50,460.70 144.60,449.20 C147.60,437.60 153.00,417.00 156.60,403.40 C165.80,369.00 166.80,360.20 163.70,342.10 C158.20,310.40 139.30,283.50 108.20,263.20 C103.00,259.80 93.30,253.70 86.70,249.60 C62.10,234.40 47.00,221.00 34.00,203.00 C0.70,156.80 0.00,97.60 32.30,52.40 C52.20,24.40 84.90,4.60 117.40,0.80 C121.40,0.30 253.80,0.00 411.50,0.10 L698.40,0.20 L701.70,3.90 Z M142.10,83.10 C127.00,87.30 114.20,100.30 109.70,116.10 C108.40,120.60 108.10,124.50 108.40,131.90 C108.90,141.00 109.20,142.40 113.10,150.30 C119.10,162.40 124.30,167.10 147.10,181.10 C169.90,195.20 176.30,199.80 187.70,210.10 C203.50,224.50 218.70,248.00 225.70,268.60 C234.40,294.40 234.90,324.30 227.00,352.70 C221.60,372.30 208.80,398.60 195.40,417.60 C193.80,419.90 193.80,420.00 195.70,418.90 C199.50,416.90 213.30,402.20 219.30,393.70 C238.60,366.80 252.50,328.40 262.70,274.20 C267.40,249.10 268.40,245.00 273.10,230.90 C281.50,205.90 292.50,184.70 307.80,164.30 C336.60,126.00 375.90,98.40 421.00,84.80 L430.70,81.80 L288.70,81.80 C177.00,81.90 145.70,82.20 142.10,83.10 Z"

/** The bolt's source viewport, from the converted vector ([ic_connect_bolt]). Path coordinates
 *  above are in this space; [PowerBolt] scales it to fit its canvas. */
private const val BOLT_VW = 706.7f
private const val BOLT_VH = 554.1f

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
@Composable
private fun PowerBolt(
    trackColor: Color,
    fillColor: Color,
    fill: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        // Fit the bolt into the canvas, centred, preserving aspect. All drawing below is then in the
        // bolt's own path space, so the clip window can be expressed directly in path coordinates.
        val s = minOf(size.width / BOLT_VW, size.height / BOLT_VH)
        val dx = (size.width - BOLT_VW * s) / 2f
        val dy = (size.height - BOLT_VH * s) / 2f
        val trackBrush = Brush.verticalGradient(
            0f to lerp(trackColor, Color.White, 0.22f),
            0.55f to trackColor,
            1f to lerp(trackColor, Color.Black, 0.12f),
            startY = 0f,
            endY = BOLT_VH,
        )
        // The lit charge climbs the bolt from foot to tip. It reads green→blue: the app's teal at
        // the foot warming up into the blue room-light at the tip, kept rich through the middle (no
        // dark edge fade) so the fill sits inward rather than hugging the outline.
        val tealEnd = ConnectTeal.copy(alpha = fillColor.alpha)
        val fillBrush = Brush.verticalGradient(
            0f to lerp(fillColor, Color.White, 0.26f),
            0.42f to fillColor,
            0.78f to lerp(fillColor, tealEnd, 0.55f),
            1f to tealEnd,
            startY = 0f,
            endY = BOLT_VH,
        )
        withTransform({
            translate(dx, dy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            // The dark base bolt, always whole.
            drawPath(ConnectBoltPath, brush = trackBrush)
            // The lit fill, clipped to a window rising from the foot. At fill = 0 nothing is drawn;
            // at fill = 1 the whole bolt is covered.
            if (fill > 0.001f) {
                clipRect(left = 0f, top = BOLT_VH * (1f - fill), right = BOLT_VW, bottom = BOLT_VH) {
                    drawPath(ConnectBoltPath, brush = fillBrush)
                }
            }
            // A hair of bright rim over both, on the upper facets.
            drawPath(
                ConnectBoltPath,
                color = Color.White.copy(alpha = 0.14f),
                style = Stroke(width = BOLT_VW * 0.012f),
            )
        }
    }
}

/**
 * The connect ring in the band around the disc — a clean, minimal progress ring, not a mechanism.
 * No teeth, no gear, no crosshair; nothing that reads as machined hardware.
 *
 * A faint hairline track is always present, giving the disc a defined rim. Over it, three faces
 * crossfaded on [PHASE_FADE_MS]:
 *
 *   OFF        — the bare track only. Nothing moves.
 *   CONNECTING — one soft teal comet ([ConnectTeal]) sweeps smoothly around the track: a
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
@Composable
private fun PowerRing(phase: ConnPhase, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    // Continuous rotation of the connecting comet, in degrees. Driven only while connecting;
    // cancelling the effect on any phase change leaves it frozen. Idle it holds 0.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(phase, reduce) {
        if (phase == ConnPhase.CONNECTING && !reduce) {
            val turns = 1000f
            spin.animateTo(
                targetValue = spin.value + 360f * turns,
                animationSpec = tween((CONNECT_SPIN_MS * turns).toInt(), easing = LinearEasing),
            )
        }
    }
    // Crossfades: the comet shows while working, the lit ring + halo while up.
    val working by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTING) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "connectWorking",
    )
    val live by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTED) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "connectLive",
    )
    // The connected halo's slow breath: 0..1, ping-ponging while up. It nudges the halo's width
    // and alpha by a few percent — a sign of life, not a pulse. Off under reduce-motion.
    val breath = remember { Animatable(0f) }
    LaunchedEffect(phase, reduce) {
        if (phase == ConnPhase.CONNECTED && !reduce) {
            breath.snapTo(0f)
            breath.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            breath.snapTo(0f)
        }
    }
    Canvas(modifier) {
        val stroke = PowerRingStroke.toPx()
        val radius = (PowerDiscSize.toPx() / 2f) + PowerRingGap.toPx() + (stroke / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // Track removed — no permanent ring around the disc at rest.

        // CONNECTED ring removed — glow is now drawn below the disc in PowerCircle instead.

        // CONNECTING: one teal comet sweeps the track. The sweep gradient runs bright head →
        // transparent tail across [CONNECT_ARC_SWEEP]°, and the whole frame is rotated by [spin]
        // so it turns. Reduce-motion parks the head at twelve o'clock without turning.
        if (working > 0.01f) {
            val head = ConnectTeal.copy(alpha = 0.95f * working)
            rotate(degrees = if (reduce) 0f else spin.value, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        // sweepGradient's 0° is 3 o'clock, increasing clockwise; the arc below is
                        // drawn from -90° (twelve o'clock) clockwise, so the head sits at the arc's
                        // leading edge and the tail fades out behind it.
                        0f to Color.Transparent,
                        (CONNECT_ARC_SWEEP / 360f) to head,
                        1f to Color.Transparent,
                        center = center,
                    ),
                    startAngle = -90f,
                    sweepAngle = CONNECT_ARC_SWEEP,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

// linear-gradient(160deg, #ffffff 0%, #e7e9ee 55%, #d9dce3 100%)
// The inset disc's flat base colour -- a touch lighter than the panel it sits in so the
// carved well still reads against the background, with the dark/light arcs doing the
// actual depth work. No white "face" anymore: the disc is not a raised object.
private val PowerWellBg = Color(0xFF1E2130)

// The inner rim of the well: a hairline just inside the disc's own edge, dark enough to
// read as the lip of a carved hole rather than a drawn border.
private val PowerWellRim = Color.Black.copy(alpha = 0.4f)

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseCard(
    state: HomeUiState,
    servers: List<SavedConfig>,
    activeId: String?,
    query: String,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onAddServer: () -> Unit,
    onToggleSearch: () -> Unit,
    onRefreshPings: (List<SavedConfig>) -> Unit,
    onRetryIp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    // Two directions to keep in step, and they are deliberately separate effects.
    //
    // Gesture → work: the state flips itself to refreshing when the drag passes the
    // threshold, and this is the only place the sweep is started. `servers` is read here
    // rather than captured in a lambda higher up, so what gets re-measured is exactly
    // what the list is showing at the moment of the pull.
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) { onRefreshPings(servers) }
    }
    // Work → indicator: the sweep's own completion is what ends the animation. VpnTab
    // clears [HomeUiState.refreshingPings] when the last measurement lands or the whole
    // sweep times out, and only then does the spinner retract — so the indicator is
    // showing for exactly as long as work is happening, never a frame more or less.
    // A 10-second hard timeout is also set so the indicator never gets stuck on screen
    // if refreshingPings never clears (e.g. a state bug or a very slow network).
    LaunchedEffect(state.refreshingPings) {
        if (state.refreshingPings) {
            pullState.startRefresh()
            delay(10_000L)
            pullState.endRefresh()
        } else {
            pullState.endRefresh()
        }
    }
    val density = LocalDensity.current
    val fade = remember(density) { panelTopFade(with(density) { PanelFade.toPx() }) }
    val frost = remember(density) { panelFrost(with(density) { PanelFrostFade.toPx() }) }
    val listState = rememberLazyListState()
    val reduce = rememberReduceMotion()
    // Scroll elevation: the divider under the card's head brightens and casts a soft shadow once
    // the list has scrolled off its first row — the standard "there is content under this edge"
    // cue. Read off [rememberLazyListState] and animated (honouring reduced motion).
    val raised by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 2
        }
    }
    val listElevation by animateFloatAsState(
        targetValue = if (raised) 1f else 0f,
        animationSpec = motionSpec(reduce, 200),
        label = "listElevation",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner))
            // The card's own fill is a gradient now, not [RefBg] flat: translucent at its
            // top edge and opaque by [PanelFade] down. That is what merges it with the
            // hero. The backdrop above is drawn [HeroBleed] taller than the hero's rows, so
            // what is behind that first [PanelFade] is the flag and its horizon light — and
            // because the fill lets them through, the card's edge sits *in* the artwork
            // instead of starting below it. There is no gap and no visible join: the
            // brightest part of the transition is the light itself.
            .background(fade)
            // ...and the frost sits *over* that fill rather than replacing it, so the flag is
            // still what is behind the tab row — now behind cold glass instead of behind plain
            // dark. Order matters: fill, then wash, then the lit edges over both.
            .background(frost)
            // A very fine noise-like grain, drawn as two overlapping low-alpha radial washes
            // offset from centre, gives the panel a touch of material texture instead of a flat
            // colour fill -- cheap to draw and reads as quality at a glance without costing a
            // real blur pass.
            .drawWithCache {
                val grain1 = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.012f),
                    1.0f to Color.Transparent,
                    center = Offset(size.width * 0.18f, size.height * 0.06f),
                    radius = size.width * 0.9f,
                )
                val grain2 = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.008f),
                    1.0f to Color.Transparent,
                    center = Offset(size.width * 0.85f, size.height * 0.35f),
                    radius = size.width * 0.7f,
                )
                onDrawBehind {
                    drawRect(grain1)
                    drawRect(grain2)
                }
            }
            .drawBehind {
                drawPanelSheen()
                drawPanelTopEdge()
            }
    ) {
        // The card's masthead. The Kill Switch / Ad Blocker status glyphs sit on the LEFT (moved
        // off the hero flag, where they used to overlap the connect disc), and the search magnifier
        // on the RIGHT. They sit *at the top* rather than below the well — the connect disc docks in
        // the centre of this band, so the two corners are clear and neither control collides with it.
        // The band's height ([CardTopRoom]) still reserves the room the disc's lower half rests over.
        Box(
            Modifier
                .fillMaxWidth()
                .height(CardTopRoom),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(start = ScreenPad - 12.dp, end = ScreenPad - 12.dp)
                    .padding(top = 4.dp),
                // Status glyphs pinned to the leading (left) edge, search to the trailing (right)
                // edge — [Arrangement.SpaceBetween] pushes the two groups to opposite corners, both
                // clear of the disc that docks in the centre.
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusFeatureIcons()
                SearchToggle(open = searchOpen, onClick = onToggleSearch)
            }
        }
        SearchField(visible = searchOpen, query = query, onQueryChange = onQueryChange)
        // The divider between the card's head and the list, brightening on scroll ([listElevation]).
        ListScrollEdge(elevation = listElevation)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // The connection is on the Box, not the LazyColumn: the whole list area
                // is the drag surface, so a pull that starts on the empty hint or in the
                // gap beside a row works exactly like one that starts on a row.
                .nestedScroll(pullState.nestedScrollConnection)
                // [PullToRefreshContainer] positions itself with a negative
                // `translationY` of its own height, so at rest it is parked *above* this
                // Box rather than hidden inside it. Nothing here clipped, so the parked
                // spinner painted over the tab row — a grey puck sitting on top of the
                // word "Custom", which read as a rendering fault rather than as an
                // indicator. Clipping to bounds is what the pattern assumes: the spinner
                // is invisible until the drag pulls it down into the list's own area.
                .clipToBounds()
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // A little air at the head so the first row does not butt the divider, and enough
                // at the foot that the last row clears the floating usage card.
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
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
                itemsIndexed(servers, key = { _, cfg -> cfg.id }) { index, cfg ->
                    val isActive = cfg.id == activeId
                    ServerRow(
                        title = state.rowTitle(cfg),
                        subtitle = state.rowSubtitle(cfg),
                        countryCode = state.countryCodeFor(cfg),
                        pingMs = cfg.pingMs,
                        isActive = isActive,
                        showDivider = index < servers.lastIndex,
                        onClick = { onSelectConfig(cfg) },
                    )
                }
            }
            // The spinner, in the panel's own colours rather than the Material default's
            // — on this near-black list a container coloured from the light scheme is a
            // white puck.
            PullToRefreshContainer(
                state = pullState,
                containerColor = RefElev2,
                contentColor = RefTextHi,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

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

@Composable
private fun ListScrollEdge(elevation: Float, modifier: Modifier = Modifier) {
    val lineAlpha =
        LIST_EDGE_ALPHA_REST + (LIST_EDGE_ALPHA_RAISED - LIST_EDGE_ALPHA_REST) * elevation
    val shadowAlpha = LIST_EDGE_SHADOW_ALPHA * elevation
    Box(
        modifier
            .fillMaxWidth()
            .height(ListEdgeShadowHeight)
            .drawBehind {
                // The soft cast under the head, only once raised.
                if (shadowAlpha > 0.001f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = shadowAlpha),
                            1f to Color.Transparent,
                        ),
                    )
                }
                // The hairline itself, along the top edge.
                drawLine(
                    color = Color.White.copy(alpha = lineAlpha),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    )
}

/**
 * How far down the browse card the hero's artwork and light are still allowed through.
 *
 * 30dp, down from 84 — this is the number that fixed the "card sits too low" complaint that two
 * rounds of spacer tightening could not. A card whose first 84dp are translucent, starting at 62%
 * opacity, has no visible top edge: the eye reads the card as beginning wherever the paint finally
 * looks solid, some 80dp below the actual edge. The spacers were already tight; the *edge* was
 * missing. 30dp still dissolves the join over a hairline rather than over a chunk of layout, and
 * starts at 0.78 — see [panelTopFade] and [drawPanelTopEdge].
 */
private val PanelFade = 30.dp

/**
 * The dock well at the top of the browse card — the band of clear glass the connect disc's lower
 * half rests over.
 *
 * The disc is docked on the card's top edge again: its centre sits on the card's head ([heroHeight])
 * and its lower half overlaps down into the card. This well is that overlap depth plus a little air,
 * so the disc rests over empty glass and the card's own header row (the IP on the left, the
 * add/search controls on the right) sits *below* the disc's foot rather than colliding with it.
 * Sized off [PowerDiscSize] (the visible disc), not the full [PowerSize] touch box.
 */
private val CardTopRoom = PowerDiscSize / 2 + 28.dp

/**
 * How deep the icy wash over the card runs — a good deal further than [PanelFade].
 *
 * The two describe different things and are deliberately not the same number. [PanelFade] is
 * where the artwork behind the card stops coming through; this is how far the glass itself
 * appears to extend. Frost that ended exactly where the artwork does would draw a line across
 * the card at the one place the card is trying not to have one, so the wash carries on past
 * it, into the rows, and is under a hundredth of alpha by the time it gets there.
 */
private val PanelFrostFade = 340.dp

/** How far down the card's top edge the specular sweep in [drawPanelSheen] reaches.
 *
 *  48dp, down from 72 with [PanelFade]. The sheen is the light that appears to be *on* the
 *  glass; a sweep running well past the point where the glass has gone opaque reads as a
 *  gradient in the list instead. */
private val PanelSheenDepth = 48.dp

/**
 * The browse card's fill: translucent [RefBg] at its top edge, nearly opaque by [heightPx]
 * down — and *nearly* is deliberate.
 *
 * Anchored in pixels with an explicit `startY`/`endY` rather than in fractions, because the
 * card's height is whatever is left of the screen after the hero — a fractional stop would
 * put the fade at a different place on every device and inside the list on a tall one.
 * [TileMode.Clamp] is what holds the end value all the way to the foot.
 *
 * It starts at 0.78 rather than at nothing, and rather than at the 0.62 it started at before.
 * Fully transparent would be a prettier merge and a card with no visible beginning — which is
 * exactly the bug this round is fixing: at 0.62 over 84dp the top edge could not be located by
 * eye, so the tab row above it looked like it was floating in open artwork instead of sitting
 * on the card. 0.78 over [PanelFade]'s 30dp reads as an edge on the first pixel and still lets
 * the flag through it.
 *
 * It ends at 0.94 rather than at 1.0 because the flag is the whole screen's background now
 * (see [HeroBackdrop]) and an opaque card would be a lid over the bottom two thirds of it —
 * the artwork would still technically reach every edge and the user would still see it stop
 * at the top of the list. 0.94 over the flag's own artwork is a hint of
 * the country's colour behind the rows, worth a percent or two of luminance: every row's own
 * fill and every label on it are unchanged in contrast terms, and the page no longer has a
 * horizon across it.
 *
 * The base is [RefPanelBg], a colder near-black than the page's [RefBg], which is the first
 * half of the frost — see [panelFrost] for the rest, and for why none of this is a blur.
 */
private fun panelTopFade(heightPx: Float): Brush = Brush.verticalGradient(
    // Starts near-transparent so the flag shows through the card's top edge,
    // then ramps quickly to opaque so the effect stays contained to the top band.
    0.00f to RefPanelBg.copy(alpha = 0.10f),
    0.18f to RefPanelBg.copy(alpha = 0.55f),
    0.38f to RefPanelBg.copy(alpha = 0.88f),
    0.55f to RefPanelBg.copy(alpha = 0.97f),
    1.00f to RefPanelBg.copy(alpha = 1.00f),
    startY = 0f,
    endY = heightPx,
    tileMode = TileMode.Clamp,
)

/**
 * The frosted-glass wash over the browse card: an icy blue at its strongest along the card's
 * top edge, gone by [heightPx] down.
 *
 * There is no blur here, and that is a decision rather than a limitation.
 * [androidx.compose.ui.draw.blur] is a per-frame offscreen render pass; this card is the
 * thing the user scrolls a list inside; and what it would blur is flag artwork already dimmed
 * to a few percent of luminance behind it — a lot of GPU work for an effect nothing behind
 * the glass is sharp enough to show. What reads as frost at this scale is the *colour*: cold
 * light collecting at the edge of the pane and falling off into its body. So the card gets a
 * colder base ([RefPanelBg], under [panelTopFade]), this wash, and the specular sweep in
 * [drawPanelSheen] — which together look like a frosted pane and cost three gradients.
 *
 * Six eased stops for a fall-off spanning about 0.15 of alpha: on a near-black panel at 8-bit
 * depth a three-stop version of this bands visibly, and the bands land across the tab row
 * where they are hardest to miss. The last two are deliberately close together — the tail is
 * where a linear ramp shows its seam against the flat panel below it.
 *
 * Anchored in pixels like [panelTopFade] and for the same reason: the card's height is
 * whatever the hero leaves it, so a fractional stop would put the frost's edge somewhere
 * different on every device.
 */
private fun panelFrost(heightPx: Float): Brush = Brush.verticalGradient(
    0.00f to RefFrost.copy(alpha = 0.04f),
    0.30f to RefFrost.copy(alpha = 0.02f),
    1.00f to Color.Transparent,
    startY = 0f,
    endY = heightPx,
    tileMode = TileMode.Clamp,
)

/**
 * The highlight along the top of the frosted pane: a faint white bloom under the card's own
 * edge, out by [PanelSheenDepth] down.
 *
 * This is the specular half of the frost. [panelFrost] gives the glass its colour and
 * [drawPanelTopEdge] gives it an edge; with nothing between them the card is a tinted
 * rectangle rather than a lit surface. 0.055 at the peak is about as far as this can go before
 * it stops looking like light on glass and starts looking like a second hairline under the
 * first. Clipped to the same corner radius as the card so the bloom follows the arcs.
 */
private fun DrawScope.drawPanelSheen() {
    // No highlight bloom anymore -- an inset panel doesn't catch light on its top edge the
    // way a raised one does. [drawPanelTopEdge] now carries the whole depth cue for this
    // card via a dark inward shadow instead. Kept as a no-op rather than deleted so the
    // call site in [BrowseCard] doesn't need touching if this needs reviving later.
}

/**
 * The card's top edge and its two corner arcs.
 *
 * 0.13 at the peak, up from 0.08 and originally 0.14. The 0.08 was tuned for an edge that was
 * *meant* to be hard to find, when the card's first 84dp were translucent and the join was
 * supposed to be a dissolve rather than a boundary. That turned out to be the whole reason the
 * tabs looked adrift above an empty region of flag: nothing on the screen said where the card
 * began. With [PanelFade] now a 30dp hairline, this edge is the thing that says it — bright
 * enough to be located at a glance, still short of the 0.14 border that the redesign removed.
 *
 * Icy rather than white, now that the pane under it is frosted: this is the lit edge of that
 * glass, and a neutral white one sat on top of the wash instead of belonging to it. The tint
 * is [RefFrost] carried most of the way to white, so the edge is still the brightest thing on
 * the card — it is just no longer a different temperature from it.
 */
private fun DrawScope.drawPanelTopEdge() {
    // Inset look: a dark shadow band falling INTO the card from its top edge (as if the
    // card is a recess carved into the page), followed by a thin dark hairline stroke on
    // the edge itself instead of the old bright highlight -- the reverse of a raised
    // panel's lit rim.
    val hairline = 1.dp.toPx()
    val radius = PanelCorner.toPx()
    // A deeper, longer inward shadow so the card reads as a recess set into the page rather than a
    // panel resting on it: darkest right at the lip, falling away over ~1.9× the corner radius. The
    // extra reach and the stronger peak are what sell the inset — a short, faint band read as flat.
    val shadowDepth = radius * 1.9f
    clipRect(top = 0f, bottom = shadowDepth) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 0.50f),
                0.22f to Color.Black.copy(alpha = 0.30f),
                0.50f to Color.Black.copy(alpha = 0.13f),
                0.78f to Color.Black.copy(alpha = 0.04f),
                1.00f to Color.Transparent,
                startY = 0f,
                endY = shadowDepth,
            ),
            cornerRadius = CornerRadius(radius),
            size = size,
        )
    }
    clipRect(top = 0f, bottom = radius + hairline) {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(hairline / 2f, hairline / 2f),
            size = Size(size.width - hairline, size.height - hairline),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = hairline),
        )
    }
}

/** The magnifier in the card's header row: white ink, accent-blue while the field is open. */
@Composable
private fun SearchToggle(open: Boolean, onClick: () -> Unit) {
    // Off state matches the other inactive masthead glyphs (StatusFeatureIcon) — the muted
    // white@22% — so the three top-bar icons read as one set; open state lights to the accent.
    val ink by animateColorAsState(if (open) RefAccent else Color.White.copy(alpha = 0.22f), tween(180), label = "searchInk")
    Box(
        Modifier
            .size(TapTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = if (open) "Close search" else "Search servers",
            tint = ink,
            modifier = Modifier.size(26.dp),
        )
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
                .padding(bottom = 9.dp)            // .search-bar margin
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.045f))
                // [heroEdge], the same graded hairline as the glyph chips beside it — the
                // field opens in that row and the two should not disagree about the light.
                .border(1.dp, heroEdge, RoundedCornerShape(50))
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = RefTextMid,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))          // .search-bar gap
            // One style for the field and its placeholder, and it is what fixes the caret.
            // [BasicTextField] sizes its cursor to the *line box*, and by default that box
            // carries the font's own ascent/descent padding on top of the glyphs — so the
            // caret was drawn taller than the text and sitting a couple of dp high in it.
            // Turning the font padding off and centring the line inside an explicit
            // lineHeight makes the caret exactly the text's own height, on the text's own
            // baseline. The placeholder shares the style so it cannot land anywhere else
            // than where the real value will.
            val fieldStyle = SearchFieldStyle
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search location or server",
                        style = fieldStyle,
                        color = RefTextLow,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(RefAccent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
    }
}

/** The search field's type, shared by the input and its placeholder — see [SearchField]. */
private val SearchFieldStyle = TextStyle(
    color = RefTextHi,
    fontSize = 14.5.sp,
    lineHeight = 19.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

// ── Server list ───────────────────────────────────────────────────────────────
// .server-row: a [RowFlagSize] circular flag, name over ping, three load bars, and a
// hairline that starts past the flag ([DividerStart]) on every row but the last.
//
// The row is deliberately compact — 48dp against the 72dp it started at — because the
// list is the part of this screen the user scrolls, and two more servers visible without
// scrolling are worth more than the whitespace. Nothing was dropped to get there: every
// field the row carried it still carries, at a size it can still be read at. What changed
// is the flag (36 → 30 → 27dp), the vertical padding (12 → 9 → 6dp), the gap after the
// flag (14 → 11dp) and a point off each of the two text sizes.
//
// 48dp is where the compaction stops, and it stops there deliberately: that is the
// platform's minimum touch target, the row is a tap target across its whole width, and
// the content now measures ~39dp, so [heightIn] is what sets the height rather than the
// padding. Taking the row below it would look tighter and be measurably harder to hit.
@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    countryCode: String,
    pingMs: Int,
    isActive: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // The mockup has no selected state; the active server gets a faint accent-blue
            // wash, and its title goes bold. There used to be a teal dot beside the name as
            // well; it is gone, along with the row's `dotColor` parameter — with a tint and a
            // weight already saying "this is the one", a third marker was just a speck. The
            // wash is blue rather than plain white so the selection reads on-brand rather than
            // as a generic highlight, and stays restrained enough not to compete with the row.
            .background(if (isActive) RefAccent.copy(alpha = 0.06f) else Color.Transparent)
            // The row is a full-width tap target and it keeps the platform's 48dp floor,
            // which is the one dimension on this screen that is not a style decision. The
            // compaction below takes the *padding* out and leaves the target alone: a 42dp
            // list row would look tighter and be measurably harder to hit.
            .heightIn(min = 48.dp)
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
            // 6dp of padding around a 27dp flag is 39dp of content, so the 48dp floor is
            // what actually sets the row height now — see [heightIn] above. The padding is
            // still here because it is what keeps the two text lines off the divider.
            .padding(horizontal = ListPad, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagBadge(countryCode, RowFlagSize)
        Spacer(Modifier.width(11.dp))              // .server-row gap
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = RefTextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                fontSize = 11.5.sp,
                color = RefTextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        LoadBars(pingMs)
    }
}

/**
 * .load-bars — three 3dp bars, 6/9/12dp tall. The mockup paints the best tier green,
 * which this screen never uses, so the fast tier is the accent blue [RefAccent] instead —
 * the same accent that marks the selected row, so "good" reads as on-brand rather than as a
 * stray colour. How many light up follows the app's own ping tiers (<80ms, <180ms, worse), so
 * the row still says how good the server is, not just what colour it is. The two degraded
 * tiers keep their amber/red as semantic warning colours.
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
        filled == 3 -> RefAccent
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
            .embossed(shape, UsageCardFill, 16.dp, pressed)
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
    val reduce = rememberReduceMotion()
    val fraction = (bytes.toFloat() / USAGE_DAILY_CAP_BYTES.toFloat()).coerceIn(0f, 1f)
    val sweep by animateFloatAsState(
        targetValue = fraction,
        animationSpec = motionSpec(reduce, 600),
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

