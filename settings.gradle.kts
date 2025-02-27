pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "Flare"
include(":app")
val enableLinux = providers.gradleProperty("dev.dimension.flare.linux").orNull == "true"
if (enableLinux) {
    include(":linuxApp")
}
include(":shared")
include(":shared:ui")
include(":shared:ui:component")
include(":desktopApp")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
