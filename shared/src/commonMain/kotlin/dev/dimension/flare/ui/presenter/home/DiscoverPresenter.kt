package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.isEmpty
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.SearchOperation
import dev.dimension.flare.data.datasource.microblog.accountKeyOrNull
import dev.dimension.flare.data.datasource.microblog.accountProfileFlow
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.AccountRepository
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
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebIgnore
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@WebPresenter("discover")
public class DiscoverPresenter : PresenterBase<DiscoverState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val appScope: CoroutineScope by koinInject()
    private val searchActive = MutableStateFlow(false)
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
                    .filter { it.capabilities.search != null && it.accountProfileFlow() != null }
                    .mapNotNull { it.accountKeyOrNull }
            }.distinctUntilChanged()
    }

    private val accountsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map {
                it
                    .mapNotNull { dataSource ->
                        val capabilities = dataSource.capabilities
                        if (capabilities.search == null) return@mapNotNull null
                        dataSource.accountProfileFlow()
                    }.toImmutableList()
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull {
                        it.takeSuccess()
                    }.toImmutableList()
            }
    }

    @Composable
    override fun body(): DiscoverState {
        val accounts by accountsFlow.collectAsUiState()
        val restoredAccountType by accountSelection.selectedAccountType.collectAsState()
        val searching by searchActive.collectAsState()
        val selectedAccountKey = (restoredAccountType as? AccountType.Specific)?.accountKey
        val selectedAccount = accounts.takeSuccess()?.firstOrNull { it.key == selectedAccountKey }
        val (serviceState, retryService) = rememberSearchAccountService(restoredAccountType, accountRepository)
        val dataSource = serviceState.takeSuccess()
        val search = remember(dataSource) { dataSource?.capabilities?.search }
        val discoverPosts = !searching && search?.supports(SearchOperation.DiscoverPosts) == true
        val discoverProfiles = !searching && search?.supports(SearchOperation.DiscoverProfiles) == true
        val discoverHashtags = !searching && search?.supports(SearchOperation.DiscoverHashtags) == true

        LaunchedEffect(Unit) {
            accountSelection.restore()
        }
        LaunchedEffect(Unit) {
            accountKeysFlow.collect(accountSelection::updateAccounts)
        }

        return key(dataSource) {
            val users =
                if (search != null && discoverProfiles) {
                    rememberSearchPaging { search.discoverUsers() }
                } else {
                    PagingState.Empty {}
                }
            val hashtags =
                if (search != null && discoverHashtags) {
                    rememberSearchPaging { search.discoverHashtags() }
                } else {
                    PagingState.Empty {}
                }
            val status =
                when {
                    search != null && discoverPosts -> rememberSearchTimeline { search.discoverStatuses() }
                    !searching && serviceState is UiState.Error -> PagingState.Error(serviceState.throwable, retryService)
                    else -> PagingState.Empty {}
                }

            object : DiscoverState {
                override val users = users
                override val status = status
                override val hashtags = hashtags
                override val canSearchPosts = search?.supports(SearchOperation.Posts) == true
                override val hasNoRecommendations =
                    (discoverPosts || discoverProfiles || discoverHashtags) && users.isEmpty && status.isEmpty && hashtags.isEmpty
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

                override fun setSearchActive(active: Boolean) {
                    searchActive.value = active
                }
            }
        }
    }
}

@Immutable
public interface DiscoverState {
    public val users: PagingState<UiProfile>
    public val status: PagingState<UiTimelineV2>
    public val hashtags: PagingState<UiHashtag>
    public val canSearchPosts: Boolean
    public val hasNoRecommendations: Boolean
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

    @WebIgnore
    public fun setSearchActive(active: Boolean)
}
