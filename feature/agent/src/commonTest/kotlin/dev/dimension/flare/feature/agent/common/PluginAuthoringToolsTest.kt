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
import kotlin.test.assertTrue

internal class PluginAuthoringToolsTest {
    private val authoring = TestAuthoring()

    @Test
    fun toolsManageLocalPluginsWithoutExposingPermissionApproval() {
        assertEquals(
            setOf(
                "list_plugins",
                "create_plugin",
                "read_plugin_files",
                "write_plugin_files",
                "set_plugin_enabled",
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
            assertEquals("true", fields["enabled"]?.jsonPrimitive?.content)
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
    fun deletionUsesTheGenericConfirmationRequest() =
        runTest {
            val plugin = authoring.create("unique-request-key")
            val tool = DeletePluginTool(authoring, AgentToolInputRequestStore())
            assertTrue(tool.execute(DeletePluginTool.Args(plugin.id)).contains("inputRequestId=plugin-delete:${plugin.id}"))
            assertEquals(listOf(plugin), authoring.plugins.value)
            tool.execute(DeletePluginTool.Args(plugin.id, confirmed = true))
            assertTrue(authoring.plugins.value.isEmpty())
        }

    @Test
    fun sdkToolBoundsReadsAndRejectsInvalidPagination() =
        runTest {
            val tool = ReadPluginSdkTool(authoring)
            assertEquals("second", tool.execute(ReadPluginSdkTool.Args("api", 2, 1)))
            assertTrue(tool.execute(ReadPluginSdkTool.Args("api", 0, 900)).startsWith("Plugin operation failed"))
        }
}

internal class TestAuthoring : PluginAuthoring {
    override val plugins = MutableStateFlow(emptyList<AuthoredPlugin>())
    var written: Triple<String, Long, Map<String, String>>? = null
    var createdKey: String? = null
    var failure: Exception? = null

    override suspend fun write(
        pluginId: String,
        expectedRevision: Long,
        files: Map<String, String>,
    ): AuthoredPlugin {
        failure?.let { throw it }
        written = Triple(pluginId, expectedRevision, files)
        return AuthoredPlugin(pluginId, "Test", revision = expectedRevision + 1, phase = PluginAuthoringPhase.Validating).also {
            plugins.value = listOf(it)
        }
    }

    override suspend fun read(pluginId: String) = PluginSource(plugins.value.single { it.id == pluginId }, written?.third.orEmpty())

    override suspend fun documentation(path: String) = "first\nsecond\nthird"

    override suspend fun fetchDocumentation(url: String) = "documentation"

    override suspend fun create(requestKey: String): AuthoredPlugin {
        createdKey = requestKey
        return AuthoredPlugin("plugin-a", "Test").also { plugins.value = listOf(it) }
    }

    override suspend fun approve(
        pluginId: String,
        revision: Long,
    ) = error("Not exposed to the model")

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ) {
        plugins.value = plugins.value.map { if (it.id == pluginId) it.copy(enabled = enabled) else it }
    }

    override suspend fun delete(pluginId: String) {
        plugins.value = plugins.value.filterNot { it.id == pluginId }
    }

    override suspend fun cancelUpdate(pluginId: String) = error("Not exposed to the model")
}
