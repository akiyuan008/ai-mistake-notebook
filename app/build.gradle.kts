plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jiancuoti.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jiancuoti.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "5.15.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/keystore/jiancuoti-release.jks")
            storePassword = "jiancuoti2026"
            keyAlias = "jiancuoti"
            keyPassword = "jiancuoti2026"
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

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // 相机
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // 图片加载
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
