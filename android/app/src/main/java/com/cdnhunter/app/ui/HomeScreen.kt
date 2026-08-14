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
//   • header flag — the active server's country as an ambient wash across the top of
//                   the screen: held at the left, faded out by the right edge and at
//                   the top and foot
//   • edge light  — connected only: a green ring along the screen's top edge
//   • top bar     — hamburger → Settings, account glyph → Profile
//   • status row  — "VLESS · 8443 · Smart" on a small glass chip, chevron → Settings
//   • connect bar — 88dp glass pill naming the active server's country, with a
//                   100dp white power circle whose centre sits exactly on the bar's
//                   right edge; the bar is cut away behind it, leaving a 3dp ring of
//                   clearance. Swipe the circle up for Smart mode, down for Manual
//   • network row — transport label, public IP (tap to copy)
//   • browse card — 28dp-topped panel: Main / Custom pills, + and search buttons,
//                   then one row per server
//                   (flag, country · city, ping, three load bars)
//   • usage card  — floats over the list bottom: session-traffic ring, live
//                   speed, chevron → Locations
//
// The connect bar names the country and nothing else — not the city, not the
// config's own name. The flag already says which country it is, so the line under
// it was a third read of the same fact; the city and the server's name both belong
// to the browse list below, where they distinguish one row from another.
//
// Smart / Manual is Home's other axis, orthogonal to which server is selected:
// Manual acts on the row the user tapped, Smart acts on whichever saved server
// currently measures best (SmartMode.kt scores latency, jitter and dropped probes
// over a rolling window). The mode is switched by swiping the power circle up or
// down and named next to the protocol, so it is legible without touching anything.
//
// HomeScreen() stays stateless about the VPN: it takes one HomeUiState snapshot
// plus event lambdas, so VpnTab() remains the single owner of connection state.
// The only state kept here is view state nothing else needs — which tab is
// selected, whether search is open, and the query.
//
// The flag is the top of the screen, but as light rather than as a picture. One
// country, one image, stretched across the whole header — behind the top bar, behind
// the connect bar, behind the network row — and then faded out on three sides, so what
// is left is a wash of the country's colours held at the left of the screen and dark,
// plain page everywhere the UI needs to be read. It is drawn FillBounds into a box that
// is always [FLAG_ASPECT], not into the panel itself: every source — a square bundled
// asset, a 5:3 German flagcdn SVG, a 19:10 American one — is stretched to the same 4:3
// rectangle, which is then centred and clipped to the panel, so the flag's proportions
// are the same country to country and phone to phone. FillBounds rather than Crop within
// that box because it shows all of the flag at close to a real flag's own proportions,
// where cropping to fill would magnify one band of it and throw the rest away. It is
// flattened and de-bowed first, and rasterised well above the header's own pixel width,
// so the bands read level and crisp — see FlagArtwork.kt.
//
// The fade is an alpha mask, not a coat of paint. Two gradients multiply into the
// artwork's own alpha ([HeaderFlagFadeX], [HeaderFlagFadeY]): the horizontal one holds
// the flag at full strength from the left edge to just past the middle and takes it to
// nothing by the right, the vertical one takes it to nothing at the status bar and
// again at the header's foot. Masking rather than scrimming is what keeps this ambient:
// where the flag is gone the page's own gradient is what shows, at exactly the value it
// has everywhere else, so the header has no edges of its own — no darker rectangle at
// the top, no seam where it meets the browse card.
//
// Two things sit between the artwork and that mask: a slight desaturation
// ([HEADER_FLAG_SATURATION] — a printer's colours brought onto a screen), and a soft
// vertical scrim ([HeaderFlagScrim]) drawn inside the masked layer, so it darkens the
// flag and tapers away with it instead of tinting the page. There is no vignette any
// more: darkening the corners was the horizontal mask's job the moment the flag stopped
// reaching them. Together with [HEADER_FLAG_ALPHA] these take the flag down to a
// background without taking its colour away. The worst case for legibility is the
// brightest band the app can draw — a white flag, at the left, level with the status
// row, where the mask is full and the scrim at its lightest — and at 0.88 alpha that
// composited to #515152, which [RefTextHi] cleared at 7.4:1 and the white power disc
// cleared easily. [HEADER_FLAG_ALPHA] is 0.70 now, which can only darken that band, so
// those are floors rather than measurements. The screen's dimmer inks still do not clear
// it — [RefTextMid] was 3.0:1 there — which is what [StatusChipMaterial] is for.
//
// Choosing another server crossfades the flag instead of cutting to it: a 420ms fade
// in over a 260ms fade out, the incoming flag settling from 1.04 and the outgoing one
// easing back to 0.99, so the change reads as one image replacing another.
//
// The connect bar is glass over that flag, not a picture of its own. Its material is
// weighted to the left — [BarSurface] is a translucent top-lit floor across the whole
// pill, [BarMaterial] a horizontal ramp near-solid where the country name starts and
// gone by 58% — which is the treatment the bar had before the flag was moved into it,
// and it does two jobs here at once: the headline sits on real material, and the flag
// reads through the end of the pill nearest the power circle. When there is no flag to
// draw — country unresolved, no bundled asset, still decoding — the header carries
// [HeaderFlagFallback], a neutral slate wash, so the top is never a void. At the bar's
// head sits the country's flag as a circular badge — the same [CountryFlagBadge] the
// server list draws, from the same bundled asset — so the one flag reads the same here
// as in the list below.
//
// The status row is glass too, at chip size ([StatusChipMaterial]). It is the only row
// with dim ink on it and it sits where the scrim is deliberately lightest, so over a
// pale flag its mode word and its chevron were reading at 2:1 and 1:1 with nothing
// under them. The chip also gives the row the one thing it never had: a mark saying it
// is a button. Everything else up here is either white ink or, at the foot, already
// under the heavy end of the scrim.
//
// The only light on this screen is ambient, and there is deliberately very little of
// it: three soft directional sources — top, left and right — falling onto the connect
// control at a few percent. They are static gradient brushes, never [Modifier.blur],
// so the light stays crisp instead of smudged. Idle they are white; connected they
// turn green, tighter and a shade stronger. Over a flag that light has to be a lit room
// rather than a lamp pointed at the phone — hence the single-digit percentages. The
// bar's own top and edge highlights stay neutral white so the artwork under them keeps
// its colours.
//
// The power circle is a flat brushed-white disc in every state — the mockup's
// `.power-glow` is `display:none` — and nothing is attached to it: the two mode
// chevrons that used to sit above and below it are gone. Smart / Manual is still
// switched by swiping the circle up or down, or by its two accessibility actions, and
// the mode is still named in the status row, which is where this screen states the
// connection's facts in words. The one thing on it that answers to the tunnel is its
// mark, which goes green while the tunnel is up (see [headerInk]).
//
// Connected is teal (--teal, #35d6b8) where the mockup says teal — the usage ring's
// accent and the active row's dot. Everything else connected is the one green (--green,
// #34d17a): the ambient light, the top edge light, the header's own headline ink and the
// power mark. There is no ON/OFF pill and no pending state: the screen is either
// connected or it isn't.
//
// Green is the header's headline ink and nothing below it. The country on the connect
// bar, the protocol on the status chip, the transport and the public IP all cross to
// [RefGreen] together, on the same 520ms fade as the light, so the top of the screen
// changes state as one thing. What stays put is deliberate: the top bar's two glyphs —
// hamburger and account — are navigation, not connection state, and they are the same
// white whether the tunnel is up or down; so are the screen's secondary inks up here
// (the mode word, the separators, the chevron), which are settings and punctuation
// rather than state, and which at 14sp would be the one place a mid-chroma green could
// fall under contrast.
//
// Connected also lights the screen's own top edge in that same green: a hairline
// along the top, brightest at the centre, turning both corners and running a short
// way down each side, over a soft inward bleed. Same reasoning as the ambient
// light — one signal, stated as light rather than as a label — and the same
// technique, plain gradient brushes rather than a blur, so the edge stays a crisp
// line instead of a smudge. It is drawn last, above every row, because an edge
// light that the header's own layers can paint over is not an edge light. It is
// quieter than it was: over a flag, a bright green rim read as a filter laid over the
// artwork rather than as light caught on the glass.
//
// Motion here is minimal and all of it respects the system's "remove animations"
// setting (see [rememberReduceMotion]): the flag crossfade, the ambient colour
// crossfade, the connected ink crossfade, the edge light's fade in and out, and the two
// words that ever change on their own — the mode and the public IP — all collapse to an
// instant cut when animations are off.


import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
/**
 * The same green, dark enough to read *on* white — used for the power button's mark.
 *
 * [RefGreen] is tuned to glow on near-black; on the button's white face it is a pale,
 * thin mark that fails contrast. This is the same hue at roughly a third of the
 * lightness, which clears 4.5:1 on the disc's lightest stop.
 */
private val RefGreenInk = Color(0xFF0E8A48)
private val RefLoadMed = Color(0xFFE0B23B)     // .load-med bars
// The mockup only illustrates low and medium load, but the app measures a third
// tier (>180ms, see [LoadBars]); one step hotter in the same 0xE0 family.
private val RefLoadHigh = Color(0xFFE0563B)
private val PillInk = Color(0xFF05070C)        // .tab-pill.active text colour
private val PowerInk = Color(0xFF0C0E14)       // .power-btn svg colour

// ── Hero surface ──────────────────────────────────────────────────────────────
// The top of the screen is one panel, and it is always the same panel: [ChromeBg] — the
// same #0B0B0D the system status bar and navigation bar are painted
// (res/values/themes.xml), so the top of the app reads as part of the window — under
// [HeroOffSurface], under the country's flag whenever there is a server to show one for
// (see [HomeUiState.heroFlagCountry]), lit from above and seated by its own floor.
//
// What [ConnPhase] changes is the light on it, not the panel itself:
//
//   OFF        — white light, the plain [HeroBorder] hairline.
//   CONNECTING — the light turns [RefAccent] and tightens, the hairline lights with it,
//                and the power ring's arc turns. The animation that says "working" is
//                down in the status panel ([ConnectingPulse]), not up here: a band
//                sweeping across the flag made the whole top of the screen restless in
//                the one state where the user is already waiting on it.
//   CONNECTED  — the light turns [RefGreen], the hairline brightens, the power ring
//                closes into a lit circle.
//
// The panel is framed rather than full-bleed: rounded at the foot, a hairline around
// it, a top sheen, an inner floor at its foot and a two-colour shadow under it. That is
// the "card" — a raised surface the rows sit on, with no hard rectangle anywhere on it.


/** The window's own colour: `android:statusBarColor` / `navigationBarColor`. */
private val ChromeBg = Color(0xFF0B0B0D)

/** The panel's foot corners. Wider than the browse card's [PanelCorner] on purpose:
 *  it is the larger surface, and the two radii read as one family rather than a
 *  repeat. */
private val HeroCorner = 34.dp

/**
 * The OFF face: [ChromeBg] with a barely-there lift toward the top, so the panel is
 * the window's colour and still reads as a surface with light falling on it. The last
 * stop is a shade under the chrome, which is what separates the panel's foot from the
 * page it floats over without drawing a line there.
 */
private val HeroOffSurface = Brush.verticalGradient(
    0.00f to Color(0xFF12141A),
    0.42f to Color(0xFF0E1014),
    1.00f to Color(0xFF090A0D),
)

/** The hairline around the panel, idle. White, not [RefBorder]: over both the chrome
 *  and a flag it has to be the same edge. */
private val HeroBorder = Color.White.copy(alpha = 0.07f)

/** A single highlight along the panel's top inside edge — the light in the room
 *  catching the glass, and the reason the frame doesn't need a second border. */
private val HeroSheen = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.08f),
    0.02f to Color.White.copy(alpha = 0.03f),
    0.07f to Color.Transparent,
    1.00f to Color.Transparent,
)

/** How long the phase crossfade takes — the ink, the light, and every surface. */
private const val PHASE_FADE_MS = 520


// ── Connected colour ──────────────────────────────────────────────────────────
// One thing says "the tunnel is up": the ambient light turns green. There is no
// tone system, no per-state palette, no pill, no pending state, and — deliberately
// — nothing painted over the connect bar's flag, so the flag's own colours are the
// real ones whether the tunnel is up or down.
//
// [RefTeal] is used at full strength where the mockup uses it and the flag isn't
// underneath: the usage ring's `conic-gradient(var(--teal) …)` and the active row's
// dot.

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
private val FlagSize = 36.dp         // .server-flag — the connect bar's badge
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
private val RowFlagSize = 30.dp
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
// that the gesture completes inside the button's own 100dp.
//
// Nothing on the circle draws this gesture any more. Two chevrons used to sit above
// and below it; on a screen whose top is now one large image they were the only marks
// on it that pointed at nothing the eye was looking for, and the mode they switch is
// already written next to the protocol. The gesture is also offered as two named
// accessibility actions on the button (see [PowerCircle]), which is the part of it a
// chevron could never have carried anyway.
private val ModeSwipeThreshold = 20.dp

// ── Lighting ──────────────────────────────────────────────────────────────────
// The hero panel is lit rather than tinted, and all of it is built from three kinds of
// layer, in this order, on every state:
//
//   1. a key light   — one soft cone from above and slightly right of the connect
//                      control, which is what makes the panel read as a surface with a
//                      light source rather than as a flat rectangle;
//   2. two rim fills  — off each side edge, level with the connect bar, so the panel's
//                      sides lift away from the page instead of ending at a hairline;
//   3. a floor        — a wide, very dark gradient at the foot, which is the panel's own
//                      contact shadow: it is what seats the panel on the page and what
//                      lets the browse card below start from black without a seam.
//
// Every one of them is a plain gradient brush. There is no [Modifier.blur] and no render
// effect anywhere on this screen: a blur at these radii costs a full offscreen pass per
// frame and, at these alphas, reads as a smudge rather than as light. A gradient with the
// right stops is both cheaper and cleaner.
//
// The strengths are deliberately small and they are *layered* rather than summed into one
// bright wash — that is the whole difference between this and a glow blob. The key light
// peaks at 5.4% and is gone by the panel's foot; the rims peak at 3.2%; the floor is the
// only heavy layer and it is black, not colour. Cover any single one of them and the
// panel looks flat; look for any single one of them and it is hard to find. That is the
// intent.
//
// Idle the light is white. Connecting it is [RefAccent], connected [RefGreen], with a
// tighter falloff and a little more strength in both — the same three sources, sharpened,
// so a state change reads as the room changing colour rather than as a repaint.
private const val KEY_LIGHT_IDLE = 0.034f
private const val KEY_LIGHT_ON = 0.054f
private const val RIM_LIGHT_IDLE = 0.020f
private const val RIM_LIGHT_ON = 0.032f

/**
 * The panel's contact shadow, drawn inside its own foot: a black gradient over the
 * bottom fifth of the panel.
 *
 * This is what a [Modifier.shadow] under the panel cannot do. The panel's foot is where
 * it meets the browse card, and an outer shadow there would be drawn *behind* the card
 * — invisible. Drawn inside instead, it darkens the panel's own last few dp, so the
 * panel appears to curve away from the light and the card below reads as sitting in
 * front of it. It is the same trick the connect bar's [BarSheen] uses at its foot, at
 * panel scale.
 */
private val HeroFloor = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.72f to Color.Transparent,
    0.88f to Color.Black.copy(alpha = 0.10f),
    1.00f to Color.Black.copy(alpha = 0.26f),
)

/**
 * The panel's cast shadow, as two colours rather than one.
 *
 * [Modifier.shadow] takes an ambient and a spot colour, and giving both the platform
 * default (opaque black at the elevation's own alpha) is what makes an elevated dark
 * surface look like it is sitting in dirty grey fog. The ambient half is the light
 * bouncing around the room — near-black and wide; the spot half is the key light's own
 * shadow — deeper, and the one that gives the panel its direction. Tuned for a panel on
 * near-black: at 24dp the platform's own values would put a visible grey halo over the
 * page's gradient.
 */
private val HeroShadowAmbient = Color.Black.copy(alpha = 0.62f)
private val HeroShadowSpot = Color.Black.copy(alpha = 0.85f)

/** How far the panel is lifted off the page. */
private val HeroElevation = 24.dp

/**
 * The screen-level light: key light, then the two rims. [color] is the phase's own
 * colour and [lit] tightens the falloff for the two states that have one.
 */
private fun DrawScope.drawHeroLighting(color: Color, lit: Boolean) {
    val key = if (lit) KEY_LIGHT_ON else KEY_LIGHT_IDLE
    val rim = if (lit) RIM_LIGHT_ON else RIM_LIGHT_IDLE
    // Lit pulls the mid stop in and the tail down, so the falloff is a shorter, cleaner
    // ramp; idle spreads the same light over more of the panel.
    val mid = if (lit) 0.34f else 0.46f
    val tail = if (lit) 0.74f else 0.88f

    fun cone(centre: Offset, radius: Float, peak: Float) = Brush.radialGradient(
        0.00f to color.copy(alpha = peak),
        mid to color.copy(alpha = peak * 0.42f),
        tail to color.copy(alpha = peak * 0.06f),
        1.00f to Color.Transparent,
        center = centre,
        radius = radius,
    )

    // The key light: above the panel, centred over the bar/button seam, so the
    // brightest part of the wash lands on the connect pill's top edge.
    drawRect(cone(Offset(size.width * 0.55f, -size.height * 0.30f), size.height * 1.30f, key))
    // The two rims: level with the connect bar, just outside the panel's own edges.
    drawRect(cone(Offset(-size.width * 0.16f, size.height * 0.56f), size.width * 0.80f, rim))
    drawRect(cone(Offset(size.width * 1.16f, size.height * 0.52f), size.width * 0.80f, rim))
}


/**
 * The bar-level half: the same three directions, read as highlights on the pill
 * itself rather than as a wash behind it. Top light first, then the two edges.
 *
 * These two are white and state-independent, unlike the wash behind the bar. They
 * fall on glass that has a flag behind it, and the flag's colours are not the
 * tunnel's to change. They were dimmed along with the ambient wash, for the same
 * reason: a highlight that competes with the artwork under it stops reading as glass.
 */
private val BarTopLight = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.11f),
    0.06f to Color.White.copy(alpha = 0.05f),
    0.42f to Color.White.copy(alpha = 0.012f),
    1.00f to Color.Transparent,
)

private val BarEdgeLight = Brush.horizontalGradient(
    0.00f to Color.White.copy(alpha = 0.07f),
    0.24f to Color.Transparent,
    0.76f to Color.Transparent,
    1.00f to Color.White.copy(alpha = 0.08f),
)

/**
 * Whether the device has animations turned off — developer options' "Animation off",
 * Battery Saver, or Settings → Accessibility → "Remove animations" all set the same
 * animator duration scale to zero.
 *
 * Every animation on this screen reads this and collapses to [snap] when it is true:
 * an ambient light that fades, an edge light that blooms and a chevron that brightens
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

/**
 * The header's headline ink: [idle] while the tunnel is down, [RefAccent] while an
 * attempt is in flight, [RefGreen] while it is up — crossfaded on the same
 * [PHASE_FADE_MS] the ambient light and the hero's surfaces use, so the top of the
 * screen changes state as one thing. The country on the connect bar, the protocol on
 * the status chip, the transport and the public IP all share this; the power mark
 * shares it too, from its own dark idle. Secondary inks up here — the mode word, the
 * separators, the chevron, the top bar's navigation glyphs — deliberately do not,
 * because they are settings and punctuation rather than connection state (and at 14sp
 * a mid-chroma green is the one place contrast could fall short). Like the light, it
 * cuts instantly when the system asks for no animations.
 */
@Composable
private fun headerInk(phase: ConnPhase, idle: Color = RefTextHi): Color {
    val reduce = rememberReduceMotion()
    val target = when (phase) {
        ConnPhase.OFF -> idle
        ConnPhase.CONNECTING -> RefAccent
        ConnPhase.CONNECTED -> RefGreen
    }
    val ink by animateColorAsState(target, motionSpec(reduce, PHASE_FADE_MS), label = "headerInk")
    return ink
}

/** The colour of the light in the room for [phase]: white idle, blue working, green up. */
@Composable
private fun phaseLight(phase: ConnPhase): Color {
    val reduce = rememberReduceMotion()
    val target = when (phase) {
        ConnPhase.OFF -> Color.White
        ConnPhase.CONNECTING -> RefAccent
        ConnPhase.CONNECTED -> RefGreen
    }
    val color by animateColorAsState(target, motionSpec(reduce, PHASE_FADE_MS), label = "phaseLight")
    return color
}

// ── Connected edge light ──────────────────────────────────────────────────────
// The screen's top edge lights up green while the tunnel is up: a hairline along the
// very top, brightest at the centre and fading toward both corners, turning each
// corner and running a short way down the sides, over a soft bleed inward. Three
// plain gradient brushes and nothing else — no blur, no shadow, no render effect —
// so it reads as light on an edge rather than as a green fog over the header.
//
// Every alpha here is a little over half what it was. The edge used to be drawn on
// near-black; on the header flag the old strength read as a green filter over the
// artwork, and the hairline itself was bright enough to be the first thing the eye
// went to on the whole screen. It is [RefGreen], the same one the ambient light uses,
// because it is the same signal seen from the screen's border instead of from behind
// the connect bar.
private val EdgeGlowBleed = 92.dp         // how far inward the wash reaches
private val EdgeGlowSideRun = 148.dp      // how far down each side the ring carries
private val EdgeGlowLine = 1.5.dp         // the lit edge itself

@Composable
private fun ConnectedEdgeGlow(connected: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    // 520ms matches the ambient light's own crossfade, so connecting reads as one
    // change of light rather than as two effects starting at the same time.
    val strength by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = motionSpec(reduce, 520),
        label = "edgeGlow",
    )
    if (strength <= 0.01f) return
    Box(modifier.drawBehind { drawTopEdgeRing(strength) })
}

private fun DrawScope.drawTopEdgeRing(strength: Float) {
    fun green(alpha: Float) = RefGreen.copy(alpha = alpha * strength)

    val bleed = EdgeGlowBleed.toPx()
    val line = EdgeGlowLine.toPx()
    val sideRun = EdgeGlowSideRun.toPx()

    // The wash: strongest right under the edge, gone by the end of the bleed.
    drawRect(
        brush = Brush.verticalGradient(
            0.00f to green(0.085f),
            0.22f to green(0.038f),
            0.55f to green(0.012f),
            1.00f to Color.Transparent,
            startY = 0f,
            endY = bleed,
        ),
        size = Size(size.width, bleed),
    )
    // The lit edge. The centre is where the light is; the corners are where it has
    // travelled furthest, so they keep only a trace of it.
    drawRect(
        brush = Brush.horizontalGradient(
            0.00f to green(0.06f),
            0.18f to green(0.24f),
            0.50f to green(0.42f),
            0.82f to green(0.24f),
            1.00f to green(0.06f),
        ),
        size = Size(size.width, line),
    )
    // Both corners turned: the same hairline continuing down each side, fading out
    // well before the connect bar, so the effect reads as an edge and not a frame.
    val sideBrush = Brush.verticalGradient(
        0.00f to green(0.17f),
        0.35f to green(0.05f),
        1.00f to Color.Transparent,
        startY = 0f,
        endY = sideRun,
    )
    drawRect(brush = sideBrush, topLeft = Offset(0f, 0f), size = Size(line, sideRun))
    drawRect(
        brush = sideBrush,
        topLeft = Offset(size.width - line, 0f),
        size = Size(line, sideRun),
    )
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
// ── Header flag panel ─────────────────────────────────────────────────────────
// The flag is the top of the screen: one image, edge to edge, behind everything the
// header draws. See the file header for why it is FillBounds and what the three
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
 * Lower than it was, because the wash is on in every state now rather than only while
 * connected (see [HomeUiState.heroFlagCountry]). A value tuned for "the flag is the
 * reward for connecting" is too loud for "the flag is what the panel looks like": at 0.88
 * the artwork was a background *image* the rows sat on top of, and the app spent most of
 * its life looking like it. At 0.70, under the scrim, it is an ambient tint — the country
 * is unmistakable, and nothing on the panel is competing with the text.
 */
private const val HEADER_FLAG_ALPHA = 0.70f


// The flag crossfade. Enter is longer than exit, per the app's own motion rules, and
// the scale settle runs longer than either so the incoming flag is still easing when
// its fade has finished — that is what makes the change read as one image arriving
// rather than as two frames dissolved together.
private const val FLAG_FADE_IN_MS = 420
private const val FLAG_FADE_OUT_MS = 260
private const val FLAG_SETTLE_MS = 620

/**
 * A soft vertical scrim, drawn *inside* the masked flag layer: heaviest at the very
 * top, where the status bar's own glyphs have to survive whatever band lands there, and
 * again toward the foot; lifted through the middle, where the connect control sits and
 * the flag is allowed to be a flag. Because it lives inside the mask it tapers away
 * exactly where the flag does — it darkens the artwork, never the page. There is no
 * opaque [RefBg] bottom stop any more: the foot fade into the page is [HeaderFlagFadeY]'s
 * job, so the scrim only ever has the flag to darken.
 */
private val HeaderFlagScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.55f),
    0.22f to Color.Black.copy(alpha = 0.40f),
    0.50f to Color.Black.copy(alpha = 0.38f),
    0.78f to Color.Black.copy(alpha = 0.46f),
    1.00f to Color.Black.copy(alpha = 0.58f),
)

/**
 * The horizontal half of the flag's alpha mask. The flag is held at full strength from
 * the left edge to just past the middle, then taken to nothing by the right. This is
 * what holds the wash at the left of the screen — and what darkens the right-hand
 * corners without a vignette, because where the mask reaches zero the page's own
 * gradient is simply what shows. The colour is irrelevant; only the alpha is read, by
 * the [BlendMode.DstIn] pass in [HeaderFlag].
 */
private val HeaderFlagFadeX = Brush.horizontalGradient(
    0.00f to Color.Black,
    0.52f to Color.Black,
    1.00f to Color.Transparent,
)

/**
 * The vertical half of the mask: nothing at the status bar, nothing at the header's
 * foot, full in between. The top fade keeps the artwork from crowding the status bar's
 * glyphs; the foot fade is what lets the browse card meet plain page with no seam, in
 * place of the scrim's old opaque-[RefBg] bottom stop.
 */
private val HeaderFlagFadeY = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.16f to Color.Black,
    0.70f to Color.Black,
    1.00f to Color.Transparent,
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
                // Two flags, one shape. The artwork is drawn into a box that is always
                // [FLAG_ASPECT] — tall enough to cover the panel, so its width overhangs
                // by however much the panel's own ratio differs from 4:3 and the overhang
                // is clipped — rather than straight into the panel. Both sources are
                // stretched to the same 4:3 rectangle by FillBounds, so a 5:3 German
                // flagcdn SVG, a 19:10 American one and a square bundled asset all arrive
                // at identical proportions, and a panel that is 1.28:1 on one phone and
                // 1.34:1 on another no longer changes the flag's shape with it. This is
                // the header's half of the one rule the badge follows too — see
                // [FLAG_ASPECT], which owns both.
                val cc = canonicalCountryCode(code)?.lowercase() ?: code
                val key = if (flag === remote) "flag-cdn-$cc" else "flag-rect-$cc"
                Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(flag)
                            .size(FLAG_RENDER_PX)
                            // Coil keys a request by `data.toString()`, and a ByteBuffer's is
                            // "HeapByteBuffer[pos=0 lim=N cap=N]" — two countries whose SVGs
                            // happen to be the same byte length would share a cache entry and
                            // one would draw the other's flag. Key by the country instead.
                            .memoryCacheKey(key)
                            .diskCacheKey(key)
                            // AnimatedContent is already crossfading between two whole
                            // panels; a second fade inside the incoming one only makes the
                            // first half of that transition look like a load.
                            .crossfade(false)
                            .build(),
                        imageLoader = getFlagImageLoader(context),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        alpha = HEADER_FLAG_ALPHA,
                        colorFilter = desaturate,
                        filterQuality = FilterQuality.High,
                        // flagcdn unreachable, or no such flag there: fall back to the
                        // bundled asset rather than to the neutral wash, which is what
                        // every country outside VPN_FLAG_COUNTRIES already draws.
                        onError = { remoteFailed = true },
                        error = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            // Unbounded, and this is load-bearing: `aspectRatio` only
                            // honours the ratio if the size it computes fits the incoming
                            // constraints, and a 4:3 box as tall as a ~1.3:1 panel is
                            // slightly wider than the panel. Bounded, the modifier would
                            // quietly fall back to filling the panel — which is the very
                            // per-device ratio drift this box exists to remove. Given an
                            // unbounded width it keeps the ratio and overhangs instead,
                            // and the parent's `clipToBounds` takes the overhang off.
                            .wrapContentWidth(unbounded = true)
                            .aspectRatio(FLAG_ASPECT, matchHeightConstraintsFirst = true),
                    )
                }
            }
        }
        // Inside the masked layer, so it darkens the flag and tapers away with it.
        Box(Modifier.matchParentSize().background(HeaderFlagScrim))
    }
}

// ── Connect bar material ──────────────────────────────────────────────────────
// The pill is glass now: the flag is behind the whole header, so the bar's own job is
// to be a surface the country name is legible on without hiding the artwork it sits
// on. Two layers do that, in this order.

/**
 * The glass floor, top-lit so the pill still reads as a raised surface rather than a
 * flat cut-out. Same shape as the opaque version this replaces — bright at the top,
 * [RefElev1] through the middle, deeper at the foot — at roughly a third the alpha,
 * which is what lets the flag through across the whole pill.
 */
private val BarSurface = Brush.verticalGradient(
    0.00f to Color(0xFF1A1D25).copy(alpha = 0.42f),
    0.55f to RefElev1.copy(alpha = 0.36f),
    1.00f to Color(0xFF0B0D11).copy(alpha = 0.50f),
)

/**
 * The material under the headline: near-solid where the country name starts, gone by
 * 58% of the pill's width.
 *
 * This is the ramp the bar carried before the flag was moved inside it, restored
 * to the job it was written for. It reads as one surface with the flag showing
 * through its far end rather than as two halves, because the stops are a long ease
 * rather than a step: 0.94 → 0.88 over the first seventh, then down through 0.52 and
 * 0.16 to nothing at 0.58, by which point the pill has [PowerCut] left before the
 * power circle's clearance takes over.
 */
private val BarMaterial = Brush.horizontalGradient(
    0.00f to RefElev1.copy(alpha = 0.94f),
    0.14f to RefElev1.copy(alpha = 0.88f),
    0.30f to RefElev1.copy(alpha = 0.52f),
    0.44f to RefElev1.copy(alpha = 0.16f),
    0.58f to Color.Transparent,
)

/** The pill's hairline. Over artwork, [RefBorder] disappeared; white does not. */
private val BarBorder = Color.White.copy(alpha = 0.11f)

/**
 * What closes [BarMaterial]'s ramp when there is no flag behind the pill — i.e. every
 * state but connected. Drawn over it, at the phase's own crossfade alpha, so the pill
 * becomes one even raised surface across its whole width instead of dissolving into the
 * panel at 58%. Slightly lighter at the head than at the foot, matching the top-lit
 * shape [BarSurface] already has.
 */
private val BarSolid = Brush.verticalGradient(
    0.00f to Color(0xFF191C24).copy(alpha = 0.86f),
    0.55f to Color(0xFF14171E).copy(alpha = 0.90f),
    1.00f to Color(0xFF0E1015).copy(alpha = 0.94f),
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
     * Blank is blank: the row renders no dash, no placeholder and no dangling
     * separator when there is nothing to state. A lone "—" is a value the user can
     * neither read nor copy, and it looked like a bug every time the lookup was slow.
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

/**
 * `.protocol-line` — the transport the active config actually dials, and the port.
 *
 * Not the base protocol. This line used to read the config's [SavedConfig.proto], which
 * is "VLESS" for every server this app is ever handed — a word that is the same on every
 * row of every list and therefore says nothing about the server under it. The transport
 * does say something: it is what decides whether a server survives a given network, and
 * it is the field someone comparing two configs actually reads. So the line now reads
 * "Reality · 8443", "WebSocket · 443", "gRPC · 2087".
 *
 * [com.cdnhunter.app.vpn.ConfigUriParser.transportOf] resolves it from the URI itself
 * rather than from [SavedConfig.network], because the parsed proxy map cannot express
 * two of the cases: mihomo omits `network` entirely for plain TCP, and REALITY is not a
 * transport in it at all (it is `reality-opts` beside `tls: true`). It never throws and
 * never returns blank — anything it cannot read comes back "TCP", which is what an
 * unadorned proxy is — but the `ifBlank` here is kept as the belt to that braces, so a
 * future change there cannot empty this line.
 *
 * The port is [SavedConfig.port], which is what the parser read out of the URI itself;
 * the mockup's literal "443" was only ever placeholder copy, and a config on 8443, 2087
 * or 80 says so here. A config whose URI carried no port at all falls back to its
 * protocol's default in the parser, so there is never a port shown that the connection
 * isn't actually using.
 */
private fun protocolLabel(cfg: SavedConfig?): String {
    if (cfg == null) return "No server selected"
    val transport = com.cdnhunter.app.vpn.ConfigUriParser.transportOf(cfg.uri).ifBlank { "TCP" }
    return if (cfg.port > 0) "$transport · ${cfg.port}" else transport
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

    Box(modifier.fillMaxSize().background(PageGradient)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
                onSetMode = onSetMode,
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

        // Last, so nothing paints over it: the green ring on the screen's top edge
        // while the tunnel is up. Purely drawn — no clickable, no pointer input —
        // so every row underneath keeps its own touches.
        ConnectedEdgeGlow(
            connected = state.connected,
            modifier = Modifier.matchParentSize(),
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
// The hero panel. One framed surface — rounded at the foot, hairlined, lit from above,
// seated on the page by its own shadow and its own inner floor — carrying the four rows.
//
// What changes between the three [HomeUiState.phase] values is the *light*, not the
// panel. The flag wash is on in all three whenever there is a server to show one for
// (see [HomeUiState.heroFlagCountry]), so connecting no longer paints a moving band
// across the artwork; that animation moved down into the status panel where it belongs
// (see [ConnectingPulse]). Up here a phase change is: the key light and the two rims
// change colour and tighten ([drawHeroLighting]), the frame changes colour, and the ink
// follows. Nothing slides, nothing sweeps.
//
//   OFF        — white light, [HeroBorder] frame, flag wash if a server exists.
//   CONNECTING — [RefAccent] light, accent frame.
//   CONNECTED  — [RefGreen] light, brighter frame.
//
// The layers stack, from the back: [ChromeBg] (the window's own colour, so the panel is
// never transparent) → [HeroOffSurface] → the flag → the lighting → [HeroFloor] → the
// [HeroSheen] top highlight → the frame. The flag crossfades on [PHASE_FADE_MS], as do
// the colours; the OFF surface under it never fades, which is what keeps the panel opaque
// mid-transition.
//
// The gaps between rows are the mockup's own margins (4 / 12 / 22dp), spelled out one
// by one rather than smoothed into a single rhythm. The rows measure the panel; every
// layer behind them matches its size, so the panel is exactly as tall as its content
// and needs no height of its own.
@Composable
private fun Header(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
    onSetMode: (ConnectMode) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val phase = state.phase
    val shape = remember { RoundedCornerShape(bottomStart = HeroCorner, bottomEnd = HeroCorner) }
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
    // White idle, blue working, green connected — the light's own colour, animated so
    // changing state reads as the room changing colour rather than as a repaint.
    val ambient = phaseLight(phase)
    val lit = phase != ConnPhase.OFF
    val frame by animateColorAsState(
        targetValue = when (phase) {
            ConnPhase.OFF -> HeroBorder
            ConnPhase.CONNECTING -> RefAccent.copy(alpha = 0.34f)
            ConnPhase.CONNECTED -> Color.White.copy(alpha = 0.11f)
        },
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "heroFrame",
    )
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                // The panel's lift. Drawn on the opaque surface itself, not on a
                // translucent layer, so the shadow stays under the panel instead of
                // hazing what it is drawn over — and with both shadow colours given
                // explicitly, because the platform defaults fog a dark page.
                .shadow(
                    elevation = HeroElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = HeroShadowAmbient,
                    spotColor = HeroShadowSpot,
                )
                .clip(shape)
                .background(ChromeBg)
        ) {
            Box(Modifier.matchParentSize().background(HeroOffSurface))
            if (flagAlpha > 0.01f) {
                HeaderFlag(
                    countryCode = lastFlagCountry,
                    modifier = Modifier.matchParentSize().alpha(flagAlpha),
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawHeroLighting(ambient, lit)
                        // Last, and over the light: the floor is the panel curving out
                        // of the light, so it has to darken what the light just added.
                        drawRect(HeroFloor)
                    }
            )
            Box(Modifier.matchParentSize().background(HeroSheen))
        }
        // Above every face, so the frame is a crisp edge rather than something the
        // flag can paint over. Purely drawn — no pointer input — so the rows keep
        // their own touches.
        Box(Modifier.matchParentSize().border(1.dp, frame, shape))
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = ScreenPad, end = ScreenPad, top = 4.dp, bottom = 20.dp)
        ) {
            TopBar(onOpenSettings = onOpenSettings, onOpenProfile = onOpenProfile)
            Spacer(Modifier.height(4.dp))          // .status-row margin-top
            StatusRow(state = state, onOpenSettings = onOpenSettings)
            Spacer(Modifier.height(12.dp))         // .power-row margin-top
            ConnectRow(
                state = state,
                onOpenLocations = onOpenLocations,
                onTogglePower = onTogglePower,
                onSetMode = onSetMode,
            )
            Spacer(Modifier.height(22.dp))         // .network-row margin-top
            NetworkRow(state = state)
        }
    }
}

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
// They stay [RefTextHi] in every phase, connected or not: navigation is not connection
// state, and a hamburger that turns green says something about the tunnel that isn't
// true.

/** The glyph inside its chip. Smaller than the 25dp bare mark it replaces — with
 *  material under it, it no longer has to carry itself on size alone. */
private val NavGlyph = 20.dp

/** The framed container under each top-bar glyph. */
private val NavChip = 38.dp

/** Its corner — a squircle-ish 13dp on 38dp, the same corner-to-size relationship the
 *  status chip and the tab pills use, so all the small frames on the panel agree. */
private val GlyphChipCorner = 13.dp

/** Its material: [RefElev1] over whatever the panel is showing, so it reads the same
 *  on the chrome and on a flag. */
private val GlyphChip = Brush.verticalGradient(
    0.00f to RefElev1.copy(alpha = 0.66f),
    1.00f to RefElev1.copy(alpha = 0.82f),
)

/** Its hairline — the same white edge every framed surface up here carries. */
private val GlyphChipBorder = Color.White.copy(alpha = 0.09f)

/** How far each chip is lifted off the panel. Small: it is a chip, not a card, and the
 *  same 12dp the status chip uses so the two sit on one plane. */
private val GlyphChipElevation = 12.dp

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onOpenProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
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
                tint = RefTextHi,
                modifier = Modifier.size(NavGlyph),
            )
        }
        Spacer(Modifier.weight(1f))
        GlyphButton(
            onClick = onOpenProfile,
            label = "Account",
            modifier = Modifier.offset(x = 5.dp),
        ) {
            AccountGlyph(color = RefTextHi, modifier = Modifier.size(NavGlyph))
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
    Box(
        modifier
            .size(TapTarget)
            .clip(shape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The chip is inside the tap target, not the target itself: the touch area
        // stays 48dp while what the eye sees is 38dp.
        Box(
            Modifier
                .size(NavChip)
                .shadow(GlyphChipElevation, shape, clip = false, ambientColor = HeroShadowAmbient, spotColor = HeroShadowSpot)
                .clip(shape)
                .background(GlyphChip)
                .border(1.dp, GlyphChipBorder, shape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

// ── Status row ────────────────────────────────────────────────────────────────
// The protocol line, and after it the connect mode: "VLESS · 8443 · Smart". The
// mockup's OFF/ON pill is gone, because the ambient light already says whether the
// tunnel is up and a second, wordier read of the same fact next to it only adds a
// thing that can lag behind it.
//
// The mode belongs here rather than on the connect bar for the same reason: this row
// is where the facts about the connection are stated in words, and the bar carries
// exactly one thing, the country. The mode's own word is [RefTextMid] — it is a
// setting, not a state, so it sits a step behind the protocol without needing a
// colour of its own to say so.
//
// This is the one row that sits on the flag with nothing under it, and it carries the
// screen's dimmest ink, so it gets a chip of its own — see [StatusChipMaterial].

/**
 * The material under the status row, and why a line of text needs any.
 *
 * The row lands at about a third of the way down the header, where the flag's mask is
 * full and [HeaderFlagScrim] is at its lightest on purpose. That is fine for the row's
 * headline and wrong for everything else on it: over a white flag band — Japan,
 * Poland, Finland, France's middle stripe — the artwork and its scrim leave a light
 * grey there, on which [RefTextHi] still clears but [RefTextMid] and [RefTextLow] fall
 * well under 3:1. The mode word is information, and at that contrast it is not there.
 *
 * A chip is the fix rather than a heavier scrim because the row is already a button —
 * it opens protocol settings — and it had no mark saying so. At [RefElev1] and this
 * alpha the worst band the app can draw sits at ~#2c2d31 under the text, which puts
 * the mode word back at 5.2:1 and the headline at 12.7:1, against 7.4:1 and 18:1 when
 * this header was near-black. It is the same glass the connect bar is made of, in the
 * same pill language, so the row reads as the tappable thing it is instead of as text
 * laid on a photograph.
 */
private val StatusChipMaterial = Brush.verticalGradient(
    0.00f to RefElev1.copy(alpha = 0.62f),
    1.00f to RefElev1.copy(alpha = 0.74f),
)

@Composable
private fun StatusRow(state: HomeUiState, onOpenSettings: () -> Unit) {
    val reduce = rememberReduceMotion()
    Row(
        Modifier
            // 8dp of inset so the chip doesn't sit tight against the glyphs, pulled
            // back out by 4dp so the protocol still starts exactly where it did before
            // the chip existed. The chip's own edge is what lands in the margin, which
            // is the trick the top bar's 48dp tap boxes already use.
            .offset(x = (-4).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StatusChipMaterial)
            .clickable(onClickLabel = "Protocol settings", onClick = onOpenSettings)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            protocolLabel(state.activeConfig),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = headerInk(state.phase),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            " · ",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = RefTextLow,
            maxLines = 1,
        )
        // The word changes rarely, so it is worth animating: a 160ms fade in over a
        // 110ms fade out (enter longer than exit, per the app's own motion rules),
        // with the container sizing between "Smart" and "Manual" rather than jumping.
        // Like every other movement on this screen it collapses to a cut when the
        // system asks for no animations.
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 160)) togetherWith fadeOut(motionSpec(reduce, 110))
                    ).using(SizeTransform(clip = false))
            },
            label = "connectMode",
        ) { mode ->
            Text(
                mode.label,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = RefTextMid,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))               // .protocol-line gap
        // [RefTextMid], not [RefTextLow] like the separator beside it: this chevron is
        // the only mark that says the row opens something, and a control has to clear
        // 3:1 against its background. Over the brightest flag band the app can draw,
        // [RefTextLow] on the chip reads 2.6:1 and this reads 5.2:1.
        Chevron(size = 14.dp, color = RefTextMid)
    }
}

// ── Connect bar + power circle ────────────────────────────────────────────────
// .power-row: the bar stops 50dp short of the right edge and the 100dp circle is
// pulled back over it (CSS margin-left: -50px), so the circle's centre lands
// exactly on the bar's right edge and its outer half overhangs the row.
//
// The row is [PowerSize] tall — the mockup's own geometry, back to it. It carried two
// extra bands for a while, above and below, for the mode chevrons that used to sit
// there; with those gone the row is the circle's height again and the bar is centred
// in it, which is also what puts the flag's own centre line behind the control.
@Composable
private fun ConnectRow(
    state: HomeUiState,
    onOpenLocations: () -> Unit,
    onTogglePower: () -> Unit,
    onSetMode: (ConnectMode) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(PowerSize)) {
        ConnectBar(
            state = state,
            onClick = onOpenLocations,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = PowerSize / 2)
                .fillMaxWidth()
                .height(BarHeight),
        )
        PowerCircle(
            mode = state.mode,
            phase = state.phase,
            enabled = state.activeConfig != null,
            onClick = onTogglePower,
            onSwipeUp = { onSetMode(ConnectMode.SMART) },
            onSwipeDown = { onSetMode(ConnectMode.MANUAL) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * The connect bar: a glass pill naming the active server's country, with the power
 * circle cut out of its right edge.
 *
 * It carries the country's flag as a circular badge at its head — the same
 * [CountryFlagBadge] the server list draws, from the same bundled `flags/<cc>.svg`, so
 * the one flag the header washes across the top reads the same way here and in the list
 * below. The header wash still shows through the rest of the pill behind the text.
 *
 * There is no cast shadow: a [Modifier.shadow] under a translucent surface is drawn
 * beneath the surface as well as around it, which would put a grey haze over exactly the
 * part of the artwork this pill is built to let through. The hairline, the top light and
 * the material ramp are what lift it instead.
 */
@Composable
private fun ConnectBar(
    state: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reduce = rememberReduceMotion()
    val shape = remember(density) { connectBarShape(with(density) { PowerCut.toPx() }) }
    // How much of the pill's own material is drawn. Connected there is a flag behind it
    // and the ramp exists to let the artwork through the far end; off and connecting
    // there is nothing back there but the panel, and a pill that fades to transparent
    // over its own panel is not a pill — it is a smudge that stops halfway. So the
    // material's alpha is raised toward solid in those two states, and the ramp only
    // reappears with the flag it was written for.
    val opaque by animateFloatAsState(
        targetValue = if (state.phase == ConnPhase.CONNECTED) 0f else 1f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "barOpacity",
    )
    val cfg = state.activeConfig
    // The country, and only the country. The city used to lead this line and the
    // country plus the config's own name sat under it; the flag behind the text
    // already says which country this is, the city says nothing the user chose, and
    // the server's name is what the browse list below is for. What is left when all
    // three of those are gone is the one fact the bar is for: where the tunnel comes
    // out. The config's name is the fallback only when the country is unknown —
    // an empty bar would be worse than a technical one.
    val country = countryCodeToName(state.headerCountryCode)
    val headline = country.ifBlank {
        cfg?.let { c -> c.displayName.ifBlank { c.address } } ?: "No server"
    }

    Box(
        modifier
            .clip(shape)
            .background(BarSurface)
            .border(1.dp, BarBorder, shape)
            .clickable(onClickLabel = "Choose a server", onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The headline's own material: near-solid where the text starts, gone by 58%,
        // so the country reads on a surface and the flag reads through the rest of the
        // pill. See [BarMaterial] — this is the ramp the bar had before the flag was
        // moved inside it. [BarSolid] over it is what closes the ramp up when there is
        // no flag behind the pill to let through.
        Box(Modifier.matchParentSize().background(BarMaterial))
        if (opaque > 0.01f) {
            Box(Modifier.matchParentSize().alpha(opaque).background(BarSolid))
        }
        Box(Modifier.matchParentSize().background(BarSheen))
        // The bar's share of the ambient light: top first, then the two edges. Both
        // stay white in every state — the connected signal is the green light behind
        // the bar, not a colour cast over the flag.
        Box(Modifier.matchParentSize().background(BarTopLight))
        Box(Modifier.matchParentSize().background(BarEdgeLight))
        // `.location-block` (z-index:2) — the flag, then the country. The flag is the
        // same circular [CountryFlagBadge] the server list draws, from the same bundled
        // `flags/<cc>.svg`, so one flag reads the same across the whole screen. The end
        // inset clears the circular cut by 10dp so the last glyph never crosses the
        // missing material.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // .connect-bar padding is 0 20px
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPad, end = PowerCut + 10.dp),
        ) {
            CountryFlagBadge(state.headerCountryCode, FlagSize)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    headline,
                    fontSize = 21.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = headerInk(state.phase),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The phase, in words, under the country.
                //
                // Every other signal for it up here is light or colour — the panel's
                // face, the ring, the ink — and a state that is only ever stated as
                // colour is a state a colour-blind user has to infer from a spinner.
                // It is [RefTextMid], a step behind the country, because the country is
                // still what this bar is for; and it crossfades rather than cutting,
                // because it changes at exactly the moments the rest of the header is
                // already crossfading.
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = state.phase,
                    transitionSpec = {
                        (
                            fadeIn(motionSpec(reduce, 200)) togetherWith
                                fadeOut(motionSpec(reduce, 130))
                            ).using(SizeTransform(clip = false))
                    },
                    label = "connectPhase",
                ) { phase ->
                    Text(
                        when (phase) {
                            ConnPhase.OFF -> "Not connected"
                            ConnPhase.CONNECTING -> "Connecting…"
                            ConnPhase.CONNECTED -> "Protected"
                        },
                        fontSize = 12.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = RefTextMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
// The connect control: a disc with a ring around it, and between them the one thing on
// this screen that reports progress rather than a result.
//
// The disc is [PowerDiscSize] inside the row's [PowerSize] box, which leaves an 8dp
// band around it for the ring. That band is where the mockup had nothing at all, and
// the [PowerCut] clearance in the connect bar is unchanged, so the control still reads
// as fused to the bar's edge — the ring sits inside the space that was already there
// rather than pushing the geometry around.
//
// Three faces, one per [ConnPhase], crossfaded on [PHASE_FADE_MS]:
//
//   OFF        — the brushed-white disc, [PowerInk] mark, and a bare hairline track.
//                A white disc on dark chrome is the highest-contrast thing the screen
//                can draw, which is what an idle app's one button should be.
//   CONNECTING — the same disc under a faint accent tint, an [RefAccent] mark, and a
//                240° arc turning once a second around it. The arc is the only
//                indeterminate progress in the app and it is deliberately outside the
//                disc: the face keeps its shape, so the button still looks pressable
//                while it works.
//   CONNECTED  — the *same white disc*, with a green mark on it, and the ring around it
//                lit [RefGreen] over a soft bloom. The face never fills with colour in
//                any state: the button is the one control on the screen, so it should
//                look like the same control before and after it is pressed, and the ring
//                is what reports the result. A green disc also read as a filled
//                *primary action* — "press me" — in exactly the state where pressing it
//                disconnects, which is the opposite of what it should invite.

//
// It carries one gesture besides the tap: a vertical drag switches Smart / Manual.
// Nothing draws it — the mode is stated in words in the status row and offered to a
// screen reader as two named actions on this button.
//
// The mockup's four-part box-shadow, split by what Compose can draw:
//   0 16px 34px rgba(0,0,0,0.45)      ┐ the cast shadow — Modifier.shadow
//   0 4px 10px rgba(0,0,0,0.25)       ┘
//   inset 0 3px 4px rgba(255,255,255,0.95)  ┐ Compose has no inset box-shadow, so
//   inset 0 -10px 14px rgba(0,0,0,0.14)     ┘ these two are [PowerFaceSheen], a
//                                             bright top rim over a soft dark foot.

/** The disc itself, inside [PowerSize]'s box — the rest of the box is the ring band. */
private val PowerDiscSize = 84.dp

/** The ring's own weight, and how far outside the disc it is drawn. */
private val PowerRingStroke = 2.5.dp
private val PowerRingGap = 3.dp

/** How long one turn of the connecting arc takes. */
private const val POWER_ARC_SPIN_MS = 1000

/** How much of the ring the connecting arc covers. */
private const val POWER_ARC_SWEEP_DEG = 240f

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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,               // .power-btn:active
        label = "powerPress",
    )
    // The one coloured face left, over the white disc that is always there, so no value
    // of it can leave the button transparent mid-crossfade. There is deliberately no
    // second one for CONNECTED — see the section comment: the connected state is
    // reported by the ring, and the face stays white.
    val workingFace by animateFloatAsState(
        targetValue = if (phase == ConnPhase.CONNECTING) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "powerWorkingFace",
    )
    // The mark: dark on the idle disc, accent while working, green once up — on the same
    // crossfade as the rest of the header's ink. Connected is [RefGreenInk] rather than
    // [RefGreen]: the face is white now, and the ring's own green is tuned to glow on
    // near-black, which on white is a thin, washed-out mark. The darker green reads at
    // the same 50dp as the other two marks do.
    val mark by animateColorAsState(
        targetValue = when (phase) {
            ConnPhase.OFF -> PowerInk
            ConnPhase.CONNECTING -> RefAccent
            ConnPhase.CONNECTED -> RefGreenInk
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
                    // single most visible place on the screen for it.
                    elevation = 22.dp,
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
                    onClick = onClick,
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
            Box(Modifier.matchParentSize().background(PowerFaceSheen))
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = label,
                tint = ink,
                // The mockup's stroke svg is 54px on a 100px button; this is the
                // filled Material mark at the same proportion of the smaller disc, so
                // it still reads as the button's own symbol rather than as a glyph
                // parked in the middle of it.
                modifier = Modifier.size(50.dp),
            )
        }
    }
}

/**
 * The ring in the band around the disc: a hairline track in every state, plus one arc
 * that says what the connection is doing.
 *
 * Connecting, the arc turns — [POWER_ARC_SWEEP_DEG] of [RefAccent] once every
 * [POWER_ARC_SPIN_MS], which is the app's only indeterminate progress and the only
 * animation that runs without being asked. Connected, it is the full circle in
 * [RefGreen] over a wider, fainter stroke of the same colour, so the ring reads as lit
 * rather than as drawn. Both fade with [PHASE_FADE_MS], and with the system's
 * animations off the arc parks at the top instead of spinning — the colour still says
 * which state this is.
 */
@Composable
private fun PowerRing(phase: ConnPhase, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "powerArc")
    val spin by transition.animateFloat(
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
    val rotation = if (reduce) 0f else spin
    Canvas(modifier) {
        val stroke = PowerRingStroke.toPx()
        val radius = (PowerDiscSize.toPx() / 2f) + PowerRingGap.toPx() + (stroke / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)

        // The track. Always there, so the band never looks empty and the arc has
        // something to travel along.
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = radius,
            style = Stroke(width = stroke),
        )
        if (live > 0.01f) {
            // Lit: a soft wide bloom under the ring itself.
            drawCircle(
                color = RefGreen.copy(alpha = 0.16f * live),
                radius = radius,
                style = Stroke(width = stroke * 3.2f),
            )
            drawCircle(
                color = RefGreen.copy(alpha = 0.92f * live),
                radius = radius,
                style = Stroke(width = stroke),
            )
        }
        if (working > 0.01f) {
            // -90° puts the arc's head at the top of the circle at rotation 0.
            drawArc(
                color = RefAccent.copy(alpha = 0.95f * working),
                startAngle = rotation - 90f,
                sweepAngle = POWER_ARC_SWEEP_DEG,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
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

/** The connecting tint: the accent laid over the white face, light enough that the
 *  disc still reads as the same brushed surface holding a colour. */
private val PowerWorkingFace = Brush.linearGradient(
    0.00f to RefAccent.copy(alpha = 0.10f),
    1.00f to RefAccent.copy(alpha = 0.26f),
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

// ── Network row ───────────────────────────────────────────────────────────────
// .network-row: the panel's bottom row — the public IP hard right, and on the left the
// slot that used to name the transport ("Wi-Fi" / "Mobile data").
//
// That label is gone. It was the one thing on the panel that said something about the
// phone rather than about the tunnel, it changed under the user for reasons that had
// nothing to do with the app, and the address beside it already places the connection
// better than the words "Mobile data" ever did. [HomeUiState.networkName] is still
// carried — the network callback that feeds it is what drives reconnects — it is simply
// not drawn here any more.
//
// The slot it left is where the connecting animation lives now (see [ConnectingPulse]),
// which is the whole reason the row was kept rather than deleted: it is a full-width
// slot at the foot of the panel, directly under the button that was just pressed, and
// it is empty in the two states that have nothing to report.
//
// What the address is depends on the tunnel, and [HomeUiState.displayIp] is where that
// is decided: this device's own public IP while the tunnel is down, the exit node's
// once it is up. When there is nothing to state — the very first lookup, or the couple
// of seconds a fresh tunnel is given to settle before it is asked — the row states
// nothing. It used to show a lone "—" there, a character the user could neither read
// nor copy, sitting where a value belongs; the slot is simply empty now, and the
// address fades in when it arrives.
@Composable
private fun NetworkRow(state: HomeUiState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reduce = rememberReduceMotion()
    val ip = state.displayIp
    // The row keeps a floor height whether or not the pulse is in it, so the panel does
    // not grow by a few dp on connect and shrink again on cancel — a panel that changes
    // height while the user waits on it is the one motion on this screen nobody asked
    // for. 28dp is the height of the IP slot's own text plus its padding, which is what
    // the row measured before either child was optional.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectingPulse(
            active = state.phase == ConnPhase.CONNECTING,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        // Both changes this slot ever makes are worth a crossfade rather than a jump:
        // the first address landing, and the swap from this device's to the exit node's
        // when the tunnel comes up. The width animates with it, so an address arriving
        // in an empty slot grows into it instead of appearing at full size.
        //
        // It is weighted, `fill = false`, rather than left to its intrinsic width: a
        // non-weighted child is measured first and takes whatever it asks for, so the
        // 39-character IPv6 the old lookup returned claimed the whole row and squeezed
        // the transport label beside it down to "M…". Weighted, neither side can take
        // more than half, and with `fill = false` the address still only occupies what
        // it needs — so a 15-character IPv4 sits hard right exactly as before.
        AnimatedContent(
            targetState = ip,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 260)) togetherWith fadeOut(motionSpec(reduce, 140))
                    ).using(SizeTransform(clip = false))
            },
            label = "publicIp",
            modifier = Modifier.weight(1f, fill = false),
        ) { value ->
            if (value.isBlank()) {
                Spacer(Modifier.width(0.dp))
            } else {
                Text(
                    value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headerInk(state.phase),
                    maxLines = 1,
                    // maxLines alone truncates by clipping, which is what left a
                    // half-drawn glyph at the end of a long address; with softWrap off
                    // and ellipsis on, anything that still doesn't fit ends in "…"
                    // instead of mid-stroke. An IPv4 address is 15 characters at most
                    // and fits with room to spare — see [GeoService.lookupCurrentIp],
                    // which is now v4-only — so in practice neither applies, but the
                    // row can no longer render a cut-off value whatever it is handed.
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFeatureSettings = "tnum"),  // tabular-nums
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Copy IP address") {
                            clipboard.setText(AnnotatedString(value))
                            android.widget.Toast
                                .makeText(context, "IP copied", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ── Connecting pulse ──────────────────────────────────────────────────────────
// The one thing on this screen that says work is happening rather than finished, and the
// only animation that runs without being asked.
//
// It lives in the left half of [NetworkRow], at the foot of the hero panel, directly
// under the button that started the attempt — not across the panel's background, where a
// sweeping band made the whole top of the screen restless in exactly the state where the
// user is already waiting on it.
//
// What it draws is a track with a comet on it: a 2dp hairline rail the width of the slot,
// and a bright head with a tail trailing behind it, travelling left to right once every
// [PULSE_SWEEP_MS], leaving the rail dark again behind it. Beside the rail the word
// "Connecting" breathes between two opacities on the same clock, so the state is stated
// in words as well as in motion.
//
// The head is [RefTeal] over an [RefAccent] tail rather than one flat colour: two
// gradient stops of different hues is what makes it read as something moving along the
// rail and lighting it, rather than as a stripe being redrawn in a new place. Like every
// other light on this screen it is a plain gradient — no blur, no render effect.
//
// The whole thing fades in and out on [PHASE_FADE_MS] with the rest of the phase change,
// and with the system's animations off the comet parks at the middle of the rail: the
// rail, the colour and the word all still say which state this is, nothing moves.

/** How long the comet takes to cross the rail once. */
private const val PULSE_SWEEP_MS = 1250

/** The rail's own weight. A hairline: it is a track, not a progress bar, and this
 *  attempt has no measurable progress to report. */
private val PulseRail = 2.dp

/** How much of the rail the comet's tail covers. Long enough that the lit part of the
 *  rail is most of it at mid-travel — so the slot reads as active rather than as a dot
 *  crossing an empty line — and short enough that the head is still a head. */
private const val PULSE_COMET_FRACTION = 0.42f

@Composable
private fun ConnectingPulse(active: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val strength by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "pulseStrength",
    )
    // Nothing at all in the two states that have nothing to report — and, critically, no
    // infinite transition running in them either: the animation is created inside this
    // branch, so an idle or connected screen has no frame callback of its own from here.
    if (strength <= 0.01f) {
        Box(modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "connectingPulse")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_SWEEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseTravel",
    )
    // The word's own breath, deliberately slower than the comet and on its own easing,
    // so the two do not lock into one beat.
    val breath by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_SWEEP_MS * 2, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseBreath",
    )
    val progress = if (reduce) 0.5f else travel
    val wordAlpha = if (reduce) 1f else breath
    Row(modifier.alpha(strength), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Connecting",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = RefTeal.copy(alpha = 0.92f * wordAlpha),
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(PulseRail)
        ) {
            val radius = size.height / 2f
            // The track. Always the full width, so the comet has something to travel
            // along and the slot has a shape even at the ends of the sweep.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                cornerRadius = CornerRadius(radius, radius),
            )
            // The comet. It starts and ends fully off the rail, so there is no visible
            // re-entry at the wrap: at progress 0 the tail's start is at the left edge
            // with the head still off it, at 1 the head has left the right edge.
            //
            // `tail`/`head` are the gradient's own ends, and the clip is theirs too. It
            // has to be: a horizontal gradient is [TileMode.Clamp], so every pixel past
            // `endX` takes the last stop's colour — filling the rail ahead of the comet
            // with solid [RefTeal] rather than leaving it dark. Clipping to the band is
            // what makes the comet a comet instead of a bar.
            val comet = size.width * PULSE_COMET_FRACTION
            val tail = progress * (size.width + comet) - comet
            val head = tail + comet
            clipRect(left = tail, right = head) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.55f to RefAccent.copy(alpha = 0.55f),
                        0.88f to RefTeal.copy(alpha = 0.95f),
                        1.00f to RefTeal,
                        startX = tail,
                        endX = head,
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }
}

// ── Browse card ───────────────────────────────────────────────────────────────
// .browse-card: the list's own panel, 28dp top corners and a light hairline along
// that top edge, sitting 10dp below the header.
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
    onSelectTab: (HomeTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectConfig: (SavedConfig) -> Unit,
    onOpenLocations: () -> Unit,
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
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // The connection is on the Box, not the LazyColumn: the whole list area
                // is the drag surface, so a pull that starts on the empty hint or in the
                // gap beside a row works exactly like one that starts on a row.
                .nestedScroll(pullState.nestedScrollConnection)
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
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

/**
 * A bare "+" glyph — no disc, no fill — for adding a server.
 *
 * Ink is [RefTextMid], the same as the idle magnifier beside it and every other
 * plain glyph on the screen: it was accent-blue, which read as the one coloured
 * control in a row of white ones and pulled the eye off the connect bar.
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

/**
 * .search-btn, minus its disc: a bare magnifier that goes accent-blue while the
 * field is open. Nothing is drawn behind either of these two — the tap target is a
 * circle only so the press ripple is one, the same as every other plain icon button
 * in the app.
 */
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
// .server-row: a [RowFlagSize] circular flag, name over ping, three load bars, and a
// hairline that starts past the flag ([DividerStart]) on every row but the last.
//
// The row is deliberately compact — about 58dp against the 72dp it used to be — because
// the list is the part of this screen the user scrolls, and one more server visible
// without scrolling is worth more than the whitespace. Nothing was dropped to get there:
// every field the row carried it still carries, at a size it can still be read at. What
// changed is the flag (36 → 30dp, which is what set the floor), the vertical padding
// (12 → 9dp), the gap after the flag (14 → 12dp) and a half-point off each of the two
// text sizes. The 48dp touch floor is still cleared by the row's own height.
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
            .padding(horizontal = ListPad, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagBadge(countryCode, RowFlagSize)
        Spacer(Modifier.width(12.dp))              // .server-row gap
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 15.sp,
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
            Text(
                subtitle,
                fontSize = 12.sp,
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {},
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {},
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {},
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
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {},
        onRefreshPings = {},
    )
}

@Preview(name = "Home · no servers", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreen(
        state = HomeUiState(activeConfig = null, allConfigs = emptyList()),
        onOpenSettings = {}, onOpenProfile = {}, onOpenLocations = {},
        onTogglePower = {}, onSelectConfig = {}, onAddServer = {}, onSetMode = {},
        onRefreshPings = {},
    )
}
