package dev.dimension.flare.data.datasource.tumblr

import de.cketti.codepoints.codePointCount
import de.cketti.codepoints.offsetByCodePoints
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.tumblr.TumblrBlog
import dev.dimension.flare.data.network.tumblr.TumblrLegacyPhoto
import dev.dimension.flare.data.network.tumblr.TumblrLegacyPhotoSize
import dev.dimension.flare.data.network.tumblr.TumblrNpfBlock
import dev.dimension.flare.data.network.tumblr.TumblrNpfFormatting
import dev.dimension.flare.data.network.tumblr.TumblrNpfFormattingBlog
import dev.dimension.flare.data.network.tumblr.TumblrNpfLayout
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
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.mapper.tumblrLike
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
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
    val baseText = content.collectText(layout).ifBlank { fallbackTextParts().joinToString(separator = "\n") }.trim()
    val shouldMoveRootTagsToQuote = baseText.isBlank() && tags.isNotEmpty() && trail.isNotEmpty()
    val renderedContent = renderContent(tags = if (shouldMoveRootTagsToQuote) emptyList() else tags)
    val ownText = renderedContent.text
    val createdAt = createdAtInstant().toUi()
    val hasReblogComment = baseText.isNotBlank() || shouldMoveRootTagsToQuote
    val referencedTrailPosts =
        collectReferencedTrailPosts(
            accountKey = accountKey,
            parentStatusKey = statusKey,
            fallbackCreatedAt = createdAt,
            fallbackTags = if (shouldMoveRootTagsToQuote) tags else emptyList(),
        )
    val quotes = referencedTrailPosts.takeIf { hasReblogComment }.orEmpty()
    val quoteMediaKeys = quotes.flatMapTo(mutableSetOf()) { it.mediaDeduplicationKeys() }
    val postMedia = renderedContent.media.filterNot { it.deduplicationKey() in quoteMediaKeys }
    val actions = actionMenus(accountKey = accountKey, statusKey = statusKey).toPersistentList()
    val post =
        UiTimelineV2.Post(
            platformType = PlatformType.Tumblr,
            images = postMedia.toPersistentList(),
            sensitive = false,
            contentWarning = null,
            user = (blog ?: TumblrBlog(name = postBlogName)).toUiProfile(accountKey),
            content =
                UiTranslatableText(
                    original = renderedContent.richText.takeUnless { it.isEmpty } ?: summary.orEmpty().toUiPlainText(),
                ),
            actions = actions,
            poll = null,
            statusKey = statusKey,
            card = renderedContent.card,
            createdAt = createdAt,
            visibility =
                if (state == "private") {
                    UiTimelineV2.Post.Visibility.Private
                } else {
                    UiTimelineV2.Post.Visibility.Public
                },
            references =
                trail
                    .mapNotNull { trailItem ->
                        val trailPostId = trailItem.post?.id ?: return@mapNotNull null
                        val trailBlogName = trailItem.resolvedBlogName() ?: return@mapNotNull null
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
        referencedTrailPosts
            .lastOrNull()
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
                inlineParents =
                    referencedTrailPosts
                        .dropLast(if (repost == null) 0 else 1)
                        .takeUnless { hasReblogComment }
                        .orEmpty()
                        .toPersistentList(),
                quotes = quotes.toPersistentList(),
                repost = repost,
            ),
    )
}

internal fun TumblrBlog.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val blogName = resolvedBlogName() ?: "unknown-tumblr-blog"
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
        description = description?.takeIf { it.isNotBlank() }?.toTumblrDescriptionRichText(),
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

private fun TumblrBlog.resolvedBlogName(): String? =
    name
        ?.normalizedTumblrBlogName()
        ?.takeIf { it.isNotBlank() }
        ?: url
            ?.normalizedTumblrBlogName()
            ?.takeIf { it.isNotBlank() && it != "tumblr.com" }
        ?: uuid?.takeIf { it.isNotBlank() }?.lowercase()

private fun TumblrTrailItem.resolvedBlogName(): String? =
    blog?.resolvedBlogName()
        ?: brokenBlogName
            ?.normalizedTumblrBlogName()
            ?.takeIf { it.isNotBlank() }

private fun String.toTumblrDescriptionRichText(): UiRichText = parseHtml(this).toUi()

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
                noteCount?.let { totalNotes ->
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Comment,
                            text = ActionMenu.Item.Text.Raw("Notes"),
                            count = UiNumber(totalNotes),
                            clickEvent = ClickEvent.Noop,
                            actionFamily = PostActionFamily.Comment,
                        ),
                    )
                }
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
                ),
            ),
        actionFamily = PostActionFamily.Repost,
    )

private val TumblrPost.reblogActionCount: Long
    get() =
        reblogCount
            ?: reblogsCount
            ?: 0

private val TumblrPost.likeActionCount: Long
    get() =
        likeCount
            ?: likesCount
            ?: 0

internal fun TumblrPost.resolvedId(): String = idString ?: id?.toString() ?: error("Tumblr post id is missing")

internal fun TumblrPost.resolvedBlogName(): String =
    blogName?.normalizedTumblrBlogName()
        ?: blog?.resolvedBlogName()
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
        layout = layout,
        tags = tags,
        fallbackTextParts = fallbackTextParts(),
        photos = photos,
    )

private fun TumblrPost.fallbackTextParts(): List<String> = listOfNotNull(title, body, caption).filter { it.isNotBlank() }

private fun TumblrTrailItem.renderContent(fallbackTags: List<String> = emptyList()): TumblrRenderedPostContent =
    renderContent(
        content = content,
        layout = layout,
        tags = tags.ifEmpty { post?.tags.orEmpty() }.ifEmpty { fallbackTags },
    )

private fun renderContent(
    content: List<TumblrNpfBlock>,
    layout: List<TumblrNpfLayout>,
    tags: List<String>,
    fallbackTextParts: List<String> = emptyList(),
    photos: List<TumblrLegacyPhoto> = emptyList(),
): TumblrRenderedPostContent {
    val orderedContent = content.inLayoutDisplayOrder(layout)
    val visibleContent = orderedContent.map { it.value }
    val tagLine = tags.toTumblrTagLine()
    val text =
        visibleContent
            .collectText()
            .ifBlank {
                fallbackTextParts.joinToString(separator = "\n")
            }.appendTumblrTagLine(tagLine)
    val shouldInlineImages = visibleContent.shouldInlineAllImages()
    val renderedNpfContent =
        orderedContent.renderNpfContent(
            inlineImages = shouldInlineImages,
            askLayout = layout.firstOrNull { it.type == "ask" },
        )
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
        card = visibleContent.collectCard(),
    )
}

private fun List<TumblrNpfBlock>.inLayoutDisplayOrder(layout: List<TumblrNpfLayout>): List<IndexedValue<TumblrNpfBlock>> {
    val rowsLayout = layout.firstOrNull { it.type == "rows" }
    val orderedIndexes =
        rowsLayout
            ?.let { rows ->
                rows.display
                    .flatMap { it.blocks }
                    .ifEmpty { rows.rows.flatten() }
            }.orEmpty()
            .distinct()
    val indexes =
        if (rowsLayout != null && orderedIndexes.isNotEmpty()) {
            // A rows layout also controls visibility (for example paywall blocks).
            orderedIndexes
        } else {
            indices.toList()
        }
    return indexes.mapNotNull { index -> getOrNull(index)?.let { IndexedValue(index, it) } }
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

private fun TumblrPost.collectReferencedTrailPosts(
    accountKey: MicroBlogKey,
    parentStatusKey: MicroBlogKey,
    fallbackCreatedAt: UiDateTime,
    fallbackTags: List<String>,
): List<UiTimelineV2.Post> =
    trail.mapIndexedNotNull { index, trailItem ->
        trailItem.toReferencedPost(
            accountKey = accountKey,
            parentStatusKey = parentStatusKey,
            trailIndex = index,
            fallbackCreatedAt = fallbackCreatedAt,
            fallbackTags = fallbackTags,
        )
    }

private fun TumblrTrailItem.toReferencedPost(
    accountKey: MicroBlogKey,
    parentStatusKey: MicroBlogKey,
    trailIndex: Int,
    fallbackCreatedAt: UiDateTime,
    fallbackTags: List<String>,
): UiTimelineV2.Post? {
    val trailBlogName = resolvedBlogName() ?: return null
    val trailBlog = blog ?: TumblrBlog(name = trailBlogName)
    val resolvablePostId = post?.id
    val trailPostId = resolvablePostId ?: "broken-${parentStatusKey.id.substringAfterLast(':')}-$trailIndex"
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
        content = UiTranslatableText(original = content.richText),
        actions = persistentListOf(),
        poll = null,
        statusKey = statusKey,
        card = content.card,
        createdAt = post?.timestamp?.let(Instant::fromEpochSeconds)?.toUi() ?: fallbackCreatedAt,
        visibility = UiTimelineV2.Post.Visibility.Public,
        references = persistentListOf(),
        clickEvent =
            if (resolvablePostId == null) {
                ClickEvent.Noop
            } else {
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status.Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
                )
            },
        accountType = AccountType.Specific(accountKey),
        itemKey = "tumblr_quote_${statusKey.id}",
    )
}

private fun List<IndexedValue<TumblrNpfBlock>>.renderNpfContent(
    inlineImages: Boolean,
    askLayout: TumblrNpfLayout?,
): TumblrNpfRenderedContent {
    val renderRuns = mutableListOf<RenderContent>()
    val media = mutableListOf<UiMedia>()
    val askBlockIndexes = askLayout?.blocks.orEmpty().toSet()
    var askAttributionRendered = false
    forEach { indexedBlock ->
        val block = indexedBlock.value
        val isAskBlock = indexedBlock.index in askBlockIndexes
        if (isAskBlock && !askAttributionRendered) {
            askAttributionRendered = true
            askLayout
                ?.attribution
                ?.blog
                ?.resolvedBlogName()
                ?.let { blogName ->
                    renderRuns +=
                        RenderContent.Text(
                            runs = persistentListOf(RenderRun.Text("@$blogName")),
                            block = RenderBlockStyle(isBlockQuote = true),
                        )
                }
        }
        when (block.type) {
            "image" -> {
                val image = block.toBestImageMedia(block.altText)
                if (image != null) {
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
                block.caption
                    ?.takeIf { it.isNotBlank() }
                    ?.let { caption ->
                        renderRuns +=
                            RenderContent.Text(
                                runs = persistentListOf(RenderRun.Text(caption)),
                                block = RenderBlockStyle(isFigCaption = true),
                            )
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
                    ?.let { content ->
                        if (isAskBlock) {
                            content.copy(block = content.block.copy(isBlockQuote = true))
                        } else {
                            content
                        }
                    }?.let(renderRuns::add)
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
        val start = formatting.start?.let(text::charIndexForCodePointOffset) ?: return@mapNotNull null
        val end = formatting.end?.let(text::charIndexForCodePointOffset) ?: return@mapNotNull null
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

private fun String.charIndexForCodePointOffset(offset: Int): Int =
    offsetByCodePoints(index = 0, codePointOffset = offset.coerceIn(0, codePointCount()))

private fun List<TumblrNpfBlock>.collectText(): String =
    buildString {
        appendNpfText(this)
    }.trim()

private fun List<TumblrNpfBlock>.collectText(layout: List<TumblrNpfLayout>): String =
    inLayoutDisplayOrder(layout)
        .map { it.value }
        .collectText()

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

            "image" -> {
                val caption = block.caption
                if (!caption.isNullOrBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(caption)
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
        content.original.imageUrls.forEach { url ->
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
    return (media + TumblrNpfMedia(url = url, width = width, height = height))
        .firstNotNullOfOrNull { media ->
            media.toVideoMedia(
                poster = poster,
                provider = blockProvider,
                fallbackDescription = fallbackDescription,
                fallbackWidth = fallbackWidth,
                fallbackHeight = fallbackHeight,
            )
        }
}

private fun TumblrNpfMedia.toVideoMedia(
    poster: String?,
    provider: String?,
    fallbackDescription: String?,
    fallbackWidth: Float,
    fallbackHeight: Float,
): UiMedia.Video? {
    val url = url ?: return null
    if (!url.isLikelyPlayableVideoUrl(type = type, provider = provider)) {
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
    media
        .firstOrNull { item ->
            val mediaUrl = item.url ?: return@firstOrNull false
            item.type?.startsWith("audio/", ignoreCase = true) == true || mediaUrl.isLikelyPlayableAudioUrl()
        }?.url
        ?: media.firstNotNullOfOrNull { it.url }
        ?: url?.takeIf { it.isLikelyPlayableAudioUrl() }

private fun String.isLikelyPlayableAudioUrl(): Boolean {
    val path = substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".mp3") ||
        path.endsWith(".m4a") ||
        path.endsWith(".aac") ||
        path.endsWith(".ogg") ||
        path.endsWith(".oga") ||
        path.endsWith(".wav") ||
        path.endsWith(".flac")
}

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
