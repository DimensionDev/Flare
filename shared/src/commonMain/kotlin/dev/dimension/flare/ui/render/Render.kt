package dev.dimension.flare.ui.render

import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import kotlinx.collections.immutable.ImmutableList

object Render {
    sealed interface ItemContent {
        data class Status(
            val images: ImmutableList<UiMedia>,
            val contentWarning: String?,
            val user: User?,
            val quote: ImmutableList<Status>,
            val content: UiRichText,
            val actions: ImmutableList<StatusAction>,
            val poll: UiPoll?,
            val statusKey: MicroBlogKey,
            val card: UiCard?,
            val createdAt: UiDateTime,
            val bottomContent: BottomContent? = null,
            val topEndContent: TopEndContent? = null,
        ) : ItemContent {
            sealed interface BottomContent {
                data class Reaction(
                    val emojiReactions: ImmutableList<EmojiReaction>,
                    val myReaction: String?,
                ) : BottomContent {
                    data class EmojiReaction(
                        val name: String,
                        val url: String,
                        val count: Long,
                        val onClicked: () -> Unit,
                    ) {
                        val humanizedCount by lazy {
                            count.humanize()
                        }
                        val isImageReaction by lazy {
                            name.startsWith(":") && name.endsWith(":")
                        }
                    }
                }
            }

            sealed interface TopEndContent {
                data class Visibility(
                    val visibility: Type,
                ) : TopEndContent {
                    enum class Type {
                        Public,
                        Home,
                        Followers,
                        Specified,
                    }
                }
            }
        }

        data class User(
            val avatar: String,
            val name: UiRichText,
            val handle: String,
            val key: MicroBlogKey,
        ) : ItemContent

        data class UserList(
            val users: ImmutableList<User>,
        ) : ItemContent
    }

    data class Item(
        val topMessage: TopMessage?,
        val content: ItemContent?,
    )

    data class TopMessage(
        val user: ItemContent.User?,
        val icon: Icon?,
        val type: MessageType,
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

            sealed interface Bluesky : MessageType {
                data object Like : Bluesky

                data object Repost : Bluesky

                data object Follow : Bluesky

                data object Mention : Bluesky

                data object Reply : Bluesky

                data object Quote : Bluesky
            }

            sealed interface XQT : MessageType {
                data object Retweet : XQT

                data object Follow : XQT

                data object Like : XQT

                data object Logo : XQT

                data class Custom(
                    val message: String,
                ) : XQT

                data object Mention : XQT
            }

            sealed interface VVO : MessageType {
                data class Custom(
                    val message: String,
                ) : VVO
            }
        }
    }
}
