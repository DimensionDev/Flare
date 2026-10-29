package dev.dimension.flare.data.repository

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** App-owned plugin files; the agent never receives filesystem or account access. */
public interface PluginAuthoring {
    public val plugins: StateFlow<List<AuthoredPlugin>>

    /** Reusing a request key returns the same plugin, including after a restart. */
    public suspend fun create(requestKey: String): AuthoredPlugin

    public suspend fun read(pluginId: String): PluginSource

    public suspend fun write(
        pluginId: String,
        expectedRevision: Long,
        files: Map<String, String>,
        iconUrl: String? = null,
    ): AuthoredPlugin

    public suspend fun documentation(path: String): String

    public suspend fun fetchDocumentation(url: String): String

    public suspend fun delete(pluginId: String)

    public suspend fun cancelUpdate(pluginId: String)
}

@Serializable
public data class PluginSource(
    val plugin: AuthoredPlugin,
    val files: Map<String, String>,
    val iconUrl: String? = null,
)

@Serializable
public data class AuthoredPlugin(
    val id: String,
    val name: String,
    val phase: PluginAuthoringPhase = PluginAuthoringPhase.Idle,
    val revision: Long = 0,
    val running: Boolean = false,
    val error: String? = null,
    val platformId: String = authoredPluginPlatformId(id),
) {
    public val canCancelUpdate: Boolean
        get() = phase == PluginAuthoringPhase.Validating || phase == PluginAuthoringPhase.WaitingForOperations
}

public fun authoredPluginPlatformId(id: String): String = id.removePrefix("local.").replace('.', '_')

@Serializable
public enum class PluginAuthoringPhase {
    Idle,
    Validating,
    WaitingForOperations,
    Applied,
    Failed,
}
