pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// START Non-FOSS component
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// END Non-FOSS component
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
include(":compose-ui")
include(":desktopApp")
include(":readability")
include(":shared:api")
include(":core:common")
include(":core:humanizer")
include(":core:deeplink")
include(":service:core")
include(":service:mastodon")
include(":data:model")
include(":data:datastore")
include(":data:database")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
