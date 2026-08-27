@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.lazy.LazyCollectionCoordinator
import dev.dimension.flare.ui.lazy.LazyCollectionModel
import dev.dimension.flare.ui.lazy.LazyCollectionWidget
import dev.dimension.flare.ui.lazy.LazyCrossAxisAlignment
import dev.dimension.flare.ui.lazy.LazyItemHost
import dev.dimension.flare.ui.lazy.LazyListChangeSet
import dev.dimension.flare.ui.lazy.LazyListItemInfo
import dev.dimension.flare.ui.lazy.LazyListLayoutInfo
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.LazyListScrollRequest
import dev.dimension.flare.ui.lazy.LazyRealizedItemUpdate
import dev.dimension.flare.ui.lazy.calculateLazyListChangeSet
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.AppKit.NSAnimationContext
import platform.AppKit.NSCollectionView
import platform.AppKit.NSCollectionViewDataSourceProtocol
import platform.AppKit.NSCollectionViewDelegateFlowLayoutProtocol
import platform.AppKit.NSCollectionViewDelegateProtocol
import platform.AppKit.NSCollectionViewFlowLayout
import platform.AppKit.NSCollectionViewItem
import platform.AppKit.NSCollectionViewLayout
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionHorizontal
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionVertical
import platform.AppKit.NSCollectionViewScrollPositionLeft
import platform.AppKit.NSCollectionViewScrollPositionTop
import platform.AppKit.NSLayoutAttributeBottom
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
import platform.AppKit.indexPathForItem
import platform.AppKit.item
import platform.AppKit.layoutAttributesForItemAtIndexPath
import platform.AppKit.widthAnchor
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSCoder
import platform.Foundation.NSIndexPath
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** NSCollectionView renderer for Flare lazy collections. */
public object AppKitLazyLayoutRendererPlugin : FlareRendererPlugin<AppKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AppKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            AppKitLazyCollectionWidget()
        }
    }
}

private class AppKitLazyCollectionWidget :
    AbstractAppKitWidget<NSScrollView>(view = AppKitLazyScrollView()),
    LazyCollectionWidget {
    private val flowLayout = NSCollectionViewFlowLayout()
    private val collectionView =
        NSCollectionView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            collectionViewLayout = flowLayout
        }
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = ::applyModel,
            onScroll = ::performScroll,
        )
    private val bridge = AppKitLazyCollectionBridge(view, collectionView, flowLayout, coordinator)
    private var pendingAnchor: AppKitLazyAnchor? = null
    private var pendingDiffIsSafe: Boolean = false

    init {
        view.drawsBackground = false
        view.documentView = collectionView
        collectionView.dataSource = bridge
        collectionView.delegate = bridge
    }

    override fun setModel(model: LazyCollectionModel) {
        val previous = coordinator.model
        pendingAnchor = previous?.let(bridge::captureAnchor)
        pendingDiffIsSafe =
            previous != null &&
            maxOf(previous.itemProvider.itemCount, model.itemProvider.itemCount) <= MAX_INCREMENTAL_DIFF_ITEMS &&
            coordinator.realizedItemsMatch(previous.itemProvider)
        try {
            coordinator.setModel(model)
        } finally {
            pendingAnchor = null
            pendingDiffIsSafe = false
        }
    }

    override fun dispose() {
        collectionView.dataSource = null
        collectionView.delegate = null
        bridge.dispose()
        coordinator.dispose()
    }

    private fun applyModel(
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ): LazyRealizedItemUpdate {
        val anchor = pendingAnchor
        flowLayout.scrollDirection = current.orientation.appKitScrollDirection()
        flowLayout.minimumLineSpacing = current.spacing.toDouble()
        flowLayout.minimumInteritemSpacing = 0.0
        flowLayout.estimatedItemSize = CGSizeMake(0.0, 0.0)
        bridge.configureSizing(current)
        val vertical = current.orientation == LazyListOrientation.Vertical
        view.hasVerticalScroller = vertical
        view.hasHorizontalScroller = !vertical
        view.autohidesScrollers = true
        val finishUpdate = finishUpdate@{
            if (coordinator.model !== current) return@finishUpdate
            collectionView.layoutSubtreeIfNeeded()
            bridge.remeasureVisibleItems()
            anchor?.let {
                bridge.restoreAnchor(it, current)
                bridge.restoreAnchorAfterLayout(it, current)
            }
            bridge.reportLayoutInfo()
        }
        if (previous == null) {
            bridge.reloadDataImmediately(current.itemProvider.itemCount)
            finishUpdate()
            return LazyRealizedItemUpdate.RendererManaged
        }
        if (previous.orientation != current.orientation ||
            previous.crossAxisAlignment != current.crossAxisAlignment ||
            !pendingDiffIsSafe
        ) {
            bridge.reloadData(current.itemProvider.itemCount, finishUpdate)
            return LazyRealizedItemUpdate.RendererManaged
        } else {
            bridge.applyChanges(
                changes = calculateLazyListChangeSet(previous.itemProvider, current.itemProvider),
                newItemCount = current.itemProvider.itemCount,
                completion = finishUpdate,
            )
            return LazyRealizedItemUpdate.Rebind
        }
    }

    private fun performScroll(request: LazyListScrollRequest) {
        bridge.performScroll(request)
    }
}

private class AppKitLazyCollectionBridge(
    private val scrollView: NSScrollView,
    private val collectionView: NSCollectionView,
    private val flowLayout: NSCollectionViewFlowLayout,
    private val coordinator: LazyCollectionCoordinator,
) : NSObject(),
    NSCollectionViewDataSourceProtocol,
    NSCollectionViewDelegateProtocol,
    NSCollectionViewDelegateFlowLayoutProtocol {
    private val bindings = mutableMapOf<NSCollectionViewItem, AppKitItemBinding>()
    private val reuseIdentifiers = mutableMapOf<Any, String>()
    private var nextReuseIdentifier = 0
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val notificationTokens = mutableListOf<NSObjectProtocol>()
    private var presentedItemCount: Int = 0
    private var updateGeneration: Long = 0L
    private var disposed: Boolean = false
    private var pendingAnimatedScroll: LazyListScrollRequest? = null
    private var sizingOrientation: LazyListOrientation = LazyListOrientation.Vertical
    private val measuredExtents = mutableMapOf<Int, Double>()
    private var sizingInvalidationScheduled: Boolean = false

    init {
        scrollView.contentView().postsBoundsChangedNotifications = true
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSViewBoundsDidChangeNotification,
                `object` = scrollView.contentView(),
                queue = NSOperationQueue.mainQueue,
            ) {
                reportLayoutInfo()
            }
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSScrollViewWillStartLiveScrollNotification,
                `object` = scrollView,
                queue = NSOperationQueue.mainQueue,
            ) {
                coordinator.reportScrollInProgress(true)
            }
        notificationTokens +=
            notificationCenter.addObserverForName(
                name = NSScrollViewDidEndLiveScrollNotification,
                `object` = scrollView,
                queue = NSOperationQueue.mainQueue,
            ) {
                coordinator.reportScrollInProgress(false)
                reportLayoutInfo()
            }
    }

    override fun numberOfSectionsInCollectionView(collectionView: NSCollectionView): Long = 1L

    override fun collectionView(
        collectionView: NSCollectionView,
        numberOfItemsInSection: Long,
    ): Long = presentedItemCount.toLong()

    override fun collectionView(
        collectionView: NSCollectionView,
        itemForRepresentedObjectAtIndexPath: NSIndexPath,
    ): NSCollectionViewItem {
        val model = checkNotNull(coordinator.model)
        val index = itemForRepresentedObjectAtIndexPath.item.toInt()
        val identifier = reuseIdentifier(model, index)
        val item =
            collectionView.makeItemWithIdentifier(
                identifier = identifier,
                forIndexPath = itemForRepresentedObjectAtIndexPath,
            )
        check(item is AppKitLazyCollectionViewItem)
        val existingBinding = bindings[item]
        val binding =
            if (existingBinding?.isActive == true) {
                existingBinding
            } else {
                existingBinding?.dispose()
                createBinding(item, model).also { bindings[item] = it }
            }
        binding.bind(model, index)
        return item
    }

    override fun collectionView(
        collectionView: NSCollectionView,
        layout: NSCollectionViewLayout,
        sizeForItemAtIndexPath: NSIndexPath,
    ) = itemSize(sizeForItemAtIndexPath.item.toInt())

    @ObjCSignatureOverride
    override fun collectionView(
        collectionView: NSCollectionView,
        layout: NSCollectionViewLayout,
        minimumLineSpacingForSectionAtIndex: Long,
    ): Double = coordinator.model?.spacing?.toDouble() ?: 0.0

    @ObjCSignatureOverride
    override fun collectionView(
        collectionView: NSCollectionView,
        layout: NSCollectionViewLayout,
        minimumInteritemSpacingForSectionAtIndex: Long,
    ): Double = coordinator.model?.spacing?.toDouble() ?: 0.0

    override fun collectionView(
        collectionView: NSCollectionView,
        willDisplayItem: NSCollectionViewItem,
        forRepresentedObjectAtIndexPath: NSIndexPath,
    ) {
        val binding = bindings[willDisplayItem] ?: return
        recordMeasuredExtent(
            index = forRepresentedObjectAtIndexPath.item.toInt(),
            extent = binding.measuredExtent(sizingOrientation),
            scheduleInvalidation = true,
        )
    }

    fun configureSizing(model: LazyCollectionModel) {
        sizingOrientation = model.orientation
        measuredExtents.clear()
        sizingInvalidationScheduled = false
    }

    fun remeasureVisibleItems() {
        collectionView.indexPathsForVisibleItems().forEach { value ->
            val path = value as NSIndexPath
            val binding = collectionView.itemAtIndexPath(path)?.let(bindings::get) ?: return@forEach
            recordMeasuredExtent(
                index = path.item.toInt(),
                extent = binding.measuredExtent(sizingOrientation),
                scheduleInvalidation = false,
            )
        }
        flushSizingInvalidation()
    }

    fun captureAnchor(model: LazyCollectionModel): AppKitLazyAnchor? {
        val indexPath =
            collectionView
                .indexPathsForVisibleItems()
                .map { it as NSIndexPath }
                .minByOrNull { it.item }
                ?: return null
        val index = indexPath.item.toInt()
        if (index !in 0 until model.itemProvider.itemCount) return null
        val attributes = collectionView.collectionViewLayout?.layoutAttributesForItemAtIndexPath(indexPath) ?: return null
        val frameOrigin =
            attributes.frame.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> origin.y
                    LazyListOrientation.Horizontal -> origin.x
                }
            }
        val viewportOrigin = scrollView.contentView().bounds.mainAxisOrigin(model.orientation)
        val boundKey =
            collectionView
                .itemAtIndexPath(indexPath)
                ?.let(bindings::get)
                ?.key
        return AppKitLazyAnchor(boundKey ?: model.itemProvider.key(index), frameOrigin - viewportOrigin)
    }

    fun restoreAnchor(
        anchor: AppKitLazyAnchor,
        model: LazyCollectionModel,
    ) {
        val index = model.itemProvider.indexOfKey(anchor.key)
        if (disposed || index !in 0 until presentedItemCount) return
        val path = NSIndexPath.indexPathForItem(index.toLong(), inSection = 0L)
        collectionView.scrollToItemsAtIndexPaths(
            indexPaths = setOf(path),
            scrollPosition = model.orientation.startScrollPosition(),
        )
        collectionView.layoutSubtreeIfNeeded()
        val attributes = collectionView.collectionViewLayout?.layoutAttributesForItemAtIndexPath(path) ?: return
        val itemOrigin = attributes.frame.mainAxisOrigin(model.orientation)
        scrollToMainAxisOffset(model.orientation, itemOrigin - anchor.offset)
    }

    fun restoreAnchorAfterLayout(
        anchor: AppKitLazyAnchor,
        model: LazyCollectionModel,
    ) {
        dispatch_async(dispatch_get_main_queue()) {
            if (disposed || coordinator.model !== model) return@dispatch_async
            collectionView.layoutSubtreeIfNeeded()
            restoreAnchor(anchor, model)
            reportLayoutInfo()
        }
    }

    fun performScroll(request: LazyListScrollRequest) {
        val model = checkNotNull(coordinator.model)
        val path = NSIndexPath.indexPathForItem(request.index.toLong(), inSection = 0L)
        collectionView.layoutSubtreeIfNeeded()
        collectionView.scrollToItemsAtIndexPaths(
            indexPaths = setOf(path),
            scrollPosition = model.orientation.startScrollPosition(),
        )
        collectionView.layoutSubtreeIfNeeded()
        if (!request.animated) {
            if (!settleRequestedOffset(model, request, path)) {
                request.cancel()
                return
            }
            reportLayoutInfo()
            request.complete()
            return
        }

        val itemOrigin =
            collectionView.collectionViewLayout
                ?.layoutAttributesForItemAtIndexPath(path)
                ?.frame
                ?.mainAxisOrigin(model.orientation)
        if (itemOrigin == null) {
            request.cancel()
            return
        }
        val target = mainAxisPoint(model.orientation, itemOrigin + request.scrollOffset)

        pendingAnimatedScroll?.cancel()
        pendingAnimatedScroll = request
        coordinator.reportScrollInProgress(true)
        NSAnimationContext.runAnimationGroup(
            changes = { context ->
                context?.duration = DEFAULT_ANIMATION_DURATION
                scrollView.contentView().animator().setBoundsOrigin(target)
            },
            completionHandler = {
                if (pendingAnimatedScroll === request) {
                    pendingAnimatedScroll = null
                    if (settleRequestedOffset(model, request, path)) {
                        request.complete()
                    } else {
                        request.cancel()
                    }
                    coordinator.reportScrollInProgress(false)
                    reportLayoutInfo()
                }
            },
        )
    }

    fun reportLayoutInfo() {
        val model = coordinator.model ?: return
        val provider = model.itemProvider
        val viewportOrigin = scrollView.contentView().bounds.mainAxisOrigin(model.orientation)
        val visible =
            collectionView
                .indexPathsForVisibleItems()
                .map { it as NSIndexPath }
                .mapNotNull { path ->
                    val index = path.item.toInt()
                    if (index !in 0 until provider.itemCount) return@mapNotNull null
                    val attributes =
                        collectionView.collectionViewLayout?.layoutAttributesForItemAtIndexPath(path)
                            ?: return@mapNotNull null
                    val itemOrigin = attributes.frame.mainAxisOrigin(model.orientation)
                    val itemSize = attributes.frame.mainAxisSize(model.orientation)
                    LazyListItemInfo(
                        key = provider.key(index),
                        index = index,
                        offset = (itemOrigin - viewportOrigin).toFloat(),
                        size = itemSize.toFloat(),
                    )
                }.sortedBy(LazyListItemInfo::index)
        val viewportSize = scrollView.contentView().bounds.mainAxisSize(model.orientation)
        coordinator.reportLayoutInfo(
            LazyListLayoutInfo(
                totalItemsCount = provider.itemCount,
                viewportStartOffset = 0f,
                viewportEndOffset = viewportSize.toFloat(),
                visibleItems = visible,
            ),
        )
    }

    fun applyChanges(
        changes: LazyListChangeSet,
        newItemCount: Int,
        completion: () -> Unit,
    ) {
        val generation = ++updateGeneration
        val structuralChanges =
            changes.removedIndices.isNotEmpty() ||
                changes.insertedIndices.isNotEmpty() ||
                changes.moves.isNotEmpty()
        val reloadChangedTypes = reloadChangedTypes@{
            if (disposed || generation != updateGeneration) return@reloadChangedTypes
            presentedItemCount = newItemCount
            if (changes.reloadedIndices.isNotEmpty()) {
                collectionView.reloadItemsAtIndexPaths(changes.reloadedIndices.map(::indexPath).toSet())
            }
            completion()
        }
        if (!structuralChanges) {
            reloadChangedTypes()
            return
        }
        collectionView.performBatchUpdates(
            updates = {
                presentedItemCount = newItemCount
                if (changes.removedIndices.isNotEmpty()) {
                    collectionView.deleteItemsAtIndexPaths(changes.removedIndices.map(::indexPath).toSet())
                }
                if (changes.insertedIndices.isNotEmpty()) {
                    collectionView.insertItemsAtIndexPaths(changes.insertedIndices.map(::indexPath).toSet())
                }
                changes.moves.forEach { move ->
                    collectionView.moveItemAtIndexPath(indexPath(move.fromIndex), indexPath(move.toIndex))
                }
            },
            completionHandler = { _ -> reloadChangedTypes() },
        )
    }

    fun reloadData(
        itemCount: Int,
        completion: () -> Unit,
    ) {
        val generation = ++updateGeneration
        dispatch_async(dispatch_get_main_queue()) {
            if (disposed || generation != updateGeneration) return@dispatch_async
            disposeBindings()
            presentedItemCount = itemCount
            collectionView.reloadData()
            completion()
        }
    }

    fun reloadDataImmediately(itemCount: Int) {
        updateGeneration += 1L
        presentedItemCount = itemCount
        collectionView.reloadData()
    }

    fun dispose() {
        disposed = true
        updateGeneration += 1L
        pendingAnimatedScroll?.cancel()
        pendingAnimatedScroll = null
        notificationTokens.forEach(notificationCenter::removeObserver)
        notificationTokens.clear()
        disposeBindings()
    }

    private fun reuseIdentifier(
        model: LazyCollectionModel,
        index: Int,
    ): String {
        val contentType = model.itemProvider.contentType(index) ?: NullContentType
        return reuseIdentifiers.getOrPut(contentType) {
            "FlareLazyItem-${nextReuseIdentifier++}".also { identifier ->
                collectionView.registerClass(
                    itemClass = AppKitLazyCollectionViewItem(null, null).`class`(),
                    forItemWithIdentifier = identifier,
                )
            }
        }
    }

    private fun createRoot(model: LazyCollectionModel): AppKitLazyItemStackView =
        AppKitLazyItemStackView().apply {
            spacing = 0.0
            configure(model)
        }

    private fun createBinding(
        item: AppKitLazyCollectionViewItem,
        model: LazyCollectionModel,
    ): AppKitItemBinding {
        val root = createRoot(model)
        item.view = root
        return AppKitItemBinding(root, coordinator.createItemHost(AppKitLazyChildren(root)))
    }

    private fun itemSize(index: Int) =
        scrollView.contentView().bounds.useContents {
            val mainAxisExtent = measuredExtents[index] ?: DEFAULT_ESTIMATED_EXTENT
            when (sizingOrientation) {
                LazyListOrientation.Vertical -> {
                    CGSizeMake(size.width.coerceAtLeast(1.0), mainAxisExtent)
                }

                LazyListOrientation.Horizontal -> {
                    CGSizeMake(mainAxisExtent, size.height.coerceAtLeast(1.0))
                }
            }
        }

    private fun recordMeasuredExtent(
        index: Int,
        extent: Double,
        scheduleInvalidation: Boolean,
    ) {
        if (!extent.isFinite() || extent <= 0.0) return
        val previous = measuredExtents[index] ?: DEFAULT_ESTIMATED_EXTENT
        if (kotlin.math.abs(previous - extent) <= MEASUREMENT_TOLERANCE) return
        measuredExtents[index] = extent
        val alreadyScheduled = sizingInvalidationScheduled
        sizingInvalidationScheduled = true
        if (scheduleInvalidation && !alreadyScheduled) {
            dispatch_async(dispatch_get_main_queue()) {
                flushSizingInvalidation()
            }
        }
    }

    private fun flushSizingInvalidation() {
        if (disposed || !sizingInvalidationScheduled) return
        sizingInvalidationScheduled = false
        flowLayout.invalidateLayout()
        collectionView.layoutSubtreeIfNeeded()
        reportLayoutInfo()
    }

    private fun scrollToMainAxisOffset(
        orientation: LazyListOrientation,
        offset: Double,
    ) {
        scrollView.contentView().setBoundsOrigin(mainAxisPoint(orientation, offset))
        scrollView.reflectScrolledClipView(scrollView.contentView())
    }

    private fun settleRequestedOffset(
        model: LazyCollectionModel,
        request: LazyListScrollRequest,
        path: NSIndexPath,
    ): Boolean {
        repeat(PROGRAMMATIC_SCROLL_SETTLING_PASSES) {
            collectionView.layoutSubtreeIfNeeded()
            collectionView.indexPathsForVisibleItems()
            val itemOrigin =
                collectionView.collectionViewLayout
                    ?.layoutAttributesForItemAtIndexPath(path)
                    ?.frame
                    ?.mainAxisOrigin(model.orientation)
                    ?: return false
            scrollToMainAxisOffset(model.orientation, itemOrigin + request.scrollOffset)
        }
        return true
    }

    private fun mainAxisPoint(
        orientation: LazyListOrientation,
        offset: Double,
    ) = scrollView.contentView().bounds.useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> CGPointMake(origin.x, offset)
            LazyListOrientation.Horizontal -> CGPointMake(offset, origin.y)
        }
    }

    private fun indexPath(index: Int): NSIndexPath = NSIndexPath.indexPathForItem(index.toLong(), inSection = 0L)

    private fun disposeBindings() {
        bindings.values.toList().forEach(AppKitItemBinding::dispose)
        bindings.clear()
    }

    private data object NullContentType
}

private class AppKitLazyScrollView : NSScrollView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))

private class AppKitLazyCollectionViewItem : NSCollectionViewItem {
    @OverrideInit
    constructor(nibName: String?, bundle: NSBundle?) : super(nibName, bundle)

    @OverrideInit
    constructor(coder: NSCoder) : super(coder)
}

private class AppKitItemBinding(
    private val root: AppKitLazyItemStackView,
    private val itemHost: LazyItemHost,
) {
    val isActive: Boolean
        get() = !itemHost.isDisposed

    val key: Any?
        get() = itemHost.key

    fun bind(
        model: LazyCollectionModel,
        index: Int,
    ) {
        root.configure(model)
        itemHost.bind(index)
        root.configure(model)
    }

    fun measuredExtent(orientation: LazyListOrientation): Double {
        root.layoutSubtreeIfNeeded()
        return root.fittingSize.useContents {
            when (orientation) {
                LazyListOrientation.Vertical -> height
                LazyListOrientation.Horizontal -> width
            }
        }
    }

    fun dispose() {
        itemHost.dispose()
        root.removeFromSuperview()
    }
}

private class AppKitLazyItemStackView : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val stretchConstraints = mutableListOf<NSLayoutConstraint>()
    private var lazyOrientation: LazyListOrientation = LazyListOrientation.Vertical
    private var stretchesCrossAxis: Boolean = false

    fun configure(model: LazyCollectionModel) {
        spacing = 0.0
        lazyOrientation = model.orientation
        stretchesCrossAxis = model.crossAxisAlignment == LazyCrossAxisAlignment.Stretch
        when (model.orientation) {
            LazyListOrientation.Vertical -> {
                orientation = NSUserInterfaceLayoutOrientationVertical
                alignment = model.crossAxisAlignment.horizontalStackAlignment()
            }

            LazyListOrientation.Horizontal -> {
                orientation = NSUserInterfaceLayoutOrientationHorizontal
                alignment = model.crossAxisAlignment.verticalStackAlignment()
            }
        }
        rebuildStretchConstraints()
    }

    fun rebuildStretchConstraints() {
        NSLayoutConstraint.deactivateConstraints(stretchConstraints)
        stretchConstraints.clear()
        if (stretchesCrossAxis) {
            arrangedSubviews.forEach { installStretchConstraint(it as NSView) }
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

private class AppKitLazyChildren(
    private val parent: AppKitLazyItemStackView,
) : FlareChildren {
    private val delegate = AppKitChildren(parent)

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        delegate.insert(index, widget)
        parent.rebuildStretchConstraints()
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        delegate.move(fromIndex, toIndex, count)
        parent.rebuildStretchConstraints()
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        delegate.remove(index, count)
        parent.rebuildStretchConstraints()
    }
}

private data class AppKitLazyAnchor(
    val key: Any,
    val offset: Double,
)

private fun LazyCrossAxisAlignment.horizontalStackAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> NSLayoutAttributeLeading
        LazyCrossAxisAlignment.Center -> NSLayoutAttributeCenterX
        LazyCrossAxisAlignment.End -> NSLayoutAttributeTrailing
    }

private fun LazyCrossAxisAlignment.verticalStackAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> NSLayoutAttributeTop
        LazyCrossAxisAlignment.Center -> NSLayoutAttributeCenterY
        LazyCrossAxisAlignment.End -> NSLayoutAttributeBottom
    }

private fun LazyListOrientation.startScrollPosition(): ULong =
    when (this) {
        LazyListOrientation.Vertical -> NSCollectionViewScrollPositionTop
        LazyListOrientation.Horizontal -> NSCollectionViewScrollPositionLeft
    }

private fun dev.dimension.flare.ui.lazy.LazyItemProvider.indexOfKey(key: Any): Int {
    repeat(itemCount) { index ->
        if (key(index) == key) return index
    }
    return -1
}

private fun LazyListOrientation.appKitScrollDirection() =
    when (this) {
        LazyListOrientation.Vertical -> NSCollectionViewScrollDirectionVertical
        LazyListOrientation.Horizontal -> NSCollectionViewScrollDirectionHorizontal
    }

private fun kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>.mainAxisOrigin(orientation: LazyListOrientation): Double =
    useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> origin.y
            LazyListOrientation.Horizontal -> origin.x
        }
    }

private fun kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>.mainAxisSize(orientation: LazyListOrientation): Double =
    useContents {
        when (orientation) {
            LazyListOrientation.Vertical -> size.height
            LazyListOrientation.Horizontal -> size.width
        }
    }

private const val DEFAULT_ESTIMATED_EXTENT = 48.0
private const val MEASUREMENT_TOLERANCE = 0.5
private const val LAZY_STRETCH_PRIORITY: Float = 999f
private const val MAX_INCREMENTAL_DIFF_ITEMS: Int = 1_000
private const val DEFAULT_ANIMATION_DURATION = 0.25
private const val PROGRAMMATIC_SCROLL_SETTLING_PASSES = 4
