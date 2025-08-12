package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AntennasNotesRequest
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class AntennasTimelineRemoteMediator(
    private val service: MisskeyService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val id: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "antennas_${id}_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = pageSize,
                            ),
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
                    service
                        .antennasNotes(
                            AntennasNotesRequest(
                                antennaId = id,
                                limit = pageSize,
                                untilId = request.nextKey,
                            ),
                        )
                }
            }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data =
                response.toDbPagingTimeline(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                ),
            nextKey = response.lastOrNull()?.id,
        )
    }
}
