# ===========================================================================
# DK Player - Production R8 / ProGuard Configuration
# ===========================================================================

# ---------------------------------------------------------------------------
# AndroidX Media3 / ExoPlayer
# ---------------------------------------------------------------------------
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }

# Keep native decoder and DRM components
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn androidx.media3.**

# ---------------------------------------------------------------------------
# Room Database & SQLite
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Keep local database entities and data models
-keep class com.dk.tvplayer.data.local.** { *; }

# ---------------------------------------------------------------------------
# Jetpack Compose & Compose for TV
# ---------------------------------------------------------------------------
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}
-dontwarn androidx.tv.**
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# Kotlin Coroutines & Flow
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.Dispatchers {
    public static <fields>;
}

# ---------------------------------------------------------------------------
# Network & XML Parsing (M3U / XMLTV)
# ---------------------------------------------------------------------------
-keepclassmembers class * {
    public static ** valueOf(java.lang.String);
    public static **[] values();
}
-dontwarn org.xmlpull.v1.**

# ---------------------------------------------------------------------------
# Release build hardening (R8 shrinking is ON)
# ---------------------------------------------------------------------------
# Keep readable class names so crash reports shared from the app (CrashLogger) can be
# read directly without a mapping file. Shrinking + optimisation still apply. Remove this
# line (and keep app/build/outputs/mapping/release/mapping.txt) to also obfuscate.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Classes looked up by name from the manifest metadata / by libraries (not seen by R8).
-keep class com.dk.tvplayer.cast.DkCastOptionsProvider { *; }
-keep class androidx.appcompat.app.AppLocalesMetadataHolderService { *; }

# Foreground/Media services and the Application are manifest entries (kept automatically),
# but their @UnstableApi media3 callbacks are invoked reflectively by the framework.
-keep class com.dk.tvplayer.player.PlaybackService { *; }
-keep class com.dk.tvplayer.download.DkDownloadService { *; }

# Google Cast framework
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.**

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
