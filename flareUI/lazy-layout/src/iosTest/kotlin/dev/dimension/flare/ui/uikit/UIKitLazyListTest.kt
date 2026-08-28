@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import dev.dimension.flare.ui.lazy.LazyColumn
import dev.dimension.flare.ui.lazy.LazyListState
import dev.dimension.flare.ui.lazy.LazyRow
import dev.dimension.flare.ui.lazy.awaitAppleUi
import dev.dimension.flare.ui.lazy.items
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSThread
import platform.UIKit.UILabel
import platform.UIKit.UIScrollView
import platform.UIKit.UIStackView
import platform.UIKit.UIWindow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class UIKitLazyListTest {
    @Test
    public fun adaptiveRecyclerMeasuresMainAxisWithoutAFixedItemContract() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    item(key = "dynamic") {
                        Text("Dynamic", modifier = FlareModifier.None.height(73f))
                    }
                }
            }

            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit adaptive item was not measured.") {
                host.view.layoutIfNeeded()
                scroll.layoutIfNeeded()
                state.layoutInfo.visibleItems
                    .singleOrNull()
                    ?.size == 73f
            }

            assertEquals(
                73f,
                state.layoutInfo.visibleItems
                    .single()
                    .size,
                absoluteTolerance = 0.5f,
            )
            assertEquals(
                73.0,
                scroll
                    .itemRoots()
                    .single()
                    .frame
                    .useContents { size.height },
                absoluteTolerance = 0.5,
            )
        }
    }

    @Test
    public fun incrementThenScrollUpdatesTheLazyModelWithoutCompositionReentryOrBlankItems() {
        var count by mutableIntStateOf(0)
        var keyLookups = 0
        var increment: () -> Unit = {}
        val state = LazyListState()
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

        withLazyHost { host, _ ->
            val render: (Int) -> Unit = { revision ->
                host.setContent {
                    check(revision >= 0)
                    content()
                }
            }
            render(0)
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit increment fixture was not ready.") {
                host.view.layoutIfNeeded()
                scroll.layoutIfNeeded()
                state.layoutInfo.totalItemsCount == 10_000 && state.layoutInfo.visibleItems.isNotEmpty()
            }

            runBlocking { state.scrollToItem(538) }
            awaitAppleUi("UIKit did not realize the deep anchor before the increment.") {
                scroll.layoutIfNeeded()
                state.layoutInfo.visibleItems.any { it.index == 538 }
            }
            val anchor = state.layoutInfo.visibleItems.first { it.offset + it.size > 0f }
            keyLookups = 0
            increment()
            render(1)
            awaitAppleUi("UIKit did not preserve the deep stable-key anchor after the prepend.") {
                state.layoutInfo.totalItemsCount == 10_001 &&
                    state.layoutInfo.visibleItems.singleOrNull { it.key == anchor.key }?.let {
                        it.index == anchor.index + 1 && abs(it.offset - anchor.offset) < 1f
                    } == true &&
                    host.view.arrangedSubviews
                        .filterIsInstance<UILabel>()
                        .any { it.text == "Count 1" }
            }
            assertTrue(keyLookups < 500, "Deep prepend resolved $keyLookups keys instead of using the local anchor.")
            assertVisibleContentMatchesLayout(scroll, state, itemOffset = 1)

            val offset = scroll.contentOffset.useContents { y }
            scroll.setContentOffset(CGPointMake(0.0, offset + 12.0), animated = false)
            listOf(24, 900, 40, 538).forEach { position ->
                runBlocking { state.scrollToItem(position) }
                awaitAppleUi("UIKit did not realize item $position after the increment and scroll.") {
                    scroll.layoutIfNeeded()
                    state.layoutInfo.visibleItems.any { it.index == position }
                }
                assertVisibleContentMatchesLayout(scroll, state, itemOffset = 1)
            }
        }
    }

    @Test
    public fun firstViewportDoesNotResolveEveryItemKey() {
        var keyLookups = 0
        withLazyHost { host, _ ->
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

            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit lazy viewport did not realize items.") {
                host.view.layoutIfNeeded()
                scroll.layoutIfNeeded()
                scroll.itemRoots().isNotEmpty()
            }

            assertTrue(keyLookups < 500, "First viewport resolved $keyLookups of 10,000 keys.")
            assertTrue(scroll.itemRoots().size < 100, "The adaptive recycler realized too much overscan.")
        }
    }

    @Test
    public fun shrinkingTheModelCancelsAnInFlightNativeAnimation() {
        var count by mutableIntStateOf(1_000)
        val state = LazyListState()
        var result: Result<Unit>? = null
        val content: FlareContent = {
            val countSnapshot = count
            LazyColumn(modifier = FlareModifier.None.fillMaxSize(), state = state) {
                items(count = countSnapshot, key = { it }) { index ->
                    Text("Item $index", modifier = FlareModifier.None.height(36f))
                }
            }
        }

        withLazyHost { host, _ ->
            host.setContent(content)
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit cancellation fixture was not ready.") {
                state.layoutInfo.totalItemsCount == 1_000 && state.layoutInfo.visibleItems.isNotEmpty()
            }

            CoroutineScope(Dispatchers.Unconfined).launch {
                result = runCatching { state.animateScrollToItem(999) }
            }
            count = 1
            host.setContent(content)

            awaitAppleUi("UIKit did not cancel the outdated native animation.") {
                result?.isFailure == true &&
                    state.layoutInfo.totalItemsCount == 1 &&
                    state.layoutInfo.visibleItems.map { it.index } == listOf(0)
            }
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.4, false)
            assertEquals(listOf(0), state.layoutInfo.visibleItems.map { it.index })
            assertEquals(0.0, scroll.contentOffset.useContents { y }, absoluteTolerance = 0.5)
        }
    }

    @Test
    public fun nativeScrollViewSupportsBothLazyDirections() {
        assertTrue(NSThread.isMainThread)
        assertDirection(vertical = true) {
            LazyColumn {
                items(count = 10_000, key = { it }) { index -> Text("Item $index") }
            }
        }
        assertDirection(vertical = false) {
            LazyRow {
                items(count = 10_000, key = { it }) { index -> Text("Item $index") }
            }
        }
    }

    @Test
    public fun variableExtentsAndLayoutVersionUpdatesAreMeasuredIndividually() {
        var expanded by mutableStateOf(false)
        val state = LazyListState()
        val content: FlareContent = {
            LazyColumn(
                modifier = FlareModifier.None.fillMaxSize(),
                state = state,
            ) {
                item(key = "short") { Text("Short", modifier = FlareModifier.None.height(32f)) }
                item(key = "dynamic", layoutVersion = expanded) {
                    Text("Dynamic", modifier = FlareModifier.None.height(if (expanded) 126f else 88f))
                }
            }
        }

        withLazyHost { host, _ ->
            host.setContent(content)
            host.awaitScrollView()
            awaitAppleUi("UIKit variable lazy items were not measured.") {
                state.layoutInfo.visibleItems.map { it.size } == listOf(32f, 88f)
            }

            expanded = true
            host.setContent(content)
            awaitAppleUi("UIKit did not invalidate the changed layout version.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == "dynamic" }
                    ?.size == 126f
            }
            assertEquals(
                126f,
                state.layoutInfo.visibleItems
                    .single { it.key == "dynamic" }
                    .size,
            )
        }
    }

    @Test
    public fun visibleItemRemeasuresWhenItsIntrinsicContentChanges() {
        var expanded by mutableStateOf(false)
        val state = LazyListState()
        val content: FlareContent = {
            val expandedSnapshot = expanded
            LazyColumn(
                modifier = FlareModifier.None.fillMaxSize(),
                state = state,
            ) {
                item(key = "timeline-post") {
                    Column(spacing = 4f) {
                        Text("Timeline title")
                        if (expandedSnapshot) {
                            Text("First dynamic body line")
                            Text("Second dynamic body line")
                            Text("Third dynamic body line")
                        }
                    }
                }
            }
        }
        withLazyHost { host, _ ->
            host.setContent(content)
            host.awaitScrollView()
            awaitAppleUi("UIKit intrinsic timeline item was not measured.") {
                state.layoutInfo.visibleItems
                    .singleOrNull()
                    ?.size
                    ?.let { it > 0f } == true
            }
            val collapsedSize =
                state.layoutInfo.visibleItems
                    .single()
                    .size

            expanded = true
            host.setContent(content)
            awaitAppleUi("UIKit did not remeasure intrinsic content after recomposition.") {
                state.layoutInfo.visibleItems
                    .singleOrNull()
                    ?.size
                    ?.let { it > collapsedSize + 20f } == true
            }
        }
    }

    @Test
    public fun lazyGeometryMatchesTheSharedSpacingAndAlignmentContract() {
        val columnState = LazyListState()
        withLazyHost(width = 200.0, height = 120.0) { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.width(200f).height(120f),
                    state = columnState,
                    spacing = 6f,
                ) {
                    item(key = "first") { Text("First", modifier = FlareModifier.None.height(32f)) }
                    item(key = "second") { Text("Second", modifier = FlareModifier.None.height(48f)) }
                }
            }
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit column geometry did not settle.") {
                val items = columnState.layoutInfo.visibleItems
                items.size == 2 && items[0].size == 32f && items[1].offset == 38f && items[1].size == 48f
            }

            assertTrue(scroll.alwaysBounceVertical)
            assertTrue(!scroll.alwaysBounceHorizontal)
            val firstRoot = scroll.itemRoots().minBy { it.frame.useContents { origin.y } }
            val firstLabel = firstRoot.arrangedSubviews.single() as UILabel
            assertEquals(200.0, firstLabel.frame.useContents { size.width }, absoluteTolerance = 1.0)
        }

        val rowState = LazyListState()
        withLazyHost(width = 200.0, height = 80.0) { host, _ ->
            host.setContent {
                LazyRow(
                    modifier = FlareModifier.None.width(200f).height(80f),
                    state = rowState,
                    spacing = 6f,
                    verticalAlignment = VerticalAlignment.Center,
                ) {
                    item(key = "first") { Text("First", modifier = FlareModifier.None.width(40f).height(24f)) }
                    item(key = "second") { Text("Second", modifier = FlareModifier.None.width(60f).height(24f)) }
                }
            }
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit row geometry did not settle.") {
                val items = rowState.layoutInfo.visibleItems
                items.size == 2 && items[0].size == 40f && items[1].offset == 46f && items[1].size == 60f
            }

            assertTrue(!scroll.alwaysBounceVertical)
            assertTrue(scroll.alwaysBounceHorizontal)
            val firstRoot = scroll.itemRoots().minBy { it.frame.useContents { origin.x } }
            assertEquals(80.0, firstRoot.frame.useContents { size.height }, absoluteTolerance = 0.5)
            val firstLabel = firstRoot.arrangedSubviews.single() as UILabel
            assertEquals(28.0, firstLabel.frame.useContents { origin.y }, absoluteTolerance = 1.0)
        }
    }

    @Test
    public fun prependKeepsTheStableKeyAnchorWithVariableExtents() {
        var items by mutableStateOf((0 until 100).toList())
        val state = LazyListState()
        val content: FlareContent = {
            val reverseContentTypes = items.size > 100
            LazyColumn(
                modifier = FlareModifier.None.fillMaxSize(),
                state = state,
            ) {
                items(
                    items = items,
                    key = { it },
                    contentType = { if ((it % 2 == 0) xor reverseContentTypes) "even" else "odd" },
                ) { item ->
                    Text("Item $item", modifier = FlareModifier.None.height(if (item % 2 == 0) 36f else 64f))
                }
            }
        }

        withLazyHost { host, _ ->
            host.setContent(content)
            host.awaitScrollView()
            awaitAppleUi("UIKit lazy list was not ready for prepend.") {
                state.layoutInfo.totalItemsCount == 100
            }
            runBlocking { state.scrollToItem(index = 20, scrollOffset = 17f) }
            awaitAppleUi("UIKit anchor did not settle before prepend.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == 20 }
                    ?.offset
                    ?.let { abs(it + 17f) < 1f } == true
            }

            items = listOf(-2, -1) + items
            host.setContent(content)
            awaitAppleUi("UIKit did not restore the stable-key anchor after prepend.") {
                state.layoutInfo.totalItemsCount == 102 &&
                    state.layoutInfo.visibleItems
                        .singleOrNull { it.key == 20 }
                        ?.offset
                        ?.let { abs(it + 17f) < 1f } == true
            }
        }
    }

    @Test
    public fun stateScrollsToAnUnmeasuredItemWithOffsetAndReportsTheViewport() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 100, key = { it }, contentType = { it % 3 }) { index ->
                        Text("Item $index", modifier = FlareModifier.None.height((28 + index % 3 * 17).toFloat()))
                    }
                }
            }
            host.awaitScrollView()
            awaitAppleUi("UIKit lazy list was not ready for programmatic scrolling.") {
                state.layoutInfo.totalItemsCount == 100
            }

            runBlocking { state.scrollToItem(index = 40, scrollOffset = 13f) }
            awaitAppleUi("UIKit did not settle the requested dynamic item offset.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.index == 40 }
                    ?.offset
                    ?.let { abs(it + 13f) < 1f } == true
            }
            assertEquals(100, state.layoutInfo.totalItemsCount)
        }
    }

    private fun assertDirection(
        vertical: Boolean,
        content: FlareContent,
    ) {
        withLazyHost { host, _ ->
            host.setContent(content)
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit lazy direction did not settle.") {
                scroll.layoutIfNeeded()
                scroll.itemRoots().isNotEmpty()
            }
            assertEquals(vertical, scroll.alwaysBounceVertical)
            assertEquals(!vertical, scroll.alwaysBounceHorizontal)
            val contentSize = scroll.contentSize.useContents { width to height }
            if (vertical) {
                assertTrue(contentSize.second > scroll.bounds.useContents { size.height })
            } else {
                assertTrue(contentSize.first > scroll.bounds.useContents { size.width })
            }
            assertTrue(scroll.itemRoots().size < 100)
        }
    }

    private fun assertVisibleContentMatchesLayout(
        scroll: UIScrollView,
        state: LazyListState,
        itemOffset: Int,
    ) {
        val labels =
            scroll
                .itemRoots()
                .mapNotNull { it.arrangedSubviews.singleOrNull() as? UILabel }
                .mapNotNull { it.text }
                .toSet()
        state.layoutInfo.visibleItems.forEach { item ->
            assertTrue(
                "Item ${item.index - itemOffset}" in labels,
                "Visible item ${item.index} rendered a blank or stale view. labels=$labels",
            )
        }
    }

    private fun withLazyHost(
        width: Double = 320.0,
        height: Double = 480.0,
        block: (FlareUIKitHost, UIWindow) -> Unit,
    ) {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, width, height))
        val host = FlareUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
        try {
            host.view.setFrame(window.bounds)
            window.addSubview(host.view)
            window.hidden = false
            block(host, window)
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    private fun FlareUIKitHost.awaitScrollView(): UIScrollView {
        var scroll: UIScrollView? = null
        awaitAppleUi("UIKit adaptive lazy scroll view was not created.") {
            view.layoutIfNeeded()
            scroll = view.arrangedSubviews.filterIsInstance<UIScrollView>().singleOrNull()
            scroll?.setFrame(view.bounds)
            scroll?.layoutIfNeeded()
            scroll != null
        }
        return checkNotNull(scroll)
    }

    private fun UIScrollView.itemRoots(): List<UIStackView> = subviews.filterIsInstance<UIStackView>()
}
