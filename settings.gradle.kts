pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://androidx.dev/snapshots/builds/13932663/artifacts/repository")
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
include(":shared:ui")
include(":shared:ui:component")
include(":desktopApp")
include(":server")
include(":shared:api")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
