package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.vvo.model.Attitude
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import io.ktor.http.decodeURLPart
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal fun Status.render(accountKey: MicroBlogKey): UiTimelineV2 = renderStatusV2(accountKey)

internal fun Comment.render(accountKey: MicroBlogKey): UiTimelineV2 = renderStatusV2(accountKey)

internal fun Attitude.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val content = status?.renderStatusV2(accountKey)
    val user = user?.render(accountKey)
    return UiTimelineV2.UserList(
        message =
            UiTimelineV2.Message(
                user = user,
                statusKey = MicroBlogKey(id.toString(), accountKey.host),
                icon = UiIcon.Like,
                type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Favourite),
                createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
                clickEvent =
                    if (user != null) {
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Profile
                                .User(
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.key,
                                ),
                        )
                    } else {
                        ClickEvent.Noop
                    },
                accountType = AccountType.Specific(accountKey),
            ),
        users = listOfNotNull(user).toImmutableList(),
        post = content,
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
        statusKey = MicroBlogKey(id.toString(), accountKey.host),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun Status.renderStatusV2(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val media =
        picsList.orEmpty().mapNotNull {
            val url = it.large?.url ?: it.url
            if (url.isNullOrEmpty()) {
                null
            } else if (it.type == "video" && it.videoSrc != null) {
                UiMedia.Video(
                    url = it.videoSrc,
                    thumbnailUrl = it.url ?: url,
                    width = it.large?.geoValue?.widthValue ?: it.geoValue?.widthValue ?: 0f,
                    height = it.large?.geoValue?.heightValue ?: it.geoValue?.heightValue ?: 0f,
                    description = null,
                )
            } else {
                UiMedia.Image(
                    url = url,
                    width = it.large?.geoValue?.widthValue ?: it.geoValue?.widthValue ?: 0f,
                    height = it.large?.geoValue?.heightValue ?: it.geoValue?.heightValue ?: 0f,
                    previewUrl = it.url ?: url,
                    description = null,
                    sensitive = false,
                )
            }
        }
    val pageInfoVideo =
        listOfNotNull(
            pageInfo
                ?.takeIf {
                    it.type == "video"
                }?.let {
                    val description = it.title ?: it.content2 ?: it.content1
                    val height = it.pagePic?.height?.toFloatOrNull() ?: 0f
                    val width = it.pagePic?.width?.toFloatOrNull() ?: 0f
                    val previewUrl = it.pagePic?.url
                    val videoUrl =
                        it.urls?.mp4720PMp4
                            ?: it.urls?.mp4HDMp4
                            ?: it.urls?.mp4LdMp4
                            ?: it.mediaInfo?.streamURLHD
                            ?: it.mediaInfo?.streamURL
                    if (videoUrl != null && previewUrl != null) {
                        UiMedia.Video(
                            url = videoUrl,
                            thumbnailUrl = previewUrl,
                            width = width,
                            height = height,
                            description = description,
                        )
                    } else {
                        null
                    }
                },
        )
    val pageInfoImage =
        listOfNotNull(
            pageInfo
                ?.takeIf {
                    it.type == "bigPic"
                }?.let {
                    val url = it.pagePic?.url
                    if (url != null) {
                        UiMedia.Image(
                            url = url,
                            width = it.pagePic.width?.toFloatOrNull() ?: 0f,
                            height = it.pagePic.height?.toFloatOrNull() ?: 0f,
                            previewUrl = url,
                            description = null,
                            sensitive = false,
                        )
                    } else {
                        null
                    }
                },
        )
    val actualMedia =
        media.takeIf {
            it.isNotEmpty()
        } ?: pageInfoVideo.takeIf {
            it.isNotEmpty()
        } ?: pageInfoImage

    val user = this.user?.render(accountKey)
    val isFromMe = user?.key == accountKey
    val displayUser = user
    val statusKey = MicroBlogKey(id = id, host = accountKey.host)
    val canReblog = visible?.type == null || visible.type == 0L
    val url =
        buildString {
            append("https://$vvoHostLong/")
            append(this@renderStatusV2.user?.id)
            append('/')
            append(bid)
        }
    val message =
        title?.text?.let {
            UiTimelineV2.Message(
                user = displayUser,
                statusKey = statusKey,
                icon = UiIcon.Info,
                type = UiTimelineV2.Message.Type.Raw(it),
                createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Status.VVOStatus(
                            statusKey = statusKey,
                            accountType = AccountType.Specific(accountKey),
                        ),
                    ),
                accountType = AccountType.Specific(accountKey),
            )
        }

    return UiTimelineV2.Post(
        message = message,
        platformType = PlatformType.VVo,
        images = actualMedia.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = displayUser,
        quote = listOfNotNull(retweetedStatus?.renderStatusV2(accountKey)).toImmutableList(),
        content = renderVVOText(text.orEmpty(), accountKey),
        actions =
            listOfNotNull(
                if (canReblog) {
                    ActionMenu.Item(
                        icon = UiIcon.Quote,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                        count = UiNumber(repostsCount?.content?.toLongOrNull() ?: 0),
                        clickEvent =
                            ClickEvent.Deeplink(
                                DeeplinkRoute.Compose.Quote(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                            ),
                    )
                } else {
                    null
                },
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(commentsCount ?: 0),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        ),
                ),
                ActionMenu.vvoLike(
                    statusKey = statusKey,
                    liked = liked == true,
                    count = attitudesCount ?: 0,
                    accountKey = accountKey,
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.vvoFavorite(
                                statusKey = statusKey,
                                favorited = favorited == true,
                                accountKey = accountKey,
                            ),
                            ActionMenu.Item(
                                icon = UiIcon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.ShareSheet(
                                            statusKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                            shareUrl = url,
                                        ),
                                    ),
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = UiIcon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Status.DeleteConfirm(
                                                accountType = AccountType.Specific(accountKey),
                                                statusKey = statusKey,
                                            ),
                                        ),
                                )
                            } else {
                                ActionMenu.Item(
                                    icon = UiIcon.Report,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                    color = ActionMenu.Item.Color.Red,
                                    clickEvent = ClickEvent.Noop,
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.VVOStatus(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun Comment.renderStatusV2(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(id = id, host = accountKey.host)
    val statusMid =
        status?.mid ?: analysis_extra
            ?.split('|')
            ?.mapNotNull {
                val pair = it.split(':')
                if (pair.size == 2) pair[0] to pair[1] else null
            }?.toMap()
            ?.get("mid")
    val user = this.user?.render(accountKey)
    val isFromMe = user?.key == accountKey
    // val displayUser = user?.copy(handle = source ?: user.handle)
    val url =
        buildString {
            append("https://$vvoHostLong/")
            append(this@renderStatusV2.user?.id)
            append('/')
            append(bid)
        }
    val media =
        listOfNotNull(
            pic?.let {
                val imageUrl = it.large?.url ?: it.url
                val previewUrl = it.url ?: imageUrl
                if (!imageUrl.isNullOrEmpty() && !previewUrl.isNullOrEmpty()) {
                    UiMedia.Image(
                        url = imageUrl,
                        width = it.large?.geoValue?.widthValue ?: it.geoValue?.widthValue ?: 0f,
                        height = it.large?.geoValue?.heightValue ?: it.geoValue?.heightValue ?: 0f,
                        previewUrl = previewUrl,
                        description = null,
                        sensitive = false,
                    )
                } else {
                    null
                }
            },
        )

    val quote =
        commentList
            ?.map {
                it.renderStatusV2(accountKey).copy(
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.VVOComment(
                                commentKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            ),
                        ),
                )
            }?.toImmutableList()
            ?: listOfNotNull(status?.renderStatusV2(accountKey)).toImmutableList()

    return UiTimelineV2.Post(
        platformType = PlatformType.VVo,
        images = media.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = user,
        quote = quote,
        content = renderVVOText(text.orEmpty(), accountKey),
        actions =
            listOfNotNull(
                statusMid?.let {
                    ActionMenu.Item(
                        icon = UiIcon.Comment,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                        count = UiNumber(totalNumber ?: 0),
                        clickEvent =
                            ClickEvent.Deeplink(
                                DeeplinkRoute.Compose.VVOReplyComment(
                                    accountKey = accountKey,
                                    replyTo = statusKey,
                                    rootId = statusMid,
                                ),
                            ),
                    )
                },
                ActionMenu.vvoLikeComment(
                    statusKey = statusKey,
                    liked = liked == true,
                    count = likeCount ?: 0,
                    accountKey = accountKey,
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon = UiIcon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.ShareSheet(
                                            statusKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                            shareUrl = url,
                                        ),
                                    ),
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = UiIcon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Status.DeleteConfirm(
                                                accountType = AccountType.Specific(accountKey),
                                                statusKey = statusKey,
                                            ),
                                        ),
                                )
                            } else {
                                null
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
        clickEvent =
            ClickEvent.Deeplink(
                if (status != null) {
                    DeeplinkRoute.Status.VVOStatus(
                        statusKey = status.renderStatusV2(accountKey).statusKey,
                        accountType = AccountType.Specific(accountKey),
                    )
                } else {
                    DeeplinkRoute.Status.VVOComment(
                        commentKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    )
                },
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun User.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = id.toString(),
            host = accountKey.host,
        )
    return UiProfile(
        key = userKey,
        avatar = avatarHD ?: profileImageURL ?: "",
        handle =
            UiHandle(
                raw = screenName.orEmpty(),
                host = accountKey.host,
            ),
        nameInternal = screenName.toString().toUiPlainText(),
        description = description?.toUiPlainText(),
        banner = coverImagePhone,
        matrices =
            UiProfile.Matrices(
                followsCount = followCount ?: 0,
                platformFansCount = followersCountStr.orEmpty(),
                statusesCount = statusesCount ?: 0,
                fansCount = 0,
            ),
        mark =
            listOfNotNull(
                if (verified == true) {
                    UiProfile.Mark.Verified
                } else {
                    null
                },
            ).toImmutableList(),
        bottomContent =
            verifiedReason
                ?.takeIf {
                    it.isNotEmpty() && verified == true
                }?.let {
                    UiProfile.BottomContent.Iconify(
                        items =
                            mapOf(
                                UiProfile.BottomContent.Iconify.Icon.Verify to
                                    it.toUiPlainText(),
                            ).toImmutableMap(),
                    )
                },
        platformType = PlatformType.VVo,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
    )
}

internal fun ActionMenu.Companion.vvoLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_like_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
            ),
        count = UiNumber(count),
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.VVO.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

internal fun ActionMenu.Companion.vvoLikeComment(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_like_comment_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
            ),
        count = UiNumber(count),
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.VVO.LikeComment(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

internal fun ActionMenu.Companion.vvoFavorite(
    statusKey: MicroBlogKey,
    favorited: Boolean,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_favorite_$statusKey",
        icon = if (favorited) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (favorited) ActionMenu.Item.Text.Localized.Type.Unbookmark else ActionMenu.Item.Text.Localized.Type.Bookmark,
            ),
        count = UiNumber(0),
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.VVO.Favorite(
                    postKey = statusKey,
                    favorited = favorited,
                    accountKey = accountKey,
                ),
            ),
    )

internal fun renderVVOText(
    text: String,
    accountKey: MicroBlogKey,
): UiRichText {
    val element = parseHtml(text)

    element.childNodes().forEach {
        replaceMentionAndHashtag(element, it, accountKey)
    }
    return element.toUi()
}

private fun replaceMentionAndHashtag(
    element: Element,
    node: Node,
    accountKey: MicroBlogKey,
) {
    if (node is Element) {
        val href = node.attribute("href")?.value
        if (href != null) {
            if (href.all { it.isDigit() }) {
                val statusId = href
                node.attributes().put(
                    "href",
                    DeeplinkRoute.Status
                        .VVOStatus(
                            statusKey = MicroBlogKey(statusId, accountKey.host),
                            accountType = AccountType.Specific(accountKey),
                        ).toUri(),
                )
            } else if (href.startsWith("/n/")) {
                val id = href.removePrefix("/n/")
                if (id.isNotEmpty()) {
                    node.attributes().put(
                        "href",
                        DeeplinkRoute.Profile
                            .UserNameWithHost(
                                accountType = AccountType.Specific(accountKey),
                                userName = id,
                                host = accountKey.host,
                            ).toUri(),
                    )
                }
            } else if (href.startsWith("https://$vvoHost/search")) {
                node.attributes().put(
                    "href",
                    DeeplinkRoute.Search(AccountType.Specific(accountKey), node.text()).toUri(),
                )
            } else if (href.startsWith("https://$vvoHostShort/sinaurl?u=")) {
                val url =
                    href.removePrefix("https://$vvoHostShort/sinaurl?u=").decodeURLPart()
                if (url.contains("sinaimg.cn/")) {
                    node.attributes().put(
                        "href",
                        DeeplinkRoute.Media.Image(uri = url, previewUrl = null).toUri(),
                    )
                }
            }
        }
        node.childNodes().forEach {
            replaceMentionAndHashtag(element, it, accountKey)
        }
    }
}
