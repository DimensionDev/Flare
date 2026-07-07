package dev.dimension.flare.data.datasource.tumblr

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.tumblr.TumblrBlog
import dev.dimension.flare.data.network.tumblr.TumblrLegacyPhoto
import dev.dimension.flare.data.network.tumblr.TumblrLegacyPhotoSize
import dev.dimension.flare.data.network.tumblr.TumblrNote
import dev.dimension.flare.data.network.tumblr.TumblrNpfBlock
import dev.dimension.flare.data.network.tumblr.TumblrNpfFormatting
import dev.dimension.flare.data.network.tumblr.TumblrNpfFormattingBlog
import dev.dimension.flare.data.network.tumblr.TumblrNpfMedia
import dev.dimension.flare.data.network.tumblr.TumblrPost
import dev.dimension.flare.data.network.tumblr.TumblrTrailItem
import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.tumblrLike
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.Clock
import kotlin.time.Instant

internal fun TumblrPost.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2 {
    val postBlogName = resolvedBlogName()
    val statusKey = tumblrPostKey(postBlogName, resolvedId())
    val baseText = content.collectText().ifBlank { fallbackTextParts().joinToString(separator = "\n") }.trim()
    val shouldMoveRootTagsToQuote = baseText.isBlank() && tags.isNotEmpty() && trail.isNotEmpty()
    val renderedContent = renderContent(tags = if (shouldMoveRootTagsToQuote) emptyList() else tags)
    val ownText = renderedContent.text
    val createdAt = createdAtInstant().toUi()
    val hasReblogComment = baseText.isNotBlank() || shouldMoveRootTagsToQuote
    val referencedTrailPost =
        collectReferencedTrailPost(
            accountKey = accountKey,
            fallbackCreatedAt = createdAt,
            fallbackTags = if (shouldMoveRootTagsToQuote) tags else emptyList(),
        )
    val quote = referencedTrailPost.takeIf { hasReblogComment }
    val quoteMediaKeys = quote?.mediaDeduplicationKeys().orEmpty()
    val postMedia = renderedContent.media.filterNot { it.deduplicationKey() in quoteMediaKeys }
    val actions = actionMenus(accountKey = accountKey, statusKey = statusKey).toPersistentList()
    val post =
        UiTimelineV2.Post(
            platformType = PlatformType.Tumblr,
            images = postMedia.toPersistentList(),
            sensitive = false,
            contentWarning = null,
            user = (blog ?: TumblrBlog(name = postBlogName)).toUiProfile(accountKey),
            content = renderedContent.richText.takeUnless { it.isEmpty } ?: summary.orEmpty().toUiPlainText(),
            actions = actions,
            poll = null,
            statusKey = statusKey,
            card = renderedContent.card,
            createdAt = createdAt,
            visibility = UiTimelineV2.Post.Visibility.Public,
            references =
                trail
                    .mapNotNull { trailItem ->
                        val trailPostId = trailItem.post?.id ?: return@mapNotNull null
                        val trailBlogName = trailItem.blog?.name ?: postBlogName
                        UiTimelineV2.Post.Reference(
                            statusKey = tumblrPostKey(trailBlogName, trailPostId),
                            type = ReferenceType.Retweet,
                        )
                    }.toPersistentList(),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status.Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
                ),
            accountType = AccountType.Specific(accountKey),
            itemKey = "tumblr_${statusKey.id}",
        )
    val repost =
        referencedTrailPost
            ?.takeUnless { hasReblogComment }
            ?.copy(actions = actions)
    return UiTimelineV2.TimelinePostItem(
        post = post,
        presentation =
            UiTimelineV2.PostPresentation(
                message =
                    repost?.let {
                        repostMessage(
                            accountKey = accountKey,
                            statusKey = statusKey,
                            createdAt = createdAt,
                        )
                    },
                quotes = listOfNotNull(quote).toPersistentList(),
                repost = repost,
            ),
    )
}

internal fun TumblrBlog.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val blogName = name.normalizedTumblrBlogName()
    val userKey = tumblrUserKey(blogName)
    return UiProfile(
        key = userKey,
        handle =
            UiHandle(
                raw = blogName,
                host = TUMBLR_HOST,
            ),
        avatar = tumblrAvatarUrl(blogName),
        nameInternal = (title ?: blogName).toUiPlainText(),
        platformType = PlatformType.Tumblr,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = theme?.headerImage,
        description = description?.takeIf { it.isNotBlank() }?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = followers ?: 0,
                followsCount = 0,
                statusesCount = totalPosts ?: posts ?: 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

private fun TumblrPost.repostMessage(
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
    createdAt: UiDateTime,
): UiTimelineV2.Message {
    val profile = (blog ?: TumblrBlog(name = resolvedBlogName())).toUiProfile(accountKey)
    val accountType = AccountType.Specific(accountKey)
    return UiTimelineV2.Message(
        user = profile,
        statusKey = statusKey,
        icon = UiIcon.Retweet,
        type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Repost),
        createdAt = createdAt,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = accountType,
                    userKey = profile.key,
                ),
            ),
        accountType = accountType,
    )
}

private fun TumblrPost.actionMenus(
    accountKey: MicroBlogKey,
    statusKey: MicroBlogKey,
): List<ActionMenu> =
    buildList {
        add(
            ActionMenu.Item(
                icon = UiIcon.Reply,
                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                count = UiNumber(replyActionCount),
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Compose.Reply(
                            accountKey = accountKey,
                            statusKey = statusKey,
                        ),
                    ),
                actionFamily = PostActionFamily.Reply,
            ),
        )
        if (canReblog != false && reblogKey != null) {
            add(
                ActionMenu.Group(
                    displayItem =
                        tumblrRepeatableRepostAction(
                            statusKey = statusKey,
                            count = reblogActionCount,
                            accountKey = accountKey,
                        ),
                    actions =
                        listOf(
                            tumblrRepeatableRepostAction(
                                statusKey = statusKey,
                                count = reblogActionCount,
                                accountKey = accountKey,
                            ),
                            ActionMenu.Item(
                                icon = UiIcon.Quote,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Compose.Quote(
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ),
                                    ),
                                actionFamily = PostActionFamily.Quote,
                            ),
                        ).toPersistentList(),
                ),
            )
        }
        if (canLike != false && reblogKey != null) {
            add(
                ActionMenu.tumblrLike(
                    statusKey = statusKey,
                    liked = liked == true,
                    count = likeActionCount,
                    accountKey = accountKey,
                ),
            )
        }
        val overflow =
            buildList {
                val shareUrl = postUrl ?: shortUrl
                if (shareUrl != null) {
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Share,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                            clickEvent =
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Status.ShareSheet(
                                        statusKey = statusKey,
                                        accountType = AccountType.Specific(accountKey),
                                        shareUrl = shareUrl,
                                    ),
                                ),
                            actionFamily = PostActionFamily.Share,
                        ),
                    )
                }
                val userKey = tumblrUserKey(resolvedBlogName())
                if (userKey.id == accountKey.id) {
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Delete,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                            color = ActionMenu.Item.Color.Red,
                            clickEvent =
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Status.DeleteConfirm(
                                        statusKey = statusKey,
                                        accountType = AccountType.Specific(accountKey),
                                    ),
                                ),
                            actionFamily = PostActionFamily.Delete,
                        ),
                    )
                } else {
                    add(ActionMenu.Divider)
                    addAll(
                        userActionsMenu(
                            accountKey = accountKey,
                            userKey = userKey,
                            handle = "@${userKey.id}@$TUMBLR_HOST",
                        ),
                    )
                    add(ActionMenu.Divider)
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Report,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                            color = ActionMenu.Item.Color.Red,
                            clickEvent = ClickEvent.Noop,
                            actionFamily = PostActionFamily.Report,
                        ),
                    )
                }
            }
        if (overflow.isNotEmpty()) {
            add(
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                            actionFamily = null,
                        ),
                    actions = overflow.toPersistentList(),
                ),
            )
        }
    }

private fun tumblrRepeatableRepostAction(
    statusKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        icon = UiIcon.Retweet,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Tumblr.Repost(
                    postKey = statusKey,
                    reposted = false,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Repost,
    )

private val TumblrPost.replyActionCount: Long
    get() =
        replyCount
            ?: repliesCount
            ?: commentCount
            ?: commentsCount
            ?: notes.countTypes("reply", "replied", "comment", "commented")

private val TumblrPost.reblogActionCount: Long
    get() =
        reblogCount
            ?: reblogsCount
            ?: notes.countTypes("reblog", "reblogged", "posted")

private val TumblrPost.likeActionCount: Long
    get() =
        likeCount
            ?: likesCount
            ?: notes.countTypes("like", "liked")

private fun List<TumblrNote>.countTypes(vararg types: String): Long {
    val normalizedTypes = types.toSet()
    return count { note ->
        val type =
            note.type
                ?.trim()
                ?.lowercase()
                ?.replace('-', '_')
        normalizedTypes.contains(type)
    }.toLong()
}

internal fun TumblrPost.resolvedId(): String = idString ?: id?.toString() ?: error("Tumblr post id is missing")

internal fun TumblrPost.resolvedBlogName(): String =
    blogName?.normalizedTumblrBlogName()
        ?: blog?.name?.normalizedTumblrBlogName()
        ?: error("Tumblr blog name is missing")

private fun TumblrPost.createdAtInstant(): Instant =
    timestampEpochSeconds
        ?.let(Instant::fromEpochSeconds)
        ?: Clock.System.now()

private data class TumblrRenderedPostContent(
    val richText: UiRichText,
    val media: List<UiMedia>,
    val card: UiCard?,
) {
    val text: String
        get() = richText.innerText.trim()
}

private data class TumblrNpfRenderedContent(
    val renderRuns: List<RenderContent>,
    val media: List<UiMedia>,
)

private data class TumblrNpfFormattingRange(
    val type: String,
    val start: Int,
    val end: Int,
    val url: String?,
    val blog: TumblrNpfFormattingBlog?,
)

private fun TumblrPost.renderContent(tags: List<String> = this.tags): TumblrRenderedPostContent =
    renderContent(
        content = content,
        tags = tags,
        fallbackTextParts = fallbackTextParts(),
        photos = photos,
    )

private fun TumblrPost.fallbackTextParts(): List<String> = listOfNotNull(title, body, caption).filter { it.isNotBlank() }

private fun TumblrTrailItem.renderContent(fallbackTags: List<String> = emptyList()): TumblrRenderedPostContent =
    renderContent(
        content = content,
        tags = tags.ifEmpty { post?.tags.orEmpty() }.ifEmpty { fallbackTags },
    )

private fun renderContent(
    content: List<TumblrNpfBlock>,
    tags: List<String>,
    fallbackTextParts: List<String> = emptyList(),
    photos: List<TumblrLegacyPhoto> = emptyList(),
): TumblrRenderedPostContent {
    val tagLine = tags.toTumblrTagLine()
    val text =
        content
            .collectText()
            .ifBlank {
                fallbackTextParts.joinToString(separator = "\n")
            }.appendTumblrTagLine(tagLine)
    val shouldInlineImages = content.shouldInlineAllImages()
    val renderedNpfContent = content.renderNpfContent(inlineImages = shouldInlineImages)
    val fallbackTextRuns =
        fallbackTextParts.mapNotNull { part ->
            part.takeIf { it.isNotBlank() }?.toRenderTextContent()
        }
    val tagRuns =
        tagLine
            .takeIf { it.isNotBlank() }
            ?.toRenderTextContent()
            ?.let(::listOf)
            .orEmpty()
    val renderRuns =
        (renderedNpfContent.renderRuns.ifEmpty { fallbackTextRuns } + tagRuns)
    val media =
        renderedNpfContent.media
            .ifEmpty {
                photos.mapNotNull { photo ->
                    (photo.originalSize ?: photo.altSizes.firstOrNull())
                        ?.toImageMedia(photo.caption)
                }
            }.deduplicate()
    return TumblrRenderedPostContent(
        richText =
            uiRichTextOf(
                renderRuns = renderRuns,
                raw = text,
                innerText = text,
            ),
        media = media,
        card = content.collectCard(),
    )
}

private fun String.appendTumblrTagLine(tagLine: String): String {
    val text = trim()
    return when {
        tagLine.isBlank() -> text
        text.isBlank() -> tagLine
        else -> "$text\n$tagLine"
    }
}

private fun List<String>.toTumblrTagLine(): String =
    mapNotNull { tag ->
        tag
            .trim()
            .removePrefix("#")
            .replace(Regex("\\s+"), " ")
            .takeIf { it.isNotBlank() }
            ?.let { "#$it" }
    }.distinct()
        .joinToString(separator = " ")

private fun List<TumblrNpfBlock>.shouldInlineAllImages(): Boolean {
    var hasTextBefore = false
    forEachIndexed { index, block ->
        if (block.isImageBlockWithMedia()) {
            val hasTextAfter = drop(index + 1).any { it.isInlineDecisionTextBlock() }
            if (hasTextBefore && hasTextAfter) {
                return true
            }
        }
        if (block.isInlineDecisionTextBlock()) {
            hasTextBefore = true
        }
    }
    return false
}

private fun TumblrNpfBlock.isImageBlockWithMedia(): Boolean = type == "image" && toBestImageMedia(altText) != null

private fun TumblrNpfBlock.isInlineDecisionTextBlock(): Boolean = type == "text" && !text.isNullOrBlank()

private fun TumblrPost.collectReferencedTrailPost(
    accountKey: MicroBlogKey,
    fallbackCreatedAt: UiDateTime,
    fallbackTags: List<String>,
): UiTimelineV2.Post? =
    trail.firstNotNullOfOrNull { trailItem ->
        trailItem.toReferencedPost(
            accountKey = accountKey,
            fallbackCreatedAt = fallbackCreatedAt,
            fallbackTags = fallbackTags,
        )
    }

private fun TumblrTrailItem.toReferencedPost(
    accountKey: MicroBlogKey,
    fallbackCreatedAt: UiDateTime,
    fallbackTags: List<String>,
): UiTimelineV2.Post? {
    val trailPost = post ?: return null
    val trailPostId = trailPost.id ?: return null
    val trailBlog = blog ?: return null
    val trailBlogName = trailBlog.name.normalizedTumblrBlogName()
    val statusKey = tumblrPostKey(trailBlogName, trailPostId)
    val content = renderContent(fallbackTags = fallbackTags)

    if (content.text.isBlank() && content.media.isEmpty() && content.card == null) {
        return null
    }

    return UiTimelineV2.Post(
        platformType = PlatformType.Tumblr,
        images = content.media.toPersistentList(),
        sensitive = false,
        contentWarning = null,
        user = trailBlog.toUiProfile(accountKey),
        content = content.text.toUiPlainText(),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = content.card,
        createdAt = fallbackCreatedAt,
        visibility = UiTimelineV2.Post.Visibility.Public,
        references = persistentListOf(),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
        accountType = AccountType.Specific(accountKey),
        itemKey = "tumblr_quote_${statusKey.id}",
    )
}

private fun List<TumblrNpfBlock>.renderNpfContent(inlineImages: Boolean): TumblrNpfRenderedContent {
    val renderRuns = mutableListOf<RenderContent>()
    val media = mutableListOf<UiMedia>()
    forEach { block ->
        when (block.type) {
            "image" -> {
                val image = block.toBestImageMedia(block.altText) ?: return@forEach
                if (inlineImages) {
                    renderRuns +=
                        RenderContent.BlockImage(
                            url = image.url,
                            href = block.url,
                        )
                } else {
                    media += image
                }
            }

            "video" -> {
                block
                    .toVideoMedia()
                    ?.let(media::add)
            }

            "audio" -> {
                block.audioUrl()?.let { url ->
                    media +=
                        UiMedia.Audio(
                            url = url,
                            description = block.title,
                            previewUrl = block.posterUrl(),
                        )
                }
            }

            else -> {
                block
                    .toRenderTextContent()
                    ?.let(renderRuns::add)
            }
        }
    }
    return TumblrNpfRenderedContent(
        renderRuns = renderRuns,
        media = media,
    )
}

private fun TumblrNpfBlock.toRenderTextContent(): RenderContent.Text? {
    val text = displayText().takeIf { it.isNotBlank() } ?: return null
    val baseStyle =
        if (type == "link" && url != null) {
            RenderTextStyle(link = url)
        } else {
            RenderTextStyle()
        }
    return RenderContent.Text(
        runs = text.toRenderTextRuns(formatting, baseStyle).toPersistentList(),
        block = toRenderBlockStyle(),
    )
}

private fun String.toRenderTextContent(): RenderContent.Text =
    RenderContent.Text(
        runs = listOf(RenderRun.Text(this)).toPersistentList(),
    )

private fun TumblrNpfBlock.displayText(): String =
    when (type) {
        "text" -> text.orEmpty()
        "link" -> title ?: url.orEmpty()
        else -> text ?: title.orEmpty()
    }

private fun TumblrNpfBlock.toRenderBlockStyle(): RenderBlockStyle =
    when (subtype?.lowercase()) {
        "heading1" -> RenderBlockStyle(headingLevel = 1)
        "heading2" -> RenderBlockStyle(headingLevel = 2)
        "quote", "indented" -> RenderBlockStyle(isBlockQuote = true)
        "unordered-list-item", "ordered-list-item" -> RenderBlockStyle(isListItem = true)
        "chat" -> RenderBlockStyle()
        else -> RenderBlockStyle()
    }

private fun String.toRenderTextRuns(
    formatting: List<TumblrNpfFormatting>,
    baseStyle: RenderTextStyle,
): List<RenderRun.Text> {
    if (isEmpty()) return emptyList()
    val ranges = formatting.toRanges(this)
    if (ranges.isEmpty()) {
        return listOf(RenderRun.Text(text = this, style = baseStyle))
    }

    val boundaries =
        buildSet {
            add(0)
            add(length)
            ranges.forEach { range ->
                add(range.start)
                add(range.end)
            }
        }.filter { it in 0..length }
            .sorted()

    return boundaries
        .zipWithNext()
        .mapNotNull { (start, end) ->
            if (start >= end) return@mapNotNull null
            val segment = substring(start, end)
            if (segment.isEmpty()) return@mapNotNull null
            val activeRanges = ranges.filter { range -> start >= range.start && start < range.end }
            RenderRun.Text(
                text = segment,
                style = activeRanges.toRenderTextStyle(baseStyle),
            )
        }.ifEmpty {
            listOf(RenderRun.Text(text = this, style = baseStyle))
        }
}

private fun List<TumblrNpfFormatting>.toRanges(text: String): List<TumblrNpfFormattingRange> =
    mapNotNull { formatting ->
        val type = formatting.type?.lowercase() ?: return@mapNotNull null
        val start = formatting.start?.let(text::safeCharIndex) ?: return@mapNotNull null
        val end = formatting.end?.let(text::safeCharIndex) ?: return@mapNotNull null
        if (start >= end) return@mapNotNull null
        TumblrNpfFormattingRange(
            type = type,
            start = start,
            end = end,
            url = formatting.url,
            blog = formatting.blog,
        )
    }

private fun List<TumblrNpfFormattingRange>.toRenderTextStyle(baseStyle: RenderTextStyle): RenderTextStyle {
    var style = baseStyle
    forEach { range ->
        style =
            when (range.type) {
                "bold" -> style.copy(bold = true)
                "italic" -> style.copy(italic = true)
                "strikethrough" -> style.copy(strikethrough = true)
                "small" -> style.copy(small = true)
                "link" -> style.copy(link = range.url ?: style.link)
                "mention" -> style.copy(link = range.blog?.toTumblrBlogUrl() ?: style.link)
                "code" -> style.copy(code = true, monospace = true)
                else -> style
            }
    }
    return style
}

private fun TumblrNpfFormattingBlog.toTumblrBlogUrl(): String? =
    url ?: name?.normalizedTumblrBlogName()?.let { "https://www.tumblr.com/$it" }

private fun String.safeCharIndex(offset: Int): Int {
    val direct = offset.coerceIn(0, length)
    if (!isInsideSurrogatePair(direct)) {
        return direct
    }
    return charIndexForCodePointOffset(offset)
}

private fun String.isInsideSurrogatePair(index: Int): Boolean =
    index in 1 until length &&
        this[index - 1].isHighSurrogateChar() &&
        this[index].isLowSurrogateChar()

private fun String.charIndexForCodePointOffset(offset: Int): Int {
    var charIndex = 0
    var codePointIndex = 0
    while (charIndex < length && codePointIndex < offset) {
        val current = this[charIndex]
        val next = getOrNull(charIndex + 1)
        charIndex +=
            if (current.isHighSurrogateChar() && next?.isLowSurrogateChar() == true) {
                2
            } else {
                1
            }
        codePointIndex++
    }
    return charIndex.coerceIn(0, length)
}

private fun Char.isHighSurrogateChar(): Boolean = this in '\uD800'..'\uDBFF'

private fun Char.isLowSurrogateChar(): Boolean = this in '\uDC00'..'\uDFFF'

private fun List<TumblrNpfBlock>.collectText(): String =
    buildString {
        appendNpfText(this)
    }.trim()

private fun List<TumblrNpfBlock>.appendNpfText(builder: StringBuilder) {
    forEach { block ->
        when (block.type) {
            "text" -> {
                val text = block.text
                if (!text.isNullOrBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }
            }

            "link" -> {
                val title = block.title ?: block.url
                if (!title.isNullOrBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(title)
                }
            }

            else -> {
                val text = block.text ?: block.title
                if (!text.isNullOrBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }
            }
        }
    }
}

private fun List<TumblrNpfBlock>.collectMedia(): List<UiMedia> =
    buildList {
        this@collectMedia.forEach { block ->
            when (block.type) {
                "image" -> {
                    block
                        .toBestImageMedia(block.altText)
                        ?.let(::add)
                }

                "video" -> {
                    block
                        .toVideoMedia()
                        ?.let(::add)
                }

                "audio" -> {
                    block.audioUrl()?.let { url ->
                        add(
                            UiMedia.Audio(
                                url = url,
                                description = block.title,
                                previewUrl = block.posterUrl(),
                            ),
                        )
                    }
                }
            }
        }
    }

private fun List<UiMedia>.deduplicate(): List<UiMedia> = distinctBy { it.deduplicationKey() }

private fun UiTimelineV2.Post.mediaDeduplicationKeys(): Set<String> =
    buildSet {
        images.forEach { media ->
            add(media.deduplicationKey())
        }
        content.imageUrls.forEach { url ->
            add("image:$url")
        }
    }

private fun List<TumblrNpfBlock>.collectCard(): UiCard? =
    firstNotNullOfOrNull { block ->
        when (block.type) {
            "link" -> {
                val url = block.url ?: return@firstNotNullOfOrNull null
                UiCard(
                    title = block.title ?: url,
                    description = block.cardDescription(url),
                    media = block.posterImageMedia(),
                    url = url,
                )
            }

            "video" -> {
                if (block.toVideoMedia() != null) {
                    null
                } else {
                    val url =
                        block.url
                            ?: block.embedIframe?.url
                            ?: block.media.firstNotNullOfOrNull { it.url }
                            ?: return@firstNotNullOfOrNull null
                    UiCard(
                        title = block.title ?: block.provider ?: url,
                        description = block.cardDescription(url),
                        media = block.posterImageMedia(),
                        url = url,
                    )
                }
            }

            else -> {
                null
            }
        }
    }

private fun TumblrNpfBlock.cardDescription(fallbackUrl: String): String = description?.takeIf { it.isNotBlank() } ?: fallbackUrl

private fun TumblrNpfBlock.toBestImageMedia(description: String?): UiMedia.Image? =
    media
        .mapNotNull { it.toImageMedia(description) }
        .maxWithOrNull(
            compareBy<UiMedia.Image> { it.width * it.height }
                .thenBy { it.width }
                .thenBy { it.height },
        )

private fun TumblrNpfMedia.toImageMedia(description: String?): UiMedia.Image? {
    val url = url ?: return null
    return UiMedia.Image(
        url = url,
        previewUrl = url,
        description = description,
        width = width?.toFloat() ?: 0f,
        height = height?.toFloat() ?: 0f,
        sensitive = false,
    )
}

private fun TumblrNpfBlock.toVideoMedia(): UiMedia.Video? {
    val poster = posterUrl()
    val fallbackDescription = title ?: description
    val blockProvider = provider
    val fallbackWidth = width?.toFloat() ?: 0f
    val fallbackHeight = height?.toFloat() ?: 0f
    return media
        .firstNotNullOfOrNull { media ->
            media.toVideoMedia(
                poster = poster,
                provider = blockProvider,
                fallbackDescription = fallbackDescription,
                fallbackWidth = fallbackWidth,
                fallbackHeight = fallbackHeight,
                requirePlayableVideoUrl = true,
            )
        } ?: toVideoMediaFromBlock(
        poster = poster,
        provider = blockProvider,
        fallbackDescription = fallbackDescription,
        fallbackWidth = fallbackWidth,
        fallbackHeight = fallbackHeight,
        requirePlayableVideoUrl = true,
    )
}

private fun TumblrNpfMedia.toVideoMedia(
    poster: String?,
    provider: String?,
    fallbackDescription: String?,
    fallbackWidth: Float,
    fallbackHeight: Float,
    requirePlayableVideoUrl: Boolean = false,
): UiMedia.Video? {
    val url = url ?: return null
    if (
        requirePlayableVideoUrl &&
        !url.isLikelyPlayableVideoUrl(
            type = type,
            provider = provider,
        )
    ) {
        return null
    }
    return UiMedia.Video(
        url = url,
        thumbnailUrl = poster ?: url,
        description = fallbackDescription,
        width = width?.toFloat() ?: fallbackWidth,
        height = height?.toFloat() ?: fallbackHeight,
    )
}

private fun TumblrNpfBlock.toVideoMediaFromBlock(
    poster: String?,
    provider: String?,
    fallbackDescription: String?,
    fallbackWidth: Float,
    fallbackHeight: Float,
    requirePlayableVideoUrl: Boolean = false,
): UiMedia.Video? {
    val url = url ?: return null
    if (
        requirePlayableVideoUrl &&
        !url.isLikelyPlayableVideoUrl(
            type = null,
            provider = provider,
        )
    ) {
        return null
    }
    return UiMedia.Video(
        url = url,
        thumbnailUrl = poster ?: posterUrl() ?: url,
        description = title ?: description ?: fallbackDescription,
        width = width?.toFloat() ?: fallbackWidth,
        height = height?.toFloat() ?: fallbackHeight,
    )
}

private fun TumblrNpfBlock.posterImageMedia(): UiMedia.Image? =
    poster
        .firstOrNull()
        ?.toImageMedia(null)
        ?: posterUrl()?.let { url ->
            UiMedia.Image(
                url = url,
                previewUrl = url,
                description = null,
                width = 0f,
                height = 0f,
                sensitive = false,
            )
        }

private fun TumblrNpfBlock.posterUrl(): String? =
    thumbnailUrl
        ?: poster.firstNotNullOfOrNull { it.url }

private fun TumblrNpfBlock.audioUrl(): String? =
    url
        ?: media.firstNotNullOfOrNull { it.url }

private fun String.isLikelyPlayableVideoUrl(
    type: String?,
    provider: String?,
): Boolean {
    val normalizedType = type?.lowercase()
    if (
        provider.equals("tumblr", ignoreCase = true) ||
        normalizedType?.startsWith("video/") == true
    ) {
        return true
    }
    val path = substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".mp4") ||
        path.endsWith(".m4v") ||
        path.endsWith(".mov") ||
        path.endsWith(".webm") ||
        path.endsWith(".m3u8") ||
        contains("va.media.tumblr.com")
}

private fun TumblrLegacyPhotoSize.toImageMedia(description: String?): UiMedia.Image =
    UiMedia.Image(
        url = url,
        previewUrl = url,
        description = description,
        width = width?.toFloat() ?: 0f,
        height = height?.toFloat() ?: 0f,
        sensitive = false,
    )

private fun UiMedia.deduplicationKey(): String =
    when (this) {
        is UiMedia.Audio -> "audio:$url"
        is UiMedia.Gif -> "gif:$url"
        is UiMedia.Image -> "image:$url"
        is UiMedia.Video -> "video:$url"
    }
