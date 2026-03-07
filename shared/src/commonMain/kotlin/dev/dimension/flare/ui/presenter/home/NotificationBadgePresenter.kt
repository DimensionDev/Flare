package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

public class NotificationBadgePresenter(
    private val accountType: AccountType,
    private val autoRefresh: Boolean = true,
) : PresenterBase<NotificationBadgeState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val countFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).filterIsInstance<NotificationDataSource>()
            .map {
                it.notificationHandler.notificationBadgeCount
            }
    }

    @Composable
    override fun body(): NotificationBadgeState {
        val countState by countFlow.collectAsUiState()
        val count = countState.flatMap { it.collectAsState().toUi() }
        if (autoRefresh) {
            countState.onSuccess {
                LaunchedEffect(Unit) {
                    delay(1.minutes)
                    it.refresh()
                }
            }
        }
        return object : NotificationBadgeState {
            override val count = count

            override fun refresh() {
                countState.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}

@Immutable
public interface NotificationBadgeState {
    public val count: UiState<Int>

    public fun refresh()
}
