package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.network.pixiv.PIXIV_IMAGE_REFERER
import dev.dimension.flare.data.network.pixiv.model.PixivComment
import dev.dimension.flare.data.network.pixiv.model.PixivCommentStamp
import dev.dimension.flare.data.network.pixiv.model.PixivIllust
import dev.dimension.flare.data.network.pixiv.model.PixivTrendTag
import dev.dimension.flare.data.network.pixiv.model.PixivUser
import dev.dimension.flare.data.network.pixiv.model.PixivUserDetailResponse
import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.mapper.pixivFavourite
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlin.time.Clock
import kotlin.time.Instant

internal fun PixivIllust.toUiTimeline(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val statusKey = pixivIllustKey(id)
    return UiTimelineV2.Post(
        platformType = PlatformType.Pixiv,
        images = toUiMedia().toPersistentList(),
        sensitive = xRestrict > 0 || sanityLevel >= 6,
        contentWarning = UiTranslatableText(original = title.toUiPlainText()),
        user = user.toUiProfile(accountKey),
        content =
            UiTranslatableText(
                original =
                    buildString {
                        append(caption)
                        if (tags.isNotEmpty()) {
                            append("\n\n")
                            append(
                                tags.joinToString(" ") {
                                    "<a href=\"${DeeplinkRoute.Search(
                                        AccountType.Specific(accountKey),
                                        "#${it.name}",
                                    ).toUri()}\">#${it.name}</a>"
                                },
                            )
                        }
                    }.trim().let {
                        parseHtml(it).toUi()
                    },
            ),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Eye,
                    count = UiNumber(totalView),
                    clickEvent = ClickEvent.Noop,
                ),
                ActionMenu.pixivFavourite(
                    statusKey = statusKey,
                    favourited = isBookmarked,
                    count = totalBookmarks,
                    accountKey = accountKey,
                ),
                ActionMenu.Item(
                    icon = UiIcon.Share,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status
                                .ShareSheet(
                                    statusKey = statusKey,
                                    accountType = AccountType.Specific(accountKey),
                                    shareUrl = "https://www.pixiv.net/artworks/$id",
                                ),
                        ),
                    actionFamily = PostActionFamily.Share,
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = parsePixivDate(createDate).toUi(),
        visibility = null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Gallery.Detail(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun PixivComment.toUiTimeline(
    accountKey: MicroBlogKey,
    illustKey: MicroBlogKey,
): UiTimelineV2.Post =
    UiTimelineV2.Post(
        platformType = PlatformType.Pixiv,
        images =
            stamp
                ?.toUiMedia()
                ?.let { persistentListOf<UiMedia>(it) }
                ?: persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = user.toUiProfile(accountKey),
        content = UiTranslatableText(original = comment.stripPixivHtml().toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = pixivCommentKey(illustKey, id),
        card = null,
        createdAt = parsePixivDate(date).toUi(),
        visibility = null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = pixivUserKey(user.id),
                ),
            ),
        mediaClickPolicy = UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent,
        accountType = AccountType.Specific(accountKey),
    )

private fun PixivCommentStamp.toUiMedia(): UiMedia.Image? = stampUrl.toUiImage(persistentMapOf("Referer" to PIXIV_IMAGE_REFERER))

internal fun PixivUser.toUiProfile(accountKey: MicroBlogKey? = null): UiProfile =
    UiProfile(
        key = pixivUserKey(id),
        handle = UiHandle(raw = account, host = PIXIV_HOST),
        avatar =
            (profileImageUrls?.medium ?: profileImageUrls?.px170x170 ?: profileImageUrls?.px50x50)
                .toUiImage(persistentMapOf("Referer" to PIXIV_IMAGE_REFERER)),
        nameInternal = name.toUiPlainText(),
        platformType = PlatformType.Pixiv,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.GuestHost(PIXIV_HOST),
                    userKey = pixivUserKey(id),
                ),
            ),
        banner = null,
        description = comment?.stripPixivHtml()?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark =
            if (isPremium) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
        bottomContent = null,
    )

internal fun PixivCredential.toUiProfile(accountKey: MicroBlogKey? = null): UiProfile =
    UiProfile(
        key = pixivUserKey(userId),
        handle = UiHandle(raw = userAccount ?: userId.toString(), host = PIXIV_HOST),
        avatar = profileImageUrl.toUiImage(persistentMapOf("Referer" to PIXIV_IMAGE_REFERER)),
        nameInternal = (userName ?: userAccount ?: userId.toString()).toUiPlainText(),
        platformType = PlatformType.Pixiv,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.GuestHost(PIXIV_HOST),
                    userKey = pixivUserKey(userId),
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
            if (userIsPremium) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
        bottomContent = null,
    )

internal fun PixivUserDetailResponse.toUiProfile(accountKey: MicroBlogKey? = null): UiProfile {
    val base = user.toUiProfile(accountKey)
    return base.copy(
        banner = profile?.backgroundImageUrl.toUiImage(persistentMapOf("Referer" to PIXIV_IMAGE_REFERER)),
        description =
            profile?.webpage?.takeIf { it.isNotBlank() }?.let { webpage ->
                listOfNotNull(user.comment, webpage).joinToString("\n").stripPixivHtml().toUiPlainText()
            } ?: user.comment?.stripPixivHtml()?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = profile?.totalFollowUsers ?: 0,
                statusesCount = (profile?.totalIllusts ?: 0) + (profile?.totalManga ?: 0),
            ),
        bottomContent =
            buildMap {
                profile?.region?.takeIf { it.isNotBlank() }?.let {
                    put(UiProfile.BottomContent.Iconify.Icon.Location, it.toUiPlainText())
                }
                profile?.webpage?.takeIf { it.isNotBlank() }?.let {
                    put(UiProfile.BottomContent.Iconify.Icon.Url, it.toUiPlainText())
                }
            }.takeIf { it.isNotEmpty() }?.let {
                UiProfile.BottomContent.Iconify(it.toPersistentMap())
            },
    )
}

internal fun PixivTrendTag.toUiHashtag(): UiHashtag =
    UiHashtag(
        hashtag = tag,
        description = translatedName,
        searchContent = tag,
    )

internal fun pixivIllustKey(id: Long): MicroBlogKey = MicroBlogKey(id.toString(), PIXIV_HOST)

private fun pixivCommentKey(
    illustKey: MicroBlogKey,
    commentId: Long,
): MicroBlogKey = MicroBlogKey("${illustKey.id}:comment:$commentId", PIXIV_HOST)

internal fun pixivUserKey(id: Long): MicroBlogKey = MicroBlogKey(id.toString(), PIXIV_HOST)

private fun PixivIllust.renderContent(): String =
    buildString {
        append(title)
        caption.stripPixivHtml().takeIf { it.isNotBlank() }?.let {
            append("\n\n")
            append(it)
        }
        if (tags.isNotEmpty()) {
            append("\n\n")
            append(tags.joinToString(" ") { "#${it.name}" })
        }
    }

internal fun PixivIllust.toUiMedia(): List<UiMedia> {
    val headers = persistentMapOf("Referer" to PIXIV_IMAGE_REFERER)
    return if (metaPages.isNotEmpty()) {
        metaPages.mapNotNull { page ->
            page.imageUrls.toUiImage(
                width = width.toFloat(),
                height = height.toFloat(),
                sensitive = xRestrict > 0 || sanityLevel >= 6,
                headers = headers,
            )
        }
    } else {
        imageUrls
            .toUiImage(
                width = width.toFloat(),
                height = height.toFloat(),
                sensitive = xRestrict > 0 || sanityLevel >= 6,
                headers = headers,
                preferredOriginalUrl = metaSinglePage?.originalImageUrl,
            )?.let(::listOf)
            .orEmpty()
    }
}

private fun dev.dimension.flare.data.network.pixiv.model.PixivImageUrls.toUiImage(
    width: Float,
    height: Float,
    sensitive: Boolean,
    headers: kotlinx.collections.immutable.ImmutableMap<String, String>,
    preferredOriginalUrl: String? = null,
): UiMedia.Image? {
    val url = preferredOriginalUrl?.takeIf { it.isNotBlank() } ?: original ?: large ?: medium ?: squareMedium ?: return null
    return UiMedia.Image(
        url = url,
        previewUrl = medium ?: squareMedium ?: url,
        description = null,
        height = height,
        width = width,
        sensitive = sensitive,
        customHeaders = headers,
    )
}

private fun String.stripPixivHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()

private fun parsePixivDate(value: String): Instant =
    runCatching {
        Instant.parse(value)
    }.getOrElse {
        Clock.System.now()
    }
