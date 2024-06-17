buildscript {
    dependencies {
        classpath(libs.plugin.kotlinx.atomicfu)
    }
}

plugins {
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.bcv) apply false
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.fluxo.bcv.js) apply false
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.fluxo.kmp.conf)
}

// Setup project defaults.
fkcSetupRaw {
    explicitApi()

    // Default KMP setup.
    defaults {
        allDefaultTargets(
            // wasmWasi = true // TODO
        )
        androidNative()
    }

    projectName = "Fluxo IO"
    description = "Fluxo IO library for Kotlin Multiplatform"
    githubProject = "fluxo-kt/fluxo-io"
    group = "io.github.fluxo-kt"

    publicationConfig {
        developerId = "amal"
        developerName = "Artyom Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    enableSpotless = false
    apiValidation {
        tsApiChecks = false
    }

    experimentalLatestCompilation = true
    allWarningsAsErrors = true
    optInInternal = true
    optIns = listOf(
        "kotlin.js.ExperimentalJsExport",
    )
}


/**
 * Find Android SDK JAR for usage as dependency in non-android modules or source sets.
 */
fun findAndroidJar(project: Project, compileSdkVersion: Int): FileCollection {
    fun getSdkDirFromLocalProperties(): String? {
        // Get "sdk.dir" property from local.properties file.
        return project.file("local.properties")
            .takeIf { it.exists() }
            ?.run {
                readLines().firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter("sdk.dir=")
            }
    }

    fun findAndroidSdkDir(): String? {
        // https://developer.android.com/studio/command-line/variables
        return getSdkDirFromLocalProperties()
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getProperty("android.home")
    }

    // References:
    // https://android.googlesource.com/platform/tools/base/+/f6bef46dc8/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/SdkHandler.java#319
    // https://github.com/stepango/android-jar
    val androidSdkDir = findAndroidSdkDir()
        ?: throw java.io.FileNotFoundException("Can't locate Android SDK path")

    return project.files("$androidSdkDir/platforms/android-$compileSdkVersion/android.jar")
}

extra["androidJar"] = findAndroidJar(project, libs.versions.androidCompileSdk.get().toInt())


// Exclude unused DOM API.
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.module.name == "kotlin-dom-api-compat") {
                useTarget(libs.kotlin.stdlib.js)
            }
        }
    }
}
