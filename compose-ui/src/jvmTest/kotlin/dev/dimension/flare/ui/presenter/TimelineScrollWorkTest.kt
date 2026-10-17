package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineScrollWorkTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun currentNormalSmall() = probe(240, false)

    @Test fun currentNormalLarge() = probe(2400, false)

    @Test fun currentReadingSmall() = probe(240, true)

    @Test fun currentReadingLarge() = probe(2400, true)

    @Test fun currentReadingLargeTwoColumns() = probe(2400, true, columns = 2)

    @Test fun currentNormalLargeTwoColumns() = probe(2400, false, columns = 2)

    private fun probe(
        size: Int,
        enabled: Boolean,
        columns: Int = 1,
    ) {
        val rows =
            CountingList(
                (0 until size)
                    .map { index ->
                        UiTimelineV2.Feed(
                            title = "post-$index",
                            description = null,
                            url = "https://example.com/$index",
                            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                            source = UiTimelineV2.Feed.Source("audit", null),
                            accountType = AccountType.Guest,
                            itemKey = "post-$index",
                        ) as UiTimelineV2
                    }.toImmutableList(),
            )
        val onRefresh: suspend () -> Unit = {}
        val onRetry: () -> Unit = {}
        val constructor =
            Class
                .forName("dev.dimension.flare.common.PagingState\$Success\$ImmutableSuccess")
                .declaredConstructors
                .single { it.parameterCount == 6 }
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val paging =
            constructor.newInstance(
                rows,
                size,
                false,
                LoadState.NotLoading(true),
                onRefresh,
                onRetry,
            ) as PagingState.Success<UiTimelineV2>
        val scroll = LazyStaggeredGridState(initialFirstVisibleItemIndex = size / 2, initialFirstVisibleItemOffset = 10)
        val reading = Reading()
        var measures = 0
        var placements = 0
        var presentations = 0
        var observedGeneration = 0
        composeRule.setContent {
            // Detect accidental per-pixel commands independently of the shared state projection.
            val generation = reading.generation
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val readingState = if (enabled) reading else null
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            rememberTimelineWithLazyListState(base, scroll)
            SideEffect {
                presentations++
                observedGeneration = generation
            }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                state = scroll,
                modifier =
                    Modifier.size(300.dp, 150.dp).testTag("audit").layout { measurable, constraints ->
                        measures++
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placements++
                            placeable.place(0, 0)
                        }
                    },
            ) {
                items(paging.itemCount, key = paging.itemKey { it.stableItemKey }) {
                    Box(Modifier.fillMaxWidth().height(100.dp))
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(rows.reads <= size * 2 + 64, "Initialization must not build unused reading or notice indices")
        println("COMPOSE_AUDIT revision=current rows=$size reading=$enabled columns=$columns phase=initial peek_calls=${rows.reads}")
        composeRule.onNodeWithTag("audit").performTouchInput {
            down(center)
            moveBy(Offset(0f, -22f))
        }
        composeRule.waitForIdle()
        var startIndex = 0
        var startOffset = 0
        var beforeCancels = 0
        var beforePresentations = 0
        composeRule.runOnIdle {
            assertTrue(scroll.isScrollInProgress)
            startIndex = scroll.firstVisibleItemIndex
            startOffset = scroll.firstVisibleItemScrollOffset
            rows.reads = 0
            beforeCancels = reading.generation
            beforePresentations = presentations
            measures = 0
            placements = 0
        }
        repeat(20) {
            composeRule.onNodeWithTag("audit").performTouchInput { moveBy(Offset(0f, -1f)) }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertEquals(startIndex, scroll.firstVisibleItemIndex, "Measure pixels within one post")
            assertTrue(scroll.firstVisibleItemScrollOffset > startOffset)
            println(
                "COMPOSE_AUDIT revision=current rows=$size reading=$enabled columns=$columns phase=20_pixel_moves peek_calls=${rows.reads} cancel_calls=${reading.generation - beforeCancels} presentations=${presentations - beforePresentations} viewport_reports=${reading.reports} observed_generation=$observedGeneration measures=$measures placements=$placements",
            )
            assertTrue(rows.reads < 100, "Pixel-only scroll must not enumerate the loaded list")
            assertEquals(0, reading.generation - beforeCancels, "Pixel updates must not repeat the interaction-start command")
            assertEquals(0, presentations - beforePresentations)
            assertEquals(0, measures, "Pixel scroll must not remeasure the grid container")
        }
        composeRule.onNodeWithTag("audit").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { rows.reads = 0 }
        repeat(10) { n ->
            composeRule.runOnIdle { scroll.requestScrollToItem(size / 2 + 5 + n, 10) }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            println(
                "COMPOSE_AUDIT revision=current rows=$size reading=$enabled columns=$columns phase=10_post_moves peek_calls=${rows.reads}",
            )
            assertTrue(rows.reads < 200, "Crossing posts must not scan from the beginning of the loaded list")
        }
    }

    private class CountingList(
        private val source: ImmutableList<UiTimelineV2>,
    ) : ImmutableList<UiTimelineV2> by source {
        var reads = 0

        override fun get(index: Int): UiTimelineV2 {
            reads++
            return source[index]
        }
    }

    private class Reading : TimelineReadingState {
        var generation by mutableIntStateOf(0)
        var reports = 0
        override val position: TimelineReadingPosition? = null
        override val hasNewContent = false
        override val isRefreshing = false

        override fun updateViewport(
            itemKey: String?,
            offset: Double,
            atTop: Boolean,
            interacting: Boolean,
        ) {
            reports++
        }

        override fun cancelRestoration() {
            generation++
        }

        override fun positionRestored(requestId: Long) = Unit

        override fun savePosition() = Unit

        override suspend fun refreshAutomatically() = Unit

        override suspend fun showLatest() = Unit
    }
}
