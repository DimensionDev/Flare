package dev.dimension.flare.gradle

import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class FlareKspPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(project.flarePluginId("ksp"))
    }
}

fun KotlinMultiplatformExtension.addKspDependencyForTargets(
    project: Project,
    dependency: Any,
    targetFilter: (String) -> Boolean = { it != "metadata" },
) {
    targets.forEach { target ->
        target.name
            .takeIf(targetFilter)
            ?.toKspConfigurationName()
            ?.let { configurationName ->
                project.dependencies.add(configurationName, dependency)
            }
    }
}

private fun String.toKspConfigurationName(): String =
    "ksp${replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }}"
