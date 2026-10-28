plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.phototidy"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.phototidy"
        minSdk = 30          // Android 11+ (Nothing Phone 3a runs Android 15)
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"

        // Only include code for 64-bit ARM chips (the Nothing Phone 3a and nearly every
        // phone from the last years). Keeps the app ~4x smaller.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // A fixed key so each new build installs as an update over the previous one.
    // It's only for this personal, sideloaded app.
    signingConfigs {
        create("release") {
            storeFile = file("phototidy.keystore")
            storePassword = "phototidy"
            keyAlias = "phototidy"
            keyPassword = "phototidy"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.runtime:runtime-livedata")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device AI (bundled models – work offline, free)
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
