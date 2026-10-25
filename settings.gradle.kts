import org.gradle.api.initialization.resolve.RepositoriesMode

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

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("com.android.legacy-kapt") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}

rootProject.name = "KClash"

include(":app", ":core", ":service", ":common")
project(":app").projectDir = file("android/app")
project(":core").projectDir = file("android/core")
project(":service").projectDir = file("android/service")
project(":common").projectDir = file("android/common")
