import dev.dimension.compose.nativekit.buildlogic.NativeKitPlatform
import dev.dimension.compose.nativekit.buildlogic.nativeKit

plugins {
    id("dev.dimension.compose.nativekit.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    nativeKit {
        namespace = "dev.dimension.compose.nativekit.runtime"
        platforms(
            NativeKitPlatform.ANDROID,
            NativeKitPlatform.JVM,
            NativeKitPlatform.IOS,
            NativeKitPlatform.MACOS,
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
                implementation(libs.compose.ui)
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
