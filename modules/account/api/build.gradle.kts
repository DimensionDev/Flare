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
        namespace = "dev.dimension.flare.modules.account.api"
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
                        "flare_account_api_commonMain",
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
                                "flare_account_api",
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
                api(projects.core.model)
                api(dependencies.platform(libs.compose.bom))
                api(libs.compose.runtime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.bluesky.oauth)
            }
        }
    }
}
