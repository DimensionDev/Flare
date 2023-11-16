package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class StatusPresenter(
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusState>() {
    @Composable
    override fun body(): StatusState {
        val listState =
            activeAccountServicePresenter().map { (service, account) ->
                remember(account.accountKey, statusKey) {
                    service.context(statusKey)
                }.collectAsLazyPagingItems()
            }
//        val refreshing =
//            listState is UiState.Loading ||
//                listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0

        return object : StatusState(
            listState,
        ) {
            override suspend fun refresh() {
                listState.onSuccess {
                    it.refreshSuspend()
                }
            }
        }
    }
}

abstract class StatusState(
    val listState: UiState<LazyPagingItems<UiStatus>>,
) {
    abstract suspend fun refresh()
}
