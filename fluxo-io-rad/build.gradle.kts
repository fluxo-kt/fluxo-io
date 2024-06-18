plugins {
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.atomicfu)
}

fkcSetupMultiplatform(
    namespace = "kt.fluxo.io.rad",
    optIns = listOf(
        "fluxo.io.internal.InternalFluxoIoApi",
        "kotlin.ExperimentalMultiplatform",
        "kotlin.ExperimentalSubclassOptIn",
    ),
    config = {
        setupCoroutines = true
        setupDependencies = true
        addStdlibDependency = true
    },
) {
    common.main.dependencies {
        implementation(libs.coroutines)
        // implementation(libs.kotlinx.io.core)
    }

    val commonJvm = commonJvm
    commonJvm.main.dependencies {
        compileOnly(rootProject.extra["androidJar"]!!)
    }

    val commonJs = commonJs
    commonJs.main.dependencies {
        implementation(libs.kotlinx.atomicfu)
    }

    arrayOf(commonJvm, commonApple, commonJs, commonLinux, commonMingw).forEach {
        it.main.dependencies {
            // implementation(libs.okio)
        }
    }
}
