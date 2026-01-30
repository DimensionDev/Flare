package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
 * public helper to compute whether the "new posts" indicator should show
 * and how many posts were prepended
 */
public fun computeNewPostsFromList(
    previousFirstVisibleItemKey: String?,
    previousItemCount: Int,
    currentList: List<UiTimeline>,
    isAtTheTop: Boolean,
): Triple<Boolean, Int, Set<String>> {
    // Collect the first 'count' keys from the list (currentList is a list of UiTimeline so no null checks needed).
    fun collectFirstKeys(
        list: List<UiTimeline>,
        count: Int,
    ): Set<String> {
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
        if (foundIndex == 0) {
            return Triple(false, 0, emptySet())
        } else if (foundIndex > 0 && !isAtTheTop) {
            val added = collectFirstKeys(currentList, foundIndex)
            return Triple(true, foundIndex, added)
        }
    }

    val diff = itemCount - previousItemCount
    if (diff > 0 && !isAtTheTop) {
        val added = collectFirstKeys(currentList, diff)
        return Triple(true, diff, added)
    }

    return Triple(false, 0, emptySet())
}

/**
 * Determine a "last refresh" index from an observed first-visible index and inserted items count.
 * If the UI has already jumped to the top (observedFirstVisibleIndex == lastViewedIndex + insertedPostCount),
 * subtracting `insertedPostCount` recovers the previous last-viewed index.
 * If that would be negative, fall back to observedFirstVisibleIndex.
 */
public fun deriveLastRefreshIndex(
    observedFirstVisibleIndex: Int,
    insertedPostCount: Int,
): Int {
    val candidate = observedFirstVisibleIndex - insertedPostCount
    return if (candidate >= 0) candidate else observedFirstVisibleIndex
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

        // Keep a remembered baseline item count for detecting prepends (must be at composable scope).
        // Use -1 as sentinel for "not initialized" so we don't need a separate 'initialized' flag.
        val previousItemCountRef = remember { intArrayOf(-1) }

        val lazyListState = lazyStaggeredGridState ?: rememberLazyStaggeredGridState()

        // Consolidated watcher: single LaunchedEffect observes list snapshots and viewport position
        LaunchedEffect(Unit) {
            data class Snapshot(
                val topKey: String?,
                val itemCount: Int,
                val firstVisibleIndex: Int,
                val firstVisibleOffset: Int,
                val listState: PagingState.Success<UiTimeline>,
            )

            snapshotFlow {
                val listState = currentState.listState
                if (listState is PagingState.Success) {
                    Snapshot(
                        topKey = listState.peek(0)?.itemKey,
                        itemCount = listState.itemCount,
                        firstVisibleIndex = lazyListState.firstVisibleItemIndex,
                        firstVisibleOffset = lazyListState.firstVisibleItemScrollOffset,
                        listState = listState,
                    )
                } else {
                    null
                }
            }.mapNotNull { it }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val atTop = snapshot.firstVisibleIndex == 0 && snapshot.firstVisibleOffset == 0

                    // initialize baseline on first meaningful emission
                    if (previousItemCountRef[0] == -1) {
                        previousItemCountRef[0] = snapshot.itemCount
                        val currentIndex = snapshot.firstVisibleIndex.takeIf { it >= 0 } ?: 0
                        val topKey = snapshot.listState.peek(currentIndex)?.itemKey ?: snapshot.topKey
                        previousFirstVisibleItemKey = topKey ?: previousFirstVisibleItemKey
                        // ensure indicator cleared on initial seed
                        showNewToots = false
                        newPostsCount = 0
                        totalNewPostsCount = 0
                        lastRefreshIndex = null
                        return@collect
                    }

                    // update the top-key anchor based on current viewport
                    val currentTopItem = snapshot.listState.peek(snapshot.firstVisibleIndex)
                    previousFirstVisibleItemKey = currentTopItem?.itemKey ?: previousFirstVisibleItemKey

                    // Build a concrete list snapshot of UiTimeline items from the paging list to feed compute helper
                    val currentList = mutableListOf<UiTimeline>()
                    for (i in 0 until snapshot.itemCount) {
                        val item = snapshot.listState.peek(i)
                        if (item is UiTimeline) currentList.add(item)
                    }

                    // compute whether we should show the indicator and how many items were inserted
                    val (showIndicator, insertedPostCount, _) = computeNewPostsFromList(
                        previousFirstVisibleItemKey,
                        previousItemCountRef[0],
                        currentList,
                        atTop,
                    )

                    if (showIndicator) {
                        if (insertedPostCount > 0 && !atTop) {
                            totalNewPostsCount = insertedPostCount
                            newPostsCount = insertedPostCount
                            lastRefreshIndex = deriveLastRefreshIndex(snapshot.firstVisibleIndex, insertedPostCount)
                            showNewToots = true
                        } else {
                            // full refresh or at top -> clear
                            totalNewPostsCount = 0
                            newPostsCount = 0
                            lastRefreshIndex = null
                            showNewToots = false
                        }
                    } else {
                        totalNewPostsCount = 0
                        newPostsCount = 0
                        lastRefreshIndex = null
                    }

                    // If indicator is showing, update remaining count as the user scrolls into new items
                    if (showNewToots) {
                        val lr = lastRefreshIndex
                        if (lr != null && snapshot.firstVisibleIndex > lr) {
                            val calc = snapshot.firstVisibleIndex - lr
                            newPostsCount = if (newPostsCount > 0) {
                                minOf(newPostsCount, minOf(calc, totalNewPostsCount))
                            } else {
                                minOf(calc, totalNewPostsCount)
                            }
                            if (newPostsCount <= 0) showNewToots = false
                        }
                    }

                    // When at top, clear indicator and set baseline top key
                    if (atTop) {
                        showNewToots = false
                        totalNewPostsCount = 0
                        newPostsCount = 0
                        lastRefreshIndex = null
                        previousFirstVisibleItemKey = snapshot.topKey ?: previousFirstVisibleItemKey
                    }

                    previousItemCountRef[0] = snapshot.itemCount
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
