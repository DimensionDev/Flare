@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.uikit

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.foundation.Column
import dev.dimension.compose.nativekit.foundation.NativeButton
import dev.dimension.compose.nativekit.foundation.Text
import dev.dimension.compose.nativekit.foundation.VerticalAlignment
import dev.dimension.compose.nativekit.lazy.LazyColumn
import dev.dimension.compose.nativekit.lazy.LazyListState
import dev.dimension.compose.nativekit.lazy.LazyRow
import dev.dimension.compose.nativekit.lazy.awaitAppleUi
import dev.dimension.compose.nativekit.lazy.items
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
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UILabel
import platform.UIKit.UIScrollView
import platform.UIKit.UIStackView
import platform.UIKit.UIWindow
import platform.UIKit.item
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class UIKitLazyListTest {
    @Test
    public fun aCollapsedItemCanExpandAfterItsLayoutVersionChanges() {
        val state = LazyListState()

        fun content(expanded: Boolean): NativeKitContent =
            {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    item(key = "changing", layoutVersion = expanded) {
                        Text("Changing", modifier = NativeKitModifier.None.height(if (expanded) 72f else 0f))
                    }
                    item(key = "following") { Text("Following", modifier = NativeKitModifier.None.height(40f)) }
                }
            }
        withLazyHost { host, _ ->
            host.setContent(content(false))
            host.awaitScrollView()
            awaitAppleUi("The collapsed fixture did not settle.") {
                state.layoutInfo.visibleItems
                    .singleOrNull()
                    ?.key == "following"
            }
            host.setContent(content(true))
            awaitAppleUi("The zero-sized item was not rediscovered after expansion.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == "changing" }
                    ?.size == 72f &&
                    state.layoutInfo.visibleItems
                        .singleOrNull { it.key == "following" }
                        ?.offset == 72f
            }
        }
    }

    @Test
    public fun animationCompletionSurvivesSpacingUpdate() {
        val state = LazyListState()

        fun content(spacing: Float): NativeKitContent =
            {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state, spacing = spacing) {
                    items(count = 200, key = { it }) { Text("Item $it", modifier = NativeKitModifier.None.height(48f)) }
                }
            }
        withLazyHost { host, _ ->
            host.setContent(content(0f))
            val scroll = host.awaitScrollView()
            runBlocking { state.scrollToItem(20, 13f) }
            val initialOffset = scroll.contentOffset.useContents { y }
            val job = CoroutineScope(Dispatchers.Main.immediate).launch { state.animateScrollToItem(80, 13f) }
            try {
                assertTrue(state.isScrollInProgress)
                host.setContent(content(4f))
                awaitAppleUi("The spacing update did not move the anchor.") {
                    scroll.contentOffset.useContents { y } > initialOffset + 40 &&
                        state.layoutInfo.visibleItems.any { it.key == 20 && abs(it.offset + 13f) < 1f }
                }
                scroll.delegate?.scrollViewDidEndScrollingAnimation(scroll)
                assertTrue(job.isCompleted, "The native end-animation callback did not complete the scroll request.")
                assertTrue(!job.isCancelled)
                assertTrue(!state.isScrollInProgress)
                assertEquals(
                    -13f,
                    state.layoutInfo.visibleItems
                        .single { it.index == 80 }
                        .offset,
                    absoluteTolerance = 1f,
                )
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    public fun nativeAnimationCompletionResolvesTheScrollRequest() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    items(count = 200, key = { it }) { Text("Item $it", modifier = NativeKitModifier.None.height(48f)) }
                }
            }
            val scroll = host.awaitScrollView()
            val job = CoroutineScope(Dispatchers.Main.immediate).launch { state.animateScrollToItem(80, 13f) }
            try {
                assertTrue(state.isScrollInProgress)
                scroll.delegate?.scrollViewDidEndScrollingAnimation(scroll)
                assertTrue(job.isCompleted, "The control did not complete its native animation callback.")
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    public fun consecutivePrependsDuringDragKeepOriginalAnchor() {
        val state = LazyListState()

        fun content(prefix: Int): NativeKitContent =
            {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    items(
                        count = 200 + prefix,
                        key = { it - prefix },
                    ) { Text("Item ${it - prefix}", modifier = NativeKitModifier.None.height(48f)) }
                }
            }
        withLazyHost { host, _ ->
            host.setContent(content(0))
            val scroll = host.awaitScrollView()
            runBlocking { state.scrollToItem(20, 13f) }
            val delegate = checkNotNull(scroll.delegate)
            delegate.scrollViewWillBeginDragging(scroll)
            host.setContent(content(1))
            awaitAppleUi("First prepend did not apply during dragging.") {
                state.layoutInfo.totalItemsCount == 201 && state.layoutInfo.visibleItems
                    .firstOrNull()
                    ?.key == 19
            }
            host.setContent(content(2))
            awaitAppleUi("Second prepend did not apply during dragging.") {
                state.layoutInfo.totalItemsCount == 202 && state.layoutInfo.visibleItems
                    .firstOrNull()
                    ?.key == 18
            }
            delegate.scrollViewDidEndDragging(scroll, willDecelerate = false)
            assertEquals(
                20,
                state.layoutInfo.visibleItems
                    .firstOrNull()
                    ?.key,
                "Earlier deferred prepend compensation was lost.",
            )
            assertEquals(
                -13f,
                state.layoutInfo.visibleItems
                    .first()
                    .offset,
                absoluteTolerance = 1f,
            )
        }
    }

    @Test
    public fun zeroHeightItemDoesNotLeaveAnEstimatedGap() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    item(key = "collapsed") { Text("Hidden", modifier = NativeKitModifier.None.height(0f)) }
                    item(key = "visible") { Text("Visible", modifier = NativeKitModifier.None.height(40f)) }
                }
            }
            host.awaitScrollView()
            awaitAppleUi("Zero-height fixture did not mount.") { state.layoutInfo.visibleItems.any { it.key == "visible" } }
            assertEquals(
                0f,
                state.layoutInfo.visibleItems
                    .single { it.key == "visible" }
                    .offset,
                absoluteTolerance = 0.5f,
            )
        }
    }

    @Test
    public fun zeroWidthItemDoesNotLeaveAnEstimatedGap() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyRow(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    item(key = "collapsed") { Text("Hidden", modifier = NativeKitModifier.None.width(0f)) }
                    item(key = "visible") { Text("Visible", modifier = NativeKitModifier.None.width(40f)) }
                }
            }
            host.awaitScrollView()
            awaitAppleUi("Zero-width fixture did not mount.") { state.layoutInfo.visibleItems.any { it.key == "visible" } }
            assertEquals(
                0f,
                state.layoutInfo.visibleItems
                    .single { it.key == "visible" }
                    .offset,
                absoluteTolerance = 0.5f,
            )
        }
    }

    @Test
    public fun adaptiveRecyclerMeasuresMainAxisWithoutAFixedItemContract() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = NativeKitModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    item(key = "dynamic") {
                        Text("Dynamic", modifier = NativeKitModifier.None.height(73f))
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
        val content: NativeKitContent = {
            val itemOffset = count
            increment = { count += 1 }
            NativeButton(label = "Increase", onClick = increment)
            Text("Count $count")
            LazyColumn(
                modifier = NativeKitModifier.None.width(320f).height(240f),
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
                        modifier = NativeKitModifier.None.height(if (value % 5 == 0) 52f else 36f),
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
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize()) {
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
        val content: NativeKitContent = {
            val countSnapshot = count
            LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                items(count = countSnapshot, key = { it }) { index ->
                    Text("Item $index", modifier = NativeKitModifier.None.height(36f))
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
    public fun nativeCollectionViewSupportsBothLazyDirections() {
        assertTrue(NSThread.isMainThread)
        assertDirection(vertical = true) {
            LazyColumn(modifier = NativeKitModifier.None.fillMaxSize()) {
                items(count = 10_000, key = { it }) { index -> Text("Item $index") }
            }
        }
        assertDirection(vertical = false) {
            LazyRow(modifier = NativeKitModifier.None.fillMaxSize()) {
                items(count = 10_000, key = { it }) { index -> Text("Item $index") }
            }
        }
    }

    @Test
    public fun variableExtentsAndLayoutVersionUpdatesAreMeasuredIndividually() {
        var expanded by mutableStateOf(false)
        val state = LazyListState()
        val content: NativeKitContent = {
            LazyColumn(
                modifier = NativeKitModifier.None.fillMaxSize(),
                state = state,
            ) {
                item(key = "short") { Text("Short", modifier = NativeKitModifier.None.height(32f)) }
                item(key = "dynamic", layoutVersion = expanded) {
                    Text("Dynamic", modifier = NativeKitModifier.None.height(if (expanded) 126f else 88f))
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
        val content: NativeKitContent = {
            val expandedSnapshot = expanded
            LazyColumn(
                modifier = NativeKitModifier.None.fillMaxSize(),
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
                    modifier = NativeKitModifier.None.width(200f).height(120f),
                    state = columnState,
                    spacing = 6f,
                ) {
                    item(key = "first") { Text("First", modifier = NativeKitModifier.None.height(32f)) }
                    item(key = "second") { Text("Second", modifier = NativeKitModifier.None.height(48f)) }
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
                    modifier = NativeKitModifier.None.width(200f).height(80f),
                    state = rowState,
                    spacing = 6f,
                    verticalAlignment = VerticalAlignment.Center,
                ) {
                    item(key = "first") { Text("First", modifier = NativeKitModifier.None.width(40f).height(24f)) }
                    item(key = "second") { Text("Second", modifier = NativeKitModifier.None.width(60f).height(24f)) }
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
        val content: NativeKitContent = {
            val reverseContentTypes = items.size > 100
            LazyColumn(
                modifier = NativeKitModifier.None.fillMaxSize(),
                state = state,
            ) {
                items(
                    items = items,
                    key = { it },
                    contentType = { if ((it % 2 == 0) xor reverseContentTypes) "even" else "odd" },
                ) { item ->
                    Text("Item $item", modifier = NativeKitModifier.None.height(if (item % 2 == 0) 36f else 64f))
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
    public fun crossAxisResizeKeepsTheDeepStableKeyAnchor() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = NativeKitModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 200, key = { it }) { index ->
                        Text("Item $index", modifier = NativeKitModifier.None.height(if (index % 2 == 0) 36f else 64f))
                    }
                }
            }

            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit resize fixture was not ready.") {
                state.layoutInfo.totalItemsCount == 200 && state.layoutInfo.visibleItems.isNotEmpty()
            }
            runBlocking { state.scrollToItem(index = 80, scrollOffset = 17f) }
            awaitAppleUi("UIKit resize anchor did not settle.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == 80 }
                    ?.offset
                    ?.let { abs(it + 17f) < 1f } == true
            }

            scroll.setFrame(CGRectMake(0.0, 0.0, 220.0, 480.0))
            scroll.setNeedsLayout()
            awaitAppleUi("UIKit cross-axis resize changed the deep stable-key anchor.") {
                scroll.layoutIfNeeded()
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == 80 }
                    ?.offset
                    ?.let { abs(it + 17f) < 1f } == true
            }
        }
    }

    @Test
    public fun modelUpdateDuringDragPreservesSubsequentPhysicalScrollDelta() {
        var items by mutableStateOf((0 until 100).toList())
        val state = LazyListState()
        val content: NativeKitContent = {
            LazyColumn(
                modifier = NativeKitModifier.None.fillMaxSize(),
                state = state,
            ) {
                items(items = items, key = { it }) { item ->
                    Text("Item $item", modifier = NativeKitModifier.None.height(if (item % 2 == 0) 36f else 64f))
                }
            }
        }

        withLazyHost { host, _ ->
            host.setContent(content)
            val scroll = host.awaitScrollView()
            awaitAppleUi("UIKit drag-update fixture was not ready.") {
                state.layoutInfo.totalItemsCount == 100
            }
            runBlocking { state.scrollToItem(index = 20, scrollOffset = 17f) }
            awaitAppleUi("UIKit drag-update anchor did not settle.") {
                state.layoutInfo.visibleItems
                    .singleOrNull { it.key == 20 }
                    ?.offset
                    ?.let { abs(it + 17f) < 1f } == true
            }

            val delegate = checkNotNull(scroll.delegate)
            delegate.scrollViewWillBeginDragging(scroll)
            val offsetAtUpdate = scroll.contentOffset.useContents { y }
            items = listOf(-2, -1) + items
            host.setContent(content)
            scroll.setContentOffset(CGPointMake(0.0, offsetAtUpdate + 12.0), animated = false)
            delegate.scrollViewDidEndDragging(scroll, willDecelerate = false)

            awaitAppleUi("UIKit model update discarded the drag delta.") {
                state.layoutInfo.totalItemsCount == 102 &&
                    state.layoutInfo.visibleItems
                        .singleOrNull { it.key == 20 }
                        ?.let { it.index == 22 && abs(it.offset + 29f) < 1f } == true
            }
        }
    }

    @Test
    public fun prependPreservesTheVisibleItemCompositionAndNativeContent() {
        val state = LazyListState()
        val created = mutableMapOf<Int, Int>()
        val disposed = mutableMapOf<Int, Int>()

        fun content(prefix: Int): NativeKitContent =
            {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    items(count = 10_000 + prefix, key = { it - prefix }) { index ->
                        val key = index - prefix
                        DisposableEffect(key) {
                            created[key] = (created[key] ?: 0) + 1
                            onDispose { disposed[key] = (disposed[key] ?: 0) + 1 }
                        }
                        Text("Item $key", modifier = NativeKitModifier.None.height(60f))
                    }
                }
            }
        withLazyHost { host, _ ->
            host.setContent(content(0))
            val scroll = host.awaitScrollView()
            awaitAppleUi("The insertion fixture did not mount.") { state.layoutInfo.visibleItems.isNotEmpty() }
            runBlocking { state.scrollToItem(500, 13f) }
            awaitAppleUi("The insertion anchor did not settle.") {
                state.layoutInfo.visibleItems
                    .firstOrNull()
                    ?.let { it.key == 500 && abs(it.offset + 13f) < 1f } == true
            }
            val beforeCreated = created[500]
            val beforeDisposed = disposed[500]
            val originalRoot = scroll.itemRoots().first()
            val originalText = originalRoot.arrangedSubviews.single()
            host.setContent(content(20))
            awaitAppleUi("The insertion lost the anchor.") {
                state.layoutInfo.totalItemsCount == 10_020 &&
                    state.layoutInfo.visibleItems
                        .firstOrNull()
                        ?.let { it.key == 500 && it.index == 520 && abs(it.offset + 13f) < 1f } ==
                    true
            }
            assertEquals(beforeCreated, created[500], "Prepending rebuilt a retained item.")
            assertEquals(beforeDisposed, disposed[500], "Prepending disposed a retained item.")
            assertTrue(scroll.itemRoots().first() === originalRoot)
            assertEquals(originalText, originalRoot.arrangedSubviews.single())
            host.setContent(content(0))
            awaitAppleUi("Removing the prefix lost the anchor.") {
                state.layoutInfo.totalItemsCount == 10_000 &&
                    state.layoutInfo.visibleItems
                        .firstOrNull()
                        ?.let { it.key == 500 && it.index == 500 && abs(it.offset + 13f) < 1f } ==
                    true
            }
            assertEquals(beforeCreated, created[500])
            assertEquals(beforeDisposed, disposed[500])
            assertTrue(scroll.itemRoots().first() === originalRoot)
        }
    }

    @Test
    public fun contentModelUpdateKeepsTheRealizedNativeRoot() {
        var label by mutableStateOf("Before")
        var created = 0
        var disposed = 0
        val content: NativeKitContent = {
            val labelSnapshot = label
            LazyColumn(modifier = NativeKitModifier.None.fillMaxSize()) {
                item(key = "stable", contentType = labelSnapshot, layoutVersion = Unit) {
                    DisposableEffect(Unit) {
                        created += 1
                        onDispose { disposed += 1 }
                    }
                    Text(labelSnapshot, modifier = NativeKitModifier.None.height(40f))
                }
            }
        }

        withLazyHost { host, _ ->
            host.setContent(content)
            val scroll = host.awaitScrollView()
            lateinit var originalRoot: UIStackView
            awaitAppleUi("UIKit content-update fixture was not ready.") {
                originalRoot = scroll.itemRoots().singleOrNull() ?: return@awaitAppleUi false
                (originalRoot.arrangedSubviews.singleOrNull() as? UILabel)?.text == "Before"
            }

            val originalText = originalRoot.arrangedSubviews.single()
            label = "After"
            host.setContent(content)

            lateinit var updatedRoot: UIStackView
            awaitAppleUi("UIKit content-only update did not reach the realized item.") {
                updatedRoot = scroll.itemRoots().singleOrNull() ?: return@awaitAppleUi false
                (updatedRoot.arrangedSubviews.singleOrNull() as? UILabel)?.text == "After"
            }
            assertTrue(updatedRoot === originalRoot, "UIKit recycled the native root for an in-place model update.")
            assertEquals(originalText, updatedRoot.arrangedSubviews.single(), "The stable item rebuilt its native content.")
            assertEquals(1, created, "A content update recreated the item composition.")
            assertEquals(0, disposed)
        }
    }

    @Test
    public fun stateScrollsToAnUnmeasuredItemWithOffsetAndReportsTheViewport() {
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(
                    modifier = NativeKitModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 100, key = { it }, contentType = { it % 3 }) { index ->
                        Text("Item $index", modifier = NativeKitModifier.None.height((28 + index % 3 * 17).toFloat()))
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

    @Test
    public fun nativeReuseDisposesOffscreenContentAndRestoresSaveableState() {
        val active = mutableSetOf<Int>()
        val tokens = mutableMapOf<Int, Int>()
        var nextToken = 0
        val state = LazyListState()
        withLazyHost { host, _ ->
            host.setContent {
                LazyColumn(modifier = NativeKitModifier.None.fillMaxSize(), state = state) {
                    items(count = 2_000, key = { it }, contentType = { it % 3 }) { index ->
                        val token = rememberSaveable { nextToken++ }
                        SideEffect { tokens[index] = token }
                        DisposableEffect(index) {
                            check(active.add(index))
                            onDispose { active.remove(index) }
                        }
                        Text("Item $index", modifier = NativeKitModifier.None.height(36f))
                    }
                }
            }
            host.awaitScrollView()
            awaitAppleUi("The first native item was not composed.") { 0 in active && 0 in tokens }
            val originalToken = tokens.getValue(0)

            runBlocking { state.scrollToItem(1_500) }
            awaitAppleUi("The native collection retained an offscreen composition.") {
                1_500 in active && 0 !in active
            }
            assertTrue(active.size < 100, "Native realization retained ${active.size} compositions.")

            runBlocking { state.scrollToItem(0) }
            awaitAppleUi("The native collection did not restore the first item.") { 0 in active }
            assertEquals(originalToken, tokens.getValue(0))
        }
        assertTrue(active.isEmpty(), "Disposing the host leaked item compositions.")
    }

    private fun assertDirection(
        vertical: Boolean,
        content: NativeKitContent,
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
        block: (NativeKitUIKitHost, UIWindow) -> Unit,
    ) {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, width, height))
        val host = NativeKitUIKitHost(createUIKitWidgetSystem(UIKitLazyLayoutRendererPlugin))
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

    private fun NativeKitUIKitHost.awaitScrollView(): UIScrollView {
        var scroll: UIScrollView? = null
        awaitAppleUi("UIKit adaptive lazy scroll view was not created.") {
            view.layoutIfNeeded()
            scroll = view.arrangedSubviews.filterIsInstance<UIScrollView>().singleOrNull()
            scroll?.setFrame(view.bounds)
            scroll?.layoutIfNeeded()
            scroll != null
        }
        return checkNotNull(scroll).also { assertTrue(it is UICollectionView) }
    }

    private fun UIScrollView.itemRoots(): List<UIStackView> {
        val collection = this as UICollectionView
        return collection.visibleCells
            .filterIsInstance<UICollectionViewCell>()
            .sortedBy { collection.indexPathForCell(it)?.item }
            .flatMap { it.contentView.subviews.filterIsInstance<UIStackView>() }
    }
}
