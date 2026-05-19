package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class TimelineItemPresenter(
    private val timelineTabItem: TimelineTabItemV2,
) : PresenterBase<TimelineItemPresenter.State>(),
    KoinComponent {
    public interface State {
        public val listState: PagingState<UiTimelineV2>

        public fun refreshSync()

        public suspend fun refreshSuspend()

        public val isRefreshing: Boolean
    }

    private val timelineResolver: TimelineResolver by inject()

    private val timelinePresenter by lazy {
        timelineResolver.createPresenter(timelineTabItem)
    }

    @Composable
    override fun body(): State {
        val state = timelinePresenter.body()
        val scope = rememberCoroutineScope()
        return object : State {
            override val listState = state.listState
            override val isRefreshing = listState.isRefreshing

            override fun refreshSync() {
                scope.launch {
                    state.refresh()
                }
            }

            override suspend fun refreshSuspend() {
                state.refresh()
            }
        }
    }
}
