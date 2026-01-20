package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.cachePagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiHashtag
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
public class DiscoverPresenter :
    PresenterBase<DiscoverState>(),
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

    private val selectedAccountFlow by lazy {
        MutableStateFlow<UiProfile?>(null)
    }

    private val selectedAccountTypeFlow: Flow<AccountType> by lazy {
        selectedAccountFlow.map { profile ->
            profile?.let {
                AccountType.Specific(it.key)
            } ?: AccountType.Guest
        }
    }

    private val usersFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository)
                .flatMapLatest { dataSource ->
                    runCatching {
                        dataSource.discoverUsers()
                    }.getOrElse { PagingData.emptyFlow(isError = true) }
                }
        }
    }

    private val hashtagsFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository)
                .flatMapLatest { dataSource ->
                    runCatching {
                        dataSource.discoverHashtags()
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
        val selectedAccount by selectedAccountFlow.collectAsState()
        val selectedAccountType by selectedAccountTypeFlow.collectAsState(AccountType.Guest)
        val users = usersFlow.cachePagingState()
        val hashtags = hashtagsFlow.cachePagingState()
        val status =
            remember {
                statusFlow(scope)
            }.collectAsLazyPagingItems().toPagingState()

        accounts.onSuccess {
            LaunchedEffect(it.size) {
                if (selectedAccountFlow.value == null) {
                    selectedAccountFlow.value = it.firstOrNull()
                }
            }
        }

        return object : DiscoverState {
            override val users = users
            override val status = status
            override val hashtags = hashtags
            override val selectedAccount = selectedAccount
            override val accounts = accounts
            override val selectedAccountType = selectedAccountType

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
                hashtags.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                selectedAccountFlow.value = profile
            }
        }
    }
}

@Immutable
public interface DiscoverState {
    public val users: PagingState<UiUserV2>
    public val status: PagingState<UiTimeline>
    public val hashtags: PagingState<UiHashtag>
    public val accounts: UiState<ImmutableList<UiProfile>>
    public val selectedAccount: UiProfile?
    public val selectedAccountType: AccountType

    public suspend fun refreshSuspend()

    public fun setAccount(profile: UiProfile)
}
