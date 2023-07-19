package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Instant

sealed interface UiStatus {
    val statusKey: MicroBlogKey

    data class Mastodon(
        override val statusKey: MicroBlogKey,
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
    ): UiStatus {

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
        )

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

val UiStatus.itemKey: String get() = statusKey.toString()
val UiStatus.itemType: String get() = when (this) {
    is UiStatus.Mastodon -> "mastodon"
}

