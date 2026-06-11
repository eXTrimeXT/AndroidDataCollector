import org.apache.tools.ant.property.LocalProperties
import org.gradle.kotlin.dsl.implementation
import java.util.Base64
import java.util.Properties
import kotlin.collections.toByteArray

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.extreme.androiddatacollector"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.extreme.androiddatacollector"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("my_debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "android"
            keyPassword = "android"
        }
        create("my_release") {
            storeFile = file("release.keystore")
            storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("my_debug")
            isMinifyEnabled = false
        }
        release {
            // Для релиза используем отдельный release.keystore!
            signingConfig = signingConfigs.getByName("my_release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.register("printSigningCertSha256") {
    doLast {
        val keystoreFile = file("./release.keystore")
        val keystorePassword = ""
        val keyAlias = "android"

        val output = providers.exec {
            commandLine(
                "keytool", "-list", "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", keyAlias,
                "-storepass", keystorePassword
            )
        }.standardOutput.asText.get()

        val sha256Hex = output.lines()
            .firstOrNull { it.trim().startsWith("SHA256:") }
            ?.substringAfter(":")?.trim()
            ?.replace(":", "")

        if (sha256Hex.isNullOrBlank()) {
            println("Не удалось найти SHA256 в выводе keytool!")
            return@doLast
        }

        val sha256Bytes = sha256Hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val base64 = Base64.getEncoder().encodeToString(sha256Bytes)

        println("SHA-256 (hex): $sha256Hex")
        println("SHA-256 (Base64) for QR-code: $base64")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // HTTP-клиент и парсер JSON
    implementation(libs.okhttp)
    implementation(libs.gson)

    // WorkManager для фоновых задач
    implementation(libs.androidx.work.runtime.ktx)
}