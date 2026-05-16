plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.falldetectapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.falldetectapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only 64-bit ABIs Play Store accepts.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Required for Android 15+ 16-KB page-size compliance:
    // - useLegacyPackaging=false leaves native libs uncompressed in the APK so
    //   the linker can mmap them with 16 KB alignment.
    // - keepDebugSymbols keeps stack-trace symbols stripped in release while
    //   not breaking alignment.
    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }

    // Don't compress the model so it can be mmap'd at runtime.
    androidResources {
        noCompress += setOf("tflite")
    }

    signingConfigs {
        create("release") {
            // Wired via gradle.properties / env vars at build time. Keep keystore
            // out of the repo. See PROJECT_NOTES.md "Signing" section.
            val storeFilePath = project.findProperty("SAFEMOTION_KEYSTORE") as String?
            val storePass = project.findProperty("SAFEMOTION_KEYSTORE_PASSWORD") as String?
            val keyAliasProp = project.findProperty("SAFEMOTION_KEY_ALIAS") as String?
            val keyPass = project.findProperty("SAFEMOTION_KEY_PASSWORD") as String?
            if (storeFilePath != null && storePass != null && keyAliasProp != null && keyPass != null) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasProp
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // LiteRT is the official 16-KB-aligned successor to TensorFlow Lite.
    // Same Interpreter API → no source changes needed in TFLiteRunner.kt.
    implementation("com.google.ai.edge.litert:litert:1.4.0")
    implementation("com.google.ai.edge.litert:litert-api:1.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
