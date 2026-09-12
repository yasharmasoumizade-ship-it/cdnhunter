package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect

private val BgDark = AppColors.BgDark
private val FieldBg = AppColors.FieldBg
private val FieldBorder = AppColors.FieldBorder
private val Accent = AppColors.Accent
private val TealAccent = AppColors.Accent
private val TextHi = AppColors.TextHi
private val TextMid = AppColors.TextMid
private val ErrorRed = AppColors.ErrorRed
private val SuccessGreen = AppColors.SuccessGreen

enum class AuthMode { LOGIN, SIGNUP }
private enum class AuthStep { FORM, VERIFY, SUCCESS }

/** Turns Firebase's raw exception messages into a short, human-readable string.
 *
 *  Two security properties on top of the friendliness:
 *   - Login failures are made *indistinguishable*: a wrong password, a non-existent
 *     account, and a malformed/expired credential all resolve to the same "Incorrect
 *     email or password." so a caller can't probe which emails are registered
 *     (account enumeration). Signup's "email already in use" is unavoidable there and
 *     kept, matching platform behaviour.
 *   - Unmapped errors are treated as opaque and NEVER echoed back: raw Firebase/GMS
 *     strings can carry internal endpoints, HTML error pages, or backend detail, so the
 *     fallback is a generic message rather than the original text.
 */
private fun friendlyAuthError(raw: String?): String {
    if (raw == null) return "Something went wrong. Please try again."
    val r = raw.lowercase()
    return when {
        "network" in r || "timeout" in r || "unable to resolve" in r ->
            "Network error. Check your connection and try again."
        "password is invalid" in r || "wrong-password" in r || "invalid-credential" in r ||
            "no user record" in r || "user-not-found" in r ||
            "invalid login credentials" in r || "invalid_login_credentials" in r ->
            "Incorrect email or password."
        "email address is already in use" in r || "email-already-in-use" in r ->
            "An account already exists with that email."
        "badly formatted" in r || "invalid-email" in r ->
            "Please enter a valid email address."
        "password should be at least" in r || "weak-password" in r ->
            "Password should be at least 6 characters."
        "too many" in r ->
            "Too many attempts. Please wait and try again."
        else -> "Something went wrong. Please try again."
    }
}

private fun isValidEmail(email: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

@Composable
internal fun FullScreenLoopVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse(
                "android.resource://${context.packageName}/${com.cdnhunter.app.R.raw.auth_bg}"
            )
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
    )
}

/**
 * A borderless text field: just an underline that highlights teal on focus, no
 * outlined box. Used for the single-field login flow (email-only, then
 * password-only) where a full bordered field would look heavier than needed.
 */
@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (wasFocused && !isFocused) onFocusLost?.invoke()
        wasFocused = isFocused
    }
    with(Glass) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label, fontSize = 13.sp) },
            singleLine = true,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = Glass.FieldShape, focused = isFocused),
            shape = Glass.FieldShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (onImeAction != null) androidx.compose.ui.text.input.ImeAction.Done
                    else androidx.compose.ui.text.input.ImeAction.Default,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onImeAction?.invoke() },
            ),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            colors = Glass.textFieldColors(),
        )
    }
}

/**
 * Login screen: a single email field first (underline style, no border). Once a
 * valid email is entered and the user continues, the email field collapses into
 * a small pill showing the address (with an Edit affordance) and a password
 * field slides in below it -- avoids showing both fields at once. Google
 * sign-in stays as a secondary option, matching how Onboarding presents it.
 */
@Composable
fun AuthScreen(initialMode: AuthMode = AuthMode.LOGIN, onSignedIn: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(AuthStep.FORM) }
    var mode by remember { mutableStateOf(initialMode) }
    var pendingEmail by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        FullScreenLoopVideo(modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
                ),
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(450)) + scaleIn(initialScale = 0.96f, animationSpec = tween(450)))
                    .togetherWith(fadeOut(tween(250)))
            },
            label = "authStep",
        ) { s ->
            when (s) {
                AuthStep.FORM -> AuthFormContent(
                    mode = mode,
                    onModeChange = { mode = it },
                    onBack = onBack,
                    onSuccess = { justSignedUp, email ->
                        if (justSignedUp) {
                            pendingEmail = email
                            step = AuthStep.VERIFY
                        } else {
                            onSignedIn()
                        }
                    },
                )
                AuthStep.VERIFY -> VerifyEmailContent(
                    email = pendingEmail,
                    onVerified = { step = AuthStep.SUCCESS },
                    onSkip = { step = AuthStep.SUCCESS },
                )
                AuthStep.SUCCESS -> SuccessContent(onContinue = onSignedIn)
            }
        }
    }
}

@Composable
private fun VerifyEmailContent(email: String, onVerified: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resendMessage by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Check your email", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextHi)
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a 6-digit code to $email",
                fontSize = 13.sp, color = TextMid, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 24.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.width(200.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(.6f),
                    unfocusedBorderColor = FieldBorder,
                    focusedTextColor = TextHi,
                    unfocusedTextColor = TextHi.copy(.85f),
                    cursorColor = Accent,
                    focusedContainerColor = FieldBg,
                    unfocusedContainerColor = FieldBg,
                ),
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center)
            }
            resendMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = TextMid, fontSize = 11.5.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    error = null
                    loading = true
                    coroutineScope.launch {
                        when (val outcome = com.cdnhunter.app.vpn.ThalloAuthClient.verifyEmail(email, code)) {
                            is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Success -> {
                                com.cdnhunter.app.vpn.ThalloAuthClient.markEmailVerified(context)
                                onVerified()
                            }
                            is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Failure -> {
                                error = outcome.message
                                loading = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                enabled = !loading && code.length == 6,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Verify", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row {
                Text("Didn't get a code? ", fontSize = 13.sp, color = TextMid)
                Text(
                    if (resending) "Sending..." else "Resend",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !resending) {
                        resending = true
                        resendMessage = null
                        coroutineScope.launch {
                            val outcome = com.cdnhunter.app.vpn.ThalloAuthClient.resendVerificationCode(email)
                            resending = false
                            resendMessage = when (outcome) {
                                is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Success -> "A new code was sent."
                                is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Failure -> outcome.message
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Skip for now",
                fontSize = 12.sp, color = TextMid.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onSkip() },
            )
        }
    }
}

@Composable
private fun SuccessContent(onContinue: () -> Unit) {
    var checkVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        checkVisible = true
        delay(1400)
        onContinue()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = checkVisible,
                enter = scaleIn(initialScale = 0.3f, animationSpec = tween(500, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
            ) {
                Box(Modifier.size(88.dp).background(SuccessGreen.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).background(SuccessGreen, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = checkVisible, enter = fadeIn(tween(400, delayMillis = 200))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Successful!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(6.dp))
                    Text("Your account is ready to go.", fontSize = 13.sp, color = TextMid)
                }
            }
        }
    }
}

/**
 * Wraps [UnderlineField] with pass/fail validation feedback: a green check or red
 * X trailing icon appears once the field loses focus, based on [validator]. Used
 * for the step-by-step Sign Up flow so each field confirms itself before the
 * user moves to the next one.
 */
@Composable
private fun ValidatedField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    validator: (String) -> Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showToggle: Boolean = false,
    toggleVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
) {
    var touched by remember { mutableStateOf(false) }
    val isValid = value.isNotEmpty() && validator(value)
    val showResult = touched && value.isNotEmpty()

    UnderlineField(
        label = label,
        value = value,
        onValue = { onValue(it); touched = false },
        keyboardType = keyboardType,
        visualTransformation = visualTransformation,
        onImeAction = onImeAction,
        onFocusLost = { if (value.isNotEmpty()) touched = true },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showResult) {
                    Icon(
                        if (isValid) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isValid) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                    if (showToggle) Spacer(Modifier.width(4.dp))
                }
                if (showToggle) {
                    IconButton(onClick = { onToggleVisible?.invoke() }) {
                        Icon(
                            if (toggleVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TextMid,
                        )
                    }
                }
            }
        },
    )
}

private fun AuthFormContent(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    onBack: (() -> Unit)?,
    onSuccess: (justSignedUp: Boolean, email: String) -> Unit,
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailConfirmed by remember { mutableStateOf(false) }
    var signupStep by remember { mutableStateOf(SignupStep.USERNAME) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val submitLogin: () -> Unit = {
        error = null
        if (password.isBlank()) {
            error = "Please enter your password."
        } else {
            loading = true
            coroutineScope.launch {
                val outcome = com.cdnhunter.app.vpn.ThalloAuthClient.logIn(email.trim(), password)
                when (outcome) {
                    is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Success -> {
                        com.cdnhunter.app.vpn.ThalloAuthClient.saveSession(context, outcome.result)
                        onSuccess(false, email.trim())
                    }
                    is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Failure -> {
                        error = outcome.message
                        loading = false
                    }
                }
            }
        }
    }

    val submitSignup: () -> Unit = {
        error = null
        when {
            username.isBlank() -> error = "Please choose a username."
            email.isBlank() || !isValidEmail(email) -> error = "Please enter a valid email address."
            password.isBlank() -> error = "Please enter a password."
            password != confirmPassword -> error = "Passwords don't match."
            else -> {
                loading = true
                coroutineScope.launch {
                    val outcome = com.cdnhunter.app.vpn.ThalloAuthClient.signUp(email.trim(), password, username.trim())
                    when (outcome) {
                        is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Success -> {
                            com.cdnhunter.app.vpn.ThalloAuthClient.saveSession(context, outcome.result)
                            onSuccess(true, email.trim())
                        }
                        is com.cdnhunter.app.vpn.ThalloAuthClient.AuthOutcome.Failure -> {
                            error = outcome.message
                            loading = false
                        }
                    }
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(600, easing = EaseOutCubic)) { it / 5 },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
                .padding(top = 90.dp, bottom = 28.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextHi)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (mode == AuthMode.LOGIN) "Sign In" else "Join Sector 51",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextHi,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (mode == AuthMode.LOGIN) "Access granted only to the cleared."
                else "Get clearance. Access begins here.",
                fontSize = 13.sp, color = TextMid,
            )

            Spacer(Modifier.height(30.dp))

            if (mode == AuthMode.LOGIN) {
                // --- Single-field login flow: email first, then password replaces it ---
                AnimatedContent(
                    targetState = emailConfirmed,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 })
                            .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
                    },
                    label = "emailToPassword",
                ) { confirmed ->
                    if (!confirmed) {
                        Column {
                            UnderlineField(
                                label = "Email",
                                value = email,
                                onValue = { email = it; error = null },
                                keyboardType = KeyboardType.Email,
                                onImeAction = {
                                    if (isValidEmail(email)) emailConfirmed = true
                                    else error = "Please enter a valid email address."
                                },
                            )
                            error?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(22.dp))
                            Button(
                                onClick = {
                                    if (isValidEmail(email)) emailConfirmed = true
                                    else error = "Please enter a valid email address."
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            ) {
                                Text("Continue", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    } else {
                        Column {
                            Row(
                                with(Glass) {
                                    Modifier
                                        .fillMaxWidth()
                                        .glassSurface()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(email, fontSize = 14.sp, color = TextHi.copy(.85f))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        emailConfirmed = false
                                        password = ""
                                        error = null
                                    },
                                ) {
                                    Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            UnderlineField(
                                label = "Password",
                                value = password,
                                onValue = { password = it; error = null },
                                keyboardType = KeyboardType.Password,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton({ passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            null, tint = TextMid,
                                        )
                                    }
                                },
                                onImeAction = submitLogin,
                            )
                            error?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(22.dp))
                            Button(
                                onClick = submitLogin,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                enabled = !loading,
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Sign In", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Spacer(Modifier.height(28.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("Don't have an account? ", fontSize = 13.sp, color = TextMid)
                    Text(
                        "Sign Up",
                        fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            error = null
                            onModeChange(AuthMode.SIGNUP)
                        },
                    )
                }
            } else {
                // --- Sign Up: one field confirmed at a time (Username -> Email -> Password) ---
                AnimatedContent(
                    targetState = signupStep,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 })
                            .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
                    },
                    label = "signupStep",
                ) { stepNow ->
                    Column {
                        if (stepNow > SignupStep.USERNAME) {
                            ConfirmedFieldRow(label = "Username", value = username) {
                                signupStep = SignupStep.USERNAME
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        if (stepNow > SignupStep.EMAIL) {
                            ConfirmedFieldRow(label = "Email", value = email) {
                                signupStep = SignupStep.EMAIL
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        when (stepNow) {
                            SignupStep.USERNAME -> {
                                ValidatedField(
                                    label = "Username",
                                    value = username,
                                    onValue = { username = it; error = null },
                                    validator = { it.trim().length >= 3 },
                                    onImeAction = {
                                        if (username.trim().length >= 3) signupStep = SignupStep.EMAIL
                                        else error = "Username must be at least 3 characters."
                                    },
                                )
                                error?.let {
                                    Spacer(Modifier.height(10.dp))
                                    Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(22.dp))
                                Button(
                                    onClick = {
                                        if (username.trim().length >= 3) { error = null; signupStep = SignupStep.EMAIL }
                                        else error = "Username must be at least 3 characters."
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                ) {
                                    Text("Continue", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                            SignupStep.EMAIL -> {
                                ValidatedField(
                                    label = "Email",
                                    value = email,
                                    onValue = { email = it; error = null },
                                    validator = ::isValidEmail,
                                    keyboardType = KeyboardType.Email,
                                    onImeAction = {
                                        if (isValidEmail(email)) signupStep = SignupStep.PASSWORD
                                        else error = "Please enter a valid email address."
                                    },
                                )
                                error?.let {
                                    Spacer(Modifier.height(10.dp))
                                    Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(22.dp))
                                Button(
                                    onClick = {
                                        if (isValidEmail(email)) { error = null; signupStep = SignupStep.PASSWORD }
                                        else error = "Please enter a valid email address."
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                ) {
                                    Text("Continue", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                            SignupStep.PASSWORD -> {
                                ValidatedField(
                                    label = "Password",
                                    value = password,
                                    onValue = { password = it; error = null },
                                    validator = { it.length >= 6 },
                                    keyboardType = KeyboardType.Password,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    showToggle = true,
                                    toggleVisible = passwordVisible,
                                    onToggleVisible = { passwordVisible = !passwordVisible },
                                )
                                Spacer(Modifier.height(14.dp))
                                ValidatedField(
                                    label = "Confirm Password",
                                    value = confirmPassword,
                                    onValue = { confirmPassword = it; error = null },
                                    validator = { it.length >= 6 && it == password },
                                    keyboardType = KeyboardType.Password,
                                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    showToggle = true,
                                    toggleVisible = confirmPasswordVisible,
                                    onToggleVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                                    onImeAction = submitSignup,
                                )

                                error?.let {
                                    Spacer(Modifier.height(14.dp))
                                    Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }

                                Spacer(Modifier.height(22.dp))

                                Button(
                                    onClick = submitSignup,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    enabled = !loading,
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Sign Up", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Spacer(Modifier.height(28.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("Already have an account? ", fontSize = 13.sp, color = TextMid)
                    Text(
                        "Sign In",
                        fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            error = null
                            onModeChange(AuthMode.LOGIN)
                        },
                    )
                }
            }
        }
    }
}

private enum class SignupStep { USERNAME, EMAIL, PASSWORD }

@Composable
private fun ConfirmedFieldRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        with(Glass) {
            Modifier
                .fillMaxWidth()
                .glassSurface()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, fontSize = 11.sp, color = TextMid)
            Text(value, fontSize = 14.sp, color = TextHi.copy(.85f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onEdit() },
        ) {
            Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Edit", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

