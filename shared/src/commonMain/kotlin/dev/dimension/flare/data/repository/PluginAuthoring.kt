package dev.dimension.flare.data.repository

import dev.dimension.flare.data.model.PluginPermission
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
    ): AuthoredPlugin

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

    public suspend fun cancelUpdate(pluginId: String)
}

@Serializable
public data class PluginSource(
    val plugin: AuthoredPlugin,
    val files: Map<String, String>,
)

@Serializable
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
    val platformId: String = authoredPluginPlatformId(id),
) {
    public val canSetEnabled: Boolean get() = revision > 0
    public val canApprove: Boolean get() = phase == PluginAuthoringPhase.AwaitingApproval
    public val canCancelUpdate: Boolean
        get() = phase == PluginAuthoringPhase.Validating || canApprove || phase == PluginAuthoringPhase.WaitingForOperations
}

public fun authoredPluginPlatformId(id: String): String = id.removePrefix("local.").replace('.', '_')

@Serializable
public enum class PluginAuthoringPhase {
    Idle,
    Validating,
    AwaitingApproval,
    WaitingForOperations,
    Applied,
    Failed,
}
