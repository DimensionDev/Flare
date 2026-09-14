@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.collectioninterop.FlareCollectionLayoutProtocol
import dev.dimension.flare.ui.lazy.LazyCollectionModel
import dev.dimension.flare.ui.lazy.LazyCollectionWidget
import dev.dimension.flare.ui.lazy.LazyCrossAxisAlignment
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.NativeLazyCollection
import dev.dimension.flare.ui.lazy.NativeLazyCollectionController
import dev.dimension.flare.ui.lazy.NativeLazyItem
import dev.dimension.flare.ui.lazy.NativeLazyViewport
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSIndexPath
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewDataSourceProtocol
import platform.UIKit.UICollectionViewDelegateProtocol
import platform.UIKit.UICollectionViewLayout
import platform.UIKit.UICollectionViewLayoutAttributes
import platform.UIKit.UIColor
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UILayoutFittingCompressedSize
import platform.UIKit.UILayoutPriorityFittingSizeLevel
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentBottom
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFill
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIStackViewAlignmentTrailing
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.indexPathForItem
import platform.UIKit.item
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal class UIKitLazyCollectionWidget :
    AbstractUIKitWidget<UIKitLazyCollectionView>(UIKitLazyCollectionView()),
    LazyCollectionWidget {
    private val native = UIKitNativeLazyCollection(view)
    private val controller = NativeLazyCollectionController(native, Dispatchers.Main.immediate)

    init {
        native.controller = controller
        view.lazyLayout.controller = controller
        view.onLayout = controller::scheduleLayout
        view.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever
        view.backgroundColor = UIColor.clearColor
        view.prefetchingEnabled = false
        view.allowsSelection = false
        native.attach()
    }

    override fun setModel(model: LazyCollectionModel) = controller.setModel(model)

    override fun dispose() {
        view.onLayout = null
        view.dataSource = null
        view.delegate = null
        try {
            controller.dispose()
        } finally {
            view.lazyLayout.controller = null
        }
    }
}

private class UIKitNativeLazyCollection(
    private val view: UIKitLazyCollectionView,
) : NativeLazyCollection {
    lateinit var controller: NativeLazyCollectionController
    private val registeredIdentifiers = mutableSetOf<String>()
    private var animationCompletion: (() -> Unit)? = null

    private val delegate: NSObject =
        object : NSObject(), UICollectionViewDataSourceProtocol, UICollectionViewDelegateProtocol {
            override fun collectionView(
                collectionView: UICollectionView,
                numberOfItemsInSection: Long,
            ): Long = controller.itemCount.toLong()

            override fun collectionView(
                collectionView: UICollectionView,
                cellForItemAtIndexPath: NSIndexPath,
            ): UICollectionViewCell {
                val identifier = controller.reuseIdentifier(cellForItemAtIndexPath.item.toInt())
                if (registeredIdentifiers.add(identifier)) {
                    collectionView.registerClass(UICollectionViewCell, forCellWithReuseIdentifier = identifier)
                }
                val cell = collectionView.dequeueReusableCellWithReuseIdentifier(identifier, cellForItemAtIndexPath)
                val root =
                    cell.contentView.subviews
                        .filterIsInstance<UIKitLazyItemView>()
                        .singleOrNull()
                        ?: UIKitLazyItemView().also { root ->
                            root.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                            root.setFrame(cell.contentView.bounds)
                            cell.contentView.addSubview(root)
                        }
                controller.bind(root.item, cellForItemAtIndexPath.item.toInt())
                return cell
            }

            override fun collectionView(
                collectionView: UICollectionView,
                didEndDisplayingCell: UICollectionViewCell,
                forItemAtIndexPath: NSIndexPath,
            ) {
                didEndDisplayingCell.contentView.subviews
                    .filterIsInstance<UIKitLazyItemView>()
                    .forEach { controller.release(it.item) }
            }

            override fun scrollViewDidScroll(scrollView: UIScrollView) = controller.scheduleLayout()

            override fun scrollViewWillBeginDragging(scrollView: UIScrollView) = controller.beginPhysicalScroll()

            override fun scrollViewDidEndDecelerating(scrollView: UIScrollView) = controller.endPhysicalScroll()

            override fun scrollViewDidEndDragging(
                scrollView: UIScrollView,
                willDecelerate: Boolean,
            ) {
                if (!willDecelerate) controller.endPhysicalScroll()
            }

            override fun scrollViewDidEndScrollingAnimation(scrollView: UIScrollView) {
                val completion = animationCompletion
                animationCompletion = null
                completion?.invoke()
            }
        }

    fun attach() {
        view.dataSource = delegate as UICollectionViewDataSourceProtocol
        view.delegate = delegate as UICollectionViewDelegateProtocol
    }

    override fun viewport(orientation: LazyListOrientation) =
        NativeLazyViewport(
            view.mainAxisOffset(orientation),
            view.mainAxisViewport(orientation),
            view.crossAxisExtent(orientation),
        )

    override val isPhysicalScrollInProgress: Boolean
        get() = view.tracking || view.dragging || view.decelerating

    override fun reloadData() = view.reloadData()

    override fun updateItems(
        index: Int,
        removedCount: Int,
        insertedCount: Int,
        apply: () -> Double?,
    ) {
        UIView.performWithoutAnimation {
            view.performBatchUpdates({
                val offset = apply()
                if (removedCount >
                    0
                ) {
                    view.deleteItemsAtIndexPaths(
                        (index until index + removedCount).map { NSIndexPath.indexPathForItem(it.toLong(), 0) },
                    )
                }
                if (insertedCount >
                    0
                ) {
                    view.insertItemsAtIndexPaths(
                        (index until index + insertedCount).map { NSIndexPath.indexPathForItem(it.toLong(), 0) },
                    )
                }
                if (offset != null) view.setContentOffset(checkNotNull(controller.model).mainAxisPoint(offset), animated = false)
            }, completion = null)
            view.layoutIfNeeded()
        }
    }

    override fun invalidateLayout() {
        val vertical = controller.model?.orientation != LazyListOrientation.Horizontal
        view.alwaysBounceVertical = vertical
        view.alwaysBounceHorizontal = !vertical
        view.showsVerticalScrollIndicator = vertical
        view.showsHorizontalScrollIndicator = !vertical
        view.lazyLayout.invalidateLayout()
    }

    override fun layoutIfNeeded() = view.layoutIfNeeded()

    override fun schedule(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

    override fun scrollTo(
        offset: Double,
        animated: Boolean,
        completion: () -> Unit,
    ) {
        animationCompletion = if (animated) completion else null
        view.setContentOffset(checkNotNull(controller.model).mainAxisPoint(offset), animated)
        if (!animated) completion()
    }

    override fun stopAnimatedScroll() {
        animationCompletion = null
        view.setContentOffset(view.contentOffset, animated = false)
    }
}

internal class UIKitLazyCollectionView :
    UICollectionView(
        frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
        collectionViewLayout = UIKitLazyCollectionLayout(),
    ) {
    val lazyLayout: UIKitLazyCollectionLayout get() = collectionViewLayout as UIKitLazyCollectionLayout
    var onLayout: (() -> Unit)? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        onLayout?.invoke()
    }
}

internal class UIKitLazyCollectionLayout :
    UICollectionViewLayout(),
    FlareCollectionLayoutProtocol {
    var controller: NativeLazyCollectionController? = null

    override fun prepareLayout() = Unit

    override fun collectionViewContentSize() =
        controller?.let {
            if (it.model?.orientation == LazyListOrientation.Horizontal) {
                CGSizeMake(it.contentExtent, it.crossExtent)
            } else {
                CGSizeMake(it.crossExtent, it.contentExtent)
            }
        } ?: CGSizeMake(0.0, 0.0)

    override fun layoutAttributesForElementsInRect(rect: CValue<CGRect>): List<*> {
        val controller = controller ?: return emptyList<UICollectionViewLayoutAttributes>()
        val range =
            rect.useContents {
                if (controller.model?.orientation == LazyListOrientation.Horizontal) {
                    controller.itemRange(origin.x, origin.x + size.width)
                } else {
                    controller.itemRange(origin.y, origin.y + size.height)
                }
            }
        return range.mapNotNull { layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(it.toLong(), 0)) }
    }

    override fun layoutAttributesForItemAtIndexPath(indexPath: NSIndexPath): UICollectionViewLayoutAttributes? {
        val controller = controller ?: return null
        val model = controller.model ?: return null
        val index = indexPath.item.toInt()
        if (index !in 0 until controller.itemCount) return null
        return UICollectionViewLayoutAttributes.layoutAttributesForCellWithIndexPath(indexPath).apply {
            frame = model.itemFrame(controller.itemStart(index), controller.itemExtent(index), controller.crossExtent)
        }
    }

    override fun shouldInvalidateLayoutForBoundsChange(newBounds: CValue<CGRect>): Boolean {
        val oldBounds = collectionView?.bounds ?: return false
        val horizontal = controller?.model?.orientation == LazyListOrientation.Horizontal
        val previous = oldBounds.useContents { if (horizontal) size.height else size.width }
        val next = newBounds.useContents { if (horizontal) size.height else size.width }
        return kotlin.math.abs(previous - next) > 0.5
    }
}

private class UIKitLazyItemView : UIStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    val item =
        object : NativeLazyItem {
            override val children = UIKitChildren(this@UIKitLazyItemView)

            override fun configure(model: LazyCollectionModel) = this@UIKitLazyItemView.configure(model)

            override fun measure(
                orientation: LazyListOrientation,
                crossExtent: Double,
            ): Double = this@UIKitLazyItemView.measure(orientation, crossExtent)
        }
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

    fun measure(
        orientation: LazyListOrientation,
        crossExtent: Double,
    ): Double {
        val target =
            when (orientation) {
                LazyListOrientation.Vertical -> CGSizeMake(crossExtent, UILayoutFittingCompressedSize.height)
                LazyListOrientation.Horizontal -> CGSizeMake(UILayoutFittingCompressedSize.width, crossExtent)
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
