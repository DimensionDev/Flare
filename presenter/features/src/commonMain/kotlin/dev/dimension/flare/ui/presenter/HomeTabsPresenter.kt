package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.repository.activeAccountFlow
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTabsPresenter :
    PresenterBase<HomeTabsPresenter.State>(),
    KoinComponent {
    public interface State {
        public val tabs: UiState<ImmutableList<HomeTabs>>

        public enum class HomeTabs {
            Home,
            Notifications,
            Discover,
        }
    }

    private val accountRepository by inject<AccountRepository>()
    private val tabsFlow by lazy {
        activeAccountFlow(accountRepository)
            .map { account ->
                if (account == null) {
                    persistentListOf(
                        State.HomeTabs.Home,
                        State.HomeTabs.Discover,
                    )
                } else {
                    persistentListOf(
                        State.HomeTabs.Home,
                        State.HomeTabs.Notifications,
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
