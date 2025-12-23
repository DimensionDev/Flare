package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
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
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.DeeplinkRoute.Companion.toUri
import io.ktor.http.decodeURLPart
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlin.time.Clock

internal fun Status.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline {
    val message = title?.text
    return UiTimeline(
        topMessage =
            message?.let {
                UiTimeline.TopMessage(
                    user = null,
                    icon = UiTimeline.TopMessage.Icon.Info,
                    type =
                        UiTimeline.TopMessage.MessageType.VVO
                            .Custom(it),
                    onClicked = {
                    },
                    statusKey = MicroBlogKey(id, vvoHost),
                )
            },
        content = renderStatus(accountKey, event),
    )
}

internal fun Status.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline.ItemContent.Status {
    val media =
        picsList.orEmpty().mapNotNull {
            val url = it.large?.url ?: it.url
            if (url.isNullOrEmpty()) {
                null
            } else {
                if (it.type == "video" && it.videoSrc != null) {
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
    val canReblog = visible?.type == null || visible.type == 0L
    val rawUser = this.user?.render(accountKey)
    val isFromMe = rawUser?.key == accountKey
    val displayUser =
        rawUser?.copy(
            handle = regionName ?: source ?: rawUser.handle,
        )
    val element = renderVVOText(text.orEmpty(), accountKey)
    val statusKey =
        MicroBlogKey(
            id = id,
            host = vvoHost,
        )
    val url =
        buildString {
            append("https://$vvoHostLong/")
            append(user?.id)
            append('/')
            append(bid)
        }
    return UiTimeline.ItemContent.Status(
        statusKey = statusKey,
        content = element.toUi(),
        user = displayUser,
        quote =
            listOfNotNull(
                retweetedStatus?.renderStatus(
                    accountKey,
                    event,
                ),
            ).toImmutableList(),
        card = null,
        contentWarning = null,
        images = actualMedia.toImmutableList(),
        actions =
            listOfNotNull(
                if (canReblog) {
                    ActionMenu.Item(
                        icon = ActionMenu.Item.Icon.Reply,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                        count = UiNumber(repostsCount?.content?.toLongOrNull() ?: 0),
                        onClicked = {
                            launcher.launch(
                                DeeplinkRoute.Compose
                                    .Quote(
                                        accountKey = accountKey,
                                        statusKey = statusKey,
                                    ).toUri(),
                            )
                        },
                    )
                } else {
                    null
                },
                ActionMenu.Item(
                    icon = ActionMenu.Item.Icon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(commentsCount ?: 0),
                    onClicked = {
                        launcher.launch(
                            DeeplinkRoute.Compose
                                .Reply(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ).toUri(),
                        )
                    },
                ),
                ActionMenu.Item(
                    icon = if (liked == true) ActionMenu.Item.Icon.Unlike else ActionMenu.Item.Icon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (liked == true) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
                        ),
                    count = UiNumber(attitudesCount ?: 0),
                    color = if (liked == true) ActionMenu.Item.Color.Red else null,
                    onClicked = {
                        event.like(statusKey, liked ?: false)
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon = if (favorited == true) ActionMenu.Item.Icon.Unbookmark else ActionMenu.Item.Icon.Bookmark,
                                text =
                                    ActionMenu.Item.Text.Localized(
                                        if (favorited ==
                                            true
                                        ) {
                                            ActionMenu.Item.Text.Localized.Type.Unbookmark
                                        } else {
                                            ActionMenu.Item.Text.Localized.Type.Bookmark
                                        },
                                    ),
                                count = UiNumber(0),
                                onClicked = {
                                    event.favorite(statusKey, favorited ?: false)
                                },
                            ),
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                shareContent = url,
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        launcher.launch(
                                            DeeplinkRoute.Status
                                                .DeleteConfirm(
                                                    accountType = AccountType.Specific(accountKey),
                                                    statusKey = statusKey,
                                                ).toUri(),
                                        )
                                    },
                                )
                            } else {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Report,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        // TODO: Report
                                    },
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        poll = null,
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
        sensitive = false,
        onClicked = {
            launcher.launch(
                DeeplinkRoute.Status
                    .VVOStatus(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ).toUri(),
            )
        },
        platformType = PlatformType.VVo,
        onMediaClicked = { media, index ->
            launcher.launch(
                DeeplinkRoute.Media
                    .StatusMedia(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = statusKey,
                        index = index,
                        preview =
                            when (media) {
                                is UiMedia.Image -> media.previewUrl
                                is UiMedia.Video -> media.thumbnailUrl
                                is UiMedia.Audio -> null
                                is UiMedia.Gif -> media.previewUrl
                            },
                    ).toUri(),
            )
        },
        url = url,
    )
}

internal fun User.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = id.toString(),
            host = vvoHost,
        )
    return UiProfile(
        key = userKey,
        avatar = avatarHD ?: profileImageURL ?: "",
        handle = "@$screenName@${vvoHost.removePrefix("m.")}",
        nameInternal =
            Element("span")
                .apply {
                    appendChild(TextNode(screenName.toString()))
                }.toUi(),
        description =
            description?.let {
                Element("span")
                    .apply {
                        appendChild(TextNode(it))
                    }.toUi()
            },
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
                                    Element("span")
                                        .apply {
                                            appendChild(TextNode(it))
                                        }.toUi(),
                            ).toImmutableMap(),
                    )
                },
        platformType = PlatformType.VVo,
        onClicked = {
            launcher.launch(DeeplinkRoute.Profile.User(accountType = AccountType.Specific(accountKey), userKey = userKey).toUri())
        },
    )
}

internal fun Comment.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline =
    UiTimeline(
        topMessage = null,
        content = renderStatus(accountKey, event),
    )

internal fun Comment.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline.ItemContent.Status {
    val element = renderVVOText(text.orEmpty(), accountKey)
    val rawUser = this.user?.render(accountKey)
    val isFromMe = rawUser?.key == accountKey
    val displayUser =
        rawUser?.copy(
            handle = source ?: rawUser.handle,
        )
    val statusKey =
        MicroBlogKey(
            id = id,
            host = vvoHost,
        )
    val statusMid =
        status?.mid ?: run {
            analysis_extra
                ?.split('|')
                ?.mapNotNull {
                    val pair = it.split(':')
                    if (pair.size == 2) {
                        pair[0] to pair[1]
                    } else {
                        null
                    }
                }?.toMap()
                ?.get("mid")
        }
    val url =
        buildString {
            append("https://$vvoHostLong/")
            append(user?.id)
            append('/')
            append(bid)
        }

    return UiTimeline.ItemContent.Status(
        statusKey = statusKey,
        content = element.toUi(),
        user = displayUser,
        quote =
            commentList
                ?.map {
                    it
                        .renderStatus(
                            accountKey,
                            event,
                        ).copy(
                            onClicked = {
                                launcher.launch(
                                    DeeplinkRoute.Status
                                        .VVOComment(
                                            commentKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                        ).toUri(),
                                )
                            },
                        )
                }?.toImmutableList() ?: listOfNotNull(status)
                .map {
                    it.renderStatus(
                        accountKey,
                        event,
                    )
                }.toImmutableList(),
        card = null,
        contentWarning = null,
        images =
            listOfNotNull(
                pic?.let {
                    val url = it.large?.url ?: it.url
                    val previewUrl = it.url ?: url
                    if (!url.isNullOrEmpty() && !previewUrl.isNullOrEmpty()) {
                        UiMedia.Image(
                            url = url,
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
            ).toImmutableList(),
        poll = null,
        actions =
            listOfNotNull(
                statusMid?.let {
                    ActionMenu.Item(
                        icon = ActionMenu.Item.Icon.Comment,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                        count = UiNumber(totalNumber ?: 0),
                        onClicked = {
                            launcher.launch(
                                DeeplinkRoute.Compose
                                    .VVOReplyComment(
                                        accountKey = accountKey,
                                        replyTo = statusKey,
                                        rootId = statusMid,
                                    ).toUri(),
                            )
                        },
                    )
                },
                ActionMenu.Item(
                    icon = if (liked == true) ActionMenu.Item.Icon.Unlike else ActionMenu.Item.Icon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (liked == true) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
                        ),
                    count = UiNumber(likeCount ?: 0),
                    color = if (liked == true) ActionMenu.Item.Color.Red else null,
                    onClicked = {
                        event.likeComment(statusKey, liked ?: false)
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                shareContent = url,
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        launcher.launch(
                                            DeeplinkRoute.Status
                                                .DeleteConfirm(
                                                    accountType = AccountType.Specific(accountKey),
                                                    statusKey = statusKey,
                                                ).toUri(),
                                        )
                                    },
                                )
                            } else {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Report,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        // TODO: Report
                                    },
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
        sensitive = false,
        onClicked = {
            if (status != null) {
                launcher.launch(
                    DeeplinkRoute.Status
                        .VVOStatus(
                            statusKey = status.renderStatus(accountKey, event).statusKey,
                            accountType = AccountType.Specific(accountKey),
                        ).toUri(),
                )
            } else {
                launcher.launch(
                    DeeplinkRoute.Status
                        .VVOComment(
                            commentKey = statusKey,
                            accountType = AccountType.Specific(accountKey),
                        ).toUri(),
                )
            }
        },
        platformType = PlatformType.VVo,
        onMediaClicked = { media, index ->
            launcher.launch(
                DeeplinkRoute.Media
                    .StatusMedia(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = statusKey,
                        index = index,
                        preview =
                            when (media) {
                                is UiMedia.Image -> media.previewUrl
                                is UiMedia.Video -> media.thumbnailUrl
                                is UiMedia.Audio -> null
                                is UiMedia.Gif -> media.previewUrl
                            },
                    ).toUri(),
            )
        },
        url = url,
    )
}

internal fun Attitude.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline {
    val content = status?.renderStatus(accountKey, event)
    val user = user?.render(accountKey)
    val userListContent =
        UiTimeline.ItemContent.UserList(
            users = listOfNotNull(user).toImmutableList(),
            status = content,
        )
    return UiTimeline(
        topMessage =
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Favourite,
                type = UiTimeline.TopMessage.MessageType.VVO.Like,
                onClicked = {
                    if (user != null) {
                        launcher.launch(
                            DeeplinkRoute.Profile
                                .User(
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.key,
                                ).toUri(),
                        )
                    }
                },
                statusKey = MicroBlogKey(id.toString(), vvoHost),
            ),
        content = userListContent,
    )
}

internal fun renderVVOText(
    text: String,
    accountKey: MicroBlogKey,
): Element {
    val element = parseHtml(text)

    element.childNodes().forEach {
        replaceMentionAndHashtag(element, it, accountKey)
    }
    return element
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

internal fun MastodonList.render(): UiList =
    UiList(
        id = id.orEmpty(),
        title = title.orEmpty(),
        platformType = PlatformType.Mastodon,
    )
