import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    id("org.jetbrains.compose") version "0.3.0-build136"
}

repositories {
    google()
    jcenter()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    // NOTE: For filament to work...
    //  - Copied content of `filament-dist-v1.9.9/lib/x86_64` to ~/Library/Java/Extensions
    //  - `xattr -d com.apple.quarantine` on binaries
    implementation(fileTree("filament-dist-v1.9.9/lib")) // Imports the filament libs (jars)
    implementation(fileTree("lib")) // kotlin-math
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

/**
 * packages with jpackage, some docs:
 *
 * https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution
 *
 * https://docs.oracle.com/en/java/javase/14/jpackage/basic-packaging.html#GUID-1E2A4F61-1390-4FC3-955B-BD69A16FCA2C
 * https://docs.oracle.com/en/java/javase/14/jpackage/packaging-tool-user-guide.pdf
 */
compose.desktop {
    application {
        mainClass = "com.kanawish.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "filaDesktopSandbox"
        }
    }
}
