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
include(":social:bluesky")
include(":social:mastodon")
include(":social:misskey")
include(":social:nostr")
include(":social:vvo")
include(":social:xqt")
include(":feature:login")
include(":feature:subscription")
include(":feature:tab")
include(":compose-ui")
include(":ios-shared")
include(":web-shared")
include(":web-presenter-processor")
include(":desktopApp")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
