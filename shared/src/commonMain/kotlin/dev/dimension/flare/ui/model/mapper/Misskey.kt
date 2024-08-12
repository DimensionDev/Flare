package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.NotificationType
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserLite
import dev.dimension.flare.data.network.misskey.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import moe.tlaster.mfm.parser.MFMParser
import moe.tlaster.mfm.parser.tree.BoldNode
import moe.tlaster.mfm.parser.tree.CashNode
import moe.tlaster.mfm.parser.tree.CenterNode
import moe.tlaster.mfm.parser.tree.CodeBlockNode
import moe.tlaster.mfm.parser.tree.EmojiCodeNode
import moe.tlaster.mfm.parser.tree.FnNode
import moe.tlaster.mfm.parser.tree.HashtagNode
import moe.tlaster.mfm.parser.tree.InlineCodeNode
import moe.tlaster.mfm.parser.tree.ItalicNode
import moe.tlaster.mfm.parser.tree.LinkNode
import moe.tlaster.mfm.parser.tree.MathBlockNode
import moe.tlaster.mfm.parser.tree.MathInlineNode
import moe.tlaster.mfm.parser.tree.MentionNode
import moe.tlaster.mfm.parser.tree.QuoteNode
import moe.tlaster.mfm.parser.tree.RootNode
import moe.tlaster.mfm.parser.tree.SearchNode
import moe.tlaster.mfm.parser.tree.SmallNode
import moe.tlaster.mfm.parser.tree.StrikeNode
import moe.tlaster.mfm.parser.tree.UrlNode

internal fun Notification.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): UiTimeline {
    val user = user?.render(accountKey)
    val status = note?.renderStatus(accountKey, event)
    val topMessageType =
        when (this.type) {
            NotificationType.Follow -> UiTimeline.TopMessage.MessageType.Misskey.Follow
            NotificationType.Mention -> UiTimeline.TopMessage.MessageType.Misskey.Mention
            NotificationType.Reply -> UiTimeline.TopMessage.MessageType.Misskey.Reply
            NotificationType.Renote -> UiTimeline.TopMessage.MessageType.Misskey.Renote
            NotificationType.Quote -> UiTimeline.TopMessage.MessageType.Misskey.Quote
            NotificationType.Reaction -> UiTimeline.TopMessage.MessageType.Misskey.Reaction
            NotificationType.PollEnded -> UiTimeline.TopMessage.MessageType.Misskey.PollEnded
            NotificationType.ReceiveFollowRequest -> UiTimeline.TopMessage.MessageType.Misskey.ReceiveFollowRequest
            NotificationType.FollowRequestAccepted -> UiTimeline.TopMessage.MessageType.Misskey.FollowRequestAccepted
            NotificationType.AchievementEarned -> UiTimeline.TopMessage.MessageType.Misskey.AchievementEarned
            NotificationType.App -> UiTimeline.TopMessage.MessageType.Misskey.App
        }
    val topMessage =
        UiTimeline.TopMessage(
            user = user,
            icon = UiTimeline.TopMessage.Icon.Retweet,
            type = topMessageType,
            onClicked = {
                if (user != null) {
                    launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = user.key))
                }
            },
        )
    return UiTimeline(
        topMessage = topMessage,
        content =
            when {
                type in
                    listOf(
                        NotificationType.Follow,
                        NotificationType.FollowRequestAccepted,
                        NotificationType.ReceiveFollowRequest,
                    ) &&
                    user != null
                ->
                    UiTimeline.ItemContent.User(user)

                else ->
                    status ?: user?.let { UiTimeline.ItemContent.User(it) }
            },
        platformType = PlatformType.Misskey,
    )
}

internal fun Note.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): UiTimeline {
    val user = user.render(accountKey)
    val topMessage =
        if (renote == null || !text.isNullOrEmpty()) {
            null
        } else {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Retweet,
                type = UiTimeline.TopMessage.MessageType.Mastodon.Reblogged,
                onClicked = {
                    launcher.launch(
                        AppDeepLink.Profile(
                            accountKey = accountKey,
                            userKey = user.key,
                        ),
                    )
                },
            )
        }
    val currentStatus = this.renderStatus(accountKey, event)
    val actualStatus = renote ?: this
    return UiTimeline(
        topMessage = topMessage,
        content =
            actualStatus.renderStatus(accountKey, event).copy(
                onClicked = {
                    launcher.launch(
                        AppDeepLink.StatusDetail(
                            accountKey = accountKey,
                            statusKey = currentStatus.statusKey,
                        ),
                    )
                },
                statusKey = currentStatus.statusKey,
            ),
        platformType = PlatformType.Misskey,
    )
}

internal fun Note.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): UiTimeline.ItemContent.Status {
    val user = user.render(accountKey)
    val isFromMe = user.key == accountKey
    val canReblog = visibility in listOf(Visibility.Public, Visibility.Home)
    val renderedVisibility =
        when (visibility) {
            Visibility.Public -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public
            Visibility.Home -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home
            Visibility.Followers -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers
            Visibility.Specified -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified
        }
    val statusKey =
        MicroBlogKey(
            id,
            host = user.key.host,
        )
    val reaction =
        reactions
            .map { emoji ->
                UiTimeline.ItemContent.Status.BottomContent.Reaction.EmojiReaction(
                    name = emoji.key,
                    count = emoji.value,
                    url = resolveMisskeyEmoji(emoji.key, accountKey.host),
                    onClicked = {
                        event.react(
                            statusKey = statusKey,
                            hasReacted = myReaction != null,
                            reaction = emoji.key,
                        )
                    },
                )
            }.toPersistentList()
    return UiTimeline.ItemContent.Status(
        images =
            files
                ?.mapNotNull { file ->
                    file.toUi()
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning = cw,
        user = user,
        quote =
            listOfNotNull(
                if (text != null || !files.isNullOrEmpty() || cw != null) {
                    renote?.renderStatus(accountKey, event)
                } else {
                    null
                },
            ).toImmutableList(),
        content = misskeyParser.parse(text.orEmpty()).toHtml(accountKey).toUi(),
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = repliesCount.toLong(),
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
                    },
                ),
                if (canReblog) {
                    StatusAction.Group(
                        displayItem =
                            StatusAction.Item.Retweet(
                                count = renoteCount.toLong(),
                                retweeted = renoteId != null,
                                onClicked = {},
                            ),
                        actions =
                            listOfNotNull(
                                StatusAction.Item.Retweet(
                                    count = renoteCount.toLong(),
                                    retweeted = renoteId != null,
                                    onClicked = {
                                        event.renote(
                                            statusKey = statusKey,
                                        )
                                    },
                                ),
                                StatusAction.Item.Quote(
                                    count = 0,
                                    onClicked = {
                                        launcher.launch(
                                            AppDeepLink.Compose.Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                ),
                            ).toImmutableList(),
                    )
                } else {
                    null
                },
                StatusAction.Item.Reaction(
                    reacted = myReaction != null,
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Misskey.AddReaction(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
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
                                        launcher.launch(
                                            AppDeepLink.Misskey.ReportStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                                userKey = user.key,
                                            ),
                                        )
                                    },
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        poll =
            poll?.let {
                UiPoll(
                    // misskey poll doesn't have id
                    id = "",
                    options =
                        poll.choices
                            .map { option ->
                                UiPoll.Option(
                                    title = option.text,
                                    votesCount = option.votes.toLong(),
                                    percentage =
                                        option.votes
                                            .toFloat()
                                            .div(
                                                poll.choices.sumOf { it.votes }.toFloat(),
                                            ).takeUnless { it.isNaN() } ?: 0f,
                                )
                            }.toPersistentList(),
                    expiresAt = poll.expiresAt ?: Instant.DISTANT_PAST,
                    multiple = poll.multiple,
                    ownVotes = List(poll.choices.filter { it.isVoted }.size) { index -> index }.toPersistentList(),
                )
            },
        statusKey = statusKey,
        card = null,
        createdAt = Instant.parse(createdAt).toUi(),
        topEndContent =
            UiTimeline.ItemContent.Status.TopEndContent
                .Visibility(renderedVisibility),
        bottomContent =
            UiTimeline.ItemContent.Status.BottomContent
                .Reaction(reaction, myReaction),
        sensitive = files?.any { it.isSensitive } ?: false,
        onClicked = {
            launcher.launch(
                AppDeepLink.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
        accountKey = accountKey,
    )
}

private fun DriveFile.toUi(): UiMedia? {
    if (type.startsWith("image/")) {
        return UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = thumbnailUrl.orEmpty(),
            description = comment,
            width = properties.width?.toFloat() ?: 0f,
            height = properties.height?.toFloat() ?: 0f,
            sensitive = isSensitive,
        )
    } else if (type.startsWith("video/")) {
        return UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = thumbnailUrl.orEmpty(),
            description = comment,
            width = properties.width?.toFloat() ?: 0f,
            height = properties.height?.toFloat() ?: 0f,
        )
    } else {
        return null
    }
}

internal fun UserLite.render(accountKey: MicroBlogKey): UiProfile {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    val userKey =
        MicroBlogKey(
            id = id,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatarUrl.orEmpty(),
        name = parseName(name.orEmpty(), accountKey).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
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
        platformType = PlatformType.Misskey,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

internal fun User.render(accountKey: MicroBlogKey): UiProfile {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    val userKey =
        MicroBlogKey(
            id = id,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatarUrl.orEmpty(),
        name = parseName(name.orEmpty(), accountKey).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = bannerUrl,
        description = description?.let { misskeyParser.parse(it).toHtml(accountKey).toUi() },
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount.toLong(),
                followsCount = followingCount.toLong(),
                statusesCount = notesCount.toLong(),
            ),
        mark =
            listOfNotNull(
                if (isCat == true) {
                    UiProfile.Mark.Cat
                } else {
                    null
                },
                if (isBot == true) {
                    UiProfile.Mark.Bot
                } else {
                    null
                },
            ).toImmutableList(),
        bottomContent =
            fields
                .takeIf {
                    it.any()
                }?.let {
                    UiProfile.BottomContent.Fields(
                        fields =
                            it
                                .associate { (key, value) ->
                                    key to misskeyParser.parse(value).toHtml(accountKey).toUi()
                                }.toImmutableMap(),
                    )
                },
        platformType = PlatformType.Misskey,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

internal fun EmojiSimple.toUi(): UiEmoji =
    UiEmoji(
        shortcode = name,
        url = url,
    )

internal fun resolveMisskeyEmoji(
    name: String,
    accountHost: String,
): String =
    name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }

private val misskeyParser by lazy {
    MFMParser()
}

private fun parseName(
    name: String,
    accountKey: MicroBlogKey,
): Element {
    if (name.isEmpty()) {
        return Element("body")
    }
    return misskeyParser.parse(name).toHtml(accountKey) as? Element ?: Element("body")
}

private fun moe.tlaster.mfm.parser.tree.Node.toHtml(accountKey: MicroBlogKey): Element =
    when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is CodeBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
                        language?.let {
//                            attributes["lang"] = it
                            attributes().put("lang", it)
                        }
                        appendChild(TextNode(code))
                    },
                )
            }
        }

        is MathBlockNode -> {
            Element("pre").apply {
                appendChild(
                    Element("code").apply {
//                        attributes["lang"] = "math"
                        attributes().put("lang", "math")
                        appendChild(TextNode(formula))
                    },
                )
            }
        }

        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is SearchNode -> {
            Element("search").apply {
                appendChild(TextNode(query))
            }
        }

        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is FnNode -> {
            Element("fn").apply {
//                attributes["name"] = name
                attributes().put("name", name)
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    appendChild(it.toHtml(accountKey))
                }
            }
        }

        is CashNode -> {
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, "$$content")
                attributes().put("href", AppDeepLink.Search(accountKey, "$$content"))
                appendChild(TextNode("$$content"))
            }
        }

        is EmojiCodeNode -> {
            Element("img").apply {
//                attributes["src"] = resolveMisskeyEmoji(emoji, accountKey.host)
//                attributes["alt"] = emoji
                attributes().put("src", resolveMisskeyEmoji(emoji, accountKey.host))
                attributes().put("alt", emoji)
            }
        }

        is HashtagNode -> {
            Element("a").apply {
//                attributes["href"] = AppDeepLink.Search(accountKey, "#$tag")
                attributes().put("href", AppDeepLink.Search(accountKey, "#$tag"))
                appendChild(TextNode("#$tag"))
            }
        }

        is InlineCodeNode -> {
            Element("code").apply {
                appendChild(TextNode(code))
            }
        }

        is LinkNode -> {
            Element("a").apply {
//                attributes["href"] = url
                attributes().put("href", url)
                appendChild(TextNode(content))
            }
        }

        is MathInlineNode -> {
            Element("code").apply {
//                attributes["lang"] = "math"
                attributes().put("lang", "math")
                appendChild(TextNode(formula))
            }
        }

        is MentionNode -> {
            Element("a").apply {
                val deeplink =
                    host?.let {
                        AppDeepLink.ProfileWithNameAndHost(accountKey, userName, it)
                    } ?: AppDeepLink.ProfileWithNameAndHost(accountKey, userName, accountKey.host)
//                attributes["href"] = deeplink
                attributes().put("href", deeplink)
                appendChild(
                    TextNode(
                        buildString {
                            append("@")
                            append(userName)
                            if (host != null) {
                                append("@")
                                append(host)
                            }
                        },
                    ),
                )
            }
        }

        is moe.tlaster.mfm.parser.tree.TextNode -> {
            Element("span").apply {
                appendChild(TextNode(content))
            }
        }

        is UrlNode -> {
            Element("a").apply {
//                attributes["href"] = url
                attributes().put("href", url)
                appendChild(TextNode(url))
            }
        }
    }
