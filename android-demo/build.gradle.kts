import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_21)

android {
    val packageName = "oats.mobile.lazytransformablelayout.demo"
    namespace = packageName
    compileSdk = libs.versions.android.targetSdk.get().toInt()

    defaultConfig {
        applicationId = packageName
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":library"))
    implementation(libs.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.navigation3.ui)
    implementation(libs.navigation3.runtime)
    implementation("io.github.panpf.zoomimage:zoomimage-compose-glide:1.6.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
