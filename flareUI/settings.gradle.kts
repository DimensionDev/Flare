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

include(":core")
include(":codegen")
include(":android-compose")
include(":android-view")
include(":apple-runtime")
include(":demo:shared")
include(":demo:androidApp")
include(":demo:apple-framework")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
