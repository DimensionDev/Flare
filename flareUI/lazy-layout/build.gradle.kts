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
        namespace = "dev.dimension.flare.ui.lazy"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.JVM,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
        )
    }
    android {
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime.saveable)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                implementation(libs.recyclerview)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
                implementation(libs.compose.material3)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
