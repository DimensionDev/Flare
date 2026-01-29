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

/**
 * Public helper extracted from the presenter's logic to compute whether the "new posts"
 * indicator should show and how many posts were prepended.
 *
 * This is a pure function (no Compose runtime) so unit tests can call the exact production
 * algorithm without duplicating it.
 */
public fun computeNewPostsFromList(
    previousFirstVisibleItemKey: String?,
    previousItemCount: Int,
    currentList: List<UiTimeline>,
    isAtTheTop: Boolean,
): Triple<Boolean, Int, Set<String>> {
    // Collect the first 'count' keys from the list (currentList is a list of UiTimeline so no null checks needed).
    fun collectFirstKeys(list: List<UiTimeline>, count: Int): Set<String> {
        val limit = minOf(count, list.size)
        val added = LinkedHashSet<String>(limit)
        for (i in 0 until limit) {
            added.add(list[i].itemKey)
        }
        return added
    }

    val itemCount = currentList.size

    if (previousFirstVisibleItemKey != null) {
        val foundIndex = currentList.indexOfFirst { it.itemKey == previousFirstVisibleItemKey }
        if (foundIndex >= 0) {
            val inserted = foundIndex
            if (inserted > 0 && !isAtTheTop) {
                val added = collectFirstKeys(currentList, inserted)
                return Triple(true, inserted, added)
            }
            return Triple(false, 0, emptySet())
        }
    }

    val diff = itemCount - previousItemCount
    if (diff > 0 && !isAtTheTop) {
        val added = collectFirstKeys(currentList, diff)
        return Triple(true, diff, added)
    }

    return Triple(false, 0, emptySet())
}

public class TimelineItemPresenterWithLazyListState(
    private val timelineTabItem: TimelineTabItem,
    private val lazyStaggeredGridState: LazyStaggeredGridState? = null,
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
        val state = tabItemPresenter.body()
        var showNewToots by remember { mutableStateOf(false) }
        val currentState by rememberUpdatedState(state)

        var newPostsCount by remember { mutableStateOf(0) }
        var totalNewPostsCount by remember { mutableStateOf(0) }
        // index of the first-visible item at the time the list was last refreshed
        var lastRefreshIndex by remember { mutableStateOf<Int?>(null) }
        var previousFirstVisibleItemKey by remember { mutableStateOf<String?>(null) }
        val lazyListState = lazyStaggeredGridState ?: rememberLazyStaggeredGridState()
        // Keep a remembered baseline item count for detecting prepends (must be at composable scope).
        // Use -1 as sentinel for "not initialized" so we don't need a separate 'initialized' flag.
        val previousItemCountRef = remember { intArrayOf(-1) }

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

        // Track the key of the item currently at the top of the viewport so we can locate it after a refresh.
        LaunchedEffect(Unit) {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    val listState = currentState.listState
                    if (listState is PagingState.Success) {
                        val item = listState.peek(index)
                        previousFirstVisibleItemKey = item?.itemKey ?: previousFirstVisibleItemKey
                    }
                }
        }

        // Detect changes to the timeline list and compute how many items were prepended.
        LaunchedEffect(Unit) {
            snapshotFlow {
                val listState = currentState.listState
                if (listState is PagingState.Success) {
                    Triple(listState.peek(0)?.itemKey, listState.itemCount, listState)
                } else {
                    null
                }
            }.mapNotNull { it }
                .collect { (_, itemCount, listState) ->
                    if (previousItemCountRef[0] == -1) {
                        // first emission: capture baseline item count and optional override first-visible key
                        previousItemCountRef[0] = itemCount
                        return@collect
                    }

                    val lastReadKey = previousFirstVisibleItemKey

                    // Build a concrete list snapshot of UiTimeline items from the paging list to feed the helper
                    val currentList = mutableListOf<UiTimeline>()
                    for (i in 0 until listState.itemCount) {
                        val item = listState.peek(i)
                        if (item is UiTimeline) currentList.add(item)
                    }

                    // Use the production helper to compute inserted/new keys
                    val (showIndicator, inserted, _) =
                        computeNewPostsFromList(
                            lastReadKey,
                            previousItemCountRef[0],
                            currentList,
                            isAtTheTop,
                        )

                    if (showIndicator) {
                        if (inserted > 0 && !isAtTheTop) {
                            // Record the number of new items and the index baseline (the UI index before it moved).
                            totalNewPostsCount = inserted
                            newPostsCount = inserted
                            lastRefreshIndex = lazyListState.firstVisibleItemIndex
                            showNewToots = true
                        } else {
                            // full refresh or at top -> clear
                            totalNewPostsCount = 0
                            newPostsCount = 0
                            lastRefreshIndex = null
                            showNewToots = false
                        }
                    } else {
                        // No indicator -> clear preparatory state (mirrors previous behavior)
                        totalNewPostsCount = 0
                        newPostsCount = 0
                        lastRefreshIndex = null
                    }

                    previousItemCountRef[0] = itemCount
                }
        }

        // If the user is at the top, record the current top item's key as the last-read key.
        LaunchedEffect(isAtTheTop) {
            if (isAtTheTop) {
                val listState = currentState.listState
                if (listState is PagingState.Success) {
                    val top = listState.peek(0)
                    previousFirstVisibleItemKey = top?.itemKey ?: previousFirstVisibleItemKey
                }
            }
        }

        LaunchedEffect(showNewToots) {
            if (!showNewToots) {
                totalNewPostsCount = 0
                newPostsCount = 0
                lastRefreshIndex = null
            }
        }

        // Simplified: recompute newPostsCount from the current firstVisibleIndex and the lastRefreshIndex
        LaunchedEffect(Unit) {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisibleIndex ->
                    if (!showNewToots) {
                        newPostsCount = 0
                        return@collect
                    }

                    val lr = lastRefreshIndex
                    if (lr == null) return@collect

                    if (firstVisibleIndex > lr) {
                        val calc = firstVisibleIndex - lr
                        // If we already showed a count, only decrease it (never increase). Also don't exceed totalNewPostsCount.
                        newPostsCount = if (newPostsCount > 0) {
                            minOf(newPostsCount, minOf(calc, totalNewPostsCount))
                        } else {
                            minOf(calc, totalNewPostsCount)
                        }
                        if (newPostsCount <= 0) showNewToots = false
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
