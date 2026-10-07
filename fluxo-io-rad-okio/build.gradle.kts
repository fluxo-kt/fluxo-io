// Published adapter: the core stays dependency-free, this module carries the okio dependency.
// Okio publishes no Android Native variants, so this module has none either.
// No Android target: Android consumers get the JVM variant, which needs no Android API.
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
        // The default groups skip deprecated x64 Apple targets; published until the Kotlin 2.5 switch.
        iosX64(); macosX64(); tvosX64(); watchosX64()
    },
) {
    common.main.dependencies {
        api(projects.fluxoIoRad)
        api(project.dependencies.platform(projects.fluxoIoBom))
        api(libs.okio)
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
        description.set("Okio adapters for fluxo-io-rad: FileHandle as RandomAccessData, RandomAccessData as a Source.")
    }
}
