package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
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
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    @Serializable
    data class Request(
        @SerialName("listId")
        val listID: String? = null,
        val count: Long? = null,
        val cursor: String? = null,
    )

    var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
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
            XQT.save(
                database = database,
                accountKey = accountKey,
                pagingKey = pagingKey,
                tweet = result,
            )

            MediatorResult.Success(
                endOfPaginationReached = response.isEmpty(),
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
