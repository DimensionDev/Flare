@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.lazy.InvalidatingLazyItemChildren
import dev.dimension.flare.ui.lazy.LazyCollectionCoordinator
import dev.dimension.flare.ui.lazy.LazyCollectionModel
import dev.dimension.flare.ui.lazy.LazyCollectionWidget
import dev.dimension.flare.ui.lazy.LazyCrossAxisAlignment
import dev.dimension.flare.ui.lazy.LazyItemHost
import dev.dimension.flare.ui.lazy.LazyItemReusePool
import dev.dimension.flare.ui.lazy.LazyListItemInfo
import dev.dimension.flare.ui.lazy.LazyListLayoutInfo
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.LazyListScrollRequest
import dev.dimension.flare.ui.lazy.LazyRealizedItemUpdate
import dev.dimension.flare.ui.lazy.VariableExtentLayoutState
import dev.dimension.flare.ui.lazy.findIndexByKey
import dev.dimension.flare.ui.lazy.needsAdaptiveLazyScrollCorrection
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UILayoutFittingCompressedSize
import platform.UIKit.UILayoutPriorityFittingSizeLevel
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentBottom
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFill
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIStackViewAlignmentTrailing
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** Variable-extent UIKit renderer which keeps native scrolling but owns linear virtualization. */
internal class UIKitAdaptiveLazyCollectionWidget :
    AbstractUIKitWidget<UIKitAdaptiveLazyScrollView>(UIKitAdaptiveLazyScrollView()),
    LazyCollectionWidget {
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = ::applyModel,
            onScroll = ::performScroll,
            onScrollCancelled = ::cancelScroll,
            uiDispatcher = Dispatchers.Main.immediate,
        )
    private val bridge = UIKitAdaptiveLazyBridge(view, coordinator)
    private var pendingAnchor: UIKitAdaptiveAnchor? = null

    init {
        view.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever
        view.delegate = bridge
        view.onLayout = bridge::scheduleLayout
    }

    override fun setModel(model: LazyCollectionModel) {
        pendingAnchor = coordinator.model?.let(bridge::captureAnchor)
        try {
            coordinator.setModel(model)
        } finally {
            pendingAnchor = null
        }
    }

    override fun dispose() {
        view.onLayout = null
        view.delegate = null
        var failure: Throwable? = null
        try {
            bridge.dispose()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            coordinator.dispose()
        } catch (error: Throwable) {
            if (failure == null) failure = error
        }
        failure?.let { throw it }
    }

    private fun applyModel(
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ): LazyRealizedItemUpdate {
        val vertical = current.orientation == LazyListOrientation.Vertical
        view.alwaysBounceVertical = vertical
        view.alwaysBounceHorizontal = !vertical
        view.showsVerticalScrollIndicator = vertical
        view.showsHorizontalScrollIndicator = !vertical
        bridge.setModel(current, pendingAnchor)
        return LazyRealizedItemUpdate.RendererManaged
    }

    private fun performScroll(request: LazyListScrollRequest) {
        bridge.performScroll(request)
    }

    private fun cancelScroll(request: LazyListScrollRequest) {
        bridge.cancelScroll(request)
    }
}

private class UIKitAdaptiveLazyBridge(
    private val scrollView: UIKitAdaptiveLazyScrollView,
    private val coordinator: LazyCollectionCoordinator,
) : NSObject(),
    UIScrollViewDelegateProtocol {
    private val geometry = VariableExtentLayoutState()
    private val realized = mutableMapOf<Int, UIKitAdaptiveItemBinding>()
    private val allBindings = mutableSetOf<UIKitAdaptiveItemBinding>()
    private val pooled =
        LazyItemReusePool<UIKitAdaptiveItemBinding>(MIN_RETAINED_BINDINGS) { binding ->
            allBindings.remove(binding)
            binding.dispose()
        }
    private var environment: UIKitExtentEnvironment? = null
    private var layoutScheduled: Boolean = false
    private var layingOut: Boolean = false
    private var disposed: Boolean = false
    private var pendingAnchor: UIKitAdaptiveAnchor? = null
    private var pendingScroll: LazyListScrollRequest? = null
    private var modelResetPending: Boolean = false
    private var programmaticScrollInProgress: Boolean = false
    private var physicalScrollInProgress: Boolean = false

    override fun scrollViewDidScroll(scrollView: UIScrollView) {
        layoutVisibleItems()
    }

    override fun scrollViewWillBeginDragging(scrollView: UIScrollView) {
        physicalScrollInProgress = true
        cancelPendingScroll()
        coordinator.reportScrollInProgress(true)
    }

    override fun scrollViewDidEndDecelerating(scrollView: UIScrollView) {
        finishPhysicalScroll()
    }

    override fun scrollViewDidEndDragging(
        scrollView: UIScrollView,
        willDecelerate: Boolean,
    ) {
        if (!willDecelerate) finishPhysicalScroll()
    }

    override fun scrollViewDidEndScrollingAnimation(scrollView: UIScrollView) {
        val request = pendingScroll ?: return
        val settled = settleScrollRequest(request)
        if (pendingScroll === request) pendingScroll = null
        if (settled) {
            request.complete()
        } else {
            request.cancel()
        }
        coordinator.reportScrollInProgress(false)
    }

    fun setModel(
        model: LazyCollectionModel,
        anchor: UIKitAdaptiveAnchor?,
    ) {
        // A second model can arrive before the scheduled layout has rebuilt any bindings. Keep the
        // last real viewport anchor instead of replacing it with the resulting null capture.
        pendingAnchor =
            anchor
                ?.let { candidate ->
                    if (isPhysicalScrollInProgress()) {
                        candidate.copy(preserveViewportDelta = true)
                    } else {
                        candidate
                    }
                } ?: pendingAnchor
        modelResetPending = true
        scheduleLayout()
    }

    fun captureAnchor(model: LazyCollectionModel): UIKitAdaptiveAnchor? {
        val viewportStart = scrollView.mainAxisOffset(model.orientation)
        var binding: UIKitAdaptiveItemBinding? = null
        var bindingStart = Double.POSITIVE_INFINITY
        realized.values.forEach { candidate ->
            if (candidate.index !in 0 until model.itemProvider.itemCount) return@forEach
            val start = geometry.itemStart(candidate.index)
            if (start + geometry.itemExtent(candidate.index) > viewportStart && start < bindingStart) {
                binding = candidate
                bindingStart = start
            }
        }
        val anchorBinding = binding ?: return null
        val key = anchorBinding.key ?: model.itemProvider.key(anchorBinding.index)
        return UIKitAdaptiveAnchor(
            key = key,
            index = anchorBinding.index,
            itemCount = model.itemProvider.itemCount,
            offset = bindingStart - viewportStart,
            viewportOffset = viewportStart,
            orientation = model.orientation,
        )
    }

    fun scheduleLayout() {
        if (disposed || layoutScheduled || layingOut) return
        layoutScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            layoutScheduled = false
            if (!disposed) layoutVisibleItems()
        }
    }

    fun performScroll(request: LazyListScrollRequest) {
        val model =
            coordinator.model ?: run {
                request.cancel()
                return
            }
        if (modelResetPending) layoutVisibleItems()
        if (request.index !in 0 until model.itemProvider.itemCount) {
            request.cancel()
            return
        }
        resolveExtent(model, request.index)
        val target = geometry.itemStart(request.index) + request.scrollOffset
        if (request.animated) {
            cancelPendingScroll(stopAnimation = true)
            pendingScroll = request
            coordinator.reportScrollInProgress(true)
            scrollView.setContentOffset(model.mainAxisPoint(target), animated = true)
        } else {
            programmaticScrollInProgress = true
            try {
                scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
                if (!settleScrollRequest(request)) {
                    request.cancel()
                    return
                }
            } finally {
                programmaticScrollInProgress = false
            }
            request.complete()
        }
    }

    fun cancelScroll(request: LazyListScrollRequest) {
        if (pendingScroll !== request) return
        pendingScroll = null
        scrollView.setContentOffset(scrollView.contentOffset, animated = false)
        coordinator.reportScrollInProgress(false)
    }

    fun dispose() {
        disposed = true
        pendingScroll?.cancel()
        pendingScroll = null
        var failure: Throwable? = null
        try {
            pooled.clear()
        } catch (error: Throwable) {
            failure = error
        }
        val remainingBindings = allBindings.toList()
        allBindings.clear()
        realized.clear()
        remainingBindings.forEach { binding ->
            try {
                binding.dispose()
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun layoutVisibleItems() {
        val model = coordinator.model ?: return
        if (disposed || layingOut) return
        layingOut = true
        try {
            val environmentAnchor =
                if (modelResetPending) {
                    modelResetPending = false
                    if (canApplyModelInPlace(model)) {
                        resetGeometry(model)
                        rebindRealized(model)
                    } else {
                        recycleAll()
                        resetGeometry(model)
                    }
                    null
                } else {
                    ensureEnvironment(model)
                }
            val deferOffsetCorrection = isPhysicalScrollInProgress()
            if (deferOffsetCorrection) {
                if (pendingAnchor == null) {
                    pendingAnchor = environmentAnchor?.copy(preserveViewportDelta = true)
                } else if (pendingAnchor?.preserveViewportDelta == false) {
                    pendingAnchor = pendingAnchor?.copy(preserveViewportDelta = true)
                }
            }
            val modelAnchor = if (deferOffsetCorrection) null else pendingAnchor ?: environmentAnchor
            val restoredModelAnchor =
                modelAnchor?.let { anchor ->
                    val restored = restoreAnchor(model, anchor)
                    pendingAnchor = null
                    restored
                }
            val measurementAnchor =
                restoredModelAnchor ?: if (!deferOffsetCorrection && pendingScroll == null && !programmaticScrollInProgress) {
                    captureAnchor(model)
                } else {
                    null
                }
            var geometryChanged = false
            var pass = 0
            while (pass < MAX_LAYOUT_PASSES) {
                val viewportStart = scrollView.mainAxisOffset(model.orientation)
                val viewportSize = scrollView.mainAxisViewport(model.orientation)
                val desired =
                    geometry.visibleRange(
                        viewportStart = viewportStart,
                        viewportEnd = viewportStart + viewportSize,
                        overscan = viewportSize * OVERSCAN_VIEWPORTS,
                    )
                desired.forEach { index ->
                    geometryChanged = resolveExtent(model, index) || geometryChanged
                }
                reconcileBindings(model, desired)
                placeRealized(model)
                val measured = measurePendingBindings(model)
                geometryChanged = measured || geometryChanged
                updateContentSize(model)
                if (!measured) break
                pass += 1
            }
            placeRealized(model)
            updateContentSize(model)
            if (geometryChanged && measurementAnchor != null) {
                restoreAnchor(model, measurementAnchor)
                placeRealized(model)
            }
            reportLayoutInfo(model)
        } finally {
            layingOut = false
        }
    }

    private fun ensureEnvironment(model: LazyCollectionModel): UIKitAdaptiveAnchor? {
        val next = UIKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        if (environment == next) return null
        val anchor = captureAnchor(model)
        environment = next
        recycleAll()
        geometry.reset(model.itemProvider.itemCount, model.spacing.toDouble(), next)
        return anchor
    }

    private fun resetGeometry(model: LazyCollectionModel) {
        val next = UIKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        environment = next
        geometry.reset(model.itemProvider.itemCount, model.spacing.toDouble(), next)
    }

    private fun canApplyModelInPlace(model: LazyCollectionModel): Boolean {
        val next = UIKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        if (environment != next || geometry.itemCount != model.itemProvider.itemCount) return false
        if (geometry.spacing != model.spacing.toDouble()) return false
        val provider = model.itemProvider
        return realized.all { (index, binding) ->
            index in 0 until provider.itemCount && provider.key(index) == binding.key
        }
    }

    private fun rebindRealized(model: LazyCollectionModel) {
        val provider = model.itemProvider
        realized.forEach { (index, binding) ->
            val previous = binding.boundModel
            val contentType = provider.contentType(index)
            val measurementCompatible =
                previous != null &&
                    previous.orientation == model.orientation &&
                    previous.crossAxisAlignment == model.crossAxisAlignment &&
                    previous.subcompositions === model.subcompositions &&
                    previous.itemProvider.contentType(index) == contentType &&
                    previous.itemProvider.layoutVersion(index) == provider.layoutVersion(index)
            binding.needsMeasurement = binding.needsMeasurement || !measurementCompatible
            binding.bind(model, index, contentType)
        }
    }

    private fun resolveExtent(
        model: LazyCollectionModel,
        index: Int,
    ): Boolean {
        val provider = model.itemProvider
        return geometry.resolve(
            index = index,
            key = provider.key(index),
            layoutVersion = provider.layoutVersion(index),
            contentType = provider.contentType(index),
        ) != null
    }

    private fun reconcileBindings(
        model: LazyCollectionModel,
        desired: IntRange,
    ) {
        pooled.resize(maxOf(MIN_RETAINED_BINDINGS, desired.count()))
        realized.keys.toList().forEach { index ->
            if (index !in desired) recycle(index)
        }
        desired.forEach { index ->
            if (index in realized) return@forEach
            val provider = model.itemProvider
            val key = provider.key(index)
            val layoutVersion = provider.layoutVersion(index)
            val contentType = provider.contentType(index)
            val binding = takeBinding(contentType, key)
            val hasExactMeasurement = geometry.hasExactMeasurement(key, layoutVersion)
            binding.needsMeasurement = binding.needsMeasurement || !hasExactMeasurement
            binding.root.onExtentInvalidated = {
                if (!binding.suppressExtentInvalidation) {
                    binding.needsMeasurement = true
                    if (realized[binding.index] === binding) scheduleLayout()
                }
            }
            binding.suppressExtentInvalidation = hasExactMeasurement && binding.boundModel === model
            try {
                binding.bind(model, index, contentType)
            } finally {
                binding.suppressExtentInvalidation = false
            }
            realized[index] = binding
            scrollView.addSubview(binding.root)
        }
    }

    private fun takeBinding(
        contentType: Any?,
        key: Any,
    ): UIKitAdaptiveItemBinding {
        val typeKey = contentType.cacheKey()
        pooled.take(typeKey, key)?.let { return it }
        val root = UIKitAdaptiveItemStackView()
        return UIKitAdaptiveItemBinding(
            root = root,
            itemHost =
                coordinator.createItemHost(
                    InvalidatingLazyItemChildren(UIKitChildren(root), root::invalidateExtent),
                ),
        ).also(allBindings::add)
    }

    private fun recycle(index: Int) {
        val binding = realized.remove(index) ?: return
        binding.root.removeFromSuperview()
        val key = binding.key
        if (key == null) {
            allBindings.remove(binding)
            binding.dispose()
        } else {
            pooled.put(binding.contentType.cacheKey(), key, binding)
        }
    }

    private fun recycleAll() {
        realized.keys.toList().forEach(::recycle)
    }

    private fun placeRealized(model: LazyCollectionModel) {
        val crossExtent = scrollView.crossAxisExtent(model.orientation)
        realized.forEach { (index, binding) ->
            val start = geometry.itemStart(index)
            val extent = geometry.itemExtent(index)
            binding.root.setFrame(model.itemFrame(start, extent, crossExtent))
        }
    }

    private fun measurePendingBindings(model: LazyCollectionModel): Boolean {
        var changed = false
        realized.values.forEach { binding ->
            if (!binding.needsMeasurement) return@forEach
            binding.needsMeasurement = false
            binding.root.layoutIfNeeded()
            val extent = binding.root.measuredExtent(model.orientation, scrollView.crossAxisExtent(model.orientation))
            val provider = model.itemProvider
            val index = binding.index
            if (index !in 0 until provider.itemCount || provider.key(index) != binding.key) return@forEach
            changed =
                geometry.record(
                    index = index,
                    key = checkNotNull(binding.key),
                    layoutVersion = provider.layoutVersion(index),
                    contentType = binding.contentType,
                    extent = extent,
                ) != null || changed
        }
        return changed
    }

    private fun updateContentSize(model: LazyCollectionModel) {
        val bounds = scrollView.bounds.useContents { size.width to size.height }
        val next =
            when (model.orientation) {
                LazyListOrientation.Vertical -> CGSizeMake(bounds.first, max(bounds.second, geometry.contentExtent))
                LazyListOrientation.Horizontal -> CGSizeMake(max(bounds.first, geometry.contentExtent), bounds.second)
            }
        val nextDimensions = next.useContents { width to height }
        val changed =
            scrollView.contentSize.useContents {
                abs(width - nextDimensions.first) > CONTENT_SIZE_TOLERANCE ||
                    abs(height - nextDimensions.second) > CONTENT_SIZE_TOLERANCE
            }
        if (changed) scrollView.setContentSize(next)
    }

    private fun restoreAnchor(
        model: LazyCollectionModel,
        anchor: UIKitAdaptiveAnchor,
    ): UIKitAdaptiveAnchor? {
        val index =
            model.itemProvider.findIndexByKey(
                key = anchor.key,
                expectedIndex = anchor.index,
                previousItemCount = anchor.itemCount,
            )
        if (index !in 0 until model.itemProvider.itemCount) return null
        resolveExtent(model, index)
        val itemStart = geometry.itemStart(index)
        val currentViewportOffset = scrollView.mainAxisOffset(model.orientation)
        val target =
            restoredUIKitLazyViewportOffset(
                anchorTargetAtCapture = itemStart - anchor.offset,
                capturedViewportOffset = anchor.viewportOffset,
                currentViewportOffset = currentViewportOffset,
                preserveViewportDelta = anchor.preserveViewportDelta && anchor.orientation == model.orientation,
            )
        if (needsAdaptiveLazyScrollCorrection(currentViewportOffset, target)) {
            scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
        }
        val restoredViewportOffset = scrollView.mainAxisOffset(model.orientation)
        return UIKitAdaptiveAnchor(
            key = anchor.key,
            index = index,
            itemCount = model.itemProvider.itemCount,
            offset = itemStart - restoredViewportOffset,
            viewportOffset = restoredViewportOffset,
            orientation = model.orientation,
        )
    }

    private fun settleScrollRequest(request: LazyListScrollRequest): Boolean {
        val model = coordinator.model ?: return false
        var pass = 0
        while (pass < MAX_PROGRAMMATIC_SCROLL_CORRECTIONS) {
            if (!request.isActive || request.index !in 0 until model.itemProvider.itemCount) return false
            layoutVisibleItems()
            resolveExtent(model, request.index)
            val target = geometry.itemStart(request.index) + request.scrollOffset
            val current = scrollView.mainAxisOffset(model.orientation)
            if (!needsAdaptiveLazyScrollCorrection(current, target)) break
            scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
            pass += 1
        }
        reportLayoutInfo(model)
        return request.isActive && request.index in 0 until model.itemProvider.itemCount
    }

    private fun cancelPendingScroll(stopAnimation: Boolean = false) {
        val request = pendingScroll ?: return
        pendingScroll = null
        if (stopAnimation) {
            scrollView.setContentOffset(scrollView.contentOffset, animated = false)
        }
        request.cancel()
    }

    private fun finishPhysicalScroll() {
        physicalScrollInProgress = false
        coordinator.reportScrollInProgress(false)
        layoutVisibleItems()
    }

    private fun isPhysicalScrollInProgress(): Boolean =
        physicalScrollInProgress ||
            shouldDeferUIKitLazyOffsetCorrection(
                isTracking = scrollView.tracking,
                isDragging = scrollView.dragging,
                isDecelerating = scrollView.decelerating,
            )

    private fun reportLayoutInfo(model: LazyCollectionModel) {
        val viewportStart = scrollView.mainAxisOffset(model.orientation)
        val viewportEnd = viewportStart + scrollView.mainAxisViewport(model.orientation)
        val visible =
            realized.values
                .filter { binding ->
                    val start = geometry.itemStart(binding.index)
                    val end = start + geometry.itemExtent(binding.index)
                    end > viewportStart && start < viewportEnd
                }.sortedBy(UIKitAdaptiveItemBinding::index)
                .map { binding ->
                    val start = geometry.itemStart(binding.index)
                    LazyListItemInfo(
                        key = checkNotNull(binding.key),
                        index = binding.index,
                        offset = (start - viewportStart).toFloat(),
                        size = geometry.itemExtent(binding.index).toFloat(),
                    )
                }
        coordinator.reportLayoutInfo(
            LazyListLayoutInfo(
                totalItemsCount = model.itemProvider.itemCount,
                viewportStartOffset = 0f,
                viewportEndOffset = scrollView.mainAxisViewport(model.orientation).toFloat(),
                visibleItems = visible,
            ),
        )
    }
}

private class UIKitAdaptiveItemBinding(
    val root: UIKitAdaptiveItemStackView,
    private val itemHost: LazyItemHost,
) {
    var index: Int = -1
        private set
    var contentType: Any? = null
        private set
    var needsMeasurement: Boolean = true
    var suppressExtentInvalidation: Boolean = false
    var boundModel: LazyCollectionModel? = null
        private set

    val key: Any?
        get() = itemHost.key

    fun bind(
        model: LazyCollectionModel,
        index: Int,
        contentType: Any?,
    ) {
        root.configure(model)
        itemHost.bind(index)
        this.index = index
        this.contentType = contentType
        boundModel = model
    }

    fun dispose() {
        root.onExtentInvalidated = null
        itemHost.dispose()
        root.removeFromSuperview()
    }
}

internal class UIKitAdaptiveLazyScrollView : UIScrollView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onLayout: (() -> Unit)? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        onLayout?.invoke()
    }
}

private class UIKitAdaptiveItemStackView : UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onExtentInvalidated: (() -> Unit)? = null
    private var lazyOrientation: LazyListOrientation = LazyListOrientation.Vertical

    init {
        translatesAutoresizingMaskIntoConstraints = true
    }

    fun configure(model: LazyCollectionModel) {
        val nextAxis =
            when (model.orientation) {
                LazyListOrientation.Vertical -> UILayoutConstraintAxisVertical
                LazyListOrientation.Horizontal -> UILayoutConstraintAxisHorizontal
            }
        val nextAlignment =
            when (model.orientation) {
                LazyListOrientation.Vertical -> model.crossAxisAlignment.horizontalAlignment()
                LazyListOrientation.Horizontal -> model.crossAxisAlignment.verticalAlignment()
            }
        if (lazyOrientation == model.orientation && axis == nextAxis && alignment == nextAlignment) return
        lazyOrientation = model.orientation
        axis = nextAxis
        alignment = nextAlignment
    }

    fun invalidateExtent() {
        setNeedsLayout()
        onExtentInvalidated?.invoke()
    }

    fun measuredExtent(
        orientation: LazyListOrientation,
        crossAxisExtent: Double,
    ): Double {
        val target =
            when (orientation) {
                LazyListOrientation.Vertical -> CGSizeMake(crossAxisExtent, UILayoutFittingCompressedSize.height)
                LazyListOrientation.Horizontal -> CGSizeMake(UILayoutFittingCompressedSize.width, crossAxisExtent)
            }
        return systemLayoutSizeFittingSize(
            targetSize = target,
            withHorizontalFittingPriority =
                if (orientation == LazyListOrientation.Vertical) UILayoutPriorityRequired else UILayoutPriorityFittingSizeLevel,
            verticalFittingPriority =
                if (orientation == LazyListOrientation.Horizontal) UILayoutPriorityRequired else UILayoutPriorityFittingSizeLevel,
        ).useContents {
            when (orientation) {
                LazyListOrientation.Vertical -> height
                LazyListOrientation.Horizontal -> width
            }
        }
    }
}

private data class UIKitExtentEnvironment(
    val orientation: LazyListOrientation,
    val crossAxisExtent: Double,
)

private data class UIKitAdaptiveAnchor(
    val key: Any,
    val index: Int,
    val itemCount: Int,
    val offset: Double,
    val viewportOffset: Double,
    val orientation: LazyListOrientation,
    val preserveViewportDelta: Boolean = false,
)

private fun LazyCollectionModel.itemFrame(
    start: Double,
    extent: Double,
    crossExtent: Double,
) = when (orientation) {
    LazyListOrientation.Vertical -> CGRectMake(0.0, start, crossExtent, extent)
    LazyListOrientation.Horizontal -> CGRectMake(start, 0.0, extent, crossExtent)
}

private fun LazyCollectionModel.mainAxisPoint(offset: Double) =
    when (orientation) {
        LazyListOrientation.Vertical -> CGPointMake(0.0, offset)
        LazyListOrientation.Horizontal -> CGPointMake(offset, 0.0)
    }

private fun UIScrollView.mainAxisOffset(orientation: LazyListOrientation): Double =
    contentOffset.useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> y
            LazyListOrientation.Horizontal -> x
        }
    }

private fun UIScrollView.mainAxisViewport(orientation: LazyListOrientation): Double =
    bounds.useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> size.height
            LazyListOrientation.Horizontal -> size.width
        }
    }

private fun UIScrollView.crossAxisExtent(orientation: LazyListOrientation): Double =
    bounds
        .useContents {
            when (orientation) {
                LazyListOrientation.Vertical -> size.width
                LazyListOrientation.Horizontal -> size.height
            }
        }.coerceAtLeast(1.0)

private fun LazyCrossAxisAlignment.horizontalAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start -> UIStackViewAlignmentLeading
        LazyCrossAxisAlignment.Center -> UIStackViewAlignmentCenter
        LazyCrossAxisAlignment.End -> UIStackViewAlignmentTrailing
        LazyCrossAxisAlignment.Stretch -> UIStackViewAlignmentFill
    }

private fun LazyCrossAxisAlignment.verticalAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start -> UIStackViewAlignmentTop
        LazyCrossAxisAlignment.Center -> UIStackViewAlignmentCenter
        LazyCrossAxisAlignment.End -> UIStackViewAlignmentBottom
        LazyCrossAxisAlignment.Stretch -> UIStackViewAlignmentFill
    }

private fun Any?.cacheKey(): Any = this ?: UIKitNullContentType

private data object UIKitNullContentType

internal fun shouldDeferUIKitLazyOffsetCorrection(
    isTracking: Boolean,
    isDragging: Boolean,
    isDecelerating: Boolean,
): Boolean = isTracking || isDragging || isDecelerating

internal fun restoredUIKitLazyViewportOffset(
    anchorTargetAtCapture: Double,
    capturedViewportOffset: Double,
    currentViewportOffset: Double,
    preserveViewportDelta: Boolean,
): Double =
    if (preserveViewportDelta) {
        anchorTargetAtCapture + (currentViewportOffset - capturedViewportOffset)
    } else {
        anchorTargetAtCapture
    }

private const val OVERSCAN_VIEWPORTS = 0.5
private const val MAX_LAYOUT_PASSES = 2
private const val MAX_PROGRAMMATIC_SCROLL_CORRECTIONS = 3
private const val MIN_RETAINED_BINDINGS = 32
private const val CONTENT_SIZE_TOLERANCE = 0.5
