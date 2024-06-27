plugins {
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.kotlinx.bcv)
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
        apiValidation {
            @Suppress("UnstableApiUsage")
            klibValidationEnabled = false
            tsApiChecks = false
        }
    },
) {
    common.main.dependencies {
        implementation(libs.coroutines)
        // implementation(libs.kotlinx.io.core)
    }

    val commonJvm = commonJvm
    commonJvm.main.dependencies {
        compileOnly(rootProject.extra["androidJar"]!!)
        compileOnly(libs.androidx.annotation)
        compileOnly(libs.jetbrains.annotation)
    }

    val commonJs = commonJs
    commonJs.main.dependencies {
        api(libs.kotlinx.atomicfu)
    }

    arrayOf(commonJvm, commonApple, commonJs, commonLinux, commonMingw).forEach {
        it.main.dependencies {
            // implementation(libs.okio)
        }
    }
}

apiValidation {
    ignoredPackages.add("fluxo.io.internal")

    @Suppress("OPT_IN_USAGE")
    klib {
        enabled = true
    }
}
