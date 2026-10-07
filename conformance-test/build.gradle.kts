// Unpublished: holds the behaviour table (`RadContract`) that every module shipping a
// `RandomAccessData` implementation runs from its own tests. Each consumer test compilation
// needs a variant of this module for its target, so the targets match `:fluxo-io-rad` except
// Android: the core's Android host tests resolve its JVM variant, so CI target filters keep `JVM`.
// The `-test` in the module name makes fluxo-kmp-conf skip Dependency Guard here, whose release
// policy rejects kotlin-test on a main classpath; this module exists to put it there.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

fkcSetupMultiplatform(
    config = {
        setupCoroutines = false
        setupDependencies = false
        enablePublication = false
        enableApiValidation = false
    },
    kmp = {
        js { target { nodejs() } }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { target { nodejs() } }
        // Declared here, not via allDefaultTargets: fkc adds wasmWasi only together with its
        // own wasmJs, which this build declares itself.
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmWasi { target { nodejs() } }
        allDefaultTargets(android = false, js = false, wasm = false, wasmWasi = false)
        // The default groups skip deprecated x64 Apple targets; published until the Kotlin 2.5 switch.
        iosX64(); macosX64(); tvosX64(); watchosX64()
        androidNative()
    },
) {
    common.main.dependencies {
        api(projects.fluxoIoRad)
        api(libs.kotlin.test)
    }
}
