import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val packageName = "mobile.oats.lazytransformablelayout.demo"

kotlin {
    android {
        namespace = "$packageName.shared"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        androidResources.enable = true
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "DemoShared"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library:lazy-transformable-layout"))
            implementation(project(":library:lazy-transformable-layout:tiled-image"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.navigation3.ui)
        }

        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation3.browser)
        }
    }
}

compose.resources {
    packageOfResClass = "$packageName.resources"
}

val macTrackpadGesturesJvmArg = "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED"

tasks.withType<JavaExec>().configureEach {
    jvmArgs(macTrackpadGesturesJvmArg)
}

compose.desktop {
    application {
        mainClass = "$packageName.MainKt"
        jvmArgs += macTrackpadGesturesJvmArg

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LazyTransformableLayoutDemo"
            packageVersion = "1.0.0"
        }

        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}
