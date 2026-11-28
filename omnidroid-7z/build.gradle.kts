plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.omnidroid.sevenzip"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            debugSymbolLevel = "SYMBOL_TABLE"
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(deps.libs.kotlinxCoroutinesAndroid)

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
