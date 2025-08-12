package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
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
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "user_timeline_${userKey}_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getUserTweets(
                            variables =
                                UserTimelineRequest(
                                    userID = userKey.id,
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
                    service.getUserTweets(
                        variables =
                            UserTimelineRequest(
                                userID = userKey.id,
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
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
                includePin = request is Request.Refresh,
            )

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
            nextKey = instructions.cursor(),
        )
    }
}

@Serializable
internal data class UserTimelineRequest(
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
