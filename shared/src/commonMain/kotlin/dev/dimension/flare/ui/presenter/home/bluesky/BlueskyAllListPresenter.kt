package dev.dimension.flare.ui.presenter.home.bluesky

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.paging.LoadState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class BlueskyAllListPresenter(
    private val accountType: AccountType,
) : PresenterBase<BlueskyAllListState>() {
    @Composable
    override fun body(): BlueskyAllListState {
        val serviceState = accountServiceProvider(accountType = accountType)
        val items =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is BlueskyDataSource)
                        service.myList
                    }
                }
        val refreshState =
            items
                .flatMap {
                    it.refreshState.collectAsUiState().value
                }.map {
                    it == LoadState.Loading
                }
        val isRefreshing = refreshState is UiState.Loading || refreshState is UiState.Success && refreshState.data
        return object : BlueskyAllListState {
            override val items =
                items.toPagingState()

            override val isRefreshing = isRefreshing

            override fun refresh() {
                items.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}

@Immutable
interface BlueskyAllListState {
    val items: PagingState<UiList>
    val isRefreshing: Boolean

    fun refresh()
}
