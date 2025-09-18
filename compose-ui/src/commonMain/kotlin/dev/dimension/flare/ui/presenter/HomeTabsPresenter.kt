package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.activeAccountFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.HomeTabsPresenter.State.HomeTabState.HomeTabItem
import dev.dimension.flare.ui.presenter.home.DirectMessageBadgePresenter
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTabsPresenter :
    PresenterBase<HomeTabsPresenter.State>(),
    KoinComponent {
    public interface State {
        public val tabs: UiState<HomeTabState>

        @Immutable
        public data class HomeTabState(
            val primary: ImmutableList<HomeTabItem>,
            val secondary: ImmutableList<HomeTabItem>,
            val extraProfileRoute: HomeTabItem?,
            val secondaryIconOnly: Boolean = false,
        ) {
            val all: ImmutableList<HomeTabItem>
                get() =
                    (primary + secondary + extraProfileRoute)
                        .filterNotNull()
                        .distinctBy { it.tabItem.key }
                        .toImmutableList()

            @Immutable
            public data class HomeTabItem(
                val tabItem: TabItem,
                val badgeCountState: UiState<Int> = UiState.Success(0),
            )
        }
    }

    private val settingsRepository by inject<SettingsRepository>()
    private val accountRepository by inject<AccountRepository>()
    private val activeAccountFlow by lazy {
        combine(
            activeAccountFlow(accountRepository),
            settingsRepository.tabSettings.distinctUntilChangedBy { it.secondaryItems },
        ) { account, tabsState ->
            println("Active account changed: $account, tabsState: $tabsState")
            val secondary =
                tabsState.secondaryItems ?: TimelineTabItem.defaultSecondary(account)
            State.HomeTabState(
                primary =
                    TimelineTabItem.default
                        .map {
                            HomeTabItem(it)
                        }.toImmutableList(),
                secondary =
                    secondary
                        .map {
                            HomeTabItem(it)
                        }.toImmutableList(),
                extraProfileRoute =
                    HomeTabItem(
                        tabItem =
                            ProfileTabItem(
                                accountKey = account.accountKey,
                                userKey = account.accountKey,
                            ),
                    ),
                secondaryIconOnly = tabsState.secondaryItems == null,
            )
        }.catch {
            emit(
                State.HomeTabState(
                    primary =
                        TimelineTabItem.guest
                            .map {
                                HomeTabItem(it)
                            }.toImmutableList(),
                    secondary = persistentListOf(),
                    extraProfileRoute = null,
                    secondaryIconOnly = true,
                ),
            )
        }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val tabs =
            remember(activeAccountFlow) {
                activeAccountFlow
            }.collectAsUiState().value.map {
                it.copy(
                    primary =
                        it.primary
                            .map { item ->
                                when (item.tabItem) {
                                    is NotificationTabItem ->
                                        item.copy(
                                            badgeCountState =
                                                notificationBadgePresenter(
                                                    item.tabItem.account,
                                                ),
                                        )

                                    is DirectMessageTabItem ->
                                        item.copy(
                                            badgeCountState =
                                                directMessageBadgePresenter(
                                                    item.tabItem.account,
                                                ),
                                        )

                                    else -> item
                                }
                            }.toImmutableList(),
                )
            }

        return object : State {
            override val tabs = tabs
        }
    }

    @Composable
    private fun notificationBadgePresenter(accountType: AccountType): UiState<Int> {
        val presenter =
            remember(accountType) {
                NotificationBadgePresenter(accountType)
            }.body()
        return presenter.count
    }

    @Composable
    private fun directMessageBadgePresenter(accountType: AccountType): UiState<Int> {
        val presenter =
            remember(accountType) {
                DirectMessageBadgePresenter(accountType)
            }.body()
        return presenter.count
    }
}
