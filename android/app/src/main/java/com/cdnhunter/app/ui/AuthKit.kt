package com.cdnhunter.app.ui

// ─────────────────────────────────────────────────────────────────────────────
// Presentation kit for the authentication flow (landing, log in, sign up).
//
// Pure UI: nothing here knows about Firebase, Groomx or validation rules — the screens
// pass state and callbacks in. One visual language: a full-bleed cinematic backdrop under a
// dark scrim, translucent glass controls with a hairline edge, pill buttons, large type.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.delay

internal object AuthDs {
    val Ink = Color(0xFF0B0B0E)
    val Hi = Color.White
    val Mid = Color.White.copy(alpha = 0.66f)
    val Low = Color.White.copy(alpha = 0.44f)

    val GlassFill = Color.White.copy(alpha = 0.08f)
    val GlassFillFocus = Color.White.copy(alpha = 0.13f)
    val Edge = Color.White.copy(alpha = 0.16f)
    val EdgeFocus = Color.White.copy(alpha = 0.55f)

    val Error = Color(0xFFFF6B6B)
    val ErrorEdge = Error.copy(alpha = 0.85f)
    val Success = Color(0xFF4ADE80)

    val Gutter = 24.dp
    val ButtonHeight = 56.dp
    val FieldHeight = 58.dp
    val FieldShape = RoundedCornerShape(18.dp)
}

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
 * A full-width pill. [AuthButtonStyle.Primary] is solid white with dark text; [AuthButtonStyle.Glass]
 * is translucent with a hairline. Pressed sinks and dims, focus draws a ring, [loading] swaps the
 * label for a spinner and blocks taps (the button keeps its size, so nothing jumps).
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
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "authBtnScale")
    val shape = RoundedCornerShape(50)
    val primary = style == AuthButtonStyle.Primary
    val fill by animateColorAsState(
        when {
            primary && pressed -> Color.White.copy(alpha = 0.86f)
            primary -> Color.White
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
            .semantics { contentDescription = text }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                GlowSpinner(size = 20.dp)
            } else {
                leading?.invoke()
                Text(
                    text,
                    color = if (primary) AuthDs.Ink else AuthDs.Hi,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "──── or ────" */
@Composable
internal fun AuthOrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(AuthDs.Edge))
        Text("or", color = AuthDs.Low, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f).height(1.dp).background(AuthDs.Edge))
    }
}

/** "Already have an account?  Log In" — the action is a real 48dp-tall tap target. */
@Composable
internal fun AuthLinkRow(prefix: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, color = AuthDs.Mid, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            action,
            color = AuthDs.Hi,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 14.dp),
        )
    }
}

// ── Inputs ───────────────────────────────────────────────────────────────────

/**
 * A rounded glass input. The edge brightens on focus and turns red on [isError]; [trailing] sits
 * inside the field on the right (validation tick, show/hide). [autoFocus] opens the keyboard once
 * the page has finished sliding in.
 */
@Composable
internal fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    autoFocus: Boolean = false,
    hazeState: HazeState? = null,
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

    val edge by animateColorAsState(
        when {
            isError -> AuthDs.ErrorEdge
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

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .authGlass(AuthDs.FieldShape, hazeState, fill, edge)
            .focusRequester(requester),
        singleLine = true,
        textStyle = TextStyle(color = AuthDs.Hi, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Color.White),
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
                    .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = AuthDs.Low, fontSize = 16.sp, maxLines = 1)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** Eye / eye-off toggle for a password field's [AuthField.trailing]. */
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
 * [AuthField] that reports validity once the person has left it: a green tick or a red cross, and
 * a red edge when invalid. Typing clears the verdict again, so it never nags mid-word.
 */
@Composable
internal fun AuthValidatedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    validator: (String) -> Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showToggle: Boolean = false,
    toggleVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    hazeState: HazeState? = null,
    onImeAction: (() -> Unit)? = null,
) {
    var touched by remember { mutableStateOf(false) }
    val isValid = value.isNotEmpty() && validator(value)
    val showResult = touched && value.isNotEmpty()

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
                    if (!showToggle) Spacer(Modifier.width(12.dp))
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
        value = value,
        onValueChange = { onValueChange(it); touched = false },
        placeholder = placeholder,
        modifier = modifier,
        keyboardType = keyboardType,
        imeAction = imeAction,
        visualTransformation = visualTransformation,
        isError = showResult && !isValid,
        autoFocus = autoFocus,
        hazeState = hazeState,
        onImeAction = onImeAction,
        onFocusLost = { if (value.isNotEmpty()) touched = true },
        trailing = trailingContent,
    )
}

/** An inline error under the fields. Renders nothing for null, so the layout only grows when needed. */
@Composable
internal fun AuthError(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(
            message,
            color = AuthDs.Error,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

// ── Step chrome ──────────────────────────────────────────────────────────────

/**
 * The top row of a step screen: a round glass back button on the left, a thin segmented progress
 * bar in the middle ([steps] = 0 hides it). The right side is a spacer of the button's width so
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
        modifier.fillMaxWidth().padding(horizontal = AuthDs.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
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
            Spacer(Modifier.size(44.dp))
        }
        if (steps > 0) {
            AuthProgress(step = step, steps = steps, modifier = Modifier.weight(1f).padding(horizontal = 20.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.size(44.dp))
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
                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
            }
        }
    }
}

/**
 * One step: a large heading and subtitle, then [content] in a scrollable column (so a short phone
 * or an open keyboard never hides a field), with [bottom] pinned under it. The parent supplies
 * the insets (status bar, navigation bar, keyboard) around the whole step.
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
                .padding(top = 28.dp),
        ) {
            Text(
                title,
                color = AuthDs.Hi,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(subtitle, color = AuthDs.Mid, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(32.dp))
            content()
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier.padding(horizontal = AuthDs.Gutter).padding(top = 12.dp, bottom = 12.dp),
            content = bottom,
        )
    }
}
