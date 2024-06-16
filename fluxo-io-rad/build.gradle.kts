plugins {
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.atomicfu)
}

fkcSetupMultiplatform(
    namespace = "kt.fluxo.io.rad",
    config = {
        setupCoroutines = true
        setupDependencies = true
        addStdlibDependency = true
    },
) {
    common.main.dependencies {
        implementation(libs.coroutines)
    }
}
