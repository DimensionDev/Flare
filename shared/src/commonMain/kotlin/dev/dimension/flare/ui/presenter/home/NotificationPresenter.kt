package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class NotificationPresenter(
    private val accountType: AccountType,
) : PresenterBase<NotificationState>() {
    @Composable
    override fun body(): NotificationState {
        val scope = rememberCoroutineScope()
        var type by remember { mutableStateOf(NotificationFilter.All) }
        val serviceState = accountServiceProvider(accountType = accountType)
        val allTypes =
            serviceState.map { service ->
                service.supportedNotificationFilter.toImmutableList()
            }
        val listState =
            serviceState.map { service ->
                remember(service, type) {
                    service.notification(
                        type = type,
                        scope = scope,
                    )
                }.collectPagingProxy()
            }
//        val refreshing =
//            listState is UiState.Loading ||
//                listState is UiState.Success && listState.data.loadState.refresh is LoadState.Loading && listState.data.itemCount != 0

        return object : NotificationState(
            listState,
            type,
            allTypes,
        ) {
            override suspend fun refresh() {
                listState.onSuccess {
                    it.refreshSuspend()
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
    val listState: UiState<LazyPagingItemsProxy<UiStatus>>,
    val notificationType: NotificationFilter,
    val allTypes: UiState<ImmutableList<NotificationFilter>>,
) {
    abstract suspend fun refresh()

    abstract fun onNotificationTypeChanged(value: NotificationFilter)
}
