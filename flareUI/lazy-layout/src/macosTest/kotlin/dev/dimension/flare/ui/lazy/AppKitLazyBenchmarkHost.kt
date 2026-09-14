@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.lazy

import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.appkit.AppKitLazyLayoutRendererPlugin
import dev.dimension.flare.ui.appkit.FlareAppKitHost
import dev.dimension.flare.ui.appkit.createAppKitWidgetSystem
import kotlinx.cinterop.useContents
import platform.AppKit.NSApplication
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSCollectionView
import platform.AppKit.NSCollectionViewItem
import platform.AppKit.NSScrollView
import platform.AppKit.NSScrollViewDidEndLiveScrollNotification
import platform.AppKit.NSScrollViewWillStartLiveScrollNotification
import platform.AppKit.NSView
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowStyleMaskBorderless
import platform.AppKit.item
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import kotlin.math.abs

internal actual fun createAppleLazyBenchmarkHost(embedded: Boolean): AppleLazyBenchmarkHost {
    NSApplication.sharedApplication
    return AppKitLazyBenchmarkHost(embedded)
}

private class AppKitLazyBenchmarkHost(
    embedded: Boolean,
) : AppleLazyBenchmarkHost {
    private val rect = CGRectMake(0.0, 0.0, 390.0, 780.0)
    private val window =
        if (embedded) {
            null
        } else {
            NSWindow(
                contentRect = rect,
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        }
    private val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
    private val scroll: NSScrollView?
        get() =
            host.view.arrangedSubviews
                .filterIsInstance<NSScrollView>()
                .singleOrNull()
    private val collection: NSCollectionView?
        get() = scroll?.documentView as? NSCollectionView

    init {
        // Kotlin/Native owns this reference; close() must not release it a second time.
        window?.releasedWhenClosed = false
        val root = NSView(frame = rect)
        window?.contentView = root
        host.view.frame = root.bounds
        root.addSubview(host.view)
        window?.makeKeyAndOrderFront(null)
    }

    override val nativeView: platform.darwin.NSObject get() = host.view

    override val platform: String = "macos"

    override fun setContent(content: FlareContent) = host.setContent(content)

    override fun layout() {
        host.view.layoutSubtreeIfNeeded()
        scroll?.layoutSubtreeIfNeeded()
        collection?.layoutSubtreeIfNeeded()
    }

    override fun offset(horizontal: Boolean): Double =
        checkNotNull(scroll).contentView().bounds.useContents { if (horizontal) origin.x else origin.y }

    override fun viewportSize(): Pair<Double, Double> = checkNotNull(scroll).contentView().bounds.useContents { size.width to size.height }

    override fun scrollTo(
        offset: Double,
        horizontal: Boolean,
    ) {
        val view = checkNotNull(scroll)
        view.contentView().setBoundsOrigin(if (horizontal) CGPointMake(offset, 0.0) else CGPointMake(0.0, offset))
        view.reflectScrolledClipView(view.contentView())
    }

    override fun physicalScroll(active: Boolean) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            if (active) NSScrollViewWillStartLiveScrollNotification else NSScrollViewDidEndLiveScrollNotification,
            `object` = checkNotNull(scroll),
        )
    }

    override fun matches(
        state: LazyListState,
        horizontal: Boolean,
    ): Boolean {
        val view = collection ?: return false
        val items = view.visibleItems().filterIsInstance<NSCollectionViewItem>().associateBy { view.indexPathForItem(it)?.item?.toInt() }
        val offset = offset(horizontal)
        return state.layoutInfo.visibleItems.all { item ->
            items[item.index]?.view?.frame?.useContents {
                abs((if (horizontal) origin.x else origin.y) - offset - item.offset) < 1.0 &&
                    abs((if (horizontal) size.width else size.height) - item.size) < 1.0
            } == true
        }
    }

    override fun nativeItemIds(): List<Int> = checkNotNull(collection).visibleItems().map { it.hashCode() }

    override fun dispose() {
        host.dispose()
        window?.close()
    }
}
