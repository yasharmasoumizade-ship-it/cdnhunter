package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val BgDark = Color(0xFF060709)
private val FieldBg = Color(0xFF12141A)
private val HairBorder = Color(0xFF23262F)
private val Accent = Color(0xFF4D7FFF)
private val AccentLight = Color(0xFF6E97FF)
private val AccentGlow = Color(0xFF2F6BFF)
private val TextHi = Color(0xFFF6F7F9)
private val TextMid = Color(0xFF9BA0AC)
private val TextLow = Color(0xFF656B78)
private val ErrorRed = Color(0xFFEF4444)
private val SuccessGreen = Color(0xFF34D399)

enum class AuthMode { LOGIN, SIGNUP }

/** Turns Firebase's raw exception messages (often full HTML error pages from network
 * failures, or verbose internal error codes) into a short, human-readable string. */
private fun friendlyAuthError(raw: String?): String {
    if (raw == null) return "Something went wrong. Please try again."
    val r = raw.lowercase()
    return when {
        "network" in r || "timeout" in r || "unable to resolve" in r ->
            "Network error. Check your connection and try again."
        "password is invalid" in r || "wrong-password" in r || "invalid-credential" in r ->
            "Incorrect email or password."
        "no user record" in r || "user-not-found" in r ->
            "No account found with that email."
        "email address is already in use" in r || "email-already-in-use" in r ->
            "An account already exists with that email."
        "badly formatted" in r || "invalid-email" in r ->
            "Please enter a valid email address."
        "password should be at least" in r || "weak-password" in r ->
            "Password should be at least 6 characters."
        "too many" in r ->
            "Too many attempts. Please wait and try again."
        "internal error" in r || "<html" in r || "<!doctype" in r || raw.length > 120 ->
            "Something went wrong. Please try again."
        else -> raw
    }
}

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
    var notice by remember { mutableStateOf<String?>(null) }

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
                    .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
            } catch (e: ApiException) {
                error = "Google sign-in failed (${e.statusCode})"
                loading = false
            }
        }
    }

    // Cosmic backdrop: a slow-breathing blue nebula glow behind a faint starfield.
    val inf = rememberInfiniteTransition(label = "bg")
    val glow by inf.animateFloat(0.12f, 0.20f, infiniteRepeatable(tween(4500, easing = EaseInOutSine), RepeatMode.Reverse), label = "g")
    val pulse by inf.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "p")

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(BgDark)) {

        // Blue nebula glow, top-center
        Box(
            Modifier.size(420.dp).align(Alignment.TopCenter).offset(y = (-150).dp).blur(130.dp)
                .background(Brush.radialGradient(listOf(AccentGlow.copy(alpha = glow), Color.Transparent)), CircleShape)
        )

        // Faint starfield
        repeat(28) { i ->
            val x = (i * 37 % 360).dp
            val y = (i * 53 % 760).dp
            val s = if (i % 4 == 0) 2.dp else 1.dp
            Box(
                Modifier.size(s).offset(x, y)
                    .background(Color.White.copy(alpha = 0.04f + (i % 3) * 0.02f), CircleShape)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(700, easing = EaseOutCubic)) { it / 4 }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
                    .padding(top = 72.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.Start
            ) {

                // Cosmic monogram hero — glowing orb, centered
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(150.dp).drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    0f to AccentGlow.copy(alpha = 0.34f),
                                    0.45f to AccentGlow.copy(alpha = 0.14f),
                                    1f to Color.Transparent
                                )
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(78.dp).scale(pulse)
                                .background(Brush.linearGradient(listOf(AccentLight, AccentGlow)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Large left-aligned page title (mode-aware)
                Text(
                    if (mode == AuthMode.LOGIN) "Sign in" else "Create account",
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextHi
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (mode == AuthMode.LOGIN) "Welcome back to Ananas VPN"
                    else "Join Ananas VPN in a few seconds",
                    fontSize = 14.sp, color = TextMid
                )

                Spacer(Modifier.height(26.dp))

                // Sliding-thumb tab toggle
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(FieldBg).padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 2
                    val thumbOffset by animateDpAsState(
                        targetValue = if (mode == AuthMode.LOGIN) 0.dp else tabWidth,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        label = "tabThumb"
                    )
                    Box(
                        Modifier.offset(x = thumbOffset).width(tabWidth).height(38.dp)
                            .clip(RoundedCornerShape(11.dp)).background(Accent.copy(.15f))
                    )
                    Row(Modifier.fillMaxWidth()) {
                        listOf(AuthMode.LOGIN to "Sign In", AuthMode.SIGNUP to "Sign Up").forEach { (m, label) ->
                            val textColor by animateColorAsState(
                                targetValue = if (mode == m) Accent else TextMid,
                                animationSpec = tween(260), label = "tabText"
                            )
                            Box(
                                Modifier.weight(1f).clickable { mode = m; error = null; notice = null }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Fields — flat, borderless, leading-icon (adapted from the Figma pill fields)
                AnimatedContent(targetState = mode, label = "fields") { m ->
                    Column {
                        if (m == AuthMode.SIGNUP) {
                            AuthField("Username", username, { username = it }, Icons.Default.Person)
                            Spacer(Modifier.height(12.dp))
                        }
                        AuthField("Email", email, { email = it }, Icons.Default.Email, KeyboardType.Email)
                        Spacer(Modifier.height(12.dp))
                        AuthField(
                            "Password", password, { password = it }, Icons.Default.Lock,
                            KeyboardType.Password,
                            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton({ passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        if (passwordVisible) "Hide password" else "Show password",
                                        tint = TextMid
                                    )
                                }
                            }
                        )
                    }
                }

                // Forgot-password (login only) — sends a Firebase reset email
                if (mode == AuthMode.LOGIN) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Forgot password?",
                            fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                val target = email.trim()
                                if (target.isEmpty()) {
                                    error = "Enter your email first, then tap Forgot password."
                                    notice = null
                                } else {
                                    error = null
                                    auth.sendPasswordResetEmail(target)
                                        .addOnSuccessListener { notice = "Password reset email sent to $target." }
                                        .addOnFailureListener { e -> error = friendlyAuthError(e.message) }
                                }
                            }
                        )
                    }
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = ErrorRed, fontSize = 12.sp)
                }
                notice?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = SuccessGreen, fontSize = 12.sp)
                }

                Spacer(Modifier.height(22.dp))

                // Primary action
                Button(
                    onClick = {
                        error = null; notice = null; loading = true
                        if (mode == AuthMode.LOGIN) {
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onSignedIn() }
                                .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
                        } else {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { onSignedIn() }
                                .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    enabled = !loading
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(Accent, AccentGlow)), RoundedCornerShape(16.dp)),
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

                Spacer(Modifier.height(18.dp))

                // "or" divider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f), color = HairBorder)
                    Text("  or continue with  ", fontSize = 11.sp, color = TextLow)
                    Divider(Modifier.weight(1f), color = HairBorder)
                }

                Spacer(Modifier.height(18.dp))

                // Google (the only wired provider)
                OutlinedButton(
                    onClick = { launcher.launch(googleClient.signInIntent) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HairBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FieldBg)
                ) {
                    Text("G", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Continue with Google", color = TextHi, fontSize = 14.sp)
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "By continuing you agree to our Terms & Privacy Policy",
                    fontSize = 10.sp, color = TextLow, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    leading: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        leadingIcon = { Icon(leading, null) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent.copy(.55f),
            unfocusedBorderColor = Color.Transparent,
            focusedLeadingIconColor = Accent,
            unfocusedLeadingIconColor = TextLow,
            focusedLabelColor = AccentLight,
            unfocusedLabelColor = TextMid,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi.copy(.85f),
            cursorColor = Accent,
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg
        )
    )
}
