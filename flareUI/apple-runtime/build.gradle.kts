import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

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

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val appleMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/flareui/kotlin"))
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.configureEach {
    if (
        name.startsWith("compile") ||
        name.startsWith("ksp") ||
        name.startsWith("runKtlint") ||
        name == "prepareKotlinIdeaImport"
    ) {
        dependsOn(":codegen:verifyFlareUiRenderers")
    }
}
