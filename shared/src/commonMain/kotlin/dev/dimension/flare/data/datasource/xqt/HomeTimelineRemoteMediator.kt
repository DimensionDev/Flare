package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "home_$accountKey"

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getHomeLatestTimeline(
                            variables =
                                HomeTimelineRequest(
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

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
            nextKey = cursor,
        )
    }

    override fun onError(e: Throwable) {
        super.onError(e)
        if (e is LoginExpiredException) {
            inAppNotification.onError(
                Message.LoginExpired,
                e,
            )
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class FeaturedTimelineRemoteMediator(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "featured_$accountKey"

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getHomeTimeline(
                            variables =
                                HomeTimelineRequest(
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

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
            nextKey = instructions.cursor(),
        )
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: XQTService,
    database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "bookmark_$accountKey"

    override suspend fun timeline(
        pageSize: Int,
        request: Request,
    ): Result {
        val response =
            when (request) {
                Request.Refresh -> {
                    service
                        .getBookmarks(
                            variables =
                                HomeTimelineRequest(
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

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
            nextKey = instructions.cursor(),
        )
    }
}

@Serializable
internal data class HomeTimelineRequest(
    @Required
    val count: Long = 20,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = false,
    @Required
    val latestControlAvailable: Boolean = false,
)
