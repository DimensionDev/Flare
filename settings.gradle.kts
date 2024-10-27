pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://androidx.dev/kmp/builds/12561008/artifacts/snapshots/repository")
    }
}
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://androidx.dev/kmp/builds/12561008/artifacts/snapshots/repository")
    }
}

rootProject.name = "Flare"
include(":app")
include(":shared")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
