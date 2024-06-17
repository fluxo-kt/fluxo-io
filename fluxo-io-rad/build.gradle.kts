plugins {
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.atomicfu)
}

fkcSetupMultiplatform(
    namespace = "kt.fluxo.io.rad",
    optIns = listOf(
        "kotlin.ExperimentalSubclassOptIn",
        "fluxo.io.internal.InternalForInheritanceApi",
    ),
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

    commonJvm.main.dependencies {
        compileOnly(rootProject.extra["androidJar"]!!)
    }

    arrayOf(commonJvm, commonApple, commonJs, commonLinux, commonMingw).forEach {
        it.main.dependencies {
            implementation(libs.okio)
        }
    }
}
