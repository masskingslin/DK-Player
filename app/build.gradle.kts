import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dk.tvplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dk.tvplayer"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ---- Release signing ----------------------------------------------------------
    // Reads the key from environment variables (GitHub Actions) or a local
    // keystore.properties file (never commit it). If neither is present the release build
    // still succeeds but is signed with the throw-away debug key and prints a warning —
    // such an APK can NOT be installed over one signed with your real key, and every CI
    // run produces a differently-signed APK, so set the secrets up once (see README).
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

    val releaseStoreFile = signingValue("DK_KEYSTORE_FILE", "storeFile")
    val releaseStorePassword = signingValue("DK_KEYSTORE_PASSWORD", "storePassword")
    val releaseKeyAlias = signingValue("DK_KEY_ALIAS", "keyAlias")
    val releaseKeyPassword = signingValue("DK_KEY_PASSWORD", "keyPassword")
    val hasReleaseKey = releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null && file(releaseStoreFile).exists()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 code shrinking + optimisation and resource shrinking. Obfuscation is
            // switched off in proguard-rules.pro on purpose so crash reports stay readable.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: no release keystore configured — signing with the DEBUG key.")
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            // Uncompressed + page-aligned native libs (16 KB aligned with AGP 8.5.1+).
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 has Kotlin built in: jvmTarget follows compileOptions above, and the old
    // kotlinOptions block / org.jetbrains.kotlin.android plugin no longer exist.
    buildFeatures {
        compose = true
    }
    lint {
        // This check is a known false-positive trigger from transitive androidx.fragment
        // resolution and shouldn't block a release build on its own; the fragment-ktx
        // pin above is the real fix, this is just a safety net.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

// Room exports its schema JSON here on every build — commit app/schemas/ so future
// migrations can be tested against real history.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core & Lifecycle KTX
    implementation("androidx.core:core-ktx:1.19.1")
    // Per-app language / locale picker (AppCompatDelegate.setApplicationLocales works app-wide
    // since 1.6.0, even without AppCompatActivity — see AndroidManifest's AppLocalesMetadataHolderService).
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Explicit pin: some transitive dependency was resolving an old androidx.fragment
    // version, which trips the "InvalidFragmentVersionForActivityResult" fatal lint
    // check against MainActivity's registerForActivityResult() call.
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Media3 / ExoPlayer
    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // Media3 Cast (Chromecast support)
    implementation("androidx.media3:media3-cast:$media3Version")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")

    // Room Database
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore (settings persistence / export-import / theme)
    // Installs the baseline profile even when the APK is sideloaded (not from Play).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // Coroutines & Networking
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}