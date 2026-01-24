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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.TimelineTabItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull

public class TimelineItemPresenterWithLazyListState(
    private val timelineTabItem: TimelineTabItem,
    private val lazyStaggeredGridState: LazyStaggeredGridState? = null,
) : PresenterBase<TimelineItemPresenterWithLazyListState.State>() {
    @Immutable
    public interface State : TimelineItemPresenter.State {
        public val showNewToots: Boolean
        public val newPostsCount: Int
        public val lazyListState: LazyStaggeredGridState

        public fun onNewTootsShown()
    }

    private val tabItemPresenter by lazy {
        TimelineItemPresenter(timelineTabItem)
    }

    @Composable
    override fun body(): State {
        val state = tabItemPresenter.body()
        var showNewToots by remember { mutableStateOf(false) }
        var newPostsCount by remember { mutableStateOf(0) }
        var totalNewPostsCount by remember { mutableStateOf(0) }
        var minFirstVisibleIndex by remember { mutableStateOf(Int.MAX_VALUE) }
        var previousFirstItemKey by remember { mutableStateOf<String?>(null) }
        val lazyListState = lazyStaggeredGridState ?: rememberLazyStaggeredGridState()
        state.listState.onSuccess {
            LaunchedEffect(Unit) {
                snapshotFlow {
                    if (itemCount > 0) {
                        peek(0)?.itemKey
                    } else {
                        null
                    }
                }.mapNotNull { it }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { newFirstItemKey ->
                        // Find the position of the previous first item
                        val previousItemKey = previousFirstItemKey
                        if (previousItemKey != null) {
                            // Search for where the previous first item is now
                            var count = 0
                            for (i in 0 until itemCount) {
                                val item = peek(i)
                                if (item?.itemKey == previousItemKey) {
                                    count = i
                                    break
                                }
                            }
                            if (count > 0) {
                                println("[DEBUG] New posts detected: count=$count, firstVisibleItemIndex=${lazyListState.firstVisibleItemIndex}")
                                totalNewPostsCount = count
                                newPostsCount = count
                                minFirstVisibleIndex = maxOf(lazyListState.firstVisibleItemIndex, count)
                                println("[DEBUG] After init: totalNewPostsCount=$totalNewPostsCount, newPostsCount=$newPostsCount, minFirstVisibleIndex=$minFirstVisibleIndex")
                            }
                        }
                        previousFirstItemKey = newFirstItemKey
                        showNewToots = true
                    }
            }
        }
        val isAtTheTop by remember {
            derivedStateOf {
                lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
            }
        }
        LaunchedEffect(isAtTheTop, showNewToots) {
            if (isAtTheTop) {
                showNewToots = false
            }
        }
        // Decrement newPostsCount monotonically as user scrolls up to see new posts
        LaunchedEffect(Unit) {
            snapshotFlow {
                lazyListState.firstVisibleItemIndex
            }.distinctUntilChanged()
                .collect { firstVisibleIndex ->
                    if (showNewToots && totalNewPostsCount > 0 && minFirstVisibleIndex < Int.MAX_VALUE) {
                        minFirstVisibleIndex = minOf(minFirstVisibleIndex, firstVisibleIndex)
                        newPostsCount = minFirstVisibleIndex.coerceAtMost(totalNewPostsCount)
                        println("[DEBUG] Scroll: firstVisibleIndex=$firstVisibleIndex, minFirstVisibleIndex=$minFirstVisibleIndex, totalNewPostsCount=$totalNewPostsCount, newPostsCount=$newPostsCount")
                    }
                }
        }
        return object : State, TimelineItemPresenter.State by state {
            override val showNewToots = showNewToots
            override val newPostsCount = newPostsCount
            override val lazyListState = lazyListState

            override fun onNewTootsShown() {
                showNewToots = false
            }
        }
    }
}
