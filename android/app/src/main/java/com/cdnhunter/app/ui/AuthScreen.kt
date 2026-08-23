package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private val Purple = Color(0xFF8B5CF6)
private val Blue = Color(0xFF3B82F6)
private val Pink = Color(0xFFEC4899)
private val BgDark = Color(0xFF080B14)
private val GlassBg = Color(0xFF0F1320)
private val GlassBorder = Color(0xFF1E2640)

enum class AuthMode { LOGIN, SIGNUP }

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                error = "Google sign-in failed (${e.statusCode})"
                loading = false
            }
        }
    }

    // Orb float animation
    val inf = rememberInfiniteTransition(label = "bg")
    val orbY by inf.animateFloat(0f, 24f, infiniteRepeatable(tween(4000, easing = EaseInOutSine), RepeatMode.Reverse), label = "y")
    val orbY2 by inf.animateFloat(0f, -18f, infiniteRepeatable(tween(5500, easing = EaseInOutSine), RepeatMode.Reverse), label = "y2")
    val pulse by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(BgDark)) {

        // Cosmic background orbs
        Box(Modifier.size(400.dp).offset((-80).dp, (-100).dp + orbY.dp).blur(100.dp)
            .background(Brush.radialGradient(listOf(Purple.copy(.4f), Color.Transparent)), CircleShape))
        Box(Modifier.size(350.dp).offset(120.dp, 300.dp + orbY2.dp).blur(100.dp)
            .background(Brush.radialGradient(listOf(Blue.copy(.3f), Color.Transparent)), CircleShape))
        Box(Modifier.size(250.dp).offset(200.dp, (-50).dp + orbY.dp).blur(80.dp)
            .background(Brush.radialGradient(listOf(Pink.copy(.2f), Color.Transparent)), CircleShape))

        // Stars (static dots)
        repeat(30) { i ->
            val x = (i * 37 % 350).dp
            val y = (i * 53 % 700).dp
            val size = if (i % 3 == 0) 2.dp else 1.dp
            Box(Modifier.size(size).offset(x, y)
                .background(Color.White.copy(alpha = 0.3f + (i % 4) * 0.1f), CircleShape))
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(700, easing = EaseOutCubic)) { it / 4 }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Glass card
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(GlassBg.copy(alpha = 0.85f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                        .padding(28.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // Logo pulse
                        Box(
                            Modifier.size(64.dp).scale(pulse)
                                .background(Brush.radialGradient(listOf(Purple, Blue)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍍", fontSize = 28.sp)
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Ananas VPN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (mode == AuthMode.LOGIN) "Welcome back" else "Create your account",
                            fontSize = 13.sp, color = Color.White.copy(.45f)
                        )

                        Spacer(Modifier.height(24.dp))

                        // Tab toggle
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0A0D16)).padding(4.dp)
                        ) {
                            listOf(AuthMode.LOGIN to "Sign In", AuthMode.SIGNUP to "Sign Up").forEach { (m, label) ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (mode == m) Purple.copy(.2f) else Color.Transparent)
                                        .clickable { mode = m; error = null }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (mode == m) Purple else Color.White.copy(.4f))
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        AnimatedContent(targetState = mode, label = "fields") { m ->
                            Column {
                                if (m == AuthMode.SIGNUP) {
                                    AuthField("Username", username, { username = it })
                                    Spacer(Modifier.height(12.dp))
                                }
                                AuthField("Email", email, { email = it }, KeyboardType.Email)
                                Spacer(Modifier.height(12.dp))
                                AuthField("Password", password, { password = it },
                                    KeyboardType.Password,
                                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton({ passwordVisible = !passwordVisible }) {
                                            Icon(
                                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                null, tint = Color.White.copy(.4f)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Color(0xFFFF6B6B), fontSize = 11.sp, textAlign = TextAlign.Center)
                        }

                        Spacer(Modifier.height(20.dp))

                        // Main action button
                        Button(
                            onClick = {
                                error = null
                                loading = true
                                if (mode == AuthMode.LOGIN) {
                                    auth.signInWithEmailAndPassword(email, password)
                                        .addOnSuccessListener { onSignedIn() }
                                        .addOnFailureListener { e -> error = e.message; loading = false }
                                } else {
                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnSuccessListener { onSignedIn() }
                                        .addOnFailureListener { e -> error = e.message; loading = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            enabled = !loading
                        ) {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Brush.horizontalGradient(listOf(Purple, Blue)), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(if (mode == AuthMode.LOGIN) "Sign In" else "Create Account",
                                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Divider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Divider(Modifier.weight(1f), color = Color.White.copy(.1f))
                            Text("  or  ", fontSize = 11.sp, color = Color.White.copy(.3f))
                            Divider(Modifier.weight(1f), color = Color.White.copy(.1f))
                        }

                        Spacer(Modifier.height(16.dp))

                        // Google button
                        OutlinedButton(
                            onClick = { launcher.launch(googleClient.signInIntent) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A0D16))
                        ) {
                            Text("G ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue)
                            Text("Continue with Google", color = Color.White.copy(.85f), fontSize = 14.sp)
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            "By continuing you agree to our Terms & Privacy Policy",
                            fontSize = 10.sp, color = Color.White.copy(.2f), textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple.copy(.6f),
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = Purple.copy(.8f),
            unfocusedLabelColor = Color.White.copy(.3f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White.copy(.8f),
            cursorColor = Purple,
            focusedContainerColor = Color(0xFF0A0D16),
            unfocusedContainerColor = Color(0xFF0A0D16)
        )
    )
}
