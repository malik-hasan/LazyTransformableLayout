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
    android {
        namespace = packageName
        compileSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        named("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
            implementation(libs.junit)
        }

        jvmTest.dependencies {
            implementation(libs.jetbrains.compose.ui.test)
            implementation(
                with(System.getProperty("os.name").lowercase()) {
                    val arm = System.getProperty("os.arch").contains("aarch64")
                    when {
                        contains("mac") -> if (arm) libs.jetbrains.compose.desktop.macos.arm64 else libs.jetbrains.compose.desktop.macos.x64
                        contains("win") -> libs.jetbrains.compose.desktop.windows.x64
                        else -> if (arm) libs.jetbrains.compose.desktop.linux.arm64 else libs.jetbrains.compose.desktop.linux.x64
                    }
                }
            )
        }
    }
}

tasks.withType<Test> {
    systemProperty("java.awt.headless", "true")
    systemProperty("apple.awt.UIElement", "true")
}

mavenPublishing {
    coordinates(
        groupId = packageName,
        artifactId = libraryName,
        version = "1.0.0"
    )
}
