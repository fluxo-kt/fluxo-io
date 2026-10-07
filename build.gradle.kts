import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.kotlinx.bcv) apply false
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.fluxo.bcv.js) apply false
    alias(libs.plugins.gradle.doctor) apply false
    alias(libs.plugins.vanniktech.mvn.publish) apply false
    alias(libs.plugins.fluxo.kmp.conf)
}

// Setup project defaults.
fkcSetupRaw {
    explicitApi()

    projectName = "fluxo-io"
    description = "I/O functionality for Kotlin Multiplatform from Fluxo"
    githubProject = "fluxo-kt/fluxo-io"
    group = "io.github.fluxo-kt"

    publicationConfig {
        developerId = "amal"
        developerName = "Art Shendrik"
        developerEmail = "artyom.shendrik@gmail.com"
    }

    // Published JVM code compiles against the JDK 8 class library instead (subprojects block
    // below), and tests deliberately see the build JDK's API.
    useJdkRelease = false
    enableApiValidation = true
    useDokka = true

    setupVerification = true
    enableGenericAndroidLint = true
    enableGradleDoctor = true
    experimentalLatestCompilation = true
    latestSettingsForTests = true
    // Without this, latestSettingsForTests also compiles tests for the newest JDK the build
    // finds, and the oldest-JDK test lane cannot load them (e.g. class file 65 on a Java 8 runtime).
    javaTestsLangTarget = libs.versions.javaLangTarget.get()
    allWarningsAsErrors = false
    optInInternal = true
    optIns = listOf(
        "kotlin.ExperimentalStdlibApi",
        "kotlin.js.ExperimentalJsExport",
    )
}


kover.reports {
    dependencies {
        kover(projects.fluxoIoRad)
        kover(projects.fluxoIoRadOkio)
        kover(projects.fluxoIoRadKotlinxIo)
    }

    // TODO: Disable Kover by default to reduce performance penalty.
    //  https://github.com/Kotlin/kotlinx-kover/issues/531#issuecomment-1929483468
    val isCI by isCI()
    val isRelease by isRelease()

    filters {
        // Test classes
        excludes.classes("*Test")
    }

    verify {
        @Suppress("MagicNumber")
        rule {
            disabled = false
            groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
            minBound(55)
            bound {
                minValue = 80
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 80
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 55
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
            }
        }
    }

    total {
        xml {
            onCheck = true
            xmlFile = layout.buildDirectory.file("reports/kover-merged-report.xml")
        }
        log {
            onCheck = true
        }
        html {
            onCheck = !isCI && isRelease
            htmlDir = layout.buildDirectory.dir("reports/kover-merged-report-html")
        }
    }
}

// Every published module: Central settings, signing, POM and coordinates (artifactId = module
// name). Modules set only their POM description.
val publishVersion = libs.versions.version.get()
val unsignedLocalPublish = providers.gradleProperty("fluxo.unsignedLocalPublish").orNull == "true"
/** The JDK whose class library published JVM code compiles against: the bytecode floor. */
val floorJdk: JavaLanguageVersion =
    JavaLanguageVersion.of(libs.versions.javaLangTarget.get().removePrefix("1."))

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(
                automaticRelease = false,
                validateDeployment = com.vanniktech.maven.publish.DeploymentValidation.VALIDATED,
            )
            signAllPublications()
            coordinates("io.github.fluxo-kt", name, publishVersion)
            pom {
                name.set(this@subprojects.name)
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
        // (scripts/consumer-check.sh, updateBaseline); never for a real release. Gradle's Sign
        // tasks then skip without a key; otherwise vanniktech requires signing for every
        // non-SNAPSHOT publication.
        if (unsignedLocalPublish) {
            extensions.configure<org.gradle.plugins.signing.SigningExtension> { isRequired = false }
        }
    }

    // On an arm64 host (the macOS CI runners, Apple Silicon Macs) the Kotlin plugin skips every
    // x64 native test, having no x64 host or simulator to run it, yet still links its test binary.
    // Nothing runs that binary, so the link only costs time; `assemble` still compiles the shipped
    // x64 klibs.
    if (System.getProperty("os.arch") == "aarch64") {
        tasks.matching {
            it.name.startsWith("linkDebugTest") && it.name.endsWith("X64")
        }.configureEach {
            onlyIf("x64 native tests cannot run on an arm64 host") { false }
        }
    }

    // Published JVM code compiles against JDK 8's own class library, the bytecode floor: only
    // that catches every JDK 9+ call. Kotlin's `-Xjdk-release=1.8` (fkc `useJdkRelease`) hides
    // JDK 9+ classes but not JDK 9+ members of old classes: `InputStream.readAllBytes()` compiled
    // and then threw NoSuchMethodError on Java 8. The compiler itself still runs on the build JDK.
    // Tests are left on the build JDK's API on purpose: they also cover JDK 9+ behaviour, guarded
    // by the runtime version, and are compiled to floor bytecode so the JDK 8 lane loads them.
    val floorLauncher = provider { project.extensions.getByType<JavaToolchainService>() }
        .flatMap { it.launcherFor { languageVersion.set(floorJdk) } }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
        .matching { it.name == "compileKotlinJvm" }
        .configureEach { kotlinJavaToolchain.toolchain.use(floorLauncher) }
    tasks.withType<JavaCompile>().matching { it.name == "compileJvmMainJava" }.configureEach {
        javaCompiler.set(
            project.extensions.getByType<JavaToolchainService>()
                .compilerFor { languageVersion.set(floorJdk) },
        )
    }

    // `-Pfluxo.testJdk=<N>` runs JVM tests on JDK N while the build itself stays on its own JDK.
    // Only running the tests there shows runtime differences (buffer methods, cleaners, Unsafe,
    // JDK 9+ methods) on the oldest and the newest JDK that consumers use. Each CI lane passes
    // the JDK it installed.
    val testJdk = providers.gradleProperty("fluxo.testJdk").map { JavaLanguageVersion.of(it) }
    if (testJdk.isPresent) {
        tasks.withType<Test>().configureEach {
            javaLauncher.set(
                project.extensions.getByType<JavaToolchainService>()
                    .launcherFor { languageVersion.set(testJdk) },
            )
        }
    }
}

// Wasm tests need no npm packages, so KGP's wasm yarn lock is an empty header that nothing
// tracks. On Windows yarn writes no lock at all for such a workspace, and the store task then
// fails input validation ("build/wasm/yarn.lock doesn't exist"). The JS lock stays stored.
tasks.matching { it.name == "kotlinWasmStoreYarnLock" }.configureEach { enabled = false }

val dokkaSourceLinkRef = providers.environmentVariable("SCM_TAG").orElse("dev")

allprojects {
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Jar>().configureEach {
        manifest.attributes.remove("Build-Jdk")
        manifest.attributes.remove("Build-Jdk-Spec")
        manifest.attributes.remove("Built-By")
        manifest.attributes.remove("Build-Date")
        manifest.attributes.remove("Build-Timestamp")
        manifest.attributes.remove("Created-By")
    }

    plugins.withId("org.jetbrains.dokka") {
        extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension>("dokka") {
            dokkaSourceSets.configureEach {
                if (name.startsWith("ios")) {
                    displayName.set("ios")
                }

                sourceLink {
                    localDirectory.set(rootDir)
                    remoteUrl("https://github.com/fluxo-kt/fluxo-io/blob/${dokkaSourceLinkRef.get()}")
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }
}

// A single-file Java program run by the Gradle JVM's `java`: it scans text only, so keeping it out
// of build logic spares every build a compile and keeps configuration-cache entries valid on a
// fresh checkout. The file set and every rule live in scripts/VerifyBuildPolicy.java.
tasks.register<Exec>("verifyBuildPolicy") {
    group = "verification"
    description = "Verifies non-negotiable build, publication, and workflow policy invariants."
    val java = providers.systemProperty("java.home").map { "$it/bin/java" }
    commandLine(java.get(), "scripts/VerifyBuildPolicy.java", ".")
}

tasks.named("check") {
    dependsOn("verifyBuildPolicy")
}
