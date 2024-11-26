import java.net.URL

buildscript {
    // Starting from Kotlin 2.1.0, KGP doesn't depend on the `kotlin-compiler-embeddable`.
    // Other plugins can bring incompatible versions of the compiler.
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1729256644747559?thread_ts=1729151089.194689&cid=C0KLZSCHF
    dependencies.classpath(libs.kotlin.compiler.embeddable)
}

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
        sonatypeHost = com.vanniktech.maven.publish.SonatypeHost.S01
    }

    enableApiValidation = true

    setupVerification = true
    enableGenericAndroidLint = true
    enableGradleDoctor = true
    experimentalLatestCompilation = true
    latestSettingsForTests = true
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
    }

    // TODO: Disable Kover by default to reduce performance penalty.
    //  https://github.com/Kotlin/kotlinx-kover/issues/531#issuecomment-1929483468
    val isCI by isCI()
    val isRelease by isRelease()

    filters {
        // Test classes
        excludes.classes("*Test")

        includes.classes("kotlinx.kover.examples.merged.*")
    }

    verify {
        @Suppress("MagicNumber")
        rule {
            disabled = false
            groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION
            minBound(50)
            bound {
                minValue = 72
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 65
                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION
                aggregationForGroup =
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 50
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
        html {
            onCheck = !isCI && isRelease
            htmlDir = layout.buildDirectory.dir("reports/kover-merged-report-html")
        }
    }
}

allprojects {
    // FIXME: Setup automatically.
    plugins.withType<org.jetbrains.dokka.gradle.DokkaPlugin> {
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
            dokkaSourceSets {
                configureEach {
                    if (name.startsWith("ios")) {
                        displayName.set("ios")
                    }

                    sourceLink {
                        localDirectory.set(rootDir)
                        @Suppress("DEPRECATION")
                        remoteUrl.set(URL("https://github.com/fluxo-kt/fluxo-io/blob/main"))
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }
    }
}


/**
 * Find Android SDK JAR for usage as a dependency in non-android modules or source sets.
 */
fun findAndroidJar(project: Project, compileSdkVersion: Int): FileCollection {
    fun getSdkDirFromLocalProperties(): String? {
        // Get "sdk.dir" property from local.properties file.
        return project.file("local.properties")
            .takeIf { it.exists() }
            ?.run {
                readLines().firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter("sdk.dir=")
            }
    }

    fun findAndroidSdkDir(): String? {
        // https://developer.android.com/studio/command-line/variables
        return getSdkDirFromLocalProperties()
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getProperty("android.home")
    }

    // References:
    // https://android.googlesource.com/platform/tools/base/+/f6bef46dc8/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/SdkHandler.java#319
    // https://github.com/stepango/android-jar
    val androidSdkDir = findAndroidSdkDir()
        ?: throw java.io.FileNotFoundException("Can't locate Android SDK path")

    return project.files("$androidSdkDir/platforms/android-$compileSdkVersion/android.jar")
}

extra["androidJar"] = findAndroidJar(project, libs.versions.androidCompileSdk.get().toInt())
