package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import dev.dimension.flare.di.koinInject

public class AccountNotificationPresenter(
    private val accountKey: MicroBlogKey,
    initialFilter: NotificationFilter = NotificationFilter.All,
) : PresenterBase<AccountNotificationPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()
    private val selectedNotificationFilterFlow = MutableStateFlow(initialFilter)

    @Immutable
    public interface State {
        public val supportedNotificationFilters: UiState<ImmutableList<NotificationFilter>>
        public val timeline: PagingState<UiTimelineV2>
        public val selectedFilter: NotificationFilter

        public fun setFilter(filter: NotificationFilter)

        public suspend fun refreshSuspend()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val notificationFiltersFlow by lazy {
        accountServiceFlow(
            accountType = AccountType.Specific(accountKey),
            repository = accountRepository,
        ).map {
            require(it is NotificationTimelineDataSource)
            it.supportedNotificationFilter.toImmutableList()
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                selectedNotificationFilterFlow
                    .flatMapLatest { filter ->
                        accountServiceFlow(
                            accountType = AccountType.Specific(accountKey),
                            repository = accountRepository,
                        ).map {
                            require(it is NotificationTimelineDataSource)
                            it.notification(filter)
                        }.distinctUntilChanged()
                    }
            }
        }
    }

    @Composable
    override fun body(): State {
        val selectedNotificationFilter by selectedNotificationFilterFlow.collectAsState()
        val notificationFilters by notificationFiltersFlow.collectAsUiState()

        notificationFilters.onSuccess { filters ->
            LaunchedEffect(filters) {
                val currentFilter = selectedNotificationFilterFlow.value
                if (filters.isNotEmpty() && currentFilter !in filters) {
                    selectedNotificationFilterFlow.value = filters.first()
                }
            }
        }

        val listState = timelinePresenter.body().listState

        return object : State {
            override val supportedNotificationFilters = notificationFilters
            override val timeline = listState
            override val selectedFilter = selectedNotificationFilter

            override fun setFilter(filter: NotificationFilter) {
                selectedNotificationFilterFlow.value = filter
            }

            override suspend fun refreshSuspend() {
                listState.refreshSuspend()
            }
        }
    }
}
