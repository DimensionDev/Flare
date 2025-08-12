package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.BaseTimelineRemoteMediator.Request
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class ListTimelineRemoteMediator(
    private val listId: String,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "list_${listId}_$accountKey"

    @Serializable
    data class Request(
        @SerialName("listId")
        val listID: String? = null,
        val count: Long? = null,
        val cursor: String? = null,
    )

    override suspend fun timeline(
        pageSize: Int,
        request: BaseTimelineRemoteMediator.Request,
    ): Result {
        val response =
            when (request) {
                BaseTimelineRemoteMediator.Request.Refresh -> {
                    service
                        .getListLatestTweetsTimeline(
                            variables =
                                Request(
                                    listID = listId,
                                    count = pageSize.toLong(),
                                ).encodeJson(),
                        )
                }

                is Request.Prepend -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                is Request.Append -> {
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

        val data = result.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data = data,
            nextKey = response.cursor(),
        )
    }
}
