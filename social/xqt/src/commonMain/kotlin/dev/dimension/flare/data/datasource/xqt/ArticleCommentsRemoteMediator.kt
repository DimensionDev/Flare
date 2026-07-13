package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.conversationTweets
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.isBottomEnd
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class ArticleCommentsRemoteMediator(
    private val statusKey: MicroBlogKey,
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val collapseReplyChains: Boolean = false

    override val pagingKey: String = "article_comments_${statusKey}_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        when (request) {
            PagingRequest.Refresh -> loadPage(cursor = null)
            is PagingRequest.Append -> loadPage(cursor = request.nextKey)
            is PagingRequest.Prepend -> PagingResult(endOfPaginationReached = true)
        }

    private suspend fun loadPage(cursor: String?): PagingResult<UiTimelineV2> {
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
        val comments =
            response
                .conversationTweets()
                .mapNotNull { it.render(accountKey) }
        val nextKey = response.cursor()
        return PagingResult(
            endOfPaginationReached = response.isBottomEnd() || comments.isEmpty() || nextKey == null,
            data = comments,
            nextKey = nextKey,
        )
    }
}
