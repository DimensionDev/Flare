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
include(":codegen")
include(":foundation")
include(":plugins:badge")
include(":benchmark:android")
include(":benchmark:apple-shared")
include(":demo:androidApp")
include(":demo:shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
