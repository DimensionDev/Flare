@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.lazy

import dev.dimension.compose.nativekit.NativeKitChildren
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.max
import kotlin.math.round

/** Native collections own realization and reuse; this adapter supplies only platform operations. */
internal interface NativeLazyCollection {
    fun viewport(orientation: LazyListOrientation): NativeLazyViewport

    val isPhysicalScrollInProgress: Boolean

    fun reloadData()

    /** An unanimated positional splice. [apply] publishes the new count and geometry atomically. */
    fun updateItems(
        index: Int,
        removedCount: Int,
        insertedCount: Int,
        apply: () -> Double?,
    )

    fun invalidateLayout()

    fun layoutIfNeeded()

    fun scrollTo(
        offset: Double,
        animated: Boolean,
        completion: () -> Unit,
    )

    fun stopAnimatedScroll()

    fun schedule(block: () -> Unit)
}

internal data class NativeLazyViewport(
    val offset: Double,
    val extent: Double,
    val crossExtent: Double,
)

internal interface NativeLazyItem {
    val children: NativeKitChildren

    fun configure(model: LazyCollectionModel)

    fun measure(
        orientation: LazyListOrientation,
        crossExtent: Double,
    ): Double
}

/** Shared UIKit/AppKit binding, variable-size geometry, anchor preservation, and scroll lifecycle. */
internal class NativeLazyCollectionController(
    private val native: NativeLazyCollection,
    uiDispatcher: CoroutineDispatcher,
) {
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = { _, _ ->
                modelResetPending = true
                scheduleLayout()
                LazyRealizedItemUpdate.RendererManaged
            },
            onScroll = ::performScroll,
            onScrollCancelled = ::cancelScroll,
            uiDispatcher = uiDispatcher,
        )
    private val geometry = VariableExtentLayoutState()
    private val bindings = mutableMapOf<NativeLazyItem, Binding>()
    private val reuseIdentifiers = mutableMapOf<Any?, String>()
    private var environment: ExtentEnvironment? = null
    private var pendingAnchor: Anchor? = null
    private var pendingScroll: LazyListScrollRequest? = null
    private var modelResetPending = false
    private var nativeReloadPending = false
    private var layoutScheduled = false
    private var layingOut = false
    private var disposed = false
    private var physicalScrollInProgress = false
    private var programmaticScrollInProgress = false
    private var animationGeneration = 0L

    // Native data-source counts and attributes always refer to the same applied generation.
    var model: LazyCollectionModel? = null
        private set

    val itemCount: Int get() = geometry.itemCount
    val contentExtent: Double get() = max(geometry.contentExtent, viewport.extent)
    val crossExtent: Double get() = viewport.crossExtent.coerceAtLeast(1.0)
    private val viewport: NativeLazyViewport
        get() = native.viewport(model?.orientation ?: LazyListOrientation.Vertical)
    private val isPhysicalScroll: Boolean
        get() = physicalScrollInProgress || native.isPhysicalScrollInProgress

    fun setModel(value: LazyCollectionModel) {
        if (pendingAnchor == null) pendingAnchor = captureAnchor()
        if (isPhysicalScroll) pendingAnchor = pendingAnchor?.copy(preserveViewportDelta = true)
        coordinator.setModel(value)
    }

    fun itemRange(
        start: Double,
        end: Double,
    ): IntRange = geometry.visibleRange(start, end)

    fun itemStart(index: Int): Double = geometry.itemStart(index)

    fun itemExtent(index: Int): Double = geometry.itemExtent(index)

    fun reuseIdentifier(index: Int): String {
        val provider = checkNotNull(model).itemProvider
        val contentType = if (index in 0 until provider.itemCount) provider.contentType(index) else null
        return reuseIdentifiers.getOrPut(contentType) { "NativeKitLazyItem-${reuseIdentifiers.size}" }
    }

    /** Called only by the native data source, after dequeueing a cell/item. */
    fun bind(
        item: NativeLazyItem,
        index: Int,
    ) {
        val current = model ?: return
        // Stopping a native animation can synchronously request cells from its old index space
        // while the coordinator is attaching a newer model. The next layout reloads those cells.
        if (current !== coordinator.model || index !in 0 until itemCount) {
            nativeReloadPending = true
            scheduleLayout()
            return
        }
        val existing = bindings[item]
        val binding =
            if (existing == null || existing.host.isDisposed) {
                release(item)
                val next = Binding(item)
                next.host =
                    coordinator.createItemHost(
                        InvalidatingLazyItemChildren(item.children) {
                            next.needsMeasurement = true
                            scheduleLayout()
                        },
                    )
                bindings[item] = next
                next
            } else {
                existing
            }
        binding.item.configure(current)
        binding.host.bind(index)
        binding.needsMeasurement = true
        scheduleLayout()
    }

    /** Ends the composition when the native collection relinquishes the item. */
    fun release(item: NativeLazyItem) {
        bindings.remove(item)?.host?.dispose()
    }

    fun beginPhysicalScroll() {
        physicalScrollInProgress = true
        pendingScroll?.let { request ->
            cancelScroll(request)
            request.cancel()
        }
        coordinator.reportScrollInProgress(true)
    }

    fun endPhysicalScroll() {
        physicalScrollInProgress = false
        coordinator.reportScrollInProgress(false)
        layout()
    }

    fun scheduleLayout() {
        if (disposed || layingOut || layoutScheduled) return
        layoutScheduled = true
        native.schedule {
            layoutScheduled = false
            if (!disposed) layout()
        }
    }

    fun layout() {
        if (disposed || layingOut) return
        val current = coordinator.model ?: return
        var interruptedScroll: LazyListScrollRequest? = null
        var geometryUnsettled = false
        layingOut = true
        try {
            val nextViewport = native.viewport(current.orientation)
            val nextEnvironment =
                ExtentEnvironment(
                    current.orientation,
                    round(nextViewport.crossExtent * 2.0) / 2.0,
                    current.crossAxisAlignment,
                    current.subcompositions,
                )
            val environmentChanged = environment != nextEnvironment
            if (modelResetPending || environmentChanged || nativeReloadPending) {
                // Layout corrections can stop the platform animation. Retarget the same request
                // after applying this generation, and ignore completion from its old animation.
                interruptedScroll = pendingScroll.takeUnless { programmaticScrollInProgress }
                if (interruptedScroll != null) {
                    animationGeneration++
                    native.stopAnimatedScroll()
                }
                val splice =
                    if (!nativeReloadPending && model != null &&
                        !environmentChanged
                    ) {
                        positionalSplice(current.itemProvider)
                    } else {
                        null
                    }
                val inPlace =
                    !nativeReloadPending && model != null && itemCount == current.itemProvider.itemCount &&
                        bindings.values.all { binding ->
                            val index = binding.host.index
                            index in 0 until current.itemProvider.itemCount && current.itemProvider.key(index) == binding.host.key
                        }
                val geometrySplice = splice?.let { geometrySplice(checkNotNull(model).itemProvider, current.itemProvider, it) }
                if (pendingAnchor == null) pendingAnchor = captureAnchor()
                modelResetPending = false
                nativeReloadPending = false
                val applyModel = {
                    model = current
                    environment = nextEnvironment
                    geometry.update(current.itemProvider, current.spacing.toDouble(), nextEnvironment, geometrySplice)
                }
                if (splice != null) {
                    native.updateItems(splice.index, splice.removed, splice.inserted) {
                        applyModel()
                        bindings.values.toList().forEach { binding -> bind(binding.item, splice.newIndex(binding.host.index)) }
                        pendingAnchor?.takeUnless { isPhysicalScroll }?.let { anchor ->
                            val index = current.itemProvider.findIndexByKey(anchor.key, anchor.index, anchor.itemCount)
                            if (index in 0 until itemCount) {
                                val start = geometry.itemStart(index)
                                val target =
                                    restoredLazyViewportOffset(
                                        start - anchor.offset,
                                        anchor.viewportOffset,
                                        viewport.offset,
                                        anchor.preserveViewportDelta && anchor.orientation == current.orientation,
                                    )
                                val offset = clampedOffset(target)
                                // Batch updates may request and resolve newly inserted geometry.
                                // Keep the anchor until that layout settles, with the user's drag
                                // delta already applied so our own offset correction is not added twice.
                                pendingAnchor = Anchor(anchor.key, index, itemCount, start - offset, offset, current.orientation)
                                offset
                            } else {
                                null
                            }
                        }
                    }
                } else {
                    applyModel()
                    if (inPlace) {
                        bindings.values.toList().forEach { bind(it.item, it.host.index) }
                    } else {
                        releaseAll()
                        native.reloadData()
                    }
                }
                native.invalidateLayout()
            }

            val deferCorrection = isPhysicalScroll
            val revisionBeforeRestoring = geometry.revision
            if (deferCorrection) pendingAnchor = pendingAnchor?.copy(preserveViewportDelta = true)
            val restored =
                if (!deferCorrection) {
                    pendingAnchor?.let(::restoreAnchor).also { pendingAnchor = null }
                } else {
                    null
                }
            val measurementAnchor =
                restored ?: if (!deferCorrection && pendingScroll == null && !programmaticScrollInProgress) {
                    captureAnchor()
                } else {
                    null
                }

            var changed = geometry.revision != revisionBeforeRestoring
            var pass = 0
            while (pass++ < MAX_LAYOUT_PASSES) {
                val revision = geometry.revision
                val viewport = viewport
                var resolved = false
                itemRange(viewport.offset, viewport.offset + viewport.extent).forEach { index ->
                    resolved = resolveExtent(index) || resolved
                }
                if (resolved) native.invalidateLayout()
                native.layoutIfNeeded()
                val measured = measurePendingItems()
                geometryUnsettled = resolved || measured || geometry.revision != revision
                changed = geometryUnsettled || changed
                if (!geometryUnsettled) break
                native.invalidateLayout()
            }
            val revisionBeforeFinalLayout = geometry.revision
            native.layoutIfNeeded()
            changed = changed || geometry.revision != revisionBeforeFinalLayout
            if (changed && measurementAnchor != null) {
                restoreAnchor(measurementAnchor)
                native.layoutIfNeeded()
            }
            geometryUnsettled = geometryUnsettled || geometry.revision != revisionBeforeFinalLayout
            if (geometryUnsettled && measurementAnchor != null) pendingAnchor = measurementAnchor
            reportLayoutInfo()
        } finally {
            layingOut = false
        }
        interruptedScroll?.takeIf { it.isActive && pendingScroll === it }?.let(::performScroll)
        if (geometryUnsettled || nativeReloadPending || bindings.values.any { it.needsMeasurement }) scheduleLayout()
    }

    fun dispose() {
        disposed = true
        pendingScroll?.let { request ->
            cancelScroll(request)
            request.cancel()
        }
        try {
            releaseAll()
        } finally {
            try {
                coordinator.dispose()
            } finally {
                model = null
                pendingAnchor = null
                geometry.reset(0, 0.0, null)
                reuseIdentifiers.clear()
            }
        }
    }

    private fun releaseAll() {
        val items = bindings.values.toList()
        bindings.clear()
        var failure: Throwable? = null
        items.forEach { binding ->
            try {
                binding.host.dispose()
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun positionalSplice(provider: LazyItemProvider): LazyIndexSplice? {
        // Unretained items are bound from the current provider when requested. A positional
        // splice only needs to preserve every retained key; otherwise use the reload path.
        val delta = provider.itemCount - itemCount
        if (delta == 0 || bindings.isEmpty()) return null
        val shifted =
            bindings.values.filter { binding ->
                val index = binding.host.index
                index !in 0 until provider.itemCount || provider.key(index) != binding.host.key
            }
        val index = shifted.minOfOrNull { it.host.index + minOf(delta, 0) } ?: minOf(itemCount, provider.itemCount)
        if (index < 0) return null
        val splice = LazyIndexSplice(index, maxOf(-delta, 0), maxOf(delta, 0))
        return splice.takeIf {
            bindings.values.all { binding ->
                val nextIndex = splice.newIndex(binding.host.index)
                nextIndex in 0 until provider.itemCount && provider.key(nextIndex) == binding.host.key
            }
        }
    }

    private fun resolveExtent(index: Int): Boolean {
        val provider = checkNotNull(model).itemProvider
        return geometry.resolve(index, provider.key(index), provider.layoutVersion(index), provider.contentType(index)) != null
    }

    private fun measurePendingItems(): Boolean {
        val provider = checkNotNull(model).itemProvider
        var changed = false
        bindings.values.toList().forEach { binding ->
            val index = binding.host.index
            if (!binding.needsMeasurement || index !in 0 until itemCount) return@forEach
            binding.needsMeasurement = false
            val extent = binding.item.measure(checkNotNull(model).orientation, crossExtent)
            changed = geometry.record(
                index,
                checkNotNull(binding.host.key),
                provider.layoutVersion(index),
                provider.contentType(index),
                extent,
            ) != null || changed
        }
        return changed
    }

    private fun captureAnchor(): Anchor? {
        val current = model ?: return null
        val viewport = viewport
        val binding =
            bindings.values
                .filter { it.host.index in 0 until itemCount }
                .filter { geometry.itemStart(it.host.index) + geometry.itemExtent(it.host.index) > viewport.offset }
                .minByOrNull { geometry.itemStart(it.host.index) } ?: return null
        val index = binding.host.index
        return Anchor(
            checkNotNull(binding.host.key),
            index,
            itemCount,
            geometry.itemStart(index) - viewport.offset,
            viewport.offset,
            current.orientation,
        )
    }

    private fun restoreAnchor(anchor: Anchor): Anchor? {
        val current = checkNotNull(model)
        val index = current.itemProvider.findIndexByKey(anchor.key, anchor.index, anchor.itemCount)
        if (index !in 0 until itemCount) return null
        resolveExtent(index)
        val start = geometry.itemStart(index)
        val target =
            restoredLazyViewportOffset(
                start - anchor.offset,
                anchor.viewportOffset,
                viewport.offset,
                anchor.preserveViewportDelta && anchor.orientation == current.orientation,
            )
        native.invalidateLayout()
        native.layoutIfNeeded()
        scrollTo(target)
        return Anchor(anchor.key, index, itemCount, start - viewport.offset, viewport.offset, current.orientation)
    }

    private fun performScroll(request: LazyListScrollRequest) {
        programmaticScrollInProgress = true
        try {
            layout()
            if (request.index !in 0 until itemCount) {
                request.cancel()
                return
            }
            // An explicit scroll target replaces the old viewport anchor. Deferred measurements
            // must not restore that anchor and cancel the animation we are about to start.
            pendingAnchor = null
            resolveExtent(request.index)
            native.invalidateLayout()
            native.layoutIfNeeded()
            val target = clampedOffset(geometry.itemStart(request.index) + request.scrollOffset)
            if (request.animated && needsAdaptiveLazyScrollCorrection(viewport.offset, target)) {
                pendingScroll = request
                val generation = ++animationGeneration
                coordinator.reportScrollInProgress(true)
                native.scrollTo(target, animated = true) {
                    if (pendingScroll === request && generation == animationGeneration) finishScroll(request)
                }
            } else {
                scrollTo(target)
                finishScroll(request)
            }
        } finally {
            programmaticScrollInProgress = false
        }
    }

    private fun finishScroll(request: LazyListScrollRequest) {
        programmaticScrollInProgress = true
        try {
            repeat(MAX_SCROLL_CORRECTIONS) {
                if (!request.isActive || request.index !in 0 until itemCount) return@repeat
                layout()
                resolveExtent(request.index)
                scrollTo(geometry.itemStart(request.index) + request.scrollOffset)
            }
            layout()
            if (pendingScroll === request) pendingScroll = null
            if (request.isActive && request.index in 0 until itemCount) request.complete() else request.cancel()
            coordinator.reportScrollInProgress(pendingScroll != null || isPhysicalScroll)
        } finally {
            programmaticScrollInProgress = false
        }
    }

    private fun cancelScroll(request: LazyListScrollRequest) {
        if (pendingScroll !== request) return
        pendingScroll = null
        animationGeneration++
        native.stopAnimatedScroll()
        coordinator.reportScrollInProgress(isPhysicalScroll)
    }

    private fun clampedOffset(offset: Double): Double = offset.coerceIn(0.0, max(0.0, geometry.contentExtent - viewport.extent))

    private fun scrollTo(offset: Double) {
        val target = clampedOffset(offset)
        if (needsAdaptiveLazyScrollCorrection(viewport.offset, target)) native.scrollTo(target, animated = false) {}
    }

    private fun reportLayoutInfo() {
        val viewport = viewport
        val visible =
            bindings.values
                .mapNotNull { binding ->
                    val index = binding.host.index
                    if (index !in 0 until itemCount) return@mapNotNull null
                    val start = geometry.itemStart(index)
                    val extent = geometry.itemExtent(index)
                    if (start + extent <= viewport.offset || start >= viewport.offset + viewport.extent) return@mapNotNull null
                    LazyListItemInfo(checkNotNull(binding.host.key), index, (start - viewport.offset).toFloat(), extent.toFloat())
                }.sortedBy { it.index }
        coordinator.reportLayoutInfo(LazyListLayoutInfo(itemCount, 0f, viewport.extent.toFloat(), visible))
    }
}

private class Binding(
    val item: NativeLazyItem,
) {
    lateinit var host: LazyItemHost
    var needsMeasurement = true
}

/** Native cells carry positions; only realized stable keys need preservation during this splice. */
internal data class LazyIndexSplice(
    val index: Int,
    val removed: Int,
    val inserted: Int,
) {
    fun newIndex(oldIndex: Int): Int = if (oldIndex < index) oldIndex else oldIndex + inserted - removed
}

/** Native updates only locate retained cells. Find the data boundary separately for cached prefixes. */
private fun geometrySplice(
    previous: LazyItemProvider,
    current: LazyItemProvider,
    nativeSplice: LazyIndexSplice,
): LazyIndexSplice? {
    var low = 0
    var high = nativeSplice.index
    // For a single insert/delete, equal keys form a prefix. Query its boundary rather than
    // walking cached history. Arbitrary edits remain provisional and are validated by key on use.
    while (low < high) {
        val middle = low + (high - low) / 2
        if (previous.key(middle) == current.key(middle)) low = middle + 1 else high = middle
    }
    val splice = nativeSplice.copy(index = low)

    fun matches(index: Int): Boolean {
        if (index !in 0 until previous.itemCount || index in splice.index until splice.index + splice.removed) return true
        val next = splice.newIndex(index)
        return next in 0 until current.itemCount && previous.key(index) == current.key(next)
    }
    return splice.takeIf { matches(low - 1) && matches(low + splice.removed) && matches(previous.itemCount - 1) }
}

private data class ExtentEnvironment(
    val orientation: LazyListOrientation,
    val crossExtent: Double,
    val alignment: LazyCrossAxisAlignment,
    val subcompositions: Any,
)

private data class Anchor(
    val key: Any,
    val index: Int,
    val itemCount: Int,
    val offset: Double,
    val viewportOffset: Double,
    val orientation: LazyListOrientation,
    val preserveViewportDelta: Boolean = false,
)

private const val MAX_LAYOUT_PASSES = 3
private const val MAX_SCROLL_CORRECTIONS = 4
