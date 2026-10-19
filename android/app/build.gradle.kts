import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is supplied by the operator, never by the repository.
// Create android/keystore.properties (git-ignored) with storeFile, storePassword,
// keyAlias and keyPassword; without it the release variant builds unsigned so a
// developer can still compile and test locally.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val releaseKeystorePath = keystoreProperties.getProperty("storeFile")
val releaseSigningConfigured = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).isFile

android {
    namespace = "com.valuepilot.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.valuepilot.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 10133
        versionName = "101.1.32"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".nextpreview"
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

// Keep the isolated preview on its own Firebase app identity. The debug task stays
// disabled until the separately registered preview configuration is present.
tasks.matching { it.name == "processDebugGoogleServices" }.configureEach {
    enabled = file("src/debug/google-services.json").isFile
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(project(":shared-core"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation(composeBom)
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-android:2.9.4")
    implementation("androidx.compose.runtime:runtime")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
