package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.ChannelsTimelineRequest
import dev.dimension.flare.model.MicroBlogKey

internal class ChannelTimelineRemoteMediator(
    private val service: MisskeyService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val id: String,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "channel_${id}_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<DbPagingTimelineWithStatus> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .channelsTimeline(
                            channelsTimelineRequest =
                                ChannelsTimelineRequest(
                                    channelId = id,
                                    limit = pageSize,
                                ),
                        )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service
                        .channelsTimeline(
                            channelsTimelineRequest =
                                ChannelsTimelineRequest(
                                    channelId = id,
                                    limit = pageSize,
                                    untilId = request.nextKey,
                                ),
                        )
                }
            }

        return PagingResult(
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
