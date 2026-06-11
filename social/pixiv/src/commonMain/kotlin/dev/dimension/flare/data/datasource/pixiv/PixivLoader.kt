package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

internal class PixivLoader(
    private val accountKey: MicroBlogKey,
    private val service: PixivService,
    private val credentialFlow: Flow<PixivCredential>,
) : UserLoader,
    PostLoader,
    RelationLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val normalizedHandle = uiHandle.normalizedRaw
        val response =
            service.searchUsers(
                word = normalizedHandle,
            )
        return response
            .userPreviews
            .firstOrNull {
                it.user.account == normalizedHandle ||
                    it.user.name == normalizedHandle ||
                    it.user.id.toString() == normalizedHandle
            }?.user
            ?.toUiProfile(accountKey)
            ?: throw NoSuchElementException("Pixiv user not found: ${uiHandle.canonical}")
    }

    override suspend fun userById(id: String): UiProfile {
        val userId = id.toLongOrNull() ?: throw IllegalArgumentException("Invalid Pixiv user id: $id")
        return try {
            service
                .userDetail(
                    userId = userId,
                ).toUiProfile(accountKey)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            credentialFlow
                .firstOrNull()
                ?.takeIf { it.userId == userId }
                ?.toUiProfile(accountKey)
                ?: throw e
        }
    }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val illustId = statusKey.pixivId()
        return service
            .illustDetail(
                illustId = illustId,
            ).illust
            .toUiTimeline(accountKey)
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey): Unit =
        throw UnsupportedOperationException("Pixiv does not support deleting illustrations through this data source")

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val userId = userKey.pixivId()
        val response =
            service.userDetail(
                userId = userId,
            )
        return UiRelation(following = response.user.isFollowed)
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.followUser(
            userId = userKey.pixivId(),
        )
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollowUser(
            userId = userKey.pixivId(),
        )
    }

    override suspend fun block(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Pixiv block is not supported")

    override suspend fun unblock(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Pixiv block is not supported")

    override suspend fun mute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Pixiv mute is not supported")

    override suspend fun unmute(userKey: MicroBlogKey): Unit = throw UnsupportedOperationException("Pixiv mute is not supported")

    private fun MicroBlogKey.pixivId(): Long = id.toLongOrNull() ?: throw IllegalArgumentException("Invalid Pixiv id: $id")
}
