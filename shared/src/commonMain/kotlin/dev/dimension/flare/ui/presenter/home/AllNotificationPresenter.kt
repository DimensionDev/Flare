package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class AllNotificationPresenter :
    PresenterBase<AllNotificationPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    public interface State {
        public val notifications: UiState<ImmutableMap<UiProfile, Int>>
        public val supportedNotificationFilters: UiState<ImmutableList<NotificationFilter>>
        public val timeline: PagingState<UiTimeline>

        public fun selectProfile(profile: UiProfile)

        public fun selectFilter(filter: NotificationFilter)
    }

    private val accountsNotificationFlow by lazy {
        accountRepository.allAccounts
            .map {
                it.map {
                    combine(it.dataSource.userById(it.accountKey.id).data, it.dataSource.notificationBadgeCount().data) { user, badge ->
                        if (user is CacheState.Success && badge is CacheState.Success) {
                            user.data to badge.data
                        } else {
                            null
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it.filterNotNull().toMap().toImmutableMap()
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val notifications by accountsNotificationFlow.collectAsUiState()
        var selectedAccount by remember {
            mutableStateOf<UiProfile?>(null)
        }
        var selectedNotificationFilter by remember {
            mutableStateOf<NotificationFilter?>(null)
        }

        val notificationFilters by remember {
            snapshotFlow { selectedAccount }
                .filterNotNull()
                .flatMapLatest {
                    accountServiceFlow(AccountType.Specific(it.key), accountRepository)
                }.map {
                    require(it is AuthenticatedMicroblogDataSource)
                    it.supportedNotificationFilter.toImmutableList()
                }
        }.collectAsUiState()

        notificationFilters.onSuccess {
            LaunchedEffect(it) {
                selectedNotificationFilter = it.firstOrNull()
            }
        }
        notifications.onSuccess {
            LaunchedEffect(it) {
                selectedAccount = it.keys.firstOrNull()
            }
        }

        val listState =
            remember {
                combine(
                    snapshotFlow { selectedNotificationFilter }.filterNotNull(),
                    snapshotFlow { selectedAccount }.filterNotNull(),
                ) { filter, profile ->
                    accountServiceFlow(AccountType.Specific(profile.key), accountRepository)
                        .flatMapLatest {
                            require(it is AuthenticatedMicroblogDataSource)
                            it.notification(filter, scope = scope)
                        }
                }.flatMapLatest { it }
            }.collectAsLazyPagingItems().toPagingState()

        return object : State {
            override val notifications = notifications
            override val supportedNotificationFilters = notificationFilters
            override val timeline = listState

            override fun selectProfile(profile: UiProfile) {
                selectedAccount = profile
            }

            override fun selectFilter(filter: NotificationFilter) {
                selectedNotificationFilter = filter
            }
        }
    }
}
