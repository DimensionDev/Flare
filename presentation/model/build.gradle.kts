import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.presentation.model"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
    }

    targets.configureEach {
        if (name != "wasmJs") {
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.addAll(
                            "-module-name",
                            "flare_ui_model",
                        )
                    }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.deeplink)
                api(projects.core.model)
                api(projects.storage.datastore)
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
                api(libs.paging.common)
                api(libs.kotlinx.serialization.json)
                api(libs.bluesky.oauth)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.kotlin.codepoints.deluxe)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ksoup)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
