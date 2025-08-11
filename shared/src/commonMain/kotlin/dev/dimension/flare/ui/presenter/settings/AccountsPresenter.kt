package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.allAccountsPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AccountsPresenter :
    PresenterBase<AccountsState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): AccountsState {
        val accounts by allAccountsPresenter(repository = accountRepository)
        val activeAccount by accountRepository.activeAccount.flattenUiState()
        val user =
            accounts.map {
                it
                    .map { account ->
                        account to
                            accountServiceProvider(
                                accountType = AccountType.Specific(account.accountKey),
                                repository = accountRepository,
                            ).flatMap { service ->
                                remember(account.accountKey) {
                                    service.userById(account.accountKey.id)
                                }.collectAsState().toUi()
                            }
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }
        return object : AccountsState {
            override val accounts = user
            override val activeAccount = activeAccount

            override fun setActiveAccount(accountKey: MicroBlogKey) {
                accountRepository.setActiveAccount(accountKey)
            }

            override fun removeAccount(accountKey: MicroBlogKey) {
                accountRepository.delete(accountKey)
            }
        }
    }
}

@Immutable
public interface AccountsState {
    public val accounts: UiState<ImmutableListWrapper<Pair<UiAccount, UiState<UiProfile>>>>
    public val activeAccount: UiState<UiAccount>

    public abstract fun setActiveAccount(accountKey: MicroBlogKey)

    public abstract fun removeAccount(accountKey: MicroBlogKey)
}
