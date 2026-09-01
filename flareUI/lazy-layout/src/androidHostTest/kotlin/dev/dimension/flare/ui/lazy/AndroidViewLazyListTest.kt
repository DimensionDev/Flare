package dev.dimension.flare.ui.lazy

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.android.AndroidViewLazyLayoutRendererPlugin
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
public class AndroidViewLazyListTest {
    @Test
    public fun lazyColumnRealizesOnlyViewportItems() {
        withHost { host ->
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000,
                        key = { index -> index },
                    ) { index ->
                        Text("Item $index")
                    }
                }
            }

            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            assertEquals(RecyclerView.VERTICAL, (recycler.layoutManager as LinearLayoutManager).orientation)
            assertTrue(recycler.isVerticalScrollBarEnabled)
            assertFalse(recycler.isHorizontalScrollBarEnabled)
            assertNotNull(recycler.verticalScrollbarThumbDrawable)
            assertTrue(recycler.isScrollbarFadingEnabled)
            assertTrue(recycler.childCount in 1 until 10_000)
            assertEquals("Item 0", recycler.firstRenderedText())
        }
    }

    @Test
    public fun largeModelUpdateAvoidsRedundantFullKeyScans() {
        var generation by mutableStateOf(0)
        var keyLookups = 0
        withHost { host ->
            host.setContent {
                val generationSnapshot = generation
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000,
                        key = { index ->
                            keyLookups += 1
                            index
                        },
                        contentType = { generationSnapshot },
                    ) { index ->
                        Text("Item $index")
                    }
                }
            }
            layout(host)

            keyLookups = 0
            generation = 1
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)

            assertTrue("Model update resolved $keyLookups keys.", keyLookups < 25_000)
        }
    }

    @Test
    public fun stableItemIdSurvivesMoreThanFourThousandOtherKeys() {
        withHost { host ->
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(count = 5_000, key = { index -> "item-$index" }) { index ->
                        Text("Item $index")
                    }
                }
            }
            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val adapter = checkNotNull(recycler.adapter)
            val originalId = adapter.getItemId(0)
            repeat(4_999) { offset -> adapter.getItemId(offset + 1) }

            assertTrue(adapter.hasStableIds())
            assertEquals(originalId, adapter.getItemId(0))
        }
    }

    @Test
    public fun deepAnchorModelUpdateUsesNearbyKeyLookup() {
        var generation by mutableStateOf(0)
        var keyLookups = 0
        withHost { host ->
            host.setContent {
                val generationSnapshot = generation
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000,
                        key = { index ->
                            keyLookups += 1
                            index
                        },
                        contentType = { generationSnapshot },
                    ) { index ->
                        Text("Item $index", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val layoutManager = recycler.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(9_000, -17)
            layout(host)

            keyLookups = 0
            generation = 1
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)

            assertTrue("Deep anchor update resolved $keyLookups keys.", keyLookups < 500)
            assertEquals(9_000, layoutManager.findFirstVisibleItemPosition())
            assertEquals(-17, layoutManager.getDecoratedTop(checkNotNull(layoutManager.findViewByPosition(9_000))))
        }
    }

    @Test
    public fun largePrependUsesTheCountDeltaAnchorFastPath() {
        var prependedItems by mutableStateOf(0)
        var keyLookups = 0
        withHost { host ->
            host.setContent {
                val prependedItemsSnapshot = prependedItems
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000 + prependedItemsSnapshot,
                        key = { index ->
                            keyLookups += 1
                            index - prependedItemsSnapshot
                        },
                    ) { index ->
                        Text("Item ${index - prependedItemsSnapshot}", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val layoutManager = recycler.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(9_000, -17)
            layout(host)

            keyLookups = 0
            prependedItems = 100
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)

            assertTrue("Large prepend resolved $keyLookups keys.", keyLookups < 500)
            assertEquals(9_100, layoutManager.findFirstVisibleItemPosition())
            assertEquals(-17, layoutManager.getDecoratedTop(checkNotNull(layoutManager.findViewByPosition(9_100))))
        }
    }

    @Test
    public fun visibleStableHolderRebindsContentWithoutRecreatingTheDataset() {
        var label by mutableStateOf("Before")
        withHost { host ->
            host.setContent {
                val labelSnapshot = label
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    item(key = "stable") { Text(labelSnapshot) }
                }
            }
            layout(host)
            val recycler = host.getChildAt(0) as RecyclerView
            assertEquals("Before", recycler.firstRenderedText())

            label = "After"
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)

            assertEquals("After", recycler.firstRenderedText())
        }
    }

    @Test
    public fun lazyRowUsesHorizontalNativeLayout() {
        withHost { host ->
            host.setContent {
                LazyRow(modifier = FlareModifier.None.fillMaxSize()) {
                    items(
                        count = 10_000,
                        key = { index -> index },
                    ) { index ->
                        Text("Item $index")
                    }
                }
            }

            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            assertEquals(RecyclerView.HORIZONTAL, (recycler.layoutManager as LinearLayoutManager).orientation)
            assertFalse(recycler.isVerticalScrollBarEnabled)
            assertTrue(recycler.isHorizontalScrollBarEnabled)
            assertNotNull(recycler.horizontalScrollbarThumbDrawable)
            assertTrue(recycler.isScrollbarFadingEnabled)
            assertTrue(recycler.childCount in 1 until 10_000)
            assertEquals("Item 0", recycler.firstRenderedText())
        }
    }

    @Test
    public fun prependKeepsTheFirstVisibleStableKeyAndOffset() {
        var items by mutableStateOf((0 until 100).toList())
        withHost { host ->
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    items(items = items, key = { it }) { item ->
                        Text("Item $item", modifier = FlareModifier.None.height(48f))
                    }
                }
            }
            layout(host)
            val recycler = host.getChildAt(0) as RecyclerView
            val layoutManager = recycler.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(20, -17)
            layout(host)
            val anchorPosition = layoutManager.findFirstVisibleItemPosition()
            val anchorView = checkNotNull(layoutManager.findViewByPosition(anchorPosition))
            val anchorOffset = layoutManager.getDecoratedTop(anchorView)
            val anchorText = (anchorView as android.view.ViewGroup).firstText()

            items = listOf(-2, -1) + items
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)

            val restoredPosition = layoutManager.findFirstVisibleItemPosition()
            val restoredView = checkNotNull(layoutManager.findViewByPosition(restoredPosition))
            assertEquals(anchorText, (restoredView as android.view.ViewGroup).firstText())
            assertEquals(anchorOffset, layoutManager.getDecoratedTop(restoredView))
        }
    }

    @Test
    public fun itemContentPlacesMultipleRootsAlongTheMainAxis() {
        withHost { host ->
            host.setContent {
                LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                    item(key = "multiple") {
                        Text("First", modifier = FlareModifier.None.height(24f))
                        Text("Second", modifier = FlareModifier.None.height(36f))
                    }
                }
            }

            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val itemRoot = recycler.getChildAt(0) as LinearLayout
            assertEquals(LinearLayout.VERTICAL, itemRoot.orientation)
            assertEquals(2, itemRoot.childCount)
            assertEquals("First", (itemRoot.getChildAt(0) as TextView).text.toString())
            assertEquals("Second", (itemRoot.getChildAt(1) as TextView).text.toString())
            assertTrue(itemRoot.getChildAt(1).top >= itemRoot.getChildAt(0).bottom)
        }
    }

    @Test
    public fun lazyGeometryUsesContentSizeSpacingAndCenteredCrossAxis() {
        withHost { host ->
            host.setContent {
                LazyRow(
                    modifier = FlareModifier.None.fillMaxSize(),
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

            layout(host)

            val density = host.resources.displayMetrics.density
            val recycler = host.getChildAt(0) as RecyclerView
            val firstRoot = recycler.getChildAt(0) as LinearLayout
            val secondRoot = recycler.getChildAt(1) as LinearLayout
            val first = firstRoot.getChildAt(0)
            val second = secondRoot.getChildAt(0)
            assertEquals((40f * density).roundToInt(), firstRoot.width)
            assertEquals((60f * density).roundToInt(), secondRoot.width)
            assertEquals((6f * density).roundToInt(), secondRoot.left - firstRoot.right)
            assertEquals((24f * density).roundToInt(), first.height)
            assertEquals((recycler.height - first.height) / 2, first.top)
            assertEquals((recycler.height - second.height) / 2, second.top)
        }
    }

    @Test
    public fun layoutInfoExcludesSpacingFromItemSize() {
        val state = LazyListState()
        withHost { host ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                    spacing = 6f,
                ) {
                    item(key = "first") { Text("First", modifier = FlareModifier.None.height(32f)) }
                    item(key = "second") { Text("Second", modifier = FlareModifier.None.height(48f)) }
                }
            }

            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val firstRoot = recycler.getChildAt(0) as LinearLayout
            assertEquals(recycler.width, firstRoot.getChildAt(0).width)
            val first = state.layoutInfo.visibleItems.single { it.key == "first" }
            val second = state.layoutInfo.visibleItems.single { it.key == "second" }
            assertEquals(0f, first.offset, 0.5f)
            assertEquals(32f, first.size, 0.5f)
            assertEquals(38f, second.offset, 0.5f)
            assertEquals(48f, second.size, 0.5f)
        }
    }

    @Test
    public fun variableHeightColumnKeepsExactSpacingBeforeAndAfterScroll() {
        var itemOffset by mutableStateOf(0)
        withHost { host ->
            host.setContent {
                val snapshotOffset = itemOffset
                LazyColumn(
                    modifier = FlareModifier.None.width(200f).height(240f),
                    spacing = 6f,
                ) {
                    items(
                        count = 100 + snapshotOffset,
                        key = { index -> index - snapshotOffset },
                        contentType = { index ->
                            if ((index - snapshotOffset) % 5 == 0) "highlight" else "standard"
                        },
                    ) { index ->
                        val value = index - snapshotOffset
                        Text(
                            "Item $value",
                            modifier = FlareModifier.None.height(if (value % 5 == 0) 52f else 36f),
                        )
                    }
                }
            }

            layout(host)
            val recycler = host.getChildAt(0) as RecyclerView
            assertVisibleItemSpacing(recycler)

            itemOffset = 1
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            layout(host)
            assertVisibleItemSpacing(recycler)

            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(37, 0)
            layout(host)
            assertVisibleItemSpacing(recycler)
        }
    }

    @Test
    public fun stateScrollsToAnItemWithOffsetAndReportsTheViewport() {
        val state = LazyListState()
        var result: Result<Unit>? = null
        withHost { host ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 1_000, key = { it }) { index ->
                        Text("Item $index", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            layout(host)

            CoroutineScope(Dispatchers.Unconfined).launch {
                result = runCatching { state.scrollToItem(index = 80, scrollOffset = 12f) }
            }
            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val layoutManager = recycler.layoutManager as LinearLayoutManager
            val target = checkNotNull(layoutManager.findViewByPosition(80))
            val expectedOffset = -(12 * recycler.resources.displayMetrics.density).roundToInt()
            assertTrue(result?.isSuccess == true)
            assertEquals(80, layoutManager.findFirstVisibleItemPosition())
            assertEquals(expectedOffset, layoutManager.getDecoratedTop(target))
            assertEquals(1_000, state.layoutInfo.totalItemsCount)
            assertTrue(state.layoutInfo.visibleItems.any { it.index == 80 })
        }
    }

    @Test
    public fun animatedStateScrollFinishesAtTheRequestedOffsetWithoutASnap() {
        val state = LazyListState()
        var result: Result<Unit>? = null
        withHost { host ->
            host.setContent {
                LazyColumn(
                    modifier = FlareModifier.None.fillMaxSize(),
                    state = state,
                ) {
                    items(count = 200, key = { it }) { index ->
                        Text("Item $index", modifier = FlareModifier.None.height(40f))
                    }
                }
            }
            layout(host)

            CoroutineScope(Dispatchers.Unconfined).launch {
                result = runCatching { state.animateScrollToItem(index = 80, scrollOffset = 12f) }
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            layout(host)

            val recycler = host.getChildAt(0) as RecyclerView
            val layoutManager = recycler.layoutManager as LinearLayoutManager
            val target = checkNotNull(layoutManager.findViewByPosition(80))
            val expectedOffset = -(12 * recycler.resources.displayMetrics.density).roundToInt()
            assertTrue(result?.isSuccess == true)
            assertEquals(expectedOffset, layoutManager.getDecoratedTop(target))
        }
    }

    private fun withHost(block: (FlareAndroidViewHost) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val context = ContextThemeWrapper(activity, MaterialR.style.Theme_Material3_DayNight)
        val host =
            FlareAndroidViewHost(
                context = context,
                widgetSystem = createAndroidWidgetSystem(AndroidViewLazyLayoutRendererPlugin),
            )
        try {
            activity.setContentView(host)
            block(host)
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun layout(host: FlareAndroidViewHost) {
        shadowOf(Looper.getMainLooper()).idle()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 320, 480)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun RecyclerView.firstRenderedText(): String {
        val holderRoot = getChildAt(0) as android.view.ViewGroup
        return holderRoot.firstText()
    }

    private fun assertVisibleItemSpacing(recycler: RecyclerView) {
        val density = recycler.resources.displayMetrics.density
        val children =
            (0 until recycler.childCount)
                .map(recycler::getChildAt)
                .sortedBy(recycler::getChildAdapterPosition)
        children.zipWithNext().forEach { (first, second) ->
            assertTrue((first as android.view.ViewGroup).childCount > 0)
            assertTrue((second as android.view.ViewGroup).childCount > 0)
            assertEquals(
                "Unexpected gap between adapter positions ${recycler.getChildAdapterPosition(first)} and " +
                    recycler.getChildAdapterPosition(second),
                (6f * density).roundToInt(),
                second.top - first.bottom,
            )
        }
    }

    private fun android.view.ViewGroup.firstText(): String = (getChildAt(0) as TextView).text.toString()
}
