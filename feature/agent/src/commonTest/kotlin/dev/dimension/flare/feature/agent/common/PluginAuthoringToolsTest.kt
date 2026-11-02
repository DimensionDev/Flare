package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import dev.dimension.flare.data.repository.PluginAuthoringPhase
import dev.dimension.flare.data.repository.PluginSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PluginAuthoringToolsTest {
    private val authoring = TestAuthoring()

    @Test
    fun writesForwardOptionalIconUrlsAndOmissionKeepsTheDefaultContract() =
        runTest {
            val tool = WritePluginFilesTool(authoring)
            tool.execute(WritePluginFilesTool.Args("plugin-a", 0, emptyList(), "https://example.com/favicon.ico"))
            assertEquals("https://example.com/favicon.ico", authoring.iconUrl)
            tool.execute(WritePluginFilesTool.Args("plugin-a", 1, emptyList()))
            assertNull(authoring.iconUrl)
        }

    @Test
    fun toolsManageLocalPluginsWithoutExposingPermissionApproval() {
        assertEquals(
            setOf(
                "list_plugins",
                "create_plugin",
                "read_plugin_files",
                "write_plugin_files",
                "delete_plugin",
                "read_plugin_sdk",
                "read_plugin_documentation",
            ),
            pluginAuthoringTools(authoring, AgentToolInputRequestStore()).map { it.name }.toSet(),
        )
        assertTrue(authoring.plugins.value.isEmpty())
    }

    @Test
    fun creationReturnsTheManifestIdentityAndInitialRevisionExplicitly() =
        runTest {
            val response = CreatePluginTool(authoring).execute(CreatePluginTool.Args("unique-request-key"))
            val fields = JSON.parseToJsonElement(response).jsonObject
            assertEquals("plugin-a", fields["platformId"]?.jsonPrimitive?.content)
            assertEquals("0", fields["revision"]?.jsonPrimitive?.content)
            assertNull(fields["enabled"])
        }

    @Test
    fun independentToolInstancesReadAndUpdateTheSamePluginByIdAndRevision() =
        runTest {
            val created =
                JSON.decodeFromString<AuthoredPlugin>(
                    CreatePluginTool(authoring).execute(CreatePluginTool.Args("unique-request-key")),
                )
            assertEquals("unique-request-key", authoring.createdKey)
            val sources = mapOf("plugin.js" to "invalid code")
            val result =
                WritePluginFilesTool(authoring).execute(
                    WritePluginFilesTool.Args(
                        created.id,
                        7,
                        sources.map { (path, content) ->
                            WritePluginFilesTool.SourceFile(path, content)
                        },
                    ),
                )
            assertEquals(Triple(created.id, 7L, sources), authoring.written)
            assertEquals(PluginAuthoringPhase.Validating, JSON.decodeFromString<AuthoredPlugin>(result).phase)
            val read = JSON.decodeFromString<PluginSource>(ReadPluginFilesTool(authoring).execute(ReadPluginFilesTool.Args(created.id)))
            assertEquals(sources, read.files)
            assertEquals(8, read.plugin.revision)
            assertEquals(
                listOf(read.plugin),
                JSON.decodeFromString<List<AuthoredPlugin>>(ListPluginsTool(authoring).execute(ListPluginsTool.Args())),
            )
        }

    @Test
    fun failuresAreBoundedAndCancellationIsNotTreatedAsModelFeedback() =
        runTest {
            authoring.failure = IllegalStateException("x".repeat(1000))
            val tool = WritePluginFilesTool(authoring)
            val args = WritePluginFilesTool.Args("plugin-a", 0, emptyList())
            assertTrue(tool.execute(args).length < 600)
            authoring.failure = CancellationException("stopped")
            assertFailsWith<CancellationException> { tool.execute(args) }
        }

    @Test
    fun repeatedFilePathsDoNotReachTheBackend() =
        runTest {
            val file = WritePluginFilesTool.SourceFile("plugin.js", "code")
            val result = WritePluginFilesTool(authoring).execute(WritePluginFilesTool.Args("plugin-a", 0, listOf(file, file)))
            assertTrue(result.startsWith("Plugin operation failed"))
            assertEquals(null, authoring.written)
        }

    @Test
    fun deletionRequiresAHostGrantForTheExactPluginRevisionAndConsumesItOnce() =
        runTest {
            val plugin = authoring.create("unique-request-key")
            val requests = AgentToolInputRequestStore()
            val tool = DeletePluginTool(authoring, requests)
            tool.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertEquals(listOf(plugin), authoring.plugins.value)
            val key =
                requests
                    .snapshot()!!
                    .options
                    .single()
                    .confirmationKey!!
            val other = DeletePluginTool(authoring, AgentToolInputRequestStore("delete_plugin:other:0"))
            other.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertEquals(listOf(plugin), authoring.plugins.value)
            val approved = DeletePluginTool(authoring, AgentToolInputRequestStore(key))
            approved.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertTrue(authoring.plugins.value.isEmpty())
            authoring.create("unique-request-key")
            approved.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertEquals(listOf(plugin), authoring.plugins.value)
        }

    @Test
    fun staleDeletionApprovalDoesNotDeleteAnUpdatedPlugin() =
        runTest {
            val plugin = authoring.create("unique-request-key")
            val approved = DeletePluginTool(authoring, AgentToolInputRequestStore("delete_plugin:${plugin.id}:0"))
            authoring.write(plugin.id, 0, emptyMap())
            approved.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertEquals(1, authoring.plugins.value.size)
        }

    @Test
    fun sdkToolBoundsReadsAndRejectsInvalidPagination() =
        runTest {
            val tool = ReadPluginSdkTool(authoring)
            assertEquals("second", tool.execute(ReadPluginSdkTool.Args("api", 2, 1)))
            assertEquals("api", authoring.documentationPath)
            authoring.documentationPath = null
            assertTrue(tool.execute(ReadPluginSdkTool.Args("api", 0, 900)).startsWith("Plugin operation failed"))
            assertNull(authoring.documentationPath)
        }

    @Test
    fun sdkReadsKeepTheSelectedDocumentAndOutputBound() =
        runTest {
            val tool = ReadPluginSdkTool(authoring)
            authoring.document = "README.md\nABI.md"
            assertEquals(authoring.document, tool.execute(ReadPluginSdkTool.Args()))
            assertEquals("", authoring.documentationPath)
            authoring.document = "x".repeat(50_000)
            assertEquals(48_000, tool.execute(ReadPluginSdkTool.Args("README.md")).length)
            assertEquals("README.md", authoring.documentationPath)
            assertEquals("", tool.execute(ReadPluginSdkTool.Args("README.md", 2, 1)))
        }
}

internal class TestAuthoring : PluginAuthoring {
    override val plugins = MutableStateFlow(emptyList<AuthoredPlugin>())
    var written: Triple<String, Long, Map<String, String>>? = null
    var iconUrl: String? = null
    var createdKey: String? = null
    var failure: Exception? = null
    var documentationPath: String? = null
    var document = "first\nsecond\nthird"

    override suspend fun write(
        pluginId: String,
        expectedRevision: Long,
        files: Map<String, String>,
        iconUrl: String?,
    ): AuthoredPlugin {
        failure?.let { throw it }
        written = Triple(pluginId, expectedRevision, files)
        this.iconUrl = iconUrl
        return AuthoredPlugin(pluginId, "Test", revision = expectedRevision + 1, phase = PluginAuthoringPhase.Validating).also {
            plugins.value = listOf(it)
        }
    }

    override suspend fun read(pluginId: String) = PluginSource(plugins.value.single { it.id == pluginId }, written?.third.orEmpty())

    override suspend fun documentation(path: String): String {
        documentationPath = path
        return document
    }

    override suspend fun fetchDocumentation(url: String) = "documentation"

    override suspend fun create(requestKey: String): AuthoredPlugin {
        createdKey = requestKey
        return AuthoredPlugin("plugin-a", "Test").also { plugins.value = listOf(it) }
    }

    override suspend fun delete(
        pluginId: String,
        expectedRevision: Long?,
    ) {
        check(expectedRevision == null || plugins.value.single { it.id == pluginId }.revision == expectedRevision)
        plugins.value = plugins.value.filterNot { it.id == pluginId }
    }

    override suspend fun cancelUpdate(pluginId: String) = error("Not exposed to the model")
}
