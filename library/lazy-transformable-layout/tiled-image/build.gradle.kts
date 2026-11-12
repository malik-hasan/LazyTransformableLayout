import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

val libraryName = "lazytransformablelayout"

kotlin {
    android {
        namespace = "oats.mobile.$libraryName.tiledimage"
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":library:lazy-transformable-layout"))
            api(libs.zoomimage.core)
            implementation(libs.compose.foundation)
            implementation(libs.kermit)
            implementation(libs.zoomimage.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "lazy-transformable-layout-tiled-image")
}
