package dev.dimension.flare.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class FlareKoinPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(project.flarePluginId("koin"))

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    dependencies {
                        implementation(project.dependencies.platform(project.flareLibrary("koin-bom")))
                        implementation(project.flareLibrary("koin-core"))
                        implementation(project.flareLibrary("koin-annotations"))
                    }
                }
            }
        }
    }
}
