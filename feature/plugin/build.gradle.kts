import dev.dimension.flare.buildlogic.FlarePlatform
import dev.dimension.flare.buildlogic.flare
import org.jetbrains.compose.compose
import java.util.Base64

plugins {
    id("dev.dimension.flare.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
}

val bundledSdkDirectory = layout.buildDirectory.dir("generated/pluginSdk")
val generatePluginSdk by tasks.registering {
    val sdk = layout.projectDirectory.dir("sdk/skill/flare-plugin")
    val sources =
        mapOf(
            "README.md" to layout.projectDirectory.file("agent/README.md"),
            "references/compatibility.md" to sdk.file("references/publishing.md"),
        ) +
            listOf(
                "ABI.md",
                "flare-plugin.d.ts",
                "references/manifest.md",
                "references/capabilities.md",
                "references/runtime-http.md",
                "references/authentication.md",
                "references/wire-data.md",
                "references/recipes.md",
                "references/troubleshooting.md",
                "assets/github-template/manifest.json",
                "assets/github-template/plugin.js",
            ).associateWith(sdk::file)
    val icon = sdk.file("assets/github-template/assets/icon.png")
    inputs.files(sources.values, icon)
    outputs.dir(bundledSdkDirectory)
    doLast {
        fun excerpt(
            text: String,
            start: String,
            end: String,
        ): String {
            val from = text.indexOf(start)
            val until = text.indexOf(end, from + start.length)
            require(from >= 0 && until > from) { "SDK excerpt boundaries are missing: $start / $end" }
            return text.substring(from, until).trimEnd() + "\n"
        }

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
        val documents =
            sources.mapValues { (path, file) ->
                val text = file.asFile.readText().replace("\r\n", "\n")
                // Keep the external SDK intact; only adapt the in-app reference set.
                when (path) {
                    "ABI.md" ->
                        ("# Flare Social Plugin API v1\n\n" + excerpt(text, "## Registration\n", "## Lifecycle and trust\n"))
                            .replace(
                                "Installation review explicitly grants\n" +
                                    "media requests to any website, including CDNs and federated servers; these URLs\n" +
                                    "can transmit the plugin account's data. Adding this permission during an update\nrequires confirmation.",
                                "Local AI plugins automatically approve this permission after validation, including updates.\n" +
                                    "Media requests may reach any website, including CDNs and federated servers; these\n" +
                                    "URLs can transmit the plugin account's data.",
                            ).replace(
                                "Permission removal, disable, and uninstall take effect after restart, like the\n" +
                                    "package change itself; absent plugins cannot restore media.",
                                "Media grants change with the running package; absent plugins cannot restore media.",
                            )
                    "references/compatibility.md" ->
                        "# Compatibility\n\n## Updates and compatibility\n\n" +
                            excerpt(text, "| Change | Author consequence |", "## Optional local installation\n")
                    "references/authentication.md" ->
                        text.replace(
                            "and [Pixelfed](../examples/pixelfed/plugin.js) for complete OAuth.",
                            "and the [OAuth recipe](#oauth) for that login flow.",
                        )
                    "references/runtime-http.md" ->
                        text.replace(
                            "Installation\nreview discloses this broad media access.",
                            "Local AI plugins automatically approve this permission after validation.",
                        ).replace("running package after restart", "running package")
                    "references/recipes.md" ->
                        text.replace("and Pixelfed's complete publish\nimplementation.", "for upload requests.")
                    "references/troubleshooting.md" ->
                        text.replace("activation after restart", "activation").replace(
                            "Restart Flare; Desired State differs from the running package until then",
                            "Check list_plugins for validation errors or pending activation; see [updating](../README.md#updating)",
                        )
                    else -> text
                }.replace("publishing.md#updates-and-compatibility", "compatibility.md#updates-and-compatibility")
            }
        val output = bundledSdkDirectory.get().file("dev/dimension/flare/feature/plugin/authoring/BundledPluginSdk.kt").asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("package dev.dimension.flare.feature.plugin.authoring")
                appendLine("internal const val DEFAULT_PLUGIN_ICON = \"${Base64.getEncoder().encodeToString(icon.asFile.readBytes())}\"")
                appendLine("internal object BundledPluginSdk {")
                appendLine("val paths: List<String> = listOf(${documents.keys.joinToString { "\"$it\"" }})")
                appendLine("fun read(path: String): String? = when (path) {")
                documents.forEach { (path, text) -> appendLine("\"$path\" -> ${literal(text)}") }
                appendLine("else -> null")
                appendLine("}")
                appendLine("}")
            },
        )
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatePluginSdk)
            dependencies {
                api(projects.shared)
                api(libs.okio)
                implementation(projects.feature.loginApi)
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
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.javascriptengine)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.quickjs.kt)
                implementation(compose("org.jetbrains.compose.ui:ui"))
            }
        }
        val jvmTest by getting {
            dependencies {
                runtimeOnly(compose.desktop.currentOs)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.datastore.core)
            }
        }
        val androidDeviceTest by getting {
            dependsOn(commonTest)
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
