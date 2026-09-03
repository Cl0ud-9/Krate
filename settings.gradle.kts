pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
