package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public class NotificationAccountsPresenter : PresenterBase<NotificationAccountsPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    private val accountsNotificationFlow by lazy {
        notificationAccountsFlow(accountRepository)
    }

    @Immutable
    public interface State {
        public val notifications: ImmutableList<NotificationAccountItem>
    }

    @Composable
    override fun body(): State {
        val notifications by accountsNotificationFlow.collectAsState(persistentListOf())
        return StateImpl(
            notifications = notifications,
        )
    }

    private data class StateImpl(
        override val notifications: ImmutableList<NotificationAccountItem>,
    ) : State
}
