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
        namespace = "dev.dimension.flare.ui.runtime"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.JVM,
            FlareUiPlatform.IOS,
        )
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                api(libs.compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-android:" +
                        libs.versions.kotlinx.coroutines.get(),
                )
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
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
