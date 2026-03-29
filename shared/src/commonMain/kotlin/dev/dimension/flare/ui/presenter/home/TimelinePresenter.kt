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
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.cachePagingState
import dev.dimension.flare.common.emptyFlow
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.NotSupportRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.TranslationSettingsSupport
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
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
    private val appDataStore: AppDataStore by inject()
    private val preTranslationService: PreTranslationService by inject()

    private val localFilterRepository: LocalFilterRepository by inject()
    private val inAppNotification: InAppNotification by inject()

    private val filterFlow: Flow<List<String>> by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    private val translationSettingsFlow: Flow<TranslationDisplayOptions> by lazy {
        TranslationSettingsSupport.displayOptionsFlow(appDataStore)
    }

    internal open fun allowLongTextTranslationDisplay(loader: RemoteLoader<UiTimelineV2>): Boolean = false

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimelineV2>> =
        loader
            .flatMapLatest { remoteLoader ->
                when (remoteLoader) {
                    is NotSupportRemoteLoader<UiTimelineV2> -> {
                        PagingData.emptyFlow(isError = false)
                    }

                    is CacheableRemoteLoader<UiTimelineV2> -> {
                        cachePager(
                            loader = remoteLoader,
                        ).cachedIn(scope).flatMapLatest { pagingData ->
                            translationSettingsFlow
                                .map { translationDisplayOptions ->
                                    withContext(Dispatchers.IO) {
                                        pagingData.map { item ->
                                            TimelinePagingMapper.toUi(
                                                item = item,
                                                pagingKey = remoteLoader.pagingKey,
                                                useDbKeyInItemKey = useDbKeyInItemKey,
                                                translationDisplayOptions = translationDisplayOptions,
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    else -> {
                        networkPager(
                            loader = remoteLoader,
                        ).cachedIn(scope)
                    }
                }.flatMapLatest { pager ->
                    filterFlow.map { filterList ->
                        pager
                            .filter { item ->
                                if (filterList.isEmpty()) {
                                    true
                                } else {
                                    !filterList.any { filter ->
                                        item.searchText
                                            .orEmpty()
                                            .contains(filter, ignoreCase = true)
                                    }
                                }
                            }.map {
                                transform(it)
                            }
                    }
                }
            }.catch {
                emitAll(PagingData.emptyFlow(isError = true))
            }

    private fun cachePager(loader: CacheableRemoteLoader<UiTimelineV2>): Flow<PagingData<DbPagingTimelineWithStatus>> =
        run {
            val allowLongText = allowLongTextTranslationDisplay(loader)
            Pager(
                config = pagingConfig,
                remoteMediator =
                    TimelineRemoteMediator(
                        loader = loader,
                        database = database,
                        allowLongText = allowLongText,
                        preTranslationService = preTranslationService,
                        notifyError = { e ->
                            if (e is LoginExpiredException) {
                                inAppNotification.onError(Message.LoginExpired, e)
                            }
                        },
                    ),
                pagingSourceFactory = {
                    database.pagingTimelineDao().getPagingSource(
                        pagingKey = loader.pagingKey,
                    )
                },
            ).flow
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
