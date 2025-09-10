package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

public class TimelineItemPresenter(
    private val timelineTabItem: TimelineTabItem,
) : PresenterBase<TimelineItemPresenter.State>() {
    public interface State {
        public val listState: PagingState<UiTimeline>
        public val showNewToots: Boolean
        public val isRefreshing: Boolean
        public val lazyListState: LazyStaggeredGridState
        public val timelineTabItem: TimelineTabItem

        public fun onNewTootsShown()

        public fun refreshSync()

        public suspend fun refreshSuspend()
    }

    @Composable
    override fun body(): State {
        val state =
            remember(timelineTabItem.key) {
                timelineTabItem.createPresenter()
            }.body()
        val badge =
            remember(timelineTabItem) {
                NotificationBadgePresenter(timelineTabItem.account)
            }.body()
        val scope = rememberCoroutineScope()
        var showNewToots by remember { mutableStateOf(false) }
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
                    .collect {
                        showNewToots = true
                    }
            }
        }
        val lazyListState = rememberLazyStaggeredGridState()
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
        return object : State {
            override val listState = state.listState
            override val showNewToots = showNewToots
            override val isRefreshing = state.listState.isRefreshing
            override val lazyListState = lazyListState
            override val timelineTabItem = this@TimelineItemPresenter.timelineTabItem

            override fun onNewTootsShown() {
                showNewToots = false
            }

            override fun refreshSync() {
                scope.launch {
                    state.refresh()
                }
                badge.refresh()
            }

            override suspend fun refreshSuspend() {
                state.refresh()
                badge.refresh()
            }
        }
    }
}
