package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusContextState>() {
    @Composable
    override fun body(): StatusContextState {
        val scope = rememberCoroutineScope()
        val listState =
            accountServiceProvider(accountType = accountType).map { service ->
                remember(service, statusKey) {
                    service.context(statusKey, scope = scope)
                }.collectAsLazyPagingItems()
            }
        return object : StatusContextState(
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

abstract class StatusContextState(
    val listState: UiState<LazyPagingItems<UiStatus>>,
) {
    abstract suspend fun refresh()
}
