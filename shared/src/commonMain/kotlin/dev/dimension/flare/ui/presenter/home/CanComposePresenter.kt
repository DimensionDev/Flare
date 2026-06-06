package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class CanComposePresenter :
    PresenterBase<CanComposeState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val canComposeFlow by lazy {
        accountRepository.allAccounts.map { accounts ->
            accounts.any { account ->
                runCatching {
                    accountRepository.getOrCreateDataSource(account) is ComposeDataSource
                }.getOrDefault(false)
            }
        }
    }

    @Composable
    override fun body(): CanComposeState {
        val canCompose by canComposeFlow.collectAsUiState()
        return object : CanComposeState {
            override val canCompose: UiState<Boolean> = canCompose
        }
    }
}

@Immutable
public interface CanComposeState {
    public val canCompose: UiState<Boolean>
}
