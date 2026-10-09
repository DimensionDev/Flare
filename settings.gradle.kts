pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content {
                includeGroup("moe.tlaster.ozone")
                includeGroup("moe.tlaster.ozone.generator")
            }
        }
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
        exclusiveContent {
            forRepository {
                ivy {
                    name = "unicodeUcd"
                    url = uri("https://www.unicode.org/Public/")
                    patternLayout { artifact("[revision]/ucd/[artifact].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.unicode", "UCD") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "twemojiParser"
                    url = uri("https://registry.npmjs.org/")
                    patternLayout { artifact("[module]/-/[module]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.twitter", "twemoji-parser") }
        }
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
include(":social:bluesky")
include(":social:bluesky:api")
include(":social:fanbox")
include(":social:mastodon")
include(":social:misskey")
include(":social:nostr")
include(":social:pixiv")
include(":social:vvo")
include(":social:xqt")
include(":feature:login-api")
include(":feature:agent")
include(":feature:login")
include(":feature:subscription")
include(":feature:tab")
include(":compose-ui")
include(":apple-shared")
include(":web-shared")
include(":web-presenter-processor")
include(":desktopApp")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
