plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ua.senses.shell"
    compileSdk = 34

    defaultConfig {
        applicationId = "ua.senses.shell"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.4"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Нужно, чтобы бинарник движка (libqemu.so) лежал на диске и мог запускаться
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
