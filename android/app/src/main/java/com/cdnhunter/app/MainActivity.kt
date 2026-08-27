package com.cdnhunter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.cdnhunter.app.ui.AppScreen
import com.cdnhunter.app.ui.AuthScreen
import com.google.firebase.auth.FirebaseAuth
import com.cdnhunter.app.ui.LocalThemeMode
import com.cdnhunter.app.ui.ThemeMode
import com.cdnhunter.app.vpn.AppSettings
import com.cdnhunter.app.vpn.CdnVpnService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Permission granted or denied — try to start VPN anyway
        CdnVpnService.start(this)
    }

    fun requestVpnPermissionAndConnect() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // Already have permission
            CdnVpnService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both system bars are painted the app's chrome colour in themes.xml
        // (#0B0B0D), which means their glyphs have to be the light set: white on that
        // background is 18.4:1, the dark set about 1.2:1. The theme attribute says so
        // too, but some OEM skins derive the status bar's appearance from the phone's
        // own light/dark setting rather than from the app's theme, and this app is
        // always dark regardless of it (see CDNHunterTheme). Stating it here through
        // WindowCompat is what holds on those devices, and it is the only way to set
        // the navigation bar's glyphs at all below API 27, where
        // windowLightNavigationBar does not exist.
        // Draw under both system bars. The home hero is one full-bleed image now — the
        // active server's flag, edge to edge — and "edge to edge" has to include the
        // strip the clock and the battery sit in, or the artwork stops at a horizontal
        // line 28dp down the screen and the whole panel reads as a card again.
        //
        // The status bar is painted transparent in themes.xml to go with this; the
        // navigation bar keeps the app's chrome colour, because nothing at the foot of
        // the screen wants to be seen through. Every screen that is not Home pads itself
        // off the bars with statusBarsPadding()/navigationBarsPadding() — Home does it
        // per row, which is what lets the flag run behind the glyphs while the top bar
        // still clears them.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CDNHunterTheme {
                MainContent(this)
            }
        }
    }
}

private enum class RootScreen { AUTH, ENTERING, HOME }

@Composable
fun MainContent(activity: MainActivity) {
    val auth = remember { FirebaseAuth.getInstance() }
    var screen by remember { mutableStateOf(if (auth.currentUser != null) RootScreen.ENTERING else RootScreen.AUTH) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Resolve which custom Auth domain to use before anything talks to Firebase Auth --
    // see AuthDomainResolver for why this isn't a hardcoded string. Runs once per process;
    // if it hasn't resolved yet, FirebaseAuth just uses its own default domain for that
    // brief window, which is a safe fallback rather than a blocking one.
    LaunchedEffect(Unit) {
        val domain = com.cdnhunter.app.vpn.AuthDomainResolver.resolveActiveDomain()
        auth.setCustomAuthDomain(domain)
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(400)))
                .togetherWith(fadeOut(tween(250)))
        },
        label = "rootScreen",
    ) { s ->
        when (s) {
            RootScreen.AUTH -> AuthScreen(onSignedIn = { screen = RootScreen.ENTERING })
            RootScreen.ENTERING -> EnteringAppLoader(onDone = { screen = RootScreen.HOME })
            RootScreen.HOME -> AppScreen(onSignOut = {
                com.cdnhunter.app.vpn.CdnVpnService.stop(context)
                auth.signOut()
                screen = RootScreen.AUTH
            })
        }
    }
}

/**
 * A brief, branded loading beat shown every time the person lands on the home screen —
 * both right after signing in and (via [RootScreen.ENTERING]'s initial state) on a cold
 * launch where Firebase already has a current user. Purely cosmetic: there is no real
 * async work gating this, just a short pause so the transition from auth to the VPN
 * screen never feels like an abrupt cut.
 */
@Composable
private fun EnteringAppLoader(onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var typedChars by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        visible = true
        delay(200)
        val name = "Thallo"
        for (i in 1..name.length) {
            typedChars = i
            delay(65)
        }
        delay(500)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0F)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(tween(500)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = com.cdnhunter.app.R.drawable.logo_thallo),
                    contentDescription = "Thallo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(200.dp),
                )
                Spacer(Modifier.height(22.dp))
                Text("Private. Fast. Secure.", fontSize = 13.sp, color = Color(0xFF8B8E98))
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
fun CDNHunterTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The whole UI (every Ananas* color in AppScreen.kt) is hardcoded dark
    // regardless of this theme -- the old Light/Auto option was removed from
    // Settings, because the app was never actually designed to look right in
    // a light scheme. Previously this still switched to lightColorScheme()
    // whenever the PHONE's system theme was light (ThemeMode.SYSTEM ->
    // isSystemInDarkTheme()), silently mismatching the always-dark UI.
    // Material3's default ripple/indication color derives from the active
    // color scheme's content color, so on a light-system phone every tap
    // rendered a dark/near-black ripple box on top of visually dark rows --
    // looked exactly like a rendering glitch ("black box on tap"). Always
    // using the dark scheme here keeps it in sync with the UI that's
    // actually drawn, regardless of the phone's own system theme.
    val amoledMode = remember { AppSettings.amoledMode(context) }
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF3B82F6),
        onPrimary = Color.White,
        secondary = Color(0xFF8B5CF6),
        onSecondary = Color.White,
        background = if (amoledMode) Color(0xFF000000) else Color(0xFF0A0A0A),  // pure black vs dark gray
        onBackground = Color(0xFFFAFAFA),
        surface = if (amoledMode) Color(0xFF000000) else Color(0xFF0A0A0A),     // pure black vs dark gray
        onSurface = Color(0xFFFAFAFA),
        error = Color(0xFFEF4444),
        onError = Color.White,
        outline = Color(0xFF222222),
    )

    CompositionLocalProvider(
        LocalThemeMode provides ThemeMode.DARK
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            content = content
        )
    }
}
