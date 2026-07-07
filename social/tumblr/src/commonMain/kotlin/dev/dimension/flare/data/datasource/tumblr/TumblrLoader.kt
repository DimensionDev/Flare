package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class TumblrLoader(
    private val service: TumblrService,
    private val accountKey: MicroBlogKey,
) : PostLoader,
    RelationLoader,
    UserLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val parts = statusKey.toTumblrPostKeyParts()
        return service
            .post(
                blogIdentifier = parts.blogName,
                postId = parts.postId,
            )?.toUiTimeline(accountKey)
            ?: error("Tumblr post not found: $statusKey")
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        val parts = statusKey.toTumblrPostKeyParts()
        service.deletePost(
            blogIdentifier = parts.blogName,
            postId = parts.postId,
        )
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val blog = service.blogInfo(userKey.toTumblrBlogIdentifier())
        return UiRelation(following = blog.following == true)
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.follow(tumblrBlogUrl(userKey.id))
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollow(tumblrBlogUrl(userKey.id))
    }

    override suspend fun block(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Tumblr block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Tumblr unblock is not supported")

    override suspend fun mute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Tumblr mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Tumblr unmute is not supported")

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        service
            .blogInfo(uiHandle.normalizedRaw.normalizedTumblrBlogName())
            .toUiProfile(accountKey)

    override suspend fun userById(id: String): UiProfile =
        service
            .blogInfo(id.normalizedTumblrBlogName())
            .toUiProfile(accountKey)
}
