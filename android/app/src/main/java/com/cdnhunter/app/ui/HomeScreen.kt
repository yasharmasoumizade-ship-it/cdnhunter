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
//   • hero        — one centred column, and the whole point of the screen:
//                     eyebrow    — a coloured dot and the phase in tracked caps
//                     headline   — the exit country at 34sp, the largest ink in the app
//                     status     — "REALITY · 8443 · Smart" on a glass chip → Settings
//                     power      — a 140dp white disc with a phase ring, centred and
//                                  alone. Swipe up for Smart mode, down for Manual
//                     meta       — the public IP (tap to copy) and, connected, the
//                                  session clock; connecting, the progress pulse
//                     server     — the active config on a glass row → Locations
//   • browse card — 28dp-topped panel: Main / Custom pills, + and search buttons,
//                   then one row per server
//                   (flag, country · city, ping, three load bars)
//   • usage card  — floats over the list bottom: session-traffic ring, live
//                   speed, chevron → Locations
//
// The hero states one fact per line, in the order a user asks for them: am I protected,
// where am I, over what, [the button], as what address, from which server. Nothing is
// stated twice — the country is the headline and does not repeat in the server row, the
// city is a caption under the country and nowhere else, and the config's own name appears
// only in the row that opens the list it comes from.
//
// It is centred on the screen's own axis, which is what the redesign turned on: the hero
// was a left-aligned stack of rows ending in a connect pill with the power button fused to
// its right edge, and the button — the one thing the screen exists to offer — was neither
// the largest element nor on any axis of its own. Centred, the column has a single spine,
// the button is unmistakably the primary action, and the flag behind it reads as light
// around a control rather than as the backdrop of a list.
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
// plain page everywhere the UI needs to be read. It is drawn Crop into the panel itself:
// scaled uniformly until the panel is covered and clipped, so every source — a square
// bundled asset, a 5:3 German flagcdn SVG, a 19:10 American one — keeps its own
// proportions and no flag is ever squeezed on one axis. Crop rather than FillBounds into
// a fixed-ratio box, which is what this was: stretching each source to one shared 4:3
// rectangle made the flag's shape consistent country to country at the cost of visibly
// warping most of them, and at header size the warp is the first thing the eye finds.
// What the crop takes off the top and the foot is already under the scrim and the mask.
// It is flattened and de-bowed first, and rasterised well above the header's own pixel
// width, so the bands read level and crisp — see FlagArtwork.kt.
//
// The fade is an alpha mask, not a coat of paint. Two gradients multiply into the
// artwork's own alpha ([HeaderFlagFadeX], [HeaderFlagFadeY]): the horizontal one holds
// the flag at full strength from the left edge and eases it only slightly by the right,
// the vertical one holds it full down the screen and never takes it below 0.88. Neither
// reaches zero any more — the flag is the whole background now, edge to edge, and a mask
// that fell away would be the flag "not covering the screen", which is exactly what it is
// there to prevent. Masking rather than scrimming is still what keeps this ambient: where
// the mask eases, the page's own gradient is what shows through, so the artwork has no
// edges of its own — no darker rectangle at the top, no seam where it meets the browse
// card.
//
// Two things sit between the artwork and that mask: a slight desaturation
// ([HEADER_FLAG_SATURATION] — a printer's colours brought onto a screen), and a soft
// vertical scrim ([HeaderFlagScrim]) drawn inside the masked layer, so it darkens the
// flag and tapers away with it instead of tinting the page. There is no vignette any
// more: darkening the corners was the horizontal mask's job the moment the flag stopped
// reaching them.
//
// The balance between those two moved deliberately. The artwork is now near-opaque —
// [HEADER_FLAG_ALPHA] 0.92 for the sharp plate, [FLAG_WASH_ALPHA] 0.62 for the full-bleed
// layer under it — and the scrim is what buys legibility back, shaped so it is heavy only
// where text actually lands: the status bar at the top, the band the browse card's first
// rows sit over at the foot, and light through the middle where the flag is just flag.
// Dimming the whole image to protect two bands is what made it read as grey. The worst
// case is still the brightest band the app can draw — a white flag, at the left, level
// with the top bar — and there the 0.58 scrim stop puts it around #6b6b6c, which
// [RefTextHi] and the white power disc clear comfortably. The screen's dimmer inks do not
// clear it, which is what the glass under them is for.
//
// Choosing another server crossfades the flag instead of cutting to it: a 420ms fade
// in over a 260ms fade out, the incoming flag settling from 1.04 and the outgoing one
// easing back to 0.99, so the change reads as one image replacing another.
//
// Everything on the hero that carries text carries it on glass — one material, three
// sizes: the mode pill, the IP chip, the server row. See the [GlassChip]
// section. The reason is the flag: the hero's dim inks ([RefTextMid], [RefTextLow]) land
// on artwork whose brightest band the app can draw is a white flag, where they read at
// 3:1 and below with nothing under them. Glass is the fix rather than a heavier scrim
// because these are all controls — every one of them opens something or copies something
// — and a control needs an edge and a hit target anyway. The two things that carry no
// glass are the two that need none: the headline, which is [RefTextHi] at 34sp
// ExtraBold, and the power disc, which is white.
//
// When there is no flag to draw — country unresolved, no bundled asset, still decoding —
// the header carries [HeaderFlagFallback], a neutral slate wash, so the top is never a
// void.
//
// The only light on this screen is ambient, and there is deliberately very little of
// it: three soft directional sources — top, left and right — falling onto the connect
// control at a few percent. They are static gradient brushes, never [Modifier.blur],
// so the light stays crisp instead of smudged. Idle they are white; connected they
// turn green, tighter and a shade stronger. Over a flag that light has to be a lit room
// rather than a lamp pointed at the phone — hence the single-digit percentages.
//
// The power circle is a flat brushed-white disc in every state — the mockup's
// `.power-glow` is `display:none` — and nothing is attached to it: the two mode
// chevrons that used to sit above and below it are gone, and so is the connect pill its
// right half used to be cut into. Smart / Manual is still switched by swiping the circle
// up or down, or by its two accessibility actions, and the mode is now named and changed
// in [ModePill], docked directly under the disc — the status chip that used to state it
// alongside the transport and the port is gone. The one
// thing on it that answers to the tunnel is its mark, which goes green while the tunnel is
// up (see [headerInk]), and the ring around it, which carries the phase.
//
// Connected is teal (--teal, #35d6b8) where the mockup says teal — the usage ring's
// accent and the active row's dot. Everything else connected is the one green (--green,
// #34d17a): the ambient light, the top edge light, the header's own headline ink and the
// power mark. There is no ON/OFF pill and no pending state: the screen is either
// connected or it isn't.
//
// Green is the header's headline ink and nothing below it. The country headline, the
// eyebrow's dot and the session clock cross to [RefGreen] together, on the same 520ms
// fade as the light, so the top of the screen changes state as one thing. What stays put
// is deliberate: the top bar's two glyphs — hamburger and account — are navigation, not
// connection state, and they are the same white whether the tunnel is up or down; so are
// the screen's secondary inks up here (the mode word, the separators, the chevrons), which
// are settings and punctuation rather than state, and which at 13.5sp would be the one
// place a mid-chroma green could fall under contrast.
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

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
/**
 * The connected green — deliberately deeper than the mockup's `--green` (#34D17A).
 *
 * That value was a bright spring green: at 118dp of lit ring plus a crown wash plus an
 * eyebrow dot, it was the loudest thing on a near-black screen and read as a highlighter
 * rather than as a state. This is the same hue held at roughly two thirds of the
 * lightness, which still clears 4.5:1 on [RefBg] for the ring and the caption but sits
 * back into the page instead of shouting off it.
 */
private val RefGreen = Color(0xFF1E9E5A)
/**
 * The room's light when the tunnel is up: blue, not the button's own green.
 *
 * The disc reports the *state* in green — mark, ring, eyebrow — and the light reports that
 * the thing is lit, so making them different hues is what stops the whole top of the
 * screen becoming one green blob. Vivid and slightly over-bright on purpose: this is the
 * one light in the app allowed to be theatrical. It is thrown by [drawHeroAtmosphere]
 * across the whole backdrop; there is no longer a halo around the button itself.
 */
private val RefGlowOn = Color(0xFF2F6BFF)
/** The connecting colour: yellow-orange, i.e. "working", and the same hue the connecting
 *  arc, disc tint and phase dot take so the state is one colour. */
private val RefWorking = Color(0xFFFFA318)

/**
 * The same green, dark enough to read *on* white — used for the power button's mark.
 *
 * [RefGreen] is tuned to glow on near-black; on the button's white face it is a pale,
 * thin mark that fails contrast. This is the same hue at roughly a third of the
 * lightness, which clears 4.5:1 on the disc's lightest stop.
 */
private val RefGreenInk = Color(0xFF0E8A48)
/** [RefWorking] dark enough to read on the button's white face, for the same reason
 *  [RefGreenInk] exists: a mid-chroma amber mark on near-white is invisible. */
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
//                at full strength. Blue rather than green because the ring, the mark and
//                the eyebrow already carry green: state is green, light is blue.
//
// The old green *hairline* along the very top edge is gone. It was one 1.5dp line of
// near-full-strength green across the whole screen with two more running down the sides,
// and against the flag it read as exactly what it was — a drawn seam — rather than as
// light. What replaces it is [drawHeroAtmosphere]'s crown: the same signal as a soft wash
// bleeding in over the top edge, with no edge of its own anywhere in it.


/** The app's chrome colour: `android:navigationBarColor`, and the window behind Compose. */
private val ChromeBg = Color(0xFF0B0B0D)

/**
 * How far the hero's artwork and light carry on *below* the last of its rows — i.e. how
 * much of the backdrop the browse card is drawn over.
 *
 * A little more than [PanelFade], so the flag and the horizon bloom are still going where
 * the card has already turned solid: the dissolve ends inside the artwork rather than at
 * the end of it, which is what leaves no line anywhere in the transition.
 */
private val HeroBleed = 138.dp

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
private val ListPad = 18.dp          // .server-row / .tab-row horizontal padding
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
// The hero is lit rather than tinted, and the light is the loudest thing about it. Five
// layers, all of them plain gradients, in this order:
//
//   1. the crown    — light bleeding in over the very top edge of the screen, across the
//                     whole width, strongest in the first few percent and gone by
//                     mid-panel. This is what says "connected" at a glance, and it is the
//                     replacement for the old 1.5dp green hairline that used to be drawn
//                     there: same signal, no seam.
//   2. a key light  — one broad cone from above and right of the connect control, which is
//                     what makes the flag read as a lit surface rather than as a picture.
//   3. two rim fills— off each side edge, level with the connect bar, so the artwork lifts
//                     off the page at the screen's own edges instead of ending flat.
//   4. the horizon  — a wide bloom centred on the hero's foot, i.e. exactly where the
//                     browse card's translucent top edge crosses it. This is the layer
//                     that fuses the two: the card's first 132dp are lit from behind by
//                     the flag's own light, so the "seam" between them is the brightest
//                     part of the transition rather than a line in it.
//   5. a vignette   — black, radial, centred high, which pulls the corners down and holds
//                     the eye on the control. It is also what keeps the status bar's own
//                     glyphs legible now that the flag runs behind them.
//
// Every one of them is a [Brush] gradient. There is no [Modifier.blur] and no render
// effect anywhere on this screen: a blur at these radii costs a full offscreen pass per
// frame and, at these alphas, reads as a smudge rather than as light.
//
// Every ramp here is written with six or seven stops rather than three, and that is what
// makes the difference between "ambient light" and "low-quality gradient". A radial gradient
// interpolates linearly between its stops, so three stops over a 900px radius is three long
// straight ramps meeting at two kinks — and on a near-black page, at these alphas, the eye
// finds both the kinks and the 8-bit steps between them as concentric bands. Extra stops on
// an eased curve cost nothing at draw time (the shader interpolates either way) and give the
// falloff no straight section long enough to band.
//
// The strengths are deliberately about twice what this screen used to carry — this is the
// "bolder, more atmospheric" pass — but they are still *layered* rather than summed into
// one bright wash, and the only heavy layer is black. Idle the light is white and low;
// connecting it is [RefWorking]; connected [RefGlowOn], a vivid blue, stronger and with a
// tighter falloff, so a state change reads as the room changing colour rather than as a
// repaint. The lit values went up again when the power button's own halo was removed: the
// room is now the only thing carrying the state, so it has to carry it on its own.
private const val KEY_LIGHT_IDLE = 0.055f
private const val KEY_LIGHT_ON = 0.135f
private const val RIM_LIGHT_IDLE = 0.032f
private const val RIM_LIGHT_ON = 0.080f
private const val CROWN_LIGHT_IDLE = 0.058f
private const val CROWN_LIGHT_ON = 0.185f
private const val HORIZON_LIGHT_IDLE = 0.072f
private const val HORIZON_LIGHT_ON = 0.205f

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
    //    132dp are lit from behind by.
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
 * The header's headline ink: [idle] while the tunnel is down, [RefWorking] while an
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
        ConnPhase.CONNECTING -> RefWorking
        ConnPhase.CONNECTED -> RefGreen
    }
    val ink by animateColorAsState(target, motionSpec(reduce, PHASE_FADE_MS), label = "headerInk")
    return ink
}

/**
 * The colour of the light in the room for [phase]: white idle, amber working, blue up.
 *
 * Connected is [RefGlowOn] rather than [RefGreen], and that is the one place the room's
 * colour and the *state's* colour deliberately disagree. Green is the state — it is the
 * ring, the eyebrow dot, the headline and the mark on the disc — but a green room over a
 * flag turned every country into a swamp, and green on near-black is also the hardest hue
 * on this palette to keep clean at low alpha. Blue reads as light rather than as a tint, so
 * the artwork keeps its own colour and the green is left to say what the tunnel is doing.
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
 * The wash is on in every state (see [HomeUiState.heroFlagCountry]) and it is now the
 * screen's actual background rather than a panel's fill — it runs behind the status bar
 * at the top and behind the browse card's first 132dp at the foot, with no frame at any
 * edge. A background can afford to be a little more present than a floating image could:
 * at 0.70, with no card outline left to say "this is the hero", the artwork read as a
 * grey suggestion of a flag.
 *
 * 0.92 now, up from 0.78, because "recognisable as a flag" was still losing to "faded
 * background" — at 0.78 under [HeaderFlagScrim] the artwork was a tinted hint of itself,
 * and the brief for this screen is a flag that is unmistakably a flag and *also* works as
 * a backdrop. The legibility of the rows on top of it is not paid for by dimming the
 * artwork any more; it is paid for by the scrim, which is where it belongs, because the
 * scrim can be shaped — heavy exactly where text lands, light where the flag is just
 * flag. See [HeaderFlagScrim].
 */
private const val HEADER_FLAG_ALPHA = 0.92f

/**
 * The aspect ratio the *sharp* flag plate is drawn at, as width ÷ height.
 *
 * This is the fix for "the flag is too zoomed in", and the cause was geometry rather than
 * scaling: [ContentScale.Crop] fills whichever axis is short, and the box it was
 * filling is roughly 0.9:1 — taller than it is wide. A 5:3 flag scaled to cover a box that
 * shape has to grow until its *height* fits, which throws away something like 45% of its
 * width. What was left on screen was two or three enormous bands, i.e. colours rather than
 * a flag.
 *
 * Drawing the artwork into a wide box instead — 1.55:1, close to the ratio most national
 * flags actually are — means the crop is a few percent off the top and foot rather than
 * half the width, so the design reads as itself: a canton, a crest, a diagonal. The plate is
 * pinned to the top of the screen; what fills the rest of the height is [FLAG_WASH_ALPHA]'s
 * full-bleed layer, not chrome.
 */
private const val HEADER_FLAG_ASPECT = 1.55f

/**
 * The full-bleed layer under the plate: the same flag, [ContentScale.Crop] into the whole
 * screen, at a fraction of the plate's alpha.
 *
 * Two layers rather than one, because the two requirements genuinely conflict. "Cover the
 * whole background, edge to edge" wants the image scaled to a ~0.46:1 box, which is what
 * crops half a flag's width away; "look like a proper flag, not just colours" wants the
 * artwork at something near its own ratio. So the screen gets both: the plate at the top,
 * at [HEADER_FLAG_ASPECT], is the readable flag, and this is the colour of that same flag
 * carried to all four edges behind it — zoomed, but at a low enough alpha that it reads as
 * the room being lit by the artwork rather than as a second, badly cropped copy of it.
 *
 * It costs no extra decode: both layers request the same URL with the same
 * `memoryCacheKey`, so Coil hands the second one the bitmap the first already holds.
 *
 * 0.62 now, up from 0.30. At 0.30 this layer did its job as *light* and failed at the
 * other half of the brief — the flag was "cut off" partway down the screen, which was
 * never a clip: it was the plate ending and a wash too dim to be read as the same flag
 * continuing. Carrying the wash to nearly two thirds means the artwork is legibly present
 * from the status bar to the last row, so the plate reads as the sharp part of one
 * full-screen flag rather than as the only part of it.
 */
private const val FLAG_WASH_ALPHA = 0.62f

/**
 * The plate's own bottom taper, applied inside its box.
 *
 * Without it the plate would end on a hard horizontal line partway down the screen — a seam
 * in the middle of the page, which is the one thing this whole arrangement exists to avoid.
 * This dissolves its last third into the full-bleed wash under it, so the transition from
 * "sharp flag" to "flag-coloured room" has no edge in it. [HeaderFlagFadeY] still runs over
 * the layer as a whole and is what keeps the foot of the screen half-lit rather than dark.
 *
 * The taper starts higher and runs longer than it did (0.44 rather than 0.66 to the first
 * break) now that the wash under it is [FLAG_WASH_ALPHA] = 0.62 rather than 0.30. With a
 * dim wash the plate had to stay opaque as long as possible or the flag simply stopped;
 * with a strong one the handover can be gradual, and a long dissolve between two copies of
 * the same artwork at similar alpha is invisible — which is the whole point.
 */
private val HeaderFlagBottomFade = Brush.verticalGradient(
    0.00f to Color.Black,
    0.44f to Color.Black,
    0.66f to Color.Black.copy(alpha = 0.74f),
    0.84f to Color.Black.copy(alpha = 0.38f),
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
 * A soft vertical scrim, drawn *inside* the masked flag layer: heaviest at the very
 * top, where the status bar's own glyphs have to survive whatever band of the flag lands
 * behind them, and again toward the foot; lifted through the middle, where the flag is
 * allowed to be a flag. Because it lives inside the mask it tapers away exactly where the
 * flag does — it darkens the artwork, never the page.
 *
 * The top stop is heavier than it was and it starts at full strength rather than easing
 * in, because the artwork now runs all the way under the clock and the battery: those
 * glyphs are drawn by the system in white with no shadow of their own, and the only thing
 * standing between them and a white flag stripe is this.
 *
 * Everything between the two ends is much lighter than it was — 0.18 through the middle
 * against 0.36–0.40 before. That is the trade this screen now makes deliberately: the flag
 * itself is near-opaque ([HEADER_FLAG_ALPHA], [FLAG_WASH_ALPHA]) and the scrim is what
 * buys legibility back, shaped so it is heavy only where text actually lands. The two
 * heavy stops are the status bar at the top and, at the foot, the band the browse card's
 * first rows sit over; the middle is the power button and open artwork, where nothing but
 * the country headline is drawn and that headline is 34sp white with the atmosphere's own
 * light behind it. Dimming the whole flag to protect two bands was the thing that made it
 * read as grey.
 */
private val HeaderFlagScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.58f),
    0.13f to Color.Black.copy(alpha = 0.34f),
    0.30f to Color.Black.copy(alpha = 0.18f),
    0.58f to Color.Black.copy(alpha = 0.20f),
    0.80f to Color.Black.copy(alpha = 0.34f),
    1.00f to Color.Black.copy(alpha = 0.44f),
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
 * own right-hand third. Ending at 0.86 keeps a trace of direction — the left is still
 * where the colour is heaviest, which is where [ConnectWordmark] sits — without the right
 * edge reading as cropped or unfinished. The colour is irrelevant; only the alpha is read,
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
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    // 1. The wash: the same artwork cropped into the whole screen, at
                    //    [FLAG_WASH_ALPHA]. This is what makes the flag the background
                    //    rather than a picture at the top of one — it reaches all four
                    //    edges, under the status bar and behind the list alike.
                    FlagLayer(
                        model = flag,
                        cacheKey = key,
                        alpha = FLAG_WASH_ALPHA,
                        desaturate = desaturate,
                        onError = { remoteFailed = true },
                        modifier = Modifier.matchParentSize(),
                    )
                    // 2. The plate: the readable flag, at something near a flag's own
                    //    ratio, pinned to the top with its own bottom taper composited
                    //    inside its box so it dissolves into the wash instead of ending
                    //    on a line. See [HEADER_FLAG_ASPECT] and [HeaderFlagBottomFade].
                    FlagLayer(
                        model = flag,
                        cacheKey = key,
                        alpha = HEADER_FLAG_ALPHA,
                        desaturate = desaturate,
                        onError = { remoteFailed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(HEADER_FLAG_ASPECT)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(HeaderFlagBottomFade, blendMode = BlendMode.DstIn)
                            },
                    )
                }
            }
        }
        // Inside the masked layer, so it darkens the flag and tapers away with it.
        Box(Modifier.matchParentSize().background(HeaderFlagScrim))
    }
}

/**
 * One drawn copy of the flag, filling whatever box [modifier] gives it.
 *
 * One rule for both sources and both layers: scale uniformly until the box is covered, clip
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
 * [cacheKey] is why the two layers cost one decode between them, and it is also a
 * correctness fix: Coil keys a request by `data.toString()`, and a ByteBuffer's is
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
// The one surface the hero still lays over the flag — the IP chip — is made of this, and it
// is not a card: no cast shadow over the artwork, no border heavier than a hairline, no
// opaque fill.
//
// The reason is the flag. It is the whole screen, at 0.92 alpha under a scrim, and every
// opaque rectangle drawn on it is a hole in the only piece of artwork the app has.
// So the surface is a translucent floor plus a top-light plus a hairline — the three
// things that make glass read as raised — and the flag carries on through all of them.
//
// There used to be a second, heavier weight for the server selector at the hero's foot;
// that row is gone, and so is the status chip it was weighed against (see [Header]), so one
// weight is all that is left.

/** The lighter floor: enough to seat 14sp secondary ink on the palest flag band, little
 *  enough that the artwork's own colour still comes through it. */
private val GlassLight = Brush.verticalGradient(
    0.00f to RefElev1.copy(alpha = 0.62f),
    1.00f to RefElev1.copy(alpha = 0.74f),
)

/** The white edge every glass surface up here carries. Over artwork [RefBorder]
 *  disappears; white at a tenth does not, and it is the only drawn line in the hero. */
private val GlassBorder = Color.White.copy(alpha = 0.13f)

/** The top-light on a glass surface: a bright first row easing to nothing by 40%, and a
 *  dark foot so the bottom edge never reads brighter than the top. Compose has no inset
 *  box-shadow; this is both of the mockup's. */
private val GlassSheen = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.09f),
    0.05f to Color.White.copy(alpha = 0.03f),
    0.14f to Color.Transparent,
    0.86f to Color.Transparent,
    1.00f to Color.Black.copy(alpha = 0.14f),
)

/** The phase dot beside the eyebrow: the phase stated as hue, at label scale. */
private val PhaseDotSize = 7.dp

/** Corner radius on the hero's own chips — now just the IP pill. Fully
 *  rounded would read as a tag; this is the same corner-to-height relationship the top
 *  bar's glyph chips use, so every small frame on the screen agrees. */
private val ChipCorner = 11.dp

/**
 * One glass chip: floor, top-light, hairline, clipped to [shape] — the hero's only
 * surface primitive, so nothing up here can drift out of the material system by accident.
 *
 * [onClick] is taken here rather than left to the call site's own modifier because the
 * order matters: applied inside, the click lands *after* the clip, so the ripple is bound
 * to the chip's rounded shape instead of washing over the flag as a rectangle.
 */
@Composable
private fun GlassChip(
    shape: Shape,
    modifier: Modifier = Modifier,
    surface: Brush = GlassLight,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(surface)
            .border(1.dp, GlassBorder, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(GlassSheen))
        content()
    }
}

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
        // First, behind everything: the artwork and the light. The flag fills this whole box
        // — the screen, edge to edge — while the light stays in a band as tall as the hero's
        // rows plus [HeroBleed]. See [HeroBackdrop] for why those two are different heights.
        HeroBackdrop(
            state = state,
            bandHeight = heroHeight + HeroBleed,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onTogglePower = onTogglePower,
                onSetMode = onSetMode,
                // Above the card in paint order, because the power button is drawn
                // [PowerOverlap] below the hero's own bounds and a later sibling would
                // otherwise cover its foot. Children of a Column paint in declaration
                // order; zIndex is what overrides that without reordering them.
                modifier = Modifier
                    .zIndex(1f)
                    .onSizeChanged { heroContentPx = it.height },
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
// The artwork and the light, and nothing else — no rows, no touches, no chrome.
//
// It is drawn as a sibling *behind* everything rather than as the hero's background, and it
// is now the size of the whole screen. Two different heights are at work inside it and the
// distinction is the point:
//
//   the flag        — the full screen, edge to edge, under the status bar at the top and
//                     behind the browse card and the usage card at the foot. It has no edge
//                     of its own anywhere, in any direction.
//   the light + floor — a band [bandHeight] tall at the top, i.e. the hero's rows plus
//                     [HeroBleed]. The atmosphere's geometry is written in fractions of its
//                     own size (the horizon bloom sits at `size.height`, on the hero's foot,
//                     which is what fuses the hero with the card) so letting it fill the
//                     screen instead would drop that bloom to the bottom of the page and put
//                     the key light in the middle of the list.
//
// There is no [Modifier.clip], no [Modifier.border] and no [Modifier.shadow] here, and
// that is the whole of the "remove the card" change: a rounded foot, a hairline and a
// cast shadow are what a card is.
//
// The layers stack, from the back: [ChromeBg] over the top band (so the artwork is never
// composited against nothing while it crossfades, while the band's own foot stays
// translucent for the card to sit over) → the flag, full screen → [drawHeroAtmosphere] over
// the band. The flag crossfades on [PHASE_FADE_MS], as does the light's colour.
@Composable
private fun HeroBackdrop(state: HomeUiState, bandHeight: Dp, modifier: Modifier = Modifier) {
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
            HeaderFlag(
                countryCode = lastFlagCountry,
                modifier = Modifier.matchParentSize().alpha(flagAlpha),
            )
        }
        // Over the flag, under the light. Being under [drawHeroAtmosphere] is what makes the
        // wordmark part of the room rather than a label pasted on it: the key light and the
        // crown wash fall across the letters, so they take the phase's own colour and go
        // blue with everything else when the tunnel comes up.
        ConnectWordmark(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = ConnectWordTopClearance)
                // coerced because the band is measured: on the first frame, before the
                // hero's rows have reported a height, it is [HeroBackdropFallback] + the
                // bleed, and a negative height is a crash rather than a small wordmark.
                .height(
                    (bandHeight - ConnectWordTopClearance - ConnectWordFootClearance)
                        .coerceAtLeast(0.dp)
                ),
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

// ── Connect wordmark ──────────────────────────────────────────────────────────
// "CONNECT" set vertically down the left edge of the hero, one letter per line.
//
// It is typography used as architecture. The hero is a centred column — eyebrow, country,
// address, button — which reads cleanly but leaves the two side margins as dead space, and
// a full-bleed flag behind dead space just looks like an image with nothing happening on
// it. A single word running the height of the left edge gives the composition a spine: the
// eye now has a vertical to read the centred column against, and the flag has something on
// it that is part of the design rather than a row of chrome.
//
// It is drawn inside [HeroBackdrop], between the flag and [drawHeroAtmosphere], which is
// what keeps it a *surface* rather than a label. The light falls on it: the key light warms
// it while idle, and when the tunnel comes up [RefGlowOn]'s blue crown wash goes across it
// with the rest of the room. Nothing about the wordmark itself animates — it does not need
// to, because the thing lighting it does.
//
// The letters are cut, not printed. Three passes per glyph:
//
//   1. a dark copy pushed down-right, which is the shadow the letter casts into the surface
//   2. a pale copy pushed up-left, which is the light catching the opposite bevel
//   3. the face itself, filled with [ConnectWordFace] — a vertical gradient, bright at the
//      top and falling to a deep warm bronze at the foot
//
// That is the standard two-bevel emboss, and doing it with three text draws rather than a
// blur is deliberate: the offsets are sub-pixel-stable and the whole thing is static, so it
// costs three draw calls per letter once and never again. A [Modifier.blur] here would be a
// full offscreen composite every frame for a softer edge nobody asked for. The face's own
// pass does use one offscreen layer, per letter, to clip its gradient to the glyph — the
// same [BlendMode] trick the flag's mask uses, and just as static.
//
// The colour is champagne into bronze rather than white. White is what a wordmark is when
// nobody has decided anything about it, and at this size — filling the hero's height — a
// white one would compete with [CountryHeadline], which is the actual largest ink on the
// screen and needs to stay that way. A metal reads as material instead of as text: it is
// unmistakably deliberate, it sits back from white without being grey, and it is the one
// warm thing on a screen that is otherwise green, blue and slate, so it cannot be confused
// with any state the app reports.

/** The word. Seven letters, which is what sets the letter size — see [ConnectWordmark]. */
private const val CONNECT_WORD = "CONNECT"

/** How far below the top of the band the wordmark starts.
 *
 *  Enough to clear the status bar and the top bar's settings chip, which are the two things
 *  that occupy the top-left corner. The wordmark owns the left edge from under them down. */
private val ConnectWordTopClearance = 104.dp

/** And how far short of the band's foot it stops, so the last letter is not sitting in the
 *  browse card's top edge. The band includes [HeroBleed], most of which is behind the card. */
private val ConnectWordFootClearance = 96.dp

/** The column's own width, and the left margin it sits on.
 *
 *  Narrower than [ScreenPad]'s gutter on purpose: the wordmark is meant to run off nothing
 *  and touch nothing, and a letter column that starts inside the content margin would read
 *  as a list item rather than as part of the surface. */
private val ConnectWordInset = 6.dp

/** The face: champagne at the top, warm bronze at the foot. Composited through each glyph
 *  ([ConnectWordLetter]) rather than over the column, so every letter gets the full sweep —
 *  which is what makes them read as seven pieces of the same metal rather than as one
 *  gradient someone laid over a word. */
private val ConnectWordFace = Brush.verticalGradient(
    0.00f to Color(0xFFF2E4C0).copy(alpha = 0.92f),
    0.42f to Color(0xFFD8BE86).copy(alpha = 0.78f),
    1.00f to Color(0xFF8A6E3E).copy(alpha = 0.62f),
)

/** The bevel the light catches: pushed up-left, so it reads as the far wall of a cut. */
private val ConnectWordHighlight = Color.White.copy(alpha = 0.16f)

/** The shadow the letter casts: pushed down-right, and darker than the page under it. */
private val ConnectWordShadow = Color.Black.copy(alpha = 0.52f)

/** How far the two emboss copies are pushed off the face. Small — at this size 2dp is
 *  already a visible bevel, and more reads as a badly registered second copy. */
private val ConnectWordBevel = 2.dp

@Composable
private fun ConnectWordmark(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(start = ConnectWordInset)) {
        // The letters size themselves off the height they are given, so the word fills the
        // hero on a small phone and on a tablet without a breakpoint anywhere. One slot per
        // letter; the glyph is drawn at 0.92 of its slot, which leaves the ~8% of leading
        // that stops "C" and "O" from touching.
        val slot = maxHeight / CONNECT_WORD.length
        val face = with(LocalDensity.current) { (slot * 0.92f).toSp() }
        Column(
            Modifier.fillMaxHeight().clearAndSetSemantics { },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CONNECT_WORD.forEach { ch -> ConnectWordLetter(ch, face) }
        }
    }
}

/** One letter of [CONNECT_WORD], embossed: shadow, highlight, face. */
@Composable
private fun ConnectWordLetter(ch: Char, size: TextUnit) {
    val letter = ch.toString()
    // lineHeight pinned to the font size strips the extra leading a heavy face carries, so
    // the seven boxes stack to the height the slots actually allow.
    val base = TextStyle(
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.Black,
        // Tight, because a stack of single letters has no word shape to hold it together;
        // what holds it together is the letters being the same width and hard against the
        // same left edge.
        letterSpacing = 0.sp,
    )
    Box {
        Text(
            letter,
            style = base.copy(color = ConnectWordShadow),
            modifier = Modifier.offset(x = ConnectWordBevel, y = ConnectWordBevel),
        )
        Text(
            letter,
            style = base.copy(color = ConnectWordHighlight),
            modifier = Modifier.offset(x = -ConnectWordBevel, y = -ConnectWordBevel),
        )
        // The face's gradient is painted *through* the glyph rather than set on the text
        // style: the letter is drawn opaque white into an offscreen layer and
        // [ConnectWordFace] is then composited [BlendMode.SrcIn] over it, so the brush is
        // clipped to the glyph's own coverage — antialiased edges included. Same idiom the
        // flag's mask uses ([HeaderFlag]), and it keeps the whole wordmark on stable API
        // rather than on the text-brush overload.
        Text(
            letter,
            style = base.copy(color = Color.White),
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(ConnectWordFace, blendMode = BlendMode.SrcIn)
                },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
// The hero's rows — the content only. Everything visual behind them is [HeroBackdrop]'s,
// which is drawn by [HomeScreen] as a sibling so it can be taller than this. This
// composable has no surface, no frame and no background of its own; what it reports, via
// its own [Modifier.onSizeChanged] at the call site, is how tall the backdrop needs to be.
//
// The composition is a single centred column, and that is the redesign. It used to be four
// left-aligned rows with the connect control docked at the foot inside a pill that had a
// circle notched out of its right edge — a clever piece of geometry that made the primary
// action a fragment of a wider bar and put the phase, the country and the button on three
// different axes. What replaces it reads top to bottom, on one axis, in the order the user
// actually asks the questions:
//
//   am I protected?      → [PhaseEyebrow], a tracked caption with the phase's own dot
//   where am I?          → [CountryHeadline], the country at 34sp, the largest ink in the app
//   as what address?     → [MetaRow], the public IP
//   [the action]         → [PowerCircle], centred, 140dp, the only round thing up here
//   [the alternative]    → [ModePill], its small secondary control, [PowerFootGap] under
//                          it, the pair hanging [PowerOverlap] over the browse card's edge
//
// The "over what?" line — the transport · port · mode chip that sat between the headline
// and the address — is gone. It answered a question nobody standing at this screen is
// asking: whether the tunnel is up, and where it comes out, are what the hero is for, and
// the transport and the port are facts about a *config*, which is what the list below and
// the config editor are for. Its one irreplaceable field was the mode, and that moved to
// [ModePill], where it is a control rather than a read-out. Deleting it also gave the
// column back 40-odd dp, which is what brings the browse card up toward the button.
//
// The server selector that used to be docked at the foot of this column is gone. It named
// the active config in a row of its own directly above a list of configs, which is the same
// fact stated twice; the country headline already says where the tunnel comes out, and
// tapping any row in the list below is how another server gets chosen. Deleting it is what
// lets the list itself come up under the button — see [PowerOverlap].

//
// Centring the action is the point of it. A VPN has exactly one control and everything else
// on the screen is a report about that control's state; put it on the screen's own axis and
// the reports arrange themselves around it, above and below, with the flag behind all of
// them. Off to one side, notched into a bar, it competed with the bar for what the eye
// should read as the thing to press.
//
// What changes between the three [HomeUiState.phase] values is still the *light*, not any
// surface: the atmosphere changes colour and tightens ([drawHeroAtmosphere]), the ring
// reports, and the ink follows. Nothing slides, nothing sweeps, and the flag wash is on in
// all three states.
//
// statusBarsPadding() on this column is what keeps the top bar clear of the clock while
// the backdrop behind it runs on to the top of the screen.

/** How far the power button hangs over the browse card's top edge.
 *
 *  The button is the last thing in the hero and it is drawn [PowerOverlap] lower than its
 *  own layout slot, so its foot sits *on* the card below rather than above it — the control
 *  reads as embedded in the list panel instead of parked in the space over it. It is an
 *  offset rather than a negative margin because there is no such thing: the hero keeps its
 *  measured height (which is what [HeroBackdrop] sizes itself from), and only the drawing
 *  moves. [Header] is given a z-index at the call site so the card cannot paint over it. */
private val PowerOverlap = 26.dp

/** Between the top bar and the phase caption — the column's own head clearance. */
private val HeroTopSpace = 20.dp

/** Between the address and the power circle: the hero's breathing room, and what makes the
 *  artwork around the button a place rather than a gap.
 *
 *  14dp now, down from 26. Two things came out of the column above it — the server selector
 *  first, the transport · port · mode row second — and with both gone the same 26dp read as
 *  the hero having run out of things to say rather than as deliberate space. Since the whole
 *  point of the change was to bring the server list up toward the button, the gap directly
 *  above the button is the one that had to give. */
private val HeroOpenSpace = 14.dp

/** Between the power disc's foot and the [ModePill] under it.
 *
 *  Small on purpose: the pill is the disc's own secondary action, not a separate control
 *  in the column, and grouping is read from proximity. It is a little under half of
 *  [PowerOverlap] so the pair still reads as one object hanging over the card edge. */
private val PowerFootGap = 12.dp

@Composable
private fun Header(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onTogglePower: () -> Unit,
    onSetMode: (ConnectMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = ScreenPad, end = ScreenPad, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(onOpenSettings = onOpenSettings, onOpenProfile = onOpenProfile)
        Spacer(Modifier.height(HeroTopSpace))
        PhaseEyebrow(state.phase)
        Spacer(Modifier.height(9.dp))
        CountryHeadline(state)
        Spacer(Modifier.height(10.dp))
        // The address moved above the button, which is the other half of the docking
        // change: the button is now the last thing in the hero, so nothing sits between
        // its foot and the card it overlaps. The order still reads as a sentence — what
        // state, which country, as what address, [the action].
        MetaRow(state = state)
        Spacer(Modifier.height(HeroOpenSpace))
        // The disc and its secondary control travel together, so the offset that docks
        // the button over the card is on the pair rather than on the disc — otherwise the
        // pill would be measured below the disc's slot and drawn above its docked foot.
        Column(
            modifier = Modifier.offset(y = PowerOverlap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PowerCircle(
                mode = state.mode,
                phase = state.phase,
                enabled = state.activeConfig != null,
                onClick = onTogglePower,
                onSwipeUp = { onSetMode(ConnectMode.SMART) },
                onSwipeDown = { onSetMode(ConnectMode.MANUAL) },
            )
            Spacer(Modifier.height(PowerFootGap))
            ModePill(mode = state.mode, onSetMode = onSetMode)
        }
    }
}

// ── Mode pill ─────────────────────────────────────────────────────────────────
// The small secondary control under the connect disc: it states the connect mode and
// toggles it.
//
// The mode was already changeable from this button — a vertical drag on the disc, up for
// Smart and down for Manual — but nothing on the screen drew that gesture, so in practice
// the only way to find it was to be told. This pill is that missing control, and it sits
// under the disc because that is where the gesture it replaces lives. The drag is kept:
// same two bindings, two ways in.
//
// It is deliberately quiet — glass, hairline, 12.5sp, dimmer ink than anything above it —
// so that the hero still has exactly one primary action. A secondary button that competes
// with the disc for the eye would undo the whole point of centring the disc.
//
// The label reads "Mode · Smart": the *current* mode, not the one a tap would switch to.
// It states the fact because nothing else on the screen does any more — the status row
// that used to carry the mode alongside the transport and the port is gone — and a control
// that is the only place a value appears has to show the value. "Mode ·" in front of it is
// what keeps it from reading as a bare label: the prefix is the noun, the word after it is
// the setting, and together they read as a setting one can change rather than as a status
// light. Only the mode word swaps, on the fade-in-over-fade-out [AnimatedContent] this
// screen uses everywhere, with the container sizing between "Smart" and "Manual" rather
// than jumping.

/** The pill's ink and hairline. Dimmer than [RefTextMid] on purpose: see the note above. */
private val ModePillInk = Color.White.copy(alpha = 0.72f)

/** The "Mode ·" prefix, one step down again so the value is what the eye lands on. */
private val ModePillLabelInk = Color.White.copy(alpha = 0.46f)

@Composable
private fun ModePill(mode: ConnectMode, onSetMode: (ConnectMode) -> Unit) {
    val reduce = rememberReduceMotion()
    val next = if (mode == ConnectMode.SMART) ConnectMode.MANUAL else ConnectMode.SMART
    Row(
        Modifier
            .clip(CircleShape)
            .background(GlyphChip)
            .border(1.dp, GlyphChipBorder, CircleShape)
            .clickable(
                onClickLabel = "Switch to ${next.label} mode",
                onClick = { onSetMode(next) },
            )
            .padding(horizontal = 15.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Mode · ",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
            color = ModePillLabelInk,
            maxLines = 1,
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 160)) togetherWith fadeOut(motionSpec(reduce, 110))
                    ).using(SizeTransform(clip = false))
            },
            label = "modePill",
        ) { target ->
            Text(
                target.label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                color = ModePillInk,
                maxLines = 1,
            )
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
// These two chips are now the only framed things at the top of the screen: the status row
// that used to sit under them first lost its glass surface and is now deleted outright (see
// [Header]), so a frame up here means "navigation", and nothing else. Their glyphs are plain
// white. They were the app's deep
// green, which was the one place the brand colour was stated independently of connection
// state — but the flag now runs the full height of the screen behind them, and a mid-tone
// green mark on an arbitrary flag is the one glyph colour that can land on its own hue.
// White is the only tint that holds against every backdrop the wash can put there, and it
// also keeps the *green* on this screen meaning exactly one thing: connected.

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

// ── Phase eyebrow ─────────────────────────────────────────────────────────────
// The first line of the hero, and the answer to the only question a VPN app is opened to
// ask: a dot in the phase's colour, and the phase in words, tracked wide at caption size.
//
// It is a caption rather than a headline because the country under it is the headline, and
// two large lines stacked would leave neither of them primary. What makes it read at a
// glance anyway is the colour and the position — first thing, dead centre, on the screen's
// own axis — not the size.
//
// The phase is stated twice over, in hue and in text, inside 11sp of height. That is
// deliberate: every other signal for it up here is light (the atmosphere, the ring), and a
// state that is only ever colour is a state a colour-blind user has to infer from a spinner.

/** The eyebrow's own text, per phase. Present tense, and deliberately not a sentence: it
 *  is a status label, and the shorter it is the more it reads as one. */
private fun phaseLabel(phase: ConnPhase): String = when (phase) {
    ConnPhase.OFF -> "NOT CONNECTED"
    ConnPhase.CONNECTING -> "CONNECTING"
    ConnPhase.CONNECTED -> "PROTECTED"
}

/** The dot's colour, and the eyebrow's: grey off, amber working, green up. */
private fun phaseDotColor(phase: ConnPhase): Color = when (phase) {
    ConnPhase.OFF -> RefTextLow
    ConnPhase.CONNECTING -> RefWorking
    ConnPhase.CONNECTED -> RefGreen
}

@Composable
private fun PhaseEyebrow(phase: ConnPhase, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val dot by animateColorAsState(
        targetValue = phaseDotColor(phase),
        animationSpec = motionSpec(reduce, PHASE_FADE_MS),
        label = "eyebrowDot",
    )
    // The dot breathes while an attempt is in flight, and only then — the animation is
    // created inside the branch, so an idle or connected screen has no frame callback of
    // its own from here.
    val pulse = if (phase == ConnPhase.CONNECTING && !reduce) {
        val transition = rememberInfiniteTransition(label = "eyebrowPulse")
        val breath by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eyebrowBreath",
        )
        breath
    } else {
        1f
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(PhaseDotSize).alpha(pulse).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        // Crossfaded with the container sizing between the three words, so the row does
        // not jump width mid-transition.
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 220)) togetherWith fadeOut(motionSpec(reduce, 140))
                    ).using(SizeTransform(clip = false))
            },
            label = "phaseEyebrow",
        ) { value ->
            Text(
                phaseLabel(value),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                // Wide tracking is what turns 11sp of uppercase into a label rather than
                // into small text. 1.6sp at this size is about 0.15em, the same ratio the
                // section headings in Settings use.
                letterSpacing = 1.6.sp,
                color = RefTextMid,
                maxLines = 1,
            )
        }
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
// [headerInk] colours it, so the headline is the phase's colour too: white off, accent
// working, green up. That is the third statement of the phase on this screen and the one
// that carries furthest, because it is on the biggest text.

/** The headline, and its subtitle's, own size. 34sp is the largest the longest country
 *  name this app can draw ("Bosnia and Herzegovina") still fits on one line of a 360dp
 *  screen at default font scale; beyond that it ellipsises, which on the screen's one
 *  headline would be worse than a smaller face. */
private val HeadlineSize = 34.sp

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
                color = headerInk(state.phase),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
// One fact, on one line: the address the internet currently sees.
//
// Off, it holds this device's own address. That is the point of keeping it visible in every
// phase: the number the user is about to change is the number they can read now, so
// connecting is a visible before-and-after rather than a value that appears out of nothing.
// Connected, the same slot carries the exit node's.
//
// The address is copyable — tap it and it goes to the clipboard — which is the one utility
// the hero offers, and the reason it is a chip: a tappable value needs a hit target and an
// edge.
//
// Two things used to sit beside it and both are gone. The session clock was a timer for
// something the user is not timing — the tunnel being up is the fact, not how long it has
// been up — and the connecting rail (a ConnectingPulse composable, deleted with it) put a
// travelling dash next to the address in the one state where the address is already about
// to change.
// What reports progress now is the ring around the button and the word at the head of the
// hero, both of which say it without anything moving next to a value.

@Composable
private fun MetaRow(state: HomeUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reduce = rememberReduceMotion()
    val ip = state.displayIp
    // A floor height whether or not either chip is in it, so the hero does not grow a few
    // dp on connect and shrink again on cancel — a header that changes height while the
    // user waits on it is the one motion nobody asked for.
    Row(
        modifier.heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Both changes this slot ever makes are worth a crossfade rather than a jump: the
        // first address landing, and the swap from this device's to the exit node's when
        // the tunnel comes up. The width animates with it, so an address arriving in an
        // empty slot grows into it instead of appearing at full size.
        AnimatedContent(
            targetState = ip,
            transitionSpec = {
                (
                    fadeIn(motionSpec(reduce, 260)) togetherWith fadeOut(motionSpec(reduce, 140))
                    ).using(SizeTransform(clip = false))
            },
            label = "publicIp",
        ) { value ->
            if (value.isBlank()) {
                Spacer(Modifier.width(0.dp))
            } else {
                GlassChip(
                    shape = RoundedCornerShape(ChipCorner),
                    onClickLabel = "Copy IP address",
                    onClick = {
                        clipboard.setText(AnnotatedString(value))
                        android.widget.Toast
                            .makeText(context, "IP copied", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "IP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = RefTextLow,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            value,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = RefTextHi,
                            maxLines = 1,
                            // maxLines alone truncates by clipping, which leaves a
                            // half-drawn glyph at the end of a long address; with softWrap
                            // off and ellipsis on, anything that still doesn't fit ends in
                            // "…" instead of mid-stroke. An IPv4 address is 15 characters
                            // at most — see [GeoService.lookupCurrentIp], now v4-only — so
                            // in practice neither applies, but the chip can no longer
                            // render a cut-off value whatever it is handed.
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFeatureSettings = "tnum"),  // tabular-nums
                        )
                    }
                }
            }
        }
    }
}

// ── Power circle ──────────────────────────────────────────────────────────────
// The connect control: a disc with a ring around it, and between them the one thing on
// this screen that reports progress rather than a result.
//
// It is centred on the screen's own axis, at [PowerSize], with nothing beside it, and it
// hangs [PowerOverlap] over the browse card below so it reads as embedded in the list panel
// rather than parked above it. It used to be half-swallowed by a connect pill — the pill
// stopped short of the right edge and the circle was pulled back over the gap, so the two
// read as one fused control and neither was quite the primary thing. That pill's job (which
// server) is now the list itself and its headline (which country) moved to
// [CountryHeadline]; what is left here is the action, alone, and big enough to be the
// obvious target from arm's length.
//
// The disc is [PowerDiscSize] inside the [PowerSize] box, which leaves an 11dp band around
// it for the ring — the same ~8% of the diameter the smaller control used, so the ring
// still reads as a rim on the disc rather than as a separate circle near it.
//
// Three faces, one per [ConnPhase], crossfaded on [PHASE_FADE_MS]:
//
//   OFF        — the brushed-white disc, [PowerInk] mark, and a bare hairline track.
//                A white disc on dark chrome is the highest-contrast thing the screen
//                can draw, which is what an idle app's one button should be.
//   CONNECTING — the same disc under a faint amber tint, an [RefWorking] mark, and a
//                240° arc turning once a second around it, over a hard yellow-orange
//                halo. The arc is the only indeterminate progress in the app and it is
//                deliberately outside the disc: the face keeps its shape, so the button
//                still looks pressable while it works.
//   CONNECTED  — the *same white disc*, with a deep green mark on it and the ring around
//                it lit [RefGreen]. Nothing is thrown around the button itself: the halo
//                that used to sit under it is gone, and what reports "lit" now is the
//                room — [drawHeroAtmosphere] turns the whole backdrop [RefGlowOn] blue.
//                The face never fills with colour in any state: the button is the one
//                control on the screen, so it should look like the same control before and
//                after it is pressed, and the ring is what reports the result. A green disc
//                also read as a filled *primary action* — "press me" — in exactly the state
//                where pressing it disconnects, which is the opposite of what it should
//                invite. The light is blue rather than green on purpose: state is green,
//                light is blue, and separating the two is what keeps a lit ring legible
//                instead of one green smear.

//
// It carries one gesture besides the tap: a vertical drag switches Smart / Manual. It is a
// shortcut rather than the only way in — [ModePill], directly under the disc, is the drawn
// control for the same two bindings — and it is offered to a screen reader as two named
// actions on this button.
//
// The mockup's four-part box-shadow, split by what Compose can draw:
//   0 16px 34px rgba(0,0,0,0.45)      ┐ the cast shadow — Modifier.shadow
//   0 4px 10px rgba(0,0,0,0.25)       ┘
//   inset 0 3px 4px rgba(255,255,255,0.95)  ┐ Compose has no inset box-shadow, so
//   inset 0 -10px 14px rgba(0,0,0,0.14)     ┘ these two are [PowerFaceSheen], a
//                                             bright top rim over a soft dark foot.

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
            ConnPhase.CONNECTING -> RefWorkingInk
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
 * The ring in the band around the disc: a hairline track in every state, plus one arc
 * that says what the connection is doing.
 *
 * Connecting, the arc turns — [POWER_ARC_SWEEP_DEG] of [RefWorking] once every
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
                color = RefWorking.copy(alpha = 0.95f * working),
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

/** The connecting tint: [RefWorking] laid over the white face, light enough that the
 *  disc still reads as the same brushed surface holding a colour. Amber rather than the
 *  old blue accent so the face, the ring arc, the eyebrow dot and the room's own light are
 *  all saying the same thing while an attempt is in flight. */
private val PowerWorkingFace = Brush.linearGradient(
    0.00f to RefWorking.copy(alpha = 0.10f),
    1.00f to RefWorking.copy(alpha = 0.26f),
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
// horizon light carry on through the tab row (see [panelTopFade]). It is deliberately not
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
    onSelectTab: (HomeTab) -> Unit,
    onQueryChange: (String) -> Unit,
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
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner))
            // The card's own fill is a gradient now, not [RefBg] flat: translucent at its
            // top edge and opaque by [PanelFade] down. That is what merges it with the
            // hero. The backdrop above is drawn [HeroBleed] taller than the hero's rows, so
            // what is behind these first 132dp is the flag and its horizon light — and
            // because the fill lets them through, the tab row sits *in* the artwork instead
            // of on a panel that starts below it. There is no gap and no visible join: the
            // brightest part of the transition is the light itself.
            .background(fade)
            .drawBehind { drawPanelTopEdge() }
    ) {
        // The power stack — the disc and the [ModePill] under it — is drawn [PowerOverlap]
        // past the bottom of the hero's measured height (see the `offset` at its call
        // site), so the top [PowerOverlap] of this card is painted under the pill. This
        // spacer is what keeps the tab pills out from underneath: without it the stack
        // lands on the row and covers the middle of "All / Custom". It is the same token
        // as the offset, so the two cannot drift.
        Spacer(Modifier.height(PowerOverlap))
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

/**
 * How far down the browse card the hero's artwork and light are still allowed through.
 *
 * Roughly the tab row plus its margins, which is what makes the merge read as intended:
 * the card's controls sit in the flag's own light, the list below them does not. Longer and
 * the first server rows would be set over artwork; shorter and the fade would land inside
 * the tab pills, which is a seam in a worse place than the one it replaced.
 */
private val PanelFade = 132.dp

/**
 * The browse card's fill: translucent [RefBg] at its top edge, nearly opaque by [heightPx]
 * down — and *nearly* is deliberate.
 *
 * Anchored in pixels with an explicit `startY`/`endY` rather than in fractions, because the
 * card's height is whatever is left of the screen after the hero — a fractional stop would
 * put the fade at a different place on every device and inside the list on a tall one.
 * [TileMode.Clamp] is what holds the end value all the way to the foot.
 *
 * It starts at 0.62 rather than at nothing. Fully transparent would be a prettier merge and
 * an unreadable tab row: the pills' own labels are set at 13sp, and over the pale band of a
 * flag they would have nothing behind them. 0.62 of near-black is enough for white text to
 * clear 7:1 against the worst case while the artwork is still unmistakably there.
 *
 * It ends at 0.92 rather than at 1.0 because the flag is the whole screen's background now
 * (see [HeroBackdrop]) and an opaque card would be a lid over the bottom two thirds of it —
 * the artwork would still technically reach every edge and the user would still see it stop
 * at the top of the list. 0.92 over [FLAG_WASH_ALPHA]'s already-dim wash is a hint of the
 * country's colour behind the rows, worth about two or three percent of luminance: every
 * row's own fill and every label on it are unchanged in contrast terms, and the page no
 * longer has a horizon across it.
 */
private fun panelTopFade(heightPx: Float): Brush = Brush.verticalGradient(
    0.00f to RefBg.copy(alpha = 0.62f),
    0.35f to RefBg.copy(alpha = 0.80f),
    0.70f to RefBg.copy(alpha = 0.89f),
    1.00f to RefBg.copy(alpha = 0.92f),
    startY = 0f,
    endY = heightPx,
    tileMode = TileMode.Clamp,
)

/**
 * The card's top edge and its two corner arcs.
 *
 * Softer than the 0.14 hairline it was. That value was tuned for a card meeting the page
 * on plain near-black, where an edge had to declare itself; this edge is now drawn over the
 * hero's own light, and at 0.14 it read as exactly what the redesign set out to remove — a
 * bright line across the screen. At 0.08, with the fade and the horizon light behind it, it
 * still catches the corners and no longer reads as a border.
 */
private fun DrawScope.drawPanelTopEdge() {
    val hairline = 1.dp.toPx()
    val radius = PanelCorner.toPx()
    clipRect(top = 0f, bottom = radius + hairline) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                0.00f to Color.White.copy(alpha = 0.03f),
                0.28f to Color.White.copy(alpha = 0.08f),
                0.72f to Color.White.copy(alpha = 0.08f),
                1.00f to Color.White.copy(alpha = 0.03f),
            ),
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
    // No elapsed time here: the session duration is deliberately not shown anywhere on
    // this screen any more (the hero's timer chip went with it), so the title stays the
    // same string in both phases and only [subtext] changes with the connection.
    val title = "Data used this session"
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
