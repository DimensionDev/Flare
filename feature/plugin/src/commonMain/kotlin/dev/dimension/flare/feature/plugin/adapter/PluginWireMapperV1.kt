package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.SemanticPostAction
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.datasource.microblog.semanticPostActionMenu
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
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
import dev.dimension.flare.feature.plugin.wire.PostDetailTypeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.RelationV1
import dev.dimension.flare.feature.plugin.wire.RichTextFormatV1
import dev.dimension.flare.feature.plugin.wire.RichTextV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.SocialListV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.feature.plugin.wire.validatedHtml
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
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.uiArticleContentOf
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.render.RenderContent
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
    private val capabilities: PluginSessionCapabilitiesV1,
    private val mediaNetwork: Boolean = false,
    private val platformIcon: IconType = UiIcon.World.asType(),
) {
    private val accountType: AccountType = accountKey?.let(AccountType::Specific) ?: AccountType.GuestHost(originHost)
    private val mediaProxy = ProxyRef.forAccountType(accountType)

    fun cachedProfile(value: ProfileV1): UiProfile =
        profile(
            if (mediaNetwork) {
                value
            } else {
                value.copy(
                    avatarUrl = null,
                    bannerUrl = null,
                    description = value.description?.withoutMedia(),
                    fields = value.fields.map { it.copy(value = it.value.withoutMedia()) },
                )
            },
        )

    private fun RichTextV1.withoutMedia(): RichTextV1 =
        when (format) {
            RichTextFormatV1.Plain -> this
            RichTextFormatV1.Html -> RichTextV1(value = validatedHtml("https://$originHost/").text())
        }

    fun profile(value: ProfileV1): UiProfile {
        value.requireValid()
        val key =
            MicroBlogKey(
                id = value.key.id,
                host = if (value.key.host.equals(originHost, ignoreCase = true)) originHost else value.key.host,
            )
        val handleHost =
            value.handle
                .trim()
                .removePrefix("@")
                .substringAfterLast('@', missingDelimiterValue = value.key.host)
                .takeIf(String::isNotBlank)
                ?: value.key.host
        return UiProfile(
            pluginId = pluginId,
            key = key,
            handle = UiHandle(value.handle, handleHost),
            avatar = value.avatarUrl.toUiImage(),
            nameInternal = value.displayName.toUiPlainText(),
            platformId = platformId,
            platformIcon = platformIcon,
            clickEvent =
                if (capabilities.canExpose(PluginAbiV1.Capabilities.PROFILE)) {
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

    fun post(value: PostV1): UiTimelineV2.TimelinePostItem {
        value.requireValid()
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
            pluginId = pluginId,
            key = value.key.toMicroBlogKey(),
            users = value.participants.map(::profile).toImmutableList(),
            lastMessage = value.lastMessage?.let(::directMessage),
            unreadCount = value.unreadCount.toLong(),
        )
    }

    fun directMessage(value: DirectMessageV1): UiDMItem =
        UiDMItem(
            pluginId = pluginId,
            key = value.key.toMicroBlogKey(),
            user = profile(value.sender),
            content = UiDMItem.Message.Text(value.content.toUi()),
            timestamp = Instant.parse(value.createdAt).toUi(),
            isFromMe = value.fromCurrentAccount,
            sendState = null,
            showSender = true,
        )

    fun article(value: ArticleV1): UiArticle {
        value.url.requireHttpsUrl()
        value.coverUrl.requireHttpsUrl()
        val richText = value.content.toUi()
        val blocks =
            richText.renderRuns.mapIndexed { index, content ->
                val blockKey = "${value.key.id}:$index"
                when (content) {
                    is RenderContent.Text -> UiArticleBlock.Text(key = blockKey, content = content)
                    is RenderContent.BlockImage -> UiArticleBlock.Image(key = blockKey, media = content.media)
                }
            }
        return UiArticle(
            pluginId = pluginId,
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
        val bookmark =
            value.actions.firstOrNull {
                it.action == SemanticActionV1.Bookmark || it.action == SemanticActionV1.Unbookmark
            }
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
                    ?.takeIf { capabilities.hasOperation(PluginAbiV1.Capabilities.POST, "mutate") }
                    ?.let { account ->
                        bookmark?.takeIf { it.enabled }?.toSemanticPostAction()?.let { action ->
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
        val key = MicroBlogKey(value.id, originHost)
        val message =
            UiTimelineV2.Message(
                user = value.actor?.let(::profile),
                statusKey = key,
                icon = value.kind.toUiIcon(),
                type =
                    value.message?.let { UiTimelineV2.Message.Type.Raw(it.toUi().raw) }
                        ?: value.kind.toMessageType(),
                createdAt = Instant.parse(value.createdAt).toUi(),
                clickEvent =
                    value.actor
                        ?.takeIf { capabilities.canExpose(PluginAbiV1.Capabilities.PROFILE) }
                        ?.let { ClickEvent.Deeplink(DeeplinkRoute.Profile.User(accountType, it.key.toMicroBlogKey())) }
                        ?: ClickEvent.Noop,
                accountType = accountType,
            )
        return value.post?.let { content ->
            val item = post(content)
            item.copy(presentation = item.presentation.copy(message = message, notificationKey = key))
        } ?: message
    }

    private fun PostV1.toUiPost(): UiTimelineV2.Post {
        val key = key.toMicroBlogKey()
        val detailRoute =
            when (detailType) {
                PostDetailTypeV1.Post -> {
                    DeeplinkRoute.Status
                        .Detail(
                            key,
                            accountType,
                        ).takeIf { capabilities.canExpose(PluginAbiV1.Capabilities.POST) }
                }

                PostDetailTypeV1.Article -> {
                    DeeplinkRoute
                        .Article(
                            accountType,
                            key,
                        ).takeIf { capabilities.canExpose(PluginAbiV1.Capabilities.ARTICLE) }
                }

                PostDetailTypeV1.Gallery -> {
                    DeeplinkRoute.Gallery.Detail(key, accountType).takeIf {
                        capabilities.canExpose(PluginAbiV1.Capabilities.GALLERY)
                    }
                }

                PostDetailTypeV1.Link -> {
                    DeeplinkRoute.OpenLinkDirectly(requireNotNull(url))
                }
            }
        val resolvedUrl = url ?: "https://${key.host}/"
        val directActions = mutableListOf<ActionMenu>()
        val overflow = mutableListOf<ActionMenu>()
        actions.filter { it.enabled }.forEach { descriptor ->
            val semantic = descriptor.toSemanticPostAction()
            if (semantic != null && accountKey != null && capabilities.hasOperation(PluginAbiV1.Capabilities.POST, "mutate")) {
                directActions += semanticPostActionMenu(key, semantic, descriptor.actionToken, descriptor.count ?: 0, accountKey)
            } else if (descriptor.action == SemanticActionV1.Reply && capabilities.canExpose(PluginAbiV1.Capabilities.COMPOSE) &&
                accountKey != null
            ) {
                directActions +=
                    ActionMenu.Item(
                        icon = UiIcon.Reply,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                        clickEvent = ClickEvent.Deeplink(DeeplinkRoute.Compose.Reply(accountKey, key)),
                        actionFamily = PostActionFamily.Reply,
                    )
            } else if (descriptor.action == SemanticActionV1.Delete && accountKey != null &&
                capabilities.hasOperation(PluginAbiV1.Capabilities.POST, "delete")
            ) {
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
            pluginId = pluginId,
            platformId = platformId,
            images = media.map { it.toUi(sensitive) }.toPersistentList(),
            sensitive = sensitive,
            contentWarning = spoilerText?.takeIf(String::isNotBlank)?.let { UiTranslatableText(it.toUiPlainText()) },
            user = author?.let(::profile),
            platformIcon = platformIcon,
            content = UiTranslatableText(content.toUi()),
            actions = directActions.toPersistentList(),
            poll = null,
            statusKey = key,
            card = null,
            createdAt = Instant.parse(createdAt).toUi(),
            visibility = visibility?.toUiVisibility(),
            references =
                replyTo
                    ?.toMicroBlogKey()
                    ?.let { UiTimelineV2.Post.Reference(it, ReferenceType.Reply) }
                    ?.let(::listOf)
                    .orEmpty()
                    .toPersistentList(),
            clickEvent = detailRoute?.let { ClickEvent.Deeplink(it) } ?: ClickEvent.Noop,
            mediaClickPolicy =
                if (detailType == PostDetailTypeV1.Post) {
                    UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia
                } else {
                    UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent
                },
            accountType = accountType,
        )
    }

    private fun RichTextV1.toUi() =
        when (format) {
            RichTextFormatV1.Plain -> {
                value.toUiPlainText()
            }

            RichTextFormatV1.Html -> {
                val html = validatedHtml("https://$originHost/")
                require(mediaNetwork || (html.getElementsByTag("img").isEmpty() && html.getElementsByTag("emoji").isEmpty())) {
                    "Plugin has no media network permission"
                }
                html.toUi().withProxy(mediaProxy)
            }
        }

    private fun requireMediaUrl(value: String?) {
        value.requireHttpsUrl()
        require(value.isNullOrBlank() || mediaNetwork) { "Plugin has no media network permission" }
    }

    private fun MediaV1.toUi(sensitive: Boolean): UiMedia {
        requireMediaUrl(url)
        requireMediaUrl(previewUrl)
        return when (type) {
            MediaTypeV1.Image -> {
                UiMedia.Image(
                    url,
                    previewUrl ?: url,
                    description,
                    height?.toFloat() ?: 0f,
                    width?.toFloat() ?: 0f,
                    sensitive,
                )
            }

            MediaTypeV1.Video -> {
                UiMedia.Video(url, previewUrl ?: url, description, height?.toFloat() ?: 0f, width?.toFloat() ?: 0f)
            }

            MediaTypeV1.Gif -> {
                UiMedia.Gif(url, previewUrl ?: url, description, height?.toFloat() ?: 0f, width?.toFloat() ?: 0f)
            }

            MediaTypeV1.Audio -> {
                UiMedia.Audio(url, description, previewUrl)
            }
        }.withProxy(mediaProxy)
    }

    private fun String?.toUiImage(): UiMedia.Image? {
        requireMediaUrl(this)
        return this?.takeIf(String::isNotBlank)?.let { UiMedia.Image(it, it, null, 0f, 0f, false, proxy = mediaProxy) }
    }
}

private fun dev.dimension.flare.feature.plugin.wire.EntityKeyV1.toMicroBlogKey(): MicroBlogKey = MicroBlogKey(id, host)

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

private fun NotificationKindV1.toMessageType(): UiTimelineV2.Message.Type {
    val id =
        when (this) {
            NotificationKindV1.Mention -> UiTimelineV2.Message.Type.Localized.MessageId.Mention
            NotificationKindV1.Reply -> UiTimelineV2.Message.Type.Localized.MessageId.Reply
            NotificationKindV1.Favourite -> UiTimelineV2.Message.Type.Localized.MessageId.Favourite
            NotificationKindV1.Repost -> UiTimelineV2.Message.Type.Localized.MessageId.Repost
            NotificationKindV1.Follow -> UiTimelineV2.Message.Type.Localized.MessageId.Follow
            NotificationKindV1.Other -> return UiTimelineV2.Message.Type.Raw("")
        }
    return UiTimelineV2.Message.Type.Localized(id)
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

private fun String?.requireHttpsUrl() {
    if (isNullOrBlank()) return
    require(length <= 8_192) { "Plugin output URL is too long" }
    val value = Url(this)
    require(value.protocol == URLProtocol.HTTPS && value.user == null && value.password == null) {
        "Plugin output URL must use HTTPS without credentials"
    }
}
