package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.cachePagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val queryFlow by lazy {
        MutableStateFlow(initialQuery)
    }

    private val selectedAccountFlow by lazy {
        MutableStateFlow<UiProfile?>(null)
    }

    private val selectedAccountTypeFlow: Flow<AccountType> by lazy {
        selectedAccountFlow.map { profile ->
            profile?.let {
                AccountType.Specific(it.key)
            } ?: accountType
        }
    }

    private val usersFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository).flatMapLatest { dataSource ->
                queryFlow.flatMapLatest { query ->
                    runCatching {
                        if (query.isEmpty()) {
                            PagingData.emptyFlow(isError = true)
                        } else {
                            dataSource.searchUser(query)
                        }
                    }.getOrElse { PagingData.emptyFlow(isError = true) }
                }
            }
        }
    }

    private fun statusFlow(scope: CoroutineScope) =
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            SearchStatusTimelinePresenter(
                accountType = accountType,
                queryFlow = queryFlow,
            ).createPager(
                scope = scope,
            )
        }

    @Composable
    override fun body(): SearchState {
        val scope = rememberCoroutineScope()
        val users = usersFlow.cachePagingState()
        val status =
            remember {
                statusFlow(scope)
            }.cachePagingState()
        val accounts by accountsFlow.collectAsUiState()
        val query by queryFlow.collectAsState()
        val selectedAccount by selectedAccountFlow.collectAsState()

        accounts.onSuccess {
            LaunchedEffect(it.size) {
                if (selectedAccount == null) {
                    selectedAccountFlow.value =
                        if (accountType is AccountType.Specific) {
                            it.find { profile -> profile.key == accountType.accountKey }
                        } else {
                            null
                        }
                }
            }
        }

        return object : SearchState {
            override val users = users
            override val status = status
            override val searching = query.isNotEmpty()
            override val accounts = accounts
            override val selectedAccount = selectedAccount

            override fun search(new: String) {
                queryFlow.value = new
            }

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                selectedAccountFlow.value = profile
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
