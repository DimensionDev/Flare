@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.appkit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import dev.dimension.flare.ui.lazy.LazyColumn
import dev.dimension.flare.ui.lazy.LazyListState
import dev.dimension.flare.ui.lazy.LazyRow
import dev.dimension.flare.ui.lazy.awaitAppleUi
import dev.dimension.flare.ui.lazy.items
import kotlinx.cinterop.useContents
import kotlinx.coroutines.runBlocking
import platform.AppKit.NSApplication
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSCollectionView
import platform.AppKit.NSCollectionViewFlowLayout
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionHorizontal
import platform.AppKit.NSCollectionViewScrollDirection.NSCollectionViewScrollDirectionVertical
import platform.AppKit.NSCollectionViewScrollPositionTop
import platform.AppKit.NSScrollView
import platform.AppKit.NSView
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowStyleMaskBorderless
import platform.AppKit.alignmentRectForFrame
import platform.AppKit.indexPathForItem
import platform.AppKit.layoutAttributesForItemAtIndexPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSIndexPath
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class AppKitLazyListTest {
    @Test
    public fun largeModelUpdateKeepsKeyResolutionViewportBound() {
        var count by mutableStateOf(0)
        var keyLookups = 0
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        val content: FlareContent = {
            LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                items(
                    count = 10_000 + count,
                    key = { index ->
                        keyLookups += 1
                        index - count
                    },
                ) { index -> Text("Item ${index - count}") }
            }
        }
        try {
            host.view.frame = root.bounds
            host.setContent(content)
            root.addSubview(host.view)
            awaitAppleUi("AppKit large lazy list did not realize its first viewport.") {
                val scroll = host.view.arrangedSubviews.singleOrNull() as? NSScrollView
                scroll?.frame = host.view.bounds
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                collection?.numberOfItemsInSection(0) == 10_000L &&
                    collection.visibleItems().isNotEmpty()
            }

            keyLookups = 0
            count = 1
            val collection =
                (host.view.arrangedSubviews.single() as NSScrollView).documentView as NSCollectionView
            awaitAppleUi("AppKit large lazy list did not apply its update.") {
                collection.layoutSubtreeIfNeeded()
                collection.numberOfItemsInSection(0) == 10_001L
            }

            assertTrue(keyLookups < 500, "Large-list update resolved $keyLookups keys instead of staying viewport-bound.")
        } finally {
            host.dispose()
            window.close()
        }
    }

    @Test
    public fun firstViewportDoesNotResolveEveryItemKey() {
        var keyLookups = 0
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            host.view.frame = root.bounds
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000,
                        key = { index ->
                            keyLookups += 1
                            index
                        },
                    ) { index -> Text("Item $index") }
                }
            }
            root.addSubview(host.view)
            awaitAppleUi("AppKit lazy viewport did not realize items.") {
                val scroll = host.view.arrangedSubviews.singleOrNull() as? NSScrollView
                scroll?.frame = host.view.bounds
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                collection?.visibleItems()?.isNotEmpty() == true
            }

            assertTrue(keyLookups < 500, "First viewport resolved $keyLookups of 10,000 keys.")
        } finally {
            host.dispose()
            window.close()
        }
    }

    @Test
    public fun collectionViewSupportsBothLazyDirections() {
        assertTrue(NSThread.isMainThread)
        assertDirection(
            expected = NSCollectionViewScrollDirectionVertical,
            content = {
                LazyColumn {
                    items(
                        count = 10_000,
                        key = { index -> index },
                    ) { index -> Text("Item $index") }
                }
            },
        )
        assertDirection(
            expected = NSCollectionViewScrollDirectionHorizontal,
            content = {
                LazyRow {
                    items(
                        count = 10_000,
                        key = { index -> index },
                    ) { index -> Text("Item $index") }
                }
            },
        )
    }

    @Test
    public fun collectionViewUsesMeasuredVariableItemExtents() {
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            host.view.frame = root.bounds
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    item(key = "short") { Text("Short", modifier = FlareModifier.None.height(32f)) }
                    item(key = "tall") { Text("Tall", modifier = FlareModifier.None.height(88f)) }
                }
            }
            root.addSubview(host.view)
            awaitAppleUi("AppKit lazy items were not measured.") {
                val scroll = host.view.arrangedSubviews.singleOrNull() as? NSScrollView
                scroll?.frame = host.view.bounds
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                val layout = collection?.collectionViewLayout
                val first =
                    layout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
                        ?.frame
                        ?.useContents { size.height }
                val second =
                    layout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
                        ?.frame
                        ?.useContents { size.height }
                collection?.visibleItems()?.size == 2 && first == 32.0 && second == 88.0
            }

            val collection = (host.view.arrangedSubviews.single() as NSScrollView).documentView as NSCollectionView
            val layout = checkNotNull(collection.collectionViewLayout)
            val short = layout.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
            val tall = layout.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
            assertEquals(32.0, checkNotNull(short).frame.useContents { size.height }, absoluteTolerance = 1.0)
            assertEquals(88.0, checkNotNull(tall).frame.useContents { size.height }, absoluteTolerance = 1.0)
        } finally {
            host.dispose()
            window.close()
        }
    }

    @Test
    public fun lazyGeometryMatchesTheSharedSpacingAndAlignmentContract() {
        val state = LazyListState()
        val columnWindow =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val columnRoot = NSView(frame = columnWindow.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        columnWindow.contentView = columnRoot
        val columnHost = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            columnHost.view.frame = columnRoot.bounds
            columnHost.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.width(200f).height(120f),
                    state = state,
                    spacing = 6f,
                ) {
                    item(key = "first") { Text("First", modifier = FlareModifier.None.height(32f)) }
                    item(key = "second") { Text("Second", modifier = FlareModifier.None.height(48f)) }
                }
            }
            columnRoot.addSubview(columnHost.view)
            awaitAppleUi("AppKit column geometry did not settle.") {
                val scroll = columnHost.view.arrangedSubviews.singleOrNull() as? NSScrollView
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                val first =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
                val second =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
                first?.frame?.useContents { size.width == 200.0 && size.height == 32.0 && origin.y == 0.0 } == true &&
                    second?.frame?.useContents { size.height == 48.0 && origin.y == 38.0 } == true
            }

            val scroll = columnHost.view.arrangedSubviews.single() as NSScrollView
            assertTrue(scroll.hasVerticalScroller)
            assertTrue(!scroll.hasHorizontalScroller)
            runBlocking { state.scrollToItem(0) }
            val first = state.layoutInfo.visibleItems.single { it.key == "first" }
            val second = state.layoutInfo.visibleItems.single { it.key == "second" }
            assertEquals(0f, first.offset, absoluteTolerance = 0.5f)
            assertEquals(32f, first.size, absoluteTolerance = 0.5f)
            assertEquals(38f, second.offset, absoluteTolerance = 0.5f)
            assertEquals(48f, second.size, absoluteTolerance = 0.5f)
            val firstItem = checkNotNull((scroll.documentView as NSCollectionView).itemAtIndexPath(NSIndexPath.indexPathForItem(0, 0)))
            firstItem.view.layoutSubtreeIfNeeded()
            val firstLabel = firstItem.view.subviews.single() as NSView
            val firstLabelAlignmentRect = firstLabel.alignmentRectForFrame(firstLabel.frame)
            assertEquals(
                200.0,
                firstLabelAlignmentRect.useContents { size.width },
                absoluteTolerance = 1.0,
                message = "The default Stretch alignment must fill the lazy column cross axis.",
            )
        } finally {
            columnHost.dispose()
            columnWindow.close()
        }

        val rowWindow =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val rowRoot = NSView(frame = rowWindow.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        rowWindow.contentView = rowRoot
        val rowHost = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            rowHost.view.frame = rowRoot.bounds
            rowHost.setContent {
                LazyRow(
                    modifier = FlareModifier.None.width(200f).height(80f),
                    spacing = 6f,
                    verticalAlignment = VerticalAlignment.Center,
                ) {
                    item(key = "first") {
                        Text("First", modifier = FlareModifier.None.width(40f).height(24f))
                    }
                    item(key = "second") {
                        Text("Second", modifier = FlareModifier.None.width(60f).height(24f))
                    }
                }
            }
            rowRoot.addSubview(rowHost.view)
            awaitAppleUi("AppKit row geometry did not settle.") {
                val scroll = rowHost.view.arrangedSubviews.singleOrNull() as? NSScrollView
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                val first =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
                val second =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
                first?.frame?.useContents { size.width == 40.0 && size.height == 80.0 && origin.x == 0.0 } == true &&
                    second?.frame?.useContents { size.width == 60.0 && origin.x == 46.0 } == true
            }

            val scroll = rowHost.view.arrangedSubviews.single() as NSScrollView
            assertTrue(!scroll.hasVerticalScroller)
            assertTrue(scroll.hasHorizontalScroller)
            val collection = scroll.documentView as NSCollectionView
            val firstItem = checkNotNull(collection.itemAtIndexPath(NSIndexPath.indexPathForItem(0, 0)))
            val firstRoot = firstItem.view
            firstRoot.layoutSubtreeIfNeeded()
            val firstLabel = firstRoot.subviews.single() as NSView
            assertEquals(28.0, firstLabel.frame.useContents { origin.y }, absoluteTolerance = 1.0)
        } finally {
            rowHost.dispose()
            rowWindow.close()
        }
    }

    @Test
    public fun prependUsesBatchUpdatesAndKeepsTheStableKeyAnchor() {
        var items by mutableStateOf((0 until 100).toList())
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            host.view.frame = root.bounds
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(items = items, key = { it }) { item ->
                        Text("Item $item", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            root.addSubview(host.view)
            awaitAppleUi("AppKit lazy collection was not ready for scrolling.") {
                val scroll = host.view.arrangedSubviews.singleOrNull() as? NSScrollView
                scroll?.frame = host.view.bounds
                (scroll?.documentView as? NSCollectionView)?.numberOfItemsInSection(0) == 100L
            }
            val scroll = host.view.arrangedSubviews.single() as NSScrollView
            val collection = scroll.documentView as NSCollectionView
            val originalPath = NSIndexPath.indexPathForItem(20, 0)
            collection.scrollToItemsAtIndexPaths(setOf(originalPath), NSCollectionViewScrollPositionTop)
            var originalOrigin = Double.NaN
            var stableLayoutPasses = 0
            awaitAppleUi("AppKit lazy anchor did not settle before the update.") {
                collection.layoutSubtreeIfNeeded()
                collection.indexPathsForVisibleItems()
                collection.layoutSubtreeIfNeeded()
                val nextOrigin =
                    collection.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(originalPath)
                        ?.frame
                        ?.useContents { origin.y }
                        ?: return@awaitAppleUi false
                scroll.contentView().setBoundsOrigin(platform.CoreGraphics.CGPointMake(0.0, nextOrigin + 17.0))
                scroll.reflectScrolledClipView(scroll.contentView())
                collection.layoutSubtreeIfNeeded()
                collection.indexPathsForVisibleItems()
                val settledOrigin =
                    collection.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(originalPath)
                        ?.frame
                        ?.useContents { origin.y }
                        ?: return@awaitAppleUi false
                val settledOffset = scroll.contentView().bounds.useContents { origin.y }
                stableLayoutPasses =
                    if (kotlin.math.abs(settledOrigin - nextOrigin) < 0.1 &&
                        kotlin.math.abs(settledOffset - (settledOrigin + 17.0)) < 0.1
                    ) {
                        stableLayoutPasses + 1
                    } else {
                        0
                    }
                originalOrigin = settledOrigin
                stableLayoutPasses >= 2
            }
            val requestedOffset = originalOrigin + 17.0
            val appliedOffset = scroll.contentView().bounds.useContents { origin.y }
            val documentHeight = collection.frame.useContents { size.height }

            items = listOf(-2, -1) + items
            var diagnostic = "the update did not finish"
            try {
                awaitAppleUi("AppKit did not restore the stable-key anchor after prepend.") {
                    collection.layoutSubtreeIfNeeded()
                    val itemCount = collection.numberOfItemsInSection(0)
                    diagnostic = "item count=$itemCount"
                    if (itemCount != 102L) return@awaitAppleUi false
                    val restoredPath = NSIndexPath.indexPathForItem(22, 0)
                    val restoredOrigin =
                        collection.collectionViewLayout
                            ?.layoutAttributesForItemAtIndexPath(restoredPath)
                            ?.frame
                            ?.useContents { origin.y }
                            ?: return@awaitAppleUi false
                    val offset = scroll.contentView().bounds.useContents { origin.y }
                    diagnostic =
                        "item origin=$restoredOrigin, content offset=$offset, " +
                        "original origin=$originalOrigin, requested offset=$requestedOffset, " +
                        "applied offset=$appliedOffset, document height=$documentHeight"
                    kotlin.math.abs((restoredOrigin - offset) + 17.0) < 1.0
                }
            } catch (failure: IllegalStateException) {
                throw IllegalStateException("${failure.message}: $diagnostic", failure)
            }
        } finally {
            host.dispose()
            window.close()
        }
    }

    @Test
    public fun stateScrollsToAnItemWithOffsetAndReportsTheViewport() {
        val state = LazyListState()
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            host.view.frame = root.bounds
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 100, key = { it }) { index ->
                        Text("Item $index", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            root.addSubview(host.view)
            awaitAppleUi("AppKit lazy collection was not ready for programmatic scrolling.") {
                val scroll = host.view.arrangedSubviews.singleOrNull() as? NSScrollView
                scroll?.frame = host.view.bounds
                val collection = scroll?.documentView as? NSCollectionView
                collection?.layoutSubtreeIfNeeded()
                collection?.numberOfItemsInSection(0) == 100L
            }

            val scroll = host.view.arrangedSubviews.single() as NSScrollView
            val collection = scroll.documentView as NSCollectionView
            runBlocking { state.scrollToItem(index = 40, scrollOffset = 13f) }
            val path = NSIndexPath.indexPathForItem(40, 0)
            val itemOrigin =
                checkNotNull(collection.collectionViewLayout?.layoutAttributesForItemAtIndexPath(path))
                    .frame
                    .useContents { origin.y }
            val viewportOrigin = scroll.contentView().bounds.useContents { origin.y }
            assertEquals(
                itemOrigin + 13.0,
                viewportOrigin,
                absoluteTolerance = 1.0,
                message =
                    "itemOrigin=$itemOrigin, viewportOrigin=$viewportOrigin, " +
                        "documentHeight=${collection.frame.useContents { size.height }}, " +
                        "viewportHeight=${scroll.contentView().bounds.useContents { size.height }}",
            )
            assertEquals(100, state.layoutInfo.totalItemsCount)
            assertTrue(state.layoutInfo.visibleItems.any { it.index == 40 })
        } finally {
            host.dispose()
            window.close()
        }
    }

    private fun assertDirection(
        expected: platform.AppKit.NSCollectionViewScrollDirection,
        content: dev.dimension.flare.ui.FlareContent,
    ) {
        NSApplication.sharedApplication
        val window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 320.0, 480.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        val root = NSView(frame = window.contentView?.frame ?: CGRectMake(0.0, 0.0, 320.0, 480.0))
        window.contentView = root
        val host = FlareAppKitHost(createAppKitWidgetSystem(AppKitLazyLayoutRendererPlugin))
        try {
            host.view.frame = root.bounds
            host.setContent(content)
            root.addSubview(host.view)
            awaitAppleUi("AppKit lazy collection was not created.") {
                host.view.arrangedSubviews.size == 1
            }

            val scrollView = host.view.arrangedSubviews.single() as NSScrollView
            scrollView.frame = host.view.bounds
            val collection = scrollView.documentView as NSCollectionView
            collection.reloadData()
            collection.layoutSubtreeIfNeeded()

            assertEquals(
                expected,
                (collection.collectionViewLayout as NSCollectionViewFlowLayout).scrollDirection,
            )
            assertEquals(10_000L, collection.numberOfItemsInSection(0))
            assertTrue(collection.visibleItems().size < 10_000)
        } finally {
            host.dispose()
            window.close()
        }
    }
}
