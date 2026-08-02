plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.midipad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.midipad"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Populated by CI from repository secrets. Absent locally, in which
        // case assembleRelease produces an unsigned APK and that is fine.
        create("release") {
            val keystore = System.getenv("MIDIPAD_KEYSTORE")
            if (!keystore.isNullOrBlank()) {
                storeFile = file(keystore)
                storePassword = System.getenv("MIDIPAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIDIPAD_KEY_ALIAS")
                keyPassword = System.getenv("MIDIPAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (!System.getenv("MIDIPAD_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
