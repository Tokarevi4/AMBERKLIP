plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.klippercontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.klippercontrol"
        minSdk = 19
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        isTransitive = false
    }

    implementation ("com.google.zxing:core:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Material 2 is used on API 21+.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.multidex:multidex:2.0.1")
}

configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:3.12.13")
        force("com.squareup.okhttp3:logging-interceptor:3.12.13")

        force("io.ktor:ktor-client-core:2.3.12")
        force("io.ktor:ktor-client-okhttp:2.3.12")
        force("io.ktor:ktor-client-websockets:2.3.12")
        force("io.ktor:ktor-client-serialization:2.3.12")
    }
}


