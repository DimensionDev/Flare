package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.data.model.PluginPermission
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.AuthoredPlugin
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.development.PluginDevelopmentStateV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginCatalogV1
import dev.dimension.flare.feature.plugin.github.GitHubPluginClient
import dev.dimension.flare.feature.plugin.github.GitHubPluginVersionV1
import dev.dimension.flare.feature.plugin.installer.PluginInstallWarningV1
import dev.dimension.flare.feature.plugin.installer.PreparedPluginInstallV1
import dev.dimension.flare.feature.plugin.installer.compareSemVer
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginRuntimeSwitcherV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateIssueV1
import dev.dimension.flare.feature.plugin.manifest.toUiText
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeIssueV1
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.ui.model.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Source
import kotlin.native.HiddenFromObjC

public data class PluginManagementStateV1(
    val plugins: List<PluginManagementItemV1> = emptyList(),
    val issues: List<PluginStateIssueV1> = emptyList(),
    val runtimeIssues: List<PluginRuntimeIssueV1> = emptyList(),
    val pendingInstall: PluginInstallReviewV1? = null,
    val canRebuildIndex: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val gitHubInstall: PluginGitHubInstallStateV1? = null,
    val development: PluginDevelopmentStateV1 = PluginDevelopmentStateV1(),
    val authoredPlugins: List<AuthoredPlugin> = emptyList(),
) {
    public val items: List<PluginManagementItemV1> =
        buildList {
            val installed = plugins.associateByTo(linkedMapOf(), PluginManagementItemV1::pluginId)
            authoredPlugins.forEach { authoring ->
                add(
                    installed.remove(authoring.id)?.copy(authoring = authoring)
                        ?: PluginManagementItemV1(
                            pluginId = authoring.id,
                            platformId = authoring.platformId,
                            nameText = UiText.Raw(authoring.name),
                            version = "",
                            iconPath = "",
                            sourceUrl = null,
                            availableUpdate = null,
                            authoring = authoring,
                        ),
                )
            }
            addAll(installed.values)
        }
}

public data class PluginGitHubInstallStateV1(
    val busy: Boolean = false,
    val error: String? = null,
)

public data class PluginManagementItemV1(
    val pluginId: String,
    val platformId: String,
    val nameText: UiText,
    val version: String,
    val iconPath: String,
    val sourceUrl: String?,
    val availableUpdate: GitHubPluginVersionV1?,
    val authoring: AuthoredPlugin? = null,
)

public data class PluginInstallReviewV1(
    val pluginId: String,
    val platformId: String,
    val name: String,
    val version: String,
    val capabilities: List<String>,
    val permissions: List<PluginPermission>,
    val warnings: List<PluginInstallWarningV1>,
    val sourceUrl: String?,
)

/** The scope belongs to the application so installed platforms outlive the management screen. */
@HiddenFromObjC
public class PluginManagementPresenterV1(
    private val subsystem: PluginSubsystemV1,
    private val scope: CoroutineScope,
    private val operationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val github: GitHubPluginClient = subsystem.github,
    registry: () -> PlatformRegistry = { koinGet() },
    accounts: () -> AccountService = { koinGet() },
) {
    private val switcher by lazy { PluginRuntimeSwitcherV1(subsystem, registry(), accounts(), scope) }
    private val updates = mutableMapOf<String, GitHubPluginVersionV1>()
    private var preview: PreparedPluginInstallV1? = null
    private val mutableState = MutableStateFlow(snapshot())
    public val state: StateFlow<PluginManagementStateV1> = mutableState.asStateFlow()

    private val actionMutex = Mutex()
    private val updateMutex = Mutex()
    private val closed = MutableStateFlow(false)

    private val observation: Job =
        combine(
            subsystem.stateStore.desired,
            subsystem.sourceIssues,
            subsystem.runtimePool.issues,
            subsystem.developmentState,
        ) { _, _, _, _ -> Unit }
            .combine(subsystem.authoring.plugins) { _, _ -> Unit }
            .onEach { refreshState() }
            .launchIn(scope)

    public fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        observation.cancel()
        scope.launch {
            observation.join()
            actionMutex.withLock {
                if (preview != null) {
                    try {
                        withContext(operationDispatcher) { clearPreview() }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        error.printStackTrace()
                    }
                }
            }
        }
    }

    public fun openGitHubInstall() {
        if (closed.value) return
        mutableState.update { it.copy(gitHubInstall = it.gitHubInstall ?: PluginGitHubInstallStateV1()) }
    }

    public suspend fun inspectGitHub(url: String) {
        openGitHubInstall()
        runAction(gitHubInstall = true) {
            clearPreview()
            val catalog = github.catalog(url, forceRefresh = true)
            prepareGitHub(catalog, catalog.versions.first())
        }
    }

    public suspend fun checkUpdates(forceRefresh: Boolean = true) {
        updateMutex.withLock {
            withContext(operationDispatcher) {
                val installed =
                    subsystem.stateStore.desired.value.plugins.values
                        .filter { !it.removing && it.source is PluginInstallSourceV1.GitHub }
                installed.forEach { plugin ->
                    val source = plugin.source as PluginInstallSourceV1.GitHub
                    val update =
                        try {
                            val catalog =
                                github.catalog(
                                    source.repository.url,
                                    forceRefresh = forceRefresh,
                                    expectedRepositoryId = source.repository.id,
                                )
                            updateCandidate(plugin, catalog)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            null
                        }
                    actionMutex.withLock {
                        if (subsystem.stateStore.desired.value.plugins[plugin.pluginId] == plugin) {
                            if (update == null) updates.remove(plugin.pluginId) else updates[plugin.pluginId] = update
                            refreshState()
                        }
                    }
                }
            }
        }
    }

    public suspend fun inspectGitHubUpdate(pluginId: String) {
        runAction {
            clearPreview()
            val installed = requireNotNull(subsystem.stateStore.desired.value.plugins[pluginId]) { "Plugin is no longer installed" }
            val source = installed.source as? PluginInstallSourceV1.GitHub ?: error("Plugin is not installed from GitHub")
            val catalog = github.catalog(source.repository.url, forceRefresh = true, expectedRepositoryId = source.repository.id)
            val version = requireNotNull(updateCandidate(installed, catalog)) { "No GitHub update is available" }
            prepareGitHub(catalog, version, expectedPluginId = pluginId)
        }
    }

    private suspend fun prepareGitHub(
        catalog: GitHubPluginCatalogV1,
        version: GitHubPluginVersionV1,
        expectedPluginId: String? = null,
    ) {
        val bytes = github.archive(catalog.repository, version)
        val source = PluginInstallSourceV1.GitHub(catalog.repository, version.tag, version.commitSha)
        val prepared = subsystem.installer.inspect(Buffer().write(bytes), source, expectedPluginId)
        preview = prepared
    }

    @HiddenFromObjC
    public suspend fun inspect(source: Source): PluginInstallReviewV1 =
        runAction {
            clearPreview()
            subsystem.installer
                .inspect(source)
                .also { prepared ->
                    preview = prepared
                }.toReview()
        }

    public suspend fun inspect(path: String): PluginInstallReviewV1 =
        runAction {
            clearPreview()
            subsystem.installer
                .inspect(path.toPath())
                .also { prepared ->
                    preview = prepared
                }.toReview()
        }

    public suspend fun confirmInstall() {
        runMutation(gitHubInstall = mutableState.value.gitHubInstall != null) {
            val prepared = requireNotNull(preview) { "No plugin installation is pending" }
            requireNotDeveloping(prepared.pluginId)
            check(
                subsystem.authoring.plugins.value
                    .none { it.id == prepared.pluginId },
            ) {
                "Delete the editable local plugin before installing another package with this ID"
            }
            subsystem.installer.commit(prepared, confirmed = true, activate = switcher::install)
            updates.remove(prepared.pluginId)
            preview = null
            mutableState.update { snapshot(it).copy(gitHubInstall = null) }
        }
    }

    public suspend fun cancelInstall() {
        runAction(gitHubInstall = mutableState.value.gitHubInstall != null) {
            clearPreview()
            mutableState.update { snapshot(it).copy(gitHubInstall = null) }
        }
    }

    public suspend fun cancelPluginUpdate(pluginId: String) {
        runAuthoringAction { subsystem.authoring.cancelUpdate(pluginId) }
    }

    public suspend fun uninstall(pluginId: String) {
        if (subsystem.authoring.plugins.value
                .any { it.id == pluginId }
        ) {
            runAuthoringAction { subsystem.authoring.delete(pluginId) }
            return
        }
        runMutation {
            requireNotDeveloping(pluginId)
            val installed = requireNotNull(subsystem.stateStore.desired.value.plugins[pluginId]) { "Plugin is no longer installed" }
            switcher.uninstall(pluginId, installed.manifest.platform.id)
            updates.remove(pluginId)
            subsystem.installer.cleanup(subsystem.running.value.plugins.values)
        }
    }

    public suspend fun retryRuntime(pluginId: String) {
        runMutation {
            subsystem.runtimePool.retry(pluginId)
        }
    }

    public suspend fun cleanup() {
        runMutation { subsystem.installer.cleanup(subsystem.running.value.plugins.values) }
    }

    public suspend fun rebuildIndex() {
        runMutation {
            subsystem.installer.rebuildIndex()
            subsystem.stateStore.desired.value.plugins.values
                .forEach { switcher.install(it) }
        }
    }

    public fun clearGitHubInstallError() {
        mutableState.update { it.copy(gitHubInstall = it.gitHubInstall?.copy(error = null)) }
    }

    public fun reportOperationFailure() {
        mutableState.value = mutableState.value.copy(error = "Plugin operation failed")
    }

    private fun refreshState() {
        mutableState.update { snapshot(it) }
    }

    private fun snapshot(current: PluginManagementStateV1 = PluginManagementStateV1()): PluginManagementStateV1 {
        val desired = subsystem.stateStore.desired.value
        return current.copy(
            plugins =
                desired.plugins.values.sortedBy(InstalledPluginV1::pluginId).map { installed ->
                    installed.toItem(
                        iconPath =
                            subsystem.stateStore.paths
                                .iconPath(installed.packageHash, installed.iconExtension)
                                .toString(),
                        availableUpdate = updates[installed.pluginId].takeUnless { installed.removing },
                    )
                },
            issues = subsystem.sourceIssues.value,
            runtimeIssues =
                subsystem.runtimePool.issues.value.values
                    .sortedBy(PluginRuntimeIssueV1::pluginId),
            development = subsystem.developmentState.value,
            authoredPlugins = subsystem.authoring.plugins.value,
            canRebuildIndex = !desired.indexHealthy,
            pendingInstall = preview?.toReview(),
        )
    }

    private fun requireNotDeveloping(pluginId: String) {
        check(
            subsystem.running.value.plugins[pluginId]
                ?.developmentSource == null,
        ) { "Stop development before changing the installed plugin" }
    }

    private suspend fun clearPreview() {
        val previous = preview
        preview = null
        refreshState()
        if (previous != null) subsystem.installer.discard(previous)
    }

    private suspend fun runAuthoringAction(block: suspend () -> Unit) {
        try {
            block()
            mutableState.update { snapshot(it).copy(error = null) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.update { snapshot(it).copy(error = error.message ?: "Plugin operation failed") }
            throw error
        }
    }

    private suspend fun <T> runMutation(
        gitHubInstall: Boolean = false,
        block: suspend () -> T,
    ): T = runAction(gitHubInstall) { subsystem.mutationMutex.withLock { block() } }

    private suspend fun <T> runAction(
        gitHubInstall: Boolean = false,
        block: suspend () -> T,
    ): T =
        actionMutex.withLock {
            check(!closed.value) { "Plugin management is closed" }
            var failure: String? = null

            fun publish(busy: Boolean) {
                mutableState.update { current ->
                    val refreshed = snapshot(current)
                    if (gitHubInstall) {
                        refreshed.copy(
                            gitHubInstall = refreshed.gitHubInstall?.copy(busy = busy, error = failure),
                        )
                    } else {
                        refreshed.copy(busy = busy, error = failure)
                    }
                }
            }
            publish(busy = true)
            try {
                withContext(operationDispatcher) { block() }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failure = error.message ?: "Plugin operation failed"
                throw error
            } finally {
                publish(busy = false)
            }
        }
}

private fun InstalledPluginV1.toItem(
    iconPath: String,
    availableUpdate: GitHubPluginVersionV1?,
): PluginManagementItemV1 =
    PluginManagementItemV1(
        pluginId = pluginId,
        platformId = manifest.platform.id,
        nameText = manifest.name.toUiText(pluginId),
        version = version,
        iconPath = iconPath,
        sourceUrl = (source as? PluginInstallSourceV1.GitHub)?.repository?.url,
        availableUpdate = availableUpdate,
    )

private fun PreparedPluginInstallV1.toReview(): PluginInstallReviewV1 =
    PluginInstallReviewV1(
        pluginId = pluginId,
        platformId = platformId,
        name = nameFallback,
        version = version,
        capabilities =
            capabilities.entries
                .sortedBy { it.key }
                .map { (capability, operations) -> "$capability: ${operations.sorted().joinToString()}" },
        permissions = permissions.sortedWith(compareBy({ it.type.name }, { it.origin }, { it.cookieName.orEmpty() })),
        warnings = warnings,
        sourceUrl = (source as? PluginInstallSourceV1.GitHub)?.repository?.url,
    )

private fun updateCandidate(
    installed: InstalledPluginV1,
    catalog: GitHubPluginCatalogV1,
): GitHubPluginVersionV1? {
    val source = installed.source as PluginInstallSourceV1.GitHub
    catalog.versions.firstOrNull { compareSemVer(it.version, installed.version) > 0 }?.let { return it }
    val current =
        requireNotNull(catalog.versions.firstOrNull { it.tag == source.tag }) { "The installed GitHub tag is no longer available" }
    return current.takeIf { it.commitSha != source.commitSha }
}
