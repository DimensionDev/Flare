package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase

class StatusPresenter(
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusState>() {
    @Composable
    override fun body(): StatusState {
        val accountServiceState =
            activeAccountServicePresenter().flatMap { (service, account) ->
                remember(account.accountKey, statusKey) {
                    service.status(statusKey)
                }.collectAsState().toUi()
            }

        return object : StatusState {
            override val status: UiState<UiStatus> = accountServiceState
        }
    }
}

interface StatusState {
    val status: UiState<UiStatus>
}
