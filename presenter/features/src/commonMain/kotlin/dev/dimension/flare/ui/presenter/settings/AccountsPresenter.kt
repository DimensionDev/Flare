package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AccountsPresenter :
    PresenterBase<AccountsState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountsFlow by lazy {
        accountRepository.allAccounts
            .map { accounts ->
                accounts.map { account ->
                    accountServiceFlow(
                        AccountType.Specific(account.accountKey),
                        accountRepository,
                    ).flatMapLatest { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler.userById(account.accountKey.id).toUi().map { user ->
                                AccountsState.AccountItem(
                                    account = account,
                                    profile = user,
                                )
                            }
                        } else {
                            flowOf(
                                AccountsState.AccountItem(
                                    account = account,
                                    profile = UiState.Error(IllegalStateException("Account service is not authenticated user data source")),
                                ),
                            )
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it.toImmutableList()
            }
    }

    @Composable
    override fun body(): AccountsState {
        val accounts by accountsFlow.collectAsUiState()
        val activeAccount by accountRepository.activeAccount.flattenUiState()
        return object : AccountsState {
            override val accounts = accounts
            override val activeAccount = activeAccount

            override fun setActiveAccount(accountKey: MicroBlogKey) {
                accountRepository.setActiveAccount(accountKey)
            }

            override fun removeAccount(accountKey: MicroBlogKey) {
                accountRepository.delete(accountKey)
            }

            override fun updateOrder(newOrder: List<MicroBlogKey>) {
                accountRepository.updateAccountOrder(newOrder)
            }
        }
    }
}

@Immutable
public interface AccountsState {
    public val accounts: UiState<ImmutableList<AccountItem>>
    public val activeAccount: UiState<UiAccount>

    public fun setActiveAccount(accountKey: MicroBlogKey)

    public fun removeAccount(accountKey: MicroBlogKey)

    public fun updateOrder(newOrder: List<MicroBlogKey>)

    @Immutable
    public data class AccountItem(
        val account: UiAccount,
        val profile: UiState<UiProfile>,
    )
}
