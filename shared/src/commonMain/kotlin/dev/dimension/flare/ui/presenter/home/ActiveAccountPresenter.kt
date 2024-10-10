package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.NoActiveAccountException
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.compose.koinInject

class ActiveAccountPresenter : PresenterBase<UserState>() {
    @Composable
    override fun body(): UserState {
        val accountRepository = koinInject<AccountRepository>()
        val account by activeAccountPresenter()
        val user =
            account
                .flatMap {
                    accountServiceProvider(accountType = AccountType.Specific(it.accountKey))
                }.flatMap {
                    if (it !is AuthenticatedMicroblogDataSource) {
                        UiState.Error(NoActiveAccountException)
                    } else {
                        remember(it.accountKey) {
                            it.userById(it.accountKey.id)
                        }.collectAsState()
                            .toUi()
                            .map {
                                it as UiUserV2
                            }
                    }
                }
        account.onSuccess { uiAccount ->
            user.onError {
                LaunchedEffect(it) {
                    if (it !is NoActiveAccountException) {
                        // auth expired
                        accountRepository.delete(uiAccount.accountKey)
                    }
                }
            }
        }
        return object : UserState {
            override val user = user
        }
    }
}

interface UserState {
    val user: UiState<UiUserV2>
}
