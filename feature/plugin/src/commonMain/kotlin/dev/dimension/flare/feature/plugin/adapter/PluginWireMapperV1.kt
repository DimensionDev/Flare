package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.SemanticPostAction
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.semanticPostActionMenu
import dev.dimension.flare.feature.plugin.wire.ArticleV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageRoomV1
import dev.dimension.flare.feature.plugin.wire.DirectMessageV1
import dev.dimension.flare.feature.plugin.wire.GalleryOrientationV1
import dev.dimension.flare.feature.plugin.wire.GalleryV1
import dev.dimension.flare.feature.plugin.wire.HashtagV1
import dev.dimension.flare.feature.plugin.wire.MediaTypeV1
import dev.dimension.flare.feature.plugin.wire.MediaV1
import dev.dimension.flare.feature.plugin.wire.NotificationKindV1
import dev.dimension.flare.feature.plugin.wire.NotificationV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RelationV1
import dev.dimension.flare.feature.plugin.wire.RichTextFormatV1
import dev.dimension.flare.feature.plugin.wire.RichTextV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.SocialListV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.uiArticleContentOf
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlin.time.Instant

internal class PluginWireMapperV1(
    private val pluginId: String,
    private val platformId: String,
    private val accountKey: MicroBlogKey?,
    private val originHost: String,
    private val profileAvailable: Boolean,
    private val postDetailAvailable: Boolean,
    private val postMutationAvailable: Boolean,
    private val composeAvailable: Boolean,
) {
    private val accountType: AccountType = accountKey?.let(AccountType::Specific) ?: AccountType.GuestHost(originHost)

    fun profile(value: ProfileV1): UiProfile {
        value.requireValid()
        value.requireSafeUrls()
        val key = value.key.toMicroBlogKey()
        return UiProfile(
            key = key,
            handle = UiHandle(value.handle, value.key.host),
            avatar = value.avatarUrl.toUiImage(),
            nameInternal = value.displayName.toUiPlainText(),
            platformId = platformId,
            platformIcon = UiIcon.World,
            clickEvent =
                if (profileAvailable) {
                    ClickEvent.Deeplink(DeeplinkRoute.Profile.User(accountType, key))
                } else {
                    ClickEvent.Noop
                },
            banner = value.bannerUrl.toUiImage(),
            description = value.description?.toUi(),
            matrices =
                UiProfile.Matrices(
                    fansCount = value.followersCount ?: 0,
                    followsCount = value.followingCount ?: 0,
                    statusesCount = value.postsCount ?: 0,
                ),
            mark =
                buildList {
                    if (value.locked) add(UiProfile.Mark.Locked)
                    if (value.bot) add(UiProfile.Mark.Bot)
                }.toPersistentList(),
            bottomContent =
                value.fields
                    .takeIf { it.isNotEmpty() }
                    ?.associate { it.name to it.value.toUi() }
                    ?.toPersistentMap()
                    ?.let(UiProfile.BottomContent::Fields),
        )
    }

    fun post(value: PostV1): UiTimelineV2 {
        value.requireValid()
        value.requireSafeUrls()
        return UiTimelineV2.TimelinePostItem(
            post = value.toUiPost(),
            presentation = UiTimelineV2.PostPresentation(repost = value.repost?.toUiPost()),
        )
    }

    fun hashtag(value: HashtagV1): UiHashtag {
        require(value.name.isNotBlank() && value.name.length <= 1_024) { "Invalid plugin hashtag" }
        value.url.requireHttpsUrl()
        return UiHashtag(
            hashtag = value.name,
            description = value.url,
            searchContent = value.name,
        )
    }

    fun relation(value: RelationV1): UiRelation =
        UiRelation(
            following = value.following,
            isFans = value.followedBy,
            blocking = value.blocking,
            muted = value.muting,
        )

    fun socialList(value: SocialListV1): dev.dimension.flare.ui.model.UiList.List {
        require(value.id.isNotBlank() && value.id.length <= 512 && value.title.length <= 4_096) { "Invalid plugin list" }
        return dev.dimension.flare.ui.model.UiList
            .List(id = value.id, title = value.title)
    }

    fun directMessageRoom(value: DirectMessageRoomV1): UiDMRoom {
        require(value.unreadCount >= 0 && value.participants.size <= 256) { "Invalid plugin direct-message room" }
        return UiDMRoom(
            key = value.key.toMicroBlogKey(),
            users = value.participants.map(::profile).toImmutableList(),
            lastMessage = value.lastMessage?.let(::directMessage),
            unreadCount = value.unreadCount.toLong(),
        )
    }

    fun directMessage(value: DirectMessageV1): UiDMItem =
        UiDMItem(
            key = value.key.toMicroBlogKey(),
            user = profile(value.sender),
            content = UiDMItem.Message.Text(value.content.toUi()),
            timestamp = Instant.parse(value.createdAt).toUi(),
            isFromMe = value.fromCurrentAccount,
            sendState = null,
            showSender = true,
            remoteCursor = value.entityToken,
        )

    fun article(value: ArticleV1): UiArticle {
        value.url.requireHttpsUrl()
        value.coverUrl.requireHttpsUrl()
        val richText = value.content.toUi()
        val blocks =
            richText.renderRuns.mapIndexedNotNull { index, content ->
                (content as? RenderContent.Text)?.let {
                    UiArticleBlock.Text(key = "${value.key.id}:$index", content = it)
                }
            }
        return UiArticle(
            key = value.key.id,
            title = value.title,
            content = uiArticleContentOf(blocks, richText.raw),
            cover = value.coverUrl.toUiImage(),
            publishDate = value.createdAt?.let(Instant::parse)?.toUi(),
            author = value.author?.let(::profile)?.let { UiArticleAuthor.Profile(it) },
            sourceUrl = value.url,
        )
    }

    fun gallery(value: GalleryV1): GalleryDetail {
        value.url.requireHttpsUrl()
        val images = value.images.mapNotNull { it.toUi(value.actions.any { action -> action.active == true }) as? UiMedia.Image }
        val bookmark = value.actions.firstOrNull { it.action == SemanticActionV1.Bookmark || it.action == SemanticActionV1.Unbookmark }
        val key = value.key.toMicroBlogKey()
        return GalleryDetail(
            orientation =
                when (value.orientation) {
                    GalleryOrientationV1.Horizontal -> GalleryOrientation.Horizontal
                    GalleryOrientationV1.Vertical -> GalleryOrientation.Vertical
                },
            statusKey = key,
            accountType = accountType,
            url = value.url,
            images = images.toImmutableList(),
            title = value.title,
            author = value.author?.let(::profile),
            createdAt = Instant.parse(value.createdAt).toUi(),
            content = value.content?.toUi(),
            isBookmarked = bookmark?.active == true || bookmark?.action == SemanticActionV1.Unbookmark,
            bookmarkAction =
                accountKey
                    ?.let { account ->
                        bookmark?.toSemanticPostAction()?.let { action ->
                            semanticPostActionMenu(key, action, bookmark.actionToken, bookmark.count ?: 0, account).clickEvent
                        }
                    } ?: ClickEvent.Noop,
            matrix =
                value.actions
                    .mapNotNull { action ->
                        val icon = action.action.toUiIcon() ?: return@mapNotNull null
                        GalleryDetail.Matrix(icon, action.count ?: 0)
                    }.toImmutableList(),
        )
    }

    fun notification(value: NotificationV1): UiTimelineV2 {
        value.post?.let { return post(it) }
        val key = MicroBlogKey(value.id, originHost)
        return UiTimelineV2.Message(
            user = value.actor?.let(::profile),
            statusKey = key,
            icon = value.kind.toUiIcon(),
            type =
                UiTimelineV2.Message.Type.Raw(
                    value.message
                        ?.toUi()
                        ?.raw
                        .orEmpty(),
                ),
            createdAt = Instant.parse(value.createdAt).toUi(),
            clickEvent =
                value.actor
                    ?.takeIf { profileAvailable }
                    ?.let { ClickEvent.Deeplink(DeeplinkRoute.Profile.User(accountType, it.key.toMicroBlogKey())) }
                    ?: ClickEvent.Noop,
            accountType = accountType,
        )
    }

    private fun PostV1.toUiPost(): UiTimelineV2.Post {
        val key = key.toMicroBlogKey()
        val resolvedUrl = url ?: "https://${key.host}/"
        val directActions = mutableListOf<ActionMenu>()
        val overflow = mutableListOf<ActionMenu>()
        actions.filter { it.enabled }.forEach { descriptor ->
            val semantic = descriptor.toSemanticPostAction()
            if (semantic != null && accountKey != null && postMutationAvailable) {
                directActions += semanticPostActionMenu(key, semantic, descriptor.actionToken, descriptor.count ?: 0, accountKey)
            } else if (descriptor.action == SemanticActionV1.Reply && composeAvailable && accountKey != null) {
                directActions +=
                    ActionMenu.Item(
                        icon = UiIcon.Reply,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                        clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Compose.Reply(accountKey, key)),
                        actionFamily = PostActionFamily.Reply,
                    )
            } else if (descriptor.action == SemanticActionV1.Delete && accountKey != null) {
                overflow +=
                    ActionMenu.Item(
                        icon = UiIcon.Delete,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                        color = ActionMenu.Item.Color.Red,
                        clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Status.DeleteConfirm(key, accountType)),
                        actionFamily = PostActionFamily.Delete,
                    )
            }
        }
        if (url != null) {
            overflow +=
                ActionMenu.Item(
                    icon = UiIcon.Share,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                    clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Status.ShareSheet(key, accountType, resolvedUrl)),
                    actionFamily = PostActionFamily.Share,
                )
        }
        if (overflow.isNotEmpty()) {
            directActions +=
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions = overflow.toPersistentList(),
                )
        }
        return UiTimelineV2.Post(
            platformId = platformId,
            images = media.map { it.toUi(sensitive) }.toPersistentList(),
            sensitive = sensitive,
            contentWarning = spoilerText?.takeIf(String::isNotBlank)?.let { UiTranslatableText(it.toUiPlainText()) },
            user = profile(author),
            platformIcon = UiIcon.World,
            content = UiTranslatableText(content.toUi()),
            actions = directActions.toPersistentList(),
            poll = null,
            statusKey = key,
            card = null,
            createdAt = Instant.parse(createdAt).toUi(),
            visibility = visibility.toUiVisibility(),
            references =
                replyTo
                    ?.toMicroBlogKey()
                    ?.let { UiTimelineV2.Post.Reference(it, ReferenceType.Reply) }
                    ?.let(::listOf)
                    .orEmpty()
                    .toPersistentList(),
            clickEvent =
                if (postDetailAvailable) {
                    ClickEvent.Deeplink(DeeplinkRoute.Status.Detail(key, accountType))
                } else {
                    ClickEvent.Noop
                },
            accountType = accountType,
        )
    }
}

private fun dev.dimension.flare.feature.plugin.wire.EntityKeyV1.toMicroBlogKey(): MicroBlogKey = MicroBlogKey(id, host)

private fun RichTextV1.toUi() =
    when (format) {
        RichTextFormatV1.Plain -> value.toUiPlainText()
        RichTextFormatV1.Html -> parseHtml(value).toUi()
    }

private fun MediaV1.toUi(sensitive: Boolean): UiMedia {
    url.requireHttpsUrl()
    previewUrl.requireHttpsUrl()
    return when (type) {
        MediaTypeV1.Image -> UiMedia.Image(url, previewUrl ?: url, description, height?.toFloat() ?: 0f, width?.toFloat() ?: 0f, sensitive)
        MediaTypeV1.Video -> UiMedia.Video(url, previewUrl ?: url, description, height?.toFloat() ?: 0f, width?.toFloat() ?: 0f)
        MediaTypeV1.Gif -> UiMedia.Gif(url, previewUrl ?: url, description, height?.toFloat() ?: 0f, width?.toFloat() ?: 0f)
        MediaTypeV1.Audio -> UiMedia.Audio(url, description, previewUrl)
    }
}

private fun String?.toUiImage(): UiMedia.Image? =
    this?.takeIf(String::isNotBlank)?.also { it.requireHttpsUrl() }?.let {
        UiMedia.Image(it, it, null, 0f, 0f, false)
    }

private fun VisibilityV1.toUiVisibility(): UiTimelineV2.Post.Visibility =
    when (this) {
        VisibilityV1.Public -> UiTimelineV2.Post.Visibility.Public
        VisibilityV1.Unlisted -> UiTimelineV2.Post.Visibility.Home
        VisibilityV1.Followers -> UiTimelineV2.Post.Visibility.Followers
        VisibilityV1.Direct -> UiTimelineV2.Post.Visibility.Specified
    }

private fun dev.dimension.flare.feature.plugin.wire.ActionDescriptorV1.toSemanticPostAction(): SemanticPostAction? =
    when (action) {
        SemanticActionV1.Favourite -> SemanticPostAction.Favourite
        SemanticActionV1.Unfavourite -> SemanticPostAction.Unfavourite
        SemanticActionV1.Repost -> SemanticPostAction.Repost
        SemanticActionV1.Unrepost -> SemanticPostAction.Unrepost
        SemanticActionV1.Bookmark -> SemanticPostAction.Bookmark
        SemanticActionV1.Unbookmark -> SemanticPostAction.Unbookmark
        else -> null
    }

private fun SemanticActionV1.toUiIcon(): UiIcon? =
    when (this) {
        SemanticActionV1.Favourite,
        SemanticActionV1.Unfavourite,
        -> UiIcon.Heart

        SemanticActionV1.Repost,
        SemanticActionV1.Unrepost,
        -> UiIcon.Retweet

        SemanticActionV1.Bookmark,
        SemanticActionV1.Unbookmark,
        -> UiIcon.Bookmark

        SemanticActionV1.Delete -> UiIcon.Delete

        SemanticActionV1.Reply -> UiIcon.Reply

        SemanticActionV1.Follow,
        SemanticActionV1.Unfollow,
        -> UiIcon.Follow

        SemanticActionV1.Block,
        SemanticActionV1.Unblock,
        -> UiIcon.Block

        SemanticActionV1.Mute,
        SemanticActionV1.Unmute,
        -> UiIcon.Mute
    }

private fun NotificationKindV1.toUiIcon(): UiIcon =
    when (this) {
        NotificationKindV1.Mention,
        NotificationKindV1.Reply,
        -> UiIcon.Reply

        NotificationKindV1.Favourite -> UiIcon.Heart

        NotificationKindV1.Repost -> UiIcon.Retweet

        NotificationKindV1.Follow -> UiIcon.Follow

        NotificationKindV1.Other -> UiIcon.Notification
    }

private fun ProfileV1.requireSafeUrls() {
    avatarUrl.requireHttpsUrl()
    bannerUrl.requireHttpsUrl()
    url.requireHttpsUrl()
}

private fun PostV1.requireSafeUrls() {
    url.requireHttpsUrl()
    author.requireSafeUrls()
    media.forEach { media ->
        media.url.requireHttpsUrl()
        media.previewUrl.requireHttpsUrl()
    }
    repost?.requireSafeUrls()
}

private fun String?.requireHttpsUrl() {
    if (isNullOrBlank()) return
    require(length <= 8_192) { "Plugin output URL is too long" }
    val value = Url(this)
    require(value.protocol == URLProtocol.HTTPS && value.user == null && value.password == null) {
        "Plugin output URL must use HTTPS without credentials"
    }
}
