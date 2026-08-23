plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    lint {
        disable += "NewApi"
        disable += "OldTargetApi"
    }

    namespace = "com.cdnhunter.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cdnhunter.scanner"
        minSdk = 24
        targetSdk = 34
        // versionCode is overridden by CI (see build-unified.yml) using the GitHub
        // Actions run number, so every release always has a strictly higher
        // versionCode than the last. This matters: if versionCode doesn't increase,
        // Android can keep stale native (.so) libraries from a previous install
        // instead of replacing them on update, even though the APK itself changed —
        // exactly what caused an old NoSuchMethodError crash to reappear on a device
        // that had "updated" to a new build.
        versionCode = (System.getenv("CI_VERSION_CODE")?.toIntOrNull()) ?: 4
        versionName = "3.0"
    }

    // Splits the native (.so) libraries per-ABI instead of bundling all four
    // architectures into one APK. This is what was making the APK ~150MB and
    // slow to install — most phones only need arm64-v8a.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true // also keep one fallback APK that works on any device
        }
    }

    // Signing credentials come exclusively from the environment. There is NO
    // hardcoded password fallback: if the CDNHUNTER_* env vars are not set the
    // release signing config resolves to empty strings and the signing step fails
    // loudly, rather than silently signing with a password that lives in git
    // history. Set these as GitHub Actions repository secrets (Settings →
    // Secrets and variables → Actions → New repository secret):
    //   CDNHUNTER_KEYSTORE_FILE      path to keystore.jks (defaults to ../keystore.jks)
    //   CDNHUNTER_KEYSTORE_PASSWORD  keystore (store) password        [required]
    //   CDNHUNTER_KEY_ALIAS          key alias (defaults to "cdnhunter")
    //   CDNHUNTER_KEY_PASSWORD       key password                    [required]
    // and expose them to the build step in build-unified.yml via `env:`. Rotating
    // the key that was previously committed is what makes this actually protect
    // anything — note that a rotated key breaks in-place updates for anyone who
    // installed a build signed with the old key.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("CDNHUNTER_KEYSTORE_FILE") ?: "../keystore.jks")
            storePassword = System.getenv("CDNHUNTER_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("CDNHUNTER_KEY_ALIAS") ?: "cdnhunter"
            keyPassword = System.getenv("CDNHUNTER_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            // R8 on: shrink + obfuscate. The keep rules that make this safe live in
            // proguard-rules.pro — chiefly the gomobile/mihomo JNI surface (go.**,
            // com.cdnhunter.mihomo.**, the Protector callback Go invokes) and the
            // whole com.cdnhunter.app.vpn package, none of which R8 can trace into.
            // Resource shrinking is low-risk here: res/ holds only the launcher icon,
            // two values files and network_security_config, and the 265 flag SVGs are
            // in assets/, which resource shrinking never touches.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG is what the sensitive log sites are gated on (see
        // CdnVpnService.startVpn and SubscriptionParser): AGP 8 does not generate
        // BuildConfig unless it is asked to, so this has to be on for those gates
        // to compile.
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

// Guard against accidentally shipping two gomobile-built AARs at once
// (e.g. libv2ray.aar (old) + libmihomo.aar (new)). Both embed the same Go runtime classes
// (go.Seq, go.Universe, ...) and the same libgojni.so, so Gradle's
// mergeReleaseNativeLibs / checkReleaseDuplicateClasses will fail the build
// with a confusing "2 files found with path lib/arm64-v8a/libgojni.so"
// error if more than one ends up in app/libs. Fail fast with a clear message
// instead. Keep any AAR not currently wired into the app (e.g. a staged
// mihomo.aar) outside of app/libs — see android/mihomo-staging/.
val aarFiles = fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))).files
require(aarFiles.size <= 1) {
    "Found ${aarFiles.size} .aar files in app/libs (${aarFiles.joinToString { it.name }}). " +
        "Only one gomobile-built AAR (e.g. libmihomo.aar) may be present at a time — " +
        "multiple gomobile AARs collide on shared Go runtime classes and libgojni.so. " +
        "Move any AAR not currently used by the app out of app/libs."
}

dependencies {
    // Local libs (libmihomo.aar built by CI — see mihomo-mobile/ + .github/workflows/build-unified.yml)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation("androidx.core:core-ktx:1.12.0")
    // At-rest encryption for the two SharedPreferences files that hold server
    // credentials and subscription URLs (see vpn/SecurePrefs.kt). 1.1.0-alpha06 is
    // the version that ships the MasterKey builder API; 1.0.0 only has the
    // deprecated MasterKeys helper.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.3.0")
    
    // Accompanist (blur, permissions, system UI)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    
    // Coil image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // QR code generation + scanning (config sharing, v2rayNG-style)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
