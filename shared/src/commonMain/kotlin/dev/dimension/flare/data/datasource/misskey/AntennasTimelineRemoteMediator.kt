package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Misskey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AntennasNotesRequest
import dev.dimension.flare.model.MicroBlogKey

internal class AntennasTimelineRemoteMediator(
    private val service: MisskeyService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val id: String,
    private val pagingKey: String,
) : BaseRemoteMediator<Int, DbPagingTimelineWithStatus>() {
    @OptIn(ExperimentalPagingApi::class)
    override suspend fun doLoad(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = state.config.pageSize,
                            ),
                        ).also {
                            database.pagingTimelineDao().delete(pagingKey = pagingKey, accountKey = accountKey)
                        }
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    val lastItem =
                        database.pagingTimelineDao().getLastPagingTimeline(pagingKey)
                            ?: return MediatorResult.Success(
                                endOfPaginationReached = true,
                            )
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = state.config.pageSize,
                                untilId = lastItem.timeline.statusKey.id,
                            ),
                        )
                }
            }

        Misskey.save(
            database = database,
            accountKey = accountKey,
            pagingKey = pagingKey,
            data = response,
        )

        return MediatorResult.Success(
            endOfPaginationReached = response.isEmpty(),
        )
    }
}
