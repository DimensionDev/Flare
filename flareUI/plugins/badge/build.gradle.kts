import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.ui.plugin.badge"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
        )
        swiftUI("Badge")
    }
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":flare-runtime"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(project(":foundation"))
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":codegen"))
    add("kspAndroid", project(":codegen"))
    add("kspIosArm64", project(":codegen"))
    add("kspIosSimulatorArm64", project(":codegen"))
}
