@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.compose

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.UiComposable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.navigation.NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS
import dev.dimension.flare.ui.navigation.NavigationBackRequest
import dev.dimension.flare.ui.navigation.NavigationEntryContentHost
import dev.dimension.flare.ui.navigation.NavigationEntryIdentity
import dev.dimension.flare.ui.navigation.NavigationModel
import dev.dimension.flare.ui.navigation.NavigationModelDispatcher
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.NavigationWidget
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
import dev.dimension.flare.ui.navigation.hasSameTopologyAs
import dev.dimension.flare.ui.navigation.topology
import kotlinx.coroutines.yield

/** Registers the Navigation3-backed Android Compose renderer for [dev.dimension.flare.ui.navigation.NavigationDisplay]. */
public object AndroidComposeNavigationRendererPlugin : FlareRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(NavigationWidget::class) { _ -> AndroidComposeNavigationWidget() }
    }
}

private class AndroidComposeNavigationWidget :
    AbstractAndroidComposeWidget(),
    NavigationWidget {
    private var model: NavigationModel? by mutableStateOf(null)
    private var modelDispatcher: NavigationModelDispatcher? = null
    private var stopObservingModels: (() -> Unit)? = null
    private val hosts = linkedMapOf<Any, ComposeEntryHost>()
    private val compositionCounts = mutableMapOf<Any, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stackRevision = 0L
    private var nextBackRequestId = 0L
    private var pendingBack: ComposeBackAcknowledgement? = null
    private var pendingBackTimeout: Runnable? = null

    override fun setModelDispatcher(dispatcher: NavigationModelDispatcher) {
        stopObservingModels?.invoke()
        modelDispatcher = dispatcher
        stopObservingModels = dispatcher.observe(::applyModel)
    }

    private fun applyModel(model: NavigationModel) {
        model.entries.requirePagesOnly("Android Compose")
        val previousModel = this.model
        if (previousModel == null || !previousModel.entries.hasSameTopologyAs(model.entries)) {
            stackRevision += 1L
        }
        settlePendingBack(model.entries.topology())
        this.model = model
        model.entries.forEach { entry ->
            val host = hosts[entry.contentKey]
            if (host == null) {
                hosts[entry.contentKey] =
                    ComposeEntryHost(
                        entry = entry,
                        nativeControllerOwner = model.nativeControllerOwner,
                        subcompositions = model.subcompositions,
                    )
            } else {
                host.update(
                    entry = entry,
                    nativeControllerOwner = model.nativeControllerOwner,
                    subcompositions = model.subcompositions,
                )
            }
        }
        // Model delivery is already deferred until after the parent Flare apply transaction. Keep
        // the visible page active and one frozen predecessor tree for predictive back. A frozen
        // ReusableComposition retains widgets for the first transition frame while cancelling the
        // hidden entry's observations and remembered effects.
        val declaredKeys = model.entries.mapTo(mutableSetOf(), ResolvedNavigationEntry::contentKey)
        val currentKey = model.entries.last().contentKey
        val predecessorKey = model.entries.getOrNull(model.entries.lastIndex - 1)?.contentKey
        checkNotNull(hosts[currentKey]).realize()
        predecessorKey?.let { contentKey ->
            val host = checkNotNull(hosts[contentKey])
            if (contentKey in compositionCounts) {
                host.realize()
            } else {
                host.prepareSnapshot()
            }
        }
        hosts.forEach { (contentKey, host) ->
            if (
                contentKey != currentKey &&
                contentKey != predecessorKey &&
                contentKey !in compositionCounts &&
                contentKey in declaredKeys
            ) {
                host.releaseContent()
            }
        }
        disposeUncomposedRemovedHosts(declaredKeys)
    }

    @Composable
    @UiComposable
    override fun Render() {
        val currentModel = model ?: return
        val entries =
            currentModel.entries.map { entry ->
                val host = checkNotNull(hosts[entry.contentKey])
                shadowEntry(entry, host)
            }
        NavDisplay(
            entries = entries,
            modifier = composeModifier,
            onBack = {
                val latest =
                    model?.takeIf { candidate ->
                        candidate.entries.size > 1 &&
                            candidate.entries.hasSameTopologyAs(currentModel.entries) &&
                            modelDispatcher?.hasUndeliveredModel != true &&
                            pendingBack == null
                    }
                if (latest != null) {
                    requestBack(latest)
                }
            },
        )
    }

    override fun dispose() {
        stopObservingModels?.invoke()
        stopObservingModels = null
        modelDispatcher = null
        hosts.values.forEach(ComposeEntryHost::dispose)
        hosts.clear()
        compositionCounts.clear()
        clearPendingBack()
        model = null
    }

    @Composable
    private fun shadowEntry(
        entry: ResolvedNavigationEntry,
        host: ComposeEntryHost,
    ): NavEntry<Any> =
        shadowEntry(entry.entry) {
            LaunchedEffect(entry.contentKey, host) {
                // The retained predecessor widget tree covers the first frame. Activate its Flare
                // composition only after this parent apply transaction is unlocked so hidden
                // observations and effects run only while NavDisplay actually composes the scene.
                yield()
                if (compositionCounts.containsKey(entry.contentKey) && hosts[entry.contentKey] === host) {
                    host.realize()
                }
            }
            DisposableEffect(entry.contentKey, host) {
                val contentKey = entry.contentKey
                compositionCounts[contentKey] = compositionCounts.getOrElse(contentKey) { 0 } + 1
                onDispose {
                    releaseComposition(contentKey, host)
                }
            }
            host.children.Render()
        }

    private fun releaseComposition(
        contentKey: Any,
        host: ComposeEntryHost,
    ) {
        val count = compositionCounts[contentKey] ?: return
        if (count > 1) {
            compositionCounts[contentKey] = count - 1
            return
        }
        compositionCounts.remove(contentKey)

        // NavDisplay retains outgoing entries while their transition is running. Once no scene is
        // composing this entry, freeze only the immediate predictive target and release older
        // widget trees completely.
        if (model?.entries?.any { it.contentKey == contentKey } == true) {
            if (contentKey == predictiveContentKey()) {
                host.deactivateContent()
            } else if (contentKey != model?.entries?.lastOrNull()?.contentKey) {
                host.releaseContent()
            }
        } else if (hosts[contentKey] === host) {
            hosts.remove(contentKey)
            host.dispose()
        } else {
            host.dispose()
        }
    }

    private fun disposeUncomposedRemovedHosts(declaredKeys: Set<Any>) {
        val iterator = hosts.iterator()
        while (iterator.hasNext()) {
            val (contentKey, host) = iterator.next()
            if (contentKey !in declaredKeys && contentKey !in compositionCounts) {
                host.dispose()
                iterator.remove()
            }
        }
    }

    private fun predictiveContentKey(): Any? = model?.entries?.let { it.getOrNull(it.lastIndex - 1)?.contentKey }

    private fun requestBack(model: NavigationModel) {
        val base = model.entries.topology()
        val acknowledgement =
            ComposeBackAcknowledgement(
                requestId = nextBackRequestId++,
                baseRevision = stackRevision,
                base = base,
                target = base.dropLast(1),
            )
        pendingBack = acknowledgement
        scheduleBackTimeout(acknowledgement)
        val request =
            NavigationBackRequest(
                requestId = acknowledgement.requestId,
                baseRevision = acknowledgement.baseRevision,
                base = acknowledgement.base,
                target = acknowledgement.target,
                popCount = 1,
                isActiveRequest = {
                    pendingBack === acknowledgement && !acknowledgement.accepted
                },
                acceptRequest = {
                    if (pendingBack !== acknowledgement || acknowledgement.accepted) {
                        false
                    } else {
                        acknowledgement.accepted = true
                        true
                    }
                },
                rejectRequest = {
                    if (pendingBack !== acknowledgement || acknowledgement.accepted) {
                        false
                    } else {
                        clearPendingBack(acknowledgement)
                    }
                },
                abortAcceptedRequest = {
                    if (pendingBack !== acknowledgement || !acknowledgement.accepted) {
                        false
                    } else {
                        clearPendingBack(acknowledgement)
                    }
                },
            )
        try {
            model.onBack(request)
        } catch (error: Throwable) {
            clearPendingBack(acknowledgement)
            throw error
        }
    }

    private fun settlePendingBack(topology: List<NavigationEntryIdentity>) {
        val acknowledgement = pendingBack ?: return
        when (topology) {
            acknowledgement.base -> Unit
            acknowledgement.target -> clearPendingBack(acknowledgement)
            else -> clearPendingBack(acknowledgement)
        }
    }

    private fun scheduleBackTimeout(acknowledgement: ComposeBackAcknowledgement) {
        pendingBackTimeout?.let(mainHandler::removeCallbacks)
        lateinit var timeout: Runnable
        timeout =
            Runnable {
                if (pendingBackTimeout !== timeout) return@Runnable
                pendingBackTimeout = null
                clearPendingBack(acknowledgement)
            }
        pendingBackTimeout = timeout
        mainHandler.postDelayed(timeout, NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS)
    }

    private fun clearPendingBack(expected: ComposeBackAcknowledgement? = null): Boolean {
        val current = pendingBack ?: return false
        if (expected != null && current !== expected) return false
        pendingBack = null
        pendingBackTimeout?.let(mainHandler::removeCallbacks)
        pendingBackTimeout = null
        return true
    }
}

private data class ComposeBackAcknowledgement(
    val requestId: Long,
    val baseRevision: Long,
    val base: List<NavigationEntryIdentity>,
    val target: List<NavigationEntryIdentity>,
    var accepted: Boolean = false,
)

private class ComposeEntryHost(
    entry: ResolvedNavigationEntry,
    nativeControllerOwner: dev.dimension.flare.ui.FlareNativeControllerOwner?,
    subcompositions: dev.dimension.flare.ui.FlareSubcompositionFactory,
) {
    val children = AndroidComposeChildren()
    private var entry: ResolvedNavigationEntry = entry
    private var contentHost: NavigationEntryContentHost? = null
    private var contentActive: Boolean = false
    private var disposed: Boolean = false

    private var nativeControllerOwner = nativeControllerOwner
    private var subcompositions = subcompositions

    fun update(
        entry: ResolvedNavigationEntry,
        nativeControllerOwner: dev.dimension.flare.ui.FlareNativeControllerOwner?,
        subcompositions: dev.dimension.flare.ui.FlareSubcompositionFactory,
    ) {
        check(!disposed) { "Compose navigation entry host is already disposed." }
        require(entry.contentKey == this.entry.contentKey) {
            "A Compose navigation entry host cannot change contentKey."
        }
        val environmentChanged =
            this.nativeControllerOwner !== nativeControllerOwner ||
                this.subcompositions !== subcompositions
        val wasActive = contentActive
        this.entry = entry
        this.nativeControllerOwner = nativeControllerOwner
        this.subcompositions = subcompositions
        if (environmentChanged) {
            releaseContent()
            if (wasActive) realize()
        } else {
            contentHost?.update(entry)
        }
    }

    fun realize() {
        check(!disposed) { "Compose navigation entry host is already disposed." }
        val current = contentHost
        if (current == null) {
            contentHost =
                NavigationEntryContentHost(
                    root = children,
                    nativeControllerOwner = nativeControllerOwner,
                    subcompositions = subcompositions,
                    initialEntry = entry,
                )
            contentActive = true
        } else if (!contentActive) {
            current.activate()
            contentActive = true
        }
    }

    fun prepareSnapshot() {
        if (contentHost == null) {
            realize()
            deactivateContent()
        } else if (contentActive) {
            deactivateContent()
        }
    }

    fun deactivateContent() {
        if (!contentActive) return
        checkNotNull(contentHost).deactivate()
        contentActive = false
    }

    fun releaseContent() {
        contentHost?.dispose()
        contentHost = null
        contentActive = false
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        releaseContent()
    }
}

@Suppress("UNCHECKED_CAST")
private fun shadowEntry(
    entry: NavEntry<*>,
    content: @Composable () -> Unit,
): NavEntry<Any> =
    NavEntry(
        navEntry = entry as NavEntry<Any>,
        content = { _: Any -> content() },
    )

private fun List<ResolvedNavigationEntry>.requirePagesOnly(adapter: String) {
    firstOrNull { it.presentation != NavigationPresentation.Page }?.let { entry ->
        error(
            "$adapter navigation currently supports only Page presentation; " +
                "contentKey ${entry.contentKey} uses ${entry.presentation}.",
        )
    }
}
