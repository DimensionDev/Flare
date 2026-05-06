package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.UiTimelineItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class TimelineItemPresenter(
    private val timelineTabItem: UiTimelineItem,
) : PresenterBase<TimelineItemPresenter.State>(),
    KoinComponent {
    public interface State {
        public val listState: PagingState<UiTimelineV2>

        public fun refreshSync()

        public suspend fun refreshSuspend()

        public val isRefreshing: Boolean
    }

    private val timelinePresenter by lazy {
        timelineTabItem.createPresenter.invoke()
    }

//    private val badgePresenter by lazy {
//        val accountKey = resolver.resolveAccountKey(timelineTabItem)
//        if (accountKey != null) {
//            NotificationBadgePresenter(AccountType.Specific(accountKey))
//        } else {
//            null
//        }
//    }

    @Composable
    override fun body(): State {
        val state = timelinePresenter.body()
//        val badge = badgePresenter?.body()
        val scope = rememberCoroutineScope()
        return object : State {
            override val listState = state.listState
            override val isRefreshing = listState.isRefreshing

            override fun refreshSync() {
                scope.launch {
                    state.refresh()
                }
                badge?.refresh()
            }

            override suspend fun refreshSuspend() {
                state.refresh()
                badge?.refresh()
            }
        }
    }
}
