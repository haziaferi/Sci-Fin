// :shared -- everything the spec calls "shared logic" (§1): the data model (§12), the financial
// engine (§3, §5, §6, §7, §9), the reminder derivation (§3.11), backup and import (§4.7, §4.3),
// and the notification seam's `expect` half. Two targets, Android and desktop JVM, both JVMs --
// which is what the jvmCommon source set below is for.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "app.scifin"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
    // expect/actual classes (SciFinDatabase, NotificationScheduler, Room's generated constructor) are Beta in Kotlin 2.x
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "app.scifin.shared"
        compileSdk = 37
        minSdk = 30
        androidResources { enable = true }   // the reminders' status-bar icon (res/drawable/ic_stat_scifin.xml)
    }

    jvm("desktop")

    // A manual dependsOn edge (jvmCommon, below) switches the default hierarchy off unless this
    // is called first -- same lesson Tendril's shared module recorded.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // Both targets are JVMs, so java.time and java.util.UUID can be shared here without
        // being reachable from a hypothetical future non-JVM target. §12's "types of time" rule
        // (LocalDate for calendar days, Instant for loggedAt) is implemented against java.time.
        val jvmCommon = create("jvmCommon") { dependsOn(commonMain.get()) }
        androidMain { dependsOn(jvmCommon) }
        getByName("desktopMain") { dependsOn(jvmCommon) }

        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        // Tests are JVM tests run on the desktop target: the engine is pure Kotlin and the
        // bundled SQLite driver gives Room a real database without a device.
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            // headless screen renders (ImageComposeScene) need skiko's native library, which currentOs carries
            implementation(compose.desktop.currentOs)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

// §12's storage rule: every schema version Room compiles is written to shared/schemas/ and
// committed, because a migration can only be generated FROM a version whose JSON is on disk.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
// Both KSP tasks write the same file; with org.gradle.parallel they must not do it at once.
tasks.matching { it.name == "kspKotlinDesktop" }.configureEach {
    mustRunAfter("kspAndroidMain")
}
