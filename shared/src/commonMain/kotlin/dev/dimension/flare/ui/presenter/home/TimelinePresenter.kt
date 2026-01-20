package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.BaseTimelinePagingSourceFactory
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.cachePagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

//    internal val pagerFlow: Flow<PagingData<UiTimeline>> by lazy {
//        createPager()
//    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimeline>> =
        loader
            .catch {
                emit(BaseTimelineLoader.NotSupported)
            }.flatMapLatest {
                when (it) {
                    is BaseTimelinePagingSourceFactory<*> ->
                        networkPager(
                            pagingSource = it,
                        ).cachedIn(scope)

                    is BaseTimelineRemoteMediator ->
                        cachePager(
                            mediator = it,
                            scope = scope,
                        )

                    BaseTimelineLoader.NotSupported -> PagingData.emptyFlow(isError = true)
                }.flatMapLatest { pager ->
                    filterFlow.map { filterList ->
                        pager.filter {
                            !it.contains(filterList)
                        }
                    }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun cachePager(
        mediator: BaseTimelineRemoteMediator,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        Pager(
            config = pagingConfig,
            remoteMediator = mediator,
            pagingSourceFactory = {
                database.pagingTimelineDao().getPagingSource(
                    pagingKey = mediator.pagingKey,
                )
            },
        ).flow
            .cachedIn(scope)
            .flatMapLatest { pagingData ->
                accountRepository.allAccounts.map { accounts ->
                    pagingData
                        .map { data ->
                            withContext(Dispatchers.IO) {
                                val dataSource =
                                    when (data.timeline.accountType) {
                                        AccountType.Guest -> null
                                        is AccountType.Specific -> {
                                            accounts.firstOrNull {
                                                it.accountKey == data.timeline.accountType.accountKey
                                            }
                                        }
                                    }?.dataSource
                                data
                                    .render(dataSource, useDbKeyInItemKey)
                                    .let { transform(it) }
                            }
                        }
                }
            }

    protected open suspend fun transform(data: UiTimeline): UiTimeline = data

    private fun networkPager(pagingSource: BaseTimelinePagingSourceFactory<*>): Flow<PagingData<UiTimeline>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                pagingSource.create()
            },
        ).flow

    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val listState =
            remember {
                createPager(scope)
            }.cachePagingState()
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
