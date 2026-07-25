import com.google.devtools.ksp.gradle.KspAATask
import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val repositoryRoot = rootProject.layout.projectDirectory
val generatedComposeSources =
    project(":android-compose").layout.buildDirectory
        .dir("generated/flareui/kotlin")
val generatedViewSources =
    project(":android-view").layout.buildDirectory
        .dir("generated/flareui/kotlin")
val generatedAppleSources =
    project(":apple-runtime").layout.buildDirectory
        .dir("generated/flareui/kotlin")

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.flareui.core"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.JVM,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
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
    add("kspJvm", project(":codegen"))
}

ksp {
    arg("flareUiRepositoryRoot", repositoryRoot.asFile.absolutePath)
}

tasks.withType<KspAATask>()
    .matching { task -> task.name == "kspKotlinJvm" }
    .configureEach {
        // The generator writes complete cross-module registries in one pass.
        // An incremental KSP round only exposes changed symbols and would
        // otherwise overwrite those registries with a partial component set.
        kspConfig.incremental.set(false)
        outputs.upToDateWhen { false }

        outputs.dir(generatedComposeSources)
        outputs.dir(generatedViewSources)
        outputs.dir(generatedAppleSources)
        outputs.files(
            repositoryRoot.file(
                "apple/Sources/SwiftUI/Generated/FlareSwiftUINode.generated.swift",
            ),
            repositoryRoot.file(
                "apple/Sources/UIKit/Generated/FlareUIKitNodeFactory.generated.swift",
            ),
            repositoryRoot.file(
                "apple/Sources/AppKit/Generated/FlareAppKitNodeFactory.generated.swift",
            ),
            repositoryRoot.file(
                "apple/Sources/Runtime/Generated/FlareUINodes.generated.swift",
            ),
            repositoryRoot.file(
                "apple/Sources/KotlinBridge/Generated/" +
                    "FlareUIKotlinNodeBridge.generated.swift",
            ),
        )
    }
