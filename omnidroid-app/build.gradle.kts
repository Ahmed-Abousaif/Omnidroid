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

        versionCode = 263

        versionName = "2.6.0" // Keep in sync with OmnidroidCores when you cut version tags

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

        } else if (usePlayDynamicFeatures()) {

            // MelonDS DS dynamic module only provides arm64-v8a, armeabi-v7a, x86_64.

            // All dynamic bundle modules must have a consistent set of ABIs for Bundletool.

            ndk {

                abiFilters.clear()

                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))

            }

        }

    }



    splits {

        abi {

            isEnable = !usePlayDynamicFeatures()

            reset()

            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

            isUniversalApk = true

        }

    }

    flavorDimensions += listOf("opensource", "cores")



    if (usePlayDynamicFeatures()) {

        println("Building Google Play version. Bundling dynamic features.")

        dynamicFeatures.addAll(

            setOf(

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

                ":omnidroid_core_armsx2",

                ":omnidroid_core_dolphin",

                ":omnidroid_core_flycast",

                ":omnidroid_core_cemu",

                ":omnidroid_core_switch",

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

            // Stripping created issues with ppsspp; narrow to only ppsspp

            keepDebugSymbols += setOf("*/*/libppsspp_libretro_android.so")

            pickFirsts += setOf(
                "**/liblibretrodroid.so",
                "**/libfile_redirect_hook.so",
                "**/libgsl_alloc_hook.so",
                "**/libhook_impl.so",
                "**/libmain_hook.so",
            )

            useLegacyPackaging = true

        }

        resources {

            excludes += setOf(

                "META-INF/DEPENDENCIES",

                "META-INF/library_release.kotlin_module",

                "org/apache/commons/codec/**",

                "mozilla/public-suffix-list.txt",

                "com/google/api/client/googleapis/google.jks",

            )

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



    applicationVariants.all {

        val variant = this

        val isPlay = variant.flavorName.contains("play", ignoreCase = true)

        val isBundled = variant.flavorName.contains("bundle", ignoreCase = true)

        val coresType = if (isBundled) "bundled" else "dynamic"

        val buildTypeName = variant.buildType.name

        val baseVersion = variant.versionName?.removeSuffix("-DEBUG") ?: defaultConfig.versionName



        val prefix = if (isPlay) "Omnidroid-play" else "Omnidroid"



        outputs.all {

            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl

            val abi = output?.getFilter("ABI")

            val suffix = if (abi != null) "-$abi" else if (splits.abi.isEnable) "-universal" else ""

            val apkName = "$prefix-$coresType$suffix-$buildTypeName-v$baseVersion.apk"

            output?.outputFileName = apkName

        }

    }

}



val abiCodes = mapOf(

    "armeabi-v7a" to 1,

    "x86" to 2,

    "arm64-v8a" to 3,

    "x86_64" to 4,

)



androidComponents {

    onVariants { variant ->

        val baseVersionCode = 263

        variant.outputs.forEach { output ->

            val filterAbi = output.filters.find {

                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI

            }?.identifier



            if (filterAbi != null) {

                val modifier = abiCodes[filterAbi] ?: 0

                output.versionCode.set(baseVersionCode * 10 + modifier)

            } else {

                output.versionCode.set(baseVersionCode * 10)

            }

        }

    }

}



dependencies {

    implementation(project(":retrograde-util"))

    implementation(project(":retrograde-app-shared"))

    implementation(project(":omnidroid-metadata-libretro-db"))

    implementation(project(":omnidroid-metadata-rawg"))

    implementation(project(":omnidroid-touchinput"))



    "baselineProfile"(project(":baselineprofile"))

    implementation(deps.libs.androidx.profileInstaller)



    // Only wired up when the omnidroid-cores submodule is checked out (see settings.gradle.kts).

    findProject(":bundled-cores")?.let { add("bundleImplementation", project(it.path)) }



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



    if (findProject(":omni-libretrodroid") != null) {

        implementation(project(":omni-libretrodroid"))

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





tasks.register("verifyConsoleBackendRatchet") {
    group = "verification"
    description = "Enforces the Console Backend Allowlist Ratchet guardrail (no new console-specific branches in shared code)."
    notCompatibleWithConfigurationCache("Scans repository source files directly across project boundaries")
    val rootDirFile = rootDir
    doLast {
        data class RatchetAllowlistEntry(
            val filePath: String,
            val pattern: Regex,
            val maxCount: Int,
            val reason: String,
        )

        val allowlist = listOf(
            RatchetAllowlistEntry(
                "retrograde-app-shared/src/main/java/com/omnidroid/lib/game/GameLoader.kt",
                Regex("""SystemID\.PS2"""),
                1,
                "PS2 BIOS staging and asset manager retrieval in GameLoader"
            ),
            RatchetAllowlistEntry(
                "retrograde-app-shared/src/main/java/com/omnidroid/lib/bios/BiosManager.kt",
                Regex("""SystemID\.PS2"""),
                10,
                "PS2 BIOS directory and library management in BiosManager"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/bios/BiosSettingsScreen.kt",
                Regex("""SystemID\.PS2"""),
                1,
                "PS2 regional BIOS selector in BiosSettingsScreen"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/bios/BiosSettingsViewModel.kt",
                Regex("""Ps2Bios\."""),
                3,
                "PS2 asset server download region mapping in BiosSettingsViewModel"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/TouchScreenLayoutController.kt",
                Regex("""CoreID\.(MELONDS_DS|CITRA|AZAHAR|PCSX_REARMED|SNES9X|MUPEN64_PLUS_NEXT|DOLPHIN|FLYCAST|CEMU|MEDNAFEN_WSWAN)"""),
                19,
                "Dual-screen layout and widescreen hack variables in TouchScreenLayoutController"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/GameViewModelTouchControls.kt",
                Regex("""CoreID\.MELONDS_DS"""),
                1,
                "MelonDS Nintendo DS lid close key sequence in GameViewModelTouchControls"
            ),
            RatchetAllowlistEntry(
                "retrograde-app-shared/src/main/java/com/omnidroid/lib/saves/migrators/SavesMigrator.kt",
                Regex("""CoreID\.MELONDS_DS"""),
                1,
                "MelonDS saves migrator mapping in SavesMigrator"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/graphicsapi/GraphicsApiSelectionViewModel.kt",
                Regex("""SystemID\.(NINTENDO_3DS|GAMECUBE|WII|PSP|PS2|WII_U)"""),
                6,
                "Hardcoded per-core graphics API options in GraphicsApiSelectionViewModel"
            ),
            RatchetAllowlistEntry(
                "retrograde-app-shared/src/main/java/com/omnidroid/lib/library/CoreID.kt",
                Regex("""AssetsManager"""),
                12,
                "Libretro asset manager factory mapping in CoreID"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/mobile/feature/settings/slices/GameDatabasesScreen.kt",
                Regex("""when \(sliceId\)"""),
                1,
                "Static database slice UI titles in GameDatabasesScreen"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/src/main/java/com/omnidroid/app/shared/game/viewmodel/TouchScreenLayoutController.kt",
                Regex("""SystemID\.SWITCH"""),
                1,
                "Static aspect ratio mapping in TouchScreenLayoutController"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/build.gradle.kts",
                Regex(""":omnidroid_core_""" + "switch"),
                1,
                "Dynamic feature registration in omnidroid-app build.gradle.kts"
            ),
            RatchetAllowlistEntry(
                "settings.gradle.kts",
                Regex("""switch"""),
                4,
                "Dynamic feature include and projectDir mapping in settings.gradle.kts"
            ),
            RatchetAllowlistEntry(
                "omnidroid-app/proguard-rules.pro",
                Regex("""org\.yuzu\.yuzu_emu"""),
                2,
                "ProGuard keep rules for Eden C++ JNI bridge"
            ),
        )

        // 1. Check allowlist counts
        for (entry in allowlist) {
            val file = File(rootDirFile, entry.filePath)
            if (!file.exists()) {
                throw GradleException("Ratchet allowlist file missing: ${entry.filePath}")
            }
            val content = file.readText()
            val matchCount = entry.pattern.findAll(content).count()
            if (matchCount > entry.maxCount) {
                throw GradleException(
                    "Ratchet violation in ${entry.filePath}: found $matchCount occurrences of pattern '${entry.pattern.pattern}' " +
                        "(max allowed: ${entry.maxCount}). Reason: ${entry.reason}"
                )
            }
        }

        // 2. Scan shared code for disallowed branching outside backend packages
        val disallowedPatterns = listOf(
            Regex("""CoreID\.""" + "SWITCH") to "Direct CoreID.SWITCH reference",
            Regex("""when\s*\([^)]*coreId""") to "Direct when (coreId) branch",
            Regex("""when\s*\([^)]*coreID""") to "Direct when (coreID) branch",
            Regex("""if\s*\([^)]*system(\.|)id\s*==""") to "Direct if (system.id == ...) branch",
        )

        val allowedFilePaths = allowlist.map { it.filePath }.toSet() + setOf(
            "retrograde-app-shared/src/main/java/com/omnidroid/lib/library/GameSystem.kt",
        )

        val exemptDirSubstrings = listOf(
            "retrograde-app-shared/src/main/java/com/omnidroid/lib/backend",
            "omnidroid-app/src/main/java/com/omnidroid/app/shared/game/backend",
            "omnidroid-app/src/main/java/com/omnidroid/app/shared/switch_emu",
        )

        val scannedDirs = listOf(
            File(rootDirFile, "omnidroid-app/src/main"),
            File(rootDirFile, "retrograde-app-shared/src/main"),
        )

        val violations = mutableListOf<String>()

        for (dir in scannedDirs) {
            dir.walkTopDown().forEach { file ->
                if (!file.isFile || !(file.name.endsWith(".kt") || file.name.endsWith(".java"))) return@forEach
                val relativePath = file.relativeTo(rootDirFile).path.replace('\\', '/')
                if (exemptDirSubstrings.any { relativePath.contains(it) }) return@forEach
                if (allowedFilePaths.contains(relativePath)) return@forEach

                val text = file.readText()
                for ((pattern, desc) in disallowedPatterns) {
                    val count = pattern.findAll(text).count()
                    if (count > 0) {
                        violations.add("$relativePath: $desc ($count occurrences)")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Console Backend Ratchet failed! Found disallowed console-specific branches outside backend modules:\n" +
                    violations.joinToString("\n")
            )
        }

        println("verifyConsoleBackendRatchet: All allowlist baselines passed and 0 ratchet violations found.")
    }
}
