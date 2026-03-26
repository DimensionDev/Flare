package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2

internal class NostrLoader(
    private val accountKey: MicroBlogKey,
    private val serviceManager: NostrServiceManager,
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
        serviceManager.withService {
            it.loadProfile(
                targetPubkey = uiHandle.normalizedRaw,
            )
        }

    override suspend fun userById(id: String): UiProfile =
        serviceManager.withService {
            it.loadProfile(
                targetPubkey = id,
            )
        }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation =
        serviceManager.withService {
            it.relation(
                targetPubkey = userKey.id,
            )
        }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        serviceManager.withService {
            it.loadStatus(
                statusKey = statusKey,
            )
        }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        serviceManager.withService {
            it.deleteStatus(
                statusKey = statusKey,
            )
        }
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.follow(
                targetPubkey = userKey.id,
            )
        }
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.unfollow(
                targetPubkey = userKey.id,
            )
        }
    }

    override suspend fun block(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.block(
                targetPubkey = userKey.id,
            )
        }
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.unblock(
                targetPubkey = userKey.id,
            )
        }
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.mute(
                targetPubkey = userKey.id,
            )
        }
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        serviceManager.withService {
            it.unmute(
                targetPubkey = userKey.id,
            )
        }
    }
}
