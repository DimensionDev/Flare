package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.bsky.feed.GetPostsQueryParams
import app.bsky.notification.ListNotificationsQueryParams
import app.bsky.notification.ListNotificationsReason
import app.bsky.notification.UpdateSeenRequest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.Clock

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val onClearMarker: () -> Unit,
) : RemoteMediator<Int, DbPagingTimelineWithStatus>() {
    private var cursor: String? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, DbPagingTimelineWithStatus>,
    ): MediatorResult {
        return try {
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
                        return MediatorResult.Success(
                            endOfPaginationReached = true,
                        )
                    }
                } ?: return MediatorResult.Success(
                    endOfPaginationReached = true,
                )

            val referencesUri =
                response.notifications
                    .mapNotNull {
                        when (it.reason) {
                            is ListNotificationsReason.Unknown -> null
                            ListNotificationsReason.Like ->
                                it.record
                                    .decodeAs<app.bsky.feed.Like>()
                                    .subject.uri
                            ListNotificationsReason.Repost ->
                                it.record
                                    .decodeAs<app.bsky.feed.Repost>()
                                    .subject.uri
                            ListNotificationsReason.Follow -> null
                            ListNotificationsReason.Mention -> it.uri
                            ListNotificationsReason.Reply -> it.uri
                            ListNotificationsReason.Quote -> it.uri
                            ListNotificationsReason.StarterpackJoined -> null
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
            Bluesky.saveNotification(
                accountKey,
                pagingKey,
                database,
                response.notifications,
                references = references,
            )

            MediatorResult.Success(
                endOfPaginationReached = cursor == null,
            )
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }
}
