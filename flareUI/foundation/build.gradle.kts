import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

dependencies {
    add("kspCommonMainMetadata", project(":codegen"))
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.ui.foundation"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
        )
        swiftUI("Foundation")
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
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
    }
}

dependencies {
    add("kspAndroid", project(":codegen"))
    add("kspIosArm64", project(":codegen"))
    add("kspIosSimulatorArm64", project(":codegen"))
}
