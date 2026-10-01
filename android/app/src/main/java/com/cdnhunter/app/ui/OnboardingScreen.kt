package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
                        modifier = Modifier.padding(top = 16.dp).size(44.dp),
                    )
                }

                Column(Modifier.padding(top = 48.dp, bottom = 12.dp)) {
                    Entrance(delayMs = 120) {
                        Text(
                            "Log In or Create an Account",
                            color = AuthDs.Hi,
                            fontSize = 40.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        )
                    }
                    Spacer(Modifier.height(32.dp))

                    Entrance(delayMs = 240) {
                        Column {
                            AuthButton(
                                text = "Create with Email",
                                onClick = onSignUp,
                                style = AuthButtonStyle.Primary,
                            )
                            Spacer(Modifier.height(20.dp))
                            AuthOrDivider()
                            Spacer(Modifier.height(20.dp))
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
                                Spacer(Modifier.height(12.dp))
                                AuthButton(
                                    text = "Continue with Apple",
                                    onClick = onApple,
                                    hazeState = hazeState,
                                )
                            }
                            AuthError(googleError)
                        }
                    }

                    Entrance(delayMs = 340) {
                        Column {
                            AuthLinkRow(
                                prefix = "Already have an account?",
                                action = "Log In",
                                onClick = onContinueWithEmail,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "By continuing, you agree to our Terms of Service and Privacy Policy.",
                                color = AuthDs.Low,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
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
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 650, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label = "authEntrance",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 28.dp.toPx()
        },
    ) { content() }
}
