import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.ui.benchmark.apple"
        platforms(FlareUiPlatform.IOS)
    }

    listOf("iosArm64", "iosSimulatorArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "FlareUI"
                isStatic = true
                export(project(":flare-runtime"))
                export(project(":foundation"))
            }
        }

    sourceSets.getByName("iosMain") {
        dependencies {
            api(project(":flare-runtime"))
            api(project(":foundation"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks
    .matching { task -> task.name == "embedAndSignAppleFrameworkForXcode" }
    .configureEach {
        dependsOn(":foundation:generateFlareSwiftUISources")
    }
