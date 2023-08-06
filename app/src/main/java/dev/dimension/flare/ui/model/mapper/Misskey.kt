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
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.screen.destinations.MisskeyProfileRouteDestination
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val misskeyParser by lazy {
    TwitterParser(enableAcct = true, enableEmoji = true, enableDotInUserName = true)
}

internal fun Notification.toUi(
    accountKey: MicroBlogKey,
    emojis: List<EmojiSimple>
): UiStatus.MisskeyNotification {
    val user = user?.toUi(accountKey.host, emojis)
    return UiStatus.MisskeyNotification(
        statusKey = MicroBlogKey(
            id,
            host = accountKey.host
        ),
        user = user,
        createdAt = createdAt.toInstant(),
        note = note?.toUi(accountKey, emojis),
        type = type,
        accountKey = accountKey
    )
}

internal fun Note.toUi(
    accountKey: MicroBlogKey,
    emojis: List<EmojiSimple>,
): UiStatus.Misskey {
    val user = user.toUi(accountKey.host, emojis)
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
                multiple = poll.multiple,
            )
        },
        // TODO: parse card content lazily
        card = null,
        createdAt = createdAt.toInstant(),
        content = text.orEmpty(),
        contentToken = parseContent(this, user.userKey.host, emojis),
        contentWarningText = cw,
        user = user,
        matrices = UiStatus.Misskey.Matrices(
            replyCount = repliesCount.toLong(),
            renoteCount = renoteCount.toLong(),
        ),
        renote = if (text == null && files?.isNotEmpty() == true && cw == null) {
            renote?.toUi(accountKey, emojis)
        } else {
            null
        },
        quote = if (text != null || !files.isNullOrEmpty() || cw != null) {
            renote?.toUi(accountKey, emojis)
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
            emojiReactions = reactionEmojis?.map { emoji ->
                UiStatus.Misskey.EmojiReaction(
                    name = emoji.key,
                    count = emoji.value,
                    url = emojis.find { it.name == emoji.key }?.url.orEmpty()
                )
            }.orEmpty().toPersistentList()
        ),
        accountKey = accountKey
    )
}

private fun parseContent(note: Note, host: String, emojis: List<EmojiSimple>): Element {
    val token = misskeyParser.parse(note.text.orEmpty())
    val element = Element("body")
    token.forEach {
        element.appendChild(it.toElement(host, emojis))
    }
    return element
}

private fun DriveFile.toUi(): UiMedia? {
    if (type.startsWith("image/")) {
        return UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = thumbnailUrl.orEmpty(),
            description = comment,
            aspectRatio = with(properties) {
                width?.toFloat()?.div(height?.toFloat() ?: 0f)?.takeUnless { it.isNaN() } ?: 1f
            },
        )
    } else if (type.startsWith("video/")) {
        return UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = thumbnailUrl.orEmpty(),
            description = comment,
            aspectRatio = with(properties) {
                width?.toFloat()?.div(height?.toFloat() ?: 0f)?.takeUnless { it.isNaN() } ?: 1f
            },
        )
    } else {
        return null
    }
}


internal fun UserLite.toUi(
    accountHost: String,
    emojis: List<EmojiSimple>,
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
        nameElement = parseName(this, accountHost, emojis),
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
    )
}

private fun parseName(
    user: UserLite,
    accountHost: String,
    emojis: List<EmojiSimple>,
): Element {
    if (user.name.isNullOrEmpty()) {
        return Element("body")
    }
    val token = misskeyParser.parse(user.name)
    val element = Element("body")
    token.forEach {
        element.appendChild(it.toElement(accountHost, emojis))
    }
    return element
}

internal fun User.toUi(
    accountHost: String,
    emojis: List<EmojiSimple>,
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
        nameElement = parseName(this, accountHost, emojis),
        bannerUrl = bannerUrl,
        description = description,
        descriptionElement = parseDescription(this, accountHost, emojis),
        matrices = UiUser.Misskey.Matrices(
            fansCount = followersCount.toLong(),
            followsCount = followingCount.toLong(),
            statusesCount = notesCount.toLong()
        ),
        handleInternal = username,
        remoteHost = remoteHost,
        isCat = isCat ?: false,
        isBot = isBot ?: false,
    )
}

private fun parseDescription(
    user: User,
    accountHost: String,
    emojis: List<EmojiSimple>,
): Element? {
    if (user.description.isNullOrEmpty()) {
        return null
    }
    val token = misskeyParser.parse(user.description)
    val element = Element("body")
    token.forEach {
        element.appendChild(it.toElement(accountHost, emojis))
    }
    return element
}

private fun Token.toElement(
    accountHost: String,
    emojis: List<EmojiSimple>,
): Node {
    return when (this) {
        is CashTagToken -> Element("a").apply {
            attr("href", AppDeepLink.Search(value))
            text(value)
        }

        is EmojiToken -> {
            if (value in emojis.map { it.name }) {
                Element("img").apply {
                    attr("src", emojis.first { it.name == value }.url)
                }
            } else {
                TextNode(value)
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
                attr("href", MisskeyProfileRouteDestination(username, host).deeplink())
            } else {
                attr("href", MisskeyProfileRouteDestination(value, accountHost).deeplink())
            }
            text(value)
        }
    }
}

private fun parseName(
    user: User,
    accountHost: String,
    emojis: List<EmojiSimple>,
): Element {
    if (user.name.isNullOrEmpty()) {
        return Element("body")
    }
    val token = misskeyParser.parse(user.name)
    val element = Element("body")
    token.forEach {
        element.appendChild(it.toElement(accountHost, emojis))
    }
    return element
}
