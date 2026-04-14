package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ActiveAccountPresenter :
    PresenterBase<UserState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userFlow by lazy {
        accountRepository.activeAccount.flatMapLatest { accountState ->
            when (accountState) {
                is UiState.Loading -> {
                    flowOf(UiState.Loading())
                }

                is UiState.Error -> {
                    flowOf(UiState.Error(accountState.throwable))
                }

                is UiState.Success -> {
                    val accountKey = accountState.data.accountKey
                    accountServiceFlow(
                        accountType = AccountType.Specific(accountKey),
                        repository = accountRepository,
                    ).flatMapLatest { service ->
                        if (service is UserDataSource) {
                            service.userHandler.userById(accountKey.id).toUi()
                        } else {
                            flowOf(UiState.Error(IllegalStateException("Current account does not support user data")))
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun body(): UserState {
        val user by userFlow.flattenUiState()
        return object : UserState {
            override val user: UiState<UiProfile> = user
        }
    }
}

@Immutable
public interface UserState {
    public val user: UiState<UiProfile>
}
