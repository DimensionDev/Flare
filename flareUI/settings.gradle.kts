pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "flare-ui"

include(":flare-runtime")
project(":flare-runtime").projectDir = file("runtime")
include(":foundation")
include(":flare-lazy-layout")
project(":flare-lazy-layout").projectDir = file("lazy-layout")
include(":flare-navigation")
project(":flare-navigation").projectDir = file("navigation")
include(":flare-resources-moko")
project(":flare-resources-moko").projectDir = file("resources-moko")
include(":demo:androidApp")
include(":demo:shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
