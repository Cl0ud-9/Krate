pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// lets Gradle download a matching JDK automatically (via the Foojay Disco API) instead of just
// failing with "Toolchain download repositories have not been configured" - needed because the
// benchmark build type (added by the baseline profile plugin) requires a JDK 17 toolchain that
// isn't guaranteed to already be installed on every machine this repo is built on
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // smooth-corner-rect-android-compose, for the Material 3 Expressive squircle shape
        maven("https://jitpack.io")
    }
}

rootProject.name = "manager"
include(":app")
include(":baselineprofile")
