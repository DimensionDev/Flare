import dev.dimension.flareui.buildlogic.FlareUiPlatform
import dev.dimension.flareui.buildlogic.flareUi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dev.dimension.flareui.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.moko.resources)
}

kotlin {
    flareUi {
        namespace = "dev.dimension.flare.ui.demo.shared"
        platforms(
            FlareUiPlatform.ANDROID,
            FlareUiPlatform.IOS,
            FlareUiPlatform.MACOS,
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
                baseName = "FlareUI"
                isStatic = true
                export(project(":flare-runtime"))
                export(project(":foundation"))
                export(project(":flare-lazy-layout"))
                export(project(":flare-navigation"))
                export(project(":flare-resources-moko"))
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":foundation"))
                api(project(":flare-lazy-layout"))
                api(project(":flare-navigation"))
                api(project(":flare-resources-moko"))
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
    resourcesPackage.set("dev.dimension.flare.ui.demo.resources")
    resourcesClassName.set("DemoRes")
    iosBaseLocalizationRegion.set("en")
    iosMinimalDeploymentTarget.set("12.0")
}
