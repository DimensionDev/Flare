package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed class UiTimelineV2 {
    public val id: String by lazy {
        itemKey
    }

    public abstract val itemKey: String

    public val itemType: String
        get() =
            when (this) {
                is Feed -> "feed"
                is Post -> "post"
                is User -> "user"
                is UserList -> "user_list"
                is Message -> "message"
            }

    @Serializable
    @Immutable
    public data class Message internal constructor(
        private val _id: String,
        val user: UiProfile? = null,
        val statusKey: MicroBlogKey? = null,
        val icon: UiIcon,
        val type: Type,
        private val clickEvent: ClickEvent,
    ) : UiTimelineV2() {
        override val itemKey: String = "Message_${_id}"

        @Serializable
        @Immutable
        public sealed class Type {
            @Serializable
            @Immutable
            public data class Unknown internal constructor(
                val rawType: String,
            ) : Type()

            @Serializable
            @Immutable
            public data class Raw internal constructor(
                val content: String,
            ) : Type()

            @Serializable
            @Immutable
            public data class Localized internal constructor(
                val data: MessageId,
                val args: ImmutableList<String> = persistentListOf(),
            ) : Type() {
                @Serializable
                @Immutable
                public enum class MessageId {
                    Mention,
                    NewPost,
                    Repost,
                    Follow,
                    FollowRequest,
                    Favourite,
                    PollEnded,
                    PostUpdated,
                    Reply,
                    Quote,
                    Reaction,
                    FollowRequestAccepted,
                    AchievementEarned,
                    App,
                    StarterpackJoined,
                    Pinned,
                }
            }
        }
    }

    @Serializable
    @Immutable
    public data class Feed internal constructor(
        val title: String?,
        val description: String?,
        val url: String,
        val image: String?,
        val createdAt: UiDateTime?,
        val source: Source,
        val imageHeaders: ImmutableMap<String, String>?,
        val openInBrowser: Boolean,
        private val clickEvent: ClickEvent =
            if (openInBrowser) {
                ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(url))
            } else {
                ClickEvent.Deeplink(DeeplinkRoute.Rss.Detail(url))
            },
    ) : UiTimelineV2() {
        override val itemKey: String
            get() =
                buildString {
                    append("Feed_")
                    append(url)
                }

        @Serializable
        @Immutable
        public data class Source internal constructor(
            val name: String,
            val icon: String?,
        )
    }

    @Serializable
    @Immutable
    public data class Post internal constructor(
        val message: Message? = null,
        val platformType: PlatformType,
        val images: ImmutableList<UiMedia>,
        val sensitive: Boolean,
        val contentWarning: UiRichText?,
        val user: UiProfile?,
        val quote: ImmutableList<Post> = persistentListOf(),
        val content: UiRichText,
        val actions: ImmutableList<ActionMenu>,
        val poll: UiPoll?,
        val statusKey: MicroBlogKey,
        val card: UiCard?,
        val createdAt: UiDateTime,
        val emojiReactions: ImmutableList<EmojiReaction> = persistentListOf(),
        val sourceChannel: SourceChannel? = null,
        val visibility: Visibility? = null,
        val replyToHandle: String? = null,
        val parents: ImmutableList<Post> = persistentListOf(),
        private val clickEvent: ClickEvent,
        private val mediaClickEvent: ClickEvent,
    ) : UiTimelineV2() {
        override val itemKey: String
            get() =
                buildString {
                    append(platformType.name)
                    append("_")
                    append(statusKey)
                    message?.let {
                        append("_")
                        append(it.itemKey)
                    }
                }

        val shouldExpandTextByDefault: Boolean by lazy {
            (contentWarning == null || contentWarning.isEmpty) && !content.isLongText
        }

        @Serializable
        @Immutable
        public data class SourceChannel(
            val id: String,
            val name: String,
        )

        @Serializable
        @Immutable
        public data class EmojiReaction internal constructor(
            val name: String,
            val url: String,
            val count: UiNumber,
            private val clickEvent: ClickEvent,
            val isUnicode: Boolean,
            val me: Boolean,
        ) {
            val isImageReaction: Boolean by lazy {
                name.startsWith(":") && name.endsWith(":")
            }
        }

        @Serializable
        public enum class Visibility {
            Public,
            Home,
            Followers,
            Specified,
            Channel,
        }
    }

    @Serializable
    @Immutable
    public data class User internal constructor(
        val message: Message? = null,
        val value: UiProfile,
        val button: ImmutableList<Button> = persistentListOf(),
    ) : UiTimelineV2() {
        override val itemKey: String
            get() =
                buildString {
                    append("User_")
                    append(value.key)
                    message?.let {
                        append("_")
                        append(it.itemKey)
                    }
                }

        @Serializable
        @Immutable
        public sealed class Button {
            @Serializable
            @Immutable
            public data class AcceptFollowRequest internal constructor(
                private val clickEvent: ClickEvent,
            ) : Button()

            @Serializable
            @Immutable
            public data class RejectFollowRequest internal constructor(
                private val clickEvent: ClickEvent,
            ) : Button()
        }
    }

    @Serializable
    @Immutable
    public data class UserList internal constructor(
        val message: Message? = null,
        val users: ImmutableList<UiProfile>,
        val status: Post? = null,
    ) : UiTimelineV2() {
        override val itemKey: String
            get() =
                buildString {
                    append("UserList_")
                    append(users.hashCode())
                    status?.let {
                        append("_")
                        append(it.itemKey)
                    }
                    message?.let {
                        append("_")
                        append(it.itemKey)
                    }
                }
    }
}
