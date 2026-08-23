package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

private val AccentPurple = Color(0xFF7C6FE0)
private val AccentBlue = Color(0xFF4F8EF7)
private val BgDark = Color(0xFF0D0F14)
private val Surface = Color(0xFF1A1D25)

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Check already signed in
    LaunchedEffect(Unit) {
        if (auth.currentUser != null) onSignedIn()
    }

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
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                loading = true
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { onSignedIn() }
                    .addOnFailureListener { e -> error = e.message; loading = false }
            } catch (e: ApiException) {
                error = "Sign-in failed (${e.statusCode})"
                loading = false
            }
        }
    }

    // Entry animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Floating orb animation
    val orbAnim = rememberInfiniteTransition(label = "orb")
    val orbY by orbAnim.animateFloat(
        initialValue = 0f, targetValue = 18f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "orbY"
    )
    val orbScale by orbAnim.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(3500, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "orbScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        // Background glow orbs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = (-180).dp + orbY.dp)
                .scale(orbScale)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AccentPurple.copy(alpha = 0.35f), Color.Transparent)
                    ), CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = 80.dp, y = (160).dp - orbY.dp)
                .scale(orbScale)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent)
                    ), CircleShape
                )
        )

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(800)) + slideInVertically(tween(800, easing = EaseOutCubic)) { it / 3 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // Logo orb
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(orbScale)
                        .background(
                            Brush.radialGradient(
                                listOf(AccentPurple, AccentBlue)
                            ), CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍍", fontSize = 36.sp)
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "Ananas VPN",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Private. Fast. Secure.",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(56.dp))

                // Google Sign-In button
                if (loading) {
                    CircularProgressIndicator(
                        color = AccentPurple,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Button(
                        onClick = { launcher.launch(googleClient.signInIntent) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = AccentBlue, modifier = Modifier.padding(end = 10.dp))
                        Text("Continue with Google", color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }

                error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    "By continuing you agree to our Terms & Privacy Policy",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
