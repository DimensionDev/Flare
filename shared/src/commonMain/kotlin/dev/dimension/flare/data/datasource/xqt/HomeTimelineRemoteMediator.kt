package dev.dimension.flare.data.datasource.xqt

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.database.cache.mapper.toDbPagingTimeline
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@OptIn(ExperimentalPagingApi::class)
internal class HomeTimelineRemoteMediator(
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
    private val inAppNotification: InAppNotification,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "home_$accountKey"
    private var cursor: String? = null

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    cursor = null
                    service
                        .getHomeLatestTimeline(
                            variables =
                                HomeTimelineRequest(
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service.getHomeLatestTimeline(
                        variables =
                            HomeTimelineRequest(
                                count = state.config.pageSize.toLong(),
                                cursor = cursor,
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
        cursor = instructions.cursor()
        val tweet = instructions.tweets()

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
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
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "featured_$accountKey"
    private var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    cursor = null
                    service
                        .getHomeTimeline(
                            variables =
                                HomeTimelineRequest(
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service.getHomeTimeline(
                        variables =
                            HomeTimelineRequest(
                                count = state.config.pageSize.toLong(),
                                cursor = cursor,
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
        cursor = instructions.cursor()
        val tweet = instructions.tweets()

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
        )
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class BookmarkTimelineRemoteMediator(
    private val service: XQTService,
    private val database: CacheDatabase,
    private val accountKey: MicroBlogKey,
) : BaseTimelineRemoteMediator(
        database = database,
    ) {
    override val pagingKey = "bookmark_$accountKey"
    private var cursor: String? = null

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    cursor = null
                    service
                        .getBookmarks(
                            variables =
                                HomeTimelineRequest(
                                    count = state.config.pageSize.toLong(),
                                ).encodeJson(),
                        )
                }

                LoadType.PREPEND -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }

                LoadType.APPEND -> {
                    service.getBookmarks(
                        variables =
                            HomeTimelineRequest(
                                count = state.config.pageSize.toLong(),
                                cursor = cursor,
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
        cursor = instructions.cursor()
        val tweet = instructions.tweets()

        val data = tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey) }

        return Result(
            endOfPaginationReached = tweet.isEmpty(),
            data = data,
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
