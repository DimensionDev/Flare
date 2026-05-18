import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("dev.dimension.flare.root-conventions")
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.nucleus) apply false
}

val validateModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Validates modularization dependency and source boundary rules."

    doLast {
        val violations = mutableListOf<String>()

        fun Project.projectDependencyPaths(): Set<String> {
            val paths = linkedSetOf<String>()
            configurations.forEach { configuration ->
                configuration.dependencies.forEach { dependency ->
                    if (dependency is ProjectDependency) {
                        paths += dependency.path
                    }
                }
            }
            return paths
        }

        fun Project.kotlinSourceFiles() =
            fileTree(projectDir) {
                include("src/**/*.kt")
                exclude("build/**")
            }.files

        rootProject.findProject(":shared")?.let {
            violations += ":shared is still included in settings.gradle.kts"
        }

        val sharedProductionFiles =
            fileTree(rootProject.layout.projectDirectory.dir("shared")) {
                include("src/**/*.kt")
                include("*.gradle.kts")
                exclude("build/**")
            }.files
        if (sharedProductionFiles.isNotEmpty()) {
            violations +=
                "shared still contains production files: " +
                    sharedProductionFiles.joinToString { it.relativeTo(rootDir).path }
        }

        val socialPlatformModules =
            subprojects
                .map { it.path }
                .filter { it.startsWith(":social:") && it !in setOf(":social:api", ":social:microblog") }
                .toSet()

        subprojects.forEach { project ->
            val dependencyPaths = project.projectDependencyPaths()

            dependencyPaths
                .filter { it == ":shared" }
                .forEach { violations += "${project.path} depends on removed module $it" }

            if (project.path.startsWith(":core:")) {
                dependencyPaths
                    .filter {
                        it.startsWith(":data:") ||
                            it.startsWith(":social:") ||
                            it.startsWith(":presenter:") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path.startsWith(":presenter:") || project.path.startsWith(":web:")) {
                if (":compose-ui" in dependencyPaths) {
                    violations += "${project.path} must not depend on :compose-ui"
                }
            }

            if (project.path in socialPlatformModules) {
                dependencyPaths
                    .filter { it in socialPlatformModules }
                    .forEach { violations += "${project.path} must not depend on sibling platform module $it" }
            }
        }

        val forbiddenCoreImport =
            Regex("""^import\s+dev\.dimension\.flare\.(data|social|ui\.presenter|ui\.component|ui\.screen)\.""")
        subprojects
            .filter { it.path.startsWith(":core:") }
            .forEach { project ->
                project.kotlinSourceFiles().forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (forbiddenCoreImport.containsMatchIn(line)) {
                                violations +=
                                    "${file.relativeTo(rootDir).path}:${index + 1} forbidden core import: ${line.trim()}"
                            }
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    separator = "\n",
                    prefix = "Module boundary violations:\n",
                ),
            )
        }
    }
}
