plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlinx-serialization")
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))

    implementation(deps.libs.okHttp3)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
}

android {
    resourcePrefix("rawg_")
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.omnidroid.metadata.rawg"
}
