package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
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
        var type by remember { mutableStateOf<NotificationFilter?>(null) }
        val serviceState =
            accountServiceProvider(accountType = accountType).map {
                require(it is AuthenticatedMicroblogDataSource)
                it
            }
        val allTypes =
            serviceState.map { service ->
                service.supportedNotificationFilter.toImmutableList()
            }
        serviceState.onSuccess {
            LaunchedEffect(it) {
                type = it.supportedNotificationFilter.firstOrNull()
            }
        }
        val listState =
            serviceState
                .flatMap { service ->
                    val currentType = type
                    if (service.supportedNotificationFilter.isEmpty() ||
                        currentType == null ||
                        currentType !in service.supportedNotificationFilter
                    ) {
                        UiState.Error(IllegalStateException("No supported notification filter"))
                    } else {
                        UiState.Success(
                            remember(service, currentType) {
                                val pagingKey = "notification_${currentType}_${service.accountKey}"
                                service.notification(
                                    type = currentType,
                                    scope = scope,
                                    pagingKey = pagingKey,
                                )
                            }.collectAsLazyPagingItems(),
                        )
                    }
                }.toPagingState()
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
                    refreshSuspend()
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
    val listState: PagingState<UiTimeline>,
    val notificationType: NotificationFilter?,
    val allTypes: UiState<ImmutableList<NotificationFilter>>,
) {
    abstract suspend fun refresh()

    abstract fun onNotificationTypeChanged(value: NotificationFilter)
}
