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
        namespace = "dev.dimension.flare.ui.resources.moko"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
        )
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":flare-runtime"))
                api(libs.moko.resources)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                implementation(libs.material.components)
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
