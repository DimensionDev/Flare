import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import java.util.Base64

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
}

val bundledSdkDirectory = layout.buildDirectory.dir("generated/pluginSdk")
val generatePluginSdk by tasks.registering {
    val sdk = layout.projectDirectory.dir("sdk/skill/flare-plugin")
    val sources = fileTree(sdk) { include("*.md", "*.d.ts", "references/*.md", "examples/pixelfed/plugin.js") }
    val icon = sdk.file("assets/github-template/assets/icon.png")
    inputs.files(sources, icon)
    outputs.dir(bundledSdkDirectory)
    doLast {
        fun literal(value: String): String =
            value.chunked(12000).joinToString(prefix = "listOf(", postfix = ").joinToString(\"\")") { chunk ->
                "\"" +
                    chunk
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("$", "\\$")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t") + "\""
            }
        val output = bundledSdkDirectory.get().file("dev/dimension/flare/feature/plugin/authoring/BundledPluginSdk.kt").asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("package dev.dimension.flare.feature.plugin.authoring")
                appendLine("internal object BundledPluginSdk {")
                appendLine("val documents: Map<String, String> = mapOf(")
                sources.files.sortedBy { it.path }.forEach { file ->
                    appendLine("${literal(file.relativeTo(sdk.asFile).invariantSeparatorsPath)} to ${literal(file.readText())},")
                }
                appendLine(")")
                appendLine("val icon: String = ${literal(Base64.getEncoder().encodeToString(icon.asFile.readBytes()))}")
                appendLine("}")
            },
        )
    }
}

tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach {
    dependsOn(generatePluginSdk)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val livePhase = providers.gradleProperty("flare.plugin.live.phase")
    listOf("phase", "repository", "root").forEach { key ->
        providers.gradleProperty("flare.plugin.live.$key").orNull?.let { systemProperty("flare.plugin.live.$key", it) }
    }
    if (livePhase.isPresent) {
        systemProperty("java.net.useSystemProxies", "true")
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Live GitHub verification must perform real requests") { true }
        testLogging.showStandardStreams = true
    }
}

kotlin {
    flare {
        namespace = "dev.dimension.flare.feature.plugin"
        platforms(
            FlarePlatform.ANDROID,
            FlarePlatform.JVM,
            FlarePlatform.IOS,
            FlarePlatform.MACOS,
        )
    }

    android {
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(bundledSdkDirectory)
            dependencies {
                api(projects.shared)
                api(libs.okio)
                implementation(projects.feature.loginApi)
                implementation(libs.quickjs.kt)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.immutable)
                implementation(libs.ksoup)
                implementation(libs.ktor.client.core)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }
        val androidJvmMain by getting {
            dependencies {
                implementation(projects.composeUi)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.ui)
                implementation(libs.material3)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.datastore.core)
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
