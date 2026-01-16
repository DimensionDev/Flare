package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiHashtag
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

    @Composable
    override fun body(): DiscoverState {
        val accounts by accountsFlow.collectAsUiState()
        var selectedAccount by remember {
            mutableStateOf<UiProfile?>(null)
        }
        accounts.onSuccess {
            LaunchedEffect(it.size) {
                selectedAccount = it.firstOrNull()
            }
        }
        val selectedAccountType =
            remember(
                selectedAccount,
            ) {
                selectedAccount?.let {
                    AccountType.Specific(it.key)
                } ?: AccountType.Guest
            }
        val accountState = accountServiceProvider(accountType = selectedAccountType, repository = accountRepository)
        val users =
            accountState
                .flatMap { dataSource ->
                    remember(dataSource) {
                        runCatching {
                            dataSource.discoverUsers()
                        }.getOrNull()
                    }?.collectAsLazyPagingItems().let {
                        if (it == null) {
                            UiState.Error(Throwable("No data"))
                        } else {
                            UiState.Success(it)
                        }
                    }
                }.toPagingState()
        val status =
            remember(
                selectedAccountType,
            ) {
                DiscoverStatusTimelinePresenter(selectedAccountType)
            }.body().listState
        val hashtags =
            accountState
                .flatMap { dataSource ->
                    remember(dataSource) {
                        runCatching {
                            dataSource.discoverHashtags()
                        }.getOrNull()
                    }?.collectAsLazyPagingItems().let {
                        if (it == null) {
                            UiState.Error(Throwable("No data"))
                        } else {
                            UiState.Success(it)
                        }
                    }
                }.toPagingState()

        return object : DiscoverState {
            override val users = users
            override val status = status
            override val hashtags = hashtags
            override val selectedAccount = selectedAccount
            override val accounts = accounts

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
                hashtags.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                selectedAccount = profile
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

    public suspend fun refreshSuspend()

    public fun setAccount(profile: UiProfile)
}
