// Standalone consumer build, never included from the root build: it must resolve fluxo-io-rad
// the way a real consumer does (from a Maven repository), on the oldest Kotlin the library
// promises to support (`consumerKotlin`). Driven by scripts/consumer-check.sh.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("multiplatform") version providers.gradleProperty("consumerKotlin").get()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "fluxo-io-consumer-check"
