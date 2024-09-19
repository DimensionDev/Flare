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
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineView
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey

@OptIn(ExperimentalPagingApi::class)
internal class UserMediaTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineView>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        cursor = null
                        service
                            .getUserMedia(
                                variables =
                                    UserTimelineRequest(
                                        userID = userKey.id,
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
                        service.getUserMedia(
                            variables =
                                UserTimelineRequest(
                                    userID = userKey.id,
                                    count = state.config.pageSize.toLong(),
                                    cursor = cursor,
                                ).encodeJson(),
                        )
                    }
                }.body()
            val instructions =
                response
                    ?.data
                    ?.user
                    ?.result
                    ?.timelineV2
                    ?.timeline
                    ?.instructions
                    .orEmpty()
            val tweet =
                instructions.tweets(
                    includePin = cursor == null,
                )
            cursor = instructions.cursor()
            XQT.save(
                accountKey = accountKey,
                pagingKey = pagingKey,
                database = database,
                tweet = tweet,
                sortIdProvider = {
                    it.id?.toLong() ?: it.sortedIndex
                },
            )
            MediatorResult.Success(
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            MediatorResult.Error(e)
        }
    }
}
