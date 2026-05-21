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
include(":core:humanizer")
include(":core:model")
include(":foundation:deeplink")
include(":foundation:network")
include(":foundation:database")
include(":foundation:datastore")
include(":foundation:filesystem")
include(":modules:account:model")
include(":modules:account:api")
include(":modules:account:data")
include(":modules:draft:data")
include(":modules:draft:presentation")
include(":modules:settings:data")
include(":modules:translation:model")
include(":modules:translation:data")
include(":modules:ai:data")
include(":modules:local:data")
include(":modules:local:model")
include(":social:api")
include(":social:microblog")
include(":social:model")
include(":social:nodeinfo")
include(":social:mastodon")
include(":social:rss:model")
include(":social:rss")
include(":social:misskey")
include(":social:bluesky")
include(":social:nostr")
include(":social:xqt")
include(":social:vvo")
include(":ui:model")
include(":ui:richtext")
include(":ui:presenter-runtime")
include(":presentation:features")
include(":web:presenter-export")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
