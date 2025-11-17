package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

public class AllNotificationBadgePresenter :
    PresenterBase<AllNotificationBadgePresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val count: Int

        public fun refresh()
    }

    private val allBadgeFlow by lazy {
        accountRepository.allAccounts
            .map {
                it.map {
                    it.dataSource.notificationBadgeCount()
                }
            }
    }

    @Composable
    override fun body(): State {
        val allBadge by allBadgeFlow.collectAsState(emptyList())

        val badgeStates =
            allBadge.map {
                it.collectAsState()
            }
        val badgeCount = badgeStates.sumOf { it.data ?: 0 }
        LaunchedEffect(badgeStates) {
            while (true) {
                delay(1.minutes)
                badgeStates.forEach {
                    it.refresh()
                }
            }
        }

        return object : State {
            override val count: Int = badgeCount

            override fun refresh() {
                badgeStates.forEach {
                    it.refresh()
                }
            }
        }
    }
}
