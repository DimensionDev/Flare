import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.modules.translation.api"
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
                        "flare_translation_api_commonMain",
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
                                "flare_translation_api",
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
                api(projects.modules.translation.model)
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
