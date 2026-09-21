import com.android.build.gradle.BaseExtension

plugins {
    id("com.android.application") version "9.0.0" apply false
    id("com.android.library") version "9.0.0" apply false
    id("com.android.test") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version deps.versions.kotlin apply false
    id("org.jetbrains.kotlin.plugin.compose") version deps.versions.kotlin apply false
    id("org.jetbrains.kotlin.plugin.serialization") version deps.versions.kotlin apply false
    id("com.google.devtools.ksp") version deps.versions.ksp apply false
    id("com.google.dagger.hilt.android") version deps.versions.dagger apply false
    id("androidx.baselineprofile") version "1.5.0-rc02" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "org.jetbrains.kotlin" -> {
                    useVersion(deps.versions.kotlin)
                }
            }
        }
    }
}

subprojects {
    tasks.matching { it.name.contains("AarMetadata") && !it.path.startsWith(":libretrodroid") }.configureEach {
        enabled = false
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    afterEvaluate {
        if (hasProperty("android")) {
            // BaseExtension is common parent for application, library and test modules
            apply(plugin = "org.jlleitschuh.gradle.ktlint")

            extensions.findByType(BaseExtension::class.java)?.apply {
                compileSdkVersion(deps.android.compileSdkVersion)
                buildToolsVersion(deps.android.buildToolsVersion)
                defaultConfig {
                    minSdkVersion(deps.android.minSdkVersion)
                    targetSdkVersion(deps.android.targetSdkVersion)
                    multiDexEnabled = true
                }
                lintOptions {
                    isAbortOnError = true
                    disable("UnusedResources") // https://issuetracker.google.com/issues/63150366
                    disable("InvalidPackage")
                    disable("VectorPath")
                    disable("TrustAllX509TrustManager")
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            (extensions.findByName("android") as? com.android.build.api.dsl.CommonExtension)?.apply {
                compileSdk = deps.android.compileSdkVersion
                buildToolsVersion = deps.android.buildToolsVersion
                compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                compileOptions.targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    configurations {
        all {
            exclude(group = "com.google.code.findbugs", module = "jsr305")
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
