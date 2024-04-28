@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }

    // For local development.
    //includeBuild("../fluxo-kmp-conf")
}

plugins {
    // https://plugins.gradle.org/plugin/com.gradle.enterprise
    id("com.gradle.enterprise") version "3.16.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "fluxo-io"

// On module update, don't forget to update '.github/workflows/build.yml'!

include(":fluxo-io-rad")
