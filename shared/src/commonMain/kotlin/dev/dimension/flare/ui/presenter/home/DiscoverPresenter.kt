package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase

class DiscoverPresenter(
    private val accountType: AccountType,
) : PresenterBase<DiscoverState>() {
    @Composable
    override fun body(): DiscoverState {
        val scope = rememberCoroutineScope()
        val accountState = accountServiceProvider(accountType = accountType)
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
            accountState
                .flatMap { dataSource ->
                    remember(dataSource) {
                        val pagingKey =
                            if (dataSource is AuthenticatedMicroblogDataSource) {
                                "discover_status_${dataSource.accountKey}"
                            } else {
                                "discover"
                            }
                        runCatching {
                            dataSource.discoverStatuses(scope = scope, pagingKey = pagingKey)
                        }.getOrNull()
                    }?.collectAsLazyPagingItems().let {
                        if (it == null) {
                            UiState.Error(Throwable("No data"))
                        } else {
                            UiState.Success(it)
                        }
                    }
                }.toPagingState()
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
        }
    }
}

interface DiscoverState {
    val users: PagingState<UiUserV2>
    val status: PagingState<UiTimeline>
    val hashtags: PagingState<UiHashtag>
}
