package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.mapper.MisskeyAchievement
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Immutable
public data class UiTimeline public constructor(
    val topMessage: TopMessage?,
    val content: ItemContent?,
    private val dbKey: String? = null,
) {
    val id: String by lazy {
        itemKey
    }
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
                if (dbKey != null) {
                    append("withDbKey")
                    append(dbKey)
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

    @Immutable
    public sealed class ItemContent {
        public abstract val itemKey: String

        @Immutable
        public data class Feed internal constructor(
            val title: String?,
            val description: String?,
            val url: String,
            val image: String?,
            val createdAt: UiDateTime?,
            val source: String,
            val sourceIcon: String?,
            val imageHeaders: ImmutableMap<String, String>?,
            private val openInBrowser: Boolean,
        ) : ItemContent() {
            val id: String by lazy {
                itemKey
            }
            override val itemKey: String
                get() = "Feed_$url"

            val onClicked: ClickContext.() -> Unit = {
                if (openInBrowser) {
                    launcher.launch(url)
                } else {
                    launcher.launch(DeeplinkRoute.Rss.Detail(url).toUri())
                }
            }
        }

        @Immutable
        public data class Status internal constructor(
            val platformType: PlatformType,
            val images: ImmutableList<UiMedia>,
            val sensitive: Boolean,
            val contentWarning: UiRichText?,
            val user: UiUserV2?,
            val quote: ImmutableList<Status>,
            val content: UiRichText,
            val actions: ImmutableList<ActionMenu>,
            val poll: UiPoll?,
            val statusKey: MicroBlogKey,
            val card: UiCard?,
            val createdAt: UiDateTime,
            val bottomContent: BottomContent? = null,
            val topEndContent: TopEndContent? = null,
            val aboveTextContent: AboveTextContent? = null,
            val parents: ImmutableList<Status> = persistentListOf(),
            val url: String,
            val onClicked: ClickContext.() -> Unit,
            val onMediaClicked: ClickContext.(media: UiMedia, index: Int) -> Unit,
        ) : ItemContent() {
            val id: String by lazy {
                itemKey
            }
            override val itemKey: String
                get() =
                    buildString {
                        append(platformType.name)
                        append("Status")
                        append(statusKey)
                    }

            val shouldExpandTextByDefault: Boolean by lazy {
                (contentWarning == null || contentWarning.isEmpty) && !content.isLongText
            }

            @Immutable
            public sealed class BottomContent {
                @Immutable
                public data class Reaction internal constructor(
                    val emojiReactions: ImmutableList<EmojiReaction>,
                ) : BottomContent() {
                    @Immutable
                    public data class EmojiReaction internal constructor(
                        val name: String,
                        val url: String,
                        val count: UiNumber,
                        val onClicked: () -> Unit,
                        // TODO: make EmojiReaction a sealed class
                        val isUnicode: Boolean,
                        val me: Boolean,
                    ) {
                        val isImageReaction: Boolean by lazy {
                            name.startsWith(":") && name.endsWith(":")
                        }
                    }
                }
            }

            @Immutable
            public sealed class TopEndContent {
                @Immutable
                public data class Visibility internal constructor(
                    val visibility: Type,
                ) : TopEndContent() {
                    @Serializable
                    public enum class Type {
                        Public,
                        Home,
                        Followers,
                        Specified,
                    }
                }
            }

            @Immutable
            public sealed class AboveTextContent {
                @Immutable
                public data class ReplyTo internal constructor(
                    val handle: String,
                ) : AboveTextContent()
            }
        }

        @Immutable
        public data class User internal constructor(
            val value: UiUserV2,
            val button: ImmutableList<Button> = persistentListOf(),
        ) : ItemContent() {
            val id: String by lazy {
                itemKey
            }
            override val itemKey: String
                get() =
                    buildString {
                        append("User")
                        append(value.key)
                    }

            @Immutable
            public sealed class Button {
                @Immutable
                public data class AcceptFollowRequest internal constructor(
                    val onClicked: ClickContext.() -> Unit,
                ) : Button()

                @Immutable
                public data class RejectFollowRequest internal constructor(
                    val onClicked: ClickContext.() -> Unit,
                ) : Button()
            }
        }

        @Immutable
        public data class UserList internal constructor(
            val users: ImmutableList<UiUserV2>,
            val status: Status? = null,
        ) : ItemContent() {
            val id: String by lazy {
                itemKey
            }
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

    @Immutable
    public data class TopMessage internal constructor(
        val user: UiUserV2?,
        val icon: Icon,
        val type: MessageType,
        val onClicked: ClickContext.() -> Unit,
        val statusKey: MicroBlogKey,
    ) {
        val id: String by lazy {
            itemKey
        }
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
            Pin,
        }

        @Immutable
        public sealed class MessageType {
            @Immutable
            public sealed class Mastodon : MessageType() {
                @Immutable
                public data class Reblogged internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Follow internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Favourite internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Mention internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Poll internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class FollowRequest internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Status internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Update internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class UnKnown internal constructor(
                    val id: String,
                ) : Mastodon()

                @Immutable
                public data class Pinned internal constructor(
                    val id: String,
                ) : Mastodon()
            }

            @Immutable
            public sealed class Misskey : MessageType() {
                @Immutable
                public data class Follow internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Mention internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Reply internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Renote internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Quote internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Reaction internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class PollEnded internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class ReceiveFollowRequest internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class FollowRequestAccepted internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class AchievementEarned internal constructor(
                    val id: String,
                    val achievement: MisskeyAchievement?,
                ) : Misskey()

                @Immutable
                public data class App internal constructor(
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class UnKnown internal constructor(
                    val type: String,
                    val id: String,
                ) : Misskey()

                @Immutable
                public data class Pinned internal constructor(
                    val id: String,
                ) : Misskey()
            }

            @Immutable
            public sealed class Bluesky : MessageType() {
                @Immutable
                public data object Like : Bluesky()

                @Immutable
                public data object Repost : Bluesky()

                @Immutable
                public data object Follow : Bluesky()

                @Immutable
                public data object Mention : Bluesky()

                @Immutable
                public data object Reply : Bluesky()

                @Immutable
                public data object Quote : Bluesky()

                @Immutable
                public data object UnKnown : Bluesky()

                @Immutable
                public data object StarterpackJoined : Bluesky()

                @Immutable
                public data object Pinned : Bluesky()
            }

            @Immutable
            public sealed class XQT : MessageType() {
                @Immutable
                public data object Retweet : XQT()

                @Immutable
                public data class Custom internal constructor(
                    val message: String,
                    val id: String,
                ) : XQT() {
                    override fun toString(): String = "Custom$id"
                }

                @Immutable
                public data object Mention : XQT()
            }

            @Immutable
            public sealed class VVO : MessageType() {
                @Immutable
                public data class Custom internal constructor(
                    val message: String,
                ) : VVO()

                @Immutable
                public data object Like : VVO()
            }
        }
    }
}
