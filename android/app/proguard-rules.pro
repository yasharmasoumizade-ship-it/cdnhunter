# R8 configuration for the release build (isMinifyEnabled + isShrinkResources).
#
# Bias throughout: keep anything reached from outside the Java/Kotlin type system —
# from Go over JNI, from reflection, or from the manifest. R8 can only see calls it
# can trace, and a missing keep rule here does not fail the build, it fails at
# runtime on a user's phone the moment they tap Connect. Where there was any doubt,
# the rule is present.

# ---------------------------------------------------------------------------
# Crash reports stay readable
# ---------------------------------------------------------------------------
# The app has a crash-log row users copy out of Settings > Diagnostics, so stack
# traces must still carry line numbers. SourceFile is renamed to a constant (it
# carries nothing useful once classes are renamed) but line numbers are preserved,
# which is what maps a frame back through the build's retrace mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures, annotations and inner-class links: needed by anything doing
# reflection over parameterized types (Gson's TypeToken above all).
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions

# ---------------------------------------------------------------------------
# gomobile / mihomo JNI bridge  (the part that must not be touched)
# ---------------------------------------------------------------------------
# libmihomo.aar is gomobile-generated. Two directions cross the language boundary:
#
#   Kotlin -> Go   Mobile.start/stop/setProtector/trafficUp/trafficDown/protectLog/
#                  coreLog are static methods whose bodies are native; the Go side
#                  resolves them by their exact JNI name.
#   Go -> Kotlin   Protector.protect(long) is invoked from Go on the object
#                  MihomoBridge registers — an anonymous class R8 would otherwise
#                  happily rename or inline out of existence. Losing that callback
#                  is the failure mode where mihomo's own outbound socket gets
#                  captured by the TUN it just created and nothing connects.
#
# go.** is gomobile's runtime support (go.Seq and friends) plus the generated proxy
# classes; every one of them is referenced by name from libgojni.so.
-keep class go.** { *; }
-keep interface go.** { *; }
-keepclassmembers class go.** { *; }
-keep class com.cdnhunter.mihomo.** { *; }
-keep interface com.cdnhunter.mihomo.** { *; }
-keep class * implements com.cdnhunter.mihomo.mobile.Protector { *; }
-dontwarn go.**
-dontwarn com.cdnhunter.mihomo.**

# Any native method anywhere, and the class declaring it, keeps its name.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# The whole vpn package stays as-is. It is small, it is where the JNI bridge, the
# VpnService and the config/YAML builder live, and a rename or an over-eager inline
# in there breaks connecting rather than degrading some screen. Obfuscating it would
# buy little — the protocol strings it builds sit in the binary either way — so
# correctness wins.
-keep class com.cdnhunter.app.vpn.** { *; }

# ---------------------------------------------------------------------------
# Gson (reflection over field names and generic types)
# ---------------------------------------------------------------------------
# Gson reads fields by name off whatever class it is handed, so any model it touches
# must keep its field names, and TypeToken subclasses must keep their generic
# signature (hence -keepattributes Signature above). Upstream's recommended rules,
# plus a blanket keep on this app's own model package.
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn sun.misc.**
-keep class com.cdnhunter.app.data.** { *; }

# ---------------------------------------------------------------------------
# Manifest-declared entry points
# ---------------------------------------------------------------------------
# AGP's default rules already keep these through the manifest; stated explicitly so
# a manifest edit cannot quietly drop one.
-keep class com.cdnhunter.app.CdnHunterApp { *; }
-keep class com.cdnhunter.app.MainActivity { *; }
-keep class com.cdnhunter.app.BuildConfig { *; }

# ---------------------------------------------------------------------------
# Jetpack Security / Tink  (SecurePrefs)
# ---------------------------------------------------------------------------
# Tink registers its key managers and parses key protos reflectively. If R8 strips
# one, EncryptedSharedPreferences fails to open — which SecurePrefs survives by
# falling back to plaintext prefs, so the symptom would be silently losing at-rest
# encryption rather than a crash. Keep the lot.
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.joda.time.**

# ---------------------------------------------------------------------------
# Third-party libraries
# ---------------------------------------------------------------------------
# OkHttp: optional TLS providers it references but this app never bundles.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing + the embedded scanner: the scanner instantiates decoders and camera
# classes by name from its own configuration.
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# Coil's SVG decoder delegates to AndroidSVG, which resolves SVG element and
# attribute handlers reflectively — and every flag in assets/flags is an SVG.
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
-dontwarn coil.**

# Lottie ships its own rules; silence the optional dependencies it references.
-dontwarn com.airbnb.lottie.**

# Kotlin/coroutines internals R8 sometimes cannot see through.
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Strip chatty logging from the release build
# ---------------------------------------------------------------------------
# Any Log.v/d/i left in the source is dead weight — and worse, a place a config
# URL, a resolved IP or a parse error could reach logcat on a shipped build. R8
# treats these as side-effect-free and removes the calls (and folds away the
# string concatenation that fed them) in release. Log.w/Log.e are deliberately
# NOT listed: warnings and errors stay, so a real fault is still diagnosable from
# the crash-log row. Debug builds keep everything (they are not minified).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
