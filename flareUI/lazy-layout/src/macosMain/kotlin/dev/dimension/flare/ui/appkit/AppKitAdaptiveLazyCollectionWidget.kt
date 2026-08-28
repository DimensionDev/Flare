@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareWidget
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
import dev.dimension.flare.ui.lazy.needsAdaptiveLazyScrollCorrection
import kotlinx.cinterop.useContents
import platform.AppKit.NSAnimationContext
import platform.AppKit.NSLayoutAttributeCenterX
import platform.AppKit.NSLayoutAttributeCenterY
import platform.AppKit.NSLayoutAttributeLeading
import platform.AppKit.NSLayoutAttributeTop
import platform.AppKit.NSLayoutAttributeTrailing
import platform.AppKit.NSLayoutConstraint
import platform.AppKit.NSScrollView
import platform.AppKit.NSScrollViewDidEndLiveScrollNotification
import platform.AppKit.NSScrollViewWillStartLiveScrollNotification
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationHorizontal
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.AppKit.NSView
import platform.AppKit.NSViewBoundsDidChangeNotification
import platform.AppKit.fittingSize
import platform.AppKit.heightAnchor
import platform.AppKit.widthAnchor
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** Variable-extent AppKit renderer which keeps NSScrollView and owns linear virtualization. */
internal class AppKitAdaptiveLazyCollectionWidget :
    AbstractAppKitWidget<AppKitAdaptiveLazyScrollView>(AppKitAdaptiveLazyScrollView()),
    LazyCollectionWidget {
    private val canvas = AppKitAdaptiveLazyCanvasView()
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = ::applyModel,
            onScroll = ::performScroll,
            onScrollCancelled = ::cancelScroll,
        )
    private val bridge = AppKitAdaptiveLazyBridge(view, canvas, coordinator)
    private var pendingAnchor: AppKitAdaptiveAnchor? = null

    init {
        view.drawsBackground = false
        view.documentView = canvas
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
        bridge.dispose()
        coordinator.dispose()
    }

    private fun applyModel(
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ): LazyRealizedItemUpdate {
        val vertical = current.orientation == LazyListOrientation.Vertical
        view.hasVerticalScroller = vertical
        view.hasHorizontalScroller = !vertical
        view.autohidesScrollers = true
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

private class AppKitAdaptiveLazyBridge(
    private val scrollView: AppKitAdaptiveLazyScrollView,
    private val canvas: AppKitAdaptiveLazyCanvasView,
    private val coordinator: LazyCollectionCoordinator,
) {
    private val geometry = VariableExtentLayoutState()
    private val realized = mutableMapOf<Int, AppKitAdaptiveItemBinding>()
    private val allBindings = mutableSetOf<AppKitAdaptiveItemBinding>()
    private val pooled =
        LazyItemReusePool<AppKitAdaptiveItemBinding>(MIN_RETAINED_BINDINGS) { binding ->
            allBindings.remove(binding)
            binding.dispose()
        }
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val notificationTokens = mutableListOf<NSObjectProtocol>()
    private var environment: AppKitExtentEnvironment? = null
    private var layoutScheduled: Boolean = false
    private var layingOut: Boolean = false
    private var disposed: Boolean = false
    private var pendingAnchor: AppKitAdaptiveAnchor? = null
    private var pendingScroll: LazyListScrollRequest? = null
    private var modelResetPending: Boolean = false
    private var programmaticScrollInProgress: Boolean = false
    private var physicalScrollInProgress: Boolean = false

    init {
        scrollView.contentView().postsBoundsChangedNotifications = true
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSViewBoundsDidChangeNotification,
                `object` = scrollView.contentView(),
                queue = NSOperationQueue.mainQueue,
            ) {
                layoutVisibleItems()
            }
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSScrollViewWillStartLiveScrollNotification,
                `object` = scrollView,
                queue = NSOperationQueue.mainQueue,
            ) {
                physicalScrollInProgress = true
                cancelPendingScroll()
                coordinator.reportScrollInProgress(true)
            }
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSScrollViewDidEndLiveScrollNotification,
                `object` = scrollView,
                queue = NSOperationQueue.mainQueue,
            ) {
                physicalScrollInProgress = false
                coordinator.reportScrollInProgress(false)
                layoutVisibleItems()
            }
    }

    fun setModel(
        model: LazyCollectionModel,
        anchor: AppKitAdaptiveAnchor?,
    ) {
        // A second model can arrive before the scheduled layout has rebuilt any bindings. Keep the
        // last real viewport anchor instead of replacing it with the resulting null capture.
        pendingAnchor = anchor ?: pendingAnchor
        modelResetPending = true
        scheduleLayout()
    }

    fun captureAnchor(model: LazyCollectionModel): AppKitAdaptiveAnchor? {
        val viewportStart = scrollView.mainAxisOffset(model.orientation)
        var binding: AppKitAdaptiveItemBinding? = null
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
        return AppKitAdaptiveAnchor(key, anchorBinding.index, bindingStart - viewportStart)
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
        if (!request.animated) {
            programmaticScrollInProgress = true
            try {
                scrollToMainAxisOffset(model.orientation, target)
                if (!settleScrollRequest(request)) {
                    request.cancel()
                    return
                }
            } finally {
                programmaticScrollInProgress = false
            }
            request.complete()
            return
        }
        cancelPendingScroll(stopAnimation = true)
        pendingScroll = request
        coordinator.reportScrollInProgress(true)
        NSAnimationContext.runAnimationGroup(
            changes = { context ->
                context?.duration = DEFAULT_ANIMATION_DURATION
                scrollView.contentView().animator().setBoundsOrigin(model.mainAxisPoint(target))
            },
            completionHandler = {
                if (pendingScroll === request) {
                    val settled = settleScrollRequest(request)
                    if (pendingScroll === request) pendingScroll = null
                    if (settled) {
                        request.complete()
                    } else {
                        request.cancel()
                    }
                    coordinator.reportScrollInProgress(false)
                }
            },
        )
    }

    fun cancelScroll(request: LazyListScrollRequest) {
        if (pendingScroll !== request) return
        pendingScroll = null
        stopAnimatedScroll()
        coordinator.reportScrollInProgress(false)
    }

    fun dispose() {
        disposed = true
        pendingScroll?.cancel()
        pendingScroll = null
        notificationTokens.forEach(notificationCenter::removeObserver)
        notificationTokens.clear()
        allBindings.toList().forEach(AppKitAdaptiveItemBinding::dispose)
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
            val deferOffsetCorrection = shouldDeferAppKitLazyOffsetCorrection(physicalScrollInProgress)
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
                updateCanvasSize(model)
                if (!measured) break
                pass += 1
            }
            placeRealized(model)
            updateCanvasSize(model)
            if (geometryChanged && measurementAnchor != null) {
                restoreAnchor(model, measurementAnchor)
                placeRealized(model)
            }
            canvas.layoutSubtreeIfNeeded()
            reportLayoutInfo(model)
        } finally {
            layingOut = false
        }
    }

    private fun ensureEnvironment(model: LazyCollectionModel) {
        val next = AppKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
        if (environment == next) return
        environment = next
        recycleAll()
        geometry.reset(model.itemProvider.itemCount, model.spacing.toDouble(), next)
    }

    private fun resetGeometry(model: LazyCollectionModel) {
        val next = AppKitExtentEnvironment(model.orientation, round(scrollView.crossAxisExtent(model.orientation) * 2.0) / 2.0)
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
            canvas.addSubview(binding.root)
        }
    }

    private fun takeBinding(
        contentType: Any?,
        key: Any,
    ): AppKitAdaptiveItemBinding {
        val typeKey = contentType.cacheKey()
        pooled.take(typeKey, key)?.let { return it }
        val root = AppKitAdaptiveItemStackView()
        return AppKitAdaptiveItemBinding(
            root = root,
            itemHost =
                coordinator.createItemHost(
                    InvalidatingLazyItemChildren(AppKitAdaptiveChildren(root), root::invalidateExtent),
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
            val extent = binding.root.measuredExtent(model.orientation)
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

    private fun updateCanvasSize(model: LazyCollectionModel) {
        val viewport = scrollView.contentView().bounds.useContents { size.width to size.height }
        val frame =
            when (model.orientation) {
                LazyListOrientation.Vertical -> CGRectMake(0.0, 0.0, viewport.first, max(viewport.second, geometry.contentExtent))
                LazyListOrientation.Horizontal -> CGRectMake(0.0, 0.0, max(viewport.first, geometry.contentExtent), viewport.second)
            }
        val nextSize = frame.useContents { size.width to size.height }
        val changed =
            canvas.frame.useContents {
                abs(size.width - nextSize.first) > CANVAS_SIZE_TOLERANCE ||
                    abs(size.height - nextSize.second) > CANVAS_SIZE_TOLERANCE
            }
        if (changed) canvas.setFrame(frame)
    }

    private fun restoreAnchor(
        model: LazyCollectionModel,
        anchor: AppKitAdaptiveAnchor,
    ) {
        val index = model.itemProvider.indexOfKey(anchor.key, anchor.index)
        if (index !in 0 until model.itemProvider.itemCount) return
        resolveExtent(model, index)
        scrollToMainAxisOffset(model.orientation, geometry.itemStart(index) - anchor.offset)
    }

    private fun settleScrollRequest(request: LazyListScrollRequest): Boolean {
        val model = coordinator.model ?: return false
        var pass = 0
        while (pass < MAX_PROGRAMMATIC_SCROLL_CORRECTIONS) {
            if (!request.isActive || request.index !in 0 until model.itemProvider.itemCount) return false
            layoutVisibleItems()
            resolveExtent(model, request.index)
            val target = geometry.itemStart(request.index) + request.scrollOffset
            if (!scrollToMainAxisOffset(model.orientation, target)) break
            pass += 1
        }
        reportLayoutInfo(model)
        return request.isActive && request.index in 0 until model.itemProvider.itemCount
    }

    private fun scrollToMainAxisOffset(
        orientation: LazyListOrientation,
        offset: Double,
    ): Boolean {
        if (!needsAdaptiveLazyScrollCorrection(scrollView.mainAxisOffset(orientation), offset)) return false
        scrollView.contentView().setBoundsOrigin(
            when (orientation) {
                LazyListOrientation.Vertical -> CGPointMake(0.0, offset)
                LazyListOrientation.Horizontal -> CGPointMake(offset, 0.0)
            },
        )
        scrollView.reflectScrolledClipView(scrollView.contentView())
        return true
    }

    private fun cancelPendingScroll(stopAnimation: Boolean = false) {
        val request = pendingScroll ?: return
        pendingScroll = null
        if (stopAnimation) stopAnimatedScroll()
        request.cancel()
    }

    private fun stopAnimatedScroll() {
        val clipView = scrollView.contentView()
        val currentOrigin = clipView.bounds.useContents { CGPointMake(origin.x, origin.y) }
        clipView.setBoundsOrigin(currentOrigin)
        scrollView.reflectScrolledClipView(clipView)
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
                }.sortedBy(AppKitAdaptiveItemBinding::index)
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

private class AppKitAdaptiveItemBinding(
    val root: AppKitAdaptiveItemStackView,
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

internal class AppKitAdaptiveLazyScrollView : NSScrollView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onLayout: (() -> Unit)? = null

    override fun layout() {
        super.layout()
        onLayout?.invoke()
    }
}

internal class AppKitAdaptiveLazyCanvasView : NSView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    override fun isFlipped(): Boolean = true
}

private class AppKitAdaptiveItemStackView : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val stretchConstraints = mutableListOf<NSLayoutConstraint>()
    private var lazyOrientation: LazyListOrientation = LazyListOrientation.Vertical
    private var stretchesCrossAxis: Boolean = false
    var onExtentInvalidated: (() -> Unit)? = null

    fun configure(model: LazyCollectionModel) {
        val nextOrientation =
            when (model.orientation) {
                LazyListOrientation.Vertical -> NSUserInterfaceLayoutOrientationVertical
                LazyListOrientation.Horizontal -> NSUserInterfaceLayoutOrientationHorizontal
            }
        val nextAlignment =
            when (model.orientation) {
                LazyListOrientation.Vertical -> model.crossAxisAlignment.horizontalAlignment()
                LazyListOrientation.Horizontal -> model.crossAxisAlignment.verticalAlignment()
            }
        val nextStretchesCrossAxis = model.crossAxisAlignment == LazyCrossAxisAlignment.Stretch
        if (lazyOrientation == model.orientation &&
            orientation == nextOrientation &&
            alignment == nextAlignment &&
            stretchesCrossAxis == nextStretchesCrossAxis
        ) {
            return
        }
        spacing = 0.0
        lazyOrientation = model.orientation
        stretchesCrossAxis = nextStretchesCrossAxis
        orientation = nextOrientation
        alignment = nextAlignment
        rebuildStretchConstraints()
    }

    fun invalidateExtent() {
        needsLayout = true
        onExtentInvalidated?.invoke()
    }

    fun rebuildStretchConstraints() {
        NSLayoutConstraint.deactivateConstraints(stretchConstraints)
        stretchConstraints.clear()
        if (stretchesCrossAxis) {
            arrangedSubviews.forEach { installStretchConstraint(it as NSView) }
        }
    }

    fun measuredExtent(orientation: LazyListOrientation): Double {
        layoutSubtreeIfNeeded()
        return fittingExtent(orientation)
    }

    private fun fittingExtent(orientation: LazyListOrientation): Double =
        fittingSize.useContents {
            when (orientation) {
                LazyListOrientation.Vertical -> height
                LazyListOrientation.Horizontal -> width
            }
        }

    private fun installStretchConstraint(child: NSView) {
        val constraint =
            when (lazyOrientation) {
                LazyListOrientation.Vertical -> child.widthAnchor.constraintEqualToAnchor(widthAnchor)
                LazyListOrientation.Horizontal -> child.heightAnchor.constraintEqualToAnchor(heightAnchor)
            }.apply {
                priority = LAZY_STRETCH_PRIORITY
            }
        stretchConstraints += constraint
        NSLayoutConstraint.activateConstraints(listOf(constraint))
    }
}

private class AppKitAdaptiveChildren(
    private val parent: AppKitAdaptiveItemStackView,
) : FlareChildren {
    private val delegate = AppKitChildren(parent)

    override fun onBeginChanges() {
        delegate.onBeginChanges()
    }

    override fun onEndChanges() {
        delegate.onEndChanges()
        parent.rebuildStretchConstraints()
    }

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        delegate.insert(index, widget)
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        delegate.move(fromIndex, toIndex, count)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        delegate.remove(index, count)
    }
}

private data class AppKitExtentEnvironment(
    val orientation: LazyListOrientation,
    val crossAxisExtent: Double,
)

private data class AppKitAdaptiveAnchor(
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

private fun NSScrollView.mainAxisOffset(orientation: LazyListOrientation): Double =
    contentView().bounds.useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> origin.y
            LazyListOrientation.Horizontal -> origin.x
        }
    }

private fun NSScrollView.mainAxisViewport(orientation: LazyListOrientation): Double =
    contentView().bounds.useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> size.height
            LazyListOrientation.Horizontal -> size.width
        }
    }

private fun NSScrollView.crossAxisExtent(orientation: LazyListOrientation): Double =
    contentView()
        .bounds
        .useContents {
            when (orientation) {
                LazyListOrientation.Vertical -> size.width
                LazyListOrientation.Horizontal -> size.height
            }
        }.coerceAtLeast(1.0)

private fun LazyCrossAxisAlignment.horizontalAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> NSLayoutAttributeLeading
        LazyCrossAxisAlignment.Center -> NSLayoutAttributeCenterX
        LazyCrossAxisAlignment.End -> NSLayoutAttributeTrailing
    }

private fun LazyCrossAxisAlignment.verticalAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> NSLayoutAttributeTop
        LazyCrossAxisAlignment.Center -> NSLayoutAttributeCenterY
        LazyCrossAxisAlignment.End -> platform.AppKit.NSLayoutAttributeBottom
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

private fun Any?.cacheKey(): Any = this ?: AppKitNullContentType

private data object AppKitNullContentType

internal fun shouldDeferAppKitLazyOffsetCorrection(isLiveScrolling: Boolean): Boolean = isLiveScrolling

private const val OVERSCAN_VIEWPORTS = 0.5
private const val MAX_LAYOUT_PASSES = 2
private const val MAX_PROGRAMMATIC_SCROLL_CORRECTIONS = 3
private const val MIN_RETAINED_BINDINGS = 32
private const val DEFAULT_ANIMATION_DURATION = 0.25
private const val CANVAS_SIZE_TOLERANCE = 0.5
private const val LAZY_STRETCH_PRIORITY: Float = 999f
private const val LOCAL_ANCHOR_SEARCH_DISTANCE = 64
