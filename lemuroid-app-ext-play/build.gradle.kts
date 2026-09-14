import java.util.Properties

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid.ext"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val dropboxKey = localProperties.getProperty("dropbox.app.key", "")
        val oneDriveId = localProperties.getProperty("onedrive.client.id", "")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxKey\"")
        buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"$oneDriveId\"")
    }
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))

    implementation(deps.libs.okHttp3)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.play.featureDelivery)
    implementation(deps.libs.play.featureDeliveryKtx)
    implementation(deps.libs.play.review)
    implementation(deps.libs.play.reviewKtx)

    implementation(deps.libs.gdrive.apiClient)
    implementation(deps.libs.gdrive.apiClientAndroid)
    implementation(deps.libs.gdrive.apiServicesDrive)
    implementation(deps.libs.play.playServices)
    implementation(deps.libs.play.coroutine)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    kapt(deps.libs.androidx.lifecycle.processor)

    implementation(deps.libs.androidx.leanback.leanback)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.material)

    implementation(deps.libs.dagger.core)

    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.timber)
}
