package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.data.repository.allAccountsPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject

class AccountsPresenter : PresenterBase<AccountsState>() {
    @Composable
    override fun body(): AccountsState {
        val accountRepository = koinInject<AccountRepository>()
        val accounts by allAccountsPresenter()
        val activeAccount by activeAccountPresenter()
        val user =
            accounts.map {
                it
                    .map { account ->
                        account to
                            accountServiceProvider(accountType = AccountType.Specific(account.accountKey)).flatMap { service ->
                                remember(account.accountKey) {
                                    service.userById(account.accountKey.id)
                                }.collectAsState().toUi()
                            }
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }
        return object : AccountsState(
            accounts = user,
            activeAccount = activeAccount,
        ) {
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
abstract class AccountsState(
    val accounts: UiState<ImmutableListWrapper<Pair<UiAccount, UiState<UiProfile>>>>,
    val activeAccount: UiState<UiAccount>,
) {
    abstract fun setActiveAccount(accountKey: MicroBlogKey)

    abstract fun removeAccount(accountKey: MicroBlogKey)
}

@Immutable
data class ImmutableListWrapper<T : Any>(
    private val data: ImmutableList<T>,
) {
    val size: Int
        get() = data.size

    operator fun get(index: Int): T = data[index]

    fun indexOf(element: T): Int = data.indexOf(element)

    fun contains(element: T): Boolean = data.contains(element)

    fun toImmutableList(): ImmutableList<T> = data
}

fun <T : Any> ImmutableList<T>.toImmutableListWrapper(): ImmutableListWrapper<T> = ImmutableListWrapper(this)
