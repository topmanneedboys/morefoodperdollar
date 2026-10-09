plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

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

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".nextpreview"
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
        }
    }
}

// Keep the isolated Compose preview independent of the production Firebase app identity.
tasks.matching { it.name == "processDebugGoogleServices" }.configureEach {
    enabled = false
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
    debugImplementation(composeBom)
    debugImplementation("androidx.activity:activity-compose:1.13.0")
    debugImplementation("androidx.browser:browser:1.8.0")
    debugImplementation("androidx.webkit:webkit:1.14.0")
    debugImplementation("androidx.lifecycle:lifecycle-viewmodel-android:2.9.4")
    debugImplementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    debugImplementation("androidx.compose.ui:ui-tooling")
    releaseCompileOnly(composeBom)
    releaseCompileOnly("androidx.compose.runtime:runtime")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
