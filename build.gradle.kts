import buildlogic.VerifyBuildPolicyTask
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

    enableApiValidation = true
    useDokka = true

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

val buildPolicyRootDir: java.io.File = layout.projectDirectory.asFile
val buildPolicyExcludedDirs = setOf(
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    ".kotlin-js-store",
    "build",
    "buildSrc/build",
    "dependencies",
    "node_modules",
)
val buildPolicyTextExtensions = setOf(
    "gradle",
    "kts",
    "kt",
    "java",
    "properties",
    "toml",
    "md",
    "txt",
    "xml",
    "yml",
    "yaml",
)
val buildPolicyFiles = fileTree(buildPolicyRootDir) {
    buildPolicyExcludedDirs.forEach { dir ->
        exclude("$dir/**", "**/$dir/**")
    }
    include(buildPolicyTextExtensions.map { "**/*.$it" })
}

tasks.register<VerifyBuildPolicyTask>("verifyBuildPolicy") {
    group = "verification"
    description = "Verifies non-negotiable build, publication, and workflow policy invariants."
    rootDirectory.set(buildPolicyRootDir.absolutePath)
    policyFiles.from(buildPolicyFiles)
}

tasks.named("check") {
    dependsOn("verifyBuildPolicy")
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
