@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.navigation

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Atomic model delivered to one renderer adapter. */
internal class NavigationModel(
    val entries: List<ResolvedNavigationEntry>,
    val onBack: (NavigationBackRequest<NavigationEntryIdentity>) -> Unit,
    val subcompositions: FlareSubcompositionFactory,
    val nativeControllerOwner: FlareNativeControllerOwner? = null,
)

/** Renderer seam implemented by the platform navigation adapters. */
internal interface NavigationWidget : FlareWidget {
    fun setModelDispatcher(dispatcher: NavigationModelDispatcher)
}

/**
 * Conflates staged models and delivers only the latest one after parent applyChanges has finished.
 *
 * Direct [dispatch] remains available for adapter tests and controlled non-Compose hosts. Production
 * [NavigationDisplay] uses [stage] and one finite scheduled delivery, so recomposition cannot resume
 * an older per-model effect after a newer model has already been staged.
 */
internal class NavigationModelDispatcher {
    private var observer: ((NavigationModel) -> Unit)? = null
    private var stagedModel: NavigationModel? = null
    private var stagedRevision: Long = 0L
    private var deliveredRevision: Long = 0L
    private var deliveryScope: CoroutineScope? = null
    private var deliveryJob: Job? = null

    /** True while a newer staged model has not reached the current renderer observer. */
    val hasUndeliveredModel: Boolean
        get() = stagedModel != null && deliveredRevision != stagedRevision

    fun observe(value: (NavigationModel) -> Unit): () -> Unit {
        check(observer == null) { "A NavigationModelDispatcher already has a renderer observer." }
        observer = value
        // A replacement renderer must receive the latest model even if its predecessor did.
        deliveredRevision = -1L
        scheduleDelivery()
        return {
            if (observer === value) observer = null
        }
    }

    fun stage(
        model: NavigationModel,
        scope: CoroutineScope,
    ) {
        stagedModel = model
        stagedRevision += 1L
        deliveryScope = scope
        scheduleDelivery()
    }

    private fun scheduleDelivery() {
        val scope = deliveryScope ?: return
        if (deliveryJob?.isActive == true || !hasUndeliveredModel) return
        deliveryJob =
            scope.launch {
                do {
                    // This first suspension keeps renderer work outside applyChanges even when the
                    // scope uses an immediate UI dispatcher.
                    yield()
                    val revision = stagedRevision
                    dispatchLatestStagedModel()
                } while (stagedRevision != revision)
            }
    }

    fun dispatch(model: NavigationModel) {
        observer?.invoke(model)
    }

    private fun dispatchLatestStagedModel() {
        val currentObserver = observer ?: return
        if (deliveredRevision == stagedRevision) return
        val currentModel = stagedModel ?: return
        val currentRevision = stagedRevision
        currentObserver(currentModel)
        deliveredRevision = currentRevision
    }
}

/** Validated entry data shared by every renderer adapter. */
internal data class ResolvedNavigationEntry(
    val contentKey: Any,
    val presentation: NavigationPresentation,
    val entry: NavEntry<*>,
)

internal fun <K : Any> resolveNavigationEntries(entries: List<NavEntry<K>>): List<ResolvedNavigationEntry> {
    require(entries.isNotEmpty()) { "A navigation stack cannot be empty." }

    val indicesByContentKey = mutableMapOf<Any, Int>()
    var foundOverlay = false
    val resolved =
        entries.mapIndexed { index, entry ->
            val presentation = entry.navigationPresentation()
            val previousIndex = indicesByContentKey.put(entry.contentKey, index)
            require(previousIndex == null) {
                "Navigation contentKey ${entry.contentKey} occurs at both index " +
                    "$previousIndex and $index."
            }
            if (presentation == NavigationPresentation.Page) {
                require(!foundOverlay) {
                    "Navigation Page at index $index appears after an overlay entry."
                }
            } else {
                foundOverlay = true
            }
            ResolvedNavigationEntry(
                contentKey = entry.contentKey,
                presentation = presentation,
                entry = entry,
            )
        }

    require(resolved.first().presentation == NavigationPresentation.Page) {
        "The first navigation entry must use Page presentation."
    }
    return resolved
}

private fun NavEntry<*>.navigationPresentation(): NavigationPresentation {
    val metadataKey = NavigationPresentationMetadata.toString()
    if (!metadata.containsKey(metadataKey)) return NavigationPresentation.Page
    val value = metadata[metadataKey]
    require(value is NavigationPresentation) {
        "Navigation metadata $metadataKey must contain a NavigationPresentation, " +
            "but was ${value?.let { it::class.simpleName } ?: "null"}."
    }
    return value
}

internal fun validateStablePresentations(
    previous: List<ResolvedNavigationEntry>,
    current: List<ResolvedNavigationEntry>,
) {
    if (previous.isEmpty()) return
    val previousByKey = previous.groupBy(ResolvedNavigationEntry::contentKey)
    current.forEach { entry ->
        val oldEntries = previousByKey[entry.contentKey] ?: return@forEach
        val oldPresentation = oldEntries.first().presentation
        require(oldEntries.all { it.presentation == oldPresentation }) {
            "Navigation contentKey ${entry.contentKey} is still retained with multiple presentations."
        }
        require(oldPresentation == entry.presentation) {
            "Navigation contentKey ${entry.contentKey} changed presentation from " +
                "$oldPresentation to ${entry.presentation}. Wait until its previous native " +
                "incarnation has been released, or use a new contentKey."
        }
    }
}

internal fun List<ResolvedNavigationEntry>.hasSameTopologyAs(other: List<ResolvedNavigationEntry>): Boolean =
    size == other.size &&
        indices.all { index ->
            this[index].contentKey == other[index].contentKey &&
                this[index].presentation == other[index].presentation
        }

internal fun List<ResolvedNavigationEntry>.isTopologyPrefixOf(other: List<ResolvedNavigationEntry>): Boolean =
    size <= other.size &&
        indices.all { index ->
            this[index].contentKey == other[index].contentKey &&
                this[index].presentation == other[index].presentation
        }

internal fun List<ResolvedNavigationEntry>.rebindCommonPrefixFrom(current: List<ResolvedNavigationEntry>): List<ResolvedNavigationEntry> {
    if (isEmpty() || current.isEmpty()) return this
    var prefixSize = 0
    val limit = minOf(size, current.size)
    while (prefixSize < limit &&
        this[prefixSize].contentKey == current[prefixSize].contentKey &&
        this[prefixSize].presentation == current[prefixSize].presentation
    ) {
        prefixSize += 1
    }
    if (prefixSize == 0) return this
    return buildList(size) {
        addAll(current.take(prefixSize))
        addAll(this@rebindCommonPrefixFrom.drop(prefixSize))
    }
}
