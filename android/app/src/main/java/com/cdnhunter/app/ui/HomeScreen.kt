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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Palette — the mockup's :root custom properties, verbatim ───────────────────
private val RefBg = Color(0xFF060709)          // --bg

/**
 * The browse card's own base: the same luminance as [RefBg], a degree or two colder.
 *
 * This is the part of the frost that survives everywhere. [panelFrost]'s wash is gone by the
 * middle of the card, but a panel whose black is a colder black than the page's reads as glass
 * over the whole of its height — and at this distance from [RefBg] nothing about it is
 * nameable as a colour, which is the point.
 */
private val RefPanelBg = Color(0xFF06080D)

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
private val RefAccent = Color(0xFF4D7FFF)      // --accent
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
private val RefGlowOn = Color(0xFF2F6BFF)
/** The connecting colour: yellow-orange, i.e. "working", and the same hue the connecting
 *  arc, disc tint and phase dot take so the state is one colour. */
private val RefWorking = Color(0xFFFFA318)

/**
 * The same teal, dark enough to read *on* white — used for the power button's mark.
 *
 * [RefLive] is tuned to glow on near-black; on the button's white face it is a pale,
 * thin mark that fails contrast. This is the same hue at roughly a third of the
 * lightness, which clears 4.5:1 on the disc's lightest stop.
 */
private val RefLiveInk = Color(0xFF07786B)
/** [RefWorking] dark enough to read on the button's white face, for the same reason
 *  [RefLiveInk] exists: a mid-chroma amber mark on near-white is invisible. */
private val RefWorkingInk = Color(0xFF9A5B00)
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PillInk = Color(0xFF05070C)        // .tab-pill.active text colour
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
//   CONNECTING — [RefWorking], tighter and stronger, and the power ring's arc turns.
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
private val HeroBleed = 40.dp

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
// Idle the light is white and low; connecting it is [RefWorking]; connected [RefGlowOn], stronger
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
private const val HORIZON_LIGHT_IDLE = 0.108f
private const val HORIZON_LIGHT_ON = 0.300f

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
// can return. The phase-to-colour rule it held is unchanged and is stated inside that function:
// [RefWorking] in flight, [RefLive] up, the country's tint at rest.

/**
 * The colour of the light in the room for [phase]: white idle, amber working, blue up.
 *
 * Connected is [RefGlowOn] rather than [RefLive], and that is the one place the room's
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
        ConnPhase.CONNECTING -> RefWorking
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
// ── Header flag panel ─────────────────────────────────────────────────────────
// The flag is the top of the screen: one image, edge to edge, behind everything the
// header draws. See the file header for why it is Crop and what the three
// layers over it are for.

/**
 * How saturated the header flag is drawn.
 *
 * Flag specifications are ink on cloth, mixed to be seen in daylight from a distance:
 * dropped onto an OLED panel at full chroma, Vietnam's red or Brazil's green arrive as
 * the loudest thing the app has ever shown. A fifth of the saturation off is enough
 * that the colour still reads as that country's and no longer as a swatch — and it is
 * done with a colour matrix on the image rather than by fading it toward black, which
 * would take the brightness with it and leave the flag looking dirty rather than calm.
 */
private const val HEADER_FLAG_SATURATION = 0.80f

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
private const val HEADER_FLAG_ALPHA = 0.94f

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
private val FlagFootRise = 12.dp

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
 * The head is much lighter than it was (0.26 against 0.58) because it is no longer alone up
 * there: [HeroTopVeil] now does the status-bar protection, over a fixed 108dp rather than over
 * a fraction of however tall the flag happens to be.
 */
private val HeaderFlagScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.26f),
    0.10f to Color.Black.copy(alpha = 0.20f),
    0.30f to Color.Black.copy(alpha = 0.16f),
    0.58f to Color.Black.copy(alpha = 0.18f),
    0.80f to Color.Black.copy(alpha = 0.30f),
    1.00f to Color.Black.copy(alpha = 0.40f),
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
    1.00f to Color.Black.copy(alpha = 0.86f),
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
    1.00f to Color.Black.copy(alpha = 0.88f),
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
    val desaturate = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(HEADER_FLAG_SATURATION) })
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
            // Two models for one country: flagcdn's true-aspect SVG for a well-known
            // exit country, the bundled circle-flags asset for everything else and for
            // every failure of the first. Only the bundled one needs unmasking and
            // de-bowing — see [rectangularFlag] and FlagArtwork.kt's header.
            val remote = remember(code) { remoteFlagUrl(code) }
            var remoteFailed by remember(code) { mutableStateOf(false) }
            val bundled = remember(code) { rectangularFlag(context, code) }
            val flag = if (remote != null && !remoteFailed) remote else bundled
            if (flag == null) {
                Box(Modifier.fillMaxSize().background(HeaderFlagFallback))
            } else {
                val cc = canonicalCountryCode(code)?.lowercase() ?: code
                val key = if (flag === remote) "flag-cdn-$cc" else "flag-rect-$cc"
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
                    desaturate = desaturate,
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
        Box(Modifier.matchParentSize().background(Color.Transparent))
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
    desaturate: ColorFilter,
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
        alpha = alpha,
        colorFilter = desaturate,
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
// tokens went with it. What is left up here is type, one disc, and the artwork; the only
// framed surfaces on the screen are the top bar's glyph chips, the mode pill and the browse
// card, and each of those is a control or a panel rather than a label in a box.
//
// If something up here ever needs a surface again, the thing to reach for is [GlyphChip] plus
// [heroEdge] — the same fill and the same lit hairline every other frame on this screen uses.

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
     * that look alike: [publicIp] blank *because we are still asking* is "Checking…", blank
     * *because every provider failed* is "Unavailable" with a tap to retry. Without this the
     * two were the same em dash, which is how a total lookup failure came to look like a
     * rendering bug. See [MetaRow].
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
        if (hasExitGeo(cfg)) exitCity else cfg.city

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
     * Disconnected that is this device's own public IP, and nothing else — no city, no
     * country, no separator: [publicIp] is resolved unproxied while the tunnel is
     * down, so it is already the address the outside world sees for this phone.
     *
     * Connected it is the tunnel's exit IP. VpnTab re-resolves [publicIp] through the
     * live tunnel the moment the state flips, but that request is deliberately given a
     * couple of seconds to let a fresh tunnel settle, and the row would otherwise sit
     * empty for exactly as long as the user is looking at it. [SavedConfig.address] is
     * the server that was dialled, so when it is a literal address it is the exit IP —
     * shown until the measured one lands and replaces it. A hostname is not an IP and
     * is never shown here.
     *
     * Blank means "no address to state"; [MetaRow] decides what to say about that, and the
     * answer depends on [ipLookupPending].
     */
    val displayIp: String
        get() = publicIp.ifBlank {
            if (connected) activeConfig?.address?.takeIf(::isIpLiteral).orEmpty() else ""
        }

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
    var tab by remember { mutableStateOf(HomeTab.MAIN) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val servers = remember(state.allConfigs, tab, query) {
        state.configsFor(tab).matching(query).byLatency()
    }
    val activeId = state.activeConfig?.id

    // How tall the hero's rows measured, in px. The backdrop behind them is drawn
    // [HeroBleed] taller than this — see [HeroBackdrop] — which is what carries the flag
    // and its light down behind the browse card's translucent top edge instead of stopping
    // at the last row. Measured rather than fixed because the rows' own height moves with
    // the status bar inset, the font scale and the phase text.
    var heroContentPx by remember { mutableStateOf(0) }
    val heroHeight = with(LocalDensity.current) {
        if (heroContentPx > 0) heroContentPx.toDp() else HeroBackdropFallback
    }

    Box(modifier.fillMaxSize().background(PageGradient)) {
        // First, behind everything: the artwork and the light. Both are sized off the hero
        // and neither fills this box — the light runs [HeroBleed] past the hero's last row,
        // the flag stops [FlagFootRise] short of it. See [HeroBackdrop] for why those differ.
        HeroBackdrop(
            state = state,
            heroHeight = heroHeight,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onTogglePower = onTogglePower,
                onSetMode = onSetMode,
                onRetryIp = onRetryIp,
                // The one thing this reports is its own height: the backdrop behind it is
                // sized off that, and so is where the browse card starts.
                modifier = Modifier.onSizeChanged { heroContentPx = it.height },
            )
            BrowseCard(
                state = state,
                servers = servers,
                activeId = activeId,
                tab = tab,
                query = query,
                searchOpen = searchOpen,
                onQueryChange = { query = it },
                onSelectTab = { tab = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onSelectConfig = onSelectConfig,
                onAddServer = onAddServer,
                onRefreshPings = onRefreshPings,
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
    val flagHeight = (heroHeight - FlagFootRise).coerceAtLeast(0.dp)
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
    // White idle, amber working, blue connected — the light's own colour, animated so
    // changing state reads as the room changing colour rather than as a repaint.
    val ambient = phaseLight(phase)
    val lit = phase != ConnPhase.OFF

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
                    // The card's cast shadow, laid on the artwork just above where the card's
                    // top edge lands — i.e. at the hero's own measured foot.
                    val edge = heroHeight.toPx()
                    val depth = HeroCardShadowDepth.toPx()
                    if (edge > depth) {
                        drawRect(
                            brush = HeroCardCastShadow,
                            topLeft = Offset(0f, edge - depth),
                            size = Size(size.width, depth),
                        )
                    }
                    // The top fade, last of the shade layers and over all of them: the flag
                    // dissolving into the status bar rather than starting at it.
                    drawRect(
                        brush = HeroTopVeil,
                        size = Size(size.width, HeroTopVeilDepth.toPx()),
                    )
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
 * A soft shade laid over the artwork, under the light: the layer that lets everything on the
 * hero be read on top of an arbitrary flag.
 *
 * Deliberately *not* the same job as [HeaderFlagScrim], and the two do not double up by
 * accident. That one lives inside the flag's own masked layer and is about the artwork —
 * keeping a saturated field from shouting, and tapering its head and foot. This one covers the
 * whole band, flag or no flag, and is shaped to where **type** lands:
 *
 *  - 0.30 at the status bar, behind the top bar's glyphs and the 34sp headline, where a flag's
 *    top stripe is at its brightest and least negotiable;
 *  - down to 0.05 through the middle, which is where the power disc is — the disc is a white
 *    object with its own shadow and needs the artwork *behind* it left alone, or the button
 *    stops looking like it is sitting on something;
 *  - back to 0.26 at the foot, under the tab pills, and running on past where the flag now ends
 *    ([FlagFootRise]) so the artwork's last 14% dissolves into shade rather than into nothing.
 *
 * Seven stops for two ramps, and the count is the point: a three-stop version of this banded
 * visibly on a dark flag, because 8-bit alpha over near-black has very little room between
 * steps. The fix for banding on this screen is always more eased stops rather than different
 * colours — the same reason [HeroFloor] and [PanelFade] are shaped the way they are.
 *
 * Black rather than a tinted navy, and it matters: any hue here would sit on top of the flag's
 * own and turn every country slightly the same colour, which is exactly what the removed
 * `--green` did.
 */
private val HeroDepthScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.30f),
    0.10f to Color.Black.copy(alpha = 0.20f),
    0.22f to Color.Black.copy(alpha = 0.10f),
    0.42f to Color.Black.copy(alpha = 0.05f),
    0.62f to Color.Black.copy(alpha = 0.09f),
    0.82f to Color.Black.copy(alpha = 0.19f),
    1.00f to Color.Black.copy(alpha = 0.26f),
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

/**
 * How deep the flag's top fade runs, measured from the very first pixel row of the screen.
 *
 * A fixed dp rather than a fraction of the band: what it has to cover is the status bar and the
 * top bar's glyphs, and those are a fixed size no matter how tall the hero measures.
 */
private val HeroTopVeilDepth = 108.dp

/**
 * The flag's top fade — the artwork softening into the top of the screen instead of starting
 * at full chroma against the status bar.
 *
 * Drawn *over* the flag, not into its alpha mask, and that is deliberate: a mask fade removes
 * the artwork and reveals what is under it, which on this screen put a visible pale band across
 * the page at exactly clock height (see [HeaderFlagFadeY]). Darkening on top has no such seam —
 * the flag is still there, it is just in shadow, so there is nothing for an edge to form
 * between.
 *
 * Six stops because the ramp is the whole effect: near-opaque at row 0 so the system's white
 * clock and battery glyphs have a black field of their own, and gone by [HeroTopVeilDepth],
 * which is a little below the top bar's chips. Fewer stops band badly here — 8-bit alpha over
 * near-black has very little room between steps.
 */
private val HeroTopVeil = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.72f),
    0.18f to Color.Black.copy(alpha = 0.55f),
    0.38f to Color.Black.copy(alpha = 0.36f),
    0.58f to Color.Black.copy(alpha = 0.20f),
    0.80f to Color.Black.copy(alpha = 0.08f),
    1.00f to Color.Transparent,
)

/**
 * The vignette's stops, centred a little above the middle of the band — around the power disc.
 *
 * Kept as a list rather than a brush because a radial gradient needs the draw scope's own size
 * for its centre and radius, so the brush can only be built inside [HeroBackdrop]'s draw pass.
 */
private val HeroVignetteStops = listOf(
    Color.Transparent,
    Color.Transparent,
    Color.Black.copy(alpha = 0.02f),
    Color.Black.copy(alpha = 0.05f),
)

/**
 * How tall the browse card's cast shadow is, drawn on the artwork immediately above the card's
 * top edge.
 *
 * This is the depth the card gets for free from being a real object: the panel is lit from its
 * own top edge ([drawPanelTopEdge]) and now the flag behind it is darkened as it approaches
 * that edge, so the card reads as sitting *in front of* the artwork rather than butted against
 * it.
 */
private val HeroCardShadowDepth = 40.dp

/** The cast shadow's ramp — nothing at its top, heaviest on the card's own edge line. */
private val HeroCardCastShadow = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.42f to Color.Black.copy(alpha = 0.02f),
    0.76f to Color.Black.copy(alpha = 0.05f),
    1.00f to Color.Black.copy(alpha = 0.08f),
)

// ── Header ────────────────────────────────────────────────────────────────────
// The hero's content only. Everything behind it is [HeroBackdrop]'s, drawn by [HomeScreen] as
// a sibling so it can be taller than this; what this column reports, via [Modifier.onSizeChanged]
// at the call site, is how tall that backdrop needs to be.
//
// One centred column, read top to bottom:
//
//   where am I?      → [CountryHeadline]
//   as what address? → [MetaRow]
//   [the action]     → [PowerCircle], on the screen's own axis
//
// The list's own controls (Main/Custom, add, search) are *not* here — they are the first row
// inside [BrowseCard], which is where they belong now that the card announces its own top edge.
//
// What changes between the three [HomeUiState.phase] values is the light, not any surface:
// the atmosphere changes colour and tightens ([drawHeroAtmosphere]), the ring reports, the ink
// follows. Nothing slides and the flag wash is on in all three states.
//
// statusBarsPadding() on this column keeps the top bar clear of the clock while the backdrop
// behind it runs on to the top of the screen.

// No spacer between the top bar and the headline: what holds the headline off the navigation
// chips is [TopBar]'s own reported height, 10dp less than the 48dp tap targets inside it (see
// [TopBarInk]). A spacer here would pay twice for the same clearance.

/** Between the headline block and the address.
 *
 *  4dp. The address lost its chip in the same pass ([MetaRow]), and a bare line of type wants
 *  to sit closer to what it belongs to than a floating container did — a chip reads as its own
 *  object at 6dp, but 13.5sp of ink reads as the headline's second line. */
private val HeadlineFootGap = 4.dp

/** Between the address and the power circle: the hero's breathing room, and what makes the
 *  artwork around the button a place rather than a gap.
 *
 *  2dp, down from 4 by way of 8 and 26. The disc's own box is [PowerSize] against a
 *  [PowerDiscSize] mark, so it already carries an 11dp band of its own on every side — a
 *  gap here is added to that band, not to the disc, which is why it can go this low without
 *  the address touching anything. */
private val HeroOpenSpace = 2.dp

/**
 * From the power disc's foot to the browse card's top edge — the hero's last measurement.
 *
 * 8dp, and it is the only gap left down there: the tab row moved inside the card, so the two
 * spacers that used to straddle it (10dp above, 6dp below) collapsed into this one.
 *
 * A warning for anyone tempted to close it further, because it cost two rounds to find: this
 * number was never what made the gap look big. The card's top used to be *translucent* for
 * 84dp ([PanelFade]) and started at 62% opacity, so its edge was invisible and the eye put the
 * card's beginning wherever the paint finally turned solid. What fixed it was making the edge
 * legible: see [PanelFade] and [drawPanelTopEdge].
 */
private val HeroFootGap = 8.dp

@Composable
private fun Header(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onTogglePower: () -> Unit,
    onSetMode: (ConnectMode) -> Unit,
    onRetryIp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = ScreenPad, end = ScreenPad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(onOpenSettings = onOpenSettings, onOpenProfile = onOpenProfile)
        CountryHeadline(state)
        Spacer(Modifier.height(HeadlineFootGap))
        // The address sits above the button: the order reads as a sentence — which country,
        // as what address, [the action].
        MetaRow(state = state, onRetryIp = onRetryIp)
        Spacer(Modifier.height(HeroOpenSpace))
        PowerCircle(
            mode = state.mode,
            phase = state.phase,
            enabled = state.activeConfig != null,
            onClick = onTogglePower,
            onSwipeUp = { onSetMode(ConnectMode.SMART) },
            onSwipeDown = { onSetMode(ConnectMode.MANUAL) },
        )
        // The hero's measured foot, which is exactly where the browse card's top edge is.
        Spacer(Modifier.height(HeroFootGap))
    }
}

// ── Mode pill ─────────────────────────────────────────────────────────────────
// Gone. The badge that used to sit on the seam between the hero and the browse card — glass,
// hairline, "Mode · Manual", docked half in and half out of the card's top edge — is deleted:
// the composable, its six tokens and the `dockOnSeam` layout modifier that put it there.
//
// Two things go with it. The seam has nothing on it any more, so [CardTopRoom] is back to a
// plain head clearance rather than a hole cut for an overhanging object, and [Header] no
// longer needs a z-index to paint over the card.
//
// The mode itself is still changeable, and by the gesture that always did it: a vertical drag
// on the connect disc, up for Smart and down for Manual, plus the two named accessibility
// actions on the same button (see [PowerCircle]). Nothing on the screen states the current
// mode now — that is the trade this removal makes, and it is deliberate: the hero is down to
// one country, one address and one action.

// ── Top bar ───────────────────────────────────────────────────────────────────
// Two navigation marks, one at each end: hamburger → Settings, account → Profile.
//
// Each sits in its own small framed container rather than bare on the panel, and the
// frame is a *rounded square* ([GlyphChipCorner], 13dp on a 38dp box) rather than the
// disc it used to be. That is the shape language the rest of the screen already speaks:
// the hero panel's foot, the browse card, the status chip, every server row's flag box's
// parent card and the tab pills are all rounded rectangles, and two circles at the top
// were the only exception. Matching them makes the top bar read as part of the same kit
// — same shape, same [GlyphChip] material, same hairline, same 12dp of elevation as the
// other chips on the panel.
//
// 38dp of chip inside a 48dp tap target, so what the eye sees is a compact control and
// the reach is unchanged.
//
// These two chips are now the only framed things at the top of the screen: the status row
// that used to sit under them first lost its glass surface and is now deleted outright (see
// [Header]), so a frame up here means "navigation", and nothing else. Their glyphs are plain
// white. They used to carry the brand colour, which was the one place it was stated
// independently of connection state — but the flag now runs the full height of the screen
// behind them, and a mid-tone mark on an arbitrary flag is the one glyph colour that can
// land on its own hue. White is the only tint that holds against every backdrop the wash
// can put there, and it also keeps [RefLive] on this screen meaning exactly one thing:
// connected.

/** The glyph inside its chip. Smaller than the 25dp bare mark it replaces — with
 *  material under it, it no longer has to carry itself on size alone. */
private val NavGlyph = 20.dp

/** The framed container under each top-bar glyph. */
private val NavChip = 38.dp

/** Its corner — a squircle-ish 13dp on 38dp, the same corner-to-size relationship the
 *  status chip and the tab pills use, so all the small frames on the panel agree. */
private val GlyphChipCorner = 13.dp

/** Its material: [RefElev1] over whatever the panel is showing, so it reads the same
 *  on the chrome and on a flag. The lit rim comes from [Modifier.embossed]. */
private val GlyphChip = Brush.verticalGradient(
    0.00f to RefElev1.copy(alpha = 0.66f),
    1.00f to RefElev1.copy(alpha = 0.82f),
)

/** How far each chip is lifted off the panel. Small: it is a chip, not a card, and the
 *  same 12dp the status chip uses so the two sit on one plane. */
private val GlyphChipElevation = 12.dp

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

/**
 * The height the top bar *reports* to the hero column, as against the [TapTarget] it measures.
 *
 * 38dp — [NavChip], the size of the frames that are actually visible in this row. The row is
 * still 48dp of touchable height, because 48 is the floor for a hit target and shrinking one to
 * buy layout is the kind of trade that makes an app hard to use in a moving vehicle; what
 * changes is that the 5dp of empty reach above and below each chip is no longer *also* charged
 * to the column below it. The row measures 48, reports 38, and places its content centred in
 * the difference, so the chips do not move on screen — everything under them comes up by 10dp.
 *
 * This is the whole of the "move the country name higher" ask that could be paid for honestly:
 * the spacer between this row and the headline was already zero, the status-bar inset is only
 * applied once ([Header] takes it; [AppScreen] deliberately does not), and Compose 1.6 already
 * trims the font padding off the headline's own line box. Ten dp of nothing was the last thing
 * left between the clock and the country.
 */
private val TopBarInk = NavChip

/**
 * Report [reported] height while measuring, and drawing, at whatever the content actually is.
 *
 * The content is centred in the difference and is not clipped to the reported box — it draws
 * outside its own bounds, which is exactly the point. Used for rows whose touchable area has to
 * stay bigger than their visible one ([TopBar]); the deleted mode pill's `dockOnSeam` was the
 * same trick with the placement biased instead of centred.
 */
private fun Modifier.reportHeight(reported: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = reported.roundToPx().coerceIn(0, placeable.height)
    layout(placeable.width, height) {
        placeable.place(0, -(placeable.height - height) / 2)
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onOpenProfile: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // Outside the height: a size modifier reports whatever it was asked for, so the
            // trim has to sit above it in the chain to be the thing the column sees.
            .reportHeight(TopBarInk)
            .height(TapTarget)
            .background(Color.Black.copy(alpha = 0.15f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The mockup's tap boxes are 40px; these are 48dp for reach and nudged
        // back out by 5dp so the chips still sit on the mockup's margins.
        GlyphButton(
            onClick = onOpenSettings,
            label = "Settings",
            modifier = Modifier.offset(x = (-5).dp),
        ) {
            Icon(
                Icons.Rounded.Menu,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(NavGlyph),
            )
        }
        Spacer(Modifier.weight(1f))
        GlyphButton(
            onClick = onOpenProfile,
            label = "Account",
            modifier = Modifier.offset(x = 5.dp),
        ) {
            AccountGlyph(color = Color.White, modifier = Modifier.size(NavGlyph))
        }
    }
}

/**
 * One glyph in one framed chip inside one 48dp tap target.
 *
 * Also the browse card's add and search buttons ([AddServerButton], [SearchToggle]), so
 * every icon-only control in this screen is the same object: same frame, same material,
 * same elevation, same reach.
 */
@Composable
private fun GlyphButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = remember { RoundedCornerShape(GlyphChipCorner) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(TapTarget)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                // No ripple: the chip's own press — it shrinks and drops onto the panel —
                // is the feedback. See [Modifier.embossed].
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The chip is inside the tap target, not the target itself: the touch area
        // stays 48dp while what the eye sees is 38dp.
        Box(
            Modifier
                .size(NavChip)
                .embossed(shape, GlyphChip, GlyphChipElevation, pressed),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

// ── Country headline ──────────────────────────────────────────────────────────
// The largest ink in the app: where the tunnel comes out, at 34sp ExtraBold, centred.
//
// One fact and no others. The city used to lead this line with the country and the
// config's own name under it; the flag behind the whole screen already says which country
// this is, the city is not something the user chose, and the server's name is what the
// selector at the foot and the list below are for. What is left is the answer.
//
// It changes on the same crossfade as the flag behind it, because they are the same event
// — the user picks another server, or the tunnel reports the exit node's real country —
// and a country name that cuts while its flag dissolves reads as two things happening.
//
// [headlineBrush] colours it, so the headline is the phase's colour too — accent working, teal
// up — and at rest it is the *flag's* colours rather than white. That is the third statement of
// the phase on this screen and the one that carries furthest, because it is on the biggest text.

/** The headline, and its subtitle's, own size. 34sp is the largest the longest country
 *  name this app can draw ("Bosnia and Herzegovina") still fits on one line of a 360dp
 *  screen at default font scale; beyond that it ellipsises, which on the screen's one
 *  headline would be worse than a smaller face. */
private val HeadlineSize = 34.sp

// ── Flag-tinted headline ──────────────────────────────────────────────────────
// The country's name, in the country's own colours.
//
// The headline used to be one fixed white ([RefTextHi]) over whatever artwork happened to be
// behind it, which is the safe choice and also the one that makes the largest text on the
// screen the only thing up here with no relationship to the flag it is printed on. Every other
// layer in the hero already takes its cue from the artwork — the wash, the horizon light, the
// scrim — and the word sat on top of all of it as chrome.
//
// The treatment is a two-stop vertical gradient across the glyphs, from a light tint of the
// flag's first dominant hue to a light tint of its second: Sweden's blue into its gold, Italy's
// green into its red, Japan's crimson into its white. Not sampled from the bitmap, and that is
// a decision rather than a shortcut. Sampling means decoding the flag on the UI thread, or
// carrying a palette extraction on every flag change, to arrive at an *average* — and the
// average of a flag is mud, because flags are chosen for contrast. It also cannot be tuned: the
// blue of the Swedish flag at full saturation fails contrast at 34sp over dark artwork, so the
// sampled colour would need lightening by hand anyway. A curated table skips both problems and
// is auditable, one line per country.
//
// Contrast is what fixes the *lightness* of every entry below. All of them sit in a narrow
// luminance band — pastels, nothing below roughly #A8 in its brightest channel — so the
// headline clears its contrast floor over the darkest and the brightest band of any flag the
// app draws, with the hero's own scrim ([HeroDepthScrim]) underneath it. What varies between
// countries is hue, never how bright the word is.
//
// The phase tints, it does not replace. [headlineBrush] leans both stops towards [RefWorking]
// while an attempt is in flight and towards [RefLive] while the tunnel is up, but only as far as
// [HEADLINE_PHASE_MIX] — so the state reads at a glance and the word stays the country's colour
// underneath it.

/** Blue into gold: SE, UA, KZ. */
private val InkBlueGold = Color(0xFFA9C9F2) to Color(0xFFF2DB99)

/** Blue into near-white ice: FI, EE, GR, IL, AR. */
private val InkBlueIce = Color(0xFFBAD6F6) to Color(0xFFECF3FC)

/** The blue-white-red family, and the largest one: US, GB, FR, NL, RU and the rest. */
private val InkTricolour = Color(0xFFB7CCF2) to Color(0xFFF0BEC1)

/** Red into white — the other large family, from DK and CH to JP and SG. */
private val InkRedWhite = Color(0xFFF5C2BD) to Color(0xFFF3EEEE)

/** Gold into red: ES, VN. */
private val InkGoldRed = Color(0xFFF5DDA0) to Color(0xFFF1B3A4)

/** Gold into red over black: DE, BE. The black band is what the tints cannot state, so the
 *  gold carries the flag and the red follows it. */
private val InkIronGold = Color(0xFFF4D68F) to Color(0xFFEDB0A0)

/** Green into gold: BR, LT, ZA. */
private val InkGreenGold = Color(0xFFAADCB6) to Color(0xFFF0DB98)

/** Green into orange: IE, NG. */
private val InkEmerald = Color(0xFFA9DEB9) to Color(0xFFF5CD9F)

/** Green into red, usually across a white middle: IT, HU, IR, MX, BG. */
private val InkGreenWhiteRed = Color(0xFFA8DDB2) to Color(0xFFF1BCB6)

/** Green into crimson: PT, KE. */
private val InkGreenCrimson = Color(0xFFA9DCB4) to Color(0xFFF1B2A5)

/** The Gulf's green-white-red-black: AE, SA, KW, IQ, OM, AZ. */
private val InkDesertGreen = Color(0xFFB4E0BD) to Color(0xFFEFC6B9)

/** Blue into gold into red: RO, MD, AM. */
private val InkBlueGoldRed = Color(0xFFB4CBF2) to Color(0xFFF3DA9E)

/** Saffron into green: IN. */
private val InkSaffron = Color(0xFFF5C79A) to Color(0xFFB6E0BA)

/** Blue into red on white: KR, TW. */
private val InkEastAsia = Color(0xFFB8D3F2) to Color(0xFFF1BBB7)

/** White into copper: CY. */
private val InkCopper = Color(0xFFF4DFC8) to Color(0xFFF2C9A4)

/** Red into gold: EG. */
private val InkRedGold = Color(0xFFF1BBB0) to Color(0xFFF3DEA6)

/** Gold into blue: CO. */
private val InkGoldBlue = Color(0xFFF3DB9E) to Color(0xFFAFCDF2)

/** No flag, or one with no entry below: the white the headline always was, cooled very
 *  slightly towards its foot so the treatment is consistent even where the hues are not. */
private val InkNeutral = RefTextHi to Color(0xFFD7DDE8)

/**
 * The two tints for one ISO 3166-1 alpha-2 code, or [InkNeutral] for anything unlisted.
 *
 * Grouped by the hue pair rather than written out per country, because that is what the flags
 * themselves do — there are only so many ways to arrange red and white — and a table of 70
 * near-duplicate colour literals would be 70 things to get subtly wrong. The codes covered are
 * the ones the app fetches artwork for (`VPN_FLAG_COUNTRIES` in FlagArtwork.kt); anything
 * outside that set is drawing the bundled placeholder, which has no country colours to
 * harmonise with, so the neutral pair is the honest answer for it.
 */
private fun flagInk(code: String): Pair<Color, Color> = when (code.uppercase()) {
    "SE", "UA", "KZ" -> InkBlueGold
    "FI", "EE", "GR", "IL", "AR" -> InkBlueIce
    "US", "GB", "FR", "NL", "RU", "CZ", "SK", "SI", "HR", "RS",
    "NO", "IS", "NZ", "AU", "CL", "LU", "TH", "PH", "MY" -> InkTricolour
    "DK", "CH", "AT", "PL", "ID", "SG", "TR", "JP", "PE", "CA",
    "BH", "QA", "LV", "GE", "HK" -> InkRedWhite
    "ES", "VN" -> InkGoldRed
    "DE", "BE" -> InkIronGold
    "BR", "LT", "ZA" -> InkGreenGold
    "IE", "NG" -> InkEmerald
    "IT", "HU", "IR", "MX", "BG" -> InkGreenWhiteRed
    "PT", "KE" -> InkGreenCrimson
    "AE", "SA", "KW", "IQ", "OM", "AZ" -> InkDesertGreen
    "RO", "MD", "AM" -> InkBlueGoldRed
    "IN" -> InkSaffron
    "KR", "TW" -> InkEastAsia
    "CY" -> InkCopper
    "EG" -> InkRedGold
    "CO" -> InkGoldBlue
    else -> InkNeutral
}

/**
 * The headline's ink: [flagInk]'s pair as a vertical gradient across the glyphs, carried towards
 * the phase's colour as the phase takes over.
 *
 * A brush rather than a colour, which is why the deleted `headerInk` helper could not be reused
 * and why the crossfade it got from [animateColorAsState] has to be rebuilt here: nothing
 * animates a [Brush]. Two phase fractions animate instead, on the same [PHASE_FADE_MS], and each
 * stop is lerped towards the state's colour by them — the gradient stays a gradient throughout,
 * and at rest in a state it is that state's colour top and bottom, which is exactly where the old
 * fixed ink ended up too.
 *
 * The lerps are applied in sequence rather than picked between, so the CONNECTING → CONNECTED
 * handover — amber falling as teal rises — passes through a blend of the two instead of
 * snapping between them.
 */
@Composable
private fun headlineBrush(phase: ConnPhase, countryCode: String): Brush {
    val reduce = rememberReduceMotion()
    val working by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTING) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "headlineWorking",
    )
    val live by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTED) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "headlineLive",
    )
    val (top, bottom) = flagInk(countryCode)
    // Capped, and this is the fix for a regression worth naming: the mix used to run to 1.0,
    // so the moment the tunnel came up the headline was pure [RefLive] teal and the flag tint
    // it was supposed to carry was gone — "Sweden" in teal over a blue-and-gold flag. At 0.34
    // the phase is unmistakable and the hue underneath it is still the country's.
    fun state(tint: Color): Color = lerp(
        lerp(tint, RefWorking, working * HEADLINE_PHASE_MIX),
        RefLive,
        live * HEADLINE_PHASE_MIX,
    )
    return Brush.verticalGradient(0f to state(top), 1f to state(bottom))
}

/** How far the headline's flag tint is allowed to travel towards the phase colour. */
private const val HEADLINE_PHASE_MIX = 0.34f

@Composable
private fun CountryHeadline(state: HomeUiState, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val cfg = state.activeConfig
    val country = countryCodeToName(state.headerCountryCode)
    // The config's name is the fallback only when the country is unknown — an empty
    // headline would be worse than a technical one.
    val headline = country.ifBlank {
        cfg?.let { c -> c.displayName.ifBlank { c.address } } ?: "No server"
    }
    // The city, when there is one and it is not already the headline: one dim line under
    // the country, at caption weight, so the hero can be specific without the headline
    // having to carry two facts.
    val city = cfg?.let { state.cityFor(it) }.orEmpty()
    // The tint follows the flag that is actually on screen ([HomeUiState.heroFlagCountry]),
    // not the config's own country: at launch the flag is the cached one for a moment, and a
    // headline coloured for a flag that is not up yet would be the one frame where the two
    // disagree.
    val ink = headlineBrush(state.phase, state.heroFlagCountry)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = headline,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, FLAG_FADE_IN_MS)) togetherWith
                        fadeOut(motionSpec(reduce, FLAG_FADE_OUT_MS))
                    ).using(SizeTransform(clip = false))
            },
            label = "countryHeadline",
        ) { value ->
            Text(
                value,
                fontSize = HeadlineSize,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                // Tight tracking on a large heavy face: at 34sp the default spacing reads
                // loose, and pulling it in is what makes the word a single mark.
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The brush replaces `color` outright — a TextStyle carrying both would use
                // the brush and silently drop the colour, so there is no `color` here at all.
                // The shadow is what keeps a pastel legible where a flag's own light band
                // comes through the scrim; white never needed it, tinted ink does.
                style = TextStyle(brush = ink, shadow = HeroInkShadow),
            )
        }
        if (city.isNotBlank() && city != headline) {
            Spacer(Modifier.height(3.dp))
            Text(
                city,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = RefTextMid,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Meta row ──────────────────────────────────────────────────────────────────
// One fact, on one line: the address the internet currently sees. Off, it holds this device's
// own address; connected, the exit node's. Keeping it visible in both is the point — the number
// the user is about to change is the number they can read now.
//
// Tap it and it goes to the clipboard. There is no container: the ink keeps the padding, clip
// and ripple the deleted chip had, so the hit target survived losing the edge.
//
// It has three states, not two, and that distinction is the whole of the bug it was carrying. A
// blank address is either "we are still asking" or "every provider failed", and both used to
// render as one dim em dash at [RefTextLow] on bare artwork — which was, in practice, invisible,
// and read as a missing element rather than as a value that never arrived. Now: the number, or
// "Checking…", or "Unavailable" with the whole line tappable to ask again. See
// [HomeUiState.ipLookupPending] for the flag and [GeoService.lookupCurrentIp] for what can fail.

/** What the hero's address line is showing right now — see [MetaRow]. */
private data class IpSlot(val value: String, val checking: Boolean) {
    val ready: Boolean get() = value.isNotBlank()
}

@Composable
private fun MetaRow(
    state: HomeUiState,
    onRetryIp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reduce = rememberReduceMotion()
    val slot = IpSlot(state.displayIp, state.ipLookupPending)
    // A floor height whether or not the address is in it yet, so the hero does not grow a
    // few dp on connect and shrink again on cancel.
    Row(
        modifier.heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = slot,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 260)) togetherWith fadeOut(motionSpec(reduce, 140))
                    ).using(SizeTransform(clip = false))
            },
            label = "publicIp",
        ) { value ->
            val label = when {
                value.ready -> value.value
                value.checking -> "Checking…"
                else -> "Unavailable"
            }
            val click: (() -> Unit)? = when {
                value.ready -> {
                    {
                        clipboard.setText(AnnotatedString(value.value))
                        android.widget.Toast
                            .makeText(context, "IP copied", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                value.checking -> null
                else -> onRetryIp
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (click == null) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                onClickLabel = if (value.ready) "Copy IP address" else "Retry IP lookup",
                                onClick = click,
                            )
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "IP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    // [RefTextMid] in every state. [RefTextLow] was tuned to sit on glass; on
                    // bare artwork it is the first thing a bright flag band eats.
                    color = RefTextMid,
                    maxLines = 1,
                    style = TextStyle(shadow = HeroInkShadow),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    fontSize = if (value.ready) 13.5.sp else 12.5.sp,
                    fontWeight = if (value.ready) FontWeight.Bold else FontWeight.SemiBold,
                    // The placeholders are a step down from the number, never below the label:
                    // dimmer than a real value, still plainly readable over any flag.
                    color = if (value.ready) RefTextHi else RefTextMid,
                    maxLines = 1,
                    // With softWrap off and ellipsis on, anything that doesn't fit ends in "…"
                    // rather than mid-stroke.
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFeatureSettings = "tnum",   // tabular-nums
                        shadow = HeroInkShadow,
                    ),
                )
                // The retry affordance, and only in the state that has something to retry.
                if (!value.ready && !value.checking) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = RefTextMid,
                        modifier = Modifier.size(13.dp),
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
//   CONNECTING — the same disc under a faint amber tint, an [RefWorking] mark, and a 240° arc
//                turning once a second around it. The arc is outside the disc on purpose: the
//                face keeps its shape, so the button still looks pressable while it works.
//   CONNECTED  — the *same white disc*, with a deep teal mark and the ring lit [RefLive]. The
//                face never fills with colour in any state: a filled disc reads as "press me"
//                in exactly the state where pressing disconnects. What reports "lit" is the
//                room — [drawHeroAtmosphere] turns the backdrop [RefGlowOn] blue. Light is blue
//                and state is teal deliberately; separating them keeps the lit ring legible.
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

/** How long one turn of the connecting comet takes.
 *
 *  1400ms, up from 1000. The arc it replaced was a hard-edged 240° band, and a hard edge can
 *  be spun quickly without looking hectic because there is nothing to read but its position.
 *  A comet has a length, and length turning this fast reads as a fan blade — slowing it by
 *  40% is what lets the eye follow the head round. This is still the app's only indeterminate
 *  progress and the only animation that runs without being asked for. */
private const val POWER_ARC_SPIN_MS = 1400

/** How much of the circle the comet's tail covers.
 *
 *  300° of the 360, so the tail very nearly catches its own head: the missing 60° is the one
 *  thing that says which way round it is going, and closing it completely would turn the
 *  comet into a plain lit ring with a bright spot on it.
 *
 *  What was here before was a fixed 240° arc of flat colour, breathing between 96° and 240° as
 *  it turned — a spinner from a widget set with a pulse bolted on. This is a single stroke
 *  whose *alpha* runs from nothing at the tail to full at the head ([powerComet]), which is
 *  how motion actually looks: the trail is where the head has been, fading. Nothing about it
 *  needs to breathe, so nothing does, and the sweep is a constant again. */
private const val POWER_ARC_SWEEP_DEG = 300f

/** Where the comet's head sits when nothing is turning: the top of the circle.
 *
 *  The arc is drawn from 0° (three o'clock) forwards, so its head is [POWER_ARC_SWEEP_DEG]
 *  round; this offset is what carries that head to twelve o'clock, and the spin is added to
 *  it. With the system's animations off it is the whole rotation — a still comet parked at the
 *  top, which is a legible "working" mark rather than one extreme of an animation. */
private const val POWER_ARC_HEAD_OFFSET = -90f - POWER_ARC_SWEEP_DEG

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

/** The one-shot ring that fires the moment the tunnel comes up, and how far past the ring
 *  band it travels.
 *
 *  This is the only celebratory motion in the app and it is deliberately small: a single
 *  expanding hairline of [RefLive] that leaves the band, fades on an eased square, and is
 *  gone in under three quarters of a second. It exists because the connected state is
 *  otherwise reported by a colour change on a ring, which is easy to miss on a phone held at
 *  arm's length — a moving edge is not. It does not loop, it cannot be triggered by anything
 *  but the phase actually changing, and it is off entirely when the system's animations are.
 *
 *  The reach is why the halo needs its own canvas: [PowerSize] has 11dp of band around the
 *  disc, and this needs 34dp more than that, so the [Canvas] is sized with a
 *  [Modifier.requiredSize] that ignores the parent's constraints rather than being clipped
 *  to them. */
private const val POWER_IGNITION_MS = 720
private val PowerIgnitionReach = 34.dp

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
    // Springs rather than tweens, and two different ones: going down is fast and dead —
    // stiff, no bounce — because a press has to feel like it arrived the instant the finger
    // did; coming back up is softer and slightly under-damped, so the disc overshoots by
    // about a percent and settles. That asymmetry is the whole difference between a button
    // that feels mechanical and one that feels sprung, and it is two numbers.
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
    // The shadow travels with the scale — see [POWER_PRESS_SCALE].
    val elevation by animateDpAsState(
        targetValue = if (pressed) PowerPressElevation else PowerRestElevation,
        animationSpec = if (reduce) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "powerPressLift",
    )
    // And so does the light on the face: pressing it takes the specular down and brings a
    // little shade up from the foot, which is what a convex white object does when it is
    // pushed towards the surface under it.
    val pressShade by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = motionSpec(reduce, 140),
        label = "powerPressShade",
    )
    // The ignition ring — see [POWER_IGNITION_MS]. Keyed on the phase, so it fires once per
    // actual connection and not on recomposition, and snapped back to zero in every other
    // state so a disconnect cannot leave a half-drawn ring behind.
    val ignition = remember { Animatable(0f) }
    LaunchedEffect(phase, reduce) {
        if (phase == ConnPhase.CONNECTED && !reduce) {
            ignition.snapTo(0f)
            ignition.animateTo(1f, tween(POWER_IGNITION_MS, easing = FastOutSlowInEasing))
        } else {
            ignition.snapTo(0f)
        }
    }
    // The one coloured face left, over the white disc that is always there, so no value
    // of it can leave the button transparent mid-crossfade. There is deliberately no
    // second one for CONNECTED — see the section comment: the connected state is
    // reported by the ring, and the face stays white.
    val workingFace by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTING) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "powerWorkingFace",
    )
    // The mark: dark on the idle disc, accent while working, teal once up — on the same
    // crossfade as the rest of the header's ink. Connected is [RefLiveInk] rather than
    // [RefLive]: the face is white now, and the ring's own teal is tuned to glow on
    // near-black, which on white is a thin, washed-out mark. The darker teal reads at
    // the same 50dp as the other two marks do.
    val mark by animateColorAsState(
        targetValue = when (phase) {
            ConnPhase.OFF -> PowerInk
            ConnPhase.CONNECTING -> RefWorkingInk
            ConnPhase.CONNECTED -> RefLiveInk
        },
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "powerMark",
    )
    val ink = if (enabled) mark else mark.copy(alpha = 0.30f)
    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { ModeSwipeThreshold.toPx() } }
    val label = when {
        connected -> "Disconnect"
        phase == ConnPhase.CONNECTING -> "Cancel connecting"
        else -> "Connect"
    }
    Box(modifier.size(PowerSize), contentAlignment = Alignment.Center) {
        // The ignition halo, behind everything and outside the box: [Modifier.requiredSize]
        // is what lets it be bigger than its parent instead of clipped to it.
        if (ignition.value > 0f && ignition.value < 1f) {
            Canvas(Modifier.requiredSize(PowerSize + PowerIgnitionReach * 2)) {
                val t = ignition.value
                val band = (PowerDiscSize.toPx() / 2f) +
                    PowerRingGap.toPx() +
                    (PowerRingStroke.toPx() / 2f)
                val reach = PowerIgnitionReach.toPx()
                // Squared, so the ring is already faint by the time it is halfway out: the
                // eye catches the departure, not the arrival, and a linear fade reads as a
                // ripple loitering.
                val fade = (1f - t) * (1f - t)
                // The soft body of it first, travelling slower than the edge.
                drawCircle(
                    color = RefLive.copy(alpha = 0.14f * fade),
                    radius = band + reach * t * 0.62f,
                    style = Stroke(width = PowerRingStroke.toPx() * (1f + 5f * t)),
                )
                // Then the edge itself, thinning as it goes.
                drawCircle(
                    color = RefLive.copy(alpha = 0.55f * fade),
                    radius = band + reach * t,
                    style = Stroke(width = PowerRingStroke.toPx() * (1f - 0.45f * t)),
                )
            }
        }
        // The ring band, under the disc's own scale so a press doesn't drag it in.
        PowerRing(phase = phase, modifier = Modifier.matchParentSize())
        Box(
            Modifier
                .size(PowerDiscSize)
                .scale(scale)
                .shadow(
                    // 0 16px 34px rgba(0,0,0,.45) + 0 4px 10px rgba(0,0,0,.25). Both
                    // colours named, like the panel's: the platform's own default put a
                    // grey halo around a white disc on a near-black panel, which is the
                    // single most visible place on the screen for it. The elevation is
                    // animated — a press drops it to [PowerPressElevation].
                    elevation = elevation,
                    shape = CircleShape,
                    ambientColor = PowerShadowAmbient,
                    spotColor = PowerShadowSpot,
                )
                .clip(CircleShape)
                .background(PowerFace)
                // Vertical drag switches the mode. It sits before .clickable so a
                // drag that passes the touch slop is claimed here and the tap is
                // cancelled instead of also firing the tunnel; anything that never
                // moves that far is still a plain tap on the button. The mode is a
                // key so the callbacks can't go stale mid-gesture.
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
                    // A haptic on the primary action, and only on this one: the tunnel going
                    // up or down is the single thing on this screen with a consequence outside
                    // the app, and the confirmation should not depend on the user watching the
                    // ring. LongPress rather than a tick — it is the firmest of the standard
                    // constants, which is what a switch this size should feel like. The
                    // platform routes it through the system's own haptics setting, so a user
                    // who has turned touch feedback off gets nothing.
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                )
                // A swipe is invisible to a screen reader, so the same two outcomes
                // are offered as named actions on the button — which is also the only
                // form the affordance takes now that the chevrons are gone.
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Switch to Smart mode") {
                            onSwipeUp(); true
                        },
                        CustomAccessibilityAction("Switch to Manual mode") {
                            onSwipeDown(); true
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (workingFace > 0.01f) {
                Box(Modifier.matchParentSize().alpha(workingFace).background(PowerWorkingFace))
            }
            // The specular: a soft off-centre highlight, built from the disc's measured size
            // rather than as a fixed brush, which is why it is [Modifier.drawWithCache] and
            // not a top-level val — a radial gradient needs a centre and a radius in pixels,
            // and caching it means that arithmetic happens on resize instead of per frame.
            // Placed up and left of centre because every other light on this screen comes
            // from there; it is what turns a flat vertical ramp into something domed.
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val glow = Brush.radialGradient(
                            0.00f to Color.White.copy(alpha = 0.92f),
                            0.42f to Color.White.copy(alpha = 0.22f),
                            0.78f to Color.White.copy(alpha = 0.04f),
                            1.00f to Color.Transparent,
                            center = Offset(size.width * 0.32f, size.height * 0.20f),
                            radius = size.minDimension * 0.68f,
                        )
                        onDrawBehind { drawCircle(glow) }
                    }
            )
            Box(Modifier.matchParentSize().background(PowerFaceSheen))
            if (pressShade > 0.01f) {
                Box(Modifier.matchParentSize().alpha(pressShade).background(PowerPressShade))
            }
            // The rim, last of the surfaces and over all of them, so it stays a crisp edge
            // instead of being washed out by the sheen's own dark foot.
            Box(Modifier.matchParentSize().border(PowerRimStroke, PowerDiscRim, CircleShape))
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = label,
                tint = ink,
                // 78dp on the 118dp disc — two thirds of it, up from the 54% the mockup's
                // stroke svg used. Only the mark grew: [PowerDiscSize] and [PowerSize] are
                // untouched, so the button, the ring band and the hero's whole vertical
                // rhythm are exactly where they were, and what changed is how much of the
                // white face the symbol claims. At 54% the glyph read as a small icon
                // parked in a lot of white; at two thirds the disc reads as a power button
                // rather than as a disc with a power icon on it. Above ~0.7 the mark starts
                // touching the disc's dark foot in [PowerFaceSheen], which is what sets the
                // ceiling.
                modifier = Modifier.size(78.dp),
            )
        }
    }
}

/**
 * The ring in the band around the disc: a hairline track in every state, plus one mark
 * that says what the connection is doing.
 *
 * Connecting, a comet turns — [POWER_ARC_SWEEP_DEG] of tail fading back from a bright head,
 * once every [POWER_ARC_SPIN_MS]; see [powerComet] for why it is a gradient rather than an
 * arc of flat colour. Connected, it is the full circle in [RefLive] over a wider, fainter
 * stroke of the same colour, so the ring reads as lit rather than as drawn — and it is
 * completely still. Both fade with [PHASE_FADE_MS], and with the system's animations off the
 * comet parks at the top instead of turning ([POWER_ARC_HEAD_OFFSET]).
 *
 * Nothing here breathes. The live ring used to pulse its bloom on a 2.6s cycle, which is a
 * detail that looks considered in a screenshot and is a light flashing in your hand for as
 * long as the tunnel is up — on the one screen a user leaves open. The state is already said
 * three times over in colour: the ring, the headline's ink and the room's own light.
 */
@Composable
private fun PowerRing(phase: ConnPhase, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "powerArc")
    // Held as a State and read *inside* the draw lambda rather than unwrapped with `by` up
    // here. A spin read in composition invalidates this composable sixty times a second for
    // as long as the screen is up, in every phase; read in the draw scope, and only under the
    // `working` branch, it invalidates nothing at all unless the comet is actually on screen.
    val spin = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(POWER_ARC_SPIN_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "powerArcSpin",
    )
    val working by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTING) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "powerArcWorking",
    )
    val live by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTED) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "powerArcLive",
    )
    Canvas(modifier) {
        val stroke = PowerRingStroke.toPx()
        val radius = (PowerDiscSize.toPx() / 2f) + PowerRingGap.toPx() + (stroke / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // The track. Always there, so the band never looks empty and the mark has
        // something to travel along — and lit from the top rather than flat, so it reads
        // as a machined groove around the disc instead of a drawn circle. A flat 10% white
        // hairline is the giveaway detail on a lot of otherwise careful dark UI: real edges
        // are brightest where the light is.
        drawCircle(
            brush = PowerRingTrack,
            radius = radius,
            style = Stroke(width = stroke),
        )
        if (live > 0.01f) {
            // Lit, and static: a soft wide bloom under the ring, then the ring itself. Two
            // strokes on the same circle rather than one, because a single wide stroke at
            // this alpha is a fat soft ring, and what this wants to look like is a thin
            // bright one with light coming off it.
            drawCircle(
                color = RefLive.copy(alpha = 0.16f * live),
                radius = radius,
                style = Stroke(width = stroke * 3.2f),
            )
            drawCircle(
                color = RefLive.copy(alpha = 0.92f * live),
                radius = radius,
                style = Stroke(width = stroke),
            )
        }
        if (working > 0.01f) {
            // The comet. Rotating the whole drawing rather than the start angle is what
            // carries the sweep gradient round with the stroke — a brush is fixed in the
            // layer's own coordinates, so an arc that moved while its gradient stood still
            // would be a stroke fading in and out on the spot.
            rotate(
                degrees = if (reduce) {
                    POWER_ARC_HEAD_OFFSET
                } else {
                    spin.value + POWER_ARC_HEAD_OFFSET
                },
                pivot = center,
            ) {
                drawArc(
                    brush = powerComet(working, center),
                    startAngle = 0f,
                    sweepAngle = POWER_ARC_SWEEP_DEG,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // The head, as a dot on top of the stroke's own end: a round cap alone is
                // the same width as the tail, and what makes this read as something moving
                // rather than as a gradient on a ring is that the leading edge is the
                // brightest, densest point on it. The halo under it is the light it throws.
                val head = POWER_ARC_SWEEP_DEG * PI.toFloat() / 180f
                val headAt = Offset(
                    x = center.x + radius * cos(head),
                    y = center.y + radius * sin(head),
                )
                drawCircle(
                    color = RefWorking.copy(alpha = 0.22f * working),
                    radius = stroke * 2.1f,
                    center = headAt,
                )
                drawCircle(
                    color = RefWorking.copy(alpha = working),
                    radius = stroke * 0.85f,
                    center = headAt,
                )
            }
        }
    }
}

/**
 * The connecting mark's colour along its length: nothing at the tail, [RefWorking] at the head.
 *
 * A [Brush.sweepGradient] is the only brush whose axis is the same shape as the thing being
 * painted here. Its fractions run once round the circle from three o'clock, so the arc — drawn
 * from 0° for [POWER_ARC_SWEEP_DEG] — occupies the first `sweep / 360` of them, and the stops
 * are placed as fractions *of that*: `head` is where the stroke ends.
 *
 * The ramp is deliberately back-loaded. A linear fade from transparent to opaque spreads the
 * interesting part of a comet over its whole length and reads as a smear; keeping most of the
 * tail faint and putting the climb in the last third is what gives it a head and a trail. The
 * one stop past the head is transparent and is never drawn — the arc stops short of it — but a
 * sweep gradient wraps, so leaving it out would bleed the head's own colour round the gap and
 * back into the tail.
 */
private fun powerComet(alpha: Float, center: Offset): Brush {
    val head = POWER_ARC_SWEEP_DEG / 360f
    return Brush.sweepGradient(
        0.00f to RefWorking.copy(alpha = 0f),
        head * 0.34f to RefWorking.copy(alpha = 0.06f * alpha),
        head * 0.62f to RefWorking.copy(alpha = 0.20f * alpha),
        head * 0.82f to RefWorking.copy(alpha = 0.48f * alpha),
        head * 0.94f to RefWorking.copy(alpha = 0.82f * alpha),
        head to RefWorking.copy(alpha = 0.98f * alpha),
        1.00f to RefWorking.copy(alpha = 0f),
        center = center,
    )
}

// linear-gradient(160deg, #ffffff 0%, #e7e9ee 55%, #d9dce3 100%)
private val PowerFace = Brush.linearGradient(
    0.00f to Color.White,
    0.30f to Color(0xFFF4F5F8),
    0.55f to Color(0xFFE7E9EE),
    1.00f to Color(0xFFD9DCE3),
)

/** The connecting tint: [RefWorking] laid over the white face, light enough that the
 *  disc still reads as the same brushed surface holding a colour. Amber rather than the
 *  old blue accent so the face, the ring arc and the room's own light are
 *  all saying the same thing while an attempt is in flight. */
private val PowerWorkingFace = Brush.linearGradient(
    0.00f to RefWorking.copy(alpha = 0.10f),
    1.00f to RefWorking.copy(alpha = 0.26f),
)

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
    tab: HomeTab,
    query: String,
    searchOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onAddServer: () -> Unit,
    onRefreshPings: (List<SavedConfig>) -> Unit,
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
    LaunchedEffect(state.refreshingPings) {
        if (state.refreshingPings) pullState.startRefresh() else pullState.endRefresh()
    }
    val density = LocalDensity.current
    val fade = remember(density) { panelTopFade(with(density) { PanelFade.toPx() }) }
    val frost = remember(density) { panelFrost(with(density) { PanelFrostFade.toPx() }) }
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
            .drawBehind {
                drawPanelSheen()
                drawPanelTopEdge()
            }
    ) {
        // The card's own head clearance, then the card's own header row. The tabs, "+" and
        // search moved in here out of the hero: they belong to the list they operate, so they
        // are inside its bounds and travel with its glass.
        Spacer(Modifier.height(CardTopRoom))
        TabPillRow(
            selected = tab,
            searchOpen = searchOpen,
            onSelect = onSelectTab,
            onToggleSearch = onToggleSearch,
            onAdd = onAddServer,
        )
        SearchField(visible = searchOpen, query = query, onQueryChange = onQueryChange)
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
                // Enough room at the foot that the last row clears the floating usage
                // card, and none at all at the head: [CardTopRoom] above is the card's own
                // clearance, and anything here would be a second one.
                contentPadding = PaddingValues(top = 0.dp, bottom = 88.dp),
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
 * The clear air at the top of the browse card, above its tab row.
 *
 * 8dp. This used to be 26 as clearance for the docked mode pill that overhung the card; with the
 * pill gone it is plain padding, and the card's own controls are what it now holds off the edge.
 */
private val CardTopRoom = 8.dp

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
 * at the top of the list. 0.94 over the flag's own dimmed, desaturated artwork is a hint of
 * the country's colour behind the rows, worth a percent or two of luminance: every row's own
 * fill and every label on it are unchanged in contrast terms, and the page no longer has a
 * horizon across it.
 *
 * The base is [RefPanelBg], a colder near-black than the page's [RefBg], which is the first
 * half of the frost — see [panelFrost] for the rest, and for why none of this is a blur.
 */
private fun panelTopFade(heightPx: Float): Brush = Brush.verticalGradient(
    0.00f to RefPanelBg.copy(alpha = 0.78f),
    0.35f to RefPanelBg.copy(alpha = 0.88f),
    0.70f to RefPanelBg.copy(alpha = 0.92f),
    1.00f to RefPanelBg.copy(alpha = 0.94f),
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
    0.00f to RefFrost.copy(alpha = 0.155f),
    0.16f to RefFrost.copy(alpha = 0.125f),
    0.42f to RefFrost.copy(alpha = 0.072f),
    0.68f to RefFrost.copy(alpha = 0.034f),
    0.86f to RefFrost.copy(alpha = 0.013f),
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
    val depth = PanelSheenDepth.toPx()
    val radius = PanelCorner.toPx()
    clipRect(top = 0f, bottom = depth) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.00f to Color.White.copy(alpha = 0.055f),
                0.34f to Color.White.copy(alpha = 0.024f),
                0.68f to Color.White.copy(alpha = 0.008f),
                1.00f to Color.Transparent,
                startY = 0f,
                endY = depth,
            ),
            cornerRadius = CornerRadius(radius),
            size = size,
        )
    }
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
    val hairline = 1.dp.toPx()
    val radius = PanelCorner.toPx()
    clipRect(top = 0f, bottom = radius + hairline) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0.00f to PanelEdgeInk.copy(alpha = 0.04f),
                0.28f to PanelEdgeInk.copy(alpha = 0.13f),
                0.72f to PanelEdgeInk.copy(alpha = 0.13f),
                1.00f to PanelEdgeInk.copy(alpha = 0.04f),
            ),
            topLeft = Offset(hairline / 2f, hairline / 2f),
            size = Size(size.width - hairline, size.height - hairline),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = hairline),
        )
    }
}

// ── Tabs + search ─────────────────────────────────────────────────────────────
// The list's controls, inside the list's card: this row is [BrowseCard]'s first child, above the
// search field and the rows it filters. It spent a round floating in the hero above the card's
// top edge, which read as chrome adrift on the artwork — it belongs to the panel it operates, so
// it is inside the panel's bounds and it travels with the panel's glass.
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
            // [ListPad], so the pills line up with the rows they filter.
            .padding(horizontal = ListPad, vertical = 6.dp),    // .tab-row
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

/**
 * The selected pill's face — domed, and no longer pure white.
 *
 * Flat `Color.White` on near-black made a 60dp tab the highest-contrast object on the screen,
 * out-shouting the 140dp connect disc that is supposed to be the one thing the eye lands on.
 * 0.95 → 0.84 keeps it unmistakably the selected one, hands the top of the contrast range back
 * to the primary action, and reads as a lit surface rather than a cut-out.
 */
private val PillSelectedFill = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.95f),
    1.00f to Color.White.copy(alpha = 0.84f),
)

private val PillIdleFill = SolidColor(Color.Transparent)

/** The selected pill's lift. Shallow — it is a tab, and it sits on glass, not on artwork. */
private val PillElevation = 6.dp

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = remember { RoundedCornerShape(12.dp) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink by animateColorAsState(
        if (selected) PillInk else RefTextMid,
        tween(180),
        label = "pillInk",
    )
    Box(
        Modifier
            // Only the selected pill is a raised object. The idle one is bare ink on the
            // panel — giving both a face would turn a two-state control into two buttons.
            .then(
                if (selected) {
                    Modifier.embossed(shape, PillSelectedFill, PillElevation, pressed)
                } else {
                    Modifier.clip(shape).background(PillIdleFill)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            )
            // 14×7 from 16×9, and the corner follows it down a point so the pill keeps the
            // same shape rather than turning into a rounded rectangle at a smaller size.
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The "+" that opens the add-server sheet, in the same chip every icon-only control on this
 * screen wears ([GlyphButton]).
 *
 * Ink is [RefTextMid], the same as the idle magnifier beside it: it was accent-blue, which read
 * as the one coloured control in a row of white ones.
 */
@Composable
private fun AddServerButton(onClick: () -> Unit) {
    GlyphButton(onClick = onClick, label = "Add server") {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = RefTextMid,
            modifier = Modifier.size(ActionGlyph),
        )
    }
}

/** .search-btn: the magnifier, accent-blue while the field is open. */
@Composable
private fun SearchToggle(open: Boolean, onClick: () -> Unit) {
    val ink by animateColorAsState(if (open) RefAccent else RefTextMid, tween(180), label = "searchInk")
    GlyphButton(
        onClick = onClick,
        label = if (open) "Close search" else "Search servers",
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(ActionGlyph),
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
            // The mockup has no selected state; the active server gets the faintest tint,
            // and its title goes bold. There used to be a teal dot beside the name as well;
            // it is gone, along with the row's `dotColor` parameter — with a tint and a
            // weight already saying "this is the one", a third marker was just a speck.
            .background(if (isActive) Color.White.copy(alpha = 0.03f) else Color.Transparent)
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
 * which this screen no longer uses anywhere, so the fast tier is [RefLive] instead. How
 * many light up follows the app's own ping tiers (<80ms, <180ms, worse), so the row still
 * says how good the server is, not just what colour it is.
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
        filled == 3 -> RefLive
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
    val title = "Data used this session"
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
