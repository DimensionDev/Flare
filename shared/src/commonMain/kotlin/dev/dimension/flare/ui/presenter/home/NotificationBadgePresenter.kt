package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

class NotificationBadgePresenter(
    private val accountType: AccountType,
    private val autoRefresh: Boolean = true,
) : PresenterBase<NotificationBadgeState>() {
    @Composable
    override fun body(): NotificationBadgeState {
        val serviceState =
            accountServiceProvider(accountType = accountType).map {
                require(it is AuthenticatedMicroblogDataSource)
                it
            }
        val countState =
            serviceState.map { service ->
                remember(service) {
                    service.notificationBadgeCount()
                }.collectAsState()
            }
        if (autoRefresh) {
            countState.onSuccess {
                LaunchedEffect(Unit) {
                    delay(1.minutes)
                    it.refresh()
                }
            }
        }
        return object : NotificationBadgeState {
            override val count: UiState<Int> = countState.flatMap { it.toUi() }

            override fun refresh() {
                countState.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}

interface NotificationBadgeState {
    val count: UiState<Int>

    fun refresh()
}
