package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal class MastodonLoader(
    override val accountKey: MicroBlogKey,
    private val service: MastodonService,
) : NotificationLoader,
    UserLoader,
    PostLoader,
    RelationLoader,
    EmojiLoader {
    override suspend fun userByHandleAndHost(
        handle: String,
        host: String,
    ): UiProfile =
        service
            .lookupUserByAcct("$handle@$host")
            ?.render(
                accountKey = accountKey,
                host = accountKey.host,
            ) ?: throw Exception("User not found")

    override suspend fun userById(id: String): UiProfile =
        service
            .lookupUser(id)
            .render(
                accountKey = accountKey,
                host = accountKey.host,
            )

    override suspend fun relation(userKey: MicroBlogKey): UiRelation = service.showFriendships(listOf(userKey.id)).first().toUi()

    override suspend fun unfollow(userKey: MicroBlogKey) {
        service.unfollow(userKey.id)
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        service.follow(userKey.id)
    }

    override suspend fun block(userKey: MicroBlogKey) {
        service.block(userKey.id)
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        service.unblock(userKey.id)
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        service.muteUser(userKey.id)
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        service.unmuteUser(userKey.id)
    }

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
        service
            .lookupStatus(
                statusKey.id,
            ).render(
                accountKey = accountKey,
                host = accountKey.host,
            )

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        service.delete(statusKey.id)
    }

    override suspend fun notificationBadgeCount(): Int {
        val marker =
            service.notificationMarkers().notifications?.lastReadID ?: return 0
        val timeline = service.notification(min_id = marker)
        return timeline.size
    }

    override suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>> {
        return service
            .emojis()
            .filter { it.visibleInPicker == true }
            .groupBy { it.category }
            .mapNotNull {
                val category = it.key ?: return@mapNotNull null
                category to it.value
            }.map { (category, value) ->
                category to
                    value
                        .map {
                            val shortCode =
                                it.shortcode
                                    .orEmpty()
                                    .let { if (!it.startsWith(':') && !it.endsWith(':')) ":$it:" else it }
                            UiEmoji(
                                shortcode = shortCode,
                                url = it.url.orEmpty(),
                                category = it.category.orEmpty(),
                                searchKeywords =
                                    listOfNotNull(
                                        it.shortcode,
                                    ).toImmutableList(),
                                insertText = " $shortCode ",
                            )
                        }.toImmutableList()
            }.toMap()
            .toImmutableMap()
    }
}
