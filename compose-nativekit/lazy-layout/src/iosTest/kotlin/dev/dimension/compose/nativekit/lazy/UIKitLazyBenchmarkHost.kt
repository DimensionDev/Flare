@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.lazy

import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.uikit.NativeKitUIKitHost
import dev.dimension.compose.nativekit.uikit.UIKitLazyLayoutRendererPlugin
import dev.dimension.compose.nativekit.uikit.createUIKitWidgetSystem
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UIWindow
import platform.UIKit.item
import kotlin.math.abs

internal actual fun createAppleLazyBenchmarkHost(embedded: Boolean): AppleLazyBenchmarkHost = UIKitLazyBenchmarkHost(embedded)

private class UIKitLazyBenchmarkHost(
    embedded: Boolean,
) : AppleLazyBenchmarkHost {
    private val window = if (embedded) null else UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 780.0))
    private val host = NativeKitUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
    private val collection: UICollectionView?
        get() =
            host.view.arrangedSubviews
                .filterIsInstance<UICollectionView>()
                .singleOrNull()

    init {
        host.view.setFrame(CGRectMake(0.0, 0.0, 390.0, 780.0))
        window?.addSubview(host.view)
        window?.hidden = false
    }

    override val nativeView: platform.darwin.NSObject get() = host.view

    override val platform: String = "ios_simulator"

    override fun setContent(content: NativeKitContent) = host.setContent(content)

    override fun layout() {
        host.view.layoutIfNeeded()
        collection?.layoutIfNeeded()
    }

    override fun offset(horizontal: Boolean): Double = checkNotNull(collection).contentOffset.useContents { if (horizontal) x else y }

    override fun viewportSize(): Pair<Double, Double> = checkNotNull(collection).bounds.useContents { size.width to size.height }

    override fun scrollTo(
        offset: Double,
        horizontal: Boolean,
    ) {
        checkNotNull(collection).setContentOffset(
            if (horizontal) CGPointMake(offset, 0.0) else CGPointMake(0.0, offset),
            animated = false,
        )
    }

    override fun physicalScroll(active: Boolean) {
        val view = checkNotNull(collection)
        if (active) view.delegate?.scrollViewWillBeginDragging(view) else view.delegate?.scrollViewDidEndDragging(view, false)
    }

    override fun matches(
        state: LazyListState,
        horizontal: Boolean,
    ): Boolean {
        val view = collection ?: return false
        val cells = view.visibleCells.filterIsInstance<UICollectionViewCell>().associateBy { view.indexPathForCell(it)?.item?.toInt() }
        val offset = offset(horizontal)
        return state.layoutInfo.visibleItems.all { item ->
            cells[item.index]?.frame?.useContents {
                abs((if (horizontal) origin.x else origin.y) - offset - item.offset) < 1.0 &&
                    abs((if (horizontal) size.width else size.height) - item.size) < 1.0
            } == true
        }
    }

    override fun nativeItemIds(): List<Int> = checkNotNull(collection).visibleCells.map { it.hashCode() }

    override fun dispose() {
        host.dispose()
        window?.hidden = true
    }
}
