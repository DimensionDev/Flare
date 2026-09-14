import dev.dimension.compose.nativekit.buildlogic.NativeKitPlatform
import dev.dimension.compose.nativekit.buildlogic.nativeKit
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.compose.nativekit.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.moko.resources)
}

kotlin {
    nativeKit {
        namespace = "dev.dimension.compose.nativekit.demo.shared"
        platforms(
            NativeKitPlatform.ANDROID,
            NativeKitPlatform.IOS,
            NativeKitPlatform.MACOS,
        )
    }
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf("iosArm64", "iosSimulatorArm64", "macosArm64")
        .map { targetName -> targets.getByName(targetName) as KotlinNativeTarget }
        .forEach { appleTarget ->
            appleTarget.binaries.framework {
                baseName = "ComposeNativeKit"
                isStatic = true
                export(project(":nativekit-runtime"))
                export(project(":foundation"))
                export(project(":nativekit-lazy-layout"))
                export(project(":nativekit-navigation"))
                export(project(":nativekit-resources-moko"))
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
                api(project(":nativekit-lazy-layout"))
                api(project(":nativekit-navigation"))
                api(project(":nativekit-resources-moko"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.androidx.fragment.ktx)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.material.components)
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

multiplatformResources {
    resourcesPackage.set("dev.dimension.compose.nativekit.demo.resources")
    resourcesClassName.set("DemoRes")
    iosBaseLocalizationRegion.set("en")
    iosMinimalDeploymentTarget.set("12.0")
}
