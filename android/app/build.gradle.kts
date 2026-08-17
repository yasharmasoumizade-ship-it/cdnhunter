import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    // Signing credentials must come from the environment in CI or a developer's
    // local secret store. Do NOT keep a keystore or plain-text passwords in git.
    // If the environment variables are not present, generate a temporary
    // keystore at ../keystore.jks (relative to android/app) so local builds and CI
    // runs without configured secrets still succeed. Ensure parent directories
    // exist before running keytool. This keeps builds working while we migrate CI
    // to restore a production signing key from secrets.
    signingConfigs {
        create("release") {
            // Read env vars if present
            val keystoreFileEnv = System.getenv("CDNHUNTER_KEYSTORE_FILE")
            val storePwdEnv = System.getenv("CDNHUNTER_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("CDNHUNTER_KEY_ALIAS")
            val keyPwdEnv = System.getenv("CDNHUNTER_KEY_PASSWORD")

            // Default path is one level up from this module: android/keystore.jks
            val keystorePath = keystoreFileEnv ?: "../keystore.jks"
            val alias = keyAliasEnv ?: "cdnhunter"

            // If no env-provided keystore/password, generate a temporary keystore
            if (keystoreFileEnv == null || storePwdEnv == null || keyAliasEnv == null || keyPwdEnv == null) {
                // Only generate if the file doesn't already exist
                val ksFile = file(keystorePath)
                // Ensure parent directory exists (fixes CI FileNotFound when parent missing)
                ksFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                if (!ksFile.exists()) {
                    val genStorePwd = UUID.randomUUID().toString().replace("-", "").take(16)
                    val genKeyPwd = genStorePwd
                    println("[build] Generating temporary keystore at ${ksFile.path}")
                    project.exec {
                        commandLine(
                            "keytool",
                            "-genkeypair",
                            "-keystore", ksFile.path,
                            "-storepass", genStorePwd,
                            "-alias", alias,
                            "-keypass", genKeyPwd,
                            "-keyalg", "RSA",
                            "-keysize", "2048",
                            "-validity", "10000",
                            "-dname", "CN=CDN Hunter, OU=Dev, O=CDN Hunter, L=Unknown, S=Unknown, C=US"
                        )
                    }
                    storeFile = ksFile
                    storePassword = genStorePwd
                    keyAlias = alias
                    keyPassword = genKeyPwd
                } else {
                    // File exists but env vars missing: try a safe fallback password used in CI previously
                    // Note: this is a temporary fallback to allow CI to proceed; for production, add secrets.
                    val fallbackPwd = storePwdEnv ?: "cdnhunter123"
                    println("[build][warn] Keystore exists at ${ksFile.path} and CDNHUNTER_KEYSTORE_PASSWORD is not set; using fallback password. Add secrets for secure signing.")
                    val keyPwd = keyPwdEnv ?: fallbackPwd
                    storeFile = ksFile
                    storePassword = fallbackPwd
                    keyAlias = alias
                    keyPassword = keyPwd
                }
            } else {
                // All env vars present: use them
                storeFile = file(keystorePath)
                storePassword = storePwdEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPwdEnv
            }
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
            // Forcing debug builds to use the release signing config prevents
            // accidental mismatches between debug and release when testing
            // upgrade paths in CI. Local developers can set the same env vars or
            // run a local debug signing flow.
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
    
    // Coil image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // QR code generation + scanning (config sharing, v2rayNG-style)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
