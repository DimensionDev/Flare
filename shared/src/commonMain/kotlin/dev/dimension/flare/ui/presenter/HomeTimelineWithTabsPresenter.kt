package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

@WebPresenter("homeTimelineWithTabs")
public class HomeTimelineWithTabsPresenter :
    PresenterBase<HomeTimelineWithTabsPresenter.State>() {
    private val settingsRepository by koinInject<SettingsRepository>()
    private val accountRepository by koinInject<AccountRepository>()

    public interface State : UserState {
        public val tabState: UiState<ImmutableList<UiTimelineTabItem>>

        public fun resolveAppearance(
            tab: UiTimelineTabItem,
            base: TimelineAppearance,
        ): TimelineAppearance
    }

    private val isLoggedInFlow by lazy {
        accountRepository.allAccounts
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
    }

    private val tabsState by lazy {
        settingsRepository.homeTimelineTabs.combine(isLoggedInFlow) { tabs, isLoggedIn ->
            tabs
                .withGuestMastodonHomeFallback(isLoggedIn = isLoggedIn)
                .toImmutableList()
        }
    }

    @Composable
    override fun body(): State {
        val accountState =
            remember {
                ActiveAccountPresenter()
            }.body()

        val tabs by tabsState.collectAsUiState()

        return object : State, UserState by accountState {
            override val tabState = tabs

            override fun resolveAppearance(
                tab: UiTimelineTabItem,
                base: TimelineAppearance,
            ): TimelineAppearance = tab.resolveTimelineAppearance(base)
        }
    }
}

private const val DEFAULT_GUEST_MASTODON_HOST = "mastodon.social"

private fun List<UiTimelineTabItem>.withGuestMastodonHomeFallback(isLoggedIn: Boolean): List<UiTimelineTabItem> =
    if (!isLoggedIn && isEmpty()) {
        listOf(guestMastodonHomeTimelineTab)
    } else {
        this
    }

internal val guestMastodonHomeTimelineTab: UiTimelineTabItem
    get() =
        CommonTimelineSpecs.guestHome
            .candidate(
                data = CommonTimelineSpecs.GuestHomeData(DEFAULT_GUEST_MASTODON_HOST),
                title = UiStrings.Home.asText(),
                icon = IconType.Material(UiIcon.Home),
            ).toUiTimelineTabItem()
