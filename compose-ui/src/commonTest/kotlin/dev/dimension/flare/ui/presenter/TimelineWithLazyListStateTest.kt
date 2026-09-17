package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineWithLazyListStateTest {
    @Test
    fun newHeadShowsBannerWithoutReplacingScrollState() =
        withTimelineState { pages, states, scrollState ->
            assertFalse(states.last().showNewToots)

            pages.value = page(-1..1)
            runCurrent()

            val updated = states.last()
            val items = assertIs<PagingState.Success<UiTimelineV2>>(updated.listState)
            assertEquals("post--1", items.peek(0)?.itemKey)
            assertEquals(1, scrollState.firstVisibleItemIndex)
            assertTrue(updated.showNewToots, "A new head should show the banner while reading older posts")
            assertEquals(1, updated.newPostsCount, "One prepended post should be counted without another scroll event")
            assertTrue(states.all { !it.showNewToots || it.newPostsCount > 0 }, "The visible banner must never report zero posts")
        }

    @Test
    fun newPostsAreCountedWhenScrollPositionUpdatesFirst() =
        withTimelineState { pages, states, scrollState ->
            scrollState.requestScrollToItem(2)
            runCurrent()

            pages.value = page(-1..1)
            runCurrent()

            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)
        }

    @Test
    fun newPostsAreCountedWhenScrollPositionUpdatesLast() =
        withTimelineState { pages, states, scrollState ->
            pages.value = page(-1..1)
            runCurrent()

            scrollState.requestScrollToItem(2)
            runCurrent()

            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)
        }

    @Test
    fun prependingPostsAndDroppingTheTailPreservesTheNewPostsCount() =
        withTimelineState { pages, states, _ ->
            pages.value = page(-1..0)
            runCurrent()

            assertEquals(2, assertIs<PagingState.Success<UiTimelineV2>>(states.last().listState).itemCount)
            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)
        }

    @Test
    fun newPostsCountAccumulatesAcrossRefreshes() =
        withTimelineState { pages, states, _ ->
            pages.value = page(-1..1)
            runCurrent()
            assertEquals(1, states.last().newPostsCount)

            pages.value = page(-3..1)
            runCurrent()
            assertEquals(3, states.last().newPostsCount)

            pages.value = page(-3..1)
            runCurrent()
            assertEquals(3, states.last().newPostsCount)
        }

    @Test
    fun scrollingOnlyClearsTheCountWhenReachingTheTop() =
        withTimelineState { pages, states, scrollState ->
            pages.value = page(-1..5)
            runCurrent()
            assertEquals(1, states.last().newPostsCount)

            scrollState.requestScrollToItem(4)
            runCurrent()
            assertEquals(1, states.last().newPostsCount)

            scrollState.requestScrollToItem(1)
            runCurrent()
            assertEquals(1, states.last().newPostsCount)

            scrollState.requestScrollToItem(0)
            runCurrent()
            assertFalse(states.last().showNewToots)
            assertEquals(0, states.last().newPostsCount)
        }

    @Test
    fun loadingOlderPostsDoesNotIncreaseTheNewPostsCount() =
        withTimelineState { pages, states, _ ->
            pages.value = page(-1..5)
            runCurrent()

            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)

            pages.value = page(-1..9)
            runCurrent()
            assertEquals(1, states.last().newPostsCount)
        }

    @Test
    fun replacingTheLoadedPageCountsTheNewlyPresentedPosts() =
        withTimelineState { pages, states, _ ->
            pages.value = page(-3..-1)
            runCurrent()

            assertTrue(states.last().showNewToots)
            assertEquals(3, states.last().newPostsCount)
        }

    @Test
    fun removingTheFirstPostDoesNotReportNewPosts() =
        withTimelineState { pages, states, _ ->
            pages.value = page(1..1)
            runCurrent()

            assertFalse(states.last().showNewToots)
            assertEquals(0, states.last().newPostsCount)
        }

    @Test
    fun appendingItemsDoesNotShowTheNewPostsBanner() =
        withTimelineState { pages, states, _ ->
            pages.value = page(0..2)
            runCurrent()

            assertEquals(3, assertIs<PagingState.Success<UiTimelineV2>>(states.last().listState).itemCount)
            assertFalse(states.last().showNewToots)
            assertEquals(0, states.last().newPostsCount)
        }

    @Test
    fun newHeadDoesNotKeepTheBannerVisibleAtTheTop() =
        withTimelineState(initialIndex = 0) { pages, states, _ ->
            pages.value = page(-1..1)
            runCurrent()

            assertEquals("post--1", assertIs<PagingState.Success<UiTimelineV2>>(states.last().listState).peek(0)?.itemKey)
            assertFalse(states.last().showNewToots)
            assertEquals(0, states.last().newPostsCount)
        }

    @Test
    fun dismissedBannerCanAppearForTheNextNewHead() =
        withTimelineState { pages, states, _ ->
            pages.value = page(-1..1)
            runCurrent()
            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)

            states.last().onNewTootsShown()
            runCurrent()
            assertFalse(states.last().showNewToots)
            assertEquals(0, states.last().newPostsCount)

            pages.value = page(-2..1)
            runCurrent()
            assertTrue(states.last().showNewToots)
            assertEquals(1, states.last().newPostsCount)
        }

    private fun withTimelineState(
        initialIndex: Int = 1,
        block: suspend TestScope.(
            MutableStateFlow<PagingData<UiTimelineV2>>,
            List<TimelineWithLazyListState>,
            LazyStaggeredGridState,
        ) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scrollState = LazyStaggeredGridState(initialFirstVisibleItemIndex = initialIndex)
        val pages = MutableStateFlow(page(0..1))
        val states = mutableListOf<TimelineWithLazyListState>()
        val job =
            launch {
                moleculeFlow(RecompositionMode.Immediate) {
                    val pagingState = pages.collectAsLazyPagingItems().toPagingState()
                    val baseState =
                        object : TimelineItemPresenter.State {
                            override val listState = pagingState
                            override val isRefreshing = pagingState.isRefreshing

                            override fun refreshSync() = Unit

                            override suspend fun refreshSuspend() = Unit
                        }
                    rememberTimelineWithLazyListState(baseState, scrollState)
                }.collect { states += it }
            }
        try {
            runCurrent()
            block(pages, states, scrollState)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
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
