plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlinx-serialization")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-Xcontext-receivers",
            )
    }
    namespace = "com.omnidroid.touchinput"

    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(project(":retrograde-util"))

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.geometry)
    implementation(deps.libs.androidx.compose.runtime)
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.extendedIcons)

    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.material)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    api(project(":omnidroid-padkit"))
    api(deps.libs.collectionsImmutable)

    implementation(kotlin(deps.libs.kotlin.stdlib))
}
