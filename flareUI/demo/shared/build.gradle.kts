import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.ui.demo.shared"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
        )
    }

    listOf("iosArm64", "iosSimulatorArm64", "macosArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "FlareUI"
                isStatic = true
                export(project(":flare-runtime"))
                export(project(":foundation"))
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
            }
        }
    }
}
