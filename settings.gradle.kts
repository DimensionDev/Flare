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
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
include(":shared:ui")
include(":compose-ui")
include(":desktopApp")
include(":server")
include(":shared:api")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
