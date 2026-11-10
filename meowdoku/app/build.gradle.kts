plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ro.meowdoku.screen"
    compileSdk = 35
    defaultConfig {
        applicationId = "ro.meowdoku.screen"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
