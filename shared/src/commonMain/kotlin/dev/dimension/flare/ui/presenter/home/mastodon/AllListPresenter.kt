package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch

class AllListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListState>() {
    @Composable
    override fun body(): AllListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        val items =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is MastodonDataSource)
                        service.allLists(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : AllListState {
            override val items = items

            override fun refresh() {
                scope.launch {
                    items.refreshSuspend()
                }
            }
        }
    }
}

@Immutable
interface AllListState {
    val items: PagingState<UiList>

    fun refresh()
}
