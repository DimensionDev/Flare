package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
public sealed class UiTimelineV2 {
    public val id: String? by lazy {
        itemKey
    }

    internal abstract val searchText: String?
    internal abstract val statusKey: MicroBlogKey
    public abstract val createdAt: UiDateTime
    internal abstract val accountType: AccountType

    public abstract val itemKey: String?

    @Transient
    public val itemType: String =
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
        val user: UiProfile? = null,
        override val statusKey: MicroBlogKey,
        val icon: UiIcon,
        @SerialName("messageType")
        val type: Type,
        override val createdAt: UiDateTime,
        private val clickEvent: ClickEvent,
        override val accountType: AccountType,
        @Transient
        override val itemKey: String? = null,
    ) : UiTimelineV2() {
        override val searchText: String? = null
        val onClicked: ClickContext.() -> Unit by lazy {
            clickEvent.onClicked
        }

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
                val args: SerializableImmutableList<String> = persistentListOf(),
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
        internal val sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
        @Transient
        public val translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
        override val createdAt: UiDateTime,
        val source: Source,
        val openInBrowser: Boolean,
        val media: UiMedia.Image? = null,
        private val clickEvent: ClickEvent =
            if (openInBrowser) {
                ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(url))
            } else {
                ClickEvent.Deeplink(DeeplinkRoute.Rss.Detail(url))
            },
        override val accountType: AccountType,
        @Transient
        override val itemKey: String? = null,
    ) : UiTimelineV2() {
        val actualCreatedAt: UiDateTime? by lazy {
            if (createdAt.value == Instant.fromEpochMilliseconds(0L)) {
                null
            } else {
                createdAt
            }
        }
        override val statusKey: MicroBlogKey = MicroBlogKey.fromRss(url)
        override val searchText: String =
            buildString {
                title?.let {
                    append(it)
                    append(" ")
                }
                description?.let {
                    append(it)
                }
            }
        val onClicked: ClickContext.() -> Unit by lazy {
            clickEvent.onClicked
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
        val images: SerializableImmutableList<UiMedia>,
        val sensitive: Boolean,
        val contentWarning: UiRichText?,
        val user: UiProfile?,
        internal val sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
        @Transient
        public val translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
        @Transient
        val quote: SerializableImmutableList<Post> = persistentListOf(),
        val content: UiRichText,
        val actions: SerializableImmutableList<ActionMenu>,
        val poll: UiPoll?,
        public override val statusKey: MicroBlogKey,
        val card: UiCard?,
        override val createdAt: UiDateTime,
        val emojiReactions: SerializableImmutableList<EmojiReaction> = persistentListOf(),
        val sourceChannel: SourceChannel? = null,
        val visibility: Visibility? = null,
        val replyToHandle: String? = null,
        internal val references: SerializableImmutableList<Reference> = persistentListOf(),
        @Transient
        val parents: SerializableImmutableList<Post> = persistentListOf(),
        @Transient
        internal val internalRepost: Post? = null,
        internal val clickEvent: ClickEvent,
        public override val accountType: AccountType,
        @Transient
        override val itemKey: String? = null,
    ) : UiTimelineV2() {
        override val searchText: String =
            buildString {
                user?.name?.raw?.let {
                    append(it)
                    append(" ")
                }
                contentWarning?.raw?.let {
                    append(it)
                    append(" ")
                }
                content.raw.let {
                    append(it)
                    append(" ")
                }
                quote.forEach { post ->
                    post.content.raw.let { quoteContent ->
                        append(quoteContent)
                        append(" ")
                    }
                }
            }
        val onClicked: ClickContext.() -> Unit by lazy {
            clickEvent.onClicked
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
            val onClicked: ClickContext.() -> Unit by lazy {
                clickEvent.onClicked
            }
            val isImageReaction: Boolean by lazy {
                name.startsWith(":") && name.endsWith(":")
            }
        }

        @Serializable
        @Immutable
        internal data class Reference internal constructor(
            val statusKey: MicroBlogKey,
            val type: ReferenceType,
        )

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
        override val createdAt: UiDateTime,
        override val statusKey: MicroBlogKey,
        val button: SerializableImmutableList<ActionMenu.Item> = persistentListOf(),
        override val accountType: AccountType,
        @Transient
        override val itemKey: String? = null,
    ) : UiTimelineV2() {
        override val searchText: String? = null
    }

    @Serializable
    @Immutable
    public data class UserList internal constructor(
        val message: Message?,
        val users: SerializableImmutableList<UiProfile>,
        override val createdAt: UiDateTime,
        override val statusKey: MicroBlogKey,
        val post: Post?,
        override val accountType: AccountType,
        @Transient
        override val itemKey: String? = null,
    ) : UiTimelineV2() {
        override val searchText: String? = null
    }
}

internal fun UiTimelineV2.withItemKey(itemKey: String?): UiTimelineV2 =
    when (this) {
        is UiTimelineV2.Feed -> copy(itemKey = itemKey)
        is UiTimelineV2.Message -> copy(itemKey = itemKey)
        is UiTimelineV2.Post -> copy(itemKey = itemKey)
        is UiTimelineV2.User -> copy(itemKey = itemKey)
        is UiTimelineV2.UserList -> copy(itemKey = itemKey)
    }
