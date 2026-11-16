plugins {
    id("com.android.library")
    id("com.android.built-in-kotlin")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint { version.set("1.0.1") }

// Compile the pinned sources with the host's Android toolchain; all library tests live in the fork.
val padkitDir = rootProject.file("modules/padkit/upstream/omnidroid-padkit")
require(padkitDir.resolve("lib/src/main/kotlin/gg/padkit/PadKit.kt").isFile) {
    "PadKit sources are missing. Run git submodule update --init -- modules/padkit/upstream/omnidroid-padkit."
}
require(deps.android.minSdkVersion == 24) { "PadKit must retain Android 7.0 (API 24) support." }

android {
    namespace = "gg.padkit.lib"
    defaultConfig { minSdk = 24 }
    sourceSets {
        named("main") {
            manifest.srcFile(padkitDir.resolve("lib/src/main/AndroidManifest.xml"))
            kotlin.directories.add(padkitDir.resolve("lib/src/main/kotlin").path)
        }
        named("test") {
            kotlin.directories.add(padkitDir.resolve("lib/src/test/kotlin").path)
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { enable.add("NewApi") }
}

dependencies {
    api(platform(deps.libs.androidx.compose.composeBom))
    api(deps.libs.androidx.compose.runtime)
    api("androidx.compose.ui:ui")
    api(deps.libs.collectionsImmutable)
    implementation("androidx.compose.foundation:foundation")
    implementation(deps.libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    testImplementation(kotlin("test-junit"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("verifyAndroid7Compatibility") {
    group = "verification"
    description = "Build, lint for unsupported APIs, and run lifecycle/haptic tests including API 24."
    dependsOn("assembleRelease", "lintRelease", "testDebugUnitTest")
}
