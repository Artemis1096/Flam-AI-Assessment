plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.andro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.andro"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ JNI C++ Configuration
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Add these arguments to help CMake find OpenCV
                arguments += listOf(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }

        // ✅ Target ABIs (supported architectures)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // ✅ Link Gradle with your CMake build script
    externalNativeBuild {
        cmake {
            path = file("../jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // ✅ Enable ViewBinding if you use XML layouts
    buildFeatures {
        viewBinding = true
    }

    ndkVersion = "29.0.14206865"

    // ✅ Tell Gradle where to find OpenCV's .so libraries
    sourceSets {
        getByName("main") {
            // Point to OpenCV's native libs - simplified
            jniLibs.srcDirs("../OpenCV-android-sdk/sdk/native/libs")
        }
    }

    // ✅ Packaging options to avoid duplicate .so files
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ✅ Add CameraX dependencies if you're using camera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}