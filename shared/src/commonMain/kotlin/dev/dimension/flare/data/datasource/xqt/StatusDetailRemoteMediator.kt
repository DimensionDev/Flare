package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val event: StatusEvent.XQT,
    private val statusOnly: Boolean,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
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
    private var cursor: String? = null
    private var conversationId: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        if (loadType == LoadType.REFRESH) {
            if (!database.pagingTimelineDao().existsPaging(accountKey, pagingKey)) {
                database.statusDao().get(statusKey, AccountType.Specific(accountKey)).firstOrNull()?.let {
                    database.connect {
                        database
                            .pagingTimelineDao()
                            .insertAll(
                                listOf(
                                    DbPagingTimeline(
                                        accountType = AccountType.Specific(accountKey),
                                        statusKey = statusKey,
                                        pagingKey = pagingKey,
                                        sortId = 0,
                                    ),
                                ),
                            )
                    }
                }
            }
        }

        if (statusOnly) {
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
            if (item != null) {
                database.connect {
                    XQT.save(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        database = database,
                        tweet = listOf(item),
                    )
                }
            }
            return Result(
                endOfPaginationReached = true,
            )
        } else {
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

            cursor = response.cursor()

            return Result(
                endOfPaginationReached = response.isBottomEnd() || actualTweet.size == 1 || cursor == null,
                data = actualTweet.map { it.toDbPagingTimeline(accountKey, pagingKey) },
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
    // tweet/profile/home
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
