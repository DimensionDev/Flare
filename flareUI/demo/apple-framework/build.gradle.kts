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
        platforms(
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
        )
    }

    listOf("iosArm64", "iosSimulatorArm64", "macosArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "FlareUIDemoKit"
                isStatic = true
                export(project(":apple-runtime"))
                export(project(":core"))
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":apple-runtime"))
                implementation(project(":demo:shared"))
            }
        }
    }
}

tasks.matching { task -> task.name == "embedAndSignAppleFrameworkForXcode" }
    .configureEach {
        dependsOn(":codegen:verifyFlareUiRenderers")
        dependsOn(":demo:shared:generateFlareUiResources")
    }
