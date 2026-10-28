package dev.dimension.flare.feature.plugin.lifecycle

import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import dev.dimension.flare.feature.plugin.runtime.PluginSessionV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class PluginStateIndexV1(
    val schemaVersion: Int = SCHEMA_VERSION,
    val plugins: List<InstalledPluginV1> = emptyList(),
) {
    public companion object {
        public const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
public data class InstalledPluginV1(
    val pluginId: String,
    val version: String,
    val packageHash: String,
    val packageSize: Long,
    val iconSize: Long,
    val source: PluginInstallSourceV1 = PluginInstallSourceV1.Local,
    val enabled: Boolean = true,
    val manifest: PluginManifestV1,
    val catalogs: Map<String, Map<String, String>> = emptyMap(),
    val runtimeGeneration: Long = 0,
)

@Serializable
public sealed interface PluginInstallSourceV1 {
    @Serializable
    @SerialName("local")
    public data object Local : PluginInstallSourceV1

    @Serializable
    @SerialName("github")
    public data class GitHub(
        val repository: GitHubPluginRepositoryV1,
        val tag: String,
        val commitSha: String,
    ) : PluginInstallSourceV1
}

public data class RunningPluginV1(
    val installed: InstalledPluginV1,
    val packagePath: String,
    val iconPath: String,
    val developmentSource: String? = null,
    val generation: Long = 0,
) {
    internal val session = PluginSessionV1()
    internal var diagnostic: ((RunningPluginV1, String) -> Unit)? = null
}

public data class PluginRunningSnapshotV1(
    val plugins: Map<String, RunningPluginV1>,
    val referencedPackageHashes: Set<String>,
    val issues: List<PluginStateIssueV1>,
    val indexHealthy: Boolean,
)

public data class PluginDesiredSnapshotV1(
    val plugins: Map<String, InstalledPluginV1>,
    val indexHealthy: Boolean,
)

public data class PluginStateIssueV1(
    val code: String,
    val pluginId: String? = null,
    val message: String,
)

public class PluginIndexCorruptException(
    message: String,
) : IllegalStateException(message)
