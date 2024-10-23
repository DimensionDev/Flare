package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.mapper.MisskeyAchievement
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class UiTimeline internal constructor(
    val topMessage: TopMessage?,
    val content: ItemContent?,
    val platformType: PlatformType,
) {
    val itemKey: String
        get() =
            buildString {
                append(platformType.name)
                if (topMessage != null) {
                    append("withTopMessage")
                    append(topMessage.itemKey)
                }
                if (content != null) {
                    append("withContent")
                    append(content.itemKey)
                }
            }
    val itemType: String
        get() =
            buildString {
                append(platformType.name)
                if (topMessage != null) {
                    append("withTopMessage")
                }
                if (content != null) {
                    append("withContent")
                    when (content) {
                        is ItemContent.Status -> {
                            append("Status")
                        }
                        is ItemContent.User -> {
                            append("User")
                        }
                        is ItemContent.UserList -> {
                            append("UserList")
                        }
                    }
                }
            }

    sealed interface ItemContent {
        val itemKey: String

        data class Status(
            val images: ImmutableList<UiMedia>,
            val sensitive: Boolean,
            val contentWarning: String?,
            val user: UiUserV2?,
            val quote: ImmutableList<Status>,
            val content: UiRichText,
            val actions: ImmutableList<StatusAction>,
            val poll: UiPoll?,
            val statusKey: MicroBlogKey,
            val card: UiCard?,
            val createdAt: UiDateTime,
            val bottomContent: BottomContent? = null,
            val topEndContent: TopEndContent? = null,
            val aboveTextContent: AboveTextContent? = null,
            val onClicked: ClickContext.() -> Unit,
            val onMediaClicked: ClickContext.(media: UiMedia, index: Int) -> Unit,
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("Status")
                        append(statusKey)
                    }

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

            sealed interface AboveTextContent {
                data class ReplyTo(
                    val handle: String,
                ) : AboveTextContent
            }
        }

        data class User(
            val value: UiUserV2,
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("User")
                        append(value.key)
                    }
        }

        data class UserList(
            val users: ImmutableList<UiUserV2>,
            val status: Status? = null,
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("UserList")
                        append(users.hashCode())
                        if (status != null) {
                            append(status.itemKey)
                        }
                    }
        }
    }

    data class TopMessage(
        val user: UiUserV2?,
        val icon: Icon,
        val type: MessageType,
        val onClicked: ClickContext.() -> Unit,
        val statusKey: MicroBlogKey,
    ) {
        val itemKey: String
            get() =
                buildString {
                    append("TopMessage")
                    append(type)
                    if (user != null) {
                        append(user.key)
                    }
                    append(statusKey.toString())
                }

        enum class Icon {
            Retweet,
            Follow,
            Favourite,
            Mention,
            Poll,
            Edit,
            Info,
            Reply,
            Quote,
        }

        sealed interface MessageType {
            sealed interface Mastodon : MessageType {
                data class Reblogged(
                    val id: String,
                ) : Mastodon

                data class Follow(
                    val id: String,
                ) : Mastodon

                data class Favourite(
                    val id: String,
                ) : Mastodon

                data class Mention(
                    val id: String,
                ) : Mastodon

                data class Poll(
                    val id: String,
                ) : Mastodon

                data class FollowRequest(
                    val id: String,
                ) : Mastodon

                data class Status(
                    val id: String,
                ) : Mastodon

                data class Update(
                    val id: String,
                ) : Mastodon

                data class UnKnown(
                    val id: String,
                ) : Mastodon
            }

            sealed interface Misskey : MessageType {
                data class Follow(
                    val id: String,
                ) : Misskey

                data class Mention(
                    val id: String,
                ) : Misskey

                data class Reply(
                    val id: String,
                ) : Misskey

                data class Renote(
                    val id: String,
                ) : Misskey

                data class Quote(
                    val id: String,
                ) : Misskey

                data class Reaction(
                    val id: String,
                ) : Misskey

                data class PollEnded(
                    val id: String,
                ) : Misskey

                data class ReceiveFollowRequest(
                    val id: String,
                ) : Misskey

                data class FollowRequestAccepted(
                    val id: String,
                ) : Misskey

                data class AchievementEarned(
                    val id: String,
                    val achievement: MisskeyAchievement?,
                ) : Misskey

                data class App(
                    val id: String,
                ) : Misskey
            }

            sealed interface Bluesky : MessageType {
                data object Like : Bluesky

                data object Repost : Bluesky

                data object Follow : Bluesky

                data object Mention : Bluesky

                data object Reply : Bluesky

                data object Quote : Bluesky

                data object UnKnown : Bluesky

                data object StarterpackJoined : Bluesky
            }

            sealed interface XQT : MessageType {
                data object Retweet : XQT

                data class Custom(
                    val message: String,
                    val id: String,
                ) : XQT {
                    override fun toString(): String = "Custom$id"
                }

                data object Mention : XQT
            }

            sealed interface VVO : MessageType {
                data class Custom(
                    val message: String,
                ) : VVO

                data object Like : VVO
            }
        }
    }
}
