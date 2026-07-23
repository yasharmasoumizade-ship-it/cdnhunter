plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
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

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = "cdnhunter123"
            keyAlias = "cdnhunter"
            keyPassword = "cdnhunter123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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

    // QR code generation + scanning (config sharing, v2rayNG-style)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
