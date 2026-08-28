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
import dev.dimension.flare.ui.lazy.LazyListItemInfo
import dev.dimension.flare.ui.lazy.LazyListLayoutInfo
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.LazyListScrollRequest
import dev.dimension.flare.ui.lazy.LazyRealizedItemUpdate
import dev.dimension.flare.ui.lazy.VariableExtentLayoutState
import kotlinx.cinterop.useContents
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
        bridge.dispose()
        coordinator.dispose()
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
}

private class UIKitAdaptiveLazyBridge(
    private val scrollView: UIKitAdaptiveLazyScrollView,
    private val coordinator: LazyCollectionCoordinator,
) : NSObject(),
    UIScrollViewDelegateProtocol {
    private val geometry = VariableExtentLayoutState()
    private val realized = mutableMapOf<Int, UIKitAdaptiveItemBinding>()
    private val pooled = mutableMapOf<Any, MutableList<UIKitAdaptiveItemBinding>>()
    private val allBindings = mutableSetOf<UIKitAdaptiveItemBinding>()
    private var environment: UIKitExtentEnvironment? = null
    private var layoutScheduled: Boolean = false
    private var layingOut: Boolean = false
    private var disposed: Boolean = false
    private var pendingAnchor: UIKitAdaptiveAnchor? = null
    private var pendingScroll: LazyListScrollRequest? = null
    private var modelResetPending: Boolean = false
    private var programmaticScrollInProgress: Boolean = false

    override fun scrollViewDidScroll(scrollView: UIScrollView) {
        layoutVisibleItems()
    }

    override fun scrollViewWillBeginDragging(scrollView: UIScrollView) {
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
        val request = pendingScroll
        request?.let(::settleScrollRequest)
        pendingScroll = null
        request?.complete()
        coordinator.reportScrollInProgress(false)
    }

    fun setModel(
        model: LazyCollectionModel,
        anchor: UIKitAdaptiveAnchor?,
    ) {
        // A second model can arrive before the scheduled layout has rebuilt any bindings. Keep the
        // last real viewport anchor instead of replacing it with the resulting null capture.
        pendingAnchor = anchor ?: pendingAnchor
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
        return UIKitAdaptiveAnchor(key, anchorBinding.index, bindingStart - viewportStart)
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
            pendingScroll?.cancel()
            pendingScroll = request
            coordinator.reportScrollInProgress(true)
            scrollView.setContentOffset(model.mainAxisPoint(target), animated = true)
        } else {
            programmaticScrollInProgress = true
            try {
                scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
                settleScrollRequest(request)
            } finally {
                programmaticScrollInProgress = false
            }
            request.complete()
        }
    }

    fun dispose() {
        disposed = true
        pendingScroll?.cancel()
        pendingScroll = null
        allBindings.toList().forEach(UIKitAdaptiveItemBinding::dispose)
        allBindings.clear()
        realized.clear()
        pooled.clear()
    }

    private fun layoutVisibleItems() {
        val model = coordinator.model ?: return
        if (disposed || layingOut) return
        layingOut = true
        try {
            if (modelResetPending) {
                modelResetPending = false
                recycleAll()
                resetGeometry(model)
            } else {
                ensureEnvironment(model)
            }
            val deferOffsetCorrection =
                shouldDeferUIKitLazyOffsetCorrection(
                    isTracking = scrollView.tracking,
                    isDragging = scrollView.dragging,
                    isDecelerating = scrollView.decelerating,
                )
            val modelAnchor = if (deferOffsetCorrection) null else pendingAnchor
            modelAnchor?.let { anchor ->
                restoreAnchor(model, anchor)
                pendingAnchor = null
            }
            val measurementAnchor =
                modelAnchor ?: if (!deferOffsetCorrection && pendingScroll == null && !programmaticScrollInProgress) {
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

    private fun ensureEnvironment(model: LazyCollectionModel) {
        val next = UIKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        if (environment == next) return
        environment = next
        recycleAll()
        geometry.reset(model.itemProvider.itemCount, model.spacing.toDouble(), next)
    }

    private fun resetGeometry(model: LazyCollectionModel) {
        val next = UIKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        environment = next
        geometry.reset(model.itemProvider.itemCount, model.spacing.toDouble(), next)
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
        val pool = pooled[typeKey]
        val stableKeyIndex = pool?.indexOfFirst { it.key == key } ?: -1
        if (stableKeyIndex >= 0) return checkNotNull(pool).removeAt(stableKeyIndex)
        disposePooledBindingForKey(key)
        if (!pool.isNullOrEmpty()) return pool.removeAt(pool.lastIndex)
        val root = UIKitAdaptiveItemStackView()
        return UIKitAdaptiveItemBinding(
            root = root,
            itemHost =
                coordinator.createItemHost(
                    InvalidatingLazyItemChildren(UIKitChildren(root), root::invalidateExtent),
                ),
        ).also(allBindings::add)
    }

    private fun disposePooledBindingForKey(key: Any) {
        pooled.values.forEach { bindings ->
            val index = bindings.indexOfFirst { it.key == key }
            if (index >= 0) {
                val binding = bindings.removeAt(index)
                allBindings.remove(binding)
                binding.dispose()
                return
            }
        }
    }

    private fun recycle(index: Int) {
        val binding = realized.remove(index) ?: return
        binding.root.removeFromSuperview()
        pooled.getOrPut(binding.contentType.cacheKey()) { mutableListOf() }.add(binding)
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
    ) {
        val index = model.itemProvider.indexOfKey(anchor.key, anchor.index)
        if (index !in 0 until model.itemProvider.itemCount) return
        resolveExtent(model, index)
        val target = geometry.itemStart(index) - anchor.offset
        scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
    }

    private fun settleScrollRequest(request: LazyListScrollRequest) {
        val model = coordinator.model ?: return
        repeat(PROGRAMMATIC_SCROLL_SETTLING_PASSES) {
            layoutVisibleItems()
            resolveExtent(model, request.index)
            val target = geometry.itemStart(request.index) + request.scrollOffset
            scrollView.setContentOffset(model.mainAxisPoint(target), animated = false)
        }
        reportLayoutInfo(model)
    }

    private fun finishPhysicalScroll() {
        coordinator.reportScrollInProgress(false)
        layoutVisibleItems()
    }

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
    val offset: Double,
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

private fun dev.dimension.flare.ui.lazy.LazyItemProvider.indexOfKey(
    key: Any,
    expectedIndex: Int,
): Int {
    if (expectedIndex in 0 until itemCount && key(expectedIndex) == key) return expectedIndex
    repeat(minOf(LOCAL_ANCHOR_SEARCH_DISTANCE, itemCount)) { distanceOffset ->
        val distance = distanceOffset + 1
        val before = expectedIndex - distance
        if (before in 0 until itemCount && key(before) == key) return before
        val after = expectedIndex + distance
        if (after in 0 until itemCount && key(after) == key) return after
    }
    repeat(itemCount) { index ->
        if (key(index) == key) return index
    }
    return -1
}

private fun Any?.cacheKey(): Any = this ?: UIKitNullContentType

private data object UIKitNullContentType

internal fun shouldDeferUIKitLazyOffsetCorrection(
    isTracking: Boolean,
    isDragging: Boolean,
    isDecelerating: Boolean,
): Boolean = isTracking || isDragging || isDecelerating

private const val OVERSCAN_VIEWPORTS = 0.5
private const val MAX_LAYOUT_PASSES = 2
private const val PROGRAMMATIC_SCROLL_SETTLING_PASSES = 3
private const val LOCAL_ANCHOR_SEARCH_DISTANCE = 64
private const val CONTENT_SIZE_TOLERANCE = 0.5
