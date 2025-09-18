package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import kotlinx.coroutines.launch

public class TimelineItemPresenter(
    private val timelineTabItem: TimelineTabItem,
) : PresenterBase<TimelineItemPresenter.State>() {
    public interface State {
        public val listState: PagingState<UiTimeline>

        public fun refreshSync()

        public suspend fun refreshSuspend()

        public val isRefreshing: Boolean
    }

    private val timelinePresenter by lazy {
        timelineTabItem.createPresenter()
    }

    private val badgePresenter by lazy {
        NotificationBadgePresenter(timelineTabItem.account)
    }

    @Composable
    override fun body(): State {
        val state = timelinePresenter.body()
        val badge = badgePresenter.body()
        val scope = rememberCoroutineScope()
        return object : State {
            override val listState = state.listState
            override val isRefreshing = listState.isRefreshing

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
