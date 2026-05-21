import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare

val hasGoogleServices = rootProject.file("app/google-services.json").exists()

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.modules.ai.data"
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
                        "flare_ai_data_commonMain",
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
                                "flare_ai_data",
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
                implementation(projects.core.common)
                api(projects.modules.settings.data)
                implementation(projects.foundation.network)
                implementation(libs.ktor.client.logging)
                implementation(libs.openai.client)
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
            }
        }
        val androidMain by getting {
            if (hasGoogleServices) {
                kotlin.srcDir("src/play/kotlin")
            } else {
                kotlin.srcDir("src/foss/kotlin")
            }
            dependencies {
                // START Non-FOSS component
                if (hasGoogleServices) {
                    implementation(libs.kotlinx.coroutines.play.services)
                    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
                    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
                }
                // END Non-FOSS component
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
