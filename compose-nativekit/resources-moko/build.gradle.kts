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
        namespace = "dev.dimension.compose.nativekit.resources.moko"
        platforms(
            NativeKitPlatform.ANDROID,
            NativeKitPlatform.IOS,
            NativeKitPlatform.MACOS,
        )
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":nativekit-runtime"))
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
