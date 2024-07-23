package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList
import kotlin.jvm.JvmInline

// TODO: Handling item click event internally
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
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("Status")
                        append(statusKey)
                    }

            sealed interface Embed {
                @JvmInline
                value class Quote(
                    val data: Status,
                ) : Embed

                @JvmInline
                value class QuoteList(
                    val data: ImmutableList<Status>,
                ) : Embed

                @JvmInline
                value class Poll(
                    val data: UiPoll,
                ) : Embed

                @JvmInline
                value class Card(
                    val data: UiCard,
                ) : Embed
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

        @JvmInline
        value class User(
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
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("UserList")
                        append(users.hashCode())
                    }
        }
    }

    data class TopMessage(
        val user: UiUserV2?,
        val icon: Icon,
        val type: MessageType,
    ) {
        enum class Icon {
            Retweet,
            Follow,
            Favourite,
            Mention,
            Poll,
            Edit,
            Info,
            Reply,
        }

        sealed interface MessageType {
            sealed interface Mastodon : MessageType {
                data object Reblogged : Mastodon

                data object Follow : Mastodon

                data object Favourite : Mastodon

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

                data class Custom(
                    val message: String,
                ) : XQT

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
