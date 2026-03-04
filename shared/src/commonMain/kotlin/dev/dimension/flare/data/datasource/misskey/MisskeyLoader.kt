package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.MuteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal class MisskeyLoader(
    override val accountKey: MicroBlogKey,
    private val service: MisskeyService,
) : NotificationLoader,
    UserLoader,
    PostLoader,
    RelationLoader,
    EmojiLoader {
    override suspend fun notificationBadgeCount(): Int =
        service
            .iNotifications(
                INotificationsRequest(
                    limit = 1,
                ),
            ).size

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile =
        service
            .usersShow(
                UsersShowRequest(
                    username = uiHandle.normalizedRaw,
                    host = uiHandle.normalizedHost,
                ),
            ).render(accountKey)

    override suspend fun userById(id: String): UiProfile = service.usersShow(UsersShowRequest(userId = id)).render(accountKey)

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        service
            .notesShow(
                IPinRequest(noteId = statusKey.id),
            ).render(accountKey)

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        service.notesDelete(
            IPinRequest(
                noteId = statusKey.id,
            ),
        )
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val user = service.usersShow(UsersShowRequest(userId = userKey.id))
        return UiRelation(
            following = user.isFollowing ?: false,
            isFans = user.isFollowed ?: false,
            blocking = user.isBlocking ?: false,
            muted = user.isMuted ?: false,
            hasPendingFollowRequestFromYou = user.hasPendingFollowRequestFromYou ?: false,
            hasPendingFollowRequestToYou = user.hasPendingFollowRequestToYou ?: false,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.followingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.followingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
    }

    override suspend fun block(userKey: MicroBlogKey) {
        service.blockingCreate(AdminAccountsDeleteRequest(userId = userKey.id))
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        service.blockingDelete(AdminAccountsDeleteRequest(userId = userKey.id))
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        service.muteCreate(MuteCreateRequest(userId = userKey.id))
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        service.muteDelete(AdminAccountsDeleteRequest(userId = userKey.id))
    }

    override suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>> =
        service
            .emojis()
            .emojis
            .orEmpty()
            .map {
                it.toUi()
            }.groupBy { it.category }
            .map { (category, value) ->
                category to value.toImmutableList()
            }.toMap()
            .toImmutableMap()
}
