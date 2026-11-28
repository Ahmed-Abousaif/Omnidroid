plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.omnidroid.ra"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":omnidroid-chd"))
    implementation(deps.libs.androidx.ktx.coreKtx)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.timber)
}
