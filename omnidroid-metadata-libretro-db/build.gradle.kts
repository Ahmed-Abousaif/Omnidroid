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
    implementation(deps.libs.retrofit)
    implementation(deps.libs.okHttp3)

    ksp(deps.libs.androidx.room.compiler)
    ksp(deps.libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
}

android {
    resourcePrefix("libretrodb_")
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.omnidroid.metadata.libretrodb"
}
