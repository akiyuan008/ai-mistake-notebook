plugins {
    id("com.android.application")
}

android {
    namespace = "com.jiancuoti.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jiancuoti.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "4.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.13.0")
}
