// :desktopApp -- the Windows host (§1). A Compose Desktop window over :shared, packaged as a
// per-user MSI. The shape of nativeDistributions follows Builds/Tendril's proven build.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.withType<Test> { useJUnit() }

compose.desktop {
    application {
        mainClass = "app.scifin.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Sci-Fin"
            // jpackage rejects a 0.x installer version; this is the installer's, not the project's.
            packageVersion = "1.0.0"
            windows {
                // Must never change once any build has been installed anywhere.
                upgradeUuid = "3f0c4a6e-9b7d-4c1e-8a2f-5d6e7f8a9b0c"
                menuGroup = "Sci-Fin"
                // the exe's and shortcut's icon, 16-256px frames from tools/build_icon.py
                iconFile.set(project.file("icon/sci-fin.ico"))
                shortcut = true
                perUserInstall = true
            }
        }
    }
}
