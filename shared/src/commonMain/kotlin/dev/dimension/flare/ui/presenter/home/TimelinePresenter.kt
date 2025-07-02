package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.map
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.BaseTimelinePagingSource
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.contains
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
public abstract class TimelinePresenter :
    PresenterBase<TimelineState>(),
    KoinComponent {

    private val database: CacheDatabase by inject()

    private val accountRepository: AccountRepository by inject()

    private val localFilterRepository: LocalFilterRepository by inject()

    private val filterFlow by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pager by lazy {
        loader.flatMapLatest {
            when (it) {
                is BaseTimelinePagingSource<*> ->
                    networkPager(
                        pagingSource = it,
                    )

                is BaseTimelineRemoteMediator ->
                    cachePager(
                        mediator = it,
                    )
            }
        }
    }

    private fun cachePager(
        mediator: BaseTimelineRemoteMediator,
        pageSize: Int = 20,
    ): Flow<PagingData<UiTimeline>> {
        val pagerFlow = Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = mediator,
            pagingSourceFactory = {
                database.pagingTimelineDao().getPagingSource(
                    pagingKey = mediator.pagingKey,
                )
            },
        ).flow

        return combine(
            pagerFlow,
            filterFlow,
            accountRepository.allAccounts,
        ) { pagingData, filters, accounts ->
            pagingData
                .map { data ->
                    withContext(Dispatchers.IO) {
                        val dataSource =
                            when (data.timeline.accountType) {
                                AccountType.Guest -> null
                                is AccountType.Specific -> {
                                    accounts.first {
                                        it.accountKey == data.timeline.accountType.accountKey
                                    }
                                }
                            }?.dataSource
                        data.render(dataSource)
                    }
                }.filter {
                    !it.contains(filters)
                }
        }
    }

    private fun networkPager(
        pagingSource: BaseTimelinePagingSource<*>,
        pageSize: Int = 20,
    ): Flow<PagingData<UiTimeline>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
            pagingSourceFactory = {
                pagingSource
            },
        ).flow
    }

    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val listState = remember {
            pager
                .cachedIn(scope)
        }
            .collectAsLazyPagingItems()
            .toPagingState()

        return object : TimelineState {
            override val listState = listState

            override suspend fun refresh() {
                listState
                    .onSuccess {
                        refreshSuspend()
                    }.onEmpty {
                        refresh()
                    }.onError {
                        onRetry()
                    }
            }
        }
    }

    internal abstract val loader: Flow<BaseTimelineLoader>
}

@Immutable
public interface TimelineState {
    public val listState: PagingState<UiTimeline>

    public suspend fun refresh()
}
