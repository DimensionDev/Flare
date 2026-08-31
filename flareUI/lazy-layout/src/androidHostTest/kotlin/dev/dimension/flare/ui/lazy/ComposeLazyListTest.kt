package dev.dimension.flare.ui.lazy

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.compose.AndroidComposeLazyLayoutRendererPlugin
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class ComposeLazyListTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun customBusinessKeyIsAcceptedByTheComposeRenderer() {
        val state = LazyListState()
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(modifier = FlareModifier.None.fillMaxSize(), state = state) {
                        items(
                            count = 100,
                            key = { index -> CustomBusinessKey(index) },
                        ) { index ->
                            Text(
                                text = "Item $index",
                                modifier = FlareModifier(testTag = "custom-key-item-$index"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("custom-key-item-0").assertTextEquals("Item 0")
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(
            CustomBusinessKey(0),
            state.layoutInfo.visibleItems
                .first()
                .key,
        )
    }

    @Test
    public fun lazyColumnKeepsGlobalCountButComposesOnlyViewport() {
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                        items(
                            count = 10_000,
                            key = { index -> index },
                        ) { index ->
                            Text(
                                text = "Item $index",
                                modifier = FlareModifier(testTag = "item-$index"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("item-0").assertTextEquals("Item 0")
        composeRule.onAllNodesWithTag("item-9999").assertCountEquals(0)
    }

    @Test
    public fun largeModelUpdateDoesNotScanEveryKey() {
        var generation by mutableIntStateOf(0)
        var keyLookups = 0
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
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
            }
        }
        composeRule.waitForIdle()

        keyLookups = 0
        composeRule.runOnIdle { generation = 1 }
        composeRule.waitForIdle()

        org.junit.Assert.assertTrue("Model update resolved $keyLookups keys.", keyLookups < 500)
    }

    @Test
    public fun visibleStableItemRebindsContentWithoutACoordinatorScan() {
        var label by mutableStateOf("Before")
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    val labelSnapshot = label
                    LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                        item(key = "stable") {
                            Text(labelSnapshot, modifier = FlareModifier(testTag = "stable-item"))
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("stable-item").assertTextEquals("Before")

        composeRule.runOnIdle { label = "After" }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stable-item").assertTextEquals("After")
    }

    @Test
    public fun lazyRowComposesHorizontalViewport() {
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyRow(modifier = FlareModifier.None.fillMaxSize()) {
                        items(
                            count = 10_000,
                            key = { index -> index },
                        ) { index ->
                            Text(
                                text = "Item $index",
                                modifier = FlareModifier(testTag = "row-item-$index"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("row-item-0").assertTextEquals("Item 0")
        composeRule.onAllNodesWithTag("row-item-9999").assertCountEquals(0)
    }

    @Test
    public fun itemContentPlacesMultipleRootsAlongTheMainAxis() {
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(modifier = FlareModifier.None.fillMaxSize()) {
                        item(key = "multiple") {
                            Text(
                                text = "First",
                                modifier = FlareModifier(testTag = "first").height(24f),
                            )
                            Text(
                                text = "Second",
                                modifier = FlareModifier(testTag = "second").height(36f),
                            )
                        }
                    }
                }
            }
        }

        val first = composeRule.onNodeWithTag("first").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("second").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(second.top >= first.bottom)
    }

    @Test
    public fun lazyGeometryUsesContentSizeSpacingAndCenteredCrossAxis() {
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyRow(
                        modifier = FlareModifier(testTag = "geometry-row").width(200f).height(80f),
                        spacing = 6f,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        item(key = "first") {
                            Text(
                                "First",
                                modifier = FlareModifier(testTag = "geometry-first").width(40f).height(24f),
                            )
                        }
                        item(key = "second") {
                            Text(
                                "Second",
                                modifier = FlareModifier(testTag = "geometry-second").width(60f).height(24f),
                            )
                        }
                    }
                }
            }
        }

        val row = composeRule.onNodeWithTag("geometry-row").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("geometry-first").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("geometry-second").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(40f, (first.right - first.left).value, 0.5f)
        org.junit.Assert.assertEquals(60f, (second.right - second.left).value, 0.5f)
        org.junit.Assert.assertEquals(6f, (second.left - first.right).value, 0.5f)
        org.junit.Assert.assertEquals(24f, (first.bottom - first.top).value, 0.5f)
        org.junit.Assert.assertEquals(28f, (first.top - row.top).value, 0.5f)
        org.junit.Assert.assertEquals(28f, (second.top - row.top).value, 0.5f)
    }

    @Test
    public fun layoutInfoExcludesSpacingFromItemSize() {
        val state = LazyListState()
        composeRule.setContent {
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(
                        modifier = FlareModifier.None.width(200f).height(120f),
                        state = state,
                        spacing = 6f,
                    ) {
                        item(key = "first") {
                            Text("First", modifier = FlareModifier(testTag = "stretch-first").height(32f))
                        }
                        item(key = "second") { Text("Second", modifier = FlareModifier.None.height(48f)) }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val stretched = composeRule.onNodeWithTag("stretch-first").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(200f, (stretched.right - stretched.left).value, 0.5f)
        val first = state.layoutInfo.visibleItems.single { it.key == "first" }
        val second = state.layoutInfo.visibleItems.single { it.key == "second" }
        org.junit.Assert.assertEquals(0f, first.offset, 0.5f)
        org.junit.Assert.assertEquals(32f, first.size, 0.5f)
        org.junit.Assert.assertEquals(38f, second.offset, 0.5f)
        org.junit.Assert.assertEquals(48f, second.size, 0.5f)
    }

    @Test
    public fun variableHeightColumnKeepsExactSpacingBeforeAndAfterScroll() {
        val state = LazyListState()
        lateinit var scope: CoroutineScope
        var itemOffset by mutableIntStateOf(0)
        composeRule.setContent {
            scope = rememberCoroutineScope()
            val snapshotOffset = itemOffset
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(
                        modifier = FlareModifier.None.width(200f).height(240f),
                        state = state,
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
                                modifier =
                                    FlareModifier(testTag = "variable-item-$value")
                                        .height(if (value % 5 == 0) 52f else 36f),
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        assertVisibleItemSpacing(state, itemOffset)

        composeRule.runOnIdle { itemOffset = 1 }
        composeRule.waitForIdle()
        assertVisibleItemSpacing(state, itemOffset)

        composeRule.runOnIdle {
            scope.launch { state.scrollToItem(37) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.layoutInfo.visibleItems.any { it.index == 37 }
        }
        assertVisibleItemSpacing(state, itemOffset)
    }

    @Test
    public fun stateScrollsToAnOffscreenItem() {
        val state = LazyListState()
        lateinit var scope: CoroutineScope
        var result: Result<Unit>? = null
        composeRule.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme {
                FlareComposeHost(
                    widgetSystem = createAndroidComposeWidgetSystem(AndroidComposeLazyLayoutRendererPlugin),
                ) {
                    LazyColumn(
                        modifier = FlareModifier.None.fillMaxSize(),
                        state = state,
                    ) {
                        items(count = 1_000, key = { it }) { index ->
                            Text(
                                text = "Item $index",
                                modifier = FlareModifier(testTag = "scroll-item-$index").height(40f),
                            )
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            scope.launch {
                result = runCatching { state.scrollToItem(250) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("scroll-item-250").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("scroll-item-250").assertTextEquals("Item 250")
        composeRule.onAllNodesWithTag("scroll-item-0").assertCountEquals(0)
        org.junit.Assert.assertTrue(result?.isSuccess == true)
        org.junit.Assert.assertEquals(1_000, state.layoutInfo.totalItemsCount)
    }

    private fun assertVisibleItemSpacing(
        state: LazyListState,
        itemOffset: Int,
    ) {
        val visibleIndices =
            state.layoutInfo.visibleItems
                .map { it.index }
                .sorted()
        visibleIndices.zipWithNext().forEach { (firstIndex, secondIndex) ->
            val first =
                composeRule
                    .onNodeWithTag("variable-item-${firstIndex - itemOffset}")
                    .getUnclippedBoundsInRoot()
            val second =
                composeRule
                    .onNodeWithTag("variable-item-${secondIndex - itemOffset}")
                    .getUnclippedBoundsInRoot()
            org.junit.Assert.assertEquals(
                "Unexpected gap between items $firstIndex and $secondIndex",
                6f,
                (second.top - first.bottom).value,
                0.5f,
            )
        }
    }

    private data class CustomBusinessKey(
        val value: Int,
    )
}
