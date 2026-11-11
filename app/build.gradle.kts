plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.ales.dochledac"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.ales.dochledac"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Vlastní podpisový klíč – díky němu jde každá nová verze nainstalovat přes starou
    // (index a nastavení zůstanou zachované).
    signingConfigs {
        create("own") {
            storeFile = file("dochledac.jks")
            storePassword = "dochledac"
            keyAlias = "dochledac"
            keyPassword = "dochledac"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("own")
        }
        debug {
            signingConfig = signingConfigs.getByName("own")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
