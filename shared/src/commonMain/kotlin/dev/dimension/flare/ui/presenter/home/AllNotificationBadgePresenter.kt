package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

@WebPresenter("notificationBadge")
public class AllNotificationBadgePresenter :
    PresenterBase<AllNotificationBadgePresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    @androidx.compose.runtime.Immutable
    public interface State {
        public val count: Int
        public val notifications: ImmutableList<NotificationAccountItem>

        public fun refresh()
    }

    private val allBadgeFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map {
                it
                    .filterIsInstance<NotificationDataSource>()
                    .map {
                        it.notificationHandler.notificationBadgeCount
                    }
            }
    }

    private val accountsNotificationFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map {
                it
                    .filterIsInstance<UserDataSource>()
                    .filterIsInstance<AuthenticatedMicroblogDataSource>()
                    .filterIsInstance<NotificationTimelineDataSource>()
            }.map { accounts ->
                accounts.map { dataSource ->
                    when (dataSource) {
                        !is UserDataSource -> {
                            flowOf(null)
                        }

                        !is NotificationDataSource -> {
                            dataSource.userHandler.userById(dataSource.accountKey.id).toUi().map {
                                it.map {
                                    it to 0
                                }
                            }
                        }

                        else -> {
                            combine(
                                dataSource.userHandler.userById(dataSource.accountKey.id).toUi(),
                                dataSource.notificationHandler.notificationBadgeCount.toUi(),
                            ) { user, badge ->
                                user.flatMap { profile ->
                                    badge.map { count ->
                                        profile to count
                                    }
                                }
                            }
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull { it?.takeSuccess() }
                    .sortedWith(
                        compareByDescending { it.second },
                    ).map { (profile, badge) ->
                        NotificationAccountItem(
                            stableKey = "${profile.key.host}:${profile.key.id}",
                            profile = profile,
                            badge = badge,
                        )
                    }.toImmutableList()
            }
    }


    @Composable
    override fun body(): State {
        val allBadge by allBadgeFlow.collectAsState(emptyList())
        val accountsNotification by accountsNotificationFlow.collectAsState(persistentListOf())

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
            override val notifications = accountsNotification

            override fun refresh() {
                badgeStates.forEach {
                    it.refresh()
                }
            }
        }
    }
}
