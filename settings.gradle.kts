pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://github.com/poolborges/maven/raw/master/thirdparty/")
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
include(":compose-ui")
include(":desktopApp")
include(":server")
include(":shared:api")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
