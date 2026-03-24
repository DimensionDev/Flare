package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class NostrLoader(
    private val accountKey: MicroBlogKey,
    private val credentialProvider: suspend () -> UiAccount.Nostr.Credential,
) : UserLoader,
    RelationLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> =
        setOf(
            RelationActionType.Follow,
            RelationActionType.Block,
            RelationActionType.Mute,
        )

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        NostrService.loadProfile(
            credential = credentialProvider(),
            accountKey = accountKey,
            targetPubkey = uiHandle.normalizedRaw,
        )

    override suspend fun userById(id: String): UiProfile =
        NostrService.loadProfile(
            credential = credentialProvider(),
            accountKey = accountKey,
            targetPubkey = id,
        )

    override suspend fun relation(userKey: MicroBlogKey): UiRelation =
        NostrService.relation(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        NostrService.loadStatus(
            credential = credentialProvider(),
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        NostrService.deleteStatus(
            credential = credentialProvider(),
            statusKey = statusKey,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        NostrService.follow(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        NostrService.unfollow(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }

    override suspend fun block(userKey: MicroBlogKey) {
        NostrService.block(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        NostrService.unblock(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        NostrService.mute(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        NostrService.unmute(
            credential = credentialProvider(),
            targetPubkey = userKey.id,
        )
    }
}
