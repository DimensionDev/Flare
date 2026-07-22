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
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import moe.tlaster.precompose.molecule.producePresenter

@Immutable
public interface TimelineWithLazyListState : TimelineItemPresenter.State {
    public val showNewToots: Boolean
    public val lazyListState: LazyStaggeredGridState
    public val newPostsCount: Int

    public fun onNewTootsShown()
}

/**
 * UI-side composable that exposes the timeline paging state plus scroll-bound indicator state
 * (new-toots banner, scroll-to-top etc.) bound to the supplied [lazyStaggeredGridState].
 *
 * The paging/refresh portion runs inside a molecule presenter scoped to a `ViewModel`
 * (so it survives configuration changes), while the lazyListState-dependent effects run in
 * plain Composition. This avoids capturing a stale `LazyStaggeredGridState` across Activity
 * recreation — every fresh Composition rebinds its own [lazyStaggeredGridState] to the effects.
 */
@Composable
public fun rememberTimelineItemPresenterWithLazyListState(
    item: UiTimelineTabItem,
    isHomeTimeline: Boolean = false,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
): TimelineWithLazyListState {
    val baseState by producePresenter("timeline_${item.id}_$isHomeTimeline") {
        remember(item, isHomeTimeline) { TimelineItemPresenter(item, isHomeTimeline) }.invoke()
    }
    return rememberTimelineWithLazyListState(baseState, lazyStaggeredGridState)
}

@Composable
private fun rememberTimelineWithLazyListState(
    baseState: TimelineItemPresenter.State,
    lazyListState: LazyStaggeredGridState,
): TimelineWithLazyListState {
    var showNewToots by remember { mutableStateOf(false) }
    var lastRefreshIndex by remember { mutableStateOf(0) }
    var newPostCount by remember { mutableStateOf(0) }
    baseState.listState.onSuccess {
        LaunchedEffect(lazyListState) {
            snapshotFlow {
                if (itemCount > 0) {
                    peek(0)?.itemKey
                } else {
                    null
                }
            }.mapNotNull { it }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    showNewToots = true
                    lastRefreshIndex = lazyListState.firstVisibleItemIndex
                }
        }
    }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                if (it > lastRefreshIndex && showNewToots) {
                    newPostCount =
                        if (newPostCount > 0) {
                            minOf(newPostCount, it - lastRefreshIndex)
                        } else {
                            it - lastRefreshIndex
                        }
                }
            }
    }
    val isAtTheTop by remember(lazyListState) {
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
    LaunchedEffect(showNewToots) {
        if (!showNewToots) {
            newPostCount = 0
        }
    }
    return object :
        TimelineWithLazyListState,
        TimelineItemPresenter.State by baseState {
        override val showNewToots = showNewToots
        override val lazyListState = lazyListState
        override val newPostsCount = newPostCount

        override fun onNewTootsShown() {
            showNewToots = false
        }
    }
}
