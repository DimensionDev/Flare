@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.compose.nativekit.appkit

import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitWidget
import dev.dimension.compose.nativekit.collectioninterop.NativeKitCollectionLayoutProtocol
import dev.dimension.compose.nativekit.lazy.LazyCollectionModel
import dev.dimension.compose.nativekit.lazy.LazyCollectionWidget
import dev.dimension.compose.nativekit.lazy.LazyCrossAxisAlignment
import dev.dimension.compose.nativekit.lazy.LazyListOrientation
import dev.dimension.compose.nativekit.lazy.NativeLazyCollection
import dev.dimension.compose.nativekit.lazy.NativeLazyCollectionController
import dev.dimension.compose.nativekit.lazy.NativeLazyItem
import dev.dimension.compose.nativekit.lazy.NativeLazyViewport
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import platform.AppKit.NSAnimationContext
import platform.AppKit.NSCollectionView
import platform.AppKit.NSCollectionViewDataSourceProtocol
import platform.AppKit.NSCollectionViewDelegateProtocol
import platform.AppKit.NSCollectionViewFlowLayout
import platform.AppKit.NSCollectionViewItem
import platform.AppKit.NSCollectionViewLayoutAttributes
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionHorizontal
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionVertical
import platform.AppKit.NSColor
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
import platform.AppKit.widthAnchor
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSIndexPath
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal class AppKitLazyCollectionWidget :
    AbstractAppKitWidget<AppKitLazyScrollView>(AppKitLazyScrollView()),
    LazyCollectionWidget {
    private val collection = AppKitLazyCollectionView()
    private val native = AppKitNativeLazyCollection(view, collection)
    private val controller = NativeLazyCollectionController(native, Dispatchers.Main.immediate)

    init {
        native.controller = controller
        collection.lazyLayout.controller = controller
        collection.collectionViewLayout = collection.lazyLayout
        collection.backgroundColors = listOf(NSColor.clearColor)
        collection.selectable = false
        native.attach()
        view.drawsBackground = false
        view.documentView = collection
        view.onLayout = controller::scheduleLayout
        native.observeScrolling()
    }

    override fun setModel(model: LazyCollectionModel) = controller.setModel(model)

    override fun dispose() {
        view.onLayout = null
        collection.dataSource = null
        collection.delegate = null
        native.dispose()
        try {
            controller.dispose()
        } finally {
            collection.lazyLayout.controller = null
        }
    }
}

private class AppKitNativeLazyCollection(
    private val view: AppKitLazyScrollView,
    private val collection: AppKitLazyCollectionView,
) : NativeLazyCollection {
    lateinit var controller: NativeLazyCollectionController
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val notifications = mutableListOf<NSObjectProtocol>()
    private val registeredIdentifiers = mutableSetOf<String>()
    private val itemClass = AppKitLazyCollectionItem(null, null).`class`()
    private var animationGeneration = 0

    fun observeScrolling() {
        view.contentView().postsBoundsChangedNotifications = true
        notifications +=
            notificationCenter.addObserverForName(
                NSViewBoundsDidChangeNotification,
                view.contentView(),
                NSOperationQueue.mainQueue,
            ) { controller.scheduleLayout() }
        notifications +=
            notificationCenter.addObserverForName(
                NSScrollViewWillStartLiveScrollNotification,
                view,
                NSOperationQueue.mainQueue,
            ) { controller.beginPhysicalScroll() }
        notifications +=
            notificationCenter.addObserverForName(
                NSScrollViewDidEndLiveScrollNotification,
                view,
                NSOperationQueue.mainQueue,
            ) { controller.endPhysicalScroll() }
    }

    fun dispose() {
        notifications.forEach(notificationCenter::removeObserver)
        notifications.clear()
    }

    private val delegate: NSObject =
        object : NSObject(), NSCollectionViewDataSourceProtocol, NSCollectionViewDelegateProtocol {
            override fun collectionView(
                collectionView: NSCollectionView,
                numberOfItemsInSection: Long,
            ): Long = controller.itemCount.toLong()

            override fun collectionView(
                collectionView: NSCollectionView,
                itemForRepresentedObjectAtIndexPath: NSIndexPath,
            ): NSCollectionViewItem {
                val identifier = controller.reuseIdentifier(itemForRepresentedObjectAtIndexPath.item.toInt())
                if (registeredIdentifiers.add(identifier)) {
                    collectionView.registerClass(itemClass, forItemWithIdentifier = identifier)
                }
                val item = collectionView.makeItemWithIdentifier(identifier, itemForRepresentedObjectAtIndexPath)
                controller.bind((item.view as AppKitLazyItemView).item, itemForRepresentedObjectAtIndexPath.item.toInt())
                return item
            }

            override fun collectionView(
                collectionView: NSCollectionView,
                didEndDisplayingItem: NSCollectionViewItem,
                forRepresentedObjectAtIndexPath: NSIndexPath,
            ) {
                controller.release((didEndDisplayingItem.view as AppKitLazyItemView).item)
            }
        }

    fun attach() {
        collection.dataSource = delegate as NSCollectionViewDataSourceProtocol
        collection.delegate = delegate as NSCollectionViewDelegateProtocol
    }

    override fun viewport(orientation: LazyListOrientation) =
        NativeLazyViewport(
            view.mainAxisOffset(orientation),
            view.mainAxisViewport(orientation),
            view.crossAxisExtent(orientation),
        )

    override val isPhysicalScrollInProgress: Boolean get() = false

    override fun reloadData() = collection.reloadData()

    override fun updateItems(
        index: Int,
        removedCount: Int,
        insertedCount: Int,
        apply: () -> Double?,
    ) {
        NSAnimationContext.runAnimationGroup({ context ->
            context?.duration = 0.0
            context?.allowsImplicitAnimation = false
            collection.performBatchUpdates({
                val offset = apply()
                if (removedCount >
                    0
                ) {
                    collection.deleteItemsAtIndexPaths(
                        (index until index + removedCount).map { NSIndexPath.indexPathForItem(it.toLong(), 0) }.toSet(),
                    )
                }
                if (insertedCount >
                    0
                ) {
                    collection.insertItemsAtIndexPaths(
                        (index until index + insertedCount).map { NSIndexPath.indexPathForItem(it.toLong(), 0) }.toSet(),
                    )
                }
                if (offset != null) {
                    collection.setFrameSize(collection.lazyLayout.collectionViewContentSize())
                    view.contentView().setBoundsOrigin(checkNotNull(controller.model).mainAxisPoint(offset))
                    view.reflectScrolledClipView(view.contentView())
                }
            }, completionHandler = null)
            collection.layoutSubtreeIfNeeded()
        }, completionHandler = null)
    }

    override fun invalidateLayout() {
        collection.lazyLayout.invalidateLayout()
    }

    override fun layoutIfNeeded() {
        // AppKit clamps custom-layout document width to the viewport unless its layout
        // advertises horizontal scrolling through NSCollectionViewFlowLayout.
        collection.lazyLayout.scrollDirection =
            if (controller.model?.orientation == LazyListOrientation.Horizontal) {
                NSCollectionViewScrollDirectionHorizontal
            } else {
                NSCollectionViewScrollDirectionVertical
            }
        // AppKit's document view must grow along either axis for NSClipView to expose
        // the full custom layout, including horizontal collections.
        val size = collection.lazyLayout.collectionViewContentSize()
        val changed =
            collection.frame.useContents { this.size.width to this.size.height } !=
                size.useContents { width to height }
        if (changed) collection.setFrameSize(size)
        collection.layoutSubtreeIfNeeded()
        // NSCollectionView configures its enclosing scrollers during its first layout.
        val vertical = controller.model?.orientation != LazyListOrientation.Horizontal
        view.hasVerticalScroller = vertical
        view.hasHorizontalScroller = !vertical
        view.autohidesScrollers = true
    }

    override fun schedule(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

    override fun scrollTo(
        offset: Double,
        animated: Boolean,
        completion: () -> Unit,
    ) {
        val point = checkNotNull(controller.model).mainAxisPoint(offset)
        if (animated) {
            val generation = ++animationGeneration
            NSAnimationContext.runAnimationGroup(
                changes = { context ->
                    context?.duration = 0.25
                    view.contentView().animator().setBoundsOrigin(point)
                },
                completionHandler = { if (animationGeneration == generation) completion() },
            )
        } else {
            view.contentView().setBoundsOrigin(point)
            view.reflectScrolledClipView(view.contentView())
            completion()
        }
    }

    override fun stopAnimatedScroll() {
        animationGeneration += 1
        val clip = view.contentView()
        clip.setBoundsOrigin(clip.bounds.useContents { CGPointMake(origin.x, origin.y) })
        view.reflectScrolledClipView(clip)
    }
}

internal class AppKitLazyScrollView : NSScrollView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onLayout: (() -> Unit)? = null

    override fun layout() {
        super.layout()
        onLayout?.invoke()
    }
}

private class AppKitLazyCollectionView : NSCollectionView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    val lazyLayout = AppKitLazyCollectionLayout()

    override fun isFlipped(): Boolean = true
}

private class AppKitLazyCollectionItem : NSCollectionViewItem {
    @OverrideInit
    constructor(nibName: String?, bundle: NSBundle?) : super(nibName, bundle)

    override fun loadView() {
        view = AppKitLazyItemView()
    }
}

private class AppKitLazyCollectionLayout :
    NSCollectionViewFlowLayout(),
    NativeKitCollectionLayoutProtocol {
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
        val controller = controller ?: return emptyList<NSCollectionViewLayoutAttributes>()
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

    override fun layoutAttributesForItemAtIndexPath(indexPath: NSIndexPath): NSCollectionViewLayoutAttributes? {
        val controller = controller ?: return null
        val model = controller.model ?: return null
        val index = indexPath.item.toInt()
        if (index !in 0 until controller.itemCount) return null
        return NSCollectionViewLayoutAttributes.layoutAttributesForItemWithIndexPath(indexPath).apply {
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

private class AppKitLazyItemView : NSStackView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val stretchConstraints = mutableListOf<NSLayoutConstraint>()
    private var lazyOrientation: LazyListOrientation = LazyListOrientation.Vertical
    private var stretchesCrossAxis: Boolean = false
    val item =
        object : NativeLazyItem {
            override val children = AppKitLazyChildren(this@AppKitLazyItemView)

            override fun configure(model: LazyCollectionModel) = this@AppKitLazyItemView.configure(model)

            override fun measure(
                orientation: LazyListOrientation,
                crossExtent: Double,
            ): Double = this@AppKitLazyItemView.measure(orientation, crossExtent)
        }

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

    fun rebuildStretchConstraints() {
        NSLayoutConstraint.deactivateConstraints(stretchConstraints)
        stretchConstraints.clear()
        if (stretchesCrossAxis) {
            arrangedSubviews.forEach { installStretchConstraint(it as NSView) }
        }
    }

    fun measure(
        orientation: LazyListOrientation,
        crossExtent: Double,
    ): Double {
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

private class AppKitLazyChildren(
    private val parent: AppKitLazyItemView,
) : NativeKitChildren {
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
        widget: NativeKitWidget,
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

private const val LAZY_STRETCH_PRIORITY: Float = 999f
