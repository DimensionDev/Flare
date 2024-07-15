package dev.dimension.flare.ui.render

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiStatusAction
import kotlinx.collections.immutable.ImmutableList

object Render {
    sealed interface ItemContent {
        data class Status(
            val images: ImmutableList<UiMedia>,
            val contentWarning: String?,
            val user: User?,
            val quote: Status?,
            val content: UiRichText,
            val actions: ImmutableList<UiStatusAction>,
            val poll: UiPoll?,
            val statusKey: MicroBlogKey,
            val card: UiCard?,
        ) : ItemContent

        data class User(
            val avatar: String,
            val name: UiRichText,
            val handle: String,
            val key: MicroBlogKey,
        ) : ItemContent
    }

    data class Item(
        val topMessage: TopMessage?,
        val content: ItemContent,
    )

    data class TopMessage(
        val user: ItemContent.User?,
        val icon: Icon?,
        val message: MessageType,
    ) {
        enum class Icon {
            Retweet,
        }

        sealed interface MessageType {
            sealed interface Mastodon : MessageType {
                data object Reblogged : Mastodon

                data object Follow : Mastodon

                data object Favourite : Mastodon

                data object Reblog : Mastodon

                data object Mention : Mastodon

                data object Poll : Mastodon

                data object FollowRequest : Mastodon

                data object Status : Mastodon

                data object Update : Mastodon
            }

            sealed interface Misskey : MessageType {
                data object Follow : Misskey

                data object Mention : Misskey

                data object Reply : Misskey

                data object Renote : Misskey

                data object Quote : Misskey

                data object Reaction : Misskey

                data object PollEnded : Misskey

                data object ReceiveFollowRequest : Misskey

                data object FollowRequestAccepted : Misskey

                data object AchievementEarned : Misskey

                data object App : Misskey
            }
        }
    }
}
