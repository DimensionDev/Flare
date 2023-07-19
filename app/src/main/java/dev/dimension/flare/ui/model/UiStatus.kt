package dev.dimension.flare.ui.model

import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant
import org.jsoup.nodes.Element
import java.text.Bidi

internal sealed interface UiStatus {
    val statusKey: MicroBlogKey

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
    ): UiStatus {

        val humanizedTime = createdAt.humanize()
        val contentDirection = if (Bidi(content, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
            LayoutDirection.Ltr
        } else {
            LayoutDirection.Rtl
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
            val humanizedExpiresAt = expiresAt.humanize()
        }

        data class PollOption(
            val title: String,
            val votesCount: Long,
            val percentage: Float,
        )

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
        )
    }
}

internal val UiStatus.itemKey: String get() = statusKey.toString()
internal val UiStatus.itemType: String get() = when (this) {
    is UiStatus.Mastodon -> "mastodon"
}

