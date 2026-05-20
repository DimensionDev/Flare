import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.modules.local.model"
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
                        "flare_local_model_commonMain",
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
                                "flare_local_model",
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
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
            }
        }
    }
}
