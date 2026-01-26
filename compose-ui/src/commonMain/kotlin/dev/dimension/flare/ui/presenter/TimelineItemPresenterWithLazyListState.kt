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
        val state = tabItemPresenter.body()
        var showNewToots by remember { mutableStateOf(false) }
        val currentState by rememberUpdatedState(state)

        // Helper to collect keys for the first 'count' items of a PagingState.Success list.
        // Use a star projection because PagingState.Success is generic.
        fun collectFirstKeys(listState: PagingState.Success<*>, count: Int): Set<String> {
            val added = mutableSetOf<String>()
            for (i in 0 until minOf(count, listState.itemCount)) {
                val item = listState.peek(i)
                if (item is UiTimeline) added.add(item.itemKey)
            }
            return added
        }

        var newPostsCount by remember { mutableStateOf(0) }
        var totalNewPostsCount by remember { mutableStateOf(0) }
        // track exact keys of items that were newly prepended
        var newItemKeys by remember { mutableStateOf(setOf<String>()) }
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

        // Track the key of the item currently at the top of the viewport so we can locate it after a refresh.
        LaunchedEffect(Unit) {
            snapshotFlow { overrideFirstVisibleIndex ?: lazyListState.firstVisibleItemIndex }
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
                } else null
            }.mapNotNull { it }
                .collect { (_, itemCount, listState) ->
                    if (previousItemCountRef[0] == -1) {
                        // first emission: capture baseline item count and optional override first-visible key
                        previousItemCountRef[0] = itemCount
                        if (overrideFirstVisibleIndex != null && listState.itemCount > overrideFirstVisibleIndex) {
                            val item = listState.peek(overrideFirstVisibleIndex)
                            previousFirstVisibleItemKey = item?.itemKey ?: previousFirstVisibleItemKey
                        }
                        return@collect
                    }

                    val lastReadKey = previousFirstVisibleItemKey

                    if (lastReadKey != null) {
                        // If the last-read key is in the new page, the number of newly prepended items
                        // is the number of items before that key (its index).
                        var foundIndex = -1
                        for (i in 0 until listState.itemCount) {
                            val item = listState.peek(i)
                            if (item is UiTimeline && item.itemKey == lastReadKey) {
                                foundIndex = i
                                break
                            }
                        }

                        if (foundIndex >= 0) {
                            val inserted = foundIndex
                            if (inserted > 0 && !isAtTheTop) {
                                // collect keys for new items
                                val added = collectFirstKeys(listState, inserted)
                                if (added.isNotEmpty()) newItemKeys = newItemKeys + added
                                totalNewPostsCount = inserted
                                newPostsCount = inserted
                                showNewToots = true
                            } else {
                                // full refresh or at top -> clear
                                totalNewPostsCount = 0
                                newPostsCount = 0
                                newItemKeys = emptySet()
                                showNewToots = false
                            }
                        }
                    } else {
                        // fallback: use item count diff
                        val diff = itemCount - previousItemCountRef[0]
                        if (diff > 0 && !isAtTheTop) {
                            val inserted = diff
                            val added = collectFirstKeys(listState, inserted)
                            if (added.isNotEmpty()) newItemKeys = newItemKeys + added
                            totalNewPostsCount = inserted
                            newPostsCount = inserted
                            showNewToots = true
                        }
                    }

                    previousItemCountRef[0] = itemCount
                }
            }

        LaunchedEffect(isAtTheTop) {
            if (isAtTheTop) {
                showNewToots = false
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
                newItemKeys = emptySet()
            }
        }

        // Recompute newPostsCount based on current firstVisibleIndex as the user scrolls.
        LaunchedEffect(Unit) {
            snapshotFlow {
                Pair(overrideFirstVisibleIndex ?: lazyListState.firstVisibleItemIndex, currentState.listState)
            }.distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (firstVisibleIndex, listStateAny) ->
                    val listState = listStateAny
                    if (showNewToots && totalNewPostsCount > 0 && listState is PagingState.Success) {
                        val keysPresent = newItemKeys.isNotEmpty()
                        var keysAbove = 0
                        if (keysPresent) {
                            for (i in 0 until minOf(firstVisibleIndex, listState.itemCount)) {
                                val item = listState.peek(i)
                                if (item is UiTimeline && newItemKeys.contains(item.itemKey)) keysAbove++
                            }
                        }

                        val unseen = if (keysPresent) {
                            // We may have partial knowledge of new items (some keys missing). Count known keys above
                            // and conservatively add any missing unknowns that could be above the viewport.
                            val missing = maxOf(0, totalNewPostsCount - newItemKeys.size)
                            val maxMissingAbove = maxOf(0, firstVisibleIndex - keysAbove)
                            keysAbove + minOf(missing, maxMissingAbove)
                        } else {
                            // No keys known; conservative estimate using totalNewPostsCount and viewport
                            minOf(totalNewPostsCount, firstVisibleIndex)
                        }

                        newPostsCount = unseen
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
