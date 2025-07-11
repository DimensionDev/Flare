plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    listOf(
        macosX64(),
        macosArm64(),
        linuxX64(),
    ).forEach { nativeTarget ->
        nativeTarget.apply {
            binaries {
                executable {
                    entryPoint = "dev.dimension.flare.server.main"
                    runTask?.apply {
                        val argsProp = providers.gradleProperty("runArgs")
                        argumentProviders.add(CommandLineArgumentProvider {
                            argsProp.orNull?.split(" ") ?: emptyList()
                        })
                    }
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.clikt)
                implementation(libs.bundles.ktor.server)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.logback.classic)
                implementation(projects.shared.api)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.openai.client)
            }

        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val linuxMain by getting {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.resources)
                implementation(libs.ktor.client.content.negotiation)
            }
        }
    }
}