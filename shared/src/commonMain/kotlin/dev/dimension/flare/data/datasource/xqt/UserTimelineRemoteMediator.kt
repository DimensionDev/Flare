package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.cache.DbPagingTimelineWithStatusView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class UserTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        cursor = null
                        service.getUserTweets(
                            variables =
                                UserTimelineRequest(
                                    userID = userKey.id,
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        ).also {
                            database.transaction {
                                database.dbPagingTimelineQueries.deletePaging(accountKey, pagingKey)
                            }
                        }
                    }

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }

                    LoadType.APPEND -> {
                        service.getUserTweets(
                            variables =
                                UserTimelineRequest(
                                    userID = userKey.id,
                                    count = state.config.pageSize.toLong(),
                                    cursor = cursor,
                                ).encodeJson(),
                        )
                    }
                }.body()
            val instructions = response?.data?.user?.result?.timelineV2?.timeline?.instructions.orEmpty()
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

@OptIn(ExperimentalPagingApi::class)
internal class UserMediaTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            val response =
                when (loadType) {
                    LoadType.REFRESH -> {
                        cursor = null
                        service.getUserMedia(
                            variables =
                                UserTimelineRequest(
                                    userID = userKey.id,
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        ).also {
                            database.transaction {
                                database.dbPagingTimelineQueries.deletePaging(accountKey, pagingKey)
                            }
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
            val instructions = response?.data?.user?.result?.timelineV2?.timeline?.instructions.orEmpty()
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

@Serializable
data class UserTimelineRequest(
    @SerialName("userId")
    @Required
    val userID: String,
    @Required
    val count: Long? = null,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = false,
    @Required
    val withQuickPromoteEligibilityTweetFields: Boolean = true,
    @Required
    val withVoice: Boolean = true,
    @Required
    val withV2Timeline: Boolean = true,
)
