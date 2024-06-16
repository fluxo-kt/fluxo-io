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
