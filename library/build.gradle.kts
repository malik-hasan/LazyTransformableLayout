@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

val libraryName = "lazytransformablelayout"
val packageName = "oats.mobile.$libraryName"

kotlin {
    androidLibrary {
        namespace = packageName
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }

        commonMain.dependencies {
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.kermit)
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = packageName,
        artifactId = libraryName,
        version = "1.0.0"
    )
}
