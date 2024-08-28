package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.cash.sqldelight.paging3.QueryPagingSource
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalPagingApi::class)
internal fun StatusEvent.timelinePager(
    pageSize: Int,
    pagingKey: String,
    accountKey: MicroBlogKey,
    database: CacheDatabase,
    scope: CoroutineScope,
    filterFlow: Flow<List<String>>,
    mediator: RemoteMediator<Int, DbPagingTimelineWithStatusView>,
): Flow<PagingData<UiTimeline>> {
    val pagerFlow =
        Pager(
            config = PagingConfig(pageSize = pageSize),
            remoteMediator = mediator,
            pagingSourceFactory = {
                QueryPagingSource(
                    countQuery =
                        database.dbPagingTimelineQueries.pageCount(
                            account_key = accountKey,
                            paging_key = pagingKey,
                        ),
                    transacter = database.dbPagingTimelineQueries,
                    context = Dispatchers.IO,
                    queryProvider = { limit, offset ->
                        database.dbPagingTimelineQueries.getPage(
                            account_key = accountKey,
                            paging_key = pagingKey,
                            offset = offset,
                            limit = limit,
                        )
                    },
                )
            },
        ).flow.cachedIn(scope)
    return combine(pagerFlow, filterFlow) { pagingData, filters ->
        pagingData
            .map {
                withContext(Dispatchers.IO) {
                    it.render(this@timelinePager)
                }
            }.filter {
                !it.contains(filters)
            }
    }.cachedIn(scope)
}

private fun UiTimeline.contains(keywords: List<String>): Boolean {
    val text =
        if (content is UiTimeline.ItemContent.Status) {
            listOfNotNull(
                content.content.raw,
                content.contentWarning,
            )
        } else {
            emptyList()
        }
    return keywords.any { keyword ->
        text.any { it.contains(keyword, ignoreCase = true) }
    }
}

internal class MemoryPagingSource<T : Any>(
    private val key: String,
    private val context: CoroutineContext,
) : PagingSource<Int, T>() {
    companion object {
        private val caches = mutableMapOf<String, MutableStateFlow<ImmutableList<Any>>>()

        fun <T : Any> update(
            key: String,
            value: ImmutableList<T>,
        ) {
            caches[key]?.value = value
        }

        fun <T : Any> append(
            key: String,
            value: ImmutableList<T>,
        ) {
            @Suppress("UNCHECKED_CAST")
            caches[key]?.value = ((caches[key]?.value as? ImmutableList<T> ?: persistentListOf()) + value).toImmutableList()
        }

        fun <T : Any> updateWith(
            key: String,
            update: (ImmutableList<T>) -> ImmutableList<T>,
        ) {
            @Suppress("UNCHECKED_CAST")
            val value = caches[key]?.value as? ImmutableList<T> ?: persistentListOf()
            caches[key]?.value = update(value)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> get(key: String): ImmutableList<T>? = caches[key]?.value as? ImmutableList<T>

        fun clear(key: String) {
            caches.remove(key)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getFlow(key: String): Flow<ImmutableList<T>> =
            caches
                .getOrPut(key) {
                    MutableStateFlow(persistentListOf<T>())
                } as Flow<ImmutableList<T>>
    }

    // TODO: workaround for skip first invalidation to avoid loading infinite loop
    private var skiped = false

    private val job =
        caches
            .getOrPut(key) {
                MutableStateFlow(persistentListOf<T>())
            }.let {
                CoroutineScope(context).launch {
                    it.collectLatest {
                        if (!skiped) {
                            skiped = true
                            return@collectLatest
                        }
                        invalidate()
                    }
                }
            }

    init {
        registerInvalidatedCallback {
            job.cancel()
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let {
            maxOf(0, it - (state.config.initialLoadSize / 2))
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0

        @Suppress("UNCHECKED_CAST")
        val list = caches[key]?.value as? ImmutableList<T> ?: return LoadResult.Error(Exception("No data"))
        val data = list.subList(page, (page + params.loadSize).coerceIn(0, list.size))
        val prevKey = (page - params.loadSize).takeIf { it in list.indices }
        val nextKey = (page + params.loadSize).takeIf { it in list.indices }
        return LoadResult.Page(
            data = data,
            prevKey = prevKey,
            nextKey = nextKey,
        )
    }
}

@OptIn(ExperimentalPagingApi::class)
internal fun <T : Any> memoryPager(
    pageSize: Int,
    pagingKey: String,
    scope: CoroutineScope,
    mediator: RemoteMediator<Int, T>,
): Flow<PagingData<T>> =
    Pager(
        config = PagingConfig(pageSize = pageSize),
        remoteMediator = mediator,
        pagingSourceFactory = {
            MemoryPagingSource(
                key = pagingKey,
                context = Dispatchers.IO,
            )
        },
    ).flow.cachedIn(scope)
