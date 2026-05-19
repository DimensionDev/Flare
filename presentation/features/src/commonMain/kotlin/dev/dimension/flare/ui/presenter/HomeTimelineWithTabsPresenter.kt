package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.homeTimelineTabs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.AccountTimelinePresenter
import dev.dimension.flare.ui.presenter.home.UserState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTimelineWithTabsPresenter :
    PresenterBase<HomeTimelineWithTabsPresenter.State>(),
    KoinComponent {
    private val appDataStore by inject<AppDataStore>()
    private val accountRepository by inject<AccountRepository>()
    private val timelineResolver by inject<TimelineResolver>()

    public interface State : UserState {
        public val tabState: UiState<ImmutableList<TimelineTabItemV2>>
    }

    private val isLoggedInFlow by lazy {
        accountRepository.allAccounts
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
    }

    private val tabsState by lazy {
        appDataStore.homeTimelineTabs(timelineResolver).combine(isLoggedInFlow) { tabs, isLoggedIn ->
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
        }
    }
}

private const val DEFAULT_GUEST_MASTODON_HOST = "mastodon.social"

private fun List<TimelineTabItemV2>.withGuestMastodonHomeFallback(isLoggedIn: Boolean): List<TimelineTabItemV2> =
    if (!isLoggedIn && isEmpty()) {
        listOf(guestMastodonHomeTimelineTab)
    } else {
        this
    }

internal val guestMastodonHomeTimelineTab: TimelineTabItemV2
    get() =
        SourceTimelineTabItemV2.runtime(
            id = "guest_home_$DEFAULT_GUEST_MASTODON_HOST",
            title = UiStrings.Home.asText(),
            icon = IconType.Material(UiIcon.Home),
            runtimePresenterFactory = {
                AccountTimelinePresenter(AccountType.GuestHost(DEFAULT_GUEST_MASTODON_HOST)) { service ->
                    service.homeTimeline()
                }
            },
        )
