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

rootProject.name = "compose-nativekit"

include(":nativekit-runtime")
project(":nativekit-runtime").projectDir = file("runtime")
include(":foundation")
include(":nativekit-lazy-layout")
project(":nativekit-lazy-layout").projectDir = file("lazy-layout")
include(":nativekit-navigation")
project(":nativekit-navigation").projectDir = file("navigation")
include(":nativekit-resources-moko")
project(":nativekit-resources-moko").projectDir = file("resources-moko")
include(":demo:androidApp")
include(":demo:shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
