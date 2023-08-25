package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserLite
import dev.dimension.flare.data.network.misskey.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.screen.destinations.ProfileWithUserNameAndHostRouteDestination
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
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
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val misskeyParser by lazy {
    MFMParser()
}

internal fun Notification.toUi(
    accountKey: MicroBlogKey
): UiStatus.MisskeyNotification {
    val user = user?.toUi(accountKey.host)
    return UiStatus.MisskeyNotification(
        statusKey = MicroBlogKey(
            id,
            host = accountKey.host
        ),
        user = user,
        createdAt = createdAt.toInstant(),
        note = note?.toUi(accountKey),
        type = type,
        accountKey = accountKey,
        achievement = achievement
    )
}

internal fun Note.toUi(
    accountKey: MicroBlogKey
): UiStatus.Misskey {
    val user = user.toUi(accountKey.host)
    return UiStatus.Misskey(
        statusKey = MicroBlogKey(
            id,
            host = user.userKey.host
        ),
        sensitive = files?.any { it.isSensitive } ?: false,
        poll = poll?.let {
            UiStatus.Misskey.Poll(
                // misskey poll doesn't have id
                id = "",
                options = poll.choices.map { option ->
                    UiStatus.Misskey.PollOption(
                        title = option.text,
                        votesCount = option.votes.toLong(),
                        percentage = option.votes.toFloat().div(
                            poll.choices.sumOf { it.votes }.toFloat()
                        ).takeUnless { it.isNaN() } ?: 0f,
                        voted = option.isVoted
                    )
                }.toPersistentList(),
                expiresAt = poll.expiresAt ?: Instant.DISTANT_PAST,
                multiple = poll.multiple
            )
        },
        // TODO: parse card content lazily
        card = null,
        createdAt = createdAt.toInstant(),
        content = text.orEmpty(),
        contentToken = parseContent(this, user.userKey.host),
        contentWarningText = cw,
        user = user,
        matrices = UiStatus.Misskey.Matrices(
            replyCount = repliesCount.toLong(),
            renoteCount = renoteCount.toLong()
        ),
        renote = if (text.isNullOrEmpty()) {
            renote?.toUi(accountKey)
        } else {
            null
        },
        quote = if (text != null || !files.isNullOrEmpty() || cw != null) {
            renote?.toUi(accountKey)
        } else {
            null
        },
        visibility = when (visibility) {
            Visibility.Public -> UiStatus.Misskey.Visibility.Public
            Visibility.Home -> UiStatus.Misskey.Visibility.Home
            Visibility.Followers -> UiStatus.Misskey.Visibility.Followers
            Visibility.Specified -> UiStatus.Misskey.Visibility.Specified
        },
        media = files?.mapNotNull { file ->
            file.toUi()
        }?.toPersistentList() ?: persistentListOf(),
        reaction = UiStatus.Misskey.Reaction(
            myReaction = myReaction,
            emojiReactions = reactions.map { emoji ->
                UiStatus.Misskey.EmojiReaction(
                    name = emoji.key,
                    count = emoji.value,
                    url = resolveMisskeyEmoji(emoji.key, accountKey.host)
                )
            }.toPersistentList()
        ),
        accountKey = accountKey
    )
}

private fun parseContent(note: Note, host: String): Element {
    return misskeyParser.parse(note.text.orEmpty()).toHtml(host) as? Element ?: Element("body")
}

private fun DriveFile.toUi(): UiMedia? {
    if (type.startsWith("image/")) {
        return UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = thumbnailUrl.orEmpty(),
            description = comment,
            aspectRatio = with(properties) {
                width?.toFloat()?.div(height?.toFloat() ?: 0f)?.takeUnless { it.isNaN() } ?: 1f
            }
        )
    } else if (type.startsWith("video/")) {
        return UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = thumbnailUrl.orEmpty(),
            description = comment,
            aspectRatio = with(properties) {
                width?.toFloat()?.div(height?.toFloat() ?: 0f)?.takeUnless { it.isNaN() } ?: 1f
            }
        )
    } else {
        return null
    }
}

internal fun UserLite.toUi(
    accountHost: String
): UiUser.Misskey {
    val remoteHost = if (host.isNullOrEmpty()) {
        accountHost
    } else {
        host
    }
    return UiUser.Misskey(
        userKey = MicroBlogKey(
            id = id,
            host = accountHost
        ),
        name = name.orEmpty(),
        avatarUrl = avatarUrl.orEmpty(),
        nameElement = parseName(this, accountHost),
        bannerUrl = null,
        description = null,
        descriptionElement = null,
        matrices = UiUser.Misskey.Matrices(
            fansCount = 0,
            followsCount = 0,
            statusesCount = 0
        ),
        handleInternal = username,
        remoteHost = remoteHost,
        isCat = isCat ?: false,
        isBot = isBot ?: false,
        relation = UiRelation.Misskey(
            following = false,
            isFans = false,
            blocking = false,
            blocked = false,
            muted = false,
            hasPendingFollowRequestFromYou = false,
            hasPendingFollowRequestToYou = false
        )
    )
}

private fun parseName(
    user: UserLite,
    accountHost: String
): Element {
    if (user.name.isNullOrEmpty()) {
        return Element("body")
    }
    return misskeyParser.parse(user.name).toHtml(accountHost) as? Element ?: Element("body")
}

internal fun User.toUi(
    accountHost: String
): UiUser.Misskey {
    val remoteHost = if (host.isNullOrEmpty()) {
        accountHost
    } else {
        host
    }
    return UiUser.Misskey(
        userKey = MicroBlogKey(
            id = id,
            host = accountHost
        ),
        name = name.orEmpty(),
        avatarUrl = avatarUrl.orEmpty(),
        nameElement = parseName(this, accountHost),
        bannerUrl = bannerUrl,
        description = description,
        descriptionElement = parseDescription(this, accountHost),
        matrices = UiUser.Misskey.Matrices(
            fansCount = followersCount.toLong(),
            followsCount = followingCount.toLong(),
            statusesCount = notesCount.toLong()
        ),
        handleInternal = username,
        remoteHost = remoteHost,
        isCat = isCat ?: false,
        isBot = isBot ?: false,
        relation = UiRelation.Misskey(
            following = isFollowing ?: false,
            isFans = isFollowed ?: false,
            blocking = isBlocking ?: false,
            blocked = isBlocked ?: false,
            muted = isMuted ?: false,
            hasPendingFollowRequestFromYou = hasPendingFollowRequestFromYou ?: false,
            hasPendingFollowRequestToYou = hasPendingFollowRequestToYou ?: false
        )
    )
}

private fun parseDescription(
    user: User,
    accountHost: String
): Element? {
    if (user.description.isNullOrEmpty()) {
        return null
    }
    return misskeyParser.parse(user.description).toHtml(accountHost) as? Element
}

private fun Token.toElement(
    accountHost: String
): Node {
    return when (this) {
        is CashTagToken -> Element("a").apply {
            attr("href", AppDeepLink.Search(value))
            text(value)
        }

        is EmojiToken -> {
            Element("img").apply {
                attr("src", resolveMisskeyEmoji(value, accountHost))
            }
        }

        is HashTagToken -> Element("a").apply {
            attr("href", AppDeepLink.Search(value))
            text(value)
        }

        is StringToken -> TextNode(value)
        is UrlToken -> Element("a").apply {
            attr("href", value)
            text(value)
        }

        is UserNameToken -> Element("a").apply {
            val trimmed = value.trimStart('@')
            if (trimmed.contains('@')) {
                val (username, host) = trimmed.split('@')
                attr("href", ProfileWithUserNameAndHostRouteDestination(username, host).deeplink())
            } else {
                attr("href", ProfileWithUserNameAndHostRouteDestination(trimmed, accountHost).deeplink())
            }
            text(value)
        }
    }
}

private fun parseName(
    user: User,
    accountHost: String
): Element {
    if (user.name.isNullOrEmpty()) {
        return Element("body")
    }
    return misskeyParser.parse(user.name).toHtml(accountHost) as? Element ?: Element("body")
}

private fun moe.tlaster.mfm.parser.tree.Node.toHtml(
    accountHost: String
): Node {
    return when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is CodeBlockNode -> {
            Element("pre").apply {
                Element("code").apply {
                    language?.let { attr("lang", it) }
                    text(code)
                }
            }
        }
        is MathBlockNode -> {
            Element("pre").apply {
                Element("code").apply {
                    attr("lang", "math")
                    text(formula)
                }
            }
        }
        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is SearchNode -> {
            Element("search").apply {
                text(query)
            }
        }
        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is FnNode -> {
            Element("fn").apply {
                attr("name", name)
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    appendChild(it.toHtml(accountHost))
                }
            }
        }
        is CashNode -> {
            Element("a").apply {
                attr("href", AppDeepLink.Search("$$content"))
                text("$$content")
            }
        }
        is EmojiCodeNode -> {
            Element("img").apply {
                attr("src", resolveMisskeyEmoji(emoji, accountHost))
            }
        }
        is HashtagNode -> {
            Element("a").apply {
                attr("href", AppDeepLink.Search("#$tag"))
                text("#$tag")
            }
        }
        is InlineCodeNode -> {
            Element("code").apply {
                text(code)
            }
        }
        is LinkNode -> {
            Element("a").apply {
                attr("href", url)
                text(content)
            }
        }
        is MathInlineNode -> {
            Element("code").apply {
                attr("lang", "math")
                text(formula)
            }
        }
        is MentionNode -> {
            Element("a").apply {
                val deeplink = host?.let {
                    ProfileWithUserNameAndHostRouteDestination(userName, it).deeplink()
                } ?: ProfileWithUserNameAndHostRouteDestination(userName, accountHost).deeplink()
                attr("href", deeplink)
                text(
                    buildString {
                        append("@")
                        append(userName)
                        if (host != null) {
                            append("@")
                            append(host)
                        }
                    }
                )
            }
        }
        is moe.tlaster.mfm.parser.tree.TextNode -> TextNode(content)
        is UrlNode -> {
            Element("a").apply {
                attr("href", url)
                text(url)
            }
        }
    }
}

internal fun EmojiSimple.toUi(): UiEmoji {
    return UiEmoji(
        shortcode = name,
        url = url
    )
}

internal fun resolveMisskeyEmoji(name: String, accountHost: String): String {
    return name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }
}
