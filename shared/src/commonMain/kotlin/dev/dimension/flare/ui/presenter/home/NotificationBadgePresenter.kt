package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

public class NotificationBadgePresenter(
    private val accountType: AccountType,
    private val autoRefresh: Boolean = true,
) : PresenterBase<NotificationBadgeState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): NotificationBadgeState {
        val service = accountServiceProvider(accountType, accountRepository)
        val countCache =
            service
                .takeSuccess()
                ?.capabilities
                ?.notification
                ?.events
                ?.notificationHandler
                ?.notificationBadgeCount
        val count = service.flatMap { countCache?.collectAsState()?.toUi() ?: UiState.Success(0) }
        if (autoRefresh && countCache != null) {
            LaunchedEffect(countCache) {
                delay(1.minutes)
                countCache.refresh()
            }
        }
        return object : NotificationBadgeState {
            override val count = count

            override fun refresh() {
                countCache?.refresh()
            }
        }
    }
}

@Immutable
public interface NotificationBadgeState {
    public val count: UiState<Int>

    public fun refresh()
}
