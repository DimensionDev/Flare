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
import dev.dimension.flare.data.repository.AccountRepository
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class NotificationPresenter(
    private val accountType: AccountType,
) : PresenterBase<NotificationState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): NotificationState {
        val scope = rememberCoroutineScope()
        var type by remember { mutableStateOf<NotificationFilter?>(null) }
        val serviceState =
            accountServiceProvider(accountType = accountType, repository = accountRepository).map {
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
                                service.notification(
                                    type = currentType,
                                    scope = scope,
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
public abstract class NotificationState(
    public val listState: PagingState<UiTimeline>,
    public val notificationType: NotificationFilter?,
    public val allTypes: UiState<ImmutableList<NotificationFilter>>,
) {
    public abstract suspend fun refresh()

    public abstract fun onNotificationTypeChanged(value: NotificationFilter)
}
