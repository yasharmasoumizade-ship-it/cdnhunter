package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

private data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val imageRes: Int,
)

private val onboardingSlides = listOf(
    OnboardingSlide(
        "Welcome to Sector 51",
        "You have entered classified territory. What happens here, stays encrypted.",
        com.cdnhunter.app.R.drawable.logo_alien,
    ),
    OnboardingSlide(
        "They're Watching. We're Not.",
        "Every signal traced, except yours. Fly under their radar.",
        com.cdnhunter.app.R.drawable.onboard_watching,
    ),
    OnboardingSlide(
        "Stay Classified",
        "Your data stays sealed. No logs, no leaks, no evidence.",
        com.cdnhunter.app.R.drawable.onboard_classified,
    ),
    OnboardingSlide(
        "Access the Unknown",
        "Break through any wall. Reach what they don't want you to see.",
        com.cdnhunter.app.R.drawable.onboard_access,
    ),
)

/**
 * Onboarding no longer gates entry behind Next/Skip: every slide shows the same
 * fixed sign-in actions below it (Google, Email, Sign Up), so the user can act
 * immediately on any slide instead of being forced through the carousel first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onGoogleSignedIn: () -> Unit, onContinueWithEmail: () -> Unit, onSignUp: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingSlides.size })
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

    Box(Modifier.fillMaxSize().background(AppColors.BgDark)) {
        FullScreenLoopVideo(modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(210.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                OnboardingSlideContent(onboardingSlides[page])
            }

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(onboardingSlides.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(if (selected) 22.dp else 7.dp, label = "dotWidth")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(7.dp)
                            .width(width)
                            .background(
                                if (selected) AppColors.Accent else AppColors.TextMid.copy(alpha = 0.35f),
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Fixed sign-in actions — identical on every slide.
            OutlinedButton(
                onClick = { googleError = null; launcher.launch(googleClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AppColors.FieldBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = AppColors.FieldBg),
                enabled = !googleLoading,
            ) {
                if (googleLoading) {
                    CircularProgressIndicator(color = AppColors.TextHi, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Image(
                        painter = painterResource(id = com.cdnhunter.app.R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).padding(end = 10.dp),
                    )
                    Text("Continue with Google", color = AppColors.TextHi.copy(.9f), fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            googleError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = AppColors.ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onContinueWithEmail,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) {
                Text("Continue with Email", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Don't have an account? ", fontSize = 13.sp, color = AppColors.TextMid)
                Text(
                    "Sign Up",
                    fontSize = 13.sp, color = AppColors.TextHi, fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onSignUp() },
                )
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun OnboardingSlideContent(slide: OnboardingSlide) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = slide.imageRes),
            contentDescription = slide.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(160.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            slide.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextHi,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            slide.subtitle,
            fontSize = 13.sp,
            color = AppColors.TextMid,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
