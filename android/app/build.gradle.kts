plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.valuepilot.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.valuepilot.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 10122
        versionName = "101.1.21"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "M12_3_QUALIFICATION_PROBE", "false")
        }
    }
}

dependencies {
    implementation(project(":shared-core"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
