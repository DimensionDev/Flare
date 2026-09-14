@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import dev.dimension.flare.ui.FlareChildren
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.max
import kotlin.math.round

/** Native collections own realization and reuse; this adapter supplies only platform operations. */
internal interface NativeLazyCollection {
    fun viewport(orientation: LazyListOrientation): NativeLazyViewport

    val isPhysicalScrollInProgress: Boolean

    fun reloadData()

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
    val children: FlareChildren

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
        if (!modelResetPending) pendingAnchor = captureAnchor() ?: pendingAnchor
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
        return reuseIdentifiers.getOrPut(contentType) { "FlareLazyItem-${reuseIdentifiers.size}" }
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
                val inPlace =
                    !nativeReloadPending && model != null && geometry.itemCount == current.itemProvider.itemCount &&
                        bindings.values.all { binding ->
                            val index = binding.host.index
                            index in 0 until current.itemProvider.itemCount && current.itemProvider.key(index) == binding.host.key
                        }
                if (!modelResetPending) pendingAnchor = captureAnchor() ?: pendingAnchor
                modelResetPending = false
                nativeReloadPending = false
                model = current
                environment = nextEnvironment
                geometry.reset(current.itemProvider.itemCount, current.spacing.toDouble(), nextEnvironment)
                if (inPlace) {
                    bindings.values.toList().forEach { bind(it.item, it.host.index) }
                } else {
                    releaseAll()
                    native.reloadData()
                }
                native.invalidateLayout()
            }

            val deferCorrection = isPhysicalScroll
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

            var changed = false
            var pass = 0
            while (pass++ < MAX_LAYOUT_PASSES) {
                val viewport = viewport
                var resolved = false
                itemRange(viewport.offset, viewport.offset + viewport.extent).forEach { index ->
                    resolved = resolveExtent(index) || resolved
                }
                if (resolved) native.invalidateLayout()
                native.layoutIfNeeded()
                val measured = measurePendingItems()
                changed = resolved || measured || changed
                if (!measured) break
                native.invalidateLayout()
            }
            native.layoutIfNeeded()
            if (changed && measurementAnchor != null) {
                restoreAnchor(measurementAnchor)
                native.layoutIfNeeded()
            }
            reportLayoutInfo()
        } finally {
            layingOut = false
        }
        if (nativeReloadPending || bindings.values.any { it.needsMeasurement }) scheduleLayout()
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
            coordinator.dispose()
            model = null
            reuseIdentifiers.clear()
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
            resolveExtent(request.index)
            native.invalidateLayout()
            native.layoutIfNeeded()
            val target = clampedOffset(geometry.itemStart(request.index) + request.scrollOffset)
            if (request.animated && needsAdaptiveLazyScrollCorrection(viewport.offset, target)) {
                pendingScroll = request
                coordinator.reportScrollInProgress(true)
                native.scrollTo(target, animated = true) {
                    if (pendingScroll === request) finishScroll(request)
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
