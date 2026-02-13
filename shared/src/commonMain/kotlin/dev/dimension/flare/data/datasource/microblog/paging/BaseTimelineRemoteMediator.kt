package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.ui.model.UiTimeline

internal sealed interface BaseTimelineLoader {
    data object NotSupported : BaseTimelineLoader
}

internal fun interface BaseTimelinePagingSourceFactory<T : Any> : BaseTimelineLoader {
    abstract fun create(): BasePagingSource<T, UiTimeline>
}

@OptIn(ExperimentalPagingApi::class)
internal abstract class BaseTimelineRemoteMediator(
    private val database: CacheDatabase,
) : BasePagingRemoteMediator<DbPagingTimelineWithStatus, DbPagingTimelineWithStatus>(
        database = database,
    ),
    BaseTimelineLoader {
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> =
        timeline(
            pageSize = pageSize,
            request = request,
        )

    abstract suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus>

    override suspend fun onSaveCache(
        request: PagingRequest,
        data: List<DbPagingTimelineWithStatus>,
    ) {
        if (request is PagingRequest.Refresh) {
            data.groupBy { it.timeline.pagingKey }.keys.forEach { key ->
                database
                    .pagingTimelineDao()
                    .delete(pagingKey = key)
            }
        }
        saveToDatabase(database, data)
    }
}
