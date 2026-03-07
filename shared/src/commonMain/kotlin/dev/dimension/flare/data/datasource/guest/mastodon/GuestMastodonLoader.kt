package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render

internal class GuestMastodonLoader(
    private val host: String,
    private val service: GuestMastodonService,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = RelationActionType.entries.toSet()

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        service
            .lookupUserByAcct("${uiHandle.normalizedRaw}@${uiHandle.normalizedHost}")
            ?.render(
                accountKey = null,
                host = host,
            ) ?: throw Exception("User not found")

    override suspend fun userById(id: String): UiProfile =
        service
            .lookupUser(id)
            .render(
                accountKey = null,
                host = host,
            )

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = UiRelation()

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        service
            .lookupStatus(statusKey.id)
            .render(
                accountKey = null,
                host = host,
            )

    override suspend fun deleteStatus(statusKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support delete status")

    override suspend fun follow(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support follow")

    override suspend fun unfollow(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support unfollow")

    override suspend fun block(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support block")

    override suspend fun unblock(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support unblock")

    override suspend fun mute(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support mute")

    override suspend fun unmute(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Guest Mastodon data source does not support unmute")
}
