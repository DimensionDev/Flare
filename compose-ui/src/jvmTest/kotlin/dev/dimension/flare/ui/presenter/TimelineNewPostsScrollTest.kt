package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.SideEffect
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
