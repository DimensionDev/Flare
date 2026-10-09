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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class TimelineNewPostsScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollingRenderedPostsConsumesTheCount() = checkScrolling(leadingHeader = false)

    @Test
    fun scrollingRenderedPostsExcludesTheLeadingHeader() = checkScrolling(leadingHeader = true)

    private fun checkScrolling(leadingHeader: Boolean) {
        val pages = MutableStateFlow(page(0..9))
        val scrollState = LazyStaggeredGridState(initialFirstVisibleItemIndex = 5)
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val pagingState = pages.collectAsLazyPagingItems().toPagingState()
            val baseState =
                object : TimelineItemPresenter.State {
                    override val listState = pagingState
                    override val isRefreshing = pagingState.isRefreshing

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(baseState, scrollState)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                state = scrollState,
                modifier = Modifier.size(width = 300.dp, height = 150.dp),
            ) {
                if (leadingHeader) {
                    item(key = "header") {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
                pagingState.onSuccess {
                    items(itemCount, key = itemKey { requireNotNull(it.itemKey) }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(5, scrollState.firstVisibleItemIndex)
            assertEquals(0, state.newPostsCount)
            pages.value = page(-3..9)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (state.listState as? PagingState.Success)?.itemCount == 13
        }
        composeRule.runOnIdle {
            assertEquals(8, scrollState.firstVisibleItemIndex, "The grid should keep the same post visible after prepending")
            assertEquals(3, state.newPostsCount)
            scrollState.requestScrollToItem(if (leadingHeader) 3 else 2)
        }
        composeRule.runOnIdle {
            assertEquals(2, state.newPostsCount)
            scrollState.requestScrollToItem(8)
        }
        composeRule.runOnIdle {
            assertEquals(2, state.newPostsCount, "Read posts should stay read when scrolling away")
            scrollState.requestScrollToItem(if (leadingHeader) 1 else 0, 20)
        }
        composeRule.runOnIdle {
            assertEquals(0, state.newPostsCount)
            assertFalse(state.showNewToots)
        }
    }

    @Test
    fun sessionRestoresPostOffsetWithHeaderAndPreservesItOnPrepend() {
        val pages = MutableStateFlow(page(0..9))
        val scroll = LazyStaggeredGridState()
        val reading = ReadingState(TimelineReadingPosition("post-5", -24.0, 1))
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val readingState = reading
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(base, scroll, leadingItemCount = 1)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                state = scroll,
                modifier = Modifier.size(300.dp, 150.dp),
            ) {
                item(key = "header") { Box(Modifier.fillMaxWidth().height(60.dp)) }
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { reading.position == null && scroll.firstVisibleItemIndex == 6 }
        composeRule.runOnIdle {
            assertEquals(24, scroll.firstVisibleItemScrollOffset)
            assertEquals("post-5", reading.itemKey)
            pages.value = page(-3..9)
        }
        composeRule.waitUntil(5_000) { (state.listState as? PagingState.Success)?.itemCount == 13 }
        composeRule.runOnIdle {
            assertEquals(9, scroll.firstVisibleItemIndex)
            assertEquals(24, scroll.firstVisibleItemScrollOffset)
            assertEquals("post-5", reading.itemKey)
            reading.position = TimelineReadingPosition("post--3", 0.0, 2, latest = true)
        }
        composeRule.waitUntil(5_000) { reading.position == null && scroll.firstVisibleItemIndex == 0 }
        composeRule.runOnIdle { assertEquals(0, scroll.firstVisibleItemScrollOffset) }
    }

    private class ReadingState(
        position: TimelineReadingPosition,
    ) : TimelineReadingState {
        override var position by mutableStateOf<TimelineReadingPosition?>(position)
        override val hasNewContent = false
        override val isRefreshing = false
        var itemKey: String? = null

        override fun updateViewport(
            itemKey: String?,
            offset: Double,
            atTop: Boolean,
            interacting: Boolean,
        ) {
            this.itemKey = itemKey
        }

        override fun positionRestored(requestId: Long) {
            if (position?.requestId == requestId) position = null
        }

        override fun cancelRestoration() {
            position = null
        }

        override fun savePosition() = Unit

        override suspend fun refreshAutomatically() = Unit

        override suspend fun showLatest() = Unit
    }

    private fun page(indices: IntRange): PagingData<UiTimelineV2> =
        PagingData.from(
            data =
                indices.map { index ->
                    UiTimelineV2.Feed(
                        title = "post-$index",
                        description = null,
                        url = "https://example.com/posts/$index",
                        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                        source = UiTimelineV2.Feed.Source(name = "test", icon = null),
                        accountType = AccountType.Guest,
                        itemKey = "post-$index",
                    )
                },
            sourceLoadStates =
                LoadStates(
                    refresh = LoadState.NotLoading(endOfPaginationReached = false),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = false),
                ),
        )
}
