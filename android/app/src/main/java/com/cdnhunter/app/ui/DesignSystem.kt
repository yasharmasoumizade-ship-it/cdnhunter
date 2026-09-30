package com.cdnhunter.app.ui

// ══ APP DESIGN SYSTEM ════════════════════════════════════════════════════════════
// The single source of truth for Home, Settings and Profile (and the screens reached from
// them). Home's private `Ds`, its type scale and its font all read from here, and every
// Settings/Profile surface is built out of the Premium* components below — so a change to a
// radius, a surface or the accent lands on all three screens at once.
//
//   Colour      matte black surfaces, ONE blue accent. Success / Warning / Error exist only as
//               signal semantics (Pro, unverified, destructive) — never as decoration.
//   Type        Manrope ([AppFont]) on the [AppType] scale.
//   Spacing     4dp grid: S1 4 · S2 8 · S3 12 · S4 16 · S5 20 · S6 24 · S7 32.
//   Radius      RMd 16 (controls, chips, icon-less rows) · RLg 22 (cards, groups, primary
//               action) · RXl 28 (hero / panel).
//   Elevation   none: a card is a slightly lighter surface with a hairline border.
//   States      rest → pressed (surface steps lighter, objects sink 2.5%) → active (accent).
//   Motion      one spring pair for every press ([animatePressScale]); all of it snaps when
//               the system "remove animations" setting is on.

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdnhunter.app.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ── Type ──────────────────────────────────────────────────────────────────────

/** Manrope, one entry per weight pinned to its own point on the variable font's weight axis. */
@OptIn(ExperimentalTextApi::class)
internal val AppFont = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Five steps, each with the weight it is always used at: a size implies a weight. */
internal object AppType {
    val Caption = 12.sp to FontWeight.Medium     // subtitles, captions, badges
    val Body = 14.sp to FontWeight.SemiBold      // chips, buttons, segmented labels
    val Subtitle = 16.sp to FontWeight.SemiBold  // row titles, card titles
    val Title = 20.sp to FontWeight.SemiBold     // screen titles, primary action
    val Headline = 26.sp to FontWeight.Bold      // hero names
}

// ── Tokens ────────────────────────────────────────────────────────────────────

internal object AppDs {
    // Colour
    val Bg = Color(0xFF0A0A0C)              // Primary background
    val BgTop = Color(0xFF0C0C0F)           // Secondary background (top of the page wash)
    val Surface = Color(0xFF111114)         // Card surface
    val SurfaceRaised = Color(0xFF17171B)   // Elevated surface (icon containers, controls)
    val SurfacePressed = Color(0xFF1F1F24)
    val Hairline = Color(0x12FFFFFF)        // white @ 7%  — dividers
    val Border = Color(0x1AFFFFFF)          // white @ 10% — card / control edges
    val TextHi = Color(0xFFF2F3F5)          // Primary text
    val TextMid = Color(0xFF9A9CA6)         // Secondary text
    val TextLow = Color(0xFF6C6F7A)         // Muted text
    val Accent = Color(0xFF3D8BFF)
    val AccentSoft = Color(0xFF7DB2FF)
    val Success = Color(0xFF34C77A)
    val Warning = Color(0xFFE0B23B)         // Pro / needs attention
    val Error = Color(0xFFEF4444)
    val WarmInk = Color(0xFF1B1712)         // dark ink on a Warning fill

    val PageGradient = Brush.verticalGradient(0.00f to BgTop, 1.00f to Bg)

    /** A whisper of light across a filled button's top half — depth without a gradient fill. */
    val ButtonTopLight = Brush.verticalGradient(
        0.00f to Color.White.copy(alpha = 0.05f),
        1.00f to Color.Transparent,
    )

    // Spacing
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 20.dp
    val S6 = 24.dp
    val S7 = 32.dp

    // Radius
    val RMd = 16.dp
    val RLg = 22.dp
    val RXl = 28.dp

    // Icons
    val IconSm = 16.dp
    val IconMd = 20.dp
    val IconContainer = 40.dp

    // Components
    val Control = 48.dp            // touch floor
    val ButtonHeight = 40.dp       // inline action chips
    val ConnectHeight = 68.dp      // Home's connect button — also the primary action's height
    val RowHeight = 64.dp
    val ServerRowHeight = 64.dp
    val ServerFlag = 36.dp
    val SearchHeight = 48.dp
    val PingWidth = 52.dp
    val ConnectWell = 48.dp
    val ClearTap = 36.dp

    /** Where a row's text starts: gutter + icon container + gap. Dividers inset to it. */
    val RowTextInset = S4 + IconContainer + S3

    // Elevation (Home's two soft shadows only)
    val ElevHero = 12.dp
    val ElevPanel = 16.dp
    val ShadowAmbient = Color.Black.copy(alpha = 0.35f)
    val ShadowSpot = Color.Black.copy(alpha = 0.45f)

    // Press
    const val PressScale = 0.975f        // cards, primary action, chips
    const val PressScaleSmall = 0.94f    // 48dp icon chips
}

// ── Motion ────────────────────────────────────────────────────────────────────

/** True when the system "remove animations" setting is on, so motion can be held static. */
@Composable
internal fun appReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Covers developer options, Battery Saver and Accessibility → Remove animations.
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
internal fun <T> appMotion(reduce: Boolean, durationMs: Int): FiniteAnimationSpec<T> =
    if (reduce) snap() else tween(durationMs)

/** The one press animation: a quick sink while held, a soft spring back on release. */
@Composable
internal fun animatePressScale(pressed: Boolean, pressedScale: Float = AppDs.PressScale): Float {
    val reduce = appReduceMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (reduce) {
            snap()
        } else if (pressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
        },
        label = "press",
    )
    return scale
}

// ── Page frame ────────────────────────────────────────────────────────────────

/** Page wash + Manrope + status-bar inset: the frame Home, Settings and Profile all sit in. */
@Composable
internal fun PremiumPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ProvideTextStyle(TextStyle(fontFamily = AppFont)) {
        Box(modifier.fillMaxSize().background(AppDs.PageGradient)) {
            Column(Modifier.fillMaxSize().statusBarsPadding(), content = content)
        }
    }
}

/** Back chip + screen title. Same chip as Home's menu button: 48dp, RMd, hairline border. */
@Composable
internal fun PremiumTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = AppDs.S4, end = AppDs.S4, top = AppDs.S2, bottom = AppDs.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PremiumBackButton(onClick = onBack)
        Spacer(Modifier.width(AppDs.S3))
        Text(
            title,
            fontSize = AppType.Title.first,
            fontWeight = AppType.Title.second,
            color = AppDs.TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PremiumBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animatePressScale(pressed, AppDs.PressScaleSmall)
    val shape = RoundedCornerShape(AppDs.RMd)
    Box(
        modifier
            .size(AppDs.Control)
            .scale(scale)
            .clip(shape)
            .background(if (pressed) AppDs.SurfacePressed else AppDs.Surface)
            .border(1.dp, AppDs.Border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Back",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = "Back",
            tint = AppDs.TextHi,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The frame every scrolling secondary screen (Settings, Profile, Payment history) sits in. */
@Composable
internal fun PremiumScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumPage {
        PremiumTopBar(title = title, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppDs.S4),
        ) {
            content()
            Spacer(Modifier.height(AppDs.S7))
        }
    }
}

// ── Surfaces ──────────────────────────────────────────────────────────────────

/**
 * A card: matte [AppDs.Surface], hairline border, RLg corners. With [onClick] it also takes
 * Home's press language — surface steps lighter and the card sinks 2.5%.
 */
@Composable
internal fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = AppDs.Border,
    shape: Shape = RoundedCornerShape(AppDs.RLg),
    contentPadding: PaddingValues = PaddingValues(AppDs.S4),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && onClick != null
    val scale = animatePressScale(active)
    val fill by animateColorAsState(
        targetValue = if (active) AppDs.SurfacePressed else AppDs.Surface,
        animationSpec = appMotion(appReduceMotion(), 100),
        label = "cardFill",
    )
    Column(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/** A group of rows in one card. Rows own their padding; [PremiumDivider] separates them. */
@Composable
internal fun PremiumCardGroup(
    modifier: Modifier = Modifier,
    borderColor: Color = AppDs.Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppDs.RLg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppDs.Surface)
            .border(1.dp, borderColor, shape),
        content = content,
    )
}

/** Hairline between two rows, inset to where the row's text begins so the icon column stays one edge. */
@Composable
internal fun PremiumDivider(startInset: Dp = AppDs.RowTextInset) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = AppDs.S4)
            .height(1.dp)
            .background(AppDs.Hairline),
    )
}

/** Group heading: "CONNECTION", "NETWORK". The top space belongs to the component. */
@Composable
internal fun SectionHeader(text: String, top: Dp = AppDs.S6) {
    Text(
        text,
        fontSize = AppType.Caption.first,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        color = AppDs.TextMid,
        modifier = Modifier.padding(top = top, bottom = AppDs.S2, start = AppDs.S1),
    )
}

// ── Icons ─────────────────────────────────────────────────────────────────────

@Composable
private fun IconContainerShell(tone: Color?, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(AppDs.IconContainer)
            .clip(CircleShape)
            .background(if (tone != null) tone.copy(alpha = 0.12f) else AppDs.SurfaceRaised)
            .border(1.dp, if (tone != null) tone.copy(alpha = 0.32f) else AppDs.Border, CircleShape),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Every row's icon: a 40dp circle on the raised surface, hairline border, 20dp glyph.
 *  [tone] (Pro, destructive) tints the container; otherwise it is neutral. */
@Composable
internal fun PremiumIconContainer(icon: ImageVector, modifier: Modifier = Modifier, tone: Color? = null) {
    IconContainerShell(tone, modifier) {
        Icon(icon, null, tint = tone ?: AppDs.TextHi.copy(alpha = 0.92f), modifier = Modifier.size(AppDs.IconMd))
    }
}

@Composable
internal fun PremiumIconContainer(@DrawableRes iconRes: Int, modifier: Modifier = Modifier, tone: Color? = null) {
    IconContainerShell(tone, modifier) {
        Icon(
            painterResource(id = iconRes),
            null,
            tint = tone ?: AppDs.TextHi.copy(alpha = 0.92f),
            modifier = Modifier.size(AppDs.IconMd),
        )
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

/**
 * The one row layout: leading icon container, title (+ optional subtitle), optional trailing
 * control. [onClick] makes it a button; [onToggle] makes the whole row a switch (the trailing
 * [PremiumToggle] is then display-only, so the touch target is the row, not a 52dp pill).
 */
@Composable
internal fun PremiumRow(
    title: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = AppDs.TextHi,
    subtitleColor: Color = AppDs.TextMid,
    trailing: @Composable (() -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    checked: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val interactive = onClick != null || onToggle != null
    val fill by animateColorAsState(
        targetValue = if (pressed && interactive) AppDs.SurfacePressed else AppDs.Surface.copy(alpha = 0f),
        animationSpec = appMotion(appReduceMotion(), 100),
        label = "rowFill",
    )
    val base = modifier.fillMaxWidth().background(fill)
    val touch = when {
        onToggle != null -> base.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            role = Role.Switch,
            onValueChange = onToggle,
        )
        onClick != null -> base.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
        else -> base
    }
    Row(
        touch
            .heightIn(min = AppDs.RowHeight)
            .padding(horizontal = AppDs.S4, vertical = AppDs.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(AppDs.S3))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = AppType.Subtitle.first,
                fontWeight = AppType.Subtitle.second,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = AppType.Caption.first,
                    fontWeight = AppType.Caption.second,
                    lineHeight = 16.sp,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AppDs.S3))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(AppDs.S2))
            RowChevron()
        }
    }
}

@Composable
private fun RowChevron() {
    Icon(Icons.Rounded.ChevronRight, null, tint = AppDs.TextLow, modifier = Modifier.size(AppDs.IconMd))
}

/** A row that opens something, or just states a value. */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    tone: Color? = null,
    titleColor: Color = AppDs.TextHi,
) {
    PremiumRow(
        title = title,
        subtitle = subtitle,
        titleColor = titleColor,
        leading = { PremiumIconContainer(icon, tone = tone) },
        chevron = showChevron,
        onClick = onClick,
    )
}

@Composable
internal fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    tone: Color? = null,
    titleColor: Color = AppDs.TextHi,
) {
    PremiumRow(
        title = title,
        subtitle = subtitle,
        titleColor = titleColor,
        leading = { PremiumIconContainer(iconRes, tone = tone) },
        chevron = showChevron,
        onClick = onClick,
    )
}

/** The same row with a switch on the right instead of a chevron. */
@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PremiumRow(
        title = title,
        subtitle = subtitle,
        leading = { PremiumIconContainer(icon) },
        trailing = { PremiumToggle(checked = checked, onCheckedChange = null) },
        onToggle = onCheckedChange,
        checked = checked,
    )
}

@Composable
internal fun SettingsToggleRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PremiumRow(
        title = title,
        subtitle = subtitle,
        leading = { PremiumIconContainer(iconRes) },
        trailing = { PremiumToggle(checked = checked, onCheckedChange = null) },
        onToggle = onCheckedChange,
        checked = checked,
    )
}

// ── Controls ──────────────────────────────────────────────────────────────────

/**
 * The app's switch: a 52×30 pill that is a matte groove when off and the accent when on, with a
 * round thumb that springs across. Not Material's Switch — no ripple halo, no default palette.
 * Pass a null [onCheckedChange] when an enclosing row owns the touch (see [PremiumRow]).
 */
@Composable
internal fun PremiumToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val reduce = appReduceMotion()
    val track by animateColorAsState(
        if (checked) AppDs.Accent else AppDs.SurfaceRaised, appMotion(reduce, 180), label = "toggleTrack",
    )
    val edge by animateColorAsState(
        if (checked) AppDs.Accent else AppDs.Border, appMotion(reduce, 180), label = "toggleEdge",
    )
    val thumb by animateColorAsState(
        if (checked) Color.White else AppDs.TextMid, appMotion(reduce, 180), label = "toggleThumb",
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 25.dp else 3.dp,
        animationSpec = if (reduce) {
            snap()
        } else {
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "toggleX",
    )
    Box(
        modifier
            .width(52.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, edge, CircleShape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbX)
                .size(24.dp)
                .shadow(
                    3.dp, CircleShape, clip = false,
                    ambientColor = AppDs.ShadowAmbient, spotColor = AppDs.ShadowSpot,
                )
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

/**
 * A two-or-three way choice as one control: a recessed track with one lit thumb that glides to
 * the selected segment. The thumb is measured to each segment's real bounds, so it fits whether
 * segments are content-sized (Server choice, MTU) or share the row ([equalWeight]).
 *
 * `options` is (stored value, shown label) — the key is what goes to [AppSettings].
 */
@Composable
internal fun PremiumSegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    equalWeight: Boolean = false,
) {
    val trackShape = remember { RoundedCornerShape(AppDs.RMd) }
    val segShape = remember { RoundedCornerShape(AppDs.RMd - 3.dp) }
    val density = LocalDensity.current
    val reduce = appReduceMotion()

    val segX = remember(options.size) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    val segW = remember(options.size) { mutableStateListOf<Float>().apply { repeat(options.size) { add(0f) } } }
    var segH by remember { mutableStateOf(0f) }

    val selIdx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val targetX = segX.getOrElse(selIdx) { 0f }
    val targetW = segW.getOrElse(selIdx) { 0f }

    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(targetX, targetW) {
        if (targetW <= 0f) return@LaunchedEffect
        if (!settled || reduce) {
            thumbX.snapTo(targetX)
            thumbW.snapTo(targetW)
            settled = true
        } else {
            launch { thumbX.animateTo(targetX, tween(220, easing = FastOutSlowInEasing)) }
            launch { thumbW.animateTo(targetW, tween(220, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        modifier
            .clip(trackShape)
            .background(AppDs.Bg)
            .border(1.dp, AppDs.Border, trackShape)
            .padding(3.dp),
    ) {
        if (settled && thumbW.value > 0f && segH > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(thumbX.value.roundToInt(), 0) }
                    .width(with(density) { thumbW.value.toDp() })
                    .height(with(density) { segH.toDp() })
                    .clip(segShape)
                    .background(AppDs.Accent.copy(alpha = 0.18f))
                    .border(1.dp, AppDs.Accent.copy(alpha = 0.45f), segShape),
            )
        }
        Row(
            if (equalWeight) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { i, (key, label) ->
                val on = key == selected
                val ink by animateColorAsState(
                    targetValue = if (on) AppDs.TextHi else AppDs.TextMid,
                    animationSpec = appMotion(reduce, 200),
                    label = "segInk",
                )
                Box(
                    (if (equalWeight) Modifier.weight(1f) else Modifier)
                        .onGloballyPositioned { c ->
                            val nx = c.positionInParent().x
                            val nw = c.size.width.toFloat()
                            val nh = c.size.height.toFloat()
                            if (segX[i] != nx) segX[i] = nx
                            if (segW[i] != nw) segW[i] = nw
                            if (segH != nh) segH = nh
                        }
                        .clip(segShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                        ) { onSelect(key) }
                        .padding(horizontal = AppDs.S3, vertical = AppDs.S2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = AppType.Caption.first,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = ink,
                    )
                }
            }
        }
    }
}

/**
 * A small action inside a row or card — "Copy", "Clear", "Resend verification". [accent] fills it
 * with the accent for the one action that is the point. Same radius, press and ink as Home's chips.
 */
@Composable
internal fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animatePressScale(pressed && enabled)
    val shape = RoundedCornerShape(AppDs.RMd)
    val fill by animateColorAsState(
        targetValue = when {
            accent && pressed -> AppDs.Accent.copy(alpha = 0.82f)
            accent -> AppDs.Accent
            pressed -> AppDs.SurfacePressed
            else -> AppDs.SurfaceRaised
        },
        animationSpec = appMotion(appReduceMotion(), 100),
        label = "buttonFill",
    )
    Row(
        modifier
            .heightIn(min = AppDs.ButtonHeight)
            .scale(scale)
            .clip(shape)
            .background(fill)
            .then(if (accent) Modifier else Modifier.border(1.dp, AppDs.Border, shape))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = AppDs.S4, vertical = AppDs.S2),
        horizontalArrangement = Arrangement.spacedBy(AppDs.S2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text,
            fontSize = AppType.Body.first,
            fontWeight = AppType.Body.second,
            color = if (accent) Color.White else AppDs.TextHi,
        )
    }
}

/**
 * The full-width call to action. It is Home's connect button language: [AppDs.ConnectHeight],
 * RLg corners, the same top-light, the same press sink and haptic. [tone] is the fill — the
 * accent by default, and the warm Pro colour only for the upgrade prompt.
 */
@Composable
internal fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = AppDs.Accent,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale = animatePressScale(pressed && enabled)
    val shape = RoundedCornerShape(AppDs.RLg)
    val shade by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = appMotion(appReduceMotion(), 100),
        label = "ctaShade",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(AppDs.ConnectHeight)
            .scale(scale)
            .clip(shape)
            .background(tone)
            .background(AppDs.ButtonTopLight)
            .background(shade)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = AppType.Title.first,
            fontWeight = AppType.Title.second,
            color = contentColor,
        )
    }
}

// ── Status, identity ──────────────────────────────────────────────────────────

/** A small pill: "FREE", "PRO". [tone] tints it (Pro → Warning); null is the neutral matte pill. */
@Composable
internal fun StatusBadge(text: String, modifier: Modifier = Modifier, tone: Color? = null) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (tone != null) tone.copy(alpha = 0.14f) else AppDs.SurfaceRaised)
            .border(1.dp, if (tone != null) tone.copy(alpha = 0.30f) else AppDs.Border, shape)
            .padding(horizontal = AppDs.S2, vertical = 3.dp),
    ) {
        Text(
            text.uppercase(),
            fontSize = AppType.Caption.first,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = tone ?: AppDs.TextMid,
        )
    }
}

/** The plan pill — amber for Pro, neutral for Free. Driven by [AccountUiState.plan]. */
@Composable
internal fun PlanBadge(plan: PlanTier, modifier: Modifier = Modifier) {
    StatusBadge(
        text = plan.label,
        modifier = modifier,
        tone = if (plan == PlanTier.PRO) AppDs.Warning else null,
    )
}

/** Initials in a matte disc with a thin accent ring — one composable, so every size matches. */
@Composable
internal fun Avatar(size: Dp, initials: String, initialsSize: TextUnit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(AppDs.SurfaceRaised)
            .border(1.5.dp, AppDs.Accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontSize = initialsSize, fontWeight = FontWeight.Bold, color = AppDs.Accent)
    }
}

/** Short one-liner next to the plan badge. Honest about the no-billing state. */
internal fun accountStatusShort(account: AccountUiState): String = when (val s = account.subscription) {
    is SubscriptionState.Active -> "Expires in ${s.daysRemaining} days"
    SubscriptionState.None -> if (account.isPro) "Subscription active" else "Free plan"
}

/** Profile's header: avatar, name, email in one card — the same container language as Home's. */
@Composable
internal fun ProfileHeader(account: AccountUiState, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(size = 64.dp, initials = account.initials, initialsSize = AppType.Title.first)
            Spacer(Modifier.width(AppDs.S4))
            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName,
                    fontSize = AppType.Title.first,
                    fontWeight = AppType.Title.second,
                    color = AppDs.TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppDs.S1))
                Text(
                    account.email,
                    fontSize = AppType.Caption.first,
                    fontWeight = AppType.Caption.second,
                    color = AppDs.TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The account card at the top of Settings: avatar, name, plan, chevron. Opens Profile. */
@Composable
internal fun AccountSummaryCard(account: AccountUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(size = 48.dp, initials = account.initials, initialsSize = AppType.Subtitle.first)
            Spacer(Modifier.width(AppDs.S3))
            Column(Modifier.weight(1f)) {
                Text(
                    account.displayName,
                    fontSize = AppType.Subtitle.first,
                    fontWeight = FontWeight.Bold,
                    color = AppDs.TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppDs.S1))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppDs.S2)) {
                    PlanBadge(account.plan)
                    Text(
                        accountStatusShort(account),
                        fontSize = AppType.Caption.first,
                        fontWeight = AppType.Caption.second,
                        color = AppDs.TextMid,
                    )
                }
            }
            RowChevron()
        }
    }
}
