plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))

    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.hilt.android)
    implementation(deps.libs.kotlinxCoroutinesAndroid)

    ksp(deps.libs.androidx.room.compiler)
    ksp(deps.libs.hilt.compiler)
}

android {
    resourcePrefix("libretrodb_")
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid.metadata.libretrodb"
}
