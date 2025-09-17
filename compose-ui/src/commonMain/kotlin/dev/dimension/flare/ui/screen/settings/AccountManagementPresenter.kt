package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsState
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AccountManagementPresenter :
    PresenterBase<AccountManagementPresenter.State>(),
    KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    public interface State : AccountsState {
        public fun logout(accountKey: MicroBlogKey)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val state =
            remember {
                AccountsPresenter()
            }.body()

        return object : State, AccountsState by state {
            override fun logout(accountKey: MicroBlogKey) {
                accounts.onSuccess { accountList ->
                    if (accountList.size == 1) {
                        // is Last account
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                TabSettings()
                            }
                        }
                    } else {
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                copy(
                                    secondaryItems =
                                        secondaryItems?.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                    mainTabs =
                                        mainTabs.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                )
                            }
                        }
                    }
                }
                removeAccount(accountKey)
            }
        }
    }
}
