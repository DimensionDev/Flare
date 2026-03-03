package dev.dimension.flare.data.datasource.bluesky

import app.bsky.actor.GetProfileQueryParams
import app.bsky.feed.GetPostsQueryParams
import app.bsky.graph.MuteActorRequest
import app.bsky.graph.UnmuteActorRequest
import app.bsky.notification.ListNotificationsQueryParams
import com.atproto.identity.ResolveHandleQueryParams
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.collections.immutable.persistentListOf
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import kotlin.time.Clock

internal class BlueskyLoader(
    override val accountKey: MicroBlogKey,
    private val service: BlueskyService,
) : NotificationLoader,
    UserLoader,
    PostLoader,
    RelationLoader {
    override suspend fun userByHandleAndHost(
        handle: String,
        host: String,
    ): UiProfile =
        service
            .getProfile(GetProfileQueryParams(actor = Handle(handle = handle)))
            .requireResponse()
            .render(accountKey)

    override suspend fun userById(id: String): UiProfile =
        service
            .getProfile(GetProfileQueryParams(actor = Did(did = id)))
            .requireResponse()
            .render(accountKey)

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val user =
            service
                .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                .requireResponse()
        return UiRelation(
            following = user.viewer?.following?.atUri != null,
            isFans = user.viewer?.followedBy?.atUri != null,
            blocking = user.viewer?.blockedBy ?: false,
            muted = user.viewer?.muted ?: false,
        )
    }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val isDid = statusKey.id.startsWith("at://did:")
        if (isDid) {
            return service
                .getPosts(
                    GetPostsQueryParams(
                        persistentListOf(AtUri(statusKey.id)),
                    ),
                ).requireResponse()
                .posts
                .first()
                .render(accountKey)
        } else {
            // "at://${handle}/app.bsky.feed.post/${id}"
            val handle = statusKey.id.substringAfter("at://").substringBefore("/")
            val id = statusKey.id.substringAfterLast('/')
            val did =
                service
                    .resolveHandle(ResolveHandleQueryParams(Handle(handle)))
                    .requireResponse()
                    .did
            val actualAtUri = AtUri("at://${did.did}/app.bsky.feed.post/$id")
            return service
                .getPosts(
                    GetPostsQueryParams(
                        persistentListOf(actualAtUri),
                    ),
                ).requireResponse()
                .posts
                .first()
                .render(accountKey)
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        service.deleteRecord(
            DeleteRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.feed.post"),
                rkey = RKey(statusKey.id.substringAfterLast('/')),
            ),
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.createRecord(
            CreateRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.graph.follow"),
                record =
                    app.bsky.graph
                        .Follow(
                            subject = Did(userKey.id),
                            createdAt = Clock.System.now(),
                        ).bskyJson(),
            ),
        )
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        val user =
            service
                .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                .requireResponse()

        val followRepo = user.viewer?.following?.atUri
        if (followRepo != null) {
            service.deleteRecord(
                DeleteRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.follow"),
                    rkey = RKey(followRepo.substringAfterLast('/')),
                ),
            )
        }
    }

    override suspend fun block(userKey: MicroBlogKey) {
        service.createRecord(
            CreateRecordRequest(
                repo = Did(did = accountKey.id),
                collection = Nsid("app.bsky.graph.block"),
                record =
                    app.bsky.graph
                        .Block(
                            subject = Did(userKey.id),
                            createdAt = Clock.System.now(),
                        ).bskyJson(),
            ),
        )
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        val user =
            service
                .getProfile(GetProfileQueryParams(actor = Did(did = userKey.id)))
                .requireResponse()

        val blockRepo = user.viewer?.blocking?.atUri
        if (blockRepo != null) {
            service.deleteRecord(
                DeleteRecordRequest(
                    repo = Did(did = accountKey.id),
                    collection = Nsid("app.bsky.graph.block"),
                    rkey = RKey(blockRepo.substringAfterLast('/')),
                ),
            )
        }
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        service.muteActor(MuteActorRequest(actor = Did(did = userKey.id)))
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        service.unmuteActor(UnmuteActorRequest(actor = Did(did = userKey.id)))
    }

    override suspend fun notificationBadgeCount(): Int {
        val notifications =
            service
                .listNotifications(
                    params = ListNotificationsQueryParams(limit = 40),
                ).requireResponse()
                .notifications
        return notifications.count { !it.isRead }
    }
}
