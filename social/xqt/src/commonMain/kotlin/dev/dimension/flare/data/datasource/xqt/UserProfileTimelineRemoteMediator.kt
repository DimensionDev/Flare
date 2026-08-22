package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.XQTTimelineQueryIds
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal enum class UserProfileTimelineType(
    val queryId: String,
    val operationName: String,
    val pagingKey: String,
) {
    Highlights(
        queryId = XQTTimelineQueryIds.USER_HIGHLIGHTS,
        operationName = "UserHighlightsTweets",
        pagingKey = "highlights",
    ),
    Articles(
        queryId = XQTTimelineQueryIds.USER_ARTICLES,
        operationName = "UserArticlesTweets",
        pagingKey = "articles",
    ),
    Reposts(
        queryId = XQTTimelineQueryIds.USER_REPOSTS,
        operationName = "UserRepostsTimeline",
        pagingKey = "reposts",
    ),
}

@OptIn(ExperimentalPagingApi::class)
internal class UserProfileTimelineRemoteMediator(
    private val type: UserProfileTimelineType,
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "user_${type.pagingKey}_${userKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> {
                    null
                }

                is PagingRequest.Prepend -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }

                is PagingRequest.Append -> {
                    request.nextKey
                }
            }
        val instructions =
            service
                .getUserProfileTimeline(
                    pathQueryId = type.queryId,
                    operationName = type.operationName,
                    variables =
                        UserProfileTimelineRequest(
                            userID = userKey.id,
                            count = pageSize.toLong(),
                            cursor = cursor,
                        ).encodeJson(),
                ).body()
                ?.data
                ?.user
                ?.result
                ?.timeline
                ?.timeline
                ?.instructions
                .orEmpty()
        val tweets =
            instructions.tweets(
                includePin = request is PagingRequest.Refresh,
            )

        return PagingResult(
            endOfPaginationReached = tweets.isEmpty(),
            data = tweets.mapNotNull { it.render(accountKey) },
            nextKey = instructions.cursor(),
        )
    }
}

@Serializable
internal data class UserProfileTimelineRequest(
    @SerialName("userId")
    @Required
    val userID: String,
    @Required
    val count: Long,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = true,
    @Required
    val withVoice: Boolean = true,
)
