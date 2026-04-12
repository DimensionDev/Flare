package dev.dimension.flare.gradle

import de.jensklingenberg.ktorfit.gradle.KtorfitPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class FlareKtorfitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("flare.ksp")
        project.pluginManager.apply(project.flarePluginId("ktorfit"))
        project.configureKtorfitCompatibility()

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.extensions.configure<KotlinMultiplatformExtension> {
                addKspDependencyForTargets(project, project.flareLibrary("ktorfit-ksp"))

                sourceSets.named("commonMain") {
                    dependencies {
                        implementation(project.flareBundle("ktorfit"))
                    }
                }
            }
        }
    }
}

private fun Project.configureKtorfitCompatibility() {
    extensions.configure<KtorfitPluginExtension> {
        compilerPluginVersion.set("2.3.3")
    }
}
