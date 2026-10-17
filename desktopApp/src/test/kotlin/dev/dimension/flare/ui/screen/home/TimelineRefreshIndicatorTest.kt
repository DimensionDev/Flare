package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.TimelineWithLazyListState
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class TimelineRefreshIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val indicator get() = composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))

    @Test
    fun readingRefreshIsVisibleWhilePagingIsIdle() = checkReadingRefresh(TimelineDisplayMode.Card)

    @Test
    fun galleryReadingRefreshIsVisibleWhilePagingIsIdle() = checkReadingRefresh(TimelineDisplayMode.Gallery)

    private fun checkReadingRefresh(mode: TimelineDisplayMode) {
        val reading = ReadingState()
        val fixture = render(reading, mode)
        indicator.assertDoesNotExist()
        composeRule.runOnIdle { fixture.state.refreshSync() }
        indicator.assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fixture.state.isRefreshing)
            assertFalse(fixture.state.listState.isRefreshing)
            reading.isRefreshing = false
        }
        indicator.assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(120, fixture.scroll.firstVisibleItemScrollOffset) }
    }

    @Test
    fun failedRefreshCanEndAndStartAgainWithoutChangingTheSnapshot() {
        val reading = ReadingState()
        val fixture = render(reading)
        repeat(2) {
            composeRule.runOnIdle { fixture.state.refreshSync() }
            indicator.assertIsDisplayed()
            // Failure ends the shared refresh without publishing a new paging snapshot.
            composeRule.runOnIdle { reading.isRefreshing = false }
            indicator.assertDoesNotExist()
            composeRule.runOnIdle { assertEquals(120, fixture.scroll.firstVisibleItemScrollOffset) }
        }
    }

    @Test
    fun completionBeforeTheNextFrameDoesNotLeaveAnIndicator() {
        val reading = ReadingState()
        val fixture = render(reading)
        composeRule.runOnIdle {
            fixture.state.refreshSync()
            reading.isRefreshing = false
        }
        indicator.assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(120, fixture.scroll.firstVisibleItemScrollOffset) }
    }

    @Test
    fun ordinaryPagingRefreshStillShowsAndEnds() {
        val fixture = render()
        indicator.assertDoesNotExist()
        composeRule.runOnIdle { fixture.state.refreshSync() }
        composeRule.waitUntil { fixture.state.listState.isRefreshing }
        indicator.assertIsDisplayed()
        composeRule.runOnIdle { fixture.pages.value = page(false) }
        composeRule.waitUntil { !fixture.state.isRefreshing }
        indicator.assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(120, fixture.scroll.firstVisibleItemScrollOffset) }
    }

    private fun render(
        reading: ReadingState? = null,
        mode: TimelineDisplayMode = TimelineDisplayMode.Card,
    ): Fixture {
        val fixture = Fixture()
        composeRule.setContent {
            val paging = fixture.pages.collectAsLazyPagingItems().toPagingState()
            val state =
                object : TimelineWithLazyListState {
                    override val listState = paging
                    override val readingState = reading
                    override val lazyListState = fixture.scroll
                    override val newPostsCount = 0
                    override val showNewToots = false
                    override val isRefreshing = paging.isRefreshing || reading?.isRefreshing == true

                    override fun onNewTootsShown() = Unit

                    override fun refreshSync() {
                        if (reading != null) reading.isRefreshing = true else fixture.pages.value = page(true)
                    }

                    override suspend fun refreshSuspend() = refreshSync()
                }
            SideEffect { fixture.state = state }
            FlareTheme(isDarkTheme = false) {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance(timelineDisplayMode = mode)) {
                    Box(Modifier.size(320.dp, 240.dp)) {
                        TimelineContent(state, header = { Box(Modifier.height(2000.dp)) }, allowGalleryMode = true)
                    }
                }
            }
        }
        composeRule.runOnIdle { assertFalse(fixture.state.isRefreshing) }
        return fixture
    }

    private inner class Fixture {
        val pages = MutableStateFlow(page(false))
        val scroll = LazyStaggeredGridState(initialFirstVisibleItemOffset = 120)
        lateinit var state: TimelineWithLazyListState
    }

    private class ReadingState : TimelineReadingState {
        override val position: TimelineReadingPosition? = null
        override val hasNewContent = false
        override var isRefreshing by mutableStateOf(false)

        override fun updateViewport(
            itemKey: String?,
            offset: Double,
            atTop: Boolean,
            interacting: Boolean,
        ) = Unit

        override fun positionRestored(requestId: Long) = Unit

        override fun cancelRestoration() = Unit

        override fun savePosition() = Unit

        override suspend fun refreshAutomatically() = Unit

        override suspend fun showLatest() = Unit
    }

    private fun page(refreshing: Boolean): PagingData<UiTimelineV2> =
        PagingData.from(
            data =
                listOf(
                    UiTimelineV2.Feed(
                        title = "cached-post",
                        description = null,
                        url = "https://example.com/post",
                        createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                        source = UiTimelineV2.Feed.Source(name = "test", icon = null),
                        accountType = AccountType.Guest,
                        itemKey = "cached-post",
                    ),
                ),
            sourceLoadStates =
                LoadStates(
                    refresh = if (refreshing) LoadState.Loading else LoadState.NotLoading(false),
                    prepend = LoadState.NotLoading(true),
                    append = LoadState.NotLoading(true),
                ),
        )
}
