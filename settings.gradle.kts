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
include(":ui:model")
include(":data:network")
include(":data:database")
include(":data:datastore")
include(":data:ai")
include(":data:account")
include(":data:nodeinfo")
include(":data:translation")
include(":data:draft")
include(":data:local")
include(":social:api")
include(":social:microblog")
include(":social:mastodon")
include(":social:rss")
include(":social:misskey")
include(":social:bluesky")
include(":social:nostr")
include(":social:xqt")
include(":social:vvo")
include(":presenter:runtime")
include(":presenter:features")
include(":web:presenter-export")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
