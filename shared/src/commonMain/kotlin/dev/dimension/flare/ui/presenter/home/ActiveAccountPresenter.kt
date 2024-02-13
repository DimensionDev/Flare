package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.mapNotNull
import org.koin.compose.koinInject

class ActiveAccountPresenter : PresenterBase<ActiveAccountState>() {
    @Composable
    override fun body(): ActiveAccountState {
        val user =
            activeAccountServicePresenter().flatMap { (service, account) ->
                remember(account.accountKey) {
                    service.userById(account.accountKey.id)
                }.collectAsState().toUi()
            }
        return object : ActiveAccountState {
            override val user = user
        }
    }
}

interface ActiveAccountState {
    val user: UiState<UiUser>
}

class UserPresenter(
    private val accountKey: MicroBlogKey?,
    private val userKey: MicroBlogKey,
) : PresenterBase<UserState>() {
    @Composable
    override fun body(): UserState {
        val accountRepository: AccountRepository = koinInject()
        val account by remember(accountKey) {
            if (accountKey == null) {
                accountRepository.activeAccount
            } else {
                accountRepository.getFlow(accountKey)
            }.mapNotNull { it }
        }.collectAsUiState()
        val user =
            account.flatMap {
                accountServiceProvider(it).userById(userKey.id).collectAsState().toUi()
            }

        return object : UserState {
            override val user = user
        }
    }
}

interface UserState {
    val user: UiState<UiUser>
}
