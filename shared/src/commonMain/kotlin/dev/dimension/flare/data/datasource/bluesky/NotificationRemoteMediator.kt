package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Like
import app.bsky.feed.Repost
import app.bsky.notification.ListNotificationsQueryParams
import app.bsky.notification.ListNotificationsReason
import app.bsky.notification.UpdateSeenRequest
import dev.dimension.flare.common.BaseTimelineRemoteMediator
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDb
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.Clock

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val onClearMarker: () -> Unit,
) : BaseTimelineRemoteMediator(
        database = database,
        accountType = AccountType.Specific(accountKey),
    ) {
    private var cursor: String? = null
    override val pagingKey: String =
        buildString {
        }

    override suspend fun timeline(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): Result {
        val response =
            when (loadType) {
                LoadType.REFRESH -> {
                    service
                        .listNotifications(
                            ListNotificationsQueryParams(
                                limit = state.config.pageSize.toLong(),
                            ),
                        ).maybeResponse()
                        .also {
                            service.updateSeen(request = UpdateSeenRequest(seenAt = Clock.System.now()))
                            onClearMarker.invoke()
                        }
                }

                LoadType.APPEND -> {
                    service
                        .listNotifications(
                            ListNotificationsQueryParams(
                                limit = state.config.pageSize.toLong(),
                                cursor = cursor,
                            ),
                        ).maybeResponse()
                }

                else -> {
                    return Result(
                        endOfPaginationReached = true,
                    )
                }
            } ?: return Result(
                endOfPaginationReached = true,
            )

        val referencesUri =
            response.notifications
                .mapNotNull {
                    when (it.reason) {
                        is ListNotificationsReason.Unknown -> null
                        ListNotificationsReason.Like ->
                            it.record
                                .decodeAs<Like>()
                                .subject.uri
                        ListNotificationsReason.Repost ->
                            it.record
                                .decodeAs<Repost>()
                                .subject.uri
                        ListNotificationsReason.Follow -> null
                        ListNotificationsReason.Mention -> it.uri
                        ListNotificationsReason.Reply -> it.uri
                        ListNotificationsReason.Quote -> it.uri
                        ListNotificationsReason.StarterpackJoined -> null
                        ListNotificationsReason.Unverified -> null
                        ListNotificationsReason.Verified -> null
                    }
                }.distinct()
                .toImmutableList()
        val references =
            service
                .getPosts(params = GetPostsQueryParams(uris = referencesUri))
                .maybeResponse()
                ?.posts
                .orEmpty()
                .associateBy { it.uri }
                .toImmutableMap()
        cursor = response.cursor
        return Result(
            endOfPaginationReached = cursor == null,
            data =
                response.notifications.toDb(
                    accountKey = accountKey,
                    pagingKey = pagingKey,
                    references = references,
                ),
        )
    }
}
