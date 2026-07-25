package dev.dimension.flareui.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.withAndroid
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

public enum class FlareUiPlatform {
    ANDROID,
    JVM,
    IOS,
    MACOS,
}

public class FlareUiMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}

public class FlareUiAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
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
            subproject.tasks.matching { task -> task.name.startsWith("runKtlint") }
                .configureEach {
                    dependsOn(subproject.tasks.matching { task -> task.name.startsWith("ksp") })
                }
        }
    }
}

public class FlareUiAndroidApplicationSpec internal constructor(
    private val project: Project,
) {
    public var namespace: String? = null
    public var applicationId: String? = null

    internal fun apply() {
        val androidNamespace =
            requireNotNull(namespace) {
                "flareUiApplication { namespace = ... } is required."
            }

        project.extensions.configure<ApplicationExtension> {
            namespace = androidNamespace
            compileSdk {
                version = release(project.intVersion("compileSdk")) {
                    minorApiLevel = 0
                }
            }
            defaultConfig {
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                applicationId = this@FlareUiAndroidApplicationSpec.applicationId ?: androidNamespace
                targetSdk {
                    version = release(project.intVersion("compileSdk"))
                }
                vectorDrawables {
                    useSupportLibrary = true
                }
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                isCoreLibraryDesugaringEnabled = true
                sourceCompatibility = JavaVersion.toVersion(project.intVersion("java"))
                targetCompatibility = JavaVersion.toVersion(project.intVersion("java"))
            }
            buildFeatures {
                compose = true
                buildConfig = true
            }
            packaging {
                resources {
                    excludes.add("/META-INF/{AL2.0,LGPL2.1}")
                    excludes.add("DebugProbesKt.bin")
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

    public fun platforms(vararg values: FlareUiPlatform) {
        platforms.clear()
        platforms.addAll(values)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    internal fun apply() {
        require(platforms.isNotEmpty()) {
            "flareUi { } requires at least one platform."
        }

        kotlin.explicitApi()

        kotlin.applyDefaultHierarchyTemplate {
            common {
                group("nonWeb") {
                    group("native") {
                        group("apple") {
                            if (FlareUiPlatform.IOS in platforms) withIos()
                            if (FlareUiPlatform.MACOS in platforms) withMacos()
                        }
                    }
                    if (
                        FlareUiPlatform.ANDROID in platforms &&
                        FlareUiPlatform.JVM in platforms
                    ) {
                        group("androidJvm") {
                            withAndroid()
                            withJvm()
                        }
                    } else if (FlareUiPlatform.ANDROID in platforms) {
                        withAndroid()
                    } else if (FlareUiPlatform.JVM in platforms) {
                        withJvm()
                    }
                }
            }
        }

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
        if (FlareUiPlatform.MACOS in platforms) kotlin.macosArm64()

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

public fun KotlinMultiplatformExtension.flareUi(
    configure: FlareUiModuleSpec.() -> Unit,
) {
    FlareUiModuleSpec(this).apply(configure).apply()
}

public fun Project.flareUiApplication(
    configure: FlareUiAndroidApplicationSpec.() -> Unit,
) {
    FlareUiAndroidApplicationSpec(this).apply(configure).apply()
}

private fun Project.intVersion(name: String): Int =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()
