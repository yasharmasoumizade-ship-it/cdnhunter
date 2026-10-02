package com.cdnhunter.app.ui

// ─────────────────────────────────────────────────────────────────────────────
// Presentation kit for the authentication flow (landing, log in, sign up, verify).
//
// Pure UI: nothing here knows about Firebase, Groomx or validation rules — screens pass state and
// callbacks in. It sits on the app's own tokens (Manrope, the 4dp [AppDs] spacing grid) and adds
// only what auth needs on top of a photographic backdrop.
//
// Rules the components follow, so screens never restyle anything:
//   Shape     actions (buttons, back) are full pills; containers (fields, banners, code cells) are 16dp.
//   Colour    white at four strengths on the scrim (Hi / Mid / Low / Edge); primary action = the
//             app's Bone fill, focus = the app accent, everything else = glass.
//   Type      [AuthType] — one style per role; a size always implies its weight.
//   State     every control has rest / focus / pressed / disabled / loading / error where it applies.
//   Feedback  errors say what happened and what to do next (see [AuthIssue]); a red edge is never
//             the only signal. Contrast: Low text is >= 4.5:1 over the scrim.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay

internal object AuthDs {
    // One system with the app: the primary action is the same Bone-on-Ink as Home's connect button,
    // focus is the app's accent, success and error come from AppDs. Only what must survive a
    // photographic backdrop (the white text ramp, the lighter error text) is auth-specific.
    val Ink = AppDs.Ink
    val Primary = AppDs.Bone
    val Hi = Color.White
    val Mid = Color.White.copy(alpha = 0.78f)
    val Low = Color.White.copy(alpha = 0.60f)

    val GlassFill = Color.White.copy(alpha = 0.08f)
    val GlassFillFocus = Color.White.copy(alpha = 0.12f)
    val Edge = Color.White.copy(alpha = 0.18f)
    val EdgeFilled = Color.White.copy(alpha = 0.38f)
    val EdgeFocus = AppDs.Accent

    // Text-sized red needs more lightness than AppDs.Error to hold 4.5:1 on the scrim.
    val Error = Color(0xFFFF7B7B)
    val ErrorFill = AppDs.Error.copy(alpha = 0.14f)
    val ErrorEdge = AppDs.Error
    val Success = AppDs.Success

    val Gutter = AppDs.S6
    val ButtonHeight = 56.dp
    val FieldHeight = 56.dp
    val CodeCellHeight = 60.dp
    val MinTouch = 48.dp
    val ContainerShape = RoundedCornerShape(AppDs.RMd)
}

/** The auth type scale. Manrope throughout; weight is fixed per role. */
internal object AuthType {
    val Hero = TextStyle(fontFamily = AppFont, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp)
    val Display = TextStyle(fontFamily = AppFont, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    val Body = TextStyle(fontFamily = AppFont, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val Label = TextStyle(fontFamily = AppFont, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
    val Input = TextStyle(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val Button = TextStyle(fontFamily = AppFont, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontFamily = AppFont, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val Notice = TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val Code = TextStyle(fontFamily = AppFont, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

// ── Issues ───────────────────────────────────────────────────────────────────

/** Which field an [AuthIssue] belongs to, so that field can draw its error edge. */
internal enum class AuthFieldId { USERNAME, EMAIL, PASSWORD, CONFIRM, CODE }

/** What the banner's button does. The screen owns the behaviour; the kit only renders the label. */
internal enum class AuthIssueAction { NONE, RETRY, LOG_IN, RESEND }

/**
 * An error the person can act on: what happened ([message]), what to do about it ([hint]), the
 * field to mark ([field]) and an optional one-tap fix ([action] + [actionLabel]).
 */
internal data class AuthIssue(
    val message: String,
    val hint: String? = null,
    val field: AuthFieldId? = null,
    val action: AuthIssueAction = AuthIssueAction.NONE,
    val actionLabel: String? = null,
)

// ── Backdrop ─────────────────────────────────────────────────────────────────

/**
 * The full-screen visual under every auth screen plus the dark scrim that keeps type legible.
 * The visual is [FullScreenLoopVideo] (res/raw/auth_bg.mp4) — swap that one call to use a still
 * image or another clip. [hazeState] is the blur source the glass controls sample; [dim] adds a
 * uniform darkening for screens that carry type near the top (the step screens).
 */
@Composable
internal fun AuthBackdrop(hazeState: HazeState, modifier: Modifier = Modifier, dim: Float = 0f) {
    Box(modifier.fillMaxSize().background(AppColors.BgDark)) {
        FullScreenLoopVideo(modifier = Modifier.fillMaxSize(), hazeState = hazeState)
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.34f),
                        0.30f to Color.Black.copy(alpha = 0.16f),
                        0.58f to Color.Black.copy(alpha = 0.58f),
                        1.00f to Color.Black.copy(alpha = 0.94f),
                    ),
                ),
        )
    }
}

// ── Glass ────────────────────────────────────────────────────────────────────

/** Translucent dark glass: a light blur of whatever [hazeState] sees, a faint fill, a hairline edge. */
internal fun Modifier.authGlass(
    shape: Shape,
    hazeState: HazeState?,
    fill: Color = AuthDs.GlassFill,
    edge: Color = AuthDs.Edge,
): Modifier = this
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = Color(0xFF0A0B0F),
                    tints = listOf(HazeTint(Color.Black.copy(alpha = 0.20f))),
                    blurRadius = 20.dp,
                    noiseFactor = 0.04f,
                ),
            )
        } else {
            Modifier
        },
    )
    .background(fill)
    .border(1.dp, edge, shape)

// ── Buttons ──────────────────────────────────────────────────────────────────

internal enum class AuthButtonStyle { Primary, Glass }

/**
 * A full-width pill. [AuthButtonStyle.Primary] is solid Bone with Ink text — the one action on a
 * screen; [AuthButtonStyle.Glass] is translucent with a hairline — everything secondary. Pressed
 * sinks and dims, keyboard focus draws a ring, [loading] swaps the label for a spinner and blocks
 * taps (the button keeps its size, so nothing jumps), disabled fades to half.
 */
@Composable
internal fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AuthButtonStyle = AuthButtonStyle.Glass,
    loading: Boolean = false,
    enabled: Boolean = true,
    hazeState: HazeState? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale = animatePressScale(pressed)
    val shape = RoundedCornerShape(50)
    val primary = style == AuthButtonStyle.Primary
    val fill by animateColorAsState(
        when {
            primary && pressed -> AuthDs.Primary.copy(alpha = 0.86f)
            primary -> AuthDs.Primary
            pressed -> Color.White.copy(alpha = 0.18f)
            else -> AuthDs.GlassFill
        },
        tween(110),
        label = "authBtnFill",
    )
    val edge = when {
        focused -> AuthDs.EdgeFocus
        primary -> Color.Transparent
        else -> AuthDs.Edge
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = AuthDs.ButtonHeight)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (primary) {
                    Modifier.clip(shape).background(fill).border(1.dp, edge, shape)
                } else {
                    Modifier.authGlass(shape, hazeState, fill, edge)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) contentDescription = "$text, in progress" }
            .padding(horizontal = AppDs.S6),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDs.S3),
        ) {
            if (loading) {
                GlowSpinner(size = 20.dp)
            } else {
                leading?.invoke()
                Text(text, style = AuthType.Button, color = if (primary) AuthDs.Ink else AuthDs.Hi, maxLines = 1)
            }
        }
    }
}

/** "──── or ────" */
@Composable
internal fun AuthOrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(AuthDs.Edge))
        Text("or", style = AuthType.Caption, color = AuthDs.Low, modifier = Modifier.padding(horizontal = AppDs.S4))
        Box(Modifier.weight(1f).height(1.dp).background(AuthDs.Edge))
    }
}

/** "Already have an account?  Log In" — the action is a full 48dp-tall tap target. */
@Composable
internal fun AuthLinkRow(prefix: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, style = AuthType.Caption.copy(fontSize = 14.sp), color = AuthDs.Mid)
        Text(
            action,
            style = AuthType.Notice,
            color = AuthDs.Hi,
            modifier = Modifier
                .clip(RoundedCornerShape(AppDs.S3))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = AuthDs.MinTouch)
                .padding(horizontal = AppDs.S2)
                .padding(vertical = 14.dp),
        )
    }
}

// ── Feedback ─────────────────────────────────────────────────────────────────

/**
 * The error surface: an icon, what happened, what to do next and — when there is a one-tap fix — a
 * button for it. It grows in and out (no layout jump) and is an assertive live region, so a screen
 * reader announces it the moment it appears. [issue] = null hides it.
 */
@Composable
internal fun AuthBanner(
    issue: AuthIssue?,
    onAction: (AuthIssueAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableStateOf(issue) }
    if (issue != null) last = issue

    AnimatedVisibility(
        visible = issue != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
    ) {
        val shown = last
        if (shown != null) {
            Row(
                Modifier
                    .padding(top = AppDs.S4)
                    .fillMaxWidth()
                    .clip(AuthDs.ContainerShape)
                    .background(AuthDs.ErrorFill)
                    .border(1.dp, AuthDs.ErrorEdge.copy(alpha = 0.40f), AuthDs.ContainerShape)
                    .padding(AppDs.S4)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                horizontalArrangement = Arrangement.spacedBy(AppDs.S3),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = AuthDs.Error,
                    modifier = Modifier.padding(top = 1.dp).size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(shown.message, style = AuthType.Notice, color = AuthDs.Hi)
                    if (shown.hint != null) {
                        Text(shown.hint, style = AuthType.Caption, color = AuthDs.Mid, modifier = Modifier.padding(top = AppDs.S1))
                    }
                    if (shown.action != AuthIssueAction.NONE && shown.actionLabel != null) {
                        Text(
                            shown.actionLabel,
                            style = AuthType.Notice,
                            color = AuthDs.Hi,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = AppDs.S1)
                                .clip(RoundedCornerShape(AppDs.S2))
                                .clickable(role = Role.Button) { onAction(shown.action) }
                                .heightIn(min = AuthDs.MinTouch)
                                .padding(vertical = AppDs.S3),
                        )
                    }
                }
            }
        }
    }
}

// ── Inputs ───────────────────────────────────────────────────────────────────

/**
 * A labelled rounded glass input. The label is always visible (a placeholder alone disappears the
 * moment you type, and screen readers lose it); the edge brightens on focus and turns red on
 * [isError]; [trailing] sits inside the field on the right. [supportingText] sits under it — a hint
 * normally, an explanation when [supportingIsError]. [autoFocus] opens the keyboard once the page
 * has finished sliding in.
 */
@Composable
internal fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    hazeState: HazeState? = null,
    supportingText: String? = null,
    supportingIsError: Boolean = false,
    onImeAction: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (wasFocused && !focused) onFocusLost?.invoke()
        wasFocused = focused
    }

    val requester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(380)
            runCatching { requester.requestFocus() }
        }
    }
    val focusManager = LocalFocusManager.current
    val showError = isError || supportingIsError

    val edge by animateColorAsState(
        when {
            showError -> AuthDs.ErrorEdge
            focused -> AuthDs.EdgeFocus
            else -> AuthDs.Edge
        },
        tween(180),
        label = "authFieldEdge",
    )
    val fill by animateColorAsState(
        if (focused) AuthDs.GlassFillFocus else AuthDs.GlassFill,
        tween(180),
        label = "authFieldFill",
    )

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(tween(180))
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = AuthType.Label, color = AuthDs.Mid, modifier = Modifier.padding(start = AppDs.S1, bottom = AppDs.S2))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.6f)
                .authGlass(AuthDs.ContainerShape, hazeState, fill, edge)
                .focusRequester(requester)
                .semantics { if (supportingIsError && supportingText != null) error(supportingText) },
            singleLine = true,
            textStyle = AuthType.Input.copy(color = AuthDs.Hi),
            cursorBrush = SolidColor(AuthDs.Primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (onImeAction != null) onImeAction() else focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
            ),
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = AuthDs.FieldHeight)
                        .padding(start = AppDs.S5, end = if (trailing != null) AppDs.S2 else AppDs.S5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = AuthType.Input, color = AuthDs.Low, maxLines = 1)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (supportingText != null) {
            Text(
                supportingText,
                style = AuthType.Caption,
                color = if (supportingIsError) AuthDs.Error else AuthDs.Low,
                modifier = Modifier.padding(start = AppDs.S1, top = AppDs.S2),
            )
        }
    }
}

/** Eye / eye-off toggle for a password field's trailing slot — a 48dp target. */
@Composable
internal fun AuthPasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = AuthDs.Mid,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * [AuthField] that judges itself once the person has left it: a tick or a cross, a red edge and
 * [invalidMessage] when invalid, [hint] otherwise. Typing clears the verdict, so it never nags
 * mid-word. [forceError] lets the screen mark the field for a server-side problem.
 */
@Composable
internal fun AuthValidatedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    validator: (String) -> Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showToggle: Boolean = false,
    toggleVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    enabled: Boolean = true,
    forceError: Boolean = false,
    hint: String? = null,
    invalidMessage: String? = null,
    hazeState: HazeState? = null,
    onImeAction: (() -> Unit)? = null,
) {
    var touched by remember { mutableStateOf(false) }
    val isValid = value.isNotEmpty() && validator(value)
    val showResult = touched && value.isNotEmpty()
    val invalid = showResult && !isValid

    val trailingContent: (@Composable () -> Unit)? = if (showResult || showToggle) {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showResult) {
                    Icon(
                        if (isValid) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isValid) "Valid" else "Invalid",
                        tint = if (isValid) AuthDs.Success else AuthDs.Error,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!showToggle) Spacer(Modifier.width(AppDs.S3))
                }
                if (showToggle) {
                    AuthPasswordToggle(visible = toggleVisible, onToggle = { onToggleVisible?.invoke() })
                }
            }
        }
    } else {
        null
    }

    AuthField(
        label = label,
        value = value,
        onValueChange = { onValueChange(it); touched = false },
        modifier = modifier,
        placeholder = placeholder,
        keyboardType = keyboardType,
        imeAction = imeAction,
        visualTransformation = visualTransformation,
        isError = forceError,
        enabled = enabled,
        autoFocus = autoFocus,
        hazeState = hazeState,
        supportingText = if (invalid && invalidMessage != null) invalidMessage else hint,
        supportingIsError = invalid && invalidMessage != null,
        onImeAction = onImeAction,
        onFocusLost = { if (value.isNotEmpty()) touched = true },
        trailing = trailingContent,
    )
}

/**
 * A one-time-code input: [length] cells over one hidden text field, so the system keyboard, paste
 * and "fill from message" all just work. Non-digits are dropped (pasting "123 456" gives 123456),
 * [onComplete] fires when the last digit lands, and bumping [shakeKey] shakes the cells to say the
 * code was wrong (skipped when the user has animations off).
 */
@Composable
internal fun AuthOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    shakeKey: Int = 0,
    onComplete: () -> Unit = {},
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(380)
        runCatching { requester.requestFocus() }
    }
    LaunchedEffect(value) { if (value.length == length) onComplete() }

    val reduce = appReduceMotion()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0 && !reduce) {
            for (x in listOf(-10f, 10f, -6f, 6f, 0f)) shake.animateTo(x, tween(55))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(length)
            if (digits != value) onValueChange(digits)
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .semantics { contentDescription = "Verification code, $length digits" },
        singleLine = true,
        textStyle = AuthType.Code.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box {
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value },
                    horizontalArrangement = Arrangement.spacedBy(AppDs.S2),
                ) {
                    repeat(length) { i ->
                        val char = value.getOrNull(i)
                        val active = focused && enabled && i == value.length.coerceAtMost(length - 1)
                        val edge by animateColorAsState(
                            when {
                                isError -> AuthDs.ErrorEdge
                                active -> AuthDs.EdgeFocus
                                char != null -> AuthDs.EdgeFilled
                                else -> AuthDs.Edge
                            },
                            tween(140),
                            label = "otpEdge$i",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(AuthDs.CodeCellHeight)
                                .clip(AuthDs.ContainerShape)
                                .background(if (active) AuthDs.GlassFillFocus else AuthDs.GlassFill)
                                .border(1.dp, edge, AuthDs.ContainerShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(char?.toString() ?: "", style = AuthType.Code, color = AuthDs.Hi)
                        }
                    }
                }
                // The real field must be composed exactly once; it stays invisible behind the cells.
                Box(Modifier.size(1.dp).alpha(0f)) { inner() }
            }
        },
    )
}

// ── Step chrome ──────────────────────────────────────────────────────────────

/**
 * The top row of a step screen: a round glass back button on the left, a thin segmented progress
 * bar in the middle ([steps] = 0 hides it). The right side is a spacer the width of the button so
 * the bar stays centred.
 */
@Composable
internal fun AuthTopBar(
    onBack: (() -> Unit)?,
    step: Int,
    steps: Int,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = AuthDs.Gutter, vertical = AppDs.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(AuthDs.MinTouch)
                    .authGlass(CircleShape, hazeState)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = AuthDs.Hi,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(AuthDs.MinTouch))
        }
        if (steps > 0) {
            AuthProgress(step = step, steps = steps, modifier = Modifier.weight(1f).padding(horizontal = AppDs.S5))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.size(AuthDs.MinTouch))
    }
}

/** [steps] hairline segments; the first [step] are filled, each fill animating on its own. */
@Composable
internal fun AuthProgress(step: Int, steps: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Step $step of $steps" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(if (index < step) 1f else 0f, tween(420), label = "authProgress$index")
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AuthDs.Edge),
            ) {
                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(AuthDs.Primary))
            }
        }
    }
}

/**
 * One step: a heading and subtitle, then [content] in a scrollable column (so a short phone or an
 * open keyboard never hides a field), with [bottom] pinned under it. The parent supplies the
 * insets (status bar, navigation bar, keyboard) around the whole step.
 */
@Composable
internal fun AuthPage(
    title: String,
    subtitle: String,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuthDs.Gutter)
                .padding(top = AppDs.S6),
        ) {
            Text(title, style = AuthType.Display, color = AuthDs.Hi, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(AppDs.S3))
            Text(subtitle, style = AuthType.Body, color = AuthDs.Mid)
            Spacer(Modifier.height(AppDs.S7))
            content()
            Spacer(Modifier.height(AppDs.S4))
        }
        Column(
            Modifier.padding(horizontal = AuthDs.Gutter).padding(top = AppDs.S3, bottom = AppDs.S3),
            content = bottom,
        )
    }
}
