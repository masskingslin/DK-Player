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
