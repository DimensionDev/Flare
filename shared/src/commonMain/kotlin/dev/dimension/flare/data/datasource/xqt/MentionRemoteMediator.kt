package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

@OptIn(ExperimentalPagingApi::class)
internal class MentionRemoteMediator(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "mention_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getNotificationsMentions(
                        count = pageSize,
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getNotificationsMentions(
                        count = pageSize,
                        cursor = request.nextKey,
                    )
                }
            }
        val tweets = response.tweets()

        return PagingResult(
            endOfPaginationReached = tweets.isEmpty(),
            data = tweets.mapNotNull { it.render(accountKey) },
            nextKey = response.cursor(),
        )
    }
}
