package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.microblog.loader.EmojiLoader
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.loader.UserLoader
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.StatusDetailItem
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal class VVOLoader(
    val accountKey: MicroBlogKey,
    private val service: VVOService,
) : NotificationLoader,
    UserLoader,
    RelationLoader,
    EmojiLoader,
    PostLoader {
    override val supportedTypes: Set<RelationActionType> = setOf(RelationActionType.Follow)

    override suspend fun notificationBadgeCount(): Int {
        val st = ensureLogin()
        val response =
            service.remindUnread(
                time = Clock.System.now().toEpochMilliseconds() / 1000,
                st = st,
            )
        val mention = response.data?.mentionStatus ?: 0
        val comment = response.data?.cmt ?: 0
        val like = response.data?.attitude ?: 0
        return (mention + comment + like).toInt()
    }

    override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
        val uid = service.getUid(uiHandle.normalizedRaw) ?: error("user not found")
        return userById(uid)
    }

    override suspend fun userById(id: String): UiProfile {
        val st = ensureLogin()
        val profile = service.profileInfo(id, st)
        val user = profile.data?.user ?: error("user not found")
        return user.render(accountKey)
    }

    override suspend fun relation(userKey: MicroBlogKey): UiRelation {
        val st = ensureLogin()
        val profile = service.profileInfo(userKey.id, st)
        val user = profile.data?.user ?: error("user not found")
        return UiRelation(
            following = user.following,
            isFans = user.followMe ?: false,
        )
    }

    override suspend fun follow(userKey: MicroBlogKey) {
        val st = ensureLogin()
        service.follow(
            st = st,
            uid = userKey.id,
        )
    }

    override suspend fun unfollow(userKey: MicroBlogKey) {
        val st = ensureLogin()
        service.unfollow(
            st = st,
            uid = userKey.id,
        )
    }

    override suspend fun block(userKey: MicroBlogKey) {
        error("VVO does not support block")
    }

    override suspend fun unblock(userKey: MicroBlogKey) {
        error("VVO does not support unblock")
    }

    override suspend fun mute(userKey: MicroBlogKey) {
        error("VVO does not support mute")
    }

    override suspend fun unmute(userKey: MicroBlogKey) {
        error("VVO does not support unmute")
    }

    override suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>> =
        service
            .emojis()
            .data
            ?.emoticon
            ?.zhCN
            .orEmpty()
            .map { (category, values) ->
                category to
                    values
                        .mapNotNull { emoji ->
                            val phrase = emoji.phrase ?: return@mapNotNull null
                            val url = emoji.url ?: return@mapNotNull null
                            UiEmoji(
                                shortcode = phrase,
                                url = url,
                                category = category,
                                searchKeywords = listOf(phrase).toImmutableList(),
                                insertText = "$phrase ",
                            )
                        }.toImmutableList()
            }.toMap()
            .toImmutableMap()

    override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
        val regex =
            "\\\$render_data\\s*=\\s*(\\[\\{.*?\\}\\])\\[0\\]\\s*\\|\\|\\s*\\{\\};".toRegex()
        val response =
            service
                .getStatusDetail(statusKey.id)
                .split("\n")
                .joinToString("")
        val json =
            regex
                .find(response)
                ?.groupValues
                ?.get(1)
                ?.decodeJson<List<StatusDetailItem>>()
                ?: throw Exception("status not found")

        return json.firstOrNull()?.status?.render(accountKey) ?: throw Exception("status not found")
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        val st = ensureLogin()
        val response = service.deleteStatus(
            mid = statusKey.id,
            st = st,
        )
        val ok = response.ok ?: 0
        if (ok == 0L) {
            val response = service.deleteComment(
                cid = statusKey.id,
                st = st,
            )
            val ok = response.ok ?: 0
            if (ok == 0L) {
                throw Exception("failed to delete status")
            }
        }
    }

    private suspend fun ensureLogin(): String {
        val config = service.config()
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }
        return requireNotNull(config.data.st) { "st is null" }
    }
}
