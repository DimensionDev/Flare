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
        )
    }

    listOf("iosArm64", "iosSimulatorArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "FlareUI"
                isStatic = true
                export(project(":flare-runtime"))
                export(project(":foundation"))
                export(project(":plugins:badge"))
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
                api(project(":plugins:badge"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
            }
        }
    }
}

tasks
    .matching { task -> task.name == "embedAndSignAppleFrameworkForXcode" }
    .configureEach {
        dependsOn(
            ":foundation:generateFlareSwiftUISources",
            ":plugins:badge:generateFlareSwiftUISources",
        )
    }
