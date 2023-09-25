package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.LoadState
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.data.datasource.NotificationFilter
import dev.dimension.flare.data.repository.activeAccountServicePresenter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase

class NotificationPresenter : PresenterBase<NotificationState>() {

    @Composable
    override fun body(): NotificationState {
        var type by remember { mutableStateOf(NotificationFilter.All) }
        val allTypes = activeAccountServicePresenter().map { (service, _) ->
            service.supportedNotificationFilter
        }
        val listState = activeAccountServicePresenter().map { (service, account) ->
            remember(account.accountKey, type) {
                service.notification(
                    type = type,
                )
            }.collectAsLazyPagingItems()
        }
        val refreshing =
            listState is UiState.Loading ||
                    listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0

        return object : NotificationState(
            refreshing,
            listState,
            type,
            allTypes,
        ) {
            override fun refresh() {
                listState.onSuccess {
                    it.refresh()
                }
            }

            override fun onNotificationTypeChanged(value: NotificationFilter) {
                type = value
            }
        }
    }
}

@Immutable
abstract class NotificationState(
    val refreshing: Boolean = false,
    val listState: UiState<LazyPagingItems<UiStatus>>,
    val notificationType: NotificationFilter,
    val allTypes: UiState<List<NotificationFilter>>,
) {
    abstract fun refresh()
    abstract fun onNotificationTypeChanged(value: NotificationFilter)
}