package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DiscoverPresenter(
    private val accountType: AccountType,
) : PresenterBase<DiscoverState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): DiscoverState {
        val scope = rememberCoroutineScope()
        val accountState = accountServiceProvider(accountType = accountType, repository = accountRepository)
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
                accountType,
            ) {
                DiscoverStatusTimelinePresenter(accountType)
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

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
                hashtags.refreshSuspend()
            }
        }
    }
}

@Immutable
public interface DiscoverState {
    public val users: PagingState<UiUserV2>
    public val status: PagingState<UiTimeline>
    public val hashtags: PagingState<UiHashtag>

    public suspend fun refreshSuspend()
}
