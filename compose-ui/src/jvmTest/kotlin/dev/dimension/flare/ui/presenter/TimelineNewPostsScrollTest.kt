package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.TimelineReadingPosition
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

    @Test
    fun returningToADisposedTabRestoresItsItemAfterThePagingWindowMoves() = checkReturningTab(PaddingValues(0.dp))

    @Test
    fun returningToADisposedTabKeepsItsOffsetWithContentPadding() = checkReturningTab(PaddingValues(top = 40.dp))

    @Test
    fun refreshingADeepWindowKeepsTheReadingItemAfterTheWindowShrinks() = checkShrinkingWindow(columns = 1)

    @Test
    fun refreshingADeepTwoColumnWindowKeepsTheReadingItemAfterTheWindowShrinks() = checkShrinkingWindow(columns = 2)

    private fun checkShrinkingWindow(columns: Int) {
        val pages = MutableStateFlow(page(0..199))
        val scroll = LazyStaggeredGridState()
        val reading = Reading(TimelineReadingPosition("post-160", -25.0))
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val reading = reading
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(base, scroll)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(columns), Modifier.size(300.dp, 150.dp), state = scroll) {
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            assertEquals(
                -25,
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == "post-160" }
                    .offset.y,
            )
            // A history refresh reloads a bounded page around the stable item,
            // even when a large number of newly fetched posts precedes it in DB.
            pages.value = page(154..173)
        }
        composeRule.waitUntil { (state.listState as? PagingState.Success)?.itemCount == 20 }
        composeRule.runOnIdle {
            assertEquals(
                -25,
                scroll.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == "post-160" }
                    ?.offset
                    ?.y,
            )
        }
    }

    @Test
    fun restoringAtFractionalDensityKeepsTheExactPixelOffset() {
        val pages = MutableStateFlow(page(0..19))
        val scroll = LazyStaggeredGridState()
        val reading = Reading(TimelineReadingPosition("post-5", -246.0 / 2.625))
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val reading = reading
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            rememberTimelineWithLazyListState(base, scroll, density = 2.625f)
            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(1), Modifier.size(300.dp, 150.dp), state = scroll) {
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                }
            }
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            assertEquals(
                -246,
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == "post-5" }
                    .offset.y,
            )
        }
    }

    @Test
    fun showingLatestRestoresTheHeadWhenThePagingWindowChanges() {
        val pages = MutableStateFlow(page(20..49))
        val scroll = LazyStaggeredGridState()
        val reading = Reading(TimelineReadingPosition("post-25", -25.0))
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val reading = reading
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            rememberTimelineWithLazyListState(base, scroll)
            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(1), Modifier.size(300.dp, 150.dp), state = scroll) {
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            reading.restorePosition = TimelineReadingPosition("post-0", 0.0)
            pages.value = page(0..29)
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            assertEquals(
                "post-0",
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .key,
            )
            assertEquals(
                0,
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .offset.y,
            )
        }
    }

    private fun checkReturningTab(contentPadding: PaddingValues) {
        val pages = MutableStateFlow(page(0..29))
        val reading = Reading(TimelineReadingPosition("post-5", -25.0))
        var visible by mutableStateOf(true)
        var expectedOffset = 0
        lateinit var scroll: LazyStaggeredGridState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            if (visible) {
                val nativeScroll = rememberLazyStaggeredGridState()
                val base =
                    object : TimelineItemPresenter.State {
                        override val listState = paging
                        override val reading = reading
                        override val isRefreshing = false

                        override fun refreshSync() = Unit

                        override suspend fun refreshSuspend() = Unit
                    }
                rememberTimelineWithLazyListState(base, nativeScroll)
                SideEffect { scroll = nativeScroll }
                LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Fixed(1),
                    Modifier.size(300.dp, 150.dp),
                    state = nativeScroll,
                    contentPadding = contentPadding,
                ) {
                    paging.onSuccess {
                        items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                    }
                }
            }
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            expectedOffset =
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == "post-5" }
                    .offset.y
            visible = false
        }
        composeRule.runOnIdle { pages.value = page(3..29) }
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                expectedOffset,
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == "post-5" }
                    .offset.y,
            )
        }
    }

    @Test
    fun restoringARealItemWithAHeaderKeepsItsOffsetAcrossRefresh() {
        val pages = MutableStateFlow(page(0..19))
        val scroll = LazyStaggeredGridState()
        val reading = Reading(TimelineReadingPosition("post-5", -25.0))
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val reading = reading
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(base, scroll, leadingItemCount = 1)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(1), Modifier.size(300.dp, 150.dp), state = scroll) {
                item(key = "header") { Box(Modifier.fillMaxWidth().height(100.dp)) }
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        composeRule.waitUntil { reading.restorePosition == null }
        composeRule.runOnIdle {
            assertEquals(
                "post-5",
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .key,
            )
            assertEquals(
                -25,
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .offset.y,
            )
            assertEquals(0, reading.saved, "Restoring must not overwrite the durable bookmark")
            pages.value = page(-3..19)
        }
        composeRule.waitUntil { (state.listState as? PagingState.Success)?.itemCount == 23 }
        composeRule.runOnIdle {
            assertEquals(
                "post-5",
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .key,
            )
            assertEquals(
                -25,
                scroll.layoutInfo.visibleItemsInfo
                    .first()
                    .offset.y,
            )
        }
    }

    @Test
    fun fillingAGapAtTheViewportTopRevealsTheInsertedPostsWithoutFollowingTheGapDown() = checkGap(PaddingValues(0.dp))

    @Test
    fun fillingAGapKeepsItsPredecessorWithContentPadding() = checkGap(PaddingValues(top = 40.dp))

    @Test
    fun fillingAGapAtTheStartOfTheLoadedWindowRevealsTheInsertedPosts() = checkGap(PaddingValues(0.dp), leading = true)

    @Test
    fun failedLeadingGapDoesNotTreatALaterRefreshAsItsInsertedPage() = checkGap(PaddingValues(0.dp), leading = true, failed = true)

    @Test
    fun partiallyFailedLeadingGapStillRevealsTheSuccessfullyInsertedPosts() =
        checkGap(PaddingValues(0.dp), leading = true, partialFailure = true)

    @Test
    fun refreshWhileALeadingGapLoadsDoesNotStealItsInsertionAnchor() =
        checkGap(PaddingValues(0.dp), leading = true, refreshDuringLoad = true)

    @Test
    fun coalescedRefreshAndGapInsertionChooseOnlyTheGapPosts() =
        checkGap(PaddingValues(0.dp), leading = true, refreshDuringLoad = true, coalesced = true)

    @Test
    fun gapCompletionAfterThePageIsRenderedStillRevealsItsPosts() = checkGap(PaddingValues(0.dp), leading = true, callbackAfterPage = true)

    private fun checkGap(
        contentPadding: PaddingValues,
        leading: Boolean = false,
        failed: Boolean = false,
        partialFailure: Boolean = false,
        refreshDuringLoad: Boolean = false,
        coalesced: Boolean = false,
        callbackAfterPage: Boolean = false,
    ) {
        lateinit var onInserted: (List<String>) -> Unit

        fun post(index: Int): UiTimelineV2 =
            UiTimelineV2.Feed(
                title = "post-$index",
                description = null,
                url = "https://example.com/$index",
                source = UiTimelineV2.Feed.Source("test", null),
                createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                accountType = AccountType.Guest,
                itemKey = "post-$index",
            )

        fun gap(revision: Long) =
            UiTimelineV2.Gap(
                statusKey = MicroBlogKey("gap", "local"),
                nextKey = "cursor",
                revision = revision,
                createdAt = Instant.fromEpochMilliseconds(revision).toUi(),
                itemKey = "timeline-gap:test",
                onActivate = { onInserted = it },
            )

        fun data(items: List<UiTimelineV2>) =
            PagingData.from(
                items,
                LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true)),
            )
        val firstGap = gap(0)
        val prefix = if (leading) emptyList() else listOf(post(0), post(1))
        val pages = MutableStateFlow(data(prefix + listOf(firstGap, post(8), post(9), post(10))))
        val scroll = LazyStaggeredGridState(initialFirstVisibleItemIndex = prefix.size)
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val isRefreshing = false

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(base, scroll)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(
                StaggeredGridCells.Fixed(1),
                Modifier.size(300.dp, 150.dp),
                state = scroll,
                contentPadding = contentPadding,
            ) {
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        composeRule.runOnIdle {
            state.loadGap(firstGap)
            if (failed || partialFailure) pages.value = data(listOf(firstGap.copy(failed = true), post(8), post(9), post(10)))
            if (failed) onInserted(emptyList())
            if (refreshDuringLoad &&
                !coalesced
            ) {
                pages.value = data(listOf(post(100), firstGap.copy(loading = true), post(8), post(9), post(10)))
            }
        }
        if (failed || partialFailure) {
            composeRule.waitUntil {
                ((state.listState as? PagingState.Success)?.peek(0) as? UiTimelineV2.Gap)?.failed == true
            }
            composeRule.waitForIdle()
        }
        if (refreshDuringLoad && !coalesced) {
            composeRule.waitUntil { (state.listState as? PagingState.Success)?.itemCount == 5 }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            if (!failed && !callbackAfterPage) onInserted(listOf("post-2", "post-3"))
            val refresh = if (refreshDuringLoad) listOf(post(100)) else emptyList()
            val remainingGap = if (failed) firstGap.copy(failed = true) else gap(1).copy(failed = partialFailure)
            pages.value =
                data(refresh + prefix + listOf(post(2), post(3), remainingGap, post(8), post(9), post(10)))
        }
        composeRule.waitUntil { (state.listState as? PagingState.Success)?.itemCount == prefix.size + 6 + if (refreshDuringLoad) 1 else 0 }
        if (callbackAfterPage) {
            composeRule.waitForIdle()
            composeRule.runOnIdle { onInserted(listOf("post-2", "post-3")) }
        }
        composeRule.runOnIdle {
            assertEquals(
                if (failed) firstGap.stableItemKey else "post-2",
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == if (failed) firstGap.stableItemKey else "post-2" }
                    .key,
            )
            assertEquals(
                0,
                scroll.layoutInfo.visibleItemsInfo
                    .first { it.key == if (failed) firstGap.stableItemKey else "post-2" }
                    .offset.y,
            )
        }
    }

    private class Reading(
        private var target: TimelineReadingPosition,
    ) : TimelineReadingState {
        override var restorePosition by mutableStateOf<TimelineReadingPosition?>(target)
        override val hasNewContent = false
        var saved = 0

        override fun viewportChanged(
            itemKey: String,
            offset: Double,
        ) = Unit

        override fun positionChanged(
            itemKey: String,
            offset: Double,
        ) {
            saved++
            target = TimelineReadingPosition(itemKey, offset)
        }

        override fun restorationFinished() {
            restorePosition = null
        }

        override fun restoreCurrentPosition() {
            if (restorePosition == null) restorePosition = target
        }

        override fun showLatest() = Unit
    }

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
