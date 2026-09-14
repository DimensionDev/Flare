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
        namespace = "dev.dimension.compose.nativekit.navigation"
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
                api(project(":nativekit-runtime"))
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
