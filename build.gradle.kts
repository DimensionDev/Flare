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
                .filter { it.startsWith(":social:") && it !in setOf(":social:api", ":social:microblog", ":social:nodeinfo") }
                .toSet()

        subprojects.forEach { project ->
            val dependencyPaths = project.projectDependencyPaths()

            dependencyPaths
                .filter { it == ":shared" }
                .forEach { violations += "${project.path} depends on removed module $it" }

            if (project.path.startsWith(":core:")) {
                dependencyPaths
                    .filter {
                        it == ":network" ||
                            it.startsWith(":storage:") ||
                            it.startsWith(":capability:") ||
                            it.startsWith(":social:") ||
                            (it.startsWith(":presentation:") && it != ":presentation:model") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path.startsWith(":presentation:") || project.path.startsWith(":web:")) {
                if (":compose-ui" in dependencyPaths) {
                    violations += "${project.path} must not depend on :compose-ui"
                }
            }

            if (project.path.startsWith(":web:") && ":social:nostr" in dependencyPaths) {
                violations += "${project.path} must not export Nostr on Web"
            }

            if (project.path == ":storage:database") {
                dependencyPaths
                    .filter {
                        it.startsWith(":social:") ||
                            it.startsWith(":capability:") ||
                            (it.startsWith(":presentation:") && it != ":presentation:model") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path.startsWith(":storage:") && project.path != ":storage:database") {
                dependencyPaths
                    .filter {
                        it.startsWith(":social:") ||
                            it.startsWith(":capability:") ||
                            it.startsWith(":presentation:") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path.startsWith(":capability:")) {
                dependencyPaths
                    .filter {
                        (it.startsWith(":presentation:") && it != ":presentation:model") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path.startsWith(":social:")) {
                dependencyPaths
                    .filter {
                        it.startsWith(":capability:") ||
                            (it.startsWith(":presentation:") && it != ":presentation:model") ||
                            it == ":compose-ui"
                    }.forEach { violations += "${project.path} must not depend on $it" }
            }

            if (project.path in socialPlatformModules) {
                dependencyPaths
                    .filter { it in socialPlatformModules }
                    .forEach { violations += "${project.path} must not depend on sibling platform module $it" }
            }

            project.kotlinSourceFiles().forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (line.matches(Regex("""^\s*package\s+dev\.dimension\.flare\.shared(\.|$).*"""))) {
                            violations +=
                                "${file.relativeTo(rootDir).path}:${index + 1} must not use removed shared package: ${line.trim()}"
                        }
                    }
                }
            }
        }

        val projectDependencyGraph =
            subprojects.associate { project ->
                project.path to
                    project
                        .projectDependencyPaths()
                        .filter { it != project.path && rootProject.findProject(it) != null }
                        .toSet()
            }
        val visitState = mutableMapOf<String, Int>()
        val dependencyStack = mutableListOf<String>()
        val cycles = linkedSetOf<String>()

        fun visitProject(path: String) {
            visitState[path] = 1
            dependencyStack += path
            projectDependencyGraph[path].orEmpty().forEach { dependency ->
                when (visitState[dependency]) {
                    1 -> {
                        val cycleStart = dependencyStack.indexOf(dependency)
                        if (cycleStart >= 0) {
                            cycles += (dependencyStack.drop(cycleStart) + dependency).joinToString(" -> ")
                        }
                    }

                    2 -> Unit
                    else -> visitProject(dependency)
                }
            }
            dependencyStack.removeAt(dependencyStack.lastIndex)
            visitState[path] = 2
        }

        projectDependencyGraph.keys.forEach { path ->
            if (visitState[path] == null) {
                visitProject(path)
            }
        }
        cycles.forEach { cycle ->
            violations += "Project dependency cycle: $cycle"
        }

        val forbiddenRegistryBypassPatterns =
            listOf(
                Regex("""\bPlatformType\.(spec|icon|agreementUrl)\b""") to
                    "use SocialPlatformRegistry metadata instead of PlatformType extensions",
                Regex("""\b(fun|val)\s+PlatformType\.(spec|icon|agreementUrl)\b""") to
                    "do not reintroduce PlatformType metadata extensions",
                Regex("""\bfun\s+UiAccount\.createDataSource\b""") to
                    "use SocialPlatformRegistry.createDataSource(account) instead of UiAccount.createDataSource()",
                Regex("""\.\s*createDataSource\s*\(\s*\)""") to
                    "use SocialPlatformRegistry.createDataSource(account) instead of zero-argument createDataSource()",
                Regex("""\bPlatformType\.entries\b""") to
                    "use SocialPlatformRegistry for platform iteration",
                Regex("""\bPlatformType\.values\s*\(""") to
                    "use SocialPlatformRegistry for platform iteration",
            )
        subprojects.forEach { project ->
            project.kotlinSourceFiles().forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        forbiddenRegistryBypassPatterns.forEach { (pattern, message) ->
                            if (pattern.containsMatchIn(line)) {
                                violations +=
                                    "${file.relativeTo(rootDir).path}:${index + 1} $message: ${line.trim()}"
                            }
                        }
                    }
                }
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
