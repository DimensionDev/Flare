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
        namespace = "dev.dimension.compose.nativekit.foundation"
        platforms(
            NativeKitPlatform.ANDROID,
            NativeKitPlatform.JVM,
            NativeKitPlatform.IOS,
            NativeKitPlatform.MACOS,
        )
    }
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":nativekit-runtime"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.material.components)
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
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
