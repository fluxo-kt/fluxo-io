plugins {
    alias(libs.plugins.kotlinx.kover)
    id("kotlinx-atomicfu")
}

fkcSetupMultiplatform(
    namespace = "kt.fluxo.io.rad",
    config = {
        setupCoroutines = true
        setupDependencies = true
        addStdlibDependency = true
    },
) {
}
