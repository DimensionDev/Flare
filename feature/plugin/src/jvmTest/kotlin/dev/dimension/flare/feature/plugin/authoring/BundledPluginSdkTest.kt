package dev.dimension.flare.feature.plugin.authoring

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledPluginSdkTest {
    private val sdk =
        sequenceOf(Path.of("sdk/skill/flare-plugin"), Path.of("feature/plugin/sdk/skill/flare-plugin"))
            .first { Files.isRegularFile(it.resolve("SKILL.md")) }

    private fun sdkText(path: String): String = sdk.resolve(path).readText().replace("\r\n", "\n")

    @Test
    fun catalogExposesTheLocalWorkflowAndSmallTemplate() {
        assertEquals(BundledPluginSdk.paths.size, BundledPluginSdk.paths.toSet().size)
        val guide = assertNotNull(BundledPluginSdk.read("README.md"))
        assertTrue(guide.contains("write_plugin_files"))
        assertTrue(guide.contains("expectedRevision"))
        assertTrue(guide.contains("complete file set"))
        assertTrue(guide.contains("Flare supplies the icon asset"))
        assertTrue("assets/github-template/manifest.json" in BundledPluginSdk.paths)
        assertTrue("assets/github-template/plugin.js" in BundledPluginSdk.paths)
        BundledPluginSdk.paths.forEach { assertTrue(assertNotNull(BundledPluginSdk.read(it), it).isNotBlank(), it) }
    }

    @Test
    fun externalSkillAndLargeExamplesRemainExternal() {
        listOf("SKILL.md", "references/publishing.md", "references/testing.md", "examples/pixelfed/plugin.js", "pack.mjs").forEach {
            assertTrue(Files.isRegularFile(sdk.resolve(it)), it)
            assertFalse(it in BundledPluginSdk.paths, it)
            assertNull(BundledPluginSdk.read(it), it)
        }
        listOf("../README.md", "../../credentials", "/ABI.md", "references/../ABI.md", "https://example.com/ABI.md").forEach {
            assertNull(BundledPluginSdk.read(it), it)
        }
    }

    @Test
    fun sharedTypesContractsAndTemplatesAreCopiedWithoutDrift() {
        listOf(
            "flare-plugin.d.ts",
            "manifest.schema.json",
            "references/wire-data.md",
            "assets/github-template/manifest.json",
            "assets/github-template/plugin.js",
        ).forEach { assertEquals(sdkText(it), BundledPluginSdk.read(it), it) }
        listOf("references/manifest.md", "references/capabilities.md").forEach {
            assertEquals(
                sdkText(it).replace("publishing.md#updates-and-compatibility", "compatibility.md#updates-and-compatibility"),
                BundledPluginSdk.read(it),
                it,
            )
        }
    }

    @Test
    fun excerptsKeepRuntimeAndCompatibilityContractsWithoutPublicationInstructions() {
        val abi = assertNotNull(BundledPluginSdk.read("ABI.md"))
        assertTrue(abi.contains("## Registration"))
        assertTrue(abi.contains("## Host API"))
        assertTrue(abi.contains("## Login"))
        assertFalse(abi.contains("node pack.mjs"))
        assertFalse(abi.contains("## GitHub installation"))
        assertFalse(abi.contains("effect after restart"))
        assertFalse(abi.contains("requires confirmation"))
        assertTrue(abi.contains("automatically approve this permission after validation"))
        val runtime = assertNotNull(BundledPluginSdk.read("references/runtime-http.md"))
        assertTrue(runtime.contains("automatically approve this permission after validation"))
        assertFalse(runtime.contains("running package after restart"))
        assertEquals(
            sdkText("references/runtime-http.md").substringAfter("## HTTP request and response"),
            runtime.substringAfter("## HTTP request and response"),
        )
        val compatibility = assertNotNull(BundledPluginSdk.read("references/compatibility.md"))
        val rules =
            sdkText("references/publishing.md")
                .substringAfter("| Change | Author consequence |")
                .substringBefore("## Optional local installation")
                .trim()
        assertTrue(compatibility.endsWith("$rules\n"))
        assertTrue(compatibility.contains("credentialSchemaVersion"))
        assertTrue(compatibility.contains("timelineSchemaVersion"))
        assertFalse(compatibility.contains("publish a new stable tag"))
    }

    @Test
    fun externalAndBundledGuidesDescribeImmediateActivation() {
        val troubleshooting = sdkText("references/troubleshooting.md")
        assertEquals(troubleshooting, BundledPluginSdk.read("references/troubleshooting.md"))
        listOf(troubleshooting, sdkText("references/publishing.md")).forEach {
            assertTrue(it.contains("take effect immediately"))
            assertFalse(it.contains("Restart Flare"))
        }
    }

    @Test
    fun everyRelativeMarkdownAndTypeReferenceResolvesLocally() {
        BundledPluginSdk.paths.forEach { path ->
            val text = assertNotNull(BundledPluginSdk.read(path))
            val links =
                Regex("\\[[^]]*]\\(([^)]+)\\)").findAll(text).map { it.groupValues[1] } +
                    Regex("@see (\\S+)").findAll(text).map { it.groupValues[1] }
            links.forEach { link ->
                val relative = link.substringBefore('#')
                val target =
                    if (relative.isEmpty()) {
                        path
                    } else {
                        (Path.of(path).parent ?: Path.of(""))
                            .resolve(relative)
                            .normalize()
                            .toString()
                            .replace('\\', '/')
                    }
                assertTrue(target in BundledPluginSdk.paths, "$path -> $link")
                if ('#' in link) {
                    val headings =
                        assertNotNull(BundledPluginSdk.read(target))
                            .lineSequence()
                            .filter { it.startsWith("#") }
                            .map {
                                it
                                    .trimStart('#', ' ')
                                    .lowercase()
                                    .replace(Regex("[^a-z0-9 -]"), "")
                                    .replace(' ', '-')
                            }.toSet()
                    assertTrue(link.substringAfter('#') in headings, "$path -> $link")
                }
            }
        }
    }

    @Test
    fun defaultIconRemainsTheSdkTemplateIcon() {
        assertContentEquals(
            Files.readAllBytes(sdk.resolve("assets/github-template/assets/icon.png")),
            Base64.getDecoder().decode(DEFAULT_PLUGIN_ICON),
        )
    }
}
