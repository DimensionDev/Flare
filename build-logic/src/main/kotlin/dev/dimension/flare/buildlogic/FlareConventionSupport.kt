package dev.dimension.flare.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.withAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

enum class FlarePlatform {
    ANDROID,
    JVM,
    IOS,
    MACOS,
    LINUX,
    WEB,
}

class FlareMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}

class FlareAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
    }
}

class FlareRootConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.subprojects.forEach { subproject ->
            subproject.pluginManager.apply(KtlintPlugin::class.java)
            subproject.extensions.configure<KtlintExtension> {
                version.set(ktlintCliVersion)
                filter {
                    exclude { element -> element.file.path.contains("build", ignoreCase = true) }
                    if (subproject.path == ":shared") {
                        exclude { element ->
                            element.file.absolutePath.contains("data/network/misskey/api/", ignoreCase = true)
                        }
                        exclude { element ->
                            element.file.absolutePath.contains("data/network/xqt/", ignoreCase = true)
                        }
                    }
                }
            }
        }
    }
}

class FlareAndroidApplicationSpec internal constructor(
    private val project: Project,
) {
    var namespace: String? = null
    var applicationId: String? = null

    internal fun apply(configure: ApplicationExtension.() -> Unit = {}) {
        val androidNamespace = requireNotNull(namespace) {
            "flare { namespace = ... } is required for Android applications."
        }
        val androidApplicationId = applicationId ?: androidNamespace

        project.extensions.configure<ApplicationExtension> {
            this.namespace = androidNamespace
            compileSdk {
                version = release(project.intVersion("compileSdk")) {
                    minorApiLevel = 0
                }
            }
            defaultConfig {
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                this.applicationId = androidApplicationId
                targetSdk {
                    version = release(project.intVersion("compileSdk"))
                }
            }
            configure()
        }
    }
}

private val commonOptIn = listOf(
    "kotlin.time.ExperimentalTime",
    "kotlin.experimental.ExperimentalObjCRefinement",
)

private val freeArgs = listOf(
    "-Xexpect-actual-classes",
    "-Xconsistent-data-class-copy-visibility",
)

private const val ktlintCliVersion = "1.8.0"

class FlareModuleSpec internal constructor(
    private val kotlin: KotlinMultiplatformExtension,
) {
    var namespace: String? = null

    private val configuredPlatforms = linkedSetOf<FlarePlatform>()
    private val kspDependencies = mutableListOf<Any>()

    fun platforms(vararg platforms: FlarePlatform) {
        configuredPlatforms.clear()
        configuredPlatforms.addAll(platforms)
    }

    fun ksp(vararg dependencyNotations: Any) {
        kspDependencies.addAll(dependencyNotations)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)
    internal fun apply() {
        require(configuredPlatforms.isNotEmpty()) {
            "flare { } requires at least one platform."
        }

        kotlin.explicitApi()

        val selectedPlatforms = configuredPlatforms.toCollection(LinkedHashSet())

        kotlin.applyDefaultHierarchyTemplate {
            common {
                group("nonWeb") {
                    group("native") {
                        group("apple") {
                            if (FlarePlatform.IOS in selectedPlatforms) withIos()
                            if (FlarePlatform.MACOS in selectedPlatforms) withMacos()
                        }
                        if (FlarePlatform.LINUX in selectedPlatforms) withLinux()
                    }

                    if (
                        FlarePlatform.ANDROID in selectedPlatforms &&
                        FlarePlatform.JVM in selectedPlatforms
                    ) {
                        group("androidJvm") {
                            withAndroid()
                            withJvm()
                        }
                    } else if (FlarePlatform.ANDROID in selectedPlatforms) {
                        withAndroid()
                    } else if (FlarePlatform.JVM in selectedPlatforms) {
                        withJvm()
                    }
                }
            }
        }

        if (FlarePlatform.ANDROID in selectedPlatforms) {
            if (!kotlin.hasTarget("android")) {
                kotlin.androidTarget()
            }
            kotlin.targets.getByName<KotlinMultiplatformAndroidLibraryTarget>("android") {
                compileSdk {
                    version = release(project.intVersion("compileSdk")) {
                        minorApiLevel = 0
                    }
                }
                this.namespace = this@FlareModuleSpec.namespace
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                this.compilerOptions {
                    this.jvmTarget.set(JvmTarget.fromTarget(project.intVersion("java").toString()))
                }
            }
        }
        if (FlarePlatform.JVM in selectedPlatforms && !kotlin.hasTarget("jvm")) {
            kotlin.jvm()
        }
        if (FlarePlatform.IOS in selectedPlatforms) {
            if (!kotlin.hasTarget("iosArm64")) kotlin.iosArm64()
            if (!kotlin.hasTarget("iosSimulatorArm64")) kotlin.iosSimulatorArm64()
        }
        if (FlarePlatform.MACOS in selectedPlatforms && !kotlin.hasTarget("macosArm64")) {
            kotlin.macosArm64()
        }
        if (FlarePlatform.LINUX in selectedPlatforms && !kotlin.hasTarget("linuxX64")) {
            kotlin.linuxX64()
        }
        if (FlarePlatform.WEB in selectedPlatforms && !kotlin.hasTarget("wasmJs")) {
            kotlin.wasmJs {
                browser()
            }
        }

        kotlin.compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(freeArgs)
            optIn.addAll(commonOptIn)
        }
        kotlin.jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(kotlin.project.intVersion("java")))
        }

        if (kspDependencies.isNotEmpty()) {
            val configurations = linkedSetOf<String>()
            configurations += kotlin.targets.mapNotNull { target ->
                target.name
                    .takeIf { it != "metadata" }
                    ?.let { "ksp${it.replaceFirstChar(Char::titlecase)}" }
            }
            configurations.forEach { configurationName ->
                kspDependencies.forEach { dependencyNotation ->
                    kotlin.project.dependencies.add(configurationName, dependencyNotation)
                }
            }
        }
    }
}

fun KotlinMultiplatformExtension.flare(configure: FlareModuleSpec.() -> Unit) {
    FlareModuleSpec(this).apply(configure).apply()
}

fun Project.flare(configure: FlareAndroidApplicationSpec.() -> Unit) {
    FlareAndroidApplicationSpec(this).apply(configure).apply()
}

private fun Project.intVersion(name: String): Int {
    return versionCatalog().findVersion(name).get().requiredVersion.toInt()
}

private fun Project.versionCatalog() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun KotlinMultiplatformExtension.hasTarget(name: String): Boolean {
    return targets.names.contains(name)
}
