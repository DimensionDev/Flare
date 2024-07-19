package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUser
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
            accountState.flatMap { dataSource ->
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
            }
        val status =
            accountState.flatMap { dataSource ->
                remember(dataSource) {
                    runCatching {
                        dataSource.discoverStatuses(scope = scope)
                    }.getOrNull()
                }?.collectAsLazyPagingItems().let {
                    if (it == null) {
                        UiState.Error(Throwable("No data"))
                    } else {
                        UiState.Success(it)
                    }
                }
            }
        val hashtags =
            accountState.flatMap { dataSource ->
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
            }

        return object : DiscoverState {
            override val users = users
            override val status = status
            override val hashtags = hashtags
        }
    }
}

interface DiscoverState {
    val users: UiState<LazyPagingItems<UiUser>>
    val status: UiState<LazyPagingItems<UiTimeline>>
    val hashtags: UiState<LazyPagingItems<UiHashtag>>
}
