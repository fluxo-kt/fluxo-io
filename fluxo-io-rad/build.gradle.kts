@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

import com.vanniktech.maven.publish.DeploymentValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.kotlinx.bcv)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.vanniktech.mvn.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.fluxo.bcv.js)
}

// `android.os.Build` is read (guarded by a runtime Android check) from shared JVM code, so
// `commonJvmMain` compiles against the Android SDK jar. AGP's boot classpath is that jar for the
// configured compileSdk; no SDK location or `platforms/android-<N>` directory name is guessed.
val androidBootClasspath = files(
    extensions.getByType<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension>()
        .sdkComponents.bootClasspath,
)

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

        setupCoroutines = false
        setupDependencies = false
        enablePublication = false
        apiValidation {
            ignoredPackages.add("fluxo.io.internal")
            @Suppress("UnstableApiUsage")
            klibValidationEnabled = true
            tsApiChecks = true
        }
    },
    kmp = {
        js {
            target {
                nodejs()
                binaries.executable()
                useEsModules()
                compilerOptions {
                    moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES)
                    sourceMap.set(true)
                    useEsClasses.set(true)
                }
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions {
                            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES)
                            sourceMap.set(true)
                            useEsClasses.set(true)
                        }
                    }
                }
            }
        }
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs {
            target {
                nodejs()
                binaries.executable()
            }
        }
        allDefaultTargets(js = false, wasm = false, wasmWasi = false)
        androidNative()
    },
) {
    common.main.dependencies {
        // implementation(libs.kotlinx.io.core)
    }
    common.test.dependencies {
        implementation(libs.kotlin.test)
    }

    val commonJvm = commonJvm
    commonJvm.main.dependencies {
        compileOnly(androidBootClasspath)
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
}

apiValidation {
    klib {
        enabled = true
    }
}

mavenPublishing {
    publishToMavenCentral(
        automaticRelease = false,
        validateDeployment = DeploymentValidation.VALIDATED,
    )
    signAllPublications()
    coordinates("io.github.fluxo-kt", "fluxo-io-rad", libs.versions.version.get())
    pom {
        name.set("fluxo-io-rad")
        description.set(
            "Read-only random-access I/O for Kotlin Multiplatform.",
        )
        inceptionYear.set("2024")
        url.set("https://github.com/fluxo-kt/fluxo-io")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("amal")
                name.set("Art Shendrik")
                email.set("artyom.shendrik@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/fluxo-kt/fluxo-io")
            connection.set("scm:git:git://github.com/fluxo-kt/fluxo-io.git")
            developerConnection.set("scm:git:ssh://git@github.com/fluxo-kt/fluxo-io.git")
        }
    }
}

// Opt-in for local mavenLocal publishes on machines and CI jobs that hold no signing key
// (scripts/consumer-check.sh, updateBaseline). Never set it for a real release. Gradle's Sign tasks then skip when no key is configured. Without the
// property, vanniktech keeps signing required for every non-SNAPSHOT publication (releases).
if (providers.gradleProperty("fluxo.unsignedLocalPublish").orNull == "true") {
    extensions.configure<org.gradle.plugins.signing.SigningExtension> { isRequired = false }
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
