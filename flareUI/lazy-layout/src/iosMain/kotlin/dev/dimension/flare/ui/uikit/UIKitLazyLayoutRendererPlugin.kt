@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareRendererPlugin
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
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSIndexPath
import platform.UIKit.NSCollectionLayoutDimension
import platform.UIKit.NSCollectionLayoutGroup
import platform.UIKit.NSCollectionLayoutItem
import platform.UIKit.NSCollectionLayoutSection
import platform.UIKit.NSCollectionLayoutSize
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewCompositionalLayout
import platform.UIKit.UICollectionViewCompositionalLayoutConfiguration
import platform.UIKit.UICollectionViewDataSourceProtocol
import platform.UIKit.UICollectionViewDelegateProtocol
import platform.UIKit.UICollectionViewScrollDirection.UICollectionViewScrollDirectionHorizontal
import platform.UIKit.UICollectionViewScrollDirection.UICollectionViewScrollDirectionVertical
import platform.UIKit.UICollectionViewScrollPositionLeft
import platform.UIKit.UICollectionViewScrollPositionTop
import platform.UIKit.UIContentInsetsReference.UIContentInsetsReferenceNone
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentBottom
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFill
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIStackViewAlignmentTrailing
import platform.UIKit.indexPathForItem
import platform.UIKit.item
import platform.UIKit.layoutAttributesForItemAtIndexPath
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** UICollectionView renderer for Flare lazy collections. */
public object UIKitLazyLayoutRendererPlugin : FlareRendererPlugin<UIKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            UIKitLazyCollectionWidget()
        }
    }
}

private class UIKitLazyCollectionWidget :
    AbstractUIKitWidget<UICollectionView>(
        view =
            UICollectionView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                collectionViewLayout = createUIKitLazyLayout(LazyListOrientation.Vertical, 0f),
            ),
    ),
    LazyCollectionWidget {
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = ::applyModel,
            onScroll = ::performScroll,
        )
    private val bridge = UIKitLazyCollectionBridge(view, coordinator)
    private var pendingAnchor: UIKitLazyAnchor? = null
    private var pendingDiffIsSafe: Boolean = false

    init {
        view.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever
        view.dataSource = bridge
        view.delegate = bridge
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
        view.dataSource = null
        view.delegate = null
        bridge.dispose()
        coordinator.dispose()
    }

    private fun applyModel(
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ): LazyRealizedItemUpdate {
        val anchor = pendingAnchor
        val layoutChanged =
            previous == null ||
                previous.orientation != current.orientation ||
                previous.spacing != current.spacing
        if (layoutChanged) {
            view.setCollectionViewLayout(
                layout = createUIKitLazyLayout(current.orientation, current.spacing),
                animated = false,
            )
        }
        val vertical = current.orientation == LazyListOrientation.Vertical
        view.alwaysBounceVertical = vertical
        view.alwaysBounceHorizontal = !vertical
        view.showsVerticalScrollIndicator = vertical
        view.showsHorizontalScrollIndicator = !vertical
        val finishUpdate = finishUpdate@{
            if (coordinator.model !== current) return@finishUpdate
            view.layoutIfNeeded()
            anchor?.let { bridge.restoreAnchor(it, current) }
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

private class UIKitLazyCollectionBridge(
    private val collectionView: UICollectionView,
    private val coordinator: LazyCollectionCoordinator,
) : NSObject(),
    UICollectionViewDataSourceProtocol,
    UICollectionViewDelegateProtocol {
    private val bindings = mutableMapOf<UICollectionViewCell, UIKitCellBinding>()
    private val reuseIdentifiers = mutableMapOf<Any, String>()
    private var nextReuseIdentifier = 0
    private var pendingAnimatedScroll: LazyListScrollRequest? = null
    private var presentedItemCount: Int = 0
    private var updateGeneration: Long = 0L
    private var disposed: Boolean = false

    override fun numberOfSectionsInCollectionView(collectionView: UICollectionView): Long = 1L

    override fun collectionView(
        collectionView: UICollectionView,
        numberOfItemsInSection: Long,
    ): Long = presentedItemCount.toLong()

    override fun collectionView(
        collectionView: UICollectionView,
        cellForItemAtIndexPath: NSIndexPath,
    ): UICollectionViewCell {
        val model = checkNotNull(coordinator.model)
        val index = cellForItemAtIndexPath.item.toInt()
        val identifier = reuseIdentifier(model, index)
        val cell =
            collectionView.dequeueReusableCellWithReuseIdentifier(
                identifier = identifier,
                forIndexPath = cellForItemAtIndexPath,
            )
        val existingBinding = bindings[cell]
        val binding =
            if (existingBinding?.isActive == true) {
                existingBinding
            } else {
                existingBinding?.dispose()
                createBinding(cell, model).also { bindings[cell] = it }
            }
        binding.bind(model, index)
        return cell
    }

    override fun scrollViewDidScroll(scrollView: UIScrollView) {
        reportLayoutInfo()
    }

    override fun scrollViewWillBeginDragging(scrollView: UIScrollView) {
        coordinator.reportScrollInProgress(true)
    }

    override fun scrollViewDidEndDecelerating(scrollView: UIScrollView) {
        coordinator.reportScrollInProgress(false)
        reportLayoutInfo()
    }

    override fun scrollViewDidEndScrollingAnimation(scrollView: UIScrollView) {
        val request = pendingAnimatedScroll
        pendingAnimatedScroll = null
        request?.let(::applyRequestedOffset)
        request?.complete()
        coordinator.reportScrollInProgress(false)
        reportLayoutInfo()
    }

    fun captureAnchor(model: LazyCollectionModel): UIKitLazyAnchor? {
        val indexPath =
            collectionView.indexPathsForVisibleItems
                .map { it as NSIndexPath }
                .minByOrNull { it.item }
                ?: return null
        val index = indexPath.item.toInt()
        if (index !in 0 until model.itemProvider.itemCount) return null
        val attributes = collectionView.collectionViewLayout.layoutAttributesForItemAtIndexPath(indexPath) ?: return null
        val frameOrigin =
            attributes.frame.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> origin.y
                    LazyListOrientation.Horizontal -> origin.x
                }
            }
        val contentOffset =
            collectionView.contentOffset.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> y
                    LazyListOrientation.Horizontal -> x
                }
            }
        val boundKey =
            collectionView
                .cellForItemAtIndexPath(indexPath)
                ?.let(bindings::get)
                ?.key
        return UIKitLazyAnchor(boundKey ?: model.itemProvider.key(index), frameOrigin - contentOffset)
    }

    fun restoreAnchor(
        anchor: UIKitLazyAnchor,
        model: LazyCollectionModel,
    ) {
        val index = model.itemProvider.indexOfKey(anchor.key)
        if (disposed || index !in 0 until presentedItemCount) return
        val path = NSIndexPath.indexPathForItem(index.toLong(), inSection = 0L)
        collectionView.scrollToItemAtIndexPath(
            indexPath = path,
            atScrollPosition = model.orientation.startScrollPosition(),
            animated = false,
        )
        val attributes = collectionView.collectionViewLayout.layoutAttributesForItemAtIndexPath(path) ?: return
        val current = collectionView.contentOffset
        val itemOrigin =
            attributes.frame.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> origin.y
                    LazyListOrientation.Horizontal -> origin.x
                }
            }
        val target =
            when (model.orientation) {
                LazyListOrientation.Vertical -> {
                    CGPointMake(
                        current.useContents { x },
                        itemOrigin - anchor.offset,
                    )
                }

                LazyListOrientation.Horizontal -> {
                    CGPointMake(
                        itemOrigin - anchor.offset,
                        current.useContents { y },
                    )
                }
            }
        collectionView.setContentOffset(target, animated = false)
    }

    fun performScroll(request: LazyListScrollRequest) {
        val model = checkNotNull(coordinator.model)
        val path = NSIndexPath.indexPathForItem(request.index.toLong(), inSection = 0L)
        if (request.animated) {
            pendingAnimatedScroll?.cancel()
            pendingAnimatedScroll = request
            coordinator.reportScrollInProgress(true)
        }
        collectionView.scrollToItemAtIndexPath(
            indexPath = path,
            atScrollPosition = model.orientation.startScrollPosition(),
            animated = request.animated,
        )
        if (!request.animated) {
            collectionView.layoutIfNeeded()
            applyRequestedOffset(request)
            reportLayoutInfo()
            request.complete()
        }
    }

    fun reportLayoutInfo() {
        val model = coordinator.model ?: return
        val provider = model.itemProvider
        val viewportOrigin =
            collectionView.contentOffset.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> y
                    LazyListOrientation.Horizontal -> x
                }
            }
        val visible =
            collectionView.indexPathsForVisibleItems
                .map { it as NSIndexPath }
                .mapNotNull { path ->
                    val index = path.item.toInt()
                    if (index !in 0 until provider.itemCount) return@mapNotNull null
                    val attributes =
                        collectionView.collectionViewLayout.layoutAttributesForItemAtIndexPath(path)
                            ?: return@mapNotNull null
                    val offsetAndSize =
                        attributes.frame.useContents {
                            when (model.orientation) {
                                LazyListOrientation.Vertical -> origin.y to size.height
                                LazyListOrientation.Horizontal -> origin.x to size.width
                            }
                        }
                    LazyListItemInfo(
                        key = provider.key(index),
                        index = index,
                        offset = (offsetAndSize.first - viewportOrigin).toFloat(),
                        size = offsetAndSize.second.toFloat(),
                    )
                }.sortedBy(LazyListItemInfo::index)
        val viewport =
            collectionView.bounds.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> size.height
                    LazyListOrientation.Horizontal -> size.width
                }
            }
        coordinator.reportLayoutInfo(
            LazyListLayoutInfo(
                totalItemsCount = provider.itemCount,
                viewportStartOffset = 0f,
                viewportEndOffset = viewport.toFloat(),
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
                collectionView.reloadItemsAtIndexPaths(changes.reloadedIndices.map(::indexPath))
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
                    collectionView.deleteItemsAtIndexPaths(changes.removedIndices.map(::indexPath))
                }
                if (changes.insertedIndices.isNotEmpty()) {
                    collectionView.insertItemsAtIndexPaths(changes.insertedIndices.map(::indexPath))
                }
                changes.moves.forEach { move ->
                    collectionView.moveItemAtIndexPath(indexPath(move.fromIndex), indexPath(move.toIndex))
                }
            },
            completion = { _ -> reloadChangedTypes() },
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
        disposeBindings()
    }

    private fun reuseIdentifier(
        model: LazyCollectionModel,
        index: Int,
    ): String {
        val contentType = model.itemProvider.contentType(index) ?: NullContentType
        return reuseIdentifiers.getOrPut(contentType) {
            "FlareLazyCell-${nextReuseIdentifier++}".also { identifier ->
                collectionView.registerClass(
                    cellClass = UICollectionViewCell,
                    forCellWithReuseIdentifier = identifier,
                )
            }
        }
    }

    private fun createRoot(model: LazyCollectionModel): UIStackView =
        UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            configure(model)
        }

    private fun createBinding(
        cell: UICollectionViewCell,
        model: LazyCollectionModel,
    ): UIKitCellBinding {
        val root = createRoot(model)
        root.translatesAutoresizingMaskIntoConstraints = false
        cell.contentView.addSubview(root)
        val trailingConstraint = root.trailingAnchor.constraintEqualToAnchor(cell.contentView.trailingAnchor)
        val bottomConstraint = root.bottomAnchor.constraintEqualToAnchor(cell.contentView.bottomAnchor)
        when (model.orientation) {
            LazyListOrientation.Vertical -> bottomConstraint.priority = SELF_SIZING_END_PRIORITY
            LazyListOrientation.Horizontal -> trailingConstraint.priority = SELF_SIZING_END_PRIORITY
        }
        NSLayoutConstraint.activateConstraints(
            listOf(
                root.leadingAnchor.constraintEqualToAnchor(cell.contentView.leadingAnchor),
                trailingConstraint,
                root.topAnchor.constraintEqualToAnchor(cell.contentView.topAnchor),
                bottomConstraint,
            ),
        )
        return UIKitCellBinding(root, coordinator.createItemHost(UIKitChildren(root)))
    }

    private fun applyRequestedOffset(request: LazyListScrollRequest) {
        val model = coordinator.model ?: return
        collectionView.layoutIfNeeded()
        val path = NSIndexPath.indexPathForItem(request.index.toLong(), inSection = 0L)
        val itemOrigin =
            collectionView.collectionViewLayout
                .layoutAttributesForItemAtIndexPath(path)
                ?.frame
                ?.useContents {
                    when (model.orientation) {
                        LazyListOrientation.Vertical -> origin.y
                        LazyListOrientation.Horizontal -> origin.x
                    }
                } ?: return
        val current = collectionView.contentOffset
        val target =
            current.useContents {
                when (model.orientation) {
                    LazyListOrientation.Vertical -> CGPointMake(x, itemOrigin + request.scrollOffset)
                    LazyListOrientation.Horizontal -> CGPointMake(itemOrigin + request.scrollOffset, y)
                }
            }
        collectionView.setContentOffset(target, animated = false)
        collectionView.layoutIfNeeded()
    }

    private fun indexPath(index: Int): NSIndexPath = NSIndexPath.indexPathForItem(index.toLong(), inSection = 0L)

    private fun disposeBindings() {
        bindings.values.toList().forEach(UIKitCellBinding::dispose)
        bindings.clear()
    }

    private data object NullContentType
}

private class UIKitCellBinding(
    private val root: UIStackView,
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
    }

    fun dispose() {
        itemHost.dispose()
        root.removeFromSuperview()
    }
}

private fun UIStackView.configure(model: LazyCollectionModel) {
    when (model.orientation) {
        LazyListOrientation.Vertical -> {
            axis = UILayoutConstraintAxisVertical
            alignment = model.crossAxisAlignment.horizontalStackAlignment()
        }

        LazyListOrientation.Horizontal -> {
            axis = UILayoutConstraintAxisHorizontal
            alignment = model.crossAxisAlignment.verticalStackAlignment()
        }
    }
}

private data class UIKitLazyAnchor(
    val key: Any,
    val offset: Double,
)

private const val SELF_SIZING_END_PRIORITY: Float = 999f
private const val MAX_INCREMENTAL_DIFF_ITEMS: Int = 1_000

private fun LazyCrossAxisAlignment.horizontalStackAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start -> UIStackViewAlignmentLeading
        LazyCrossAxisAlignment.Center -> UIStackViewAlignmentCenter
        LazyCrossAxisAlignment.End -> UIStackViewAlignmentTrailing
        LazyCrossAxisAlignment.Stretch -> UIStackViewAlignmentFill
    }

private fun LazyCrossAxisAlignment.verticalStackAlignment(): Long =
    when (this) {
        LazyCrossAxisAlignment.Start -> UIStackViewAlignmentTop
        LazyCrossAxisAlignment.Center -> UIStackViewAlignmentCenter
        LazyCrossAxisAlignment.End -> UIStackViewAlignmentBottom
        LazyCrossAxisAlignment.Stretch -> UIStackViewAlignmentFill
    }

private fun LazyListOrientation.startScrollPosition() =
    when (this) {
        LazyListOrientation.Vertical -> UICollectionViewScrollPositionTop
        LazyListOrientation.Horizontal -> UICollectionViewScrollPositionLeft
    }

private fun dev.dimension.flare.ui.lazy.LazyItemProvider.indexOfKey(key: Any): Int {
    repeat(itemCount) { index ->
        if (key(index) == key) return index
    }
    return -1
}

private fun createUIKitLazyLayout(
    orientation: LazyListOrientation,
    spacing: Float,
): UICollectionViewCompositionalLayout {
    val itemSize =
        when (orientation) {
            LazyListOrientation.Vertical -> {
                NSCollectionLayoutSize.sizeWithWidthDimension(
                    width = NSCollectionLayoutDimension.fractionalWidthDimension(1.0),
                    heightDimension = NSCollectionLayoutDimension.estimatedDimension(DEFAULT_ESTIMATED_EXTENT),
                )
            }

            LazyListOrientation.Horizontal -> {
                NSCollectionLayoutSize.sizeWithWidthDimension(
                    width = NSCollectionLayoutDimension.estimatedDimension(DEFAULT_ESTIMATED_EXTENT),
                    heightDimension = NSCollectionLayoutDimension.fractionalHeightDimension(1.0),
                )
            }
        }
    val item = NSCollectionLayoutItem.itemWithLayoutSize(itemSize)
    val group =
        when (orientation) {
            LazyListOrientation.Vertical -> {
                NSCollectionLayoutGroup.verticalGroupWithLayoutSize(
                    layoutSize = itemSize,
                    repeatingSubitem = item,
                    count = 1L,
                )
            }

            LazyListOrientation.Horizontal -> {
                NSCollectionLayoutGroup.horizontalGroupWithLayoutSize(
                    layoutSize = itemSize,
                    repeatingSubitem = item,
                    count = 1L,
                )
            }
        }
    val section =
        NSCollectionLayoutSection.sectionWithGroup(group).apply {
            interGroupSpacing = spacing.toDouble()
        }
    val configuration =
        UICollectionViewCompositionalLayoutConfiguration().apply {
            scrollDirection = orientation.uikitScrollDirection()
            contentInsetsReference = UIContentInsetsReferenceNone
        }
    return UICollectionViewCompositionalLayout(section, configuration)
}

private fun LazyListOrientation.uikitScrollDirection() =
    when (this) {
        LazyListOrientation.Vertical -> UICollectionViewScrollDirectionVertical
        LazyListOrientation.Horizontal -> UICollectionViewScrollDirectionHorizontal
    }

private const val DEFAULT_ESTIMATED_EXTENT = 48.0
