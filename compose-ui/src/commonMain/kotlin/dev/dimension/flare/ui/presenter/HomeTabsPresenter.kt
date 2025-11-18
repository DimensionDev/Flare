package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.activeAccountFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.home.DirectMessageBadgePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
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
            val primary: ImmutableList<TabItem>,
            val secondary: ImmutableList<TabItem>,
            val extraProfileRoute: TabItem?,
            val secondaryIconOnly: Boolean = false,
        ) {
            val all: ImmutableList<TabItem>
                get() =
                    (primary + secondary + extraProfileRoute)
                        .filterNotNull()
                        .distinctBy { it.key }
                        .toImmutableList()
        }
    }

    private val settingsRepository by inject<SettingsRepository>()
    private val accountRepository by inject<AccountRepository>()
    private val tabsFlow by lazy {
        activeAccountFlow(accountRepository)
            .combine(
                settingsRepository.tabSettings.distinctUntilChangedBy { it.secondaryItems },
            ) { account, tabsState ->
                if (account == null) {
                    State.HomeTabState(
                        primary =
                            TimelineTabItem.guest.toImmutableList(),
                        secondary = persistentListOf(),
                        extraProfileRoute = null,
                        secondaryIconOnly = true,
                    )
                } else {
                    val secondary =
                        tabsState.secondaryItems ?: TimelineTabItem.defaultSecondary(account)
                    State.HomeTabState(
                        primary =
                            TimelineTabItem.default.toImmutableList(),
                        secondary =
                            secondary.toImmutableList(),
                        extraProfileRoute =
                            ProfileTabItem(
                                accountKey = account.accountKey,
                                userKey = account.accountKey,
                            ),
                        secondaryIconOnly = tabsState.secondaryItems == null,
                    )
                }
            }
    }

    @Composable
    override fun body(): State {
        val tabs by
            remember(tabsFlow) {
                tabsFlow
            }.collectAsUiState()

        return object : State {
            override val tabs = tabs
        }
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
