// Published adapter: the core stays dependency-free, this module carries the kotlinx-io dependency.
// kotlinx-io has no random-access file API, so the adapter goes one way only.
// No Android target: Android consumers get the JVM variant; and fkc 0.15.1's Android Detekt
// task cannot resolve a project dependency on a KMP-Android module.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.kotlinx.bcv)
    alias(libs.plugins.vanniktech.mvn.publish)
    alias(libs.plugins.dokka)
}

fkcSetupMultiplatform(
    optIns = listOf("fluxo.io.internal.InternalFluxoIoApi"),
    config = {
        setupCoroutines = false
        setupDependencies = false
        enablePublication = false
        apiValidation {
            @Suppress("UnstableApiUsage")
            klibValidationEnabled = true
        }
    },
    kmp = {
        js { target { nodejs() } }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { target { nodejs() } }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmWasi { target { nodejs() } }
        allDefaultTargets(android = false, js = false, wasm = false, wasmWasi = false)
        androidNative()
    },
) {
    common.main.dependencies {
        api(projects.fluxoIoRad)
        api(project.dependencies.platform(projects.fluxoIoBom))
        api(libs.kotlinx.io.core)
    }
    // Kotlin/JS links only against a standard library at least as new as the compiler.
    commonJs.main.dependencies {
        implementation(libs.kotlin.stdlib)
    }
    common.test.dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.conformanceTest)
    }
}

apiValidation {
    klib {
        enabled = true
    }
}

mavenPublishing {
    pom {
        description.set("kotlinx-io adapter for fluxo-io-rad: RandomAccessData as a RawSource.")
    }
}
