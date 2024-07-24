package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.vvo.model.Attitude
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.decodeURLPart
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.datetime.Clock
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text

internal fun Status.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline {
    val message = title?.text
    return UiTimeline(
        topMessage =
            message?.let {
                val rendered = user?.render(accountKey)
                UiTimeline.TopMessage(
                    user = rendered,
                    icon = UiTimeline.TopMessage.Icon.Info,
                    type =
                        UiTimeline.TopMessage.MessageType.VVO
                            .Custom(it),
                    onClicked = {
                        if (rendered != null) {
                            launcher.launch(
                                AppDeepLink.Profile(
                                    accountKey = accountKey,
                                    userKey = rendered.key,
                                ),
                            )
                        }
                    },
                )
            },
        content = renderStatus(accountKey, event),
        platformType = PlatformType.VVo,
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
                if (it.type == "video") {
                    null
//                    UiMedia.Video(
//                        url = it.videoSrc,
//                        thumbnailUrl = it.url ?: url,
//                        width = it.large?.geo?.widthValue ?: it.geo?.widthValue ?: 0f,
//                        height = it.large?.geo?.heightValue ?: it.geo?.heightValue ?: 0f,
//                        description = null,
//                    )
                } else {
                    UiMedia.Image(
                        url = url,
                        width = it.large?.geo?.widthValue ?: it.geo?.widthValue ?: 0f,
                        height = it.large?.geo?.heightValue ?: it.geo?.heightValue ?: 0f,
                        previewUrl = it.url ?: url,
                        description = null,
                        sensitive = false,
                    )
                }
            }
        } +
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
        images = media.toImmutableList(),
        actions =
            listOfNotNull(
                if (canReblog) {
                    StatusAction.Item.Retweet(
                        count = repostsCount?.content?.toLongOrNull() ?: 0,
                        retweeted = false,
                        onClicked = {
                            launcher.launch(
                                AppDeepLink.Compose.Quote(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                            )
                        },
                    )
                } else {
                    null
                },
                StatusAction.Item.Reply(
                    count = commentsCount ?: 0,
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
                    },
                ),
                StatusAction.Item.Like(
                    count = attitudesCount ?: 0,
                    liked = favorited ?: false,
                    onClicked = {
                        event.like(statusKey, favorited ?: false)
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            if (isFromMe) {
                                StatusAction.Item.Delete(
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.DeleteStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                StatusAction.Item.Report(
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
                AppDeepLink.VVO.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
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
        name =
            Element("span")
                .apply {
                    children.add(Text(screenName))
                }.toUi(),
        description = description?.let { Element("span").apply { children.add(Text(it)) }.toUi() },
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
            verifiedReason?.let {
                UiProfile.BottomContent.Iconify(
                    items =
                        mapOf(
                            UiProfile.BottomContent.Iconify.Icon.Verify to
                                Element("span")
                                    .apply {
                                        children.add(Text(it))
                                    }.toUi(),
                        ).toImmutableMap(),
                )
            },
        platformType = PlatformType.VVo,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
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
        platformType = PlatformType.VVo,
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
                                    AppDeepLink.VVO.CommentDetail(
                                        accountKey = accountKey,
                                        statusKey = statusKey,
                                    ),
                                )
                            },
                        )
                }.orEmpty()
                .toImmutableList(),
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
                            width = it.large?.geo?.widthValue ?: it.geo?.widthValue ?: 0f,
                            height = it.large?.geo?.heightValue ?: it.geo?.heightValue ?: 0f,
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
                StatusAction.Item.Reply(
                    count = replyCount ?: 0,
                    onClicked = {
                        if (rootidstr != null) {
                            launcher.launch(
                                AppDeepLink.VVO.ReplyToComment(
                                    accountKey = accountKey,
                                    replyTo = statusKey,
                                    rootId = rootidstr,
                                ),
                            )
                        }
                    },
                ),
                StatusAction.Item.Like(
                    count = likeCount ?: 0,
                    liked = liked ?: false,
                    onClicked = {
                        event.likeComment(statusKey, liked ?: false)
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            if (isFromMe) {
                                StatusAction.Item.Delete(
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.DeleteStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                StatusAction.Item.Report(
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
            launcher.launch(
                AppDeepLink.VVO.CommentDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
    )
}

internal fun Attitude.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): UiTimeline {
    val content = status?.renderStatus(accountKey, event)
    val user = user?.render(accountKey)
    return UiTimeline(
        topMessage =
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Favourite,
                type = UiTimeline.TopMessage.MessageType.VVO.Like,
                onClicked = {
                    if (user != null) {
                        launcher.launch(
                            AppDeepLink.Profile(
                                accountKey = accountKey,
                                userKey = user.key,
                            ),
                        )
                    }
                },
            ),
        content = content,
        platformType = PlatformType.VVo,
    )
}

internal fun renderVVOText(
    text: String,
    accountKey: MicroBlogKey,
): Element {
    val element = Ktml.parse(text)
    element.children.forEach {
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
        val href = node.attributes["href"]
        if (href != null) {
            if (href.startsWith("/n/")) {
                val id = href.removePrefix("/n/")
                if (id.isNotEmpty()) {
                    node.attributes["href"] =
                        AppDeepLink.ProfileWithNameAndHost(
                            accountKey = accountKey,
                            userName = id,
                            host = accountKey.host,
                        )
                }
            } else if (href.startsWith("https://$vvoHost/search")) {
                node.attributes["href"] = AppDeepLink.Search(accountKey, node.innerText)
            } else if (href.startsWith("https://weibo.cn/sinaurl?u=")) {
                val url =
                    href.removePrefix("https://weibo.cn/sinaurl?u=").decodeURLPart()
                if (url.contains("sinaimg.cn/")) {
                    node.attributes["href"] = AppDeepLink.RawImage(url)
                }
            }
        }
        node.children.forEach { replaceMentionAndHashtag(element, it, accountKey) }
    }
}
