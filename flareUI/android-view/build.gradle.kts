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
        namespace = "dev.dimension.flare.flareui.android.view"
        platforms(FlareUiPlatform.ANDROID)
    }
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
            }
        }
        val androidMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/flareui/kotlin"))
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.material.components)
                implementation(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-android:" +
                        libs.versions.kotlinx.coroutines.get(),
                )
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
    }
}

tasks.configureEach {
    if (
        name.startsWith("compile") ||
        name.startsWith("ksp") ||
        name.startsWith("runKtlint") ||
        name == "prepareKotlinIdeaImport"
    ) {
        dependsOn(":codegen:verifyFlareUiRenderers")
    }
}
