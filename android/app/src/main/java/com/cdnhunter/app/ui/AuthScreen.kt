package com.cdnhunter.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var agreeTerms by remember { mutableStateOf(false) }
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

    // Submit handler shared by the primary button (email/password auth).
    // Explicit () -> Unit so the trailing `when` is treated as statements (no
    // experimental unit-coercion when passed to Button's onClick).
    val submit: () -> Unit = {
        error = null; notice = null
        when {
            email.isBlank() || password.isBlank() ->
                error = "Please enter your email and password."
            mode == AuthMode.SIGNUP && !agreeTerms ->
                error = "Please agree to the Terms & Privacy Policy to continue."
            else -> {
                loading = true
                if (mode == AuthMode.LOGIN) {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { onSignedIn() }
                        .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
                } else {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { onSignedIn() }
                        .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
                }
            }
        }
    }

    // Cosmic backdrop: a slow-breathing blue nebula glow behind a faint starfield.
    val inf = rememberInfiniteTransition(label = "bg")
    val glow by inf.animateFloat(0.12f, 0.20f, infiniteRepeatable(tween(4500, easing = EaseInOutSine), RepeatMode.Reverse), label = "g")

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize().background(BgDark)) {

        Box(
            Modifier.size(420.dp).align(Alignment.TopCenter).offset(y = (-150).dp).blur(130.dp)
                .background(Brush.radialGradient(listOf(AccentGlow.copy(alpha = glow), Color.Transparent)), CircleShape)
        )

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
                    .padding(top = 56.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.Start
            ) {

                // Cosmic hero — abstract, mode-aware (a portal for login, a constellation for signup)
                CosmicHero(mode = mode, modifier = Modifier.fillMaxWidth().height(200.dp))

                Spacer(Modifier.height(20.dp))

                // Large left-aligned page title (matches the Figma title placement)
                Text(
                    if (mode == AuthMode.LOGIN) "Login" else "Sign up",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TextHi
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mode == AuthMode.LOGIN) "Welcome back — sign in to continue."
                    else "Create your account to get started.",
                    fontSize = 14.sp, color = TextMid
                )

                Spacer(Modifier.height(28.dp))

                // Fields — flat, borderless, leading-icon inside (adapted from the Figma pills)
                AuthField("Email", email, { email = it }, Icons.Default.Email, KeyboardType.Email)
                Spacer(Modifier.height(14.dp))
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

                Spacer(Modifier.height(16.dp))

                // Checkbox row — "Remember me" (login) / agree-to-terms (signup)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val checked = if (mode == AuthMode.LOGIN) rememberMe else agreeTerms
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            if (mode == AuthMode.LOGIN) rememberMe = it
                            else { agreeTerms = it; if (it) error = null }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Accent, uncheckedColor = TextLow, checkmarkColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (mode == AuthMode.LOGIN) "Remember me"
                        else "I agree to the Terms & Privacy Policy",
                        fontSize = 13.sp, color = TextMid
                    )
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

                // Primary action — our blue accent gradient
                Button(
                    onClick = submit,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
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
                            Text(if (mode == AuthMode.LOGIN) "Login" else "Sign up",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }

                // Forget the Password? (login only) — real Firebase reset email
                if (mode == AuthMode.LOGIN) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Forget the Password?",
                        fontSize = 13.sp, color = TextMid,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                            val target = email.trim()
                            if (target.isEmpty()) {
                                error = "Enter your email first, then tap Forget the Password."
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

                Spacer(Modifier.height(26.dp))

                // OR Continue with — divider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f), color = HairBorder)
                    Text("  OR Continue with  ", fontSize = 11.sp, color = TextLow)
                    Divider(Modifier.weight(1f), color = HairBorder)
                }

                Spacer(Modifier.height(20.dp))

                // Google — the only wired provider, a single centered tile
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 72.dp, height = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FieldBg)
                        .clickable(onClickLabel = "Continue with Google") { launcher.launch(googleClient.signInIntent) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("G", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
                }

                Spacer(Modifier.height(24.dp))

                // Bottom text-link mode switch (replaces the top tab toggle)
                Row(
                    Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (mode == AuthMode.LOGIN) "Don't have an account? " else "Already have an account? ",
                        fontSize = 13.sp, color = TextMid
                    )
                    Text(
                        if (mode == AuthMode.LOGIN) "Sign up" else "Login",
                        fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            mode = if (mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
                            error = null; notice = null
                        }
                    )
                }
            }
        }
    }
}

/** Abstract, procedurally-drawn cosmic hero — no image assets required.
 *  Login → a glowing "portal" (concentric rings + core + orbiting spark) evoking entry/return.
 *  Signup → a "constellation" (connected twinkling stars) evoking creating/joining. */
@Composable
private fun CosmicHero(mode: AuthMode, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "hero")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "rot")
    val breathe by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse), label = "br")

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val c = Offset(cx, cy)
        val unit = minOf(size.width, size.height)

        // Ambient core glow (shared by both motifs)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentGlow.copy(alpha = 0.22f), Color.Transparent),
                center = c, radius = unit * 0.5f
            ),
            radius = unit * 0.5f, center = c
        )

        if (mode == AuthMode.LOGIN) {
            listOf(0.44f to 0.30f, 0.34f to 0.45f, 0.24f to 0.65f).forEach { (r, a) ->
                drawCircle(
                    color = Accent.copy(alpha = a),
                    radius = unit * r, center = c,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, AccentLight, Color.Transparent),
                    center = c, radius = unit * 0.16f * breathe
                ),
                radius = unit * 0.16f * breathe, center = c
            )
            val rad = Math.toRadians(rot.toDouble())
            val spark = Offset(
                cx + (unit * 0.44f) * kotlin.math.cos(rad).toFloat(),
                cy + (unit * 0.44f) * kotlin.math.sin(rad).toFloat()
            )
            drawCircle(color = AccentLight, radius = unit * 0.028f, center = spark)
        } else {

            val pts = listOf(
                Offset(-0.34f, -0.22f), Offset(-0.10f, -0.36f), Offset(0.20f, -0.18f),
                Offset(0.36f, 0.14f), Offset(0.06f, 0.30f), Offset(-0.26f, 0.20f)
            )
            val rad = Math.toRadians(rot.toDouble() * 0.15)
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()
            val world = pts.map { p ->
                val px = p.x * unit; val py = p.y * unit
                Offset(cx + (px * cos - py * sin), cy + (px * sin + py * cos))
            }
            for (i in world.indices) {
                val a = world[i]; val b = world[(i + 1) % world.size]
                drawLine(color = Accent.copy(alpha = 0.28f), start = a, end = b, strokeWidth = 1.5.dp.toPx())
            }
            world.forEachIndexed { i, p ->
                val glowR = unit * (if (i % 2 == 0) 0.03f else 0.022f) * breathe
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentLight, Color.Transparent),
                        center = p, radius = glowR * 2.4f
                    ),
                    radius = glowR * 2.4f, center = p
                )
                drawCircle(color = Color.White, radius = glowR, center = p)
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
