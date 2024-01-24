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
internal class StatusDetailRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val pagingKey: String,
    private val statusOnly: Boolean,
) : RemoteMediator<Int, DbPagingTimelineWithStatusView>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatusView>,
    ): MediatorResult {
        return try {
            if (loadType == LoadType.REFRESH) {
                if (!database.dbPagingTimelineQueries.existsPaging(accountKey, pagingKey).executeAsOne()) {
                    database.dbStatusQueries.get(statusKey, accountKey).executeAsOneOrNull()?.let {
                        database.dbPagingTimelineQueries
                            .insert(
                                account_key = accountKey,
                                status_key = statusKey,
                                paging_key = pagingKey,
                                sort_id = 0,
                            )
                    }
                }
            }
            val response =
                service.getTweetDetail(
                    variables =
                        TweetDetailRequest(
                            focalTweetID = statusKey.id,
                            cursor = null,
                        ).encodeJson(),
                )
                    .body()
                    ?.data
                    ?.threadedConversationWithInjectionsV2
                    ?.instructions
                    .orEmpty()
            val tweet = response.tweets()

            if (statusOnly) {
                val item = tweet.firstOrNull { it.id == statusKey.id }
                if (item != null) {
                    XQT.save(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        database = database,
                        tweet = listOf(item),
                    )
                }
                MediatorResult.Success(
                    endOfPaginationReached = true,
                )
            } else {
                cursor = response.cursor()
                XQT.save(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    database = database,
                    tweet = tweet,
                )
                MediatorResult.Success(
                    endOfPaginationReached = cursor == null,
                )
            }
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}

@Serializable
data class TweetDetailRequest(
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
