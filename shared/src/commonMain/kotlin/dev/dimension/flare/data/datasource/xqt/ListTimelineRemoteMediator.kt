package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
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

    var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .getListLatestTweetsTimeline(
                            variables =
                                Request(
                                    listID = listId,
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service.getListLatestTweetsTimeline(
                        variables =
                            Request(
                                listID = listId,
                                count = state.config.pageSize.toLong(),
                                cursor = cursor,
                            ).encodeJson(),
                    )
                }
            }.body()?.data?.list?.tweetsTimeline?.timeline?.instructions.orEmpty()
        cursor = response.cursor()
        val result = response.tweets()

        val data = result.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = response.isEmpty(),
            data = data,
        )
    }
}
