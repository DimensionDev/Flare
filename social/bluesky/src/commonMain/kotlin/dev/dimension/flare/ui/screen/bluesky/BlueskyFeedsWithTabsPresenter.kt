package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsPresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedsState
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch

public class BlueskyFeedsWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyFeedsWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            override fun getTimelineTabItem(item: UiList): UiTimelineTabItem =
                BlueskyPlatformSpec.feedTimelineSpec
                    .candidate(
                        data = TimelineSpec.AccountResourceData(specificAccountKey(), item.id),
                        title = UiText.Raw(item.title),
                        icon =
                            (item as? UiList.Feed)?.avatar?.let {
                                IconType.Url(it)
                            } ?: IconType.Material(UiIcon.Feeds),
                    ).toUiTimelineTabItem()
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
