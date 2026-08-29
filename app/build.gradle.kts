android {
    ...
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signs the release APK with the default Android debug key
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
