import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        platforms(
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.flareUI.core)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val appleMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/flareui/kotlin"))
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
