package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

internal class NostrLoader(
    private val accountKey: MicroBlogKey,
    private val credentialProvider: suspend () -> UiAccount.Nostr.Credential,
) : UserLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = emptySet()

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

    override suspend fun follow(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Nostr follow is not implemented yet")

    override suspend fun unfollow(userKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Nostr unfollow is not implemented yet")

    override suspend fun block(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Nostr block is not implemented yet")

    override suspend fun unblock(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Nostr unblock is not implemented yet")

    override suspend fun mute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Nostr mute is not implemented yet")

    override suspend fun unmute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Nostr unmute is not implemented yet")
}
