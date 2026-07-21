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
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val USER_HIGHLIGHTS_QUERY_ID = "06hDMOiAMANzKwXHHUwD1w"

@OptIn(ExperimentalPagingApi::class)
internal class UserHighlightsTimelineRemoteMediator(
    private val userKey: MicroBlogKey,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "user_highlights_${userKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getUserHighlightsTweets(
                        pathQueryId = USER_HIGHLIGHTS_QUERY_ID,
                        variables =
                            UserHighlightsTimelineRequest(
                                userID = userKey.id,
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
                    service.getUserHighlightsTweets(
                        pathQueryId = USER_HIGHLIGHTS_QUERY_ID,
                        variables =
                            UserHighlightsTimelineRequest(
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
internal data class UserHighlightsTimelineRequest(
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
