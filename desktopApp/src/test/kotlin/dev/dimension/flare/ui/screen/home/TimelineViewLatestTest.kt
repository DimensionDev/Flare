package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.Res
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.home_timeline_new_content
import dev.dimension.flare.home_timeline_new_toots
import dev.dimension.flare.home_timeline_view_latest
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.TimelineWithLazyListState
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineViewLatestTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readingNoticePublishesLatestBeforeDismissing() {
        val reading = ReadingState()
        val scroll = render(reading)
        val label = runBlocking { getString(Res.string.home_timeline_view_latest) }
        val notice = runBlocking { getString(Res.string.home_timeline_new_content) }
        composeRule.onNodeWithText(notice).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(label).performClick()

        composeRule.runOnIdle {
            assertEquals(1, reading.showLatestCalls)
            assertEquals(120, scroll.firstVisibleItemScrollOffset)
        }
        composeRule.onNodeWithText(notice).assertIsDisplayed()
        composeRule.runOnIdle { reading.publish.complete(true) }
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
    }

    @Test
    fun unsuccessfulPublicationKeepsTheNoticeAndReadingPosition() {
        val reading = ReadingState()
        val scroll = render(reading)
        val label = runBlocking { getString(Res.string.home_timeline_view_latest) }

        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.runOnIdle { reading.publish.complete(false) }

        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, reading.showLatestCalls)
            assertEquals(120, scroll.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun ordinaryNoticeStillDismissesAndScrollsToTop() {
        val scroll = render()
        val label = runBlocking { getPluralString(Res.plurals.home_timeline_new_toots, 3, 3) }

        composeRule.onNodeWithText(label).performClick()

        composeRule.runOnIdle {
            assertEquals(0, scroll.firstVisibleItemIndex)
            assertEquals(0, scroll.firstVisibleItemScrollOffset)
        }
        composeRule.onNodeWithText(label).assertDoesNotExist()
    }

    private fun render(reading: ReadingState? = null): LazyStaggeredGridState {
        val scroll = LazyStaggeredGridState(initialFirstVisibleItemOffset = 120)
        val pages = MutableStateFlow(PagingData.empty<UiTimelineV2>())
        var newPostCount by mutableIntStateOf(3)
        composeRule.setContent {
            val paging = pages.collectAsLazyPagingItems().toPagingState()
            val state =
                object : TimelineWithLazyListState {
                    override val listState = paging
                    override val lazyListState = scroll
                    override val readingState = reading
                    override val newPostsCount = newPostCount
                    override val showNewToots = reading?.hasNewContent ?: (newPostCount > 0)
                    override val isRefreshing = false

                    override fun onNewTootsShown() {
                        newPostCount = 0
                    }

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() = Unit
                }
            FlareTheme(isDarkTheme = false) {
                CompositionLocalProvider(LocalTimelineAppearance provides TimelineAppearance()) {
                    Box(Modifier.size(320.dp, 240.dp)) {
                        TimelineContent(state, header = { Box(Modifier.height(2000.dp)) })
                    }
                }
            }
        }
        composeRule.runOnIdle { assertEquals(120, scroll.firstVisibleItemScrollOffset) }
        return scroll
    }

    private class ReadingState : TimelineReadingState {
        override val position: TimelineReadingPosition? = null
        override var hasNewContent by mutableStateOf(true)
        override val isRefreshing = false
        val publish = CompletableDeferred<Boolean>()
        var showLatestCalls = 0

        override suspend fun showLatest() {
            showLatestCalls++
            if (publish.await()) hasNewContent = false
        }

        override fun updateViewport(
            itemKey: String?,
            offset: Double,
            atTop: Boolean,
            interacting: Boolean,
        ) = Unit

        override fun positionRestored(requestId: Long) = Unit

        override fun savePosition() = Unit

        override fun cancelRestoration() = Unit

        override suspend fun refreshAutomatically() = Unit
    }
}
