package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.ui.model.UiTimelineV2

@OptIn(ExperimentalPagingApi::class)
internal class TimelineRemoteMediator(
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
) : BasePagingRemoteMediator<DbPagingTimelineWithStatus, DbPagingTimelineWithStatus>(
        database = database,
    ),
    RemoteLoader<DbPagingTimelineWithStatus> {
    override val pagingKey: String
        get() = loader.pagingKey

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val result =
            timeline(
                pageSize = pageSize,
                request = request,
            )
        val data =
            result.data.map {
                TimelinePagingMapper.toDb(
                    data = it,
                    pagingKey = pagingKey,
                )
            }
        return PagingResult(
            data = data,
            nextKey = result.nextKey,
            previousKey = result.previousKey,
        )
    }

    suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        loader.load(
            pageSize = pageSize,
            request = request,
        )

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
