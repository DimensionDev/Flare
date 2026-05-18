package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class LoggedInPresenter :
    PresenterBase<LoggedInState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

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
