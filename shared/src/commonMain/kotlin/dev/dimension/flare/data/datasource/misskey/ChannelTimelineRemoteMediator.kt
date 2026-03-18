package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.ChannelsTimelineRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class ChannelTimelineRemoteMediator(
    private val service: MisskeyService,
    private val accountKey: MicroBlogKey,
    private val id: String,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey = "channel_${id}_$accountKey"

    override val supportPrepend: Boolean
        get() = true

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
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
                    service
                        .channelsTimeline(
                            channelsTimelineRequest =
                                ChannelsTimelineRequest(
                                    channelId = id,
                                    limit = pageSize,
                                    sinceId = request.previousKey,
                                ),
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
                response.render(accountKey),
            nextKey = response.lastOrNull()?.id,
            previousKey = response.firstOrNull()?.id,
        )
    }
}
