package dev.dimension.flare.ui.model

import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.humanizer.humanizePercentage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

expect class UiStatusExtra

internal expect fun createStatusExtra(status: UiStatus): UiStatusExtra

sealed class UiStatus {
    abstract val statusKey: MicroBlogKey
    abstract val accountKey: MicroBlogKey
    val extra by lazy {
        createStatusExtra(this)
    }

    open val itemKey: String by lazy {
        statusKey.toString()
    }

    val itemType: String by lazy {
        when (this) {
            is Mastodon -> buildString {
                append("mastodon")
                if (reblogStatus != null) append("_reblog")
                with(reblogStatus ?: this@UiStatus) {
                    if (media.isNotEmpty()) append("_media")
                    if (poll != null) append("_poll")
                    if (card != null) append("_card")
                }
            }

            is MastodonNotification -> buildString {
                append("mastodon_notification")
                append("_${type.name.lowercase()}")
                if (status != null) {
                    append(status.itemType)
                }
            }

            is Misskey -> buildString {
                append("misskey")
                if (renote != null) {
                    append("_reblog")
                    append("_${renote.itemType}")
                }
                if (quote != null) {
                    append("_quote")
                    append("_${quote.itemType}")
                }
                if (media.isNotEmpty()) append("_media")
                if (poll != null) append("_poll")
                if (card != null) append("_card")
            }
            is MisskeyNotification -> buildString {
                append("misskey_notification")
                append("_${type.name.lowercase()}")
                if (note != null) {
                    append(note.itemType)
                }
            }

//            is Bluesky -> buildString {
//                append("bluesky")
//                if (repostBy != null) append("_reblog")
//                if (medias.isNotEmpty()) append("_media")
//                if (quote != null) {
//                    append("_quote")
//                    append("_${quote.itemType}")
//                }
//            }
//
//            is BlueskyNotification -> buildString {
//                append("bluesky_notification")
//                append("_${reason.lowercase()}")
//            }
        }
    }

    data class MastodonNotification(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val createdAt: Instant,
        val status: Mastodon?,
        val type: NotificationTypes,
    ) : UiStatus() {
        val humanizedTime by lazy {
            createdAt.humanize()
        }
    }

    data class Mastodon(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val content: String,
        val contentWarningText: String?,
        val matrices: Matrices,
        val media: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: Poll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val reblogStatus: Mastodon?,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Status,
    ) : UiStatus() {

        val humanizedTime by lazy {
            createdAt.humanize()
        }

        data class Reaction(
            val liked: Boolean,
            val reblogged: Boolean,
            val bookmarked: Boolean,
        )

        data class Poll(
            val id: String,
            val options: ImmutableList<PollOption>,
            val expiresAt: Instant,
            val expired: Boolean,
            val multiple: Boolean,
            val voted: Boolean,
            val ownVotes: ImmutableList<Int>,
        ) {
            val humanizedExpiresAt by lazy { expiresAt.humanize() }
        }

        data class PollOption(
            val title: String,
            val votesCount: Long,
            val percentage: Float,
        ) {
            val humanizedPercentage by lazy { percentage.humanizePercentage() }
        }

        enum class Visibility {
            Public,
            Unlisted,
            Private,
            Direct,
        }

        data class Matrices(
            val replyCount: Long,
            val reblogCount: Long,
            val favouriteCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.toString() else null }
            val humanizedReblogCount by lazy { if (reblogCount > 0) reblogCount.toString() else null }
            val humanizedFavouriteCount by lazy { if (favouriteCount > 0) favouriteCount.toString() else null }
        }
    }

    data class Misskey(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Misskey,
        val content: String,
        val contentWarningText: String?,
        val matrices: Matrices,
        val media: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: Poll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val quote: Misskey?,
        val renote: Misskey?,
    ) : UiStatus() {
        val humanizedTime: String by lazy {
            createdAt.humanize()
        }

        data class Reaction(
            val emojiReactions: ImmutableList<EmojiReaction>,
            val myReaction: String?,
        )

        data class EmojiReaction(
            val name: String,
            val url: String,
            val count: Long,
        ) {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        enum class Visibility {
            Public,
            Home,
            Followers,
            Specified,
        }

        data class Poll(
            val id: String,
            val options: ImmutableList<PollOption>,
            val expiresAt: Instant,
            val multiple: Boolean,
        ) {
            val expired: Boolean by lazy { expiresAt < Clock.System.now() }
            val humanizedExpiresAt by lazy { expiresAt.humanize() }
        }

        data class PollOption(
            val title: String,
            val votesCount: Long,
            val percentage: Float,
            val voted: Boolean,
        ) {
            val humanizedPercentage by lazy { percentage.humanizePercentage() }
        }

        data class Matrices(
            val replyCount: Long,
            val renoteCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.toString() else null }
            val humanizedReNoteCount by lazy { if (renoteCount > 0) renoteCount.toString() else null }
        }
    }

    data class MisskeyNotification(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Misskey?,
        val createdAt: Instant,
        val note: Misskey?,
        val type: Notification.Type,
        val achievement: String?,
    ) : UiStatus() {
        val humanizedTime by lazy {
            createdAt.humanize()
        }
    }

//    data class Bluesky(
//        override val accountKey: MicroBlogKey,
//        override val statusKey: MicroBlogKey,
//        val user: UiUser.Bluesky,
//        val indexedAt: Instant,
//        val repostBy: UiUser.Bluesky?,
//        val quote: Bluesky?,
//        val content: String,
//        val medias: ImmutableList<UiMedia>,
//        val card: UiCard?,
//        val matrices: Matrices,
//        val reaction: Reaction,
//    ) : UiStatus() {
//        val humanizedTime by lazy {
//            indexedAt.humanize()
//        }
//        data class Matrices(
//            val replyCount: Long,
//            val likeCount: Long,
//            val repostCount: Long,
//        ) {
//            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.toString() else null }
//            val humanizedLikeCount by lazy { if (likeCount > 0) likeCount.toString() else null }
//            val humanizedRepostCount by lazy { if (repostCount > 0) repostCount.toString() else null }
//        }
//        data class Reaction(
//            val liked: Boolean,
//            val reposted: Boolean,
//        )
//
//        override val itemKey: String by lazy {
//            statusKey.toString() + repostBy?.let { "_reblog_${it.userKey}" }.orEmpty()
//        }
//    }
//
//    data class BlueskyNotification(
//        override val statusKey: MicroBlogKey,
//        override val accountKey: MicroBlogKey,
//        val user: UiUser.Bluesky,
//        /**
//         * Expected values are 'like', 'repost', 'follow', 'mention', 'reply', and 'quote'.
//         */
//        val reason: String,
//        val indexedAt: Instant,
//    ) : UiStatus() {
//        override val itemKey: String by lazy {
//            statusKey.toString() + "_${user.userKey}"
//        }
//        val humanizedTime by lazy {
//            indexedAt.humanize()
//        }
//    }
}
