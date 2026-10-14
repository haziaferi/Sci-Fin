// :androidApp -- the Android host. Owns the Activity, the WorkManager workers' registration and
// the runtime notification permission (§11.5); everything it shows comes from :shared.
// AGP 9 compiles Kotlin itself (built-in Kotlin), so no Kotlin Android plugin is applied.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.scifin"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.scifin"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
