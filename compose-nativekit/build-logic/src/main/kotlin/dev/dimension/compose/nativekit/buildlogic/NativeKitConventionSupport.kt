package dev.dimension.compose.nativekit.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

public enum class NativeKitPlatform {
    ANDROID,
    JVM,
    IOS,
    MACOS,
}

public class NativeKitMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = Unit
}

public class NativeKitRootConventionsPlugin : Plugin<Project> {
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

public class NativeKitModuleSpec internal constructor(
    private val kotlin: KotlinMultiplatformExtension,
) {
    public var namespace: String? = null

    private val platforms = linkedSetOf<NativeKitPlatform>()
    public fun platforms(vararg values: NativeKitPlatform) {
        platforms.clear()
        platforms.addAll(values)
    }

    internal fun apply() {
        require(platforms.isNotEmpty()) {
            "nativeKit { } requires at least one platform."
        }

        kotlin.explicitApi()

        kotlin.applyDefaultHierarchyTemplate()

        if (NativeKitPlatform.ANDROID in platforms) {
            kotlin.targets.getByName<KotlinMultiplatformAndroidLibraryTarget>("android") {
                compileSdk {
                    version = release(project.intVersion("compileSdk")) {
                        minorApiLevel = 0
                    }
                }
                this.namespace = this@NativeKitModuleSpec.namespace
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.intVersion("java").toString()))
                }
            }
        }
        if (NativeKitPlatform.JVM in platforms) kotlin.jvm()
        if (NativeKitPlatform.IOS in platforms) {
            kotlin.iosArm64()
            kotlin.iosSimulatorArm64()
        }
        if (NativeKitPlatform.MACOS in platforms) {
            kotlin.macosArm64()
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
    }
}

public fun KotlinMultiplatformExtension.nativeKit(
    configure: NativeKitModuleSpec.() -> Unit,
) {
    NativeKitModuleSpec(this).apply(configure).apply()
}

private fun Project.intVersion(name: String): Int =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()
