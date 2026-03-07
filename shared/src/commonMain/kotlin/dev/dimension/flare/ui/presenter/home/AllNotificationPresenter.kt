package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public data class NotificationAccountItem(
    val stableKey: String,
    val profile: UiProfile,
    val badge: Int,
)

public class AllNotificationPresenter :
    PresenterBase<AllNotificationPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val selectedAccountFlow = MutableStateFlow<UiProfile?>(null)
    private val selectedNotificationFilterFlow = MutableStateFlow<NotificationFilter?>(null)

    @androidx.compose.runtime.Immutable
    public interface State {
        public val notifications: ImmutableList<NotificationAccountItem>
        public val supportedNotificationFilters: UiState<ImmutableList<NotificationFilter>>
        public val timeline: PagingState<UiTimelineV2>
        public val selectedFilter: NotificationFilter?
        public val selectedAccount: UiProfile?
        public val selectedAccountIndex: Int

        public fun setAccount(profile: UiProfile)

        public fun setFilter(filter: NotificationFilter)

        public suspend fun refreshSuspend()
    }

    private val accountsNotificationFlow by lazy {
        accountRepository.allAccounts
            .map { accounts ->
                accounts.map { account ->
                    when (val dataSource = account.dataSource) {
                        !is UserDataSource -> {
                            flowOf(null)
                        }

                        !is NotificationDataSource -> {
                            dataSource.userHandler.userById(account.accountKey.id).data.map {
                                if (it is CacheState.Success) {
                                    it.data to 0
                                } else {
                                    null
                                }
                            }
                        }

                        else -> {
                            combine(
                                dataSource.userHandler.userById(account.accountKey.id).data,
                                dataSource.notificationHandler.notificationBadgeCount.data,
                            ) { user, badge ->
                                if (user is CacheState.Success) {
                                    user.data to
                                        if (badge is CacheState.Success) {
                                            badge.data
                                        } else {
                                            0
                                        }
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .filterNotNull()
                    .sortedWith(
                        compareByDescending<Pair<UiProfile, Int>> { it.second }
                            .thenBy { it.first.handle.canonical }
                            .thenBy { it.first.key.host }
                            .thenBy { it.first.key.id },
                    ).map { (profile, badge) ->
                        NotificationAccountItem(
                            stableKey = "${profile.key.host}:${profile.key.id}",
                            profile = profile,
                            badge = badge,
                        )
                    }.toImmutableList()
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notificationFiltersFlow by lazy {
        selectedAccountFlow
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest {
                accountServiceFlow(AccountType.Specific(it.key), accountRepository)
            }.map {
                require(it is AuthenticatedMicroblogDataSource)
                it.supportedNotificationFilter.toImmutableList()
            }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                combine(
                    selectedNotificationFilterFlow.filterNotNull(),
                    selectedAccountFlow.filterNotNull(),
                ) { filter, profile -> filter to profile.key }
                    .distinctUntilChanged()
                    .flatMapLatest { (filter, accountKey) ->
                        accountServiceFlow(AccountType.Specific(accountKey), accountRepository)
                            .map {
                                require(it is AuthenticatedMicroblogDataSource)
                                it.notification(filter)
                            }.distinctUntilChanged()
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val notifications by accountsNotificationFlow.collectAsState(emptyList<NotificationAccountItem>().toImmutableList())
        val selectedAccount by selectedAccountFlow.collectAsState()
        val selectedAccountIndex by remember {
            derivedStateOf {
                val maxIndex = (notifications.size - 1).coerceAtLeast(0)
                selectedAccount?.let { profile ->
                    notifications
                        .indexOfFirst { it.profile.key == profile.key }
                        .coerceIn(0, maxIndex)
                } ?: 0
            }
        }
        val selectedNotificationFilter by selectedNotificationFilterFlow.collectAsState()
        val notificationFilters by notificationFiltersFlow.collectAsUiState()

        notificationFilters.onSuccess {
            LaunchedEffect(it) {
                selectedNotificationFilterFlow.value = it.firstOrNull()
            }
        }

        LaunchedEffect(notifications) {
            val current = selectedAccountFlow.value
            if (current == null || notifications.none { it.profile.key == current.key }) {
                selectedAccountFlow.value = notifications.firstOrNull()?.profile
            }
        }

        val listState = timelinePresenter.body().listState

        return object : State {
            override val notifications = notifications
            override val supportedNotificationFilters = notificationFilters
            override val timeline = listState
            override val selectedFilter = selectedNotificationFilter
            override val selectedAccount = selectedAccount
            override val selectedAccountIndex = selectedAccountIndex

            override fun setAccount(profile: UiProfile) {
                selectedAccountFlow.value = profile
            }

            override fun setFilter(filter: NotificationFilter) {
                selectedNotificationFilterFlow.value = filter
            }

            override suspend fun refreshSuspend() {
                listState.refreshSuspend()
            }
        }
    }
}
