package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActiveAccountPresenter :
    PresenterBase<UserState>(),
    KoinComponent {
    private val accountRepository by inject<AccountRepository>()

    @Composable
    override fun body(): UserState {
        val account by activeAccountPresenter(repository = accountRepository)
        val user =
            account
                .flatMap {
                    accountServiceProvider(accountType = AccountType.Specific(it.accountKey), repository = accountRepository)
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
//                        accountRepository.delete(uiAccount.accountKey)
                        // better to show a dialog to ask user to re-login
                    }
                }
            }
        }
        return object : UserState {
            override val user = user
        }
    }
}

@Immutable
interface UserState {
    val user: UiState<UiUserV2>
}
