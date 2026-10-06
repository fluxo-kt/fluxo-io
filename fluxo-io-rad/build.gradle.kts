@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class)

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
        // Declared here, not via allDefaultTargets: fkc adds wasmWasi only together with its
        // own wasmJs, which this build declares itself.
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmWasi { target { nodejs() } }
        allDefaultTargets(js = false, wasm = false, wasmWasi = false)
        // The default groups skip deprecated x64 Apple targets; published until the Kotlin 2.5 switch.
        iosX64(); macosX64(); tvosX64(); watchosX64()
        androidNative()
    },
) {
    common.main.dependencies {
        // Gradle aligns every fluxo-io artifact a consumer uses to one version (see the BOM).
        api(project.dependencies.platform(projects.fluxoIoBom))
    }
    common.test.dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.conformanceTest)
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
        // Kotlin/JS doesn't support an older standard library than the compiler.
        implementation(libs.kotlin.stdlib)
    }
}

apiValidation {
    klib {
        enabled = true
    }
}

// Coordinates, POM, signing and Central settings are shared by every published module in the
// root build script; only the description is per module.
mavenPublishing {
    pom {
        description.set("Read-only random-access I/O for Kotlin Multiplatform.")
    }
}

// Kotlin's WASI test driver preopens no directory (KT-65179), so a WASI test can reach no file.
// Map the guest's /tmp to this task's temp dir by patching the generated driver just before the
// run. Only a Provider and a File are captured, which keeps the configuration cache valid.
// `matching`, not `named`: a KMP_TARGETS filter can leave the task out.
tasks.matching { it.name == "wasmWasiNodeTest" }.configureEach {
    val driver = layout.buildDirectory.file(
        "compileSync/wasmWasi/test/testDevelopmentExecutable/kotlin/fluxo-io-fluxo-io-rad-test.mjs",
    )
    val tmpDir = temporaryDir
    doFirst {
        val file = driver.get().asFile
        val original = "new WASI({ version: 'preview1', args: argv, env, })"
        val hostDir = tmpDir.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
        val text = file.readText()
        if (original !in text) {
            // Already patched when the sync task left its output in place.
            check("preopens: { '/tmp'" in text) { "The KGP WASI test driver changed; update this patch: $file" }
            return@doFirst
        }
        file.writeText(
            text.replace(original, "new WASI({ version: 'preview1', args: argv, env, preopens: { '/tmp': '$hostDir' } })"),
        )
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

    // `matching`, not `named`: a KMP_TARGETS filter can leave either source set out.
    sourceSets.matching { it.name == "androidMain" }.configureEach {
        dependsOn(sourceSets.getByName("commonJvmMain"))
    }
    // Node's async fs and Blob reads complete on the event loop, so their tests must suspend;
    // runTest returns the Promise the JS test runners wait for.
    sourceSets.matching { it.name == "webTest" }.configureEach {
        dependencies { implementation(libs.coroutines.test) }
    }
}
