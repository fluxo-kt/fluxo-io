// The consumer floor. JS/Native/Wasm klibs are readable only by a compiler of the same or a newer
// minor version than the one that built them (a 2.4.20-built klib fails on 2.2.21 and 2.3.21 and
// works on 2.4.0); the language version only lowers the JVM floor. So the oldest consumer that
// must work is <library compiler major.minor>.0, chosen by scripts/consumer-check.sh. If this
// build fails to resolve, compile, link or run, the documented floor is false.
plugins {
    kotlin("multiplatform")
}

val fluxoIoVersion: String = providers.gradleProperty("fluxoIoVersion").get()

kotlin {
    jvm()
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    macosArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.fluxo-kt:fluxo-io-rad:$fluxoIoVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
