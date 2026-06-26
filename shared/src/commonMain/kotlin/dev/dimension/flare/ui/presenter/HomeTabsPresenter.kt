package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

@WebPresenter("homeTabs")
public class HomeTabsPresenter :
    PresenterBase<HomeTabsPresenter.State>() {
    public interface State {
        public val tabs: UiState<ImmutableList<HomeTabs>>

        public enum class HomeTabs {
            Home,
            Notifications,
            Discover,
        }
    }

    private val accountRepository by koinInject<AccountRepository>()
    private val tabsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { accountServices ->
                if (accountServices.any { it is NotificationTimelineDataSource }) {
                    persistentListOf(
                        State.HomeTabs.Home,
                        State.HomeTabs.Notifications,
                        State.HomeTabs.Discover,
                    )
                } else {
                    persistentListOf(
                        State.HomeTabs.Home,
                        State.HomeTabs.Discover,
                    )
                }.toImmutableList()
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
}
