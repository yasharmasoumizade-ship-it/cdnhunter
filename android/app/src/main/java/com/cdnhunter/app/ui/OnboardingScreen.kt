package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dev.chrisbanes.haze.HazeState

/**
 * First screen: a full-bleed backdrop, the logo top-left, and — low on the screen — the headline
 * and the sign-in actions. "Create with Email" starts sign-up, Google signs in directly, and a
 * quiet "Log In" link covers returning email users. The sign-in plumbing is unchanged; only the
 * presentation lives here.
 *
 * [onApple] is optional: the app has no Apple sign-in backend, so the button only appears when a
 * caller supplies a handler.
 */
@Composable
fun OnboardingScreen(
    onGoogleSignedIn: () -> Unit,
    onContinueWithEmail: () -> Unit,
    onSignUp: () -> Unit,
    onApple: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var googleError by remember { mutableStateOf<String?>(null) }
    var googleLoading by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("270834492287-nppqi8eb25khf5l2icprs2c73at80l9u.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken == null) {
                    googleError = "Google sign-in failed. Please try again."
                } else {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    googleLoading = true
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { onGoogleSignedIn() }
                        .addOnFailureListener { googleError = "Google sign-in failed. Please try again."; googleLoading = false }
                }
            } catch (e: ApiException) {
                googleError = "Google sign-in failed. Please try again."
                googleLoading = false
            }
        }
    }

    val hazeState = remember { HazeState() }

    Box(Modifier.fillMaxSize()) {
        AuthBackdrop(hazeState)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = screenHeight)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = AuthDs.Gutter),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Entrance(delayMs = 0) {
                    Image(
                        painter = painterResource(id = com.cdnhunter.app.R.drawable.logo_alien),
                        contentDescription = "Logo",
                        modifier = Modifier.padding(top = AppDs.S4).size(44.dp),
                    )
                }

                Column(Modifier.padding(top = AppDs.S7 + AppDs.S6, bottom = AppDs.S3)) {
                    Entrance(delayMs = 120) {
                        Column {
                            Text(
                                "Log In or Create an Account",
                                style = AuthType.Hero,
                                color = AuthDs.Hi,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(AppDs.S3))
                            Text(
                                "Fast, private access to the open internet.",
                                style = AuthType.Body,
                                color = AuthDs.Mid,
                            )
                        }
                    }
                    Spacer(Modifier.height(AppDs.S7))

                    Entrance(delayMs = 240) {
                        Column {
                            AuthButton(
                                text = "Create with Email",
                                onClick = onSignUp,
                                style = AuthButtonStyle.Primary,
                            )
                            Spacer(Modifier.height(AppDs.S5))
                            AuthOrDivider()
                            Spacer(Modifier.height(AppDs.S5))
                            AuthButton(
                                text = "Continue with Google",
                                onClick = { googleError = null; launcher.launch(googleClient.signInIntent) },
                                loading = googleLoading,
                                hazeState = hazeState,
                                leading = {
                                    Image(
                                        painter = painterResource(id = com.cdnhunter.app.R.drawable.ic_google_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                            )
                            if (onApple != null) {
                                Spacer(Modifier.height(AppDs.S3))
                                AuthButton(
                                    text = "Continue with Apple",
                                    onClick = onApple,
                                    hazeState = hazeState,
                                )
                            }
                            AuthBanner(
                                issue = googleError?.let {
                                    AuthIssue(
                                        "Google sign-in didn't work",
                                        "Check your connection and try again.",
                                        action = AuthIssueAction.RETRY,
                                        actionLabel = "Try again",
                                    )
                                },
                                onAction = { googleError = null; launcher.launch(googleClient.signInIntent) },
                            )
                        }
                    }

                    Entrance(delayMs = 340) {
                        Column {
                            AuthLinkRow(
                                prefix = "Already have an account?",
                                action = "Log In",
                                onClick = onContinueWithEmail,
                                modifier = Modifier.padding(top = AppDs.S2),
                            )
                            Text(
                                "By continuing, you agree to our Terms of Service and Privacy Policy.",
                                style = AuthType.Caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = AuthDs.Low,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = AppDs.S1, bottom = AppDs.S2, start = AppDs.S3, end = AppDs.S3),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fades and lifts its content in once, [delayMs] after the screen appears — a staggered reveal. */
@Composable
private fun Entrance(delayMs: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reduce = appReduceMotion()
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduce) snap() else tween(durationMillis = 650, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "authEntrance",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 28.dp.toPx()
        },
    ) { content() }
}
