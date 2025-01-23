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
public data class UiTimeline internal constructor(
    val topMessage: TopMessage?,
    val content: ItemContent?,
) {
    val itemKey: String
        get() =
            buildString {
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
//                append(platformType.name)
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

                        is ItemContent.Feed -> {
                            append("Feed")
                        }
                    }
                }
            }

    public sealed interface ItemContent {
        public val itemKey: String

        public data class Feed internal constructor(
            val title: String,
            val description: String?,
            val url: String,
            val image: String?,
            val createdAt: UiDateTime?,
        ) : ItemContent {
            override val itemKey: String
                get() = "Feed_$url"
        }

        public data class Status internal constructor(
            val platformType: PlatformType,
            val images: ImmutableList<UiMedia>,
            val sensitive: Boolean,
            val contentWarning: UiRichText?,
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
                        append(platformType.name)
                        append("Status")
                        append(statusKey)
                    }

            public sealed interface BottomContent {
                public data class Reaction internal constructor(
                    val emojiReactions: ImmutableList<EmojiReaction>,
                    val myReaction: String?,
                ) : BottomContent {
                    public data class EmojiReaction internal constructor(
                        val name: String,
                        val url: String,
                        val count: Long,
                        val onClicked: () -> Unit,
                        // TODO: make EmojiReaction a sealed interface
                        val isUnicode: Boolean,
                    ) {
                        val humanizedCount: String by lazy {
                            count.humanize()
                        }
                        val isImageReaction: Boolean by lazy {
                            name.startsWith(":") && name.endsWith(":")
                        }
                    }
                }
            }

            public sealed interface TopEndContent {
                public data class Visibility internal constructor(
                    val visibility: Type,
                ) : TopEndContent {
                    public enum class Type {
                        Public,
                        Home,
                        Followers,
                        Specified,
                    }
                }
            }

            public sealed interface AboveTextContent {
                public data class ReplyTo internal constructor(
                    val handle: String,
                ) : AboveTextContent
            }
        }

        public data class User internal constructor(
            val value: UiUserV2,
        ) : ItemContent {
            override val itemKey: String
                get() =
                    buildString {
                        append("User")
                        append(value.key)
                    }
        }

        public data class UserList internal constructor(
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

    public data class TopMessage internal constructor(
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

        public enum class Icon {
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

        public sealed interface MessageType {
            public sealed interface Mastodon : MessageType {
                public data class Reblogged internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Follow internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Favourite internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Mention internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Poll internal constructor(
                    val id: String,
                ) : Mastodon

                public data class FollowRequest internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Status internal constructor(
                    val id: String,
                ) : Mastodon

                public data class Update internal constructor(
                    val id: String,
                ) : Mastodon

                public data class UnKnown internal constructor(
                    val id: String,
                ) : Mastodon
            }

            public sealed interface Misskey : MessageType {
                public data class Follow internal constructor(
                    val id: String,
                ) : Misskey

                public data class Mention internal constructor(
                    val id: String,
                ) : Misskey

                public data class Reply internal constructor(
                    val id: String,
                ) : Misskey

                public data class Renote internal constructor(
                    val id: String,
                ) : Misskey

                public data class Quote internal constructor(
                    val id: String,
                ) : Misskey

                public data class Reaction internal constructor(
                    val id: String,
                ) : Misskey

                public data class PollEnded internal constructor(
                    val id: String,
                ) : Misskey

                public data class ReceiveFollowRequest internal constructor(
                    val id: String,
                ) : Misskey

                public data class FollowRequestAccepted internal constructor(
                    val id: String,
                ) : Misskey

                public data class AchievementEarned internal constructor(
                    val id: String,
                    val achievement: MisskeyAchievement?,
                ) : Misskey

                public data class App internal constructor(
                    val id: String,
                ) : Misskey

                public data class UnKnown internal constructor(
                    val type: String,
                    val id: String,
                ) : Misskey
            }

            public sealed interface Bluesky : MessageType {
                public data object Like : Bluesky

                public data object Repost : Bluesky

                public data object Follow : Bluesky

                public data object Mention : Bluesky

                public data object Reply : Bluesky

                public data object Quote : Bluesky

                public data object UnKnown : Bluesky

                public data object StarterpackJoined : Bluesky
            }

            public sealed interface XQT : MessageType {
                public data object Retweet : XQT

                public data class Custom internal constructor(
                    val message: String,
                    val id: String,
                ) : XQT {
                    override fun toString(): String = "Custom$id"
                }

                public data object Mention : XQT
            }

            public sealed interface VVO : MessageType {
                public data class Custom internal constructor(
                    val message: String,
                ) : VVO

                public data object Like : VVO
            }
        }
    }
}
