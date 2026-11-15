plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Build the pinned in-repository submodule with Omnidroid's toolchain.
val padkitDir = rootProject.file("modules/padkit/upstream/omnidroid-padkit")

require(padkitDir.resolve("lib/src/commonMain/kotlin/gg/padkit/PadKit.kt").isFile) {
    "PadKit sources are missing at $padkitDir. " +
        "Run git submodule update --init --recursive -- modules/padkit/upstream/omnidroid-padkit."
}

logger.lifecycle("Omnidroid PadKit sources: $padkitDir")

kotlin {
    androidTarget()

    sourceSets {
        commonMain {
            kotlin.srcDir(padkitDir.resolve("lib/src/commonMain/kotlin"))
            resources.srcDir(padkitDir.resolve("lib/src/commonMain/resources"))

            dependencies {
                implementation(project.dependencies.platform(deps.libs.androidx.compose.composeBom))
                implementation(deps.libs.androidx.compose.runtime)
                implementation("androidx.compose.foundation:foundation")
                implementation(deps.libs.androidx.compose.material3)
                implementation(deps.libs.androidx.compose.extendedIcons)
                implementation(deps.libs.collectionsImmutable)
            }
        }
        androidMain {
            kotlin.srcDir(padkitDir.resolve("lib/src/androidMain/kotlin"))
            resources.srcDir(padkitDir.resolve("lib/src/androidMain/resources"))
        }
    }
}

android {
    namespace = "gg.padkit.lib"

    sourceSets["main"].apply {
        manifest.srcFile(padkitDir.resolve("lib/src/androidMain/AndroidManifest.xml"))
        res.srcDir(padkitDir.resolve("lib/src/androidMain/res"))
        assets.srcDir(padkitDir.resolve("lib/src/androidMain/assets"))
    }

    buildFeatures {
        compose = true
    }
}
