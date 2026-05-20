plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.vanniktech.mvn.publish)
}

fkcSetupMultiplatform(
    namespace = "kt.fluxo.io.rad",
    optIns = listOf(
        "fluxo.io.internal.InternalFluxoIoApi",
        "kotlin.ExperimentalMultiplatform",
        "kotlin.ExperimentalSubclassOptIn",
    ),
    config = {
        projectName = "fluxo-io"
        description = "I/O functionality for Kotlin Multiplatform from Fluxo" +
            ", including read-only random data access interface with multiple implementations" +
            ", and more."

        useDokka = true
        setupCoroutines = false
        setupDependencies = false
        enablePublication = true
        apiValidation {
            ignoredPackages.add("fluxo.io.internal")
            @Suppress("UnstableApiUsage")
            klibValidationEnabled = true
            tsApiChecks = false
        }
    },
    kmp = {
        allDefaultTargets(wasmWasi = false)
        androidNative()
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
        compileOnly(libs.jsr305)
        compileOnly(libs.coroutines)
    }
    commonJvm.test.dependencies {
        implementation(libs.coroutines)
        implementation(libs.kotlin.test.junit)
        implementation(libs.coroutines.test)
        implementation(libs.assertj)
        implementation(libs.lincheck)
    }

//    sourceSets.jvmTest.configure {
//        dependsOn(commonJvm.main)
//    }

    val commonJs = commonJs
    commonJs.main.dependencies {
        // Fix JS build KLIB issue
        implementation(libs.kotlinx.atomicfu)
        // Kotlin/JS doesn't support an older standard library than the compiler.
        implementation(libs.kotlin.stdlib)
    }

    commonNative.main.dependencies {
        implementation(libs.stately.concurrent.collections)
    }
}

kotlin {
    android {
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("src/commonJvmMain/resources/META-INF/proguard/fluxo-io-rad.pro")
            }
        }
    }

    sourceSets.named("androidMain") {
        dependsOn(sourceSets.named("commonJvmMain").get())
    }
}
