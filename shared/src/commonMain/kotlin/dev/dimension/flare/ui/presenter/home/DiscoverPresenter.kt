package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@WebPresenter("discover")
public class DiscoverPresenter : PresenterBase<DiscoverState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val appScope: CoroutineScope by koinInject()
    private val accountSelection by lazy {
        RememberedAccountSelection(appDataStore.discoverPreferences, appScope, accountRepository.accountMutationMutex) {
            accountRepository.find(it) !=
                null
        }
    }

    private val accountKeysFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { sources ->
                sources
                    .filterIsInstance<AuthenticatedMicroblogDataSource>()
                    .filter { it is UserDataSource }
                    .map { it.accountKey }
            }.distinctUntilChanged()
    }

    private val accountsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map {
                it
                    .filterIsInstance<UserDataSource>()
                    .filterIsInstance<AuthenticatedMicroblogDataSource>()
                    .toImmutableList()
            }.map {
                it.map { dataSource ->
                    val authenticated = dataSource as UserDataSource
                    val accountKey = dataSource.accountKey
                    authenticated.userHandler.userById(accountKey.id).toUi()
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull {
                        it.takeSuccess()
                    }.toImmutableList()
            }
    }

    private val selectedAccountTypeFlow: Flow<AccountType> by lazy {
        accountSelection.selectedAccountType.filterNotNull()
    }

    private val usersFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository)
                .flatMapLatest { dataSource ->
                    runCatching {
                        Pager(config = pagingConfig) {
                            dataSource.discoverUsers().toPagingSource()
                        }.flow
                    }.getOrElse { PagingData.emptyFlow(isError = true) }
                }
        }
    }

    private val hashtagsFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository)
                .flatMapLatest { dataSource ->
                    runCatching {
                        Pager(config = pagingConfig) {
                            dataSource.discoverHashtags().toPagingSource()
                        }.flow
                    }.getOrElse { PagingData.emptyFlow(isError = true) }
                }
        }
    }

    private fun statusFlow(scope: CoroutineScope) =
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            DiscoverStatusTimelinePresenter(accountType).createPager(scope)
        }

    @Composable
    override fun body(): DiscoverState {
        val scope = rememberCoroutineScope()
        val accounts by accountsFlow.collectAsUiState()
        val restoredAccountType by accountSelection.selectedAccountType.collectAsState()
        val selectedAccountKey = (restoredAccountType as? AccountType.Specific)?.accountKey
        val selectedAccount = accounts.takeSuccess()?.firstOrNull { it.key == selectedAccountKey }
        val users =
            remember(usersFlow, scope) {
                usersFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val hashtags =
            remember(hashtagsFlow, scope) {
                hashtagsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val status =
            remember {
                statusFlow(scope)
            }.collectAsLazyPagingItems().toPagingState()

        LaunchedEffect(Unit) {
            accountSelection.restore()
        }
        LaunchedEffect(Unit) {
            accountKeysFlow.collect(accountSelection::updateAccounts)
        }

        return object : DiscoverState {
            override val users = users
            override val status = status
            override val hashtags = hashtags
            override val selectedAccount = selectedAccount
            override val selectedAccountKey = selectedAccountKey
            override val accounts = accounts
            override val selectedAccountType = restoredAccountType ?: AccountType.Guest
            override val isAccountReady = restoredAccountType != null
            override val canSwitchAccount = accounts.takeSuccess()?.any { it.key != selectedAccountKey } == true

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
                hashtags.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                setAccountKey(profile.key)
            }

            override fun setAccountKey(key: MicroBlogKey) {
                accountSelection.selectAccount(key)
            }
        }
    }
}

@Immutable
public interface DiscoverState {
    public val users: PagingState<UiProfile>
    public val status: PagingState<UiTimelineV2>
    public val hashtags: PagingState<UiHashtag>
    public val accounts: UiState<ImmutableList<UiProfile>>
    public val selectedAccount: UiProfile?
    public val selectedAccountKey: MicroBlogKey?
    public val selectedAccountType: AccountType
    public val isAccountReady: Boolean
    public val canSwitchAccount: Boolean

    public suspend fun refreshSuspend()

    @WebIgnore
    public fun setAccount(profile: UiProfile)

    public fun setAccountKey(key: MicroBlogKey)
}
