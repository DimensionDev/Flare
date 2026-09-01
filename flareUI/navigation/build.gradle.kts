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
        namespace = "dev.dimension.flare.ui.navigation"
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
                api(project(":flare-runtime"))
                api(libs.navigation3.runtime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.androidx.fragment.ktx)
                implementation(libs.compose.material3)
                implementation(libs.material.components)
                implementation(libs.navigation3.ui)
            }
        }
    }
}
