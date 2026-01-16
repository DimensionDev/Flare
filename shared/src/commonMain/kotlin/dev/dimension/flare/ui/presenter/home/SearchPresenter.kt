package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SearchPresenter(
    private val accountType: AccountType,
    private val initialQuery: String = "",
) : PresenterBase<SearchState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private val accountsFlow by lazy {
        accountRepository.allAccounts
            .map {
                it.map {
                    it.dataSource.userById(it.accountKey.id).toUi()
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull {
                        it.takeSuccess()
                    }.toImmutableList()
            }
    }

    @Composable
    override fun body(): SearchState {
        val scope = rememberCoroutineScope()

        val accounts by accountsFlow.collectAsUiState()
        var selectedAccount by remember {
            mutableStateOf<UiProfile?>(null)
        }

        accounts.onSuccess { profiles ->
            LaunchedEffect(profiles) {
                if (selectedAccount == null) {
                    selectedAccount = if (accountType is AccountType.Specific) {
                        profiles.find { it.key == accountType.accountKey }
                    } else {
                        null
                    } ?: profiles.firstOrNull()
                }
            }
        }

        val currentAccountType =
            remember(selectedAccount) {
                selectedAccount?.let { AccountType.Specific(it.key) } ?: accountType
            }

        val accountState =
            accountServiceProvider(accountType = currentAccountType, repository = accountRepository)
        var query by remember { mutableStateOf(initialQuery) }

        val user =
            accountState
                .flatMap { service ->
                    if (query.isEmpty()) {
                        UiState.Error(IllegalStateException("Query is empty"))
                    } else {
                        UiState.Success(
                            remember(service, query) {
                                service.searchUser(query, scope = scope)
                            }.collectAsLazyPagingItems(),
                        )
                    }
                }.toPagingState()

        val status =
            remember(currentAccountType) {
                SearchStatusTimelinePresenter(
                    accountType = currentAccountType,
                    initialQuery = query,
                )
            }
        val stateState =
            key(currentAccountType) {
                status.body().listState
            }
        LaunchedEffect(query) {
            status.setQuery(query)
        }

        val isSearching = query.isNotEmpty()

        return object : SearchState {
            override val users = user
            override val status = stateState
            override val searching = isSearching
            override val accounts = accounts
            override val selectedAccount = selectedAccount

            override fun search(new: String) {
                query = new
            }

            override suspend fun refreshSuspend() {
                user.refreshSuspend()
                stateState.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                selectedAccount = profile
            }
        }
    }
}

@Immutable
public interface SearchState {
    public val users: PagingState<UiUserV2>
    public val status: PagingState<UiTimeline>
    public val searching: Boolean
    public val accounts: UiState<ImmutableList<UiProfile>>
    public val selectedAccount: UiProfile?

    public fun search(new: String)

    public suspend fun refreshSuspend()

    public fun setAccount(profile: UiProfile)
}
