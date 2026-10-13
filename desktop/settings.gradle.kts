pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.jetbrains.compose") version "1.7.3"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
        id("org.jetbrains.kotlin.multiplatform") version "2.1.10"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "NAFI-TV-24-Desktop"
