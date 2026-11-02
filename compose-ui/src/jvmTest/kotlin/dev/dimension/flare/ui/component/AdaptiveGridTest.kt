package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdaptiveGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactTimelineKeepsItsNaturalHeight() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance.Default,
                LocalTimelineAppearance provides TimelineAppearance.Default,
            ) {
                Box(Modifier.size(400.dp, 600.dp)) {
                    LazyStatusVerticalStaggeredGrid(
                        modifier = Modifier.heightIn(max = 200.dp).testTag("compact-list"),
                        columns = StaggeredGridCells.Fixed(1),
                    ) { item { Box(Modifier.height(40.dp)) } }
                }
            }
        }
        assertEquals(
            with(composeRule.density) { 40.dp.roundToPx() },
            composeRule
                .onNodeWithTag("compact-list")
                .fetchSemanticsNode()
                .layoutInfo.height,
        )
    }

    @Test
    fun mediaWithoutAnImageAspectKeepsItsIntrinsicHeight() {
        composeRule.setContent {
            Box(Modifier.size(400.dp, 600.dp)) {
                AdaptiveGrid(
                    itemCount = 1,
                    maxSingleHeight = 260.dp,
                    itemContent = { Box(Modifier.height(64.dp).testTag("audio")) },
                )
            }
        }
        assertEquals(
            with(composeRule.density) { 64.dp.roundToPx() },
            composeRule
                .onNodeWithTag("audio")
                .fetchSemanticsNode()
                .layoutInfo.height,
        )
    }

    @Test
    fun singleImageRespectsViewportCapAndKeepsOrdinaryImageHeight() {
        var cap by mutableStateOf(455.dp)
        var ratio by mutableStateOf(0.125f)
        composeRule.setContent {
            Box(Modifier.size(400.dp, 1_000.dp)) {
                AdaptiveGrid(
                    itemCount = 1,
                    itemContent = { Box(Modifier.fillMaxSize().testTag("image")) },
                    singleAspectRatio = ratio,
                    maxSingleHeight = cap,
                )
            }
        }
        for ((height, aspect) in listOf(455 to 0.125f, 260 to 0.125f, 600 to (4f / 3f), 0 to 0.125f)) {
            composeRule.runOnIdle {
                cap = height.dp
                ratio = aspect
            }
            val image = composeRule.onNodeWithTag("image").fetchSemanticsNode().layoutInfo
            val expected = minOf(height.toFloat(), 400 / aspect)
            assertEquals(with(composeRule.density) { expected.dp.roundToPx() }, image.height)
        }
    }

    @Test
    fun timelineProvidesTheCapBeforeFirstMediaMeasureAndScrollingDoesNotRemeasureIt() {
        val budgets = mutableListOf<Float>()
        val measurements = mutableMapOf<Int, Int>()
        lateinit var state: LazyStaggeredGridState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance.Default,
                LocalTimelineAppearance provides TimelineAppearance.Default,
            ) {
                state = remember { LazyStaggeredGridState() }
                scope = rememberCoroutineScope()
                Box(Modifier.size(400.dp, 600.dp)) {
                    LazyStatusVerticalStaggeredGrid(state = state, columns = StaggeredGridCells.Fixed(1)) {
                        items(20) { index ->
                            val cap = LocalTimelineMediaMaxHeight.current
                            SideEffect { budgets.add(cap.value) }
                            AdaptiveGrid(
                                itemCount = 1,
                                singleAspectRatio = 0.125f,
                                maxSingleHeight = cap,
                                itemContent = {
                                    Box(
                                        Modifier.fillMaxSize().layout { measurable, constraints ->
                                            measurements[index] = measurements.getOrDefault(index, 0) + 1
                                            val child = measurable.measure(constraints)
                                            layout(child.width, child.height) { child.place(0, 0) }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        val initial =
            composeRule.runOnIdle {
                assertTrue(budgets.isNotEmpty())
                assertTrue(
                    budgets.all { kotlin.math.abs(it - 390f) < 0.01f },
                    "Budget must be available in the first composition: $budgets",
                )
                measurements.getValue(0)
            }
        composeRule.runOnIdle { scope.launch { state.scrollToItem(0, 8) } }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(initial, measurements.getValue(0), "Scrolling must only place existing media") }
    }

    @Test
    fun shrinkingMediaRetainsTheRemainingItem() {
        var media by mutableStateOf(mediaItems(2))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        val retained =
            composeRule.runOnIdle {
                probe.instances.getValue("media-0").also { media = mediaItems(1) }
            }
        composeRule.runOnIdle {
            assertEquals(setOf("media-0"), probe.instances.keys)
            assertSame(retained, probe.instances.getValue("media-0"))
            assertEquals(listOf("media-0", "media-1"), probe.created)
            assertEquals(listOf("media-1"), probe.disposed)
        }
    }

    @Test
    fun changingMediaCountAboveTheLimitKeepsTheVisibleItems() {
        var media by mutableStateOf(mediaItems(9))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        val retained = composeRule.runOnIdle { probe.instances.toMap() }
        for (count in listOf(12, 10, 9)) {
            composeRule.runOnIdle { media = mediaItems(count) }
            composeRule.runOnIdle {
                assertEquals(retained, probe.instances)
                assertEquals(9, probe.created.size)
                assertTrue(probe.disposed.isEmpty())
                assertEquals((count - 9).takeIf { it > 0 }, probe.overflowCount)
            }
        }
    }

    @Test
    fun mediaCanBecomeEmptyAndBePopulatedAgain() {
        var media by mutableStateOf(mediaItems(2))
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(media) }
        composeRule.runOnIdle { media = mediaItems(0) }
        composeRule.runOnIdle {
            assertTrue(probe.instances.isEmpty())
            assertEquals(setOf("media-0", "media-1"), probe.disposed.toSet())
            assertNull(probe.overflowCount)
            media = mediaItems(3)
        }
        composeRule.runOnIdle {
            assertEquals(media.toSet(), probe.instances.keys)
            assertEquals(5, probe.created.size)
        }
    }

    @Test
    fun zeroVisibleItemsComposeNeitherMediaNorOverflow() {
        val probe = MediaProbe()
        composeRule.setContent { probe.Grid(mediaItems(3), maxItems = 0) }
        composeRule.runOnIdle {
            assertTrue(probe.created.isEmpty())
            assertNull(probe.overflowCount)
        }
    }

    @Test
    fun overflowCoversTheLastVisibleCellInBothDirections() {
        var maxItems by mutableIntStateOf(1)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.size(width = 240.dp, height = 600.dp)) {
                    AdaptiveGrid(
                        itemCount = 12,
                        itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                        maxItems = maxItems,
                        overflowContent = { Box(Modifier.fillMaxSize().testTag("overflow")) },
                    )
                }
            }
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (limit in listOf(1, 2, 3, 4, 5, 7, 8, 9)) {
                composeRule.runOnIdle {
                    direction = layoutDirection
                    maxItems = limit
                }
                assertEquals(
                    composeRule.onNodeWithTag("media-${limit - 1}").fetchSemanticsNode().boundsInRoot,
                    composeRule.onNodeWithTag("overflow").fetchSemanticsNode().boundsInRoot,
                    "The overflow should cover the last cell for $limit items in $layoutDirection",
                )
            }
        }
    }

    @Test
    fun threeAndFourImagesHandleExhaustedParentHeight() {
        var count by mutableIntStateOf(4)
        composeRule.setContent {
            Box(Modifier.size(width = 240.dp, height = 40.dp)) {
                Column {
                    Box(Modifier.height(40.dp))
                    Box {
                        AdaptiveGrid(
                            itemCount = count,
                            itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                            modifier = Modifier.testTag("grid"),
                        )
                    }
                }
            }
        }
        for (mediaCount in listOf(4, 3)) {
            composeRule.runOnIdle { count = mediaCount }
            assertEquals(
                0,
                composeRule
                    .onNodeWithTag("grid")
                    .fetchSemanticsNode()
                    .layoutInfo.height,
            )
            repeat(mediaCount) {
                assertEquals(
                    0,
                    composeRule
                        .onNodeWithTag("media-$it")
                        .fetchSemanticsNode()
                        .layoutInfo.height,
                )
            }
        }
    }

    @Test
    fun narrowLayoutsKeepCellsAndOverflowWithinTheGrid() {
        var width by mutableStateOf(0.dp)
        var count by mutableIntStateOf(2)
        composeRule.setContent {
            Box(Modifier.size(width = width, height = 600.dp)) {
                AdaptiveGrid(
                    itemCount = 12,
                    maxItems = count,
                    itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                    modifier = Modifier.testTag("grid"),
                    overflowContent = { Box(Modifier.fillMaxSize().testTag("overflow")) },
                )
            }
        }
        for (gridWidth in listOf(0.dp, 1.dp, 4.dp)) {
            for (mediaCount in 2..9) {
                composeRule.runOnIdle {
                    width = gridWidth
                    count = mediaCount
                }
                val grid = composeRule.onNodeWithTag("grid").fetchSemanticsNode()
                repeat(mediaCount) {
                    val cell = composeRule.onNodeWithTag("media-$it").fetchSemanticsNode()
                    assertTrue(cell.layoutInfo.width in 0..grid.layoutInfo.width)
                    assertTrue(cell.layoutInfo.height >= 0)
                    assertTrue(cell.boundsInRoot.left >= grid.boundsInRoot.left)
                    assertTrue(cell.boundsInRoot.right <= grid.boundsInRoot.right)
                }
                assertEquals(
                    composeRule.onNodeWithTag("media-${mediaCount - 1}").fetchSemanticsNode().boundsInRoot,
                    composeRule.onNodeWithTag("overflow").fetchSemanticsNode().boundsInRoot,
                )
            }
        }
    }

    @Test
    fun fixedGridMinimumsAreNotPassedToEachCell() {
        var count by mutableIntStateOf(1)
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            AdaptiveGrid(
                itemCount = count,
                itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                modifier = Modifier.size(width = 240.dp, height = 600.dp).testTag("grid"),
                expandedSize = expanded,
            )
        }
        for (expandedSize in listOf(true, false)) {
            for (mediaCount in 1..9) {
                composeRule.runOnIdle {
                    count = mediaCount
                    expanded = expandedSize
                }
                val grid = composeRule.onNodeWithTag("grid").fetchSemanticsNode()
                assertEquals(with(composeRule.density) { 240.dp.roundToPx() }, grid.layoutInfo.width)
                assertEquals(with(composeRule.density) { 600.dp.roundToPx() }, grid.layoutInfo.height)
                repeat(mediaCount) {
                    val cell = composeRule.onNodeWithTag("media-$it").fetchSemanticsNode()
                    assertTrue(cell.layoutInfo.width in 0..grid.layoutInfo.width)
                    assertTrue(cell.layoutInfo.height in 0..grid.layoutInfo.height)
                }
            }
        }
    }

    @Test
    fun fourImagesKeepNormalCellSizesAndSpacing() {
        composeRule.setContent {
            Box(Modifier.size(width = 240.dp, height = 600.dp)) {
                AdaptiveGrid(
                    itemCount = 4,
                    itemContent = { Box(Modifier.fillMaxSize().testTag("media-$it")) },
                    modifier = Modifier.testTag("grid"),
                )
            }
        }
        val width = with(composeRule.density) { 240.dp.roundToPx() }
        val gap = with(composeRule.density) { 4.dp.roundToPx() }
        val height = width * 9 / 16
        assertEquals(
            height,
            composeRule
                .onNodeWithTag("grid")
                .fetchSemanticsNode()
                .layoutInfo.height,
        )
        val first = composeRule.onNodeWithTag("media-0").fetchSemanticsNode()
        val last = composeRule.onNodeWithTag("media-3").fetchSemanticsNode()
        assertEquals((width - gap) / 2, first.layoutInfo.width)
        assertEquals((height - gap) / 2, first.layoutInfo.height)
        assertEquals(first.boundsInRoot.right + gap, last.boundsInRoot.left)
        assertEquals(first.boundsInRoot.bottom + gap, last.boundsInRoot.top)
    }

    private fun mediaItems(count: Int): ImmutableList<String> = (0 until count).map { "media-$it" }.toImmutableList()

    private class MediaProbe {
        val instances = mutableMapOf<String, Any>()
        val created = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        var overflowCount: Int? = null

        @Composable
        fun Grid(
            media: ImmutableList<String>,
            maxItems: Int = 9,
        ) {
            Box(Modifier.size(width = 240.dp, height = 600.dp)) {
                AdaptiveGrid(
                    itemCount = media.size,
                    itemContent = { index ->
                        // Match StatusMediaComponent's indexed callback and nested composition.
                        CompositionLocalProvider(LocalMediaProbe provides true) {
                            Item(media[index])
                        }
                    },
                    maxItems = maxItems,
                    overflowContent = { count ->
                        DisposableEffect(count) {
                            overflowCount = count
                            onDispose { overflowCount = null }
                        }
                        Box(Modifier.fillMaxSize())
                    },
                )
            }
        }

        @Composable
        private fun Item(id: String) {
            val instance = remember { Any() }
            DisposableEffect(id) {
                instances[id] = instance
                created += id
                onDispose {
                    instances.remove(id)
                    disposed += id
                }
            }
            Box(Modifier.fillMaxSize())
        }
    }
}

private val LocalMediaProbe = compositionLocalOf { false }
