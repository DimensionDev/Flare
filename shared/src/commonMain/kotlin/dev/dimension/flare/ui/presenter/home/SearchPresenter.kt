package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.isEmpty
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.SearchOperation
import dev.dimension.flare.data.datasource.microblog.accountKeyOrNull
import dev.dimension.flare.data.datasource.microblog.accountProfileFlow
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
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
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebIgnore
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Composable
public fun rememberDiscoverSearchState(discover: DiscoverState): SearchState {
    var committedQuery by remember { mutableStateOf("") }
    val state =
        key(discover.selectedAccountType, discover.isAccountReady) {
            remember {
                SearchPresenter(discover.selectedAccountType, if (discover.isAccountReady) committedQuery else "")
            }.body()
        }
    return object : SearchState by state {
        override val searching = committedQuery.isNotEmpty()

        override fun search(query: String) {
            committedQuery = query
            discover.setSearchActive(query.isNotEmpty())
            if (discover.isAccountReady) state.search(query)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@WebPresenter("search")
public class SearchPresenter(
    private val accountType: AccountType,
    private val initialQuery: String = "",
) : PresenterBase<SearchState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val selectedAccountKeyFlow = MutableStateFlow((accountType as? AccountType.Specific)?.accountKey)

    private val accountsFlow by lazy {
        combine(allAccountServicesFlow(accountRepository), selectedAccountKeyFlow) { sources, selectedKey ->
            sources.filter { dataSource ->
                dataSource.capabilities.search?.canSearch == true || selectedKey != null && dataSource.accountKeyOrNull == selectedKey
            }
        }.distinctUntilChanged()
            .flatMapLatest { sources ->
                val defaultAccountType =
                    sources
                        .firstNotNullOfOrNull { source ->
                            source.accountKeyOrNull?.takeIf { source.capabilities.search?.canSearch == true }
                        }?.let { AccountType.Specific(it) } ?: AccountType.Guest
                flowOf(sources.mapNotNull { it.accountProfileFlow() })
                    .combineLatestFlowLists()
                    .map { profiles -> SearchAccounts(defaultAccountType, profiles.mapNotNull { it.takeSuccess() }.toImmutableList()) }
                    .onStart { emit(SearchAccounts(defaultAccountType, persistentListOf())) }
            }
    }

    private data class SearchAccounts(
        val defaultAccountType: AccountType,
        val profiles: ImmutableList<UiProfile>,
    )

    private val queryFlow by lazy {
        MutableStateFlow(initialQuery)
    }

    @Composable
    override fun body(): SearchState {
        val accountState by accountsFlow.collectAsUiState()
        val accounts = accountState.map { it.profiles }
        val query by queryFlow.collectAsState()
        val selectedAccountKey by selectedAccountKeyFlow.collectAsState()
        val selectedAccount = accounts.takeSuccess()?.firstOrNull { it.key == selectedAccountKey }
        val selectedAccountType = selectedAccountKey?.let { AccountType.Specific(it) } ?: accountType
        val (serviceState, retryService) = rememberSearchAccountService(selectedAccountType, accountRepository)
        val dataSource = serviceState.takeSuccess()
        val search = remember(dataSource) { dataSource?.capabilities?.search }
        val searching = query.isNotEmpty()

        return key(dataSource, query) {
            val users =
                if (searching && search?.supports(SearchOperation.Profiles) == true) {
                    rememberSearchPaging { search.searchUser(query) }
                } else {
                    PagingState.Empty {}
                }
            val status =
                when {
                    searching && search?.supports(SearchOperation.Posts) == true -> {
                        rememberSearchTimeline { search.searchStatus(query) }
                    }

                    searching && serviceState is UiState.Error -> {
                        PagingState.Error(serviceState.throwable, retryService)
                    }

                    else -> {
                        PagingState.Empty {}
                    }
                }

            object : SearchState {
                override val users = users
                override val status = status
                override val searching = searching
                override val isSearchUnavailable = searching && dataSource != null && search?.canSearch != true
                override val hasNoResults = searching && search?.canSearch == true && users.isEmpty && status.isEmpty
                override val accounts = accounts
                override val defaultAccountType = accountState.map { it.defaultAccountType }
                override val selectedAccount = selectedAccount

                override fun search(query: String) {
                    queryFlow.value = query
                }

                override suspend fun refreshSuspend() {
                    users.refreshSuspend()
                    status.refreshSuspend()
                }

                override fun setAccount(profile: UiProfile) {
                    selectedAccountKeyFlow.value = profile.key
                }

                override fun setAccountKey(key: MicroBlogKey) {
                    if (accounts.takeSuccess()?.any { it.key == key } == true) {
                        selectedAccountKeyFlow.value = key
                    }
                }
            }
        }
    }
}

@Immutable
public interface SearchState {
    public val users: PagingState<UiProfile>
    public val status: PagingState<UiTimelineV2>
    public val searching: Boolean
    public val isSearchUnavailable: Boolean
    public val hasNoResults: Boolean
    public val accounts: UiState<ImmutableList<UiProfile>>
    public val defaultAccountType: UiState<AccountType>
    public val selectedAccount: UiProfile?

    public fun search(query: String)

    public suspend fun refreshSuspend()

    @WebIgnore
    public fun setAccount(profile: UiProfile)

    public fun setAccountKey(key: MicroBlogKey)
}

@Composable
internal fun rememberSearchAccountService(
    accountType: AccountType?,
    repository: AccountRepository,
): Pair<UiState<MicroblogDataSource>, () -> Unit> {
    val attempt = remember(accountType) { mutableIntStateOf(0) }
    val retry: () -> Unit = remember(attempt) { { attempt.intValue += 1 } }
    val service =
        if (accountType == null) {
            UiState.Loading()
        } else {
            key(accountType, attempt.intValue) {
                remember { flow { emitAll(accountServiceFlow(accountType, repository)) } }.collectAsUiState().value
            }
        }
    return service to retry
}

@Composable
internal fun <T : Any> rememberSearchPaging(load: () -> RemoteLoader<T>): PagingState<T> {
    val scope = rememberCoroutineScope()
    val pages =
        remember {
            Pager(config = pagingConfig) { load().toPagingSource() }.flow.cachedIn(scope)
        }
    return pages.collectAsLazyPagingItems().toPagingState()
}

@Composable
internal fun rememberSearchTimeline(load: () -> RemoteLoader<UiTimelineV2>): PagingState<UiTimelineV2> {
    val scope = rememberCoroutineScope()
    val pages = remember { TimelinePresenter(loader = flow { emit(load()) }).createPager(scope) }
    return pages.collectAsLazyPagingItems().toPagingState()
}
