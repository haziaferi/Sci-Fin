// :androidApp -- the Android host. Owns the Activity, the WorkManager workers' registration and
// the runtime notification permission (§11.5); everything it shows comes from :shared.
// AGP 9 compiles Kotlin itself (built-in Kotlin), so no Kotlin Android plugin is applied.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

// Release signing, read from native/local.properties (machine-local, git-ignored) and never committed:
// this repository is public, and a keystore in it would let anyone sign an APK that Android accepts as an
// update of every app signed with that key. The four values are all present or all absent -- some but not
// all is a typo, never an intent; a blank value counts as absent. Absent, the release APK is built unsigned,
// as before (CI does not sign). A relative storeFile is read from native/, where local.properties is.
//   scifin.signing.storeFile=C:/Users/<you>/.android/debug.keystore
//   scifin.signing.storePassword=…   scifin.signing.keyAlias=…   scifin.signing.keyPassword=…
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { reader -> load(reader) }
}
val signing: Map<String, String?> = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .associateWith { localProperties.getProperty("scifin.signing.$it")?.takeIf { it.isNotBlank() } }
if (signing.values.any { it != null } && signing.values.any { it == null }) {
    throw GradleException("local.properties sets only some of scifin.signing.*: missing " +
        signing.filterValues { it == null }.keys.joinToString { "scifin.signing.$it" })
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

    if (signing.values.all { it != null }) {
        signingConfigs.create("local") {
            storeFile = rootProject.file(signing.getValue("storeFile")!!)
            storePassword = signing.getValue("storePassword")
            keyAlias = signing.getValue("keyAlias")
            keyPassword = signing.getValue("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("local")
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
