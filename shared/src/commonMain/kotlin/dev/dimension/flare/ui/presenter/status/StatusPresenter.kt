package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): StatusState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val accountServiceState =
            serviceState.flatMap { service ->
                remember(service, statusKey) {
                    service.status(statusKey)
                }.collectAsState().toUi()
            }
        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()

        return object : StatusState {
            override val status: UiState<UiTimeline> = accountServiceState
        }
    }
}

@Immutable
interface StatusState {
    val status: UiState<UiTimeline>
}
