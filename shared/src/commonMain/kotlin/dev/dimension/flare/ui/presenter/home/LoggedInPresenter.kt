package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

public class LoggedInPresenter :
    PresenterBase<LoggedInState>() {
    private val accountRepository: AccountRepository by koinInject()

    private val loggedInFlow by lazy {
        accountRepository.allAccounts.map { it.isNotEmpty() }
    }

    @Composable
    override fun body(): LoggedInState {
        val isLoggedIn by loggedInFlow.collectAsUiState()
        return object : LoggedInState {
            override val isLoggedIn: UiState<Boolean> = isLoggedIn
        }
    }
}

@Immutable
public interface LoggedInState {
    public val isLoggedIn: UiState<Boolean>
}
