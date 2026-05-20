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
        namespace = "dev.dimension.flare.modules.account.data"
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
                        "flare_account_data_commonMain",
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
                                "flare_account_data",
                            )
                        }
                    }
                }
            }
        }
    }

    compilerOptions {
        allWarningsAsErrors.set(false)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.model)
                api(projects.foundation.database)
                api(projects.modules.account.api)
                api(projects.modules.settings.data)
                api(projects.social.api)
                api(projects.ui.model)
                api(libs.kotlinx.coroutines.core)
                implementation(projects.social.microblog)
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
            }
        }
    }
}
