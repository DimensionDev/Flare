package dev.dimension.flare.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.withAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class FlareKmpPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        project.pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        val configuredPlatforms = linkedSetOf<FlarePlatform>()
        val extension = FlareExtension(project) { namespace, requestedPlatforms ->
            val effectivePlatforms = project.effectivePlatforms(requestedPlatforms)
            val newPlatforms = effectivePlatforms - configuredPlatforms
            if (newPlatforms.isNotEmpty()) {
                configuredPlatforms += newPlatforms
                project.configureKmpPlatforms(namespace, newPlatforms)
            }
        }
        project.extensions.add("flare", extension)

        project.configureKotlinDefaults()
        project.configureKtlintDefaults()
    }
}

private fun Project.configureKotlinDefaults() {
    val configuredFreeCompilerArgs = providers.gradleProperty("flare.kotlin.freeCompilerArgs")
        .map { it.toCsvList() }
        .orElse(emptyList())
    val configuredOptIns = providers.gradleProperty("flare.kotlin.optIns")
        .map { it.toCsvList() }
        .orElse(emptyList())

    extensions.configure<KotlinMultiplatformExtension> {
        explicitApi()
        compilerOptions {
            allWarningsAsErrors.set(true)
            freeCompilerArgs.addAll(configuredFreeCompilerArgs.get())
            optIn.addAll(configuredOptIns.get())
        }
        jvmToolchain(flareVersionInt("java"))
    }
}

private fun Project.configureKtlintDefaults() {
    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            version.set(flareVersion("ktlint"))
            filter {
                exclude { element -> element.file.path.contains("build", ignoreCase = true) }
            }
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
private fun Project.configureKmpPlatforms(namespace: String, platforms: Set<FlarePlatform>) {
    extensions.configure<KotlinMultiplatformExtension> {
        applyDefaultHierarchyTemplate {
            withCompilations { true }
            common {
                group("nonWeb") {
                    group("androidJvm") {
                        withJvm()
                        withAndroid()
                    }
                    withNative()
                }
            }
        }

        if (FlarePlatform.Android in platforms) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                compileSdk {
                    version = release(flareVersionInt("compileSdk")) {
                        this.minorApiLevel = 0
                    }
                }
                minSdk {
                    version = release(flareVersionInt("minSdk"))
                }
                this.namespace = namespace
            }
        }

        if (FlarePlatform.Desktop in platforms) {
            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(flareVersion("java")))
                }
            }
        }

        if (FlarePlatform.Ios in platforms) {
            iosArm64()
            iosSimulatorArm64()
        }

        if (FlarePlatform.WasmJs in platforms) {
            wasmJs {
                browser()
            }
        }

        if (FlarePlatform.Macos in platforms) {
            macosArm64()
        }

        if (FlarePlatform.Linux in platforms) {
            linuxX64()
        }

        if (FlarePlatform.Windows in platforms) {
            mingwX64()
        }
    }
}

private fun Project.effectivePlatforms(platforms: Set<FlarePlatform>): Set<FlarePlatform> {
    if (currentHostSupportsKotlinNative()) {
        return platforms
    }

    val nativePlatforms = platforms.filter { it.isNative }
    if (nativePlatforms.isNotEmpty()) {
        logger.lifecycle(
            "Skipping Kotlin/Native platforms ${nativePlatforms.joinToString()} in $path " +
                "because the current host does not support Kotlin/Native.",
        )
    }
    return platforms.filterNotTo(linkedSetOf()) { it.isNative }
}

private val FlarePlatform.isNative: Boolean
    get() = when (this) {
        FlarePlatform.Ios,
        FlarePlatform.Macos,
        FlarePlatform.Linux,
        FlarePlatform.Windows -> true

        FlarePlatform.Android,
        FlarePlatform.Desktop,
        FlarePlatform.WasmJs -> false
    }

private fun currentHostSupportsKotlinNative(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isX64 = arch == "x86_64" || arch == "amd64"
    val isArm64 = arch == "aarch64" || arch == "arm64"

    return when {
        os.contains("mac") -> isX64 || isArm64
        os.contains("linux") -> isX64
        os.contains("windows") -> isX64
        else -> false
    }
}

private fun Project.configureAndroidLibraryDefaults(namespace: String) {
    extensions.configure<KotlinMultiplatformExtension> {
        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            compileSdk {
                version = release(flareVersionInt("compileSdk"))
            }
            minSdk {
                version = release(flareVersionInt("minSdk"))
            }
            this.namespace = namespace
        }
    }
}

private fun String.toCsvList(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
