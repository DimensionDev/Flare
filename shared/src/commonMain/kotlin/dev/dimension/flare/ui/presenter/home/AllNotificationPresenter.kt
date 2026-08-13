package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.accountKeyOrNull
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebIgnore
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

public data class NotificationAccountItem(
    val stableKey: String,
    val profile: UiProfile,
    val badge: Int,
)

internal fun notificationAccountsFlow(accountRepository: AccountRepository): Flow<ImmutableList<NotificationAccountItem>> =
    allAccountServicesFlow(accountRepository)
        .map {
            it
                .mapNotNull { dataSource ->
                    val profile = dataSource.capabilities.profile ?: return@mapNotNull null
                    val notification = dataSource.capabilities.notification ?: return@mapNotNull null
                    if (notification.timeline == null) return@mapNotNull null
                    val accountKey = dataSource.accountKeyOrNull ?: return@mapNotNull null
                    Triple(profile, accountKey, notification.events)
                }
        }.map { accounts ->
            accounts.map { (profile, accountKey, notificationEvents) ->
                when (notificationEvents) {
                    null -> {
                        profile.userHandler.userById(accountKey.id).toUi().map {
                            it.map {
                                it to 0
                            }
                        }
                    }

                    else -> {
                        combine(
                            profile.userHandler.userById(accountKey.id).toUi(),
                            notificationEvents.notificationHandler.notificationBadgeCount.toUi(),
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
                .mapNotNull { it.takeSuccess() }
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

@WebPresenter("notifications")
public class AllNotificationPresenter : PresenterBase<AllNotificationPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val appScope: CoroutineScope by koinInject()
    private val accountSelection by lazy {
        RememberedAccountSelection(appDataStore.notificationPreferences, appScope, accountRepository.accountMutationMutex) {
            accountRepository.find(it) !=
                null
        }
    }
    private val selectedNotificationFilterFlow = MutableStateFlow<NotificationFilter?>(null)

    @androidx.compose.runtime.Immutable
    public interface State {
        public val notifications: ImmutableList<NotificationAccountItem>
        public val supportedNotificationFilters: UiState<ImmutableList<NotificationFilter>>
        public val timeline: PagingState<UiTimelineV2>
        public val selectedFilter: NotificationFilter?
        public val selectedAccount: UiProfile?
        public val selectedAccountKey: MicroBlogKey?
        public val selectedAccountIndex: Int
        public val canSwitchAccount: Boolean

        @WebIgnore
        public fun setAccount(profile: UiProfile)

        public fun setAccountKey(key: MicroBlogKey)

        public fun setFilter(filter: NotificationFilter)

        public suspend fun refreshSuspend()
    }

    private val accountsNotificationFlow by lazy {
        notificationAccountsFlow(accountRepository)
    }

    private val notificationAccountKeysFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { sources ->
                sources
                    .filter { it.capabilities.notification?.timeline != null && it.capabilities.profile != null }
                    .mapNotNull { it.accountKeyOrNull }
            }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notificationFiltersFlow by lazy {
        accountSelection.selectedAccountKey
            .flatMapLatest { accountKey ->
                if (accountKey == null) {
                    flowOf(emptyList<NotificationFilter>().toImmutableList())
                } else {
                    accountServiceFlow(AccountType.Specific(accountKey), accountRepository).map {
                        requireNotNull(it.capabilities.notification?.timeline).supportedNotificationFilter.toImmutableList()
                    }
                }
            }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                accountSelection.selectedAccountKey.flatMapLatest { accountKey ->
                    if (accountKey == null) {
                        flowOf(notSupported())
                    } else {
                        accountServiceFlow(AccountType.Specific(accountKey), accountRepository)
                            .flatMapLatest { source ->
                                val timeline = requireNotNull(source.capabilities.notification?.timeline)
                                selectedNotificationFilterFlow
                                    .map { filter ->
                                        filter?.takeIf { it in timeline.supportedNotificationFilter }
                                            ?: timeline.supportedNotificationFilter.firstOrNull()
                                    }.distinctUntilChanged()
                                    .map { filter ->
                                        filter?.let(timeline::notification) ?: notSupported()
                                    }
                            }.distinctUntilChanged()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val notifications by accountsNotificationFlow.collectAsState(emptyList<NotificationAccountItem>().toImmutableList())
        val selectedAccountKey by accountSelection.selectedAccountKey.collectAsState(null)
        val selectedAccount = notifications.firstOrNull { it.profile.key == selectedAccountKey }?.profile
        val selectedAccountIndex by remember {
            derivedStateOf {
                notifications.indexOfFirst { it.profile.key == selectedAccountKey }
            }
        }
        val selectedNotificationFilter by selectedNotificationFilterFlow.collectAsState()
        val notificationFilters by notificationFiltersFlow.collectAsUiState()

        notificationFilters.onSuccess {
            LaunchedEffect(it) {
                selectedNotificationFilterFlow.value = it.firstOrNull()
            }
        }

        LaunchedEffect(Unit) {
            accountSelection.restore()
        }
        LaunchedEffect(Unit) {
            notificationAccountKeysFlow.collect(accountSelection::updateAccounts)
        }

        val listState = timelinePresenter.body().listState

        return object : State {
            override val notifications = notifications
            override val supportedNotificationFilters = notificationFilters
            override val timeline = listState
            override val selectedFilter = selectedNotificationFilter
            override val selectedAccount = selectedAccount
            override val selectedAccountKey = selectedAccountKey
            override val selectedAccountIndex = selectedAccountIndex
            override val canSwitchAccount = notifications.any { it.profile.key != selectedAccountKey }

            override fun setAccount(profile: UiProfile) {
                setAccountKey(profile.key)
            }

            override fun setAccountKey(key: MicroBlogKey) {
                accountSelection.selectAccount(key)
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
