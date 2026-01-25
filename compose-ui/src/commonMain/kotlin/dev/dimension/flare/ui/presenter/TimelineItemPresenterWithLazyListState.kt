package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

public class TimelineItemPresenterWithLazyListState(
    private val timelineTabItem: TimelineTabItem,
    private val lazyStaggeredGridState: LazyStaggeredGridState? = null,
    private val overrideFirstVisibleIndex: Int? = null,
    private val internalPresenter: TimelineItemPresenter? = null,
) : PresenterBase<TimelineItemPresenterWithLazyListState.State>() {
    @Immutable
    public interface State : TimelineItemPresenter.State {
        public val showNewToots: Boolean
        public val newPostsCount: Int
        public val lazyListState: LazyStaggeredGridState

        public fun onNewTootsShown()
    }

    private val tabItemPresenter by lazy {
        internalPresenter ?: TimelineItemPresenter(timelineTabItem)
    }

    @Composable
    override fun body(): State {
        val presenterState = tabItemPresenter.body()
        val currentPresenterState by rememberUpdatedState(presenterState)

        var showNewToots by remember { mutableStateOf(false) }
        var newPostsCount by remember { mutableStateOf(0) }
        var totalNewPostsCount by remember { mutableStateOf(0) }
        var minFirstVisibleIndex by remember { mutableStateOf(Int.MAX_VALUE) }
        var previousItemCount by remember { mutableStateOf(0) }
        val lazyListState = lazyStaggeredGridState ?: rememberLazyStaggeredGridState()

        val isAtTheTop by remember {
            derivedStateOf {
                val firstIndex = overrideFirstVisibleIndex ?: lazyListState.firstVisibleItemIndex
                firstIndex == 0 &&
                    (overrideFirstVisibleIndex == null && lazyListState.firstVisibleItemScrollOffset == 0)
            }
        }

        // Simpler approach: detect increases in itemCount. When items are prepended (itemCount grows)
        // and the user is not at the top, show the new posts indicator. This avoids reliance on item keys
        // which can be fragile in unit tests.
        LaunchedEffect(Unit) {
            snapshotFlow {
                val listState = currentPresenterState.listState
                if (listState is PagingState.Success) {
                    listState.itemCount to listState
                } else {
                    null
                }
            }.mapNotNull { it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (itemCount, listState) ->
                    val prev = previousItemCount
                    if (prev > 0 && itemCount > prev) {
                        val added = itemCount - prev
                        totalNewPostsCount += added
                        val currentFirstVisibleIndex = overrideFirstVisibleIndex ?: lazyListState.firstVisibleItemIndex
                        if (!isAtTheTop) {
                            minFirstVisibleIndex = if (minFirstVisibleIndex == Int.MAX_VALUE) {
                                currentFirstVisibleIndex + added
                            } else {
                                minFirstVisibleIndex + added
                            }
                            newPostsCount = minFirstVisibleIndex.coerceAtMost(totalNewPostsCount)
                            if (newPostsCount > 0) {
                                showNewToots = true
                            }
                        }
                    }
                    previousItemCount = itemCount
                }
        }

        LaunchedEffect(isAtTheTop) {
            if (isAtTheTop) {
                showNewToots = false
            }
        }

        LaunchedEffect(showNewToots) {
            if (!showNewToots) {
                totalNewPostsCount = 0
                newPostsCount = 0
                minFirstVisibleIndex = Int.MAX_VALUE
            }
        }

        // Decrement newPostsCount monotonically as user scrolls up to see new posts
        LaunchedEffect(Unit) {
            snapshotFlow {
                overrideFirstVisibleIndex ?: lazyListState.firstVisibleItemIndex
            }.distinctUntilChanged()
                .collect { firstVisibleIndex ->
                    if (showNewToots && totalNewPostsCount > 0 && minFirstVisibleIndex < Int.MAX_VALUE) {
                        if (firstVisibleIndex < minFirstVisibleIndex) {
                            minFirstVisibleIndex = firstVisibleIndex
                            newPostsCount = minFirstVisibleIndex.coerceAtMost(totalNewPostsCount)
                            if (newPostsCount <= 0) {
                                showNewToots = false
                            }
                        }
                    }
                }
        }
        return object : State, TimelineItemPresenter.State by presenterState {
            override val showNewToots = showNewToots
            override val newPostsCount = newPostsCount
            override val lazyListState = lazyListState

            override fun onNewTootsShown() {
                showNewToots = false
            }
        }
    }
}
