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
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.cachePagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
public abstract class TimelinePresenter :
    PresenterBase<TimelineState>(),
    KoinComponent {
    private val database: CacheDatabase by inject()

    private val localFilterRepository: LocalFilterRepository by inject()

    private val filterFlow by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimelineV2>> =
        loader
            .flatMapLatest {
                if (it is CacheableRemoteLoader<UiTimelineV2>) {
                    cachePager(
                        loader = it,
                    ).cachedIn(scope)
                } else {
                    networkPager(
                        loader = it,
                    ).cachedIn(scope)
                }.flatMapLatest { pager ->
                    filterFlow.map { filterList ->
                        pager.filter { item ->
                            filterList.any { filter ->
                                item.searchText.orEmpty().contains(filter, ignoreCase = true)
                            }
                        }
                    }
                }
            }.catch {
                emitAll(PagingData.emptyFlow(isError = true))
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun cachePager(loader: CacheableRemoteLoader<UiTimelineV2>): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = database,
                ),
            pagingSourceFactory = {
                database.pagingTimelineDao().getPagingSource(
                    pagingKey = loader.pagingKey,
                )
            },
        ).flow.map { pagingData ->
            pagingData.map { item ->
                mapping(item, loader.pagingKey, useDbKeyInItemKey)
            }
        }

    internal companion object {
        fun mapping(
            item: DbPagingTimelineWithStatus,
            pagingKey: String,
            useDbKeyInItemKey: Boolean,
        ): UiTimelineV2 {
            val root = mappingDbStatusWithUser(item.status.status, pagingKey, useDbKeyInItemKey)
            val references =
                item.status.references
                    .mapNotNull { it.status }
                    .map { mappingDbStatusWithUser(it, pagingKey, useDbKeyInItemKey) }
            return when (root) {
                is UiTimelineV2.Feed -> root
                is UiTimelineV2.Message -> root
                is UiTimelineV2.Post ->
                    root.copy(
                        parents =
                            root.parents
                                .map { parent ->
                                    references.find { it.statusKey == parent.statusKey } as? UiTimelineV2.Post ?: parent
                                }.toImmutableList(),
                        quote =
                            root.quote
                                .map { quote ->
                                    references.find { it.statusKey == quote.statusKey } as? UiTimelineV2.Post ?: quote
                                }.toImmutableList(),
                    )
                is UiTimelineV2.User -> root
                is UiTimelineV2.UserList ->
                    root.copy(
                        post =
                            root.post?.let { post ->
                                references.find { it.statusKey == post.statusKey } as? UiTimelineV2.Post ?: post
                            },
                    )
            }
        }

        private fun mappingDbStatusWithUser(
            data: DbStatusWithUser,
            pagingKey: String,
            useDbKeyInItemKey: Boolean,
        ): UiTimelineV2 {
            val root = data.data.content
            val users = data.references.mapNotNull { it.user?.content }
            return when (root) {
                is UiTimelineV2.Feed -> root
                is UiTimelineV2.Message ->
                    root.copy(
                        user = users.find { root.user?.key == it.key },
                        extraKey = if (useDbKeyInItemKey) pagingKey else null,
                    )
                is UiTimelineV2.Post ->
                    root.copy(
                        user = users.find { root.user?.key == it.key },
                        extraKey = if (useDbKeyInItemKey) pagingKey else null,
                    )
                is UiTimelineV2.User ->
                    root.copy(
                        value = users.find { root.value.key == it.key } ?: root.value,
                        extraKey = if (useDbKeyInItemKey) pagingKey else null,
                    )
                is UiTimelineV2.UserList ->
                    root.copy(
                        users =
                            root.users
                                .map { user ->
                                    users.find { user.key == it.key } ?: user
                                }.toImmutableList(),
                        extraKey = if (useDbKeyInItemKey) pagingKey else null,
                    )
            }
        }
    }

    protected open suspend fun transform(data: UiTimelineV2): UiTimelineV2 = data

    private fun networkPager(loader: RemoteLoader<UiTimelineV2>): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                loader.toPagingSource()
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

    internal abstract val loader: Flow<RemoteLoader<UiTimelineV2>>
    protected open val useDbKeyInItemKey: Boolean = false
}

@Immutable
public interface TimelineState {
    public val listState: PagingState<UiTimelineV2>

    public suspend fun refresh()
}
