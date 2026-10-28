package dev.dimension.flare.data.repository

import dev.dimension.flare.data.model.PluginPermission
import kotlinx.coroutines.flow.StateFlow

/** App-owned plugin files; the agent never receives filesystem or account access. */
public interface PluginAuthoring {
    public val plugins: StateFlow<List<AuthoredPlugin>>

    public suspend fun create(): AuthoredPlugin

    public suspend fun begin(pluginId: String): String

    public suspend fun read(
        pluginId: String,
        runId: String,
    ): Map<String, String>

    public suspend fun write(
        pluginId: String,
        runId: String,
        files: Map<String, String>,
    ): String

    public suspend fun finish(
        pluginId: String,
        runId: String,
        apply: Boolean,
    )

    public suspend fun documentation(path: String): String

    public suspend fun fetchDocumentation(url: String): String

    public suspend fun approve(
        pluginId: String,
        revision: Long,
    )

    public suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    )

    public suspend fun delete(pluginId: String)

    public suspend fun stop(pluginId: String)
}

public data class AuthoredPlugin(
    val id: String,
    val name: String,
    val phase: PluginAuthoringPhase = PluginAuthoringPhase.Idle,
    val revision: Long = 0,
    val enabled: Boolean = true,
    val running: Boolean = false,
    val error: String? = null,
    val permissions: List<PluginPermission> = emptyList(),
    val credentialsChanged: Boolean = false,
) {
    public val conversationId: String get() = "$PLUGIN_CONVERSATION_PREFIX$id"
}

public enum class PluginAuthoringPhase {
    Idle,
    Generating,
    Validating,
    AwaitingApproval,
    WaitingForOperations,
    Applied,
    Failed,
}

private const val PLUGIN_CONVERSATION_PREFIX = "plugin-authoring:"

public fun authoredPluginId(conversationId: String): String? =
    conversationId.takeIf { it.startsWith(PLUGIN_CONVERSATION_PREFIX) }?.removePrefix(PLUGIN_CONVERSATION_PREFIX)
