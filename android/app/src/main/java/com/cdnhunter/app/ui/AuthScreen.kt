package com.cdnhunter.app.ui

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material.icons.filled.Close
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

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
internal fun FullScreenLoopVideo(modifier: Modifier = Modifier, onReady: (() -> Unit)? = null, hazeState: HazeState? = null) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse(
                "android.resource://${context.packageName}/${com.cdnhunter.app.R.raw.auth_bg}"
            )
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            if (onReady != null) {
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        onReady()
                    }
                })
            }
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = if (hazeState != null) modifier.haze(hazeState) else modifier,
        factory = {
            // Inflated from XML (surface_type="texture_view") rather than built via the
            // PlayerView(context) constructor, because that constructor always creates a
            // SurfaceView-backed player and PlayerView offers no programmatic way to
            // change it -- surface_type is an XML-only attribute. This matters here
            // because Haze can only capture Compose/TextureView content for its blur
            // snapshot; a SurfaceView renders to a separate hardware layer the compositor
            // can't read from, which crashed on launch once hazeState was wired into this
            // video (see PR discussion / Haze docs: "a SurfaceView cannot be captured").
            (android.view.LayoutInflater.from(context)
                .inflate(com.cdnhunter.app.R.layout.player_view_texture, null) as PlayerView)
                .apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
        },
    )
}

/** Shared Haze blur source across a screen: the video sets itself as the blur
 *  source, and any glass card reads this local to sample it, without every
 *  composable in between needing to thread a HazeState parameter through. */
internal val LocalHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun AuthScreen(initialMode: AuthMode = AuthMode.LOGIN, onSignedIn: () -> Unit, onBack: (() -> Unit)? = null) {
    val hazeState = remember { HazeState() }
    var step by remember { mutableStateOf(AuthStep.FORM) }
    var mode by remember { mutableStateOf(initialMode) }
    var pendingEmail by remember { mutableStateOf("") }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            AuthBackdrop(hazeState, dim = 0.34f)

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(200))) },
                label = "authStep",
            ) { s ->
                when (s) {
                    AuthStep.FORM -> AuthFormContent(
                        mode = mode,
                        onModeChange = { mode = it },
                        onBack = onBack,
                        // The 6-digit email-verification step has no real backend behind it --
                        // it's UI-only, not wired to an actual code-send/verify flow -- so signup
                        // now goes straight to SUCCESS like sign-in does. Whether the account's
                        // real email is verified is still tracked and surfaced later, in
                        // EmailVerificationCard on the Profile screen, via Firebase's own
                        // isEmailVerified -- this just removes the fake gate that blocked entry
                        // into the app on a code this screen never actually checked server-side.
                        onSuccess = { _, _ -> onSignedIn() },
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
                        when (val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.verifyEmail(email, code)) {
                            is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> {
                                com.cdnhunter.app.vpn.GroomxAuthClient.markEmailVerified(context)
                                onVerified()
                            }
                            is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure -> {
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
                    GlowSpinner(size = 20.dp)
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
                            val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.resendVerificationCode(email)
                            resending = false
                            resendMessage = when (outcome) {
                                is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> "A new code was sent."
                                is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure -> outcome.message
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

private enum class SignupStep { USERNAME, ACCOUNT }

/** Which page of the flow is on screen. Order matters: it decides slide direction. */
private enum class AuthFormPage { LOGIN, NAME, ACCOUNT }

/**
 * Log in and sign up, as paged steps over the shared backdrop:
 *  - Log in: one page, email + password.
 *  - Sign up: "Let's Get Started" (username), then "Set Up Your Account" (email, password, confirm).
 *
 * The auth calls, validation rules and error strings are the ones the screen always had; only the
 * paging and presentation changed. Back steps through sign-up before leaving the screen.
 */
@Composable
private fun AuthFormContent(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    onBack: (() -> Unit)?,
    onSuccess: (justSignedUp: Boolean, email: String) -> Unit,
) {
    val context = LocalContext.current
    val hazeState = LocalHazeState.current
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var signupStep by remember { mutableStateOf(SignupStep.USERNAME) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val submitLogin: () -> Unit = {
        error = null
        when {
            !isValidEmail(email) -> error = "Please enter a valid email address."
            password.isBlank() -> error = "Please enter your password."
            else -> {
                loading = true
                coroutineScope.launch {
                    val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.logIn(email.trim(), password)
                    when (outcome) {
                        is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> {
                            com.cdnhunter.app.vpn.GroomxAuthClient.saveSession(context, outcome.result)
                            onSuccess(false, email.trim())
                        }
                        is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure -> {
                            error = outcome.message
                            loading = false
                        }
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
                    val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.signUp(email.trim(), password, username.trim())
                    when (outcome) {
                        is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> {
                            com.cdnhunter.app.vpn.GroomxAuthClient.saveSession(context, outcome.result)
                            onSuccess(true, email.trim())
                        }
                        is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure -> {
                            error = outcome.message
                            loading = false
                        }
                    }
                }
            }
        }
    }

    val continueFromUsername: () -> Unit = {
        if (username.trim().length >= 3) { error = null; signupStep = SignupStep.ACCOUNT }
        else error = "Username must be at least 3 characters."
    }

    val page = when {
        mode == AuthMode.LOGIN -> AuthFormPage.LOGIN
        signupStep == SignupStep.USERNAME -> AuthFormPage.NAME
        else -> AuthFormPage.ACCOUNT
    }

    // Back steps through sign-up first; from the first page it leaves the screen.
    val goBack: (() -> Unit)? = if (page == AuthFormPage.ACCOUNT) {
        val stepBack: () -> Unit = { error = null; signupStep = SignupStep.USERNAME }
        stepBack
    } else if (onBack != null) {
        val leave: () -> Unit = { focusManager.clearFocus(); onBack() }
        leave
    } else {
        null
    }
    androidx.activity.compose.BackHandler(enabled = page == AuthFormPage.ACCOUNT) {
        error = null
        signupStep = SignupStep.USERNAME
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AuthTopBar(
            onBack = goBack,
            step = if (page == AuthFormPage.ACCOUNT) 2 else 1,
            steps = if (mode == AuthMode.SIGNUP) 2 else 0,
            hazeState = hazeState,
        )

        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 6 })
                    .togetherWith(
                        fadeOut(tween(180)) + slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -dir * it / 6 },
                    )
            },
            label = "authPage",
        ) { p ->
            when (p) {
                AuthFormPage.LOGIN -> AuthPage(
                    title = "Welcome Back",
                    subtitle = "Log in to pick up where you left off.",
                    bottom = {
                        AuthButton(
                            text = "Log In",
                            onClick = submitLogin,
                            style = AuthButtonStyle.Primary,
                            loading = loading,
                        )
                        AuthLinkRow(
                            prefix = "Don't have an account?",
                            action = "Sign Up",
                            onClick = { error = null; onModeChange(AuthMode.SIGNUP) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                ) {
                    AuthField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        placeholder = "Email",
                        keyboardType = KeyboardType.Email,
                        autoFocus = true,
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        placeholder = "Password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        hazeState = hazeState,
                        onImeAction = submitLogin,
                        trailing = { AuthPasswordToggle(passwordVisible) { passwordVisible = !passwordVisible } },
                    )
                    AuthError(error)
                }

                AuthFormPage.NAME -> AuthPage(
                    title = "Let's Get Started",
                    subtitle = "First, choose a username for your account.",
                    bottom = {
                        AuthButton(
                            text = "Continue",
                            onClick = continueFromUsername,
                            style = AuthButtonStyle.Primary,
                        )
                        AuthLinkRow(
                            prefix = "Already have an account?",
                            action = "Log In",
                            onClick = { error = null; onModeChange(AuthMode.LOGIN) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                ) {
                    AuthValidatedField(
                        value = username,
                        onValueChange = { username = it; error = null },
                        placeholder = "Username",
                        validator = { it.trim().length >= 3 },
                        imeAction = ImeAction.Done,
                        autoFocus = true,
                        hazeState = hazeState,
                        onImeAction = continueFromUsername,
                    )
                    AuthError(error)
                }

                AuthFormPage.ACCOUNT -> AuthPage(
                    title = "Set Up Your Account",
                    subtitle = "Add your email and a password to finish.",
                    bottom = {
                        AuthButton(
                            text = "Create an Account",
                            onClick = submitSignup,
                            style = AuthButtonStyle.Primary,
                            loading = loading,
                        )
                        AuthLinkRow(
                            prefix = "Already have an account?",
                            action = "Log In",
                            onClick = { error = null; onModeChange(AuthMode.LOGIN) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                ) {
                    AuthValidatedField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        placeholder = "Email",
                        validator = ::isValidEmail,
                        keyboardType = KeyboardType.Email,
                        autoFocus = true,
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthValidatedField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        placeholder = "Password",
                        validator = { it.length >= 6 },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        showToggle = true,
                        toggleVisible = passwordVisible,
                        onToggleVisible = { passwordVisible = !passwordVisible },
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthValidatedField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        placeholder = "Confirm password",
                        validator = { it.length >= 6 && it == password },
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        showToggle = true,
                        toggleVisible = confirmPasswordVisible,
                        onToggleVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                        hazeState = hazeState,
                        onImeAction = submitSignup,
                    )
                    AuthError(error)
                }
            }
        }
    }
}
