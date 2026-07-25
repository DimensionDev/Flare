import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val repositoryRoot = rootProject.layout.projectDirectory
val generatedComposeSources =
    project(":flareUI:android-compose").layout.buildDirectory
        .dir("generated/flareui/kotlin")
val generatedViewSources =
    project(":flareUI:android-view").layout.buildDirectory
        .dir("generated/flareui/kotlin")
val generatedAppleSources =
    project(":flareUI:apple-runtime").layout.buildDirectory
        .dir("generated/flareui/kotlin")

kotlin {
    flare {
        namespace = "dev.dimension.flare.flareui.core"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    add("kspJvm", projects.flareUI.codegen)
}

ksp {
    arg("flareUiRepositoryRoot", repositoryRoot.asFile.absolutePath)
}

tasks.matching { task -> task.name == "kspKotlinJvm" }
    .configureEach {
        outputs.dir(generatedComposeSources)
        outputs.dir(generatedViewSources)
        outputs.dir(generatedAppleSources)
        outputs.files(
            repositoryRoot.file(
                "flareUI/apple/Sources/SwiftUI/Generated/FlareSwiftUINode.generated.swift",
            ),
            repositoryRoot.file(
                "flareUI/apple/Sources/UIKit/Generated/FlareUIKitNodeFactory.generated.swift",
            ),
            repositoryRoot.file(
                "flareUI/apple/Sources/AppKit/Generated/FlareAppKitNodeFactory.generated.swift",
            ),
        )
    }
