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
        implementation(libs.kotlinx.io.core)
    }

    arrayOf(commonJvm, commonApple, commonJs, commonLinux, commonMingw).forEach {
        it.main.dependencies {
            implementation(libs.okio)
        }
    }
}
