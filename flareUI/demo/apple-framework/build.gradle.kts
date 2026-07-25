import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        platforms(
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    listOf("iosArm64", "iosSimulatorArm64", "macosArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "FlareUIDemoKit"
                isStatic = true
                export(projects.flareUI.appleRuntime)
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.flareUI.appleRuntime)
                implementation(projects.flareUI.demo.shared)
            }
        }
    }
}

tasks.matching { task -> task.name == "embedAndSignAppleFrameworkForXcode" }
    .configureEach {
        dependsOn(":flareUI:codegen:verifyFlareUiRenderers")
    }
