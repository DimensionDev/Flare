package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val statusOnly: Boolean,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("status_detail_")
            if (statusOnly) {
                append("status_only_")
            }
            append(statusKey.toString())
            append("_")
            append(accountKey.toString())
        }

    private var conversationId: String? = null

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        when (request) {
            is PagingRequest.Append -> {
                if (statusOnly) {
                    PagingResult(
                        endOfPaginationReached = true,
                    )
                } else {
                    val cursor = request.nextKey.takeIf { it.isNotEmpty() }
                    val response =
                        service
                            .getTweetDetail(
                                variables =
                                    TweetDetailRequest(
                                        focalTweetID = statusKey.id,
                                        cursor = cursor,
                                    ).encodeJson(),
                            ).body()
                            ?.data
                            ?.threadedConversationWithInjectionsV2
                            ?.instructions
                            .orEmpty()

                    val tweet =
                        service
                            .getTweetResultByRestId(
                                variables =
                                    TweetDetailWithRestIdRequest(
                                        tweetID = statusKey.id,
                                        withCommunity = true,
                                        includePromotedContent = true,
                                        withVoice = true,
                                    ).encodeJson(),
                            ).body()
                            ?.data
                            ?.tweetResult
                            ?.result

                    conversationId =
                        when (tweet) {
                            is Tweet -> tweet.legacy?.conversationIdStr
                            is TweetTombstone -> null
                            is TweetWithVisibilityResults -> tweet.tweet.legacy?.conversationIdStr
                            null -> null
                        }

                    val actualTweet =
                        response
                            .tweets()
                            .filter {
                                when (val result = it.tweets.tweetResults.result) {
                                    is TweetTombstone -> false
                                    null -> false
                                    is Tweet -> result.legacy?.conversationIdStr == conversationId
                                    is TweetWithVisibilityResults -> result.tweet.legacy?.conversationIdStr == conversationId
                                }
                            }

                    PagingResult(
                        endOfPaginationReached = response.isBottomEnd() || actualTweet.size == 1 || response.cursor() == null,
                        data =
                            actualTweet.mapNotNull {
                                it.render(accountKey)
                            },
                        nextKey = response.cursor(),
                    )
                }
            }

            is PagingRequest.Prepend -> {
                PagingResult(
                    endOfPaginationReached = true,
                )
            }

            PagingRequest.Refresh -> {
                val response =
                    service
                        .getTweetDetail(
                            variables =
                                TweetDetailRequest(
                                    focalTweetID = statusKey.id,
                                    cursor = null,
                                ).encodeJson(),
                        ).body()
                        ?.data
                        ?.threadedConversationWithInjectionsV2
                        ?.instructions
                        .orEmpty()
                val tweet = response.tweets()
                val item = tweet.firstOrNull { it.id == statusKey.id }

                PagingResult(
                    endOfPaginationReached = statusOnly,
                    data =
                        listOfNotNull(item).mapNotNull {
                            it.render(accountKey)
                        },
                    nextKey = if (statusOnly) null else "",
                )
            }
        }
}

@Serializable
internal data class TweetDetailWithRestIdRequest(
    @SerialName("tweetId")
    val tweetID: String,
    @Required
    val withCommunity: Boolean = false,
    @Required
    val includePromotedContent: Boolean = false,
    @Required
    val withVoice: Boolean = false,
)

@Serializable
internal data class TweetDetailRequest(
    @SerialName("focalTweetId")
    val focalTweetID: String,
    val cursor: String? = null,
    @Required
    val referrer: String = "tweet",
    @SerialName("with_rux_injections")
    @Required
    val withRuxInjections: Boolean = false,
    @Required
    val includePromotedContent: Boolean = true,
    @Required
    val withCommunity: Boolean = true,
    @Required
    val withQuickPromoteEligibilityTweetFields: Boolean = true,
    @Required
    val withBirdwatchNotes: Boolean = true,
    @Required
    val withVoice: Boolean = true,
    @Required
    val withV2Timeline: Boolean = true,
)
