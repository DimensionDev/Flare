package dev.dimension.flare.data.datasource.bluesky

import androidx.paging.ExperimentalPagingApi
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.Like
import app.bsky.feed.Repost
import app.bsky.notification.ListNotificationsNotificationReason
import app.bsky.notification.ListNotificationsQueryParams
import app.bsky.notification.UpdateSeenRequest
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.time.Clock

@OptIn(ExperimentalPagingApi::class)
internal class NotificationRemoteMediator(
    private val service: BlueskyService,
    private val accountKey: MicroBlogKey,
    private val onClearMarker: () -> Unit,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String =
        buildString {
            append("notification_")
            append(accountKey.toString())
        }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val response =
            when (request) {
                PagingRequest.Refresh -> {
                    service
                        .listNotifications(
                            ListNotificationsQueryParams(
                                limit = pageSize.toLong(),
                            ),
                        ).maybeResponse()
                        .also {
                            service.updateSeen(
                                request =
                                    UpdateSeenRequest(
                                        seenAt = Clock.System.now(),
                                    ),
                            )
                            onClearMarker.invoke()
                        }
                }

                is PagingRequest.Append -> {
                    service
                        .listNotifications(
                            ListNotificationsQueryParams(
                                limit = pageSize.toLong(),
                                cursor = request.nextKey,
                            ),
                        ).maybeResponse()
                }

                else -> {
                    return PagingResult(
                        endOfPaginationReached = true,
                    )
                }
            } ?: return PagingResult(
                endOfPaginationReached = true,
            )

        val referencesUri =
            response.notifications
                .mapNotNull {
                    when (it.reason) {
                        is ListNotificationsNotificationReason.Unknown -> null
                        ListNotificationsNotificationReason.Like ->
                            it.record
                                .decodeAs<Like>()
                                .subject.uri

                        ListNotificationsNotificationReason.Repost ->
                            it.record
                                .decodeAs<Repost>()
                                .subject.uri

                        ListNotificationsNotificationReason.Follow -> null
                        ListNotificationsNotificationReason.Mention -> it.uri
                        ListNotificationsNotificationReason.Reply -> it.uri
                        ListNotificationsNotificationReason.Quote -> it.uri
                        ListNotificationsNotificationReason.StarterpackJoined -> null
                        ListNotificationsNotificationReason.Unverified -> null
                        ListNotificationsNotificationReason.Verified -> null
                        ListNotificationsNotificationReason.LikeViaRepost -> it.uri
                        ListNotificationsNotificationReason.RepostViaRepost -> it.uri
                        ListNotificationsNotificationReason.SubscribedPost -> it.uri
                        ListNotificationsNotificationReason.ContactMatch -> it.uri
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
        return PagingResult(
            endOfPaginationReached = response.cursor == null,
            data = response.notifications.render(accountKey, references),
            nextKey = response.cursor,
        )
    }
}
