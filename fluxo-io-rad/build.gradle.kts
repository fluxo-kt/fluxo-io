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
        enablePublication = false
        apiValidation {
            ignoredPackages.add("fluxo.io.internal")
            @Suppress("UnstableApiUsage")
            klibValidationEnabled = true
            tsApiChecks = false
        }
    },
) {
    common.main.dependencies {
        // implementation(libs.kotlinx.io.core)
    }

    val commonJvm = commonJvm
    commonJvm.main.dependencies {
        compileOnly(rootProject.extra["androidJar"]!!)
        compileOnly(libs.androidx.annotation)
        compileOnly(libs.jetbrains.annotation)
        compileOnly(libs.coroutines)
    }
    commonJvm.test.dependencies {
        implementation(libs.kotlin.test.junit)
        implementation(libs.coroutines.test)
        implementation(libs.assertj)
    }

    sourceSets.jvmTest.configure {
        dependsOn(commonJvm.main)
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
