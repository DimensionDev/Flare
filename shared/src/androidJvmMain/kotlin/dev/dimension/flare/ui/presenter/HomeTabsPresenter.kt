package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.NotificationTabItem
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.HomeTabsPresenter.State.HomeTabState.HomeTabItem
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.DirectMessageBadgePresenter
import dev.dimension.flare.ui.presenter.home.NotificationBadgePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

public class HomeTabsPresenter(
    private val tabSettings: Flow<TabSettings>,
) : PresenterBase<HomeTabsPresenter.State>() {
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

    @Composable
    override fun body(): State {
        val account =
            remember {
                ActiveAccountPresenter()
            }.invoke()
        val settings by tabSettings.collectAsUiState()

        val tabs =
            remember(
                account,
                settings,
            ) {
                account.user
                    .flatMap(
                        onError = {
                            UiState.Success(
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
                        },
                    ) { user ->
                        settings.flatMap(
                            onError = {
                                UiState.Success(
                                    State.HomeTabState(
                                        primary =
                                            TimelineTabItem
                                                .defaultPrimary(user)
                                                .map {
                                                    HomeTabItem(it)
                                                }.toImmutableList(),
                                        secondary =
                                            TimelineTabItem
                                                .defaultSecondary(user)
                                                .map {
                                                    HomeTabItem(it)
                                                }.toImmutableList(),
                                        extraProfileRoute =
                                            HomeTabItem(
                                                tabItem =
                                                    ProfileTabItem(
                                                        accountKey = user.key,
                                                        userKey = user.key,
                                                    ),
                                            ),
                                        secondaryIconOnly = true,
                                    ),
                                )
                            },
                        ) { tabSettings ->
                            val secondary =
                                tabSettings.secondaryItems ?: TimelineTabItem.defaultSecondary(user)
                            UiState.Success(
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
                                                    accountKey = user.key,
                                                    userKey = user.key,
                                                ),
                                        ),
                                    secondaryIconOnly = tabSettings.secondaryItems == null,
                                ),
                            )
                        }
                    }
            }.map {
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
            }.invoke()
        return presenter.count
    }

    @Composable
    private fun directMessageBadgePresenter(accountType: AccountType): UiState<Int> {
        val presenter =
            remember(accountType) {
                DirectMessageBadgePresenter(accountType)
            }.invoke()
        return presenter.count
    }
}
