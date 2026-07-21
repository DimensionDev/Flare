package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.network.fanbox.FANBOX_USER_AGENT
import dev.dimension.flare.data.network.fanbox.FANBOX_WEB_URL
import dev.dimension.flare.data.network.fanbox.FanboxCommentItem
import dev.dimension.flare.data.network.fanbox.FanboxCreatorDetailBody
import dev.dimension.flare.data.network.fanbox.FanboxPostDetailBody
import dev.dimension.flare.data.network.fanbox.FanboxPostEntity
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.network.fanbox.FanboxUserEntity
import dev.dimension.flare.data.platform.FANBOX_HOST
import dev.dimension.flare.data.platform.FANBOX_WEB_HOST
import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiArticleAuthor
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiArticleContentGateReason
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.model.uiArticleContentOf
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlin.time.Clock
import kotlin.time.Instant

internal fun fanboxPostKey(id: String): MicroBlogKey = MicroBlogKey(id = id, host = FANBOX_HOST)

internal fun fanboxCreatorKey(id: String): MicroBlogKey = MicroBlogKey(id = id, host = FANBOX_HOST)

internal fun fanboxCommentKey(
    postKey: MicroBlogKey,
    id: String,
): MicroBlogKey = MicroBlogKey(id = "${postKey.id}:comment:$id", host = FANBOX_HOST)

private val FANBOX_ARTICLE_VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v")

private val FANBOX_ARTICLE_URL_REGEX = Regex("""https?://\S+""")

internal suspend fun FanboxService.fanboxImageHeaders(): ImmutableMap<String, String> = currentCredential().toFanboxImageHeaders()

internal fun FanboxCredential.toFanboxImageHeaders(): ImmutableMap<String, String> =
    buildMap {
        put("Origin", "https://$FANBOX_WEB_HOST")
        put("Referer", FANBOX_WEB_URL)
        put("User-Agent", FANBOX_USER_AGENT)
        sessionId.takeIf { it.isNotBlank() }?.let {
            put("Cookie", "FANBOXSESSID=$it")
        }
    }.toPersistentMap()

internal fun FanboxPostEntity.toUiTimeline(
    accountKey: MicroBlogKey,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiTimelineV2.Feed {
    val articleKey = fanboxPostKey(id)
    return UiTimelineV2.Feed(
        title = title,
        description = excerpt.takeIf { it.isNotBlank() },
        url = fanboxPostUrl(creatorId, id),
        createdAt = parseFanboxInstant(publishedDatetime).toUi(),
        source =
            UiTimelineV2.Feed.Source(
                name = user?.name?.takeIf { it.isNotBlank() } ?: creatorId,
                icon = user?.iconUrl,
            ),
        media = cover?.url.toArticleCover(hasAdultContent, imageHeaders),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Article(
                    accountType = AccountType.Specific(accountKey),
                    articleKey = articleKey,
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun FanboxPostDetailBody.toUiTimeline(
    accountKey: MicroBlogKey,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiTimelineV2.Feed {
    val articleKey = fanboxPostKey(id)
    return UiTimelineV2.Feed(
        title = title,
        description = excerpt.takeIf { it.isNotBlank() },
        url = fanboxPostUrl(creatorId, id),
        createdAt = parseFanboxInstant(publishedDatetime).toUi(),
        source =
            UiTimelineV2.Feed.Source(
                name = user?.name?.takeIf { it.isNotBlank() } ?: creatorId,
                icon = user?.iconUrl,
            ),
        media = toArticleCover(imageHeaders),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Article(
                    accountType = AccountType.Specific(accountKey),
                    articleKey = articleKey,
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun FanboxPostDetailBody.toUiArticle(
    accountKey: MicroBlogKey,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiArticle {
    val articleKey = fanboxPostKey(id)
    val author = user?.toUiProfile(accountKey, creatorId, imageHeaders)
    val sourceUrl = fanboxPostUrl(creatorId, id)
    val blocks =
        buildList {
            addAll(
                body
                    ?.toArticleBlocks(
                        postId = id,
                        sensitive = hasAdultContent,
                        imageHeaders = imageHeaders,
                    ).orEmpty()
                    .ifEmpty {
                        excerpt
                            .takeIf { it.isNotBlank() }
                            ?.toArticleTextBlock(key = "$id:excerpt")
                            ?.let { listOf(it) }
                            .orEmpty()
                    },
            )
            if (isRestricted) {
                add(
                    UiArticleBlock.ContentGate(
                        key = "$id:content-gate",
                        reason =
                            UiArticleContentGateReason.SubscriptionRequired(
                                platformType = PlatformType.Fanbox,
                                feeRequired = feeRequired.takeIf { it > 0 },
                            ),
                        actionUrl = sourceUrl,
                    ),
                )
            }
        }
    return UiArticle(
        key = articleKey.toString(),
        title = title,
        content = uiArticleContentOf(blocks),
        cover = toArticleCover(imageHeaders),
        publishDate = parseFanboxInstant(publishedDatetime).toUi(),
        author = author?.let { UiArticleAuthor.Profile(it) },
        sourceUrl = sourceUrl,
    )
}

internal fun FanboxCommentItem.toUiTimeline(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiTimelineV2.Post {
    val statusKey = fanboxCommentKey(postKey, id)
    return UiTimelineV2.Post(
        platformType = PlatformType.Fanbox,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = user?.toUiProfile(accountKey, creatorId = null, imageHeaders),
        content = UiTranslatableText(original = body.toUiPlainText()),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = if (isLiked) UiIcon.Unlike else UiIcon.Like,
                    count = UiNumber(likeCount.toLong()),
                    color = if (isLiked) ActionMenu.Item.Color.Red else null,
                    clickEvent = ClickEvent.Noop,
                    actionFamily = PostActionFamily.Like,
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = parseFanboxInstant(createdDatetime).toUi(),
        visibility = null,
        clickEvent = user?.let { ClickEvent.Deeplink(it.profileRoute(accountKey, null)) } ?: ClickEvent.Noop,
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun FanboxCreatorDetailBody.toUiProfile(
    accountKey: MicroBlogKey? = null,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiProfile {
    val userEntity = user
    val key = fanboxCreatorKey(creatorId)
    return UiProfile(
        key = key,
        handle = UiHandle(raw = creatorId, host = FANBOX_HOST),
        avatar = userEntity?.iconUrl.toUiImage(imageHeaders),
        nameInternal = (userEntity?.name ?: creatorId).toUiPlainText(),
        platformType = PlatformType.Fanbox,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.GuestHost(FANBOX_HOST),
                    userKey = key,
                ),
            ),
        banner = coverImageUrl.toUiImage(imageHeaders),
        description = description.takeIf { it.isNotBlank() }?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark =
            if (isSupported) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
        bottomContent =
            profileLinks
                .takeIf { it.isNotEmpty() }
                ?.mapIndexed { index, link -> "Link ${index + 1}" to link.toUiPlainText() }
                ?.toMap()
                ?.toPersistentMap()
                ?.let { UiProfile.BottomContent.Fields(it) },
    )
}

internal fun FanboxCreatorDetailBody.toUiTimeline(
    accountKey: MicroBlogKey,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiTimelineV2.User {
    val profile = toUiProfile(accountKey, imageHeaders)
    return UiTimelineV2.User(
        value = profile,
        createdAt = Clock.System.now().toUi(),
        statusKey = profile.key,
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun FanboxCredential.toUiProfile(
    accountKey: MicroBlogKey? = null,
    profileKey: MicroBlogKey? = null,
    imageHeaders: ImmutableMap<String, String>? = toFanboxImageHeaders(),
): UiProfile {
    val profileId = creatorId ?: userId
    val key = profileKey ?: fanboxCreatorKey(profileId)
    return UiProfile(
        key = key,
        handle = UiHandle(raw = profileId, host = FANBOX_HOST),
        avatar = iconUrl.toUiImage(imageHeaders),
        nameInternal = (name ?: profileId).toUiPlainText(),
        platformType = PlatformType.Fanbox,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.GuestHost(FANBOX_HOST),
                    userKey = key,
                ),
            ),
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark =
            if (isSupporter || isCreator) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
        bottomContent = null,
    )
}

private fun FanboxUserEntity.toUiProfile(
    accountKey: MicroBlogKey,
    creatorId: String?,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiProfile {
    val profileId = creatorId ?: userId
    val key = fanboxCreatorKey(profileId)
    return UiProfile(
        key = key,
        handle = UiHandle(raw = profileId, host = FANBOX_HOST),
        avatar = iconUrl.toUiImage(imageHeaders),
        nameInternal = (name.ifBlank { profileId }).toUiPlainText(),
        platformType = PlatformType.Fanbox,
        clickEvent = ClickEvent.Deeplink(profileRoute(accountKey, creatorId)),
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun FanboxUserEntity.profileRoute(
    accountKey: MicroBlogKey,
    creatorId: String?,
): DeeplinkRoute =
    DeeplinkRoute.Profile.User(
        accountType = AccountType.Specific(accountKey),
        userKey = fanboxCreatorKey(creatorId ?: userId),
    )

private fun FanboxPostDetailBody.BodyContent.toArticleBlocks(
    postId: String,
    sensitive: Boolean,
    imageHeaders: ImmutableMap<String, String>? = null,
): List<UiArticleBlock> {
    if (blocks.isNotEmpty()) {
        return blocks.flatMapIndexed { index, block ->
            block.toArticleBlocks(
                keyPrefix = "$postId:block:$index",
                content = this,
                sensitive = sensitive,
                imageHeaders = imageHeaders,
            )
        }
    }

    val fallbackBlocks = mutableListOf<UiArticleBlock>()
    text
        ?.takeIf { it.isNotBlank() }
        ?.toArticleTextBlock(key = "$postId:text")
        ?.let(fallbackBlocks::add)
    images.mapIndexedTo(fallbackBlocks) { index, image ->
        image.toArticleImageBlock(
            key = "$postId:image:${image.id.ifBlank { index.toString() }}",
            sensitive = sensitive,
            imageHeaders = imageHeaders,
        )
    }
    files.forEachIndexed { index, file ->
        fallbackBlocks.addAll(
            file.toArticleFileBlocks(
                key = "$postId:file:${file.id.ifBlank { index.toString() }}",
                imageHeaders = imageHeaders,
            ),
        )
    }
    return fallbackBlocks
}

private fun FanboxPostDetailBody.Block.toArticleBlocks(
    keyPrefix: String,
    content: FanboxPostDetailBody.BodyContent,
    sensitive: Boolean,
    imageHeaders: ImmutableMap<String, String>? = null,
): List<UiArticleBlock> {
    text?.takeIf { it.isNotBlank() }?.let {
        return listOf(
            it.toArticleTextBlock(
                key = keyPrefix,
                headingLevel = type.toArticleHeadingLevel(),
            ),
        )
    }
    imageId
        ?.let(content.imageMap::get)
        ?.let {
            return listOf(
                it.toArticleImageBlock(
                    key = "$keyPrefix:image:${it.id}",
                    sensitive = sensitive,
                    imageHeaders = imageHeaders,
                ),
            )
        }
    fileId
        ?.let(content.fileMap::get)
        ?.let {
            return it.toArticleFileBlocks(
                key = "$keyPrefix:file:${it.id}",
                imageHeaders = imageHeaders,
            )
        }
    urlEmbedId
        ?.let(content.urlEmbedMap::get)
        ?.let {
            return listOf(
                it.toArticleEmbedBlock(
                    key = "$keyPrefix:embed:${it.id}",
                ),
            )
        }
    return emptyList()
}

private fun String.toArticleTextBlock(
    key: String,
    headingLevel: Int? = null,
): UiArticleBlock.Text =
    UiArticleBlock.Text(
        key = key,
        content =
            RenderContent.Text(
                runs = toArticleTextRuns().toImmutableList(),
                block = RenderBlockStyle(headingLevel = headingLevel),
            ),
    )

private fun String.toArticleTextRuns(): List<RenderRun.Text> =
    buildList {
        var previousEnd = 0
        FANBOX_ARTICLE_URL_REGEX.findAll(this@toArticleTextRuns).forEach { match ->
            if (match.range.first > previousEnd) {
                add(RenderRun.Text(text = substring(previousEnd, match.range.first)))
            }
            val rawUrl = match.value
            val url = rawUrl.trimEndUrlPunctuation()
            if (url.isNotEmpty()) {
                add(
                    RenderRun.Text(
                        text = url,
                        style = RenderTextStyle(link = url),
                    ),
                )
            }
            val suffix = rawUrl.removePrefix(url)
            if (suffix.isNotEmpty()) {
                add(RenderRun.Text(text = suffix))
            }
            previousEnd = match.range.last + 1
        }
        if (previousEnd < this@toArticleTextRuns.length) {
            add(RenderRun.Text(text = substring(previousEnd)))
        }
        if (isEmpty()) {
            add(RenderRun.Text(text = this@toArticleTextRuns))
        }
    }

private fun String.trimEndUrlPunctuation(): String =
    trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}', '>', '。', '、', '，', '！', '？', '；', '：')

private fun String.toArticleHeadingLevel(): Int? =
    when (this) {
        "header", "h1" -> 1
        "h2" -> 2
        "h3" -> 3
        else -> null
    }

private fun FanboxPostDetailBody.ImageItem.toArticleImageBlock(
    key: String,
    sensitive: Boolean,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiArticleBlock.Image =
    UiArticleBlock.Image(
        key = key,
        media =
            UiMedia.Image(
                url = originalUrl,
                previewUrl = thumbnailUrl.ifBlank { originalUrl },
                description = null,
                height = height.toFloat(),
                width = width.toFloat(),
                sensitive = sensitive,
                customHeaders = imageHeaders,
            ),
    )

private fun FanboxPostDetailBody.FileItem.toArticleFileBlocks(
    key: String,
    imageHeaders: ImmutableMap<String, String>? = null,
): List<UiArticleBlock> =
    buildList {
        if (isArticleVideoFile()) {
            add(toArticleVideoBlock(key = "$key:video", imageHeaders = imageHeaders))
        }
        add(toArticleFileBlock(key = key, imageHeaders = imageHeaders))
    }

private fun FanboxPostDetailBody.FileItem.toArticleVideoBlock(
    key: String,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiArticleBlock.Video =
    UiArticleBlock.Video(
        key = key,
        media =
            UiMedia.Video(
                url = url,
                thumbnailUrl = "",
                description = name.takeIf { it.isNotBlank() },
                height = 9f,
                width = 16f,
                customHeaders = imageHeaders,
            ),
    )

private fun FanboxPostDetailBody.FileItem.toArticleFileBlock(
    key: String,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiArticleBlock.File =
    UiArticleBlock.File(
        key = key,
        name = name,
        url = url,
        sizeBytes = size,
        extension = extension.takeIf { it.isNotBlank() },
        customHeaders = imageHeaders,
    )

private fun FanboxPostDetailBody.FileItem.isArticleVideoFile(): Boolean = articleFileExtension() in FANBOX_ARTICLE_VIDEO_EXTENSIONS

private fun FanboxPostDetailBody.FileItem.articleFileExtension(): String =
    sequenceOf(
        extension,
        name.substringAfterLast('.', missingDelimiterValue = ""),
        url
            .substringBefore("?")
            .substringBefore("#")
            .substringAfterLast('.', missingDelimiterValue = ""),
    ).firstOrNull { it.isNotBlank() }
        .orEmpty()
        .lowercase()

private fun FanboxPostDetailBody.UrlEmbed.toArticleEmbedBlock(key: String): UiArticleBlock.Embed =
    UiArticleBlock.Embed(
        key = key,
        url = postInfo?.let { fanboxPostUrl(it.creatorId, it.id) },
        title = postInfo?.title,
        description = postInfo?.excerpt?.takeIf { it.isNotBlank() },
        imageUrl = postInfo?.cover?.url,
        htmlFallback = html,
    )

private fun String?.toArticleCover(
    sensitive: Boolean,
    imageHeaders: ImmutableMap<String, String>? = null,
): UiMedia.Image? =
    this
        ?.takeIf { it.isNotBlank() }
        ?.let {
            UiMedia.Image(
                url = it,
                previewUrl = it,
                description = null,
                height = 0f,
                width = 0f,
                sensitive = sensitive,
                customHeaders = imageHeaders,
            )
        }

private fun FanboxPostDetailBody.toArticleCover(imageHeaders: ImmutableMap<String, String>? = null): UiMedia.Image? =
    (coverImageUrl ?: imageForShare ?: body?.firstImageUrl()).toArticleCover(hasAdultContent, imageHeaders)

private fun FanboxPostDetailBody.BodyContent.firstImageUrl(): String? =
    images.firstOrNull()?.let { image ->
        image.thumbnailUrl.ifBlank { image.originalUrl }
    }
        ?: blocks.firstNotNullOfOrNull { block ->
            block.imageId
                ?.let(imageMap::get)
                ?.let { image -> image.thumbnailUrl.ifBlank { image.originalUrl } }
        }

private fun fanboxPostUrl(
    creatorId: String,
    postId: String,
): String = "https://www.fanbox.cc/@$creatorId/posts/$postId"

private fun parseFanboxInstant(value: String): Instant =
    runCatching {
        Instant.parse(value)
    }.getOrElse {
        Clock.System.now()
    }
