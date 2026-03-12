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
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "home_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getHomeLatestTimeline(
                        variables =
                            HomeTimelineRequest(
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
                    service.getHomeLatestTimeline(
                        variables =
                            HomeTimelineRequest(
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                    )
                }
            }.body()
        val instructions =
            response
                ?.data
                ?.home
                ?.homeTimelineUrt
                ?.instructions
                .orEmpty()
        val cursor = instructions.cursor()
        val tweet = instructions.tweets()

        return PagingResult(
            endOfPaginationReached = tweet.isEmpty(),
            data = tweet.mapNotNull { it.render(accountKey) },
            nextKey = cursor,
        )
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class FeaturedTimelineRemoteMediator(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "featured_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getHomeTimeline(
                        variables =
                            HomeTimelineRequest(
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
                    service.getHomeTimeline(
                        variables =
                            HomeTimelineRequest(
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                    )
                }
            }.body()
        val instructions =
            response
                ?.data
                ?.home
                ?.homeTimelineUrt
                ?.instructions
                .orEmpty()
        val tweet = instructions.tweets()

        return PagingResult(
            endOfPaginationReached = tweet.isEmpty(),
            data = tweet.mapNotNull { it.render(accountKey) },
            nextKey = instructions.cursor(),
        )
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "bookmark_$accountKey"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service.getBookmarks(
                        variables =
                            HomeTimelineRequest(
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
                    service.getBookmarks(
                        variables =
                            HomeTimelineRequest(
                                count = pageSize.toLong(),
                                cursor = request.nextKey,
                            ).encodeJson(),
                    )
                }
            }.body()
        val instructions =
            response
                ?.data
                ?.bookmarkTimelineV2
                ?.timeline
                ?.instructions
                .orEmpty()
        val tweet = instructions.tweets()

        return PagingResult(
            endOfPaginationReached = tweet.isEmpty(),
            data = tweet.mapNotNull { it.render(accountKey) },
            nextKey = instructions.cursor(),
        )
    }
}

@Serializable
internal data class HomeTimelineRequest(
    @Required
    val count: Long? = null,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = true,
    @Required
    val latestControlAvailable: Boolean = true,
    @Required
    val requestContext: String = "launch",
    @Required
    val withCommunity: Boolean = true,
    @Required
    val seenTweetIds: List<String> = emptyList(),
)
