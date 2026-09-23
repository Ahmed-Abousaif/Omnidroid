import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    defaultConfig {
        versionCode = 258
        versionName = "2.1.0" // Keep in sync with OmnidroidCores when you cut version tags
        applicationId = "com.omnidroid"
        // Optional: ./gradlew ... -PabiFilters=armeabi-v7a,arm64-v8a
        val abiFiltersProp = rootProject.findProperty("abiFilters") as String?
        if (!abiFiltersProp.isNullOrBlank()) {
            ndk {
                abiFilters.clear()
                abiFilters.addAll(
                    abiFiltersProp.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                )
            }
        }
    }
    flavorDimensions += listOf("opensource", "cores")

    if (usePlayDynamicFeatures()) {
        println("Building Google Play version. Bundling dynamic features.")
        dynamicFeatures.addAll(
            setOf(
                ":omnidroid_core_desmume",
                ":omnidroid_core_dosbox_pure",
                ":omnidroid_core_fbneo",
                ":omnidroid_core_fceumm",
                ":omnidroid_core_gambatte",
                ":omnidroid_core_genesis_plus_gx",
                ":omnidroid_core_handy",
                ":omnidroid_core_mame2003_plus",
                ":omnidroid_core_mednafen_ngp",
                ":omnidroid_core_mednafen_pce_fast",
                ":omnidroid_core_mednafen_wswan",
                ":omnidroid_core_melonds",
                ":omnidroid_core_melondsds",
                ":omnidroid_core_mgba",
                ":omnidroid_core_mupen64plus_next_gles3",
                ":omnidroid_core_pcsx_rearmed",
                ":omnidroid_core_ppsspp",
                ":omnidroid_core_prosystem",
                ":omnidroid_core_snes9x",
                ":omnidroid_core_stella",
                ":omnidroid_core_citra",
                ":omnidroid_core_azahar",
                ":omnidroid_core_pcee2",
                ":omnidroid_core_dolphin",
                ":omnidroid_core_flycast",
            ),
        )
    }

    // Since some dependencies are closed source we make a completely free as in free speech variant.

    productFlavors {

        create("free") {
            dimension = "opensource"
        }

        create("play") {
            dimension = "opensource"
        }

        // Include cores in the final apk
        create("bundle") {
            dimension = "cores"
        }

        // Download cores on demand (from GooglePlay or GitHub)
        create("dynamic") {
            dimension = "cores"
        }
    }

    packagingOptions {
        jniLibs {
            // Stripping created some issues with some libretro cores such as ppsspp
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            pickFirsts += setOf("**/liblibretrodroid.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module")
        }
    }

    signingConfigs {
        // Prefer a project-local debug keystore when present; otherwise AGP's default (~/.android/debug.keystore).
        maybeCreate("debug").apply {
            val projectDebugKeystore = file("$rootDir/debug.keystore")
            if (projectDebugKeystore.exists()) {
                val storePass =
                    project.signingSecret("OMNIDROID_DEBUG_STORE_PASSWORD", "omnidroid.debugStorePassword")
                        ?: project.signingSecret("OMNIDROID_STORE_PASSWORD", "omnidroid.storePassword")
                        ?: "android"
                val keyPass =
                    project.signingSecret("OMNIDROID_DEBUG_KEY_PASSWORD", "omnidroid.debugKeyPassword")
                        ?: storePass
                storeFile = projectDebugKeystore
                keyAlias = "androiddebugkey"
                storePassword = storePass
                keyPassword = keyPass
            }
        }

        maybeCreate("release").apply {
            val releaseKeystore = file("$rootDir/release.jks")
            if (releaseKeystore.exists()) {
                val storePass = project.signingSecret("OMNIDROID_STORE_PASSWORD", "omnidroid.storePassword")
                    ?: error(
                        "release.jks found but no store password. Set OMNIDROID_STORE_PASSWORD env " +
                            "or omnidroid.storePassword in local.properties / gradle.properties.",
                    )
                val keyPass = project.signingSecret("OMNIDROID_KEY_PASSWORD", "omnidroid.keyPassword") ?: storePass
                val alias = project.signingSecret("OMNIDROID_KEY_ALIAS", "omnidroid.keyAlias") ?: "omnidroid"
                storeFile = releaseKeystore
                keyAlias = alias
                storePassword = storePass
                keyPassword = keyPass
            } else {
                // Local/CI without a release keystore — fall back to debug signing material only.
                val projectDebugKeystore = file("$rootDir/debug.keystore")
                val storePass =
                    project.signingSecret("OMNIDROID_DEBUG_STORE_PASSWORD", "omnidroid.debugStorePassword")
                        ?: project.signingSecret("OMNIDROID_STORE_PASSWORD", "omnidroid.storePassword")
                        ?: "android"
                val keyPass =
                    project.signingSecret("OMNIDROID_DEBUG_KEY_PASSWORD", "omnidroid.debugKeyPassword")
                        ?: storePass
                if (projectDebugKeystore.exists()) {
                    storeFile = projectDebugKeystore
                }
                keyAlias = "androiddebugkey"
                storePassword = storePass
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs["release"]
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "omnidroid_name", "Omnidroid")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "omnidroid_name", "OmnidroidDebug")
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }


    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.omnidroid"
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":omnidroid-metadata-libretro-db"))
    implementation(project(":omnidroid-metadata-rawg"))
    implementation(project(":omnidroid-touchinput"))

    "baselineProfile"(project(":baselineprofile"))
    implementation(deps.libs.androidx.profileInstaller)

    "bundleImplementation"(project(":bundled-cores"))

    "freeImplementation"(project(":omnidroid-app-ext-free"))
    "playImplementation"(project(":omnidroid-app-ext-play"))

    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)

    implementation(deps.libs.androidx.leanback.leanback)
    implementation(deps.libs.androidx.leanback.leanbackPreference)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.hilt.android)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.androidx.leanback.tvProvider)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.animation)
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)
    implementation(deps.libs.composeHtmlText)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    if (findProject(":libretrodroid") != null) {
        implementation(project(":libretrodroid"))
    } else {
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    }

    // Uncomment this when using a local aar file.
    implementation(deps.libs.hilt.work)

    ksp(deps.libs.hilt.compiler)
    ksp(deps.libs.hilt.workCompiler)
}

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

/** Resolve signing secrets: env var first, then Gradle/-P property, then local.properties. */
fun Project.signingSecret(envName: String, propertyName: String): String? {
    System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it }
    (findProperty(propertyName) as String?)?.takeIf { it.isNotBlank() }?.let { return it }
    val localProperties = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { localProperties.load(it) }
    }
    return localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}
