package com.cdnhunter.app.ui

import android.app.Activity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.draw.clip
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

private const val RESEND_COOLDOWN_S = 30

/**
 * The six-digit email code step. The code field takes paste and the keyboard, submits itself on
 * the sixth digit, shakes on a wrong code, and "Resend" is gated by a short countdown. Failures
 * come back as [AuthIssue]s with a next step (new code, retry) rather than a bare red line.
 *
 * Not on the sign-up path today (sign-up goes straight into the app and verifies later from the
 * profile); it is ready for it — set the pending email and `step = VERIFY` in `onSuccess`.
 */
@Composable
private fun VerifyEmailContent(email: String, onVerified: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val hazeState = LocalHazeState.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<AuthIssue?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    var secondsLeft by remember { mutableStateOf(RESEND_COOLDOWN_S) }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    val verify: () -> Unit = {
        if (!loading && code.length == 6) {
            issue = null
            notice = null
            loading = true
            scope.launch {
                when (val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.verifyEmail(email, code)) {
                    is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> {
                        com.cdnhunter.app.vpn.GroomxAuthClient.markEmailVerified(context)
                        onVerified()
                    }
                    is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure -> {
                        issue = classifyVerifyFailure(outcome.message)
                        shakeKey += 1
                        loading = false
                    }
                }
            }
        }
    }

    val sendNewCode: () -> Unit = {
        if (!resending) {
            resending = true
            issue = null
            notice = null
            scope.launch {
                when (val outcome = com.cdnhunter.app.vpn.GroomxAuthClient.resendVerificationCode(email)) {
                    is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Success -> {
                        code = ""
                        notice = "A new code is on its way."
                        secondsLeft = RESEND_COOLDOWN_S
                    }
                    is com.cdnhunter.app.vpn.GroomxAuthClient.AuthOutcome.Failure ->
                        issue = commonAuthIssue(outcome.message) ?: AuthIssue(outcome.message)
                }
                resending = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AuthTopBar(onBack = null, step = 0, steps = 0, hazeState = hazeState)
        AuthPage(
            title = "Check your email",
            subtitle = "We sent a 6-digit code to $email. Enter it below.",
            bottom = {
                AuthButton(
                    text = "Verify",
                    onClick = verify,
                    style = AuthButtonStyle.Primary,
                    loading = loading,
                    enabled = code.length == 6,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = AppDs.S2),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (secondsLeft > 0) {
                        val mm = secondsLeft / 60
                        val ss = (secondsLeft % 60).toString().padStart(2, '0')
                        Text(
                            "Resend code in $mm:$ss",
                            style = AuthType.Caption.copy(fontSize = 14.sp),
                            color = AuthDs.Low,
                            modifier = Modifier.heightIn(min = AuthDs.MinTouch).padding(vertical = 14.dp),
                        )
                    } else {
                        Text(
                            if (resending) "Sending…" else "Resend code",
                            style = AuthType.Notice,
                            color = AuthDs.Hi,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppDs.S3))
                                .clickable(enabled = !resending, role = androidx.compose.ui.semantics.Role.Button, onClick = sendNewCode)
                                .heightIn(min = AuthDs.MinTouch)
                                .padding(horizontal = AppDs.S3, vertical = 14.dp),
                        )
                    }
                }
                Text(
                    "Skip for now",
                    style = AuthType.Caption.copy(fontSize = 14.sp),
                    color = AuthDs.Low,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(AppDs.S3))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onSkip)
                        .heightIn(min = AuthDs.MinTouch)
                        .padding(horizontal = AppDs.S4, vertical = 14.dp),
                )
            },
        ) {
            AuthOtpField(
                value = code,
                onValueChange = { code = it; issue = null; notice = null },
                isError = issue?.field == AuthFieldId.CODE,
                enabled = !loading,
                shakeKey = shakeKey,
                onComplete = verify,
            )
            notice?.let {
                Text(
                    it,
                    style = AuthType.Caption,
                    color = AuthDs.Success,
                    modifier = Modifier
                        .padding(top = AppDs.S4)
                        .politeLiveRegion(),
                )
            }
            AuthBanner(issue, onAction = { action ->
                when (action) {
                    AuthIssueAction.RETRY -> verify()
                    AuthIssueAction.RESEND -> sendNewCode()
                    else -> Unit
                }
            })
        }
    }
}

private fun Modifier.politeLiveRegion(): Modifier =
    this.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }

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

// ── Turning raw failures into something actionable ───────────────────────────
// UI-only: these read the text the existing auth calls already return and choose how to present
// it. Anything unrecognised falls back to the original message, as it always did.

private fun commonAuthIssue(raw: String): AuthIssue? {
    val m = raw.lowercase()
    return when {
        "network" in m || "connection" in m || "timeout" in m || "timed out" in m || "offline" in m ->
            AuthIssue(
                "Can't reach the server",
                "Check your internet connection, then try again.",
                action = AuthIssueAction.RETRY,
                actionLabel = "Try again",
            )
        "too many" in m || "rate limit" in m || "rate-limit" in m || "try again later" in m || "429" in m ->
            AuthIssue("Too many attempts", "Wait a minute, then try again.")
        else -> null
    }
}

private fun classifyAuthFailure(raw: String, signingUp: Boolean): AuthIssue {
    commonAuthIssue(raw)?.let { return it }
    val m = raw.lowercase()
    return when {
        signingUp && "username" !in m &&
            ("already" in m || "exists" in m || "in use" in m || "registered" in m) ->
            AuthIssue(
                "This email already has an account",
                "Log in instead, or use a different email.",
                field = AuthFieldId.EMAIL,
                action = AuthIssueAction.LOG_IN,
                actionLabel = "Log in",
            )
        // Every credential failure reads the same on purpose, so the screen can't be used to
        // find out which emails are registered.
        !signingUp && ("incorrect" in m || "invalid" in m || "wrong" in m || "not found" in m ||
            "no account" in m || "credential" in m || "password" in m || "unauthor" in m) ->
            AuthIssue("Incorrect email or password", "Check your details and try again.", field = AuthFieldId.PASSWORD)
        "went wrong" in m ->
            AuthIssue("Something went wrong", "Please try again.", action = AuthIssueAction.RETRY, actionLabel = "Try again")
        else -> AuthIssue(raw)
    }
}

private fun classifyVerifyFailure(raw: String): AuthIssue {
    commonAuthIssue(raw)?.let { return it }
    return if ("expired" in raw.lowercase()) {
        AuthIssue(
            "This code has expired",
            "Request a new one and enter it here.",
            field = AuthFieldId.CODE,
            action = AuthIssueAction.RESEND,
            actionLabel = "Send a new code",
        )
    } else {
        AuthIssue("That code isn't right", "Check the code in your email and try again.", field = AuthFieldId.CODE)
    }
}

/**
 * Log in and sign up, as paged steps over the shared backdrop:
 *  - Log in: one page, email + password.
 *  - Sign up: "Let's Get Started" (username), then "Set Up Your Account" (email, password, confirm).
 *
 * The auth calls, validation rules and session handling are the ones the screen always had; the
 * paging, copy and presentation changed. Back steps through sign-up before leaving the screen,
 * fields lock while a request is in flight, and every error is an [AuthIssue].
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
    var issue by remember { mutableStateOf<AuthIssue?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val reduce = appReduceMotion()

    val submitLogin: () -> Unit = {
        issue = null
        when {
            !isValidEmail(email) ->
                issue = AuthIssue("Enter a valid email address", "Use the format name@example.com.", field = AuthFieldId.EMAIL)
            password.isBlank() ->
                issue = AuthIssue("Enter your password", field = AuthFieldId.PASSWORD)
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
                            issue = classifyAuthFailure(outcome.message, signingUp = false)
                            loading = false
                        }
                    }
                }
            }
        }
    }

    val submitSignup: () -> Unit = {
        issue = null
        when {
            username.isBlank() -> {
                signupStep = SignupStep.USERNAME
                issue = AuthIssue("Choose a username", "Use at least 3 characters.", field = AuthFieldId.USERNAME)
            }
            email.isBlank() || !isValidEmail(email) ->
                issue = AuthIssue("Enter a valid email address", "Use the format name@example.com.", field = AuthFieldId.EMAIL)
            password.isBlank() ->
                issue = AuthIssue("Create a password", "Use at least 6 characters.", field = AuthFieldId.PASSWORD)
            password != confirmPassword ->
                issue = AuthIssue("Passwords don't match", "Enter the same password in both fields.", field = AuthFieldId.CONFIRM)
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
                            issue = classifyAuthFailure(outcome.message, signingUp = true)
                            loading = false
                        }
                    }
                }
            }
        }
    }

    val continueFromUsername: () -> Unit = {
        if (username.trim().length >= 3) {
            issue = null
            signupStep = SignupStep.ACCOUNT
        } else {
            issue = AuthIssue("That username is too short", "Use at least 3 characters.", field = AuthFieldId.USERNAME)
        }
    }

    val onIssueAction: (AuthIssueAction) -> Unit = { action ->
        when (action) {
            AuthIssueAction.RETRY -> if (mode == AuthMode.SIGNUP) submitSignup() else submitLogin()
            // The email stays filled in: this composable outlives the mode change.
            AuthIssueAction.LOG_IN -> { issue = null; onModeChange(AuthMode.LOGIN) }
            AuthIssueAction.RESEND, AuthIssueAction.NONE -> Unit
        }
    }

    val page = when {
        mode == AuthMode.LOGIN -> AuthFormPage.LOGIN
        signupStep == SignupStep.USERNAME -> AuthFormPage.NAME
        else -> AuthFormPage.ACCOUNT
    }

    // Back steps through sign-up first; from the first page it leaves the screen.
    val goBack: (() -> Unit)? = if (page == AuthFormPage.ACCOUNT) {
        val stepBack: () -> Unit = { issue = null; signupStep = SignupStep.USERNAME }
        stepBack
    } else if (onBack != null) {
        val leave: () -> Unit = { focusManager.clearFocus(); onBack() }
        leave
    } else {
        null
    }
    androidx.activity.compose.BackHandler(enabled = page == AuthFormPage.ACCOUNT) {
        issue = null
        signupStep = SignupStep.USERNAME
    }

    fun marks(field: AuthFieldId) = issue?.field == field

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
                if (reduce) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { dir * it / 6 })
                        .togetherWith(
                            fadeOut(tween(180)) + slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -dir * it / 6 },
                        )
                }
            },
            label = "authPage",
        ) { p ->
            when (p) {
                AuthFormPage.LOGIN -> AuthPage(
                    title = "Welcome Back",
                    subtitle = "Log in to your account.",
                    bottom = {
                        AuthButton(
                            text = "Log In",
                            onClick = submitLogin,
                            style = AuthButtonStyle.Primary,
                            loading = loading,
                        )
                        AuthLinkRow(
                            prefix = "New here?",
                            action = "Create an account",
                            onClick = { issue = null; onModeChange(AuthMode.SIGNUP) },
                            modifier = Modifier.padding(top = AppDs.S1),
                        )
                    },
                ) {
                    AuthValidatedField(
                        label = "Email",
                        value = email,
                        onValueChange = { email = it; issue = null },
                        validator = ::isValidEmail,
                        placeholder = "name@example.com",
                        keyboardType = KeyboardType.Email,
                        autoFocus = true,
                        enabled = !loading,
                        forceError = marks(AuthFieldId.EMAIL),
                        invalidMessage = "Enter a valid email address",
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(AppDs.S4))
                    AuthField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it; issue = null },
                        placeholder = "Your password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = marks(AuthFieldId.PASSWORD),
                        enabled = !loading,
                        hazeState = hazeState,
                        onImeAction = submitLogin,
                        trailing = { AuthPasswordToggle(passwordVisible) { passwordVisible = !passwordVisible } },
                    )
                    AuthBanner(issue, onIssueAction)
                }

                AuthFormPage.NAME -> AuthPage(
                    title = "Let's Get Started",
                    subtitle = "Pick a username for your account.",
                    bottom = {
                        AuthButton(
                            text = "Continue",
                            onClick = continueFromUsername,
                            style = AuthButtonStyle.Primary,
                        )
                        AuthLinkRow(
                            prefix = "Already have an account?",
                            action = "Log In",
                            onClick = { issue = null; onModeChange(AuthMode.LOGIN) },
                            modifier = Modifier.padding(top = AppDs.S1),
                        )
                    },
                ) {
                    AuthValidatedField(
                        label = "Username",
                        value = username,
                        onValueChange = { username = it; issue = null },
                        validator = { it.trim().length >= 3 },
                        placeholder = "Choose a username",
                        imeAction = ImeAction.Done,
                        autoFocus = true,
                        forceError = marks(AuthFieldId.USERNAME),
                        hint = "At least 3 characters",
                        invalidMessage = "Use at least 3 characters",
                        hazeState = hazeState,
                        onImeAction = continueFromUsername,
                    )
                    AuthBanner(issue, onIssueAction)
                }

                AuthFormPage.ACCOUNT -> AuthPage(
                    title = "Set Up Your Account",
                    subtitle = "Add an email and a password to finish.",
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
                            onClick = { issue = null; onModeChange(AuthMode.LOGIN) },
                            modifier = Modifier.padding(top = AppDs.S1),
                        )
                    },
                ) {
                    AuthValidatedField(
                        label = "Email",
                        value = email,
                        onValueChange = { email = it; issue = null },
                        validator = ::isValidEmail,
                        placeholder = "name@example.com",
                        keyboardType = KeyboardType.Email,
                        autoFocus = true,
                        enabled = !loading,
                        forceError = marks(AuthFieldId.EMAIL),
                        invalidMessage = "Enter a valid email address",
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(AppDs.S4))
                    AuthValidatedField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it; issue = null },
                        validator = { it.length >= 6 },
                        placeholder = "Create a password",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        showToggle = true,
                        toggleVisible = passwordVisible,
                        onToggleVisible = { passwordVisible = !passwordVisible },
                        enabled = !loading,
                        forceError = marks(AuthFieldId.PASSWORD),
                        hint = "At least 6 characters",
                        invalidMessage = "Use at least 6 characters",
                        hazeState = hazeState,
                    )
                    Spacer(Modifier.height(AppDs.S4))
                    AuthValidatedField(
                        label = "Confirm password",
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; issue = null },
                        validator = { it.length >= 6 && it == password },
                        placeholder = "Re-enter your password",
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        showToggle = true,
                        toggleVisible = confirmPasswordVisible,
                        onToggleVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                        enabled = !loading,
                        forceError = marks(AuthFieldId.CONFIRM),
                        invalidMessage = if (confirmPassword != password) "Passwords don't match" else "Use at least 6 characters",
                        hazeState = hazeState,
                        onImeAction = submitSignup,
                    )
                    AuthBanner(issue, onIssueAction)
                }
            }
        }
    }
}
