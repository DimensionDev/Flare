package dev.dimension.flare.ui.model

import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.humanizer.humanizePercentage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant
import org.jsoup.nodes.Element
import java.text.Bidi

internal sealed interface UiStatus {
    val statusKey: MicroBlogKey

    data class MastodonNotification(
        override val statusKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val createdAt: Instant,
        val status: Mastodon?,
        val type: NotificationTypes,
    ) : UiStatus {
        val humanizedTime by lazy {
            createdAt.humanize()
        }
    }

    data class Mastodon(
        override val statusKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val content: String,
        val contentToken: Element,
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
    ) : UiStatus {

        val humanizedTime by lazy {
            createdAt.humanize()
        }
        val contentDirection by lazy {
            if (Bidi(content, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            }
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
}

internal val UiStatus.itemKey: String get() = statusKey.toString()
internal val UiStatus.itemType: String
    get() = when (this) {
        is UiStatus.Mastodon -> buildString {
            append("mastodon")
            if (reblogStatus != null) append("_reblog")
            with(reblogStatus ?: this@UiStatus) {
                if (media.isNotEmpty()) append("_media")
                if (poll != null) append("_poll")
                if (card != null) append("_card")
            }
        }

        is UiStatus.MastodonNotification -> buildString {
            append("mastodon_notification")
            append("_${type.name.lowercase()}")
            if (status != null) append(status.itemType)
        }
    }

