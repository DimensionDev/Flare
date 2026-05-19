package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.platform.toTimelineTabDescriptor
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsState
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import org.koin.core.component.inject

public class BlueskyFeedsWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyFeedsWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            private val timelinePersistenceMapper by inject<TimelinePersistenceMapper>()

            override fun getTimelineTabItem(item: UiList): TimelineSlot =
                timelinePersistenceMapper.toSlot((item as UiList.Feed).toTimelineTabDescriptor(specificAccountKey()))
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        var isRefreshing by remember { mutableStateOf(false) }
        val state =
            remember(accountType) {
                BlueskyFeedsPresenter(accountType = accountType)
            }.invoke()
        val tabState = pinTabsPresenter.invoke()
        return object : State, BlueskyFeedsState by state, PinTabsPresenter.State<UiList> by tabState {
            override val isRefreshing: Boolean
                get() = isRefreshing

            override fun refresh() {
                isRefreshing = true
                scope.launch {
                    state.refreshSuspend()
                    isRefreshing = false
                }
            }
        }
    }

    public interface State :
        BlueskyFeedsState,
        PinTabsPresenter.State<UiList> {
        public val isRefreshing: Boolean

        public fun refresh()
    }

    private fun specificAccountKey() = (accountType as AccountType.Specific).accountKey
}
