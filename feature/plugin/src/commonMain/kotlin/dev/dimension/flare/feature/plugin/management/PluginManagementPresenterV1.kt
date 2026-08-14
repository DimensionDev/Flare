package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.installer.PluginSensitivePermissionV1
import dev.dimension.flare.feature.plugin.installer.PreparedPluginInstallV1
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateIssueV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeIssueV1
import dev.dimension.flare.ui.model.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath
import okio.Source
import kotlin.native.HiddenFromObjC

public data class PluginManagementStateV1(
    val plugins: List<PluginManagementItemV1> = emptyList(),
    val issues: List<PluginStateIssueV1> = emptyList(),
    val runtimeIssues: List<PluginRuntimeIssueV1> = emptyList(),
    val pendingInstall: PluginInstallReviewV1? = null,
    val requiresRestart: Boolean = false,
    val canRebuildIndex: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

public data class PluginManagementItemV1(
    val pluginId: String,
    val platformId: String,
    val name: String,
    val nameText: UiText,
    val version: String,
    val enabled: Boolean,
    val running: Boolean,
    val pendingRestart: Boolean,
    val source: PluginInstallSourceV1,
    val capabilities: Set<String>,
    val iconPath: String,
)

public data class PluginInstallReviewV1(
    val pluginId: String,
    val platformId: String,
    val name: String,
    val version: String,
    val existingVersion: String?,
    val capabilities: List<String>,
    val permissions: List<PluginSensitivePermissionV1>,
    val warnings: List<PluginInstallWarningV1>,
)

@HiddenFromObjC
public class PluginManagementPresenterV1(
    private val subsystem: PluginSubsystemV1,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(snapshot())
    public val state: StateFlow<PluginManagementStateV1> = mutableState.asStateFlow()

    private var preview: PreparedPluginInstallV1? = null
    private val actionMutex = Mutex()

    init {
        combine(
            subsystem.stateStore.desired,
            subsystem.sourceIssues,
            subsystem.runtimePool.issues,
        ) { _, _, _ -> snapshot() }
            .onEach { snapshot ->
                val current = mutableState.value
                mutableState.value =
                    snapshot.copy(
                        pendingInstall = current.pendingInstall,
                        busy = current.busy,
                        error = current.error,
                    )
            }.launchIn(scope)
    }

    @HiddenFromObjC
    public suspend fun inspect(source: Source): PluginInstallReviewV1 =
        runAction {
            clearPreview()
            subsystem.installer
                .inspect(source)
                .also { prepared ->
                    preview = prepared
                    mutableState.value = snapshot().copy(pendingInstall = prepared.toReview())
                }.toReview()
        }

    public suspend fun inspect(path: String): PluginInstallReviewV1 =
        runAction {
            clearPreview()
            subsystem.installer
                .inspect(path.toPath())
                .also { prepared ->
                    preview = prepared
                    mutableState.value = snapshot().copy(pendingInstall = prepared.toReview())
                }.toReview()
        }

    public suspend fun confirmInstall() {
        runAction {
            val prepared = requireNotNull(preview) { "No plugin installation is pending" }
            subsystem.installer.commit(prepared, confirmed = true)
            preview = null
            mutableState.value = snapshot()
        }
    }

    public suspend fun cancelInstall() {
        val prepared = preview ?: return
        runAction {
            subsystem.installer.discard(prepared)
            preview = null
            mutableState.value = snapshot()
        }
    }

    public suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ) {
        runAction {
            subsystem.stateStore.setEnabled(pluginId, enabled)
            mutableState.value = snapshot()
        }
    }

    public suspend fun uninstall(pluginId: String) {
        runAction {
            subsystem.stateStore.uninstall(pluginId)
            mutableState.value = snapshot()
        }
    }

    public suspend fun retryRuntime(pluginId: String) {
        runAction {
            subsystem.runtimePool.retry(pluginId)
            mutableState.value = snapshot()
        }
    }

    public suspend fun cleanup() {
        runAction { subsystem.installer.cleanup() }
    }

    public suspend fun rebuildIndex() {
        runAction {
            subsystem.installer.rebuildIndex()
            mutableState.value = snapshot()
        }
    }

    public fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun snapshot(): PluginManagementStateV1 {
        val desired = subsystem.stateStore.desired.value
        val running = subsystem.stateStore.running.plugins
        return PluginManagementStateV1(
            plugins =
                desired.plugins.values.sortedBy(InstalledPluginV1::pluginId).map { installed ->
                    installed.toItem(
                        running = running[installed.pluginId]?.installed,
                        pendingRestart = subsystem.stateStore.requiresRestart(installed.pluginId),
                        iconPath =
                            subsystem.stateStore.paths
                                .iconPath(installed.packageHash)
                                .toString(),
                    )
                },
            issues = subsystem.sourceIssues.value,
            runtimeIssues =
                subsystem.runtimePool.issues.value.values
                    .sortedBy(PluginRuntimeIssueV1::pluginId),
            requiresRestart = subsystem.stateStore.requiresRestart,
            canRebuildIndex = !desired.indexHealthy,
        )
    }

    private fun clearPreview() {
        preview = null
        mutableState.value = mutableState.value.copy(pendingInstall = null)
    }

    private suspend fun <T> runAction(block: suspend () -> T): T =
        actionMutex.withLock {
            mutableState.value = mutableState.value.copy(busy = true, error = null)
            try {
                block()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.value = snapshot().copy(error = error.message ?: "Plugin operation failed")
                throw error
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
}

private fun InstalledPluginV1.toItem(
    running: InstalledPluginV1?,
    pendingRestart: Boolean,
    iconPath: String,
): PluginManagementItemV1 =
    PluginManagementItemV1(
        pluginId = pluginId,
        platformId = manifest.platform.id,
        name = manifest.name.fallbackValue(),
        nameText = manifest.name.toUiText(pluginId),
        version = version,
        enabled = enabled,
        running = running != null,
        pendingRestart = pendingRestart,
        source = source,
        capabilities = manifest.platform.capabilities.keys,
        iconPath = iconPath,
    )

private fun PluginTextV1.fallbackValue(): String =
    when (this) {
        is PluginTextV1.Literal -> value
        is PluginTextV1.Localized -> fallback
    }

private fun PreparedPluginInstallV1.toReview(): PluginInstallReviewV1 =
    PluginInstallReviewV1(
        pluginId = pluginId,
        platformId = platformId,
        name = nameFallback,
        version = version,
        existingVersion = existingVersion,
        capabilities =
            capabilities.entries
                .sortedBy { it.key }
                .map { (capability, operations) -> "$capability: ${operations.sorted().joinToString()}" },
        permissions = permissions.sortedWith(compareBy({ it.type.name }, { it.origin }, { it.cookieName.orEmpty() })),
        warnings = warnings,
    )
