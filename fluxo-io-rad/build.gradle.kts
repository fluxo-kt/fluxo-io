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
        // implementation(libs.kotlinx.io.core)
    }

    commonJvm.main.dependencies {
        compileOnly(rootProject.extra["androidJar"]!!)
        compileOnly(libs.androidx.annotation)
        compileOnly(libs.jetbrains.annotation)
        compileOnly(libs.coroutines)
    }

    val commonJs = commonJs
    commonJs.main.dependencies {
        // Fix JS build KLIB issue
        implementation(libs.kotlinx.atomicfu)
    }

    commonNative.main.dependencies {
        implementation(libs.stately.concurrent.collections)
    }
}

apiValidation {
    ignoredPackages.add("fluxo.io.internal")

    @Suppress("OPT_IN_USAGE")
    klib {
        enabled = true
    }
}
