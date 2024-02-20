package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.allAccountsPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject

class QuickMenuPresenter : PresenterBase<QuickMenuState>() {
    @Composable
    override fun body(): QuickMenuState {
        val accountRepository = koinInject<AccountRepository>()
        val user = remember { ActiveAccountPresenter() }.body()
        val accounts by allAccountsPresenter()
        val allUsers =
            accounts.flatMap { data ->
                user.user.map { current ->
                    data.filter {
                        it.accountKey != current.userKey
                    }.map { account ->
                        accountServiceProvider(accountKey = account.accountKey).flatMap { service ->
                            remember(account.accountKey) {
                                service.userById(account.accountKey.id)
                            }.collectAsState().toUi()
                        }
                    }.toImmutableList().toImmutableListWrapper()
                }
            }

        return object : QuickMenuState, ActiveAccountState by user {
            override val allUsers = allUsers

            override fun setActiveAccount(accountKey: MicroBlogKey) {
                accountRepository.setActiveAccount(accountKey)
            }
        }
    }
}

interface QuickMenuState : ActiveAccountState {
    val allUsers: UiState<ImmutableListWrapper<UiState<UiUser>>>

    fun setActiveAccount(accountKey: MicroBlogKey)
}
