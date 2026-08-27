@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import dev.dimension.flare.ui.lazy.LazyColumn
import dev.dimension.flare.ui.lazy.LazyListState
import dev.dimension.flare.ui.lazy.LazyRow
import dev.dimension.flare.ui.lazy.awaitAppleUi
import dev.dimension.flare.ui.lazy.items
import kotlinx.cinterop.useContents
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSIndexPath
import platform.Foundation.NSThread
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCompositionalLayout
import platform.UIKit.UICollectionViewScrollDirection.UICollectionViewScrollDirectionHorizontal
import platform.UIKit.UICollectionViewScrollDirection.UICollectionViewScrollDirectionVertical
import platform.UIKit.UICollectionViewScrollPositionTop
import platform.UIKit.UILabel
import platform.UIKit.UIWindow
import platform.UIKit.indexPathForItem
import platform.UIKit.item
import platform.UIKit.layoutAttributesForItemAtIndexPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class UIKitLazyListTest {
    @Test
    public fun selfSizingCellMakesItsMainAxisEndConstraintNonRequired() {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    item(key = "fixed") {
                        Text("Fixed", modifier = FlareModifier.None.height(36f))
                    }
                }
            }
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit self-sizing cell was not realized.") {
                val collection = host.view.arrangedSubviews.singleOrNull() as? UICollectionView
                collection?.setFrame(host.view.bounds)
                collection?.layoutIfNeeded()
                collection?.visibleCells?.singleOrNull() != null
            }

            val collection = host.view.arrangedSubviews.single() as UICollectionView
            val cell = collection.visibleCells.single() as platform.UIKit.UICollectionViewCell
            val root = cell.contentView.subviews.single()
            val rootEdgeConstraints =
                cell.contentView.constraints
                    .filterIsInstance<NSLayoutConstraint>()
                    .filter { constraint ->
                        constraint.firstItem == root || constraint.secondItem == root
                    }

            assertEquals(4, rootEdgeConstraints.size)
            assertEquals(
                1,
                rootEdgeConstraints.count { constraint -> constraint.priority < 1_000f },
                "Only the main-axis end constraint should yield to UICollectionView's temporary estimated extent.",
            )
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    @Test
    public fun incrementThenScrollUpdatesTheLazyModelWithoutCompositionReentry() {
        var count by mutableIntStateOf(0)
        var keyLookups = 0
        var increment: () -> Unit = {}
        val state = LazyListState()
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        val content: FlareContent = {
            val itemOffset = count
            increment = { count += 1 }
            NativeButton(label = "Increase", onClick = increment)
            Text("Count $count")
            LazyColumn(
                modifier = FlareModifier.None.width(320f).height(240f),
                state = state,
            ) {
                items(
                    count = 10_000 + itemOffset,
                    key = { index ->
                        keyLookups += 1
                        index - itemOffset
                    },
                    contentType = { index ->
                        if ((index - itemOffset) % 5 == 0) "highlight" else "standard"
                    },
                ) { index ->
                    val value = index - itemOffset
                    Text(
                        "Item $value",
                        modifier = FlareModifier.None.height(if (value % 5 == 0) 52f else 36f),
                    )
                }
            }
        }
        try {
            host.view.setFrame(window.bounds)
            host.setContent(content)
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit increment fixture was not ready.") {
                host.view.layoutIfNeeded()
                val collection =
                    host.view.arrangedSubviews
                        .filterIsInstance<UICollectionView>()
                        .singleOrNull()
                collection?.layoutIfNeeded()
                collection?.numberOfItemsInSection(0) == 10_000L && collection.visibleCells.isNotEmpty()
            }

            val collection =
                host.view.arrangedSubviews
                    .filterIsInstance<UICollectionView>()
                    .single()
            keyLookups = 0
            increment()
            // The native test runner has no UIApplication frame pump. Resubmitting the same
            // content applies the already-mutated snapshot synchronously, like the app's next frame.
            host.setContent(content)

            awaitAppleUi("Increment did not update the count label.") {
                host.view.arrangedSubviews
                    .filterIsInstance<UILabel>()
                    .any { it.text == "Count 1" }
            }
            awaitAppleUi("Increment did not update the lazy collection item count.") {
                collection.numberOfItemsInSection(0) == 10_001L
            }
            assertTrue(keyLookups < 500, "Large-list update resolved $keyLookups keys instead of staying viewport-bound.")

            val offsetAfterUpdate = collection.contentOffset.useContents { y }
            collection.setContentOffset(CGPointMake(0.0, offsetAfterUpdate + 12.0), animated = false)
            collection.layoutIfNeeded()

            val scrollPositions = listOf(538, 24, 900, 40, 538)
            scrollPositions.forEach { position ->
                runBlocking { state.scrollToItem(position) }
                awaitAppleUi("UIKit did not realize item $position after the increment and scroll.") {
                    collection.layoutIfNeeded()
                    collection.indexPathsForVisibleItems.any { path ->
                        (path as NSIndexPath).item == position.toLong()
                    }
                }
                assertVisibleCellContent(collection, itemOffset = 1)
            }

            val targetPath = NSIndexPath.indexPathForItem(538, 0)
            val targetHeight =
                checkNotNull(collection.collectionViewLayout.layoutAttributesForItemAtIndexPath(targetPath))
                    .frame
                    .useContents { size.height }
            assertEquals(36.0, targetHeight, absoluteTolerance = 0.5)
        } finally {
            host.dispose()
            window.hidden = true
            var drainedRunLoopTurns = 0
            awaitAppleUi("UIKit lazy batch completion did not drain after disposal.") {
                drainedRunLoopTurns += 1
                drainedRunLoopTurns >= 3
            }
        }
    }

    @Test
    public fun firstViewportDoesNotResolveEveryItemKey() {
        var keyLookups = 0
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
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
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit lazy viewport did not realize cells.") {
                val collection = host.view.arrangedSubviews.singleOrNull() as? UICollectionView
                collection?.setFrame(host.view.bounds)
                collection?.layoutIfNeeded()
                collection?.visibleCells?.isNotEmpty() == true
            }

            assertTrue(keyLookups < 500, "First viewport resolved $keyLookups of 10,000 keys.")
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    @Test
    public fun collectionViewSupportsBothLazyDirections() {
        assertTrue(NSThread.isMainThread)
        assertDirection(
            expected = UICollectionViewScrollDirectionVertical,
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
            expected = UICollectionViewScrollDirectionHorizontal,
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
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    item(key = "short") { Text("Short", modifier = FlareModifier.None.height(32f)) }
                    item(key = "tall") { Text("Tall", modifier = FlareModifier.None.height(88f)) }
                }
            }
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit lazy items were not measured.") {
                val collection = host.view.arrangedSubviews.singleOrNull() as? UICollectionView
                collection?.setFrame(host.view.bounds)
                collection?.layoutIfNeeded()
                val layout = collection?.collectionViewLayout as? UICollectionViewCompositionalLayout
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
                collection?.visibleCells?.size == 2 && first == 32.0 && second == 88.0
            }

            val collection = host.view.arrangedSubviews.single() as UICollectionView
            val layout = collection.collectionViewLayout as UICollectionViewCompositionalLayout
            val short = layout.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
            val tall = layout.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
            assertEquals(32.0, checkNotNull(short).frame.useContents { size.height }, absoluteTolerance = 1.0)
            assertEquals(88.0, checkNotNull(tall).frame.useContents { size.height }, absoluteTolerance = 1.0)
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    @Test
    public fun lazyGeometryMatchesTheSharedSpacingAndAlignmentContract() {
        val state = LazyListState()
        val columnWindow = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val columnHost = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            columnHost.view.setFrame(CGRectMake(0.0, 0.0, 200.0, 120.0))
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
            columnWindow.addSubview(columnHost.view)
            columnWindow.hidden = false
            awaitAppleUi("UIKit column geometry did not settle.") {
                val collection = columnHost.view.arrangedSubviews.singleOrNull() as? UICollectionView
                columnHost.view.layoutIfNeeded()
                collection?.layoutIfNeeded()
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

            val collection = columnHost.view.arrangedSubviews.single() as UICollectionView
            assertTrue(collection.alwaysBounceVertical)
            assertTrue(!collection.alwaysBounceHorizontal)
            runBlocking { state.scrollToItem(0) }
            val first = state.layoutInfo.visibleItems.single { it.key == "first" }
            val second = state.layoutInfo.visibleItems.single { it.key == "second" }
            assertEquals(0f, first.offset, absoluteTolerance = 0.5f)
            assertEquals(32f, first.size, absoluteTolerance = 0.5f)
            assertEquals(38f, second.offset, absoluteTolerance = 0.5f)
            assertEquals(48f, second.size, absoluteTolerance = 0.5f)
            val firstCell = checkNotNull(collection.cellForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0)))
            firstCell.layoutIfNeeded()
            val firstRoot = firstCell.contentView.subviews.single() as platform.UIKit.UIStackView
            val firstLabel = firstRoot.arrangedSubviews.single() as UILabel
            assertEquals(
                200.0,
                firstLabel.frame.useContents { size.width },
                absoluteTolerance = 1.0,
                message = "The default Stretch alignment must fill the lazy column cross axis.",
            )
        } finally {
            columnHost.dispose()
            columnWindow.hidden = true
        }

        val rowWindow = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val rowHost = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            rowHost.view.setFrame(CGRectMake(0.0, 0.0, 200.0, 80.0))
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
            rowWindow.addSubview(rowHost.view)
            rowWindow.hidden = false
            awaitAppleUi("UIKit row geometry did not settle.") {
                val collection = rowHost.view.arrangedSubviews.singleOrNull() as? UICollectionView
                rowHost.view.layoutIfNeeded()
                collection?.layoutIfNeeded()
                val first =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0))
                val second =
                    collection
                        ?.collectionViewLayout
                        ?.layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0))
                collection?.visibleCells?.size == 2 &&
                    first?.frame?.useContents { size.width == 40.0 && size.height == 80.0 && origin.x == 0.0 } == true &&
                    second?.frame?.useContents { size.width == 60.0 && origin.x == 46.0 } == true
            }

            val collection = rowHost.view.arrangedSubviews.single() as UICollectionView
            val firstAttributes =
                checkNotNull(
                    collection.collectionViewLayout
                        .layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0)),
                )
            val secondAttributes =
                checkNotNull(
                    collection.collectionViewLayout
                        .layoutAttributesForItemAtIndexPath(NSIndexPath.indexPathForItem(1, 0)),
                )
            val firstFrame = firstAttributes.frame.useContents { listOf(origin.x, origin.y, size.width, size.height) }
            val secondFrame = secondAttributes.frame.useContents { listOf(origin.x, origin.y, size.width, size.height) }
            assertEquals(0.0, firstFrame[0], absoluteTolerance = 0.5, message = "first=$firstFrame, second=$secondFrame")
            assertEquals(40.0, firstFrame[2], absoluteTolerance = 0.5, message = "first=$firstFrame, second=$secondFrame")
            assertEquals(80.0, firstFrame[3], absoluteTolerance = 0.5, message = "first=$firstFrame, second=$secondFrame")
            assertEquals(46.0, secondFrame[0], absoluteTolerance = 0.5, message = "first=$firstFrame, second=$secondFrame")
            assertEquals(60.0, secondFrame[2], absoluteTolerance = 0.5, message = "first=$firstFrame, second=$secondFrame")
            assertTrue(!collection.alwaysBounceVertical)
            assertTrue(collection.alwaysBounceHorizontal)
            val firstCell =
                checkNotNull(collection.cellForItemAtIndexPath(NSIndexPath.indexPathForItem(0, 0)))
            firstCell.layoutIfNeeded()
            val firstRoot = firstCell.contentView.subviews.single() as platform.UIKit.UIStackView
            val firstLabel = firstRoot.arrangedSubviews.single() as UILabel
            assertEquals(28.0, firstLabel.frame.useContents { origin.y }, absoluteTolerance = 1.0)
        } finally {
            rowHost.dispose()
            rowWindow.hidden = true
        }
    }

    @Test
    public fun prependUsesBatchUpdatesAndKeepsTheStableKeyAnchor() {
        var items by mutableStateOf((0 until 100).toList())
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        val content: FlareContent = {
            LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                items(items = items, key = { it }) { item ->
                    Text("Item $item", modifier = FlareModifier.None.height(40f))
                }
            }
        }
        try {
            host.view.setFrame(window.bounds)
            host.setContent(content)
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit lazy collection was not ready for scrolling.") {
                (host.view.arrangedSubviews.singleOrNull() as? UICollectionView)?.numberOfItemsInSection(0) == 100L
            }
            val collection = host.view.arrangedSubviews.single() as UICollectionView
            collection.setFrame(host.view.bounds)
            val originalPath = NSIndexPath.indexPathForItem(20, 0)
            collection.scrollToItemAtIndexPath(originalPath, UICollectionViewScrollPositionTop, false)
            collection.layoutIfNeeded()
            val originalOrigin =
                checkNotNull(collection.collectionViewLayout.layoutAttributesForItemAtIndexPath(originalPath))
                    .frame
                    .useContents { origin.y }
            collection.setContentOffset(
                platform.CoreGraphics.CGPointMake(0.0, originalOrigin + 17.0),
                animated = false,
            )

            items = listOf(-2, -1) + items
            host.setContent(content)
            var diagnostic = "the update did not finish"
            try {
                awaitAppleUi("UIKit did not restore the stable-key anchor after prepend.") {
                    collection.layoutIfNeeded()
                    val itemCount = collection.numberOfItemsInSection(0)
                    diagnostic = "item count=$itemCount"
                    if (itemCount != 102L) return@awaitAppleUi false
                    val restoredPath = NSIndexPath.indexPathForItem(22, 0)
                    val restoredAttributes =
                        collection.collectionViewLayout.layoutAttributesForItemAtIndexPath(restoredPath)
                    diagnostic = "item count=$itemCount, attributes=$restoredAttributes"
                    val restoredOrigin = restoredAttributes?.frame?.useContents { origin.y } ?: return@awaitAppleUi false
                    val offset = collection.contentOffset.useContents { y }
                    diagnostic = "item origin=$restoredOrigin, content offset=$offset"
                    kotlin.math.abs((restoredOrigin - offset) + 17.0) < 1.0
                }
            } catch (failure: IllegalStateException) {
                throw IllegalStateException("${failure.message}: $diagnostic", failure)
            }
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    @Test
    public fun stateScrollsToAnItemWithOffsetAndReportsTheViewport() {
        val state = LazyListState()
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
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
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit lazy collection was not ready for programmatic scrolling.") {
                val collection = host.view.arrangedSubviews.singleOrNull() as? UICollectionView
                collection?.setFrame(host.view.bounds)
                collection?.layoutIfNeeded()
                collection?.numberOfItemsInSection(0) == 100L
            }

            val collection = host.view.arrangedSubviews.single() as UICollectionView
            runBlocking { state.scrollToItem(index = 40, scrollOffset = 13f) }
            val path = NSIndexPath.indexPathForItem(40, 0)
            val origin =
                checkNotNull(collection.collectionViewLayout.layoutAttributesForItemAtIndexPath(path))
                    .frame
                    .useContents { origin.y }
            assertEquals(origin + 13.0, collection.contentOffset.useContents { y }, absoluteTolerance = 1.0)
            assertEquals(100, state.layoutInfo.totalItemsCount)
            assertTrue(state.layoutInfo.visibleItems.any { it.index == 40 })
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    private fun assertDirection(
        expected: platform.UIKit.UICollectionViewScrollDirection,
        content: dev.dimension.flare.ui.FlareContent,
    ) {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
            host.setContent(content)
            window.addSubview(host.view)
            window.hidden = false
            awaitAppleUi("UIKit lazy collection was not created.") {
                host.view.arrangedSubviews.size == 1
            }

            val collection = host.view.arrangedSubviews.single() as UICollectionView
            collection.setFrame(host.view.bounds)
            collection.reloadData()
            collection.layoutIfNeeded()

            assertEquals(
                expected,
                (collection.collectionViewLayout as UICollectionViewCompositionalLayout).configuration.scrollDirection,
            )
            assertEquals(10_000L, collection.numberOfItemsInSection(0))
            assertTrue(collection.visibleCells.size < 10_000)
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    private fun assertVisibleCellContent(
        collection: UICollectionView,
        itemOffset: Int,
    ) {
        collection.indexPathsForVisibleItems.forEach { rawPath ->
            val path = rawPath as NSIndexPath
            val cell = checkNotNull(collection.cellForItemAtIndexPath(path))
            val root = cell.contentView.subviews.singleOrNull() as? platform.UIKit.UIStackView
            val label = root?.arrangedSubviews?.singleOrNull() as? UILabel
            assertEquals(
                "Item ${path.item.toInt() - itemOffset}",
                label?.text,
                "Visible item ${path.item} rendered a blank or stale cell.",
            )
        }
    }
}
