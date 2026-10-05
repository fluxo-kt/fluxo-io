// Unpublished: holds the behaviour table (`RadContract`) that every module shipping a
// `RandomAccessData` implementation runs from its own tests. Each consumer test compilation
// needs a variant of this module for its target, so the targets match `:fluxo-io-rad` except
// Android: no Android test compilation consumes it, and fluxo-kmp-conf 0.15.1's Android Detekt
// task cannot resolve a KMP-Android project dependency (ambiguous `androidApiElements` variants).
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
        androidNative()
    },
) {
    common.main.dependencies {
        api(projects.fluxoIoRad)
        api(libs.kotlin.test)
    }
}
