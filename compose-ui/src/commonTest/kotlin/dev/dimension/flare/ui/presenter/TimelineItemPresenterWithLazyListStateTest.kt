package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineItemPresenterWithLazyListStateTest {

    class FakeTimelineItemPresenter : TimelineItemPresenter(
        dev.dimension.flare.data.model.HomeTimelineTabItem(dev.dimension.flare.model.AccountType.Active)
    ) {
        var pagingState by mutableStateOf<PagingState<UiTimeline>>(PagingState.Loading())

        @Composable
        override fun body(): State {
            return object : State {
                override val listState = pagingState
                override val isRefreshing = false
                override fun refreshSync() {}
                override suspend fun refreshSuspend() {}
            }
        }
    }

    private fun createTimeline(key: String): UiTimeline {
        return UiTimeline(null, null, key)
    }

    private fun createSuccessState(items: List<UiTimeline>): PagingState.Success<UiTimeline> {
        return PagingState.Success.ImmutableSuccess(persistentListOf<UiTimeline>().addAll(items))
    }

    @Test
    fun testIndicatorAppearsWhenNewPostsArrive() = runTest {
        val tabItem = dev.dimension.flare.data.model.HomeTimelineTabItem(dev.dimension.flare.model.AccountType.Active)

        val lazyListState = LazyStaggeredGridState()

        val fakePresenter = FakeTimelineItemPresenter()

        val initialItems = (10 downTo 1).map { createTimeline("item_$it") }
        fakePresenter.pagingState = createSuccessState(initialItems)

        // Simulate being scrolled away from top by overriding first visible index
        val presenter = TimelineItemPresenterWithLazyListState(tabItem, lazyListState, overrideFirstVisibleIndex = 5, internalPresenter = fakePresenter)

        val states = mutableListOf<TimelineItemPresenterWithLazyListState.State>()
        val job = launch {
            moleculeFlow(mode = RecompositionMode.Immediate) {
                presenter.body()
            }.collect {
                states.add(it)
            }
        }

        // allow composition to settle
        var i = 0
        while (states.isEmpty() && i++ < 200) {
            advanceUntilIdle()
        }

        // Initially at index 5 (not at top). Indicator should be hidden initially.
        assertFalse(states.last().showNewToots, "Indicator should be hidden initially")

        // New posts arrive
        val newItems = (15 downTo 11).map { createTimeline("item_$it") } + initialItems
        fakePresenter.pagingState = createSuccessState(newItems)

        // Wait for expected state
        i = 0
        while ((states.lastOrNull()?.showNewToots != true) && i++ < 200) {
            advanceUntilIdle()
        }

        // Indicator should now be shown
        assertTrue(states.last().showNewToots, "Indicator should be shown after new posts arrive when NOT at top")
        assertEquals(5, states.last().newPostsCount, "New posts count should be 5")

        job.cancel()
    }

    @Test
    fun testRefreshReportsCorrectNewPostsCount() = runTest {
        val tabItem = dev.dimension.flare.data.model.HomeTimelineTabItem(dev.dimension.flare.model.AccountType.Active)

        val lazyListState = LazyStaggeredGridState()
        val fakePresenter = FakeTimelineItemPresenter()

        // Start with 8 items
        val initialItems = (8 downTo 1).map { createTimeline("item_$it") }
        fakePresenter.pagingState = createSuccessState(initialItems)

        // Simulate being scrolled away from top
        val presenter = TimelineItemPresenterWithLazyListState(tabItem, lazyListState, overrideFirstVisibleIndex = 3, internalPresenter = fakePresenter)

        val states = mutableListOf<TimelineItemPresenterWithLazyListState.State>()
        val job = launch {
            moleculeFlow(mode = RecompositionMode.Immediate) {
                presenter.body()
            }.collect {
                states.add(it)
            }
        }

        // allow composition to settle
        var i = 0
        while (states.isEmpty() && i++ < 200) {
            advanceUntilIdle()
        }

        // Simulate refresh that prepends 4 new items
        val refreshedItems = (12 downTo 9).map { createTimeline("item_$it") } + initialItems
        fakePresenter.pagingState = createSuccessState(refreshedItems)

        // Wait for expected state
        i = 0
        while ((states.lastOrNull()?.showNewToots != true) && i++ < 200) {
            advanceUntilIdle()
        }

        // After refresh, indicator should show and the count should equal 4
        assertTrue(states.last().showNewToots, "Indicator should be shown after refresh when NOT at top")
        assertEquals(4, states.last().newPostsCount, "After refresh newPostsCount should equal number of prepended items")

        job.cancel()
    }
}
