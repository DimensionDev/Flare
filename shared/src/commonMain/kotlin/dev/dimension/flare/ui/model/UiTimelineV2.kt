package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.mapper.fromRss
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Instant

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
    public abstract val renderHash: Int

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
        override val renderHash: Int by lazy {
            renderHashBuilder()
                .add(itemKey)
                .add(user?.renderSummaryHash())
                .add(statusKey)
                .add(icon)
                .add(type.renderSummaryHash())
                .add(createdAt.value)
                .add(accountType)
                .build()
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
                    Like,
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
        val descriptionHtml: String? = null,
        val url: String,
        internal val sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
        @Transient
        public val translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
        override val createdAt: UiDateTime,
        val source: Source,
        val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
        val media: UiMedia.Image? = null,
        private val clickEvent: ClickEvent =
            when (displayMode) {
                RssDisplayMode.OPEN_IN_BROWSER -> {
                    ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(url))
                }

                RssDisplayMode.FULL_CONTENT -> {
                    ClickEvent.Deeplink(DeeplinkRoute.Rss.Detail(url))
                }

                RssDisplayMode.DESCRIPTION_ONLY -> {
                    ClickEvent.Deeplink(DeeplinkRoute.Rss.Detail(url, descriptionHtml, title))
                }
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
        override val renderHash: Int by lazy {
            renderHashBuilder()
                .add(itemKey)
                .add(title)
                .add(description)
                .add(url)
                .add(sourceLanguages)
                .add(translationDisplayState)
                .add(createdAt.value)
                .add(source.name)
                .add(source.icon)
                .add(displayMode)
                .add(media?.renderSummaryHash())
                .add(accountType)
                .build()
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
        override val renderHash: Int by lazy {
            renderHashBuilder()
                .add(itemKey)
                .add(message?.renderHash)
                .add(platformType)
                .add(images.renderSummaryHash { it.renderSummaryHash() })
                .add(sensitive)
                .add(contentWarning?.renderSummaryHash())
                .add(sourceLanguages)
                .add(translationDisplayState)
                .add(quote.renderSummaryHash { it.renderSummaryHash() })
                .add(content.renderSummaryHash())
                .add(actions.renderSummaryHash { it.renderSummaryHash() })
                .add(poll?.renderSummaryHash())
                .add(statusKey)
                .add(card?.renderSummaryHash())
                .add(createdAt.value)
                .add(emojiReactions.renderSummaryHash { it.renderSummaryHash() })
                .add(sourceChannel?.id)
                .add(sourceChannel?.name)
                .add(visibility)
                .add(replyToHandle)
                .add(references.renderSummaryHash { it.renderSummaryHash() })
                .add(parents.renderSummaryHash { it.renderSummaryHash() })
                .add(internalRepost?.renderSummaryHash())
                .add(accountType)
                .build()
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
        override val renderHash: Int by lazy {
            renderHashBuilder()
                .add(itemKey)
                .add(message?.renderHash)
                .add(value.renderSummaryHash())
                .add(createdAt.value)
                .add(statusKey)
                .add(button.renderSummaryHash { it.renderSummaryHash() })
                .add(accountType)
                .build()
        }
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
        override val renderHash: Int by lazy {
            renderHashBuilder()
                .add(itemKey)
                .add(message?.renderHash)
                .add(users.renderSummaryHash { it.renderSummaryHash() })
                .add(createdAt.value)
                .add(statusKey)
                .add(post?.renderSummaryHash())
                .add(accountType)
                .build()
        }
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

private fun renderHashBuilder(): RenderHashBuilder = RenderHashBuilder()

private class RenderHashBuilder {
    private var result: Int = 17

    fun add(value: Any?): RenderHashBuilder {
        result = 31 * result + (value?.hashCode() ?: 0)
        return this
    }

    fun build(): Int = result
}

private fun UiTimelineV2.Message.Type.renderSummaryHash(): Int =
    when (this) {
        is UiTimelineV2.Message.Type.Localized -> {
            renderHashBuilder()
                .add(data)
                .add(args)
                .build()
        }

        is UiTimelineV2.Message.Type.Raw -> {
            renderHashBuilder()
                .add(content)
                .build()
        }

        is UiTimelineV2.Message.Type.Unknown -> {
            renderHashBuilder()
                .add(rawType)
                .build()
        }
    }

private fun UiTimelineV2.Post.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(itemKey)
        .add(message?.renderHash)
        .add(platformType)
        .add(user?.renderSummaryHash())
        .add(contentWarning?.renderSummaryHash())
        .add(content.renderSummaryHash())
        .add(images.renderSummaryHash { it.renderSummaryHash() })
        .add(sensitive)
        .add(poll?.renderSummaryHash())
        .add(card?.renderSummaryHash())
        .add(emojiReactions.renderSummaryHash { it.renderSummaryHash() })
        .add(replyToHandle)
        .add(sourceChannel?.id)
        .add(sourceChannel?.name)
        .add(visibility)
        .add(translationDisplayState)
        .add(actions.renderSummaryHash { it.renderSummaryHash() })
        .add(createdAt.value)
        .build()

private fun UiProfile.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(key)
        .add(handle.raw)
        .add(handle.host)
        .add(avatar)
        .add(name.raw)
        .add(platformType)
        .add(banner)
        .add(description?.renderSummaryHash())
        .add(translationDisplayState)
        .add(matrices.fansCount)
        .add(matrices.followsCount)
        .add(matrices.statusesCount)
        .add(matrices.platformFansCount)
        .add(mark)
        .add(bottomContent?.renderSummaryHash())
        .build()

private fun UiProfile.BottomContent.renderSummaryHash(): Int =
    when (this) {
        is UiProfile.BottomContent.Fields -> {
            fields.entries
                .sortedBy { it.key }
                .fold(renderHashBuilder()) { builder, entry ->
                    builder
                        .add(entry.key)
                        .add(entry.value.renderSummaryHash())
                }.build()
        }

        is UiProfile.BottomContent.Iconify -> {
            items.entries
                .sortedBy { it.key.name }
                .fold(renderHashBuilder()) { builder, entry ->
                    builder
                        .add(entry.key)
                        .add(entry.value.renderSummaryHash())
                }.build()
        }
    }

private fun UiPoll.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(id)
        .add(options.renderSummaryHash { it.renderSummaryHash() })
        .add(multiple)
        .add(ownVotes)
        .add(expired)
        .add(voted)
        .add(canVote)
        .build()

private fun UiPoll.Option.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(title)
        .add(votesCount)
        .add(percentage.toBits())
        .build()

private fun UiCard.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(title)
        .add(description)
        .add(media?.renderSummaryHash())
        .add(url)
        .build()

private fun UiMedia.renderSummaryHash(): Int =
    when (this) {
        is UiMedia.Audio -> {
            renderHashBuilder()
                .add(url)
                .add(description)
                .add(previewUrl)
                .build()
        }

        is UiMedia.Gif -> {
            renderHashBuilder()
                .add(url)
                .add(previewUrl)
                .add(description)
                .add(height.toBits())
                .add(width.toBits())
                .build()
        }

        is UiMedia.Image -> {
            renderHashBuilder()
                .add(url)
                .add(previewUrl)
                .add(description)
                .add(height.toBits())
                .add(width.toBits())
                .add(sensitive)
                .build()
        }

        is UiMedia.Video -> {
            renderHashBuilder()
                .add(url)
                .add(thumbnailUrl)
                .add(description)
                .add(height.toBits())
                .add(width.toBits())
                .build()
        }
    }

private fun UiRichText.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(raw)
        .add(innerText)
        .add(imageUrls)
        .build()

private fun UiTimelineV2.Post.EmojiReaction.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(name)
        .add(url)
        .add(count)
        .add(isUnicode)
        .add(me)
        .build()

private fun UiTimelineV2.Post.Reference.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(statusKey)
        .add(type)
        .build()

private fun ActionMenu.renderSummaryHash(): Int =
    when (this) {
        ActionMenu.Divider -> {
            renderHashBuilder()
                .add("divider")
                .build()
        }

        is ActionMenu.Group -> {
            renderHashBuilder()
                .add(displayItem.renderSummaryHash())
                .add(actions.renderSummaryHash { it.renderSummaryHash() })
                .build()
        }

        is ActionMenu.Item -> {
            renderSummaryHash()
        }
    }

private fun ActionMenu.Item.renderSummaryHash(): Int =
    renderHashBuilder()
        .add(updateKey)
        .add(icon)
        .add(text.renderSummaryHash())
        .add(count)
        .add(color)
        .build()

private fun ActionMenu.Item.Text?.renderSummaryHash(): Int =
    when (this) {
        null -> {
            0
        }

        is ActionMenu.Item.Text.Localized -> {
            renderHashBuilder()
                .add(type)
                .add(parameters)
                .build()
        }

        is ActionMenu.Item.Text.Raw -> {
            renderHashBuilder()
                .add(text)
                .build()
        }
    }

private inline fun <T> Iterable<T>.renderSummaryHash(hash: (T) -> Int): Int =
    fold(renderHashBuilder()) { builder, item ->
        builder.add(hash(item))
    }.build()
