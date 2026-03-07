package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class ListTimelineRemoteMediator(
    private val listId: String,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "list_${listId}_$accountKey"

    @Serializable
    data class Request(
        @SerialName("listId")
        val listID: String? = null,
        val count: Long? = null,
        val cursor: String? = null,
    )

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getListLatestTweetsTimeline(
                        variables =
                            Request(
                                listID = listId,
                                count = pageSize.toLong(),
                            ).encodeJson(),
                    )
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    service.getListLatestTweetsTimeline(
                        variables =
                            Request(
                                listID = listId,
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                    )
                }
            }.body()?.data?.list?.tweetsTimeline?.timeline?.instructions.orEmpty()
        val result = response.tweets()

        return PagingResult(
            endOfPaginationReached = response.isEmpty(),
            data = result.mapNotNull { it.render(accountKey) },
            nextKey = response.cursor(),
        )
    }
}
