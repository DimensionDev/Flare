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
include(":compose-ui")
include(":desktopApp")
include(":core:common")
include(":core:model")
include(":core:deeplink")
include(":network")
include(":storage:database")
include(":storage:datastore")
include(":capability:account")
include(":capability:ai")
include(":capability:draft")
include(":capability:local")
include(":capability:settings")
include(":capability:translation")
include(":social:api")
include(":social:microblog")
include(":social:nodeinfo")
include(":social:mastodon")
include(":social:rss")
include(":social:misskey")
include(":social:bluesky")
include(":social:nostr")
include(":social:xqt")
include(":social:vvo")
include(":presentation:model")
include(":presentation:runtime")
include(":presentation:features")
include(":web:presenter-export")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
