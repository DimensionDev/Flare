pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "Flare"
include(":app")
include(":linuxApp")
include(":shared")
include(":shared:ui")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
