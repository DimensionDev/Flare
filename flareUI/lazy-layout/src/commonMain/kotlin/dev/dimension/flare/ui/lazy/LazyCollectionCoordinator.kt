@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import androidx.compose.runtime.key as composeKey

internal class LazyCollectionCoordinator(
    private val owner: Any,
    private val onModelChanged: (
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ) -> LazyRealizedItemUpdate,
    private val onScroll: (LazyListScrollRequest) -> Unit,
) {
    private val itemHosts = mutableSetOf<LazyItemHost>()
    private val realizedKeys = mutableMapOf<Any, LazyItemHost>()
    var model: LazyCollectionModel? = null
        private set

    fun setModel(value: LazyCollectionModel) {
        val previous = model
        val previouslyRealizedHosts = itemHosts.toList()
        if (previous?.state !== value.state) {
            previous?.state?.detach(owner)
        }
        model = value
        value.state.attach(
            owner = owner,
            itemCount = value.itemProvider.itemCount,
            onScroll = onScroll,
        )
        val realizedItemUpdate = onModelChanged(previous, value)
        if (previous != null && realizedItemUpdate == LazyRealizedItemUpdate.Rebind) {
            rebindPreviouslyRealizedHosts(previouslyRealizedHosts, value.itemProvider)
        }
    }

    fun reportLayoutInfo(value: LazyListLayoutInfo) {
        model?.state?.updateLayoutInfo(owner, value)
    }

    fun reportScrollInProgress(value: Boolean) {
        model?.state?.updateScrollInProgress(owner, value)
    }

    fun createItemHost(root: FlareChildren): LazyItemHost {
        checkNotNull(model) { "A lazy collection model must be set before realizing items." }
        return LazyItemHost(this, root).also(itemHosts::add)
    }

    fun realizedItemsMatch(provider: LazyItemProvider): Boolean =
        itemHosts.all { host ->
            val key = host.key ?: return@all true
            host.index in 0 until provider.itemCount && provider.key(host.index) == key
        }

    internal fun requireModel(): LazyCollectionModel = checkNotNull(model) { "The lazy collection has already been disposed." }

    internal fun bindKey(
        host: LazyItemHost,
        previousKey: Any?,
        key: Any,
        index: Int,
        updateContentSynchronously: Boolean,
    ) {
        if (previousKey != null && realizedKeys[previousKey] === host) {
            realizedKeys.remove(previousKey)
        }
        val previousHost = realizedKeys[key]
        if (previousHost != null && previousHost !== host) {
            val provider = requireModel().itemProvider
            val previousIndexStillOwnsKey =
                previousHost.index in 0 until provider.itemCount &&
                    provider.key(previousHost.index) == key
            check(previousHost.index == index || !previousIndexStillOwnsKey) {
                "Lazy list key $key is used by more than one realized item."
            }
            if (previousHost.index == index || previousHost.index !in 0 until provider.itemCount) {
                previousHost.dispose()
            } else {
                // Move the old holder to the key now owned by its current index before giving this
                // key to the replacement. This preserves its composition and avoids both a visible
                // blank and overlapping SaveableStateProvider instances for the same stable key.
                previousHost.bind(previousHost.index, updateContentSynchronously)
            }
        }
        realizedKeys[key] = host
    }

    internal fun release(
        host: LazyItemHost,
        key: Any?,
    ) {
        itemHosts.remove(host)
        if (key != null && realizedKeys[key] === host) {
            realizedKeys.remove(key)
        }
    }

    fun dispose() {
        model?.state?.detach(owner)
        model = null
        val hosts = itemHosts.toList()
        itemHosts.clear()
        realizedKeys.clear()
        hosts.forEach(LazyItemHost::disposeFromCoordinator)
    }

    private fun rebindPreviouslyRealizedHosts(
        hosts: List<LazyItemHost>,
        provider: LazyItemProvider,
    ) {
        val activeHosts = hosts.filter { it in itemHosts && it.key != null }
        if (activeHosts.isEmpty()) return
        val requestedKeys = activeHosts.mapNotNull(LazyItemHost::key).toSet()
        val indicesByKey = mutableMapOf<Any, Int>()
        repeat(provider.itemCount) { index ->
            val key = provider.key(index)
            if (key in requestedKeys) {
                check(indicesByKey.put(key, index) == null) {
                    "Lazy list key $key occurs more than once in the updated item provider."
                }
            }
        }
        activeHosts.forEach { host ->
            val nextIndex = indicesByKey[host.key]
            if (nextIndex == null) {
                host.dispose()
            } else {
                host.bind(nextIndex, updateContentSynchronously = false)
            }
        }
    }
}

internal enum class LazyRealizedItemUpdate {
    Rebind,
    RendererManaged,
}

internal class LazyItemHost(
    private val coordinator: LazyCollectionCoordinator,
    private val root: FlareChildren,
) {
    private var composition: FlareSubcomposition? = null
    private var compositionFactory: FlareSubcompositionFactory? = null
    private var content: FlareContent? = null
    private var contentVersion: MutableState<Int>? = null
    private var disposed: Boolean = false
    private val hostedContent: FlareContent = {
        contentVersion?.value
        checkNotNull(content).invoke()
    }

    internal val isDisposed: Boolean
        get() = disposed

    var index: Int = -1
        private set

    var key: Any? = null
        private set

    fun bind(index: Int) {
        bind(index, updateContentSynchronously = true)
    }

    internal fun bind(
        index: Int,
        updateContentSynchronously: Boolean,
    ) {
        check(!disposed) { "Lazy item host is already disposed." }
        val model = coordinator.requireModel()
        val provider = model.itemProvider
        require(index in 0 until provider.itemCount) {
            "Lazy list index $index is outside 0 until ${provider.itemCount}."
        }
        val nextKey = provider.key(index)
        coordinator.bindKey(this, key, nextKey, index, updateContentSynchronously)
        val nextContent: FlareContent = {
            composeKey(nextKey) {
                provider.Item(index)
            }
        }

        if (composition == null || compositionFactory !== model.subcompositions) {
            composition?.dispose()
            compositionFactory = model.subcompositions
            content = nextContent
            contentVersion = mutableStateOf(0)
            composition =
                model.subcompositions.create(root).also { nextComposition ->
                    nextComposition.setContent(hostedContent)
                }
        } else {
            content = nextContent
            if (updateContentSynchronously) {
                checkNotNull(composition).setContent {
                    contentVersion?.value
                    checkNotNull(content).invoke()
                }
            } else {
                val version = checkNotNull(contentVersion)
                version.value += 1
            }
        }

        this.index = index
        key = nextKey
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        composition?.dispose()
        composition = null
        content = null
        contentVersion = null
        coordinator.release(this, key)
        key = null
        index = -1
    }

    internal fun disposeFromCoordinator() {
        if (disposed) return
        disposed = true
        composition?.dispose()
        composition = null
        content = null
        contentVersion = null
        key = null
        index = -1
    }
}
