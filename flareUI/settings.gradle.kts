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
include(":demo:androidApp")
include(":demo:shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
