package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datasource.microblog.DirectMessageDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

public class DirectMessageAvailabilityPresenter : PresenterBase<DirectMessageAvailabilityPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    private val hasAvailableAccountFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services.any { it.isDirectMessageCapableAccountService() }
            }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val hasAvailableAccount by hasAvailableAccountFlow.collectAsState(false)
        return StateImpl(
            hasAvailableAccount = hasAvailableAccount,
        )
    }

    @Immutable
    public interface State {
        public val hasAvailableAccount: Boolean
    }

    private data class StateImpl(
        override val hasAvailableAccount: Boolean,
    ) : State
}

internal fun MicroblogDataSource.isDirectMessageCapableAccountService(): Boolean = this is DirectMessageDataSource && this is UserDataSource
