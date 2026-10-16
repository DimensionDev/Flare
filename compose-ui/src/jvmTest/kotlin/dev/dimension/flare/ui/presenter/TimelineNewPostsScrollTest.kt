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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun sessionRestoresPostOffsetWithHeaderAndPreservesItOnPrepend() = checkSessionRestoration(leadingHeader = true)

    @Test
    fun sessionRestoresPostOffsetWithoutHeaderAndPreservesItOnPrepend() = checkSessionRestoration(leadingHeader = false)

    @Test
    fun sessionRestoresWhenHeaderAppearsBeforePostsLoad() = checkSessionRestoration(leadingHeader = false, addHeaderBeforePosts = true)

    @Test
    fun refreshedPostsAndGapInsertionKeepThePostAndCanBeScrolledToDirectly() =
        checkSessionRestoration(leadingHeader = true, gapDuringRefresh = true)

    private fun checkSessionRestoration(
        leadingHeader: Boolean,
        addHeaderBeforePosts: Boolean = false,
        gapDuringRefresh: Boolean = false,
    ) {
        var showHeader by mutableStateOf(leadingHeader)
        val pages = MutableStateFlow(if (addHeaderBeforePosts) PagingData.empty() else page(0..9))
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
            val timeline = rememberTimelineWithLazyListState(base, scroll, leadingItemCount = if (showHeader) 1 else 0)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                state = scroll,
                modifier = Modifier.size(300.dp, 150.dp),
            ) {
                if (showHeader) {
                    item(key = "header") { Box(Modifier.fillMaxWidth().height(60.dp)) }
                }
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        if (addHeaderBeforePosts) {
            composeRule.runOnIdle { showHeader = true }
            composeRule.runOnIdle { pages.value = page(0..9) }
        }
        val headerCount = if (showHeader) 1 else 0
        composeRule.waitUntil(5_000) { reading.position == null }
        composeRule.runOnIdle {
            assertEquals(5 + headerCount, scroll.firstVisibleItemIndex)
            assertEquals(24, scroll.firstVisibleItemScrollOffset)
            assertEquals("post-5", reading.itemKey)
            pages.value = page(-3..9, gapBefore = if (gapDuringRefresh) 0 else null)
        }
        composeRule.waitUntil(5_000) { (state.listState as? PagingState.Success)?.itemCount == if (gapDuringRefresh) 14 else 13 }
        composeRule.runOnIdle {
            assertEquals(8 + headerCount + (if (gapDuringRefresh) 1 else 0), scroll.firstVisibleItemIndex)
            assertEquals(24, scroll.firstVisibleItemScrollOffset)
            assertEquals("post-5", reading.itemKey)
            if (gapDuringRefresh) {
                scroll.requestScrollToItem(headerCount)
            } else {
                reading.position = TimelineReadingPosition("post--3", 0.0, 2, latest = true)
            }
        }
        composeRule.waitUntil(
            5_000,
        ) { reading.position == null && scroll.firstVisibleItemIndex == if (gapDuringRefresh) headerCount else 0 }
        composeRule.runOnIdle {
            assertEquals(0, scroll.firstVisibleItemScrollOffset)
            if (gapDuringRefresh) assertEquals("post--3", reading.itemKey)
        }
    }

    @Test
    fun viewLatestWaitsForThePublishedHeadBeforeScrolling() = checkViewLatestPublication()

    @Test
    fun viewLatestWaitsForThePublishedHeadWithHeader() = checkViewLatestPublication(leadingHeader = true)

    @Test
    fun scrollingCancelsViewLatestWhileWaitingForThePublishedHead() = checkViewLatestPublication(cancelBeforePublication = true)

    @Test
    fun mouseWheelCancelsViewLatestWhileWaitingForThePublishedHead() =
        checkViewLatestPublication(cancelBeforePublication = true, mouseWheel = true)

    private fun checkViewLatestPublication(
        leadingHeader: Boolean = false,
        cancelBeforePublication: Boolean = false,
        mouseWheel: Boolean = false,
    ) {
        val headerCount = if (leadingHeader) 1 else 0
        val pages = MutableStateFlow(page(0..9))
        val scroll = LazyStaggeredGridState(initialFirstVisibleItemIndex = 5 + headerCount)
        val reading = ReadingState(null)
        lateinit var state: TimelineWithLazyListState
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val base =
                object : TimelineItemPresenter.State {
                    override val listState = paging
                    override val readingState = reading
                    override val isRefreshing = paging.isRefreshing || reading.isRefreshing

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            val timeline = rememberTimelineWithLazyListState(base, scroll, leadingItemCount = headerCount)
            SideEffect { state = timeline }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                state = scroll,
                modifier = Modifier.size(300.dp, 150.dp).testTag("timeline"),
            ) {
                if (leadingHeader) {
                    item(key = "header") { Box(Modifier.fillMaxWidth().height(60.dp)) }
                }
                paging.onSuccess {
                    items(itemCount, key = itemKey { it.stableItemKey }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals("post-5", reading.itemKey)
            reading.isRefreshing = true
            reading.position = TimelineReadingPosition("post-5", 0.0, 100, latest = true)
        }
        composeRule.runOnIdle {
            assertNotNull(reading.position, "The old snapshot must not acknowledge View latest")
            assertEquals(5 + headerCount, scroll.firstVisibleItemIndex)
            reading.isRefreshing = false
        }
        composeRule.runOnIdle {
            assertNotNull(reading.position, "Refresh completion does not mean the new paging data is presented")
            assertEquals(5 + headerCount, scroll.firstVisibleItemIndex)
        }
        if (cancelBeforePublication) {
            if (mouseWheel) {
                composeRule.onNodeWithTag("timeline").performMouseInput {
                    moveTo(center)
                    scroll(3f)
                }
            } else {
                composeRule.onNodeWithTag("timeline").performTouchInput {
                    swipeUp(startY = 110f, endY = 40f, durationMillis = 200)
                }
            }
        }
        var expectedKey = "post-5"
        var expectedOffset = 0
        composeRule.runOnIdle {
            if (cancelBeforePublication) {
                assertNull(reading.position, "The user's scroll must cancel the pending scroll request")
                expectedKey = assertNotNull(reading.itemKey)
                expectedOffset = scroll.firstVisibleItemScrollOffset
            }
            pages.value = page(listOf(5, 10, 6, 7, 8, 9, 0, 1, 2, 3, 4))
        }
        composeRule.waitUntil(5_000) {
            (state.listState as? PagingState.Success)?.itemCount == 11 && reading.position == null
        }
        composeRule.runOnIdle {
            assertEquals(expectedKey, reading.itemKey)
            if (cancelBeforePublication) {
                assertEquals(expectedOffset, scroll.firstVisibleItemScrollOffset)
            } else {
                assertEquals(0, scroll.firstVisibleItemIndex)
                assertEquals(0, scroll.firstVisibleItemScrollOffset)
            }
        }
    }

    private class ReadingState(
        position: TimelineReadingPosition?,
    ) : TimelineReadingState {
        override var position by mutableStateOf<TimelineReadingPosition?>(position)
        override val hasNewContent = false
        override var isRefreshing by mutableStateOf(false)
        var itemKey: String? = null

        override fun updateViewport(
            itemKey: String?,
            offset: Double,
            atTop: Boolean,
            interacting: Boolean,
        ) {
            this.itemKey = itemKey
            if (interacting) position = null
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

    private fun page(
        indices: Iterable<Int>,
        gapBefore: Int? = null,
    ): PagingData<UiTimelineV2> =
        PagingData.from(
            data =
                indices.flatMap { index ->
                    val post =
                        UiTimelineV2.Feed(
                            title = "post-$index",
                            description = null,
                            url = "https://example.com/posts/$index",
                            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                            source = UiTimelineV2.Feed.Source(name = "test", icon = null),
                            accountType = AccountType.Guest,
                            itemKey = "post-$index",
                        )
                    if (index == gapBefore) {
                        listOf(
                            UiTimelineV2.Message(
                                statusKey = MicroBlogKey("gap", "test"),
                                icon = UiIcon.Info,
                                type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.ReadingGap),
                                createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                                clickEvent = ClickEvent.Noop,
                                accountType = AccountType.Guest,
                                itemKey = "reading-gap:test",
                            ),
                            post,
                        )
                    } else {
                        listOf(post)
                    }
                },
            sourceLoadStates =
                LoadStates(
                    refresh = LoadState.NotLoading(endOfPaginationReached = false),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = false),
                ),
        )
}
