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
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.render.Render
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.decodeURLPart
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text

internal fun Status.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): Render.Item {
    val message = title?.text
    return Render.Item(
        topMessage =
            message?.let {
                Render.TopMessage(
                    user = user?.render(accountKey),
                    icon = null,
                    type =
                        Render.TopMessage.MessageType.VVO
                            .Custom(it),
                )
            },
        content = renderStatus(accountKey, event),
        platformType = PlatformType.VVo,
    )
}

internal fun Status.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): Render.ItemContent.Status {
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
    val element = Ktml.parse(text.orEmpty())
    element.children.forEach {
        replaceMentionAndHashtag(element, it, accountKey)
    }
    val statusKey =
        MicroBlogKey(
            id = id,
            host = vvoHost,
        )
    return Render.ItemContent.Status(
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
                        },
                    )
                } else {
                    null
                },
                StatusAction.Item.Reply(
                    count = commentsCount ?: 0,
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
                                StatusAction.Item.Delete
                            } else {
                                StatusAction.Item.Report
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        poll = null,
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
    )
}

internal fun User.render(accountKey: MicroBlogKey): Render.ItemContent.User =
    Render.ItemContent.User(
        key =
            MicroBlogKey(
                id = id.toString(),
                host = vvoHost,
            ),
        avatar = avatarHD ?: profileImageURL ?: "",
        handle = "@$screenName@${vvoHost.removePrefix("m.")}",
        name =
            Element("span")
                .apply {
                    children.add(Text(screenName))
                }.toUi(),
    )

internal fun Comment.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): Render.Item =
    Render.Item(
        topMessage = null,
        content = renderStatus(accountKey, event),
        platformType = PlatformType.VVo,
    )

internal fun Comment.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): Render.ItemContent.Status {
    val element = Ktml.parse(text.orEmpty())
    element.children.forEach {
        replaceMentionAndHashtag(element, it, accountKey)
    }
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
    return Render.ItemContent.Status(
        statusKey = statusKey,
        content = element.toUi(),
        user = displayUser,
        quote =
            commentList
                ?.map {
                    it.renderStatus(
                        accountKey,
                        event,
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
                                StatusAction.Item.Delete
                            } else {
                                StatusAction.Item.Report
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
    )
}

internal fun Attitude.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.VVO,
): Render.Item {
    val content = status?.renderStatus(accountKey, event)
    return Render.Item(
        topMessage =
            Render.TopMessage(
                user = null,
                icon = null,
                type = Render.TopMessage.MessageType.VVO.Like,
            ),
        content = content,
        platformType = PlatformType.VVo,
    )
}

internal fun Status.toUi(accountKey: MicroBlogKey): UiStatus.VVO {
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
    return UiStatus.VVO(
        statusKey =
            MicroBlogKey(
                id = id,
                host = vvoHost,
            ),
        accountKey = accountKey,
        content = text.orEmpty(),
        rawContent = rawText.orEmpty(),
        matrices =
            UiStatus.VVO.Matrices(
                commentCount = commentsCount ?: 0,
                repostCount = repostsCount?.content.orEmpty(),
                likeCount = attitudesCount ?: 0,
            ),
        liked = favorited ?: false,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        rawUser = user?.toUi(accountKey),
        regionName = regionName,
        source = source,
        medias = media.toImmutableList(),
        quote = retweetedStatus?.toUi(accountKey),
        canReblog = visible?.type == null || visible.type == 0L,
    )
}

internal fun User.toUi(accountKey: MicroBlogKey): UiUser.VVO =
    UiUser.VVO(
        userKey =
            MicroBlogKey(
                id = id.toString(),
                host = vvoHost,
            ),
        avatarUrl = avatarHD ?: profileImageURL ?: "",
        bannerUrl = coverImagePhone,
        rawHandle = screenName,
        rawDescription = description,
        verified = verified ?: false,
        verifiedReason = verifiedReason,
        accountKey = accountKey,
        matrices =
            UiUser.VVO.Matrices(
                followsCount = followCount ?: 0,
                fansCount = followersCountStr.orEmpty(),
                statusesCount = statusesCount ?: 0,
            ),
        relation =
            UiRelation.VVO(
                following = following ?: false,
                isFans = followMe ?: false,
            ),
    )

internal fun Comment.toUi(accountKey: MicroBlogKey): UiStatus.VVOComment =
    UiStatus.VVOComment(
        statusKey = MicroBlogKey(id = id, host = vvoHost),
        accountKey = accountKey,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        source = source,
        rawUser = user?.toUi(accountKey),
        text = text.orEmpty(),
        likeCount = likeCount ?: 0,
        liked = liked ?: false,
        comments =
            commentList
                ?.mapNotNull {
                    it.toUi(accountKey)
                }.orEmpty()
                .toImmutableList(),
        medias =
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
        status = status?.toUi(accountKey),
        rootId = rootidstr,
    )

internal fun Attitude.toUi(accountKey: MicroBlogKey): UiStatus.VVONotification =
    UiStatus.VVONotification(
        statusKey = MicroBlogKey(id = idStr, host = vvoHost),
        accountKey = accountKey,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        source = source?.let { Ktml.parse(it).innerText },
        rawUser = user?.toUi(accountKey),
        status = status?.toUi(accountKey),
    )

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
