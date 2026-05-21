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
        namespace = "dev.dimension.flare.ui.model"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.WEB,
        )
    }

    tasks
        .withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>()
        .configureEach {
            if (name == "compileCommonMainKotlinMetadata") {
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-module-name",
                        "flare_ui_common_model_commonMain",
                    )
                }
            }
        }

    targets.configureEach {
        if (name != "wasmJs" && name != "metadata") {
            compilations.configureEach {
                if (name == "main") {
                    compileTaskProvider.configure {
                        compilerOptions {
                            freeCompilerArgs.addAll(
                                "-module-name",
                                "flare_ui_common_model",
                            )
                        }
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
                implementation(projects.core.humanizer)
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
    }
}
