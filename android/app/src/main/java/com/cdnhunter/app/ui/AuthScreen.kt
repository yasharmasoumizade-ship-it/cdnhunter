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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

private val BgDark = Color(0xFF0A0B0F)
private val FieldBg = Color(0xFF15171E)
private val FieldBorder = Color(0xFF23262F)
private val Accent = Color(0xFF3B82F6)
private val TextHi = Color(0xFFF6F7F9)
private val TextMid = Color(0xFF8B8E98)
private val ErrorRed = Color(0xFFEF4444)
private val SuccessGreen = Color(0xFF22C55E)

enum class AuthMode { LOGIN, SIGNUP }
private enum class AuthStep { SPLASH, FORM, SUCCESS }

/** Turns Firebase's raw exception messages into a short, human-readable string. */
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
    val auth = remember { FirebaseAuth.getInstance() }
    var step by remember { mutableStateOf(AuthStep.SPLASH) }
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }

    LaunchedEffect(Unit) {
        delay(1100)
        if (auth.currentUser != null) onSignedIn() else step = AuthStep.FORM
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(450)) + scaleIn(initialScale = 0.96f, animationSpec = tween(450)))
                    .togetherWith(fadeOut(tween(250)))
            },
            label = "authStep",
        ) { s ->
            when (s) {
                AuthStep.SPLASH -> SplashContent()
                AuthStep.FORM -> AuthFormContent(
                    mode = mode,
                    onModeChange = { mode = it },
                    onSuccess = { justSignedUp -> if (justSignedUp) step = AuthStep.SUCCESS else onSignedIn() },
                )
                AuthStep.SUCCESS -> SuccessContent(onContinue = onSignedIn)
            }
        }
    }
}

@Composable
private fun SplashContent() {
    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val scale by pulse.animateFloat(
        0.94f, 1.02f,
        infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "splashScale",
    )
    var visible by remember { mutableStateOf(false) }
    var typedChars by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        visible = true
        delay(250)
        val name = "Thallo"
        for (i in 1..name.length) {
            typedChars = i
            delay(65)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(500))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = com.cdnhunter.app.R.drawable.logo_thallo),
                    contentDescription = "Thallo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(200.dp).scale(scale),
                )
                Spacer(Modifier.height(22.dp))
                Text("Private. Fast. Secure.", fontSize = 13.sp, color = TextMid)
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
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

@Composable
private fun AuthFormContent(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    onSuccess: (justSignedUp: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                    .addOnSuccessListener { onSuccess(false) }
                    .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
            } catch (e: ApiException) {
                error = "Google sign-in failed (${e.statusCode})"
                loading = false
            }
        }
    }

    val submit: () -> Unit = {
        error = null
        when {
            email.isBlank() || password.isBlank() ->
                error = "Please enter your email and password."
            mode == AuthMode.SIGNUP && username.isBlank() ->
                error = "Please choose a username."
            mode == AuthMode.SIGNUP && password != confirmPassword ->
                error = "Passwords don't match."
            else -> {
                loading = true
                if (mode == AuthMode.LOGIN) {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { onSuccess(false) }
                        .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
                } else {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(username.trim())
                                .build()
                            result.user?.updateProfile(profileUpdate)
                            onSuccess(true)
                        }
                        .addOnFailureListener { e -> error = friendlyAuthError(e.message); loading = false }
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
                .padding(top = 48.dp, bottom = 28.dp),
        ) {
            Image(
                painter = painterResource(id = com.cdnhunter.app.R.drawable.logo_thallo),
                contentDescription = "Thallo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(110.dp),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                if (mode == AuthMode.LOGIN) "Welcome Back" else "Create Account",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextHi,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (mode == AuthMode.LOGIN) "Sign in to keep your connection secure."
                else "Set up your account to get started.",
                fontSize = 13.sp, color = TextMid,
            )

            Spacer(Modifier.height(30.dp))

            OutlinedButton(
                onClick = { launcher.launch(googleClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FieldBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = FieldBg),
            ) {
                Text("G", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.padding(end = 8.dp))
                Text("Continue with Google", color = TextHi.copy(.9f), fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider(Modifier.weight(1f), color = FieldBorder)
                Text("  or  ", fontSize = 11.sp, color = TextMid)
                Divider(Modifier.weight(1f), color = FieldBorder)
            }
            Spacer(Modifier.height(18.dp))

            if (mode == AuthMode.SIGNUP) {
                AuthField("Username", username, { username = it }, Icons.Default.Person)
                Spacer(Modifier.height(14.dp))
            }
            AuthField("Email Address", email, { email = it }, Icons.Default.Email, KeyboardType.Email)
            Spacer(Modifier.height(14.dp))
            AuthField(
                "Password", password, { password = it }, Icons.Default.Lock, KeyboardType.Password,
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TextMid,
                        )
                    }
                },
            )
            if (mode == AuthMode.SIGNUP) {
                Spacer(Modifier.height(14.dp))
                AuthField(
                    "Confirm Password", confirmPassword, { confirmPassword = it }, Icons.Default.Lock, KeyboardType.Password,
                    if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton({ confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = TextMid,
                            )
                        }
                    },
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = ErrorRed, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = submit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (mode == AuthMode.LOGIN) "Sign In" else "Sign Up", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    if (mode == AuthMode.LOGIN) "Don't have an account? " else "Already have an account? ",
                    fontSize = 13.sp, color = TextMid,
                )
                Text(
                    if (mode == AuthMode.LOGIN) "Sign Up" else "Sign In",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        error = null
                        onModeChange(if (mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN)
                    },
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
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        leadingIcon = { Icon(leading, null) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent.copy(.6f),
            unfocusedBorderColor = FieldBorder,
            focusedLeadingIconColor = Accent,
            unfocusedLeadingIconColor = TextMid,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextMid,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi.copy(.85f),
            cursorColor = Accent,
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
        ),
    )
}
