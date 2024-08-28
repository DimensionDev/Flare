package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.LoadState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import kotlinx.collections.immutable.toImmutableList

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
                        require(service is MastodonDataSource)
                        service.allLists()
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
                items
                    .flatMap {
                        it.collectAsState().toUi()
                    }.map {
                        it.toImmutableList().toImmutableListWrapper()
                    }

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
    val items: UiState<ImmutableListWrapper<UiList>>
    val isRefreshing: Boolean

    fun refresh()
}
