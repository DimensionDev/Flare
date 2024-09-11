package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.paging.LoadState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class AllListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListState>() {
    @Composable
    override fun body(): AllListState {
        val serviceState = accountServiceProvider(accountType = accountType)
        val items =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is ListDataSource)
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
        return object : AllListState {
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
interface AllListState {
    val items: PagingState<UiList>
    val isRefreshing: Boolean

    fun refresh()
}
