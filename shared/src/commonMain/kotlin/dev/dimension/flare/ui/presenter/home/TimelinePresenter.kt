package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
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
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    protected fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimeline>> =
        loader
            .catch {
                emit(BaseTimelineLoader.NotSupported)
            }.flatMapLatest {
                when (it) {
                    is BaseTimelinePagingSource<*> ->
                        networkPager(
                            pagingSource = it,
                            scope = scope,
                        )

                    is BaseTimelineRemoteMediator ->
                        cachePager(
                            mediator = it,
                            scope = scope,
                        )

                    BaseTimelineLoader.NotSupported ->
                        flowOf(
                            PagingData.empty(
                                sourceLoadStates =
                                    LoadStates(
                                        refresh = LoadState.Error(NotImplementedError()),
                                        prepend = LoadState.Error(NotImplementedError()),
                                        append = LoadState.Error(NotImplementedError()),
                                    ),
                                mediatorLoadStates =
                                    LoadStates(
                                        refresh = LoadState.Error(NotImplementedError()),
                                        prepend = LoadState.Error(NotImplementedError()),
                                        append = LoadState.Error(NotImplementedError()),
                                    ),
                            ),
                        )
                }.combine(filterFlow) { pager, filterList ->
                    pager.filter {
                        !it.contains(filterList)
                    }
                }
            }

    private fun cachePager(
        mediator: BaseTimelineRemoteMediator,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiTimeline>> {
        val pagerFlow =
            Pager(
                config = pagingConfig,
                remoteMediator = mediator,
                pagingSourceFactory = {
                    database.pagingTimelineDao().getPagingSource(
                        pagingKey = mediator.pagingKey,
                    )
                },
            ).flow.cachedIn(scope)
        return combine(
            pagerFlow,
            accountRepository.allAccounts,
        ) { pagingData, accounts ->
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
                        data.render(dataSource, useDbKeyInItemKey)
                    }
                }
        }
    }

    private fun networkPager(
        pagingSource: BaseTimelinePagingSource<*>,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiTimeline>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                pagingSource
            },
        ).flow.cachedIn(scope)

    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val listState =
            remember {
                createPager(scope)
                    .cachedIn(scope)
            }.collectAsLazyPagingItems()
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
    protected open val useDbKeyInItemKey: Boolean = false
}

@Immutable
public interface TimelineState {
    public val listState: PagingState<UiTimeline>

    public suspend fun refresh()
}
