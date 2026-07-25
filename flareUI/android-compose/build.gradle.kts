import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.flareui.android.compose"
        platforms(FlarePlatform.ANDROID)
    }
    android {
        withHostTest {}
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.flareUI.core)
            }
        }
        val androidMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/flareui/kotlin"))
            dependencies {
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.ui)
                implementation(libs.material3)
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
        dependsOn(":flareUI:codegen:verifyFlareUiRenderers")
    }
}
