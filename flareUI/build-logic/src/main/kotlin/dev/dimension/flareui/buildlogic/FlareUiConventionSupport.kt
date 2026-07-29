package dev.dimension.flareui.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

public enum class FlareUiPlatform {
    ANDROID,
    JVM,
    IOS,
}

public class FlareUiMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("com.google.devtools.ksp") {
            target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                val kotlin =
                    target.extensions.getByType<KotlinMultiplatformExtension>()

                // KSP does not connect common metadata generation to every concrete KMP target.
                // Add its output to commonMain and connect every source consumer except the
                // generating task itself.
                target.afterEvaluate {
                    val commonKsp =
                        target.configurations.findByName("kspCommonMainMetadata")
                    if (commonKsp?.dependencies?.isNotEmpty() == true) {
                        val commonKspTask = "kspCommonMainKotlinMetadata"
                        kotlin.sourceSets
                            .getByName("commonMain")
                            .kotlin
                            .srcDir(
                                target.layout.buildDirectory.dir(
                                    "generated/ksp/metadata/commonMain/kotlin",
                                ),
                            )
                        target.tasks
                            .matching { task ->
                                task.name != commonKspTask &&
                                    (
                                        task.name.startsWith("compile") ||
                                            task.name.startsWith("ksp") ||
                                            task.name.contains("Ktlint") ||
                                            task.name.endsWith("SourcesJar")
                                    )
                            }.configureEach {
                                dependsOn(commonKspTask)
                            }
                    }
                }
            }
        }
    }
}

public class FlareUiRootConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.subprojects.forEach { subproject ->
            subproject.pluginManager.apply(KtlintPlugin::class.java)
            subproject.extensions.configure<KtlintExtension> {
                version.set("1.8.0")
                filter {
                    exclude { element ->
                        element.file.path.contains("build", ignoreCase = true)
                    }
                }
            }
        }
    }
}

public class FlareUiModuleSpec internal constructor(
    private val kotlin: KotlinMultiplatformExtension,
) {
    public var namespace: String? = null

    private val platforms = linkedSetOf<FlareUiPlatform>()
    private var swiftUI: SwiftUIModule? = null

    public fun platforms(vararg values: FlareUiPlatform) {
        platforms.clear()
        platforms.addAll(values)
    }

    /**
     * Enables generated SwiftUI node and plugin plumbing for this module.
     *
     * The generated file is checked into the module's Swift source directory so SwiftPM can
     * resolve a clean checkout before Gradle has run.
     */
    public fun swiftUI(
        moduleName: String,
        kotlinModuleName: String = "FlareUI",
    ) {
        check(swiftUI == null) {
            "flareUi.swiftUI may only be configured once."
        }
        swiftUI =
            SwiftUIModule(
                moduleName = moduleName,
                kotlinModuleName = kotlinModuleName,
            )
    }

    internal fun apply() {
        require(platforms.isNotEmpty()) {
            "flareUi { } requires at least one platform."
        }

        kotlin.explicitApi()

        kotlin.applyDefaultHierarchyTemplate()

        if (FlareUiPlatform.ANDROID in platforms) {
            kotlin.targets.getByName<KotlinMultiplatformAndroidLibraryTarget>("android") {
                compileSdk {
                    version = release(project.intVersion("compileSdk")) {
                        minorApiLevel = 0
                    }
                }
                this.namespace = this@FlareUiModuleSpec.namespace
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.intVersion("java").toString()))
                }
            }
        }
        if (FlareUiPlatform.JVM in platforms) kotlin.jvm()
        if (FlareUiPlatform.IOS in platforms) {
            kotlin.iosArm64()
            kotlin.iosSimulatorArm64()
        }
        kotlin.compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                "-Xconsistent-data-class-copy-visibility",
            )
            optIn.addAll(
                "kotlin.time.ExperimentalTime",
                "kotlin.experimental.ExperimentalObjCRefinement",
            )
        }
        kotlin.jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(kotlin.project.intVersion("java")))
        }
        swiftUI?.apply()
    }

    private fun SwiftUIModule.apply() {
        require(FlareUiPlatform.IOS in platforms) {
            "flareUi.swiftUI requires the iOS platform."
        }

        val project = kotlin.project
        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            project.extensions.configure<KspExtension> {
                arg("flare.swiftui.moduleName", moduleName)
                arg("flare.swiftui.kotlinModuleName", kotlinModuleName)
            }

            val fileName = "${moduleName}SwiftUIGenerated.swift"
            val generatedFile =
                project.layout.buildDirectory.file(
                    "generated/ksp/metadata/commonMain/resources/$fileName",
                )
            val checkedInFile =
                project.layout.projectDirectory.file(
                    "src/iosMain/swift/generated/$fileName",
                )
            val commonKspTask = "kspCommonMainKotlinMetadata"

            project.tasks.register<Sync>("generateFlareSwiftUISources") {
                group = "flare ui"
                description = "Updates the checked-in $moduleName SwiftUI generated source."
                dependsOn(commonKspTask)
                from(generatedFile)
                into(checkedInFile.asFile.parentFile)
            }

            val verifyTask =
                project.tasks.register("verifyFlareSwiftUISources") {
                    group = "verification"
                    description = "Checks that the $moduleName SwiftUI source is up to date."
                    dependsOn(commonKspTask)
                    inputs.file(generatedFile)
                    inputs.file(checkedInFile)
                    doLast {
                        val generated = generatedFile.get().asFile
                        val checkedIn = checkedInFile.asFile
                        check(
                            checkedIn.isFile &&
                                generated.readBytes().contentEquals(checkedIn.readBytes()),
                        ) {
                            "$checkedIn is stale. Run " +
                                "${project.path}:generateFlareSwiftUISources."
                        }
                    }
                }

            project.tasks
                .matching { task -> task.name == "check" }
                .configureEach {
                    dependsOn(verifyTask)
                }
        }
    }

    private data class SwiftUIModule(
        val moduleName: String,
        val kotlinModuleName: String,
    )
}

public fun KotlinMultiplatformExtension.flareUi(
    configure: FlareUiModuleSpec.() -> Unit,
) {
    FlareUiModuleSpec(this).apply(configure).apply()
}

private fun Project.intVersion(name: String): Int =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()
