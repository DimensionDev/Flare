package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.data.repository.PluginAuthoring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class PluginAuthoringToolsTest {
    private val authoring = TestAuthoring()
    private val context = AgentPluginContext("plugin-a", "run-a", authoring)

    @Test
    fun pluginToolsHaveNoAccountOrActivationTools() {
        assertEquals(
            setOf("read_plugin_files", "write_plugin_files", "read_plugin_sdk", "read_plugin_documentation"),
            pluginAuthoringTools(context)
                .toolRegistry.tools
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun writesAreScopedToTheHostAssignedPluginAndReturnValidationFeedback() =
        runTest {
            val sources = mapOf("plugin.js" to "invalid code")
            val tool = WritePluginFilesTool(context)
            assertEquals(
                "Validation failed: syntax error",
                tool.execute(
                    WritePluginFilesTool.Args(
                        sources.map { (path, content) ->
                            WritePluginFilesTool.SourceFile(path, content)
                        },
                    ),
                ),
            )
            assertEquals(Triple("plugin-a", "run-a", sources), authoring.written)
            assertEquals("{}", ReadPluginFilesTool(context).execute(ReadPluginFilesTool.Args()))
        }

    @Test
    fun failuresAreBoundedAndCancellationIsNotTreatedAsModelFeedback() =
        runTest {
            authoring.failure = IllegalStateException("x".repeat(1000))
            val tool = WritePluginFilesTool(context)
            assertTrue(tool.execute(WritePluginFilesTool.Args(emptyList())).length < 600)
            authoring.failure = CancellationException("stopped")
            assertFailsWith<CancellationException> { tool.execute(WritePluginFilesTool.Args(emptyList())) }
        }

    @Test
    fun repeatedFilePathsDoNotReachTheBackend() =
        runTest {
            val file = WritePluginFilesTool.SourceFile("plugin.js", "code")
            val result = WritePluginFilesTool(context).execute(WritePluginFilesTool.Args(listOf(file, file)))
            assertTrue(result.startsWith("Plugin operation failed"))
            assertEquals(null, authoring.written)
        }

    @Test
    fun sdkToolBoundsReadsAndRejectsInvalidPagination() =
        runTest {
            val tool = ReadPluginSdkTool(context)
            assertEquals("second", tool.execute(ReadPluginSdkTool.Args("api", 2, 1)))
            assertTrue(tool.execute(ReadPluginSdkTool.Args("api", 0, 900)).startsWith("Plugin operation failed"))
        }
}

private class TestAuthoring : PluginAuthoring {
    override val plugins = MutableStateFlow(emptyList<AuthoredPlugin>())
    var written: Triple<String, String, Map<String, String>>? = null
    var failure: Exception? = null

    override suspend fun write(
        pluginId: String,
        runId: String,
        files: Map<String, String>,
    ): String {
        failure?.let { throw it }
        written = Triple(pluginId, runId, files)
        return "Validation failed: syntax error"
    }

    override suspend fun read(
        pluginId: String,
        runId: String,
    ) = emptyMap<String, String>()

    override suspend fun documentation(path: String) = "first\nsecond\nthird"

    override suspend fun fetchDocumentation(url: String) = "documentation"

    override suspend fun create(): AuthoredPlugin = error("not exposed to the model")

    override suspend fun begin(pluginId: String): String = error("not exposed to the model")

    override suspend fun finish(
        pluginId: String,
        runId: String,
        apply: Boolean,
    ) = error("not exposed to the model")

    override suspend fun approve(
        pluginId: String,
        revision: Long,
    ) = error("not exposed to the model")

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ) = error("not exposed to the model")

    override suspend fun delete(pluginId: String) = error("not exposed to the model")

    override suspend fun stop(pluginId: String) = error("not exposed to the model")
}
