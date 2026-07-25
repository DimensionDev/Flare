import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.flareui.android.compose"
        platforms(FlareUiPlatform.ANDROID)
    }
    android {
        withHostTest {}
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
            }
        }
        val androidMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/flareui/kotlin"))
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.ui)
                implementation(libs.material3)
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
