package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
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
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.TimelineWithLazyListState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelineNewContentNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyReadingTimelineShowsNoticeUntilLatestIsPublished() = checkPublication(success = true)

    @Test
    fun failedPublicationKeepsTheEmptyTimelineNotice() = checkPublication(success = false)

    @Test
    fun emptyOrdinaryTimelineDoesNotShowAStaleNotice() {
        render()

        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.home_timeline_new_toots, 3, 3)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.home_timeline_view_latest)).assertDoesNotExist()
    }

    private fun checkPublication(success: Boolean) {
        val reading = ReadingState()
        render(reading)
        val label = context.getString(R.string.home_timeline_view_latest)
        val notice = context.getString(R.string.home_timeline_new_content)
        composeRule.onNodeWithText(notice).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(label).performClick()

        composeRule.runOnIdle { assertEquals(1, reading.showLatestCalls) }
        composeRule.onNodeWithText(notice).assertIsDisplayed()
        composeRule.runOnIdle { reading.publish.complete(success) }
        if (success) {
            composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
        } else {
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    private fun render(reading: ReadingState? = null) {
        val pages =
            MutableStateFlow(
                PagingData.empty<UiTimelineV2>(
                    sourceLoadStates =
                        LoadStates(
                            refresh = LoadState.NotLoading(false),
                            prepend = LoadState.NotLoading(true),
                            append = LoadState.NotLoading(true),
                        ),
                ),
            )
        val scroll = LazyStaggeredGridState()
        var newPostCount by mutableIntStateOf(3)
        lateinit var paging: PagingState<UiTimelineV2>
        composeRule.setContent {
            paging = pages.collectAsLazyPagingItems().toPagingState()
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
            MaterialTheme {
                Box(Modifier.size(320.dp, 240.dp)) {
                    TimelineNewContentNotice(state)
                }
            }
        }
        composeRule.runOnIdle { assertTrue(paging is PagingState.Empty) }
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
