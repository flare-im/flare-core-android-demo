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
    }
}

rootProject.name = "flare-core-android-app"
include(":app")
include(":flare-core-android-sdk")

project(":flare-core-android-sdk").projectDir = file("../../packages/flare-core-android-sdk")
