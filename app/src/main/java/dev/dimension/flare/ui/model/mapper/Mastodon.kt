package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.screen.destinations.ProfileRouteDestination
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

internal fun Notification.toUi(
    accountKey: MicroBlogKey,
): UiStatus {
    requireNotNull(account) { "account is null" }
    val user = account.toUi(accountKey.host)
    return UiStatus.MastodonNotification(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = user.userKey.host,
        ),
        user = user,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        status = status?.toUi(accountKey),
        type = type
            ?: throw IllegalArgumentException("mastodon Notification.type should not be null"),
        accountKey = accountKey,
    )
}

internal fun Status.toUi(
    accountKey: MicroBlogKey,
): UiStatus.Mastodon {
    requireNotNull(account) { "account is null" }
    val user = account.toUi(accountKey.host)
    return UiStatus.Mastodon(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = user.userKey.host,
        ),
        sensitive = sensitive ?: false,
        poll = poll?.let {
            UiStatus.Mastodon.Poll(
                id = poll.id ?: "",
                options = poll.options?.map { option ->
                    UiStatus.Mastodon.PollOption(
                        title = option.title.orEmpty(),
                        votesCount = option.votesCount ?: 0,
                        percentage = option.votesCount?.toFloat()?.div(
                            if (poll.multiple == true) {
                                poll.votersCount ?: 1
                            } else {
                                poll.votesCount
                                    ?: 1
                            },
                        )?.takeUnless { it.isNaN() } ?: 0f,
                    )
                }?.toPersistentList() ?: persistentListOf(),
                expiresAt = poll.expiresAt ?: Instant.DISTANT_PAST,
                expired = poll.expired ?: false,
                multiple = poll.multiple ?: false,
                voted = poll.voted ?: false,
                ownVotes = poll.ownVotes?.toPersistentList() ?: persistentListOf(),
            )
        },
        card = card?.url?.let { url ->
            UiCard(
                url = url,
                title = card.title.orEmpty(),
                description = card.description?.takeIf { it.isNotEmpty() && it.isNotBlank() },
                media = card.image?.let {
                    UiMedia.Image(
                        url = card.image,
                        previewUrl = card.image,
                        description = card.description,
                        aspectRatio = card.width?.toFloat()?.div(card.height ?: 1)
                            ?.coerceAtLeast(0f)
                            ?.takeUnless { it.isNaN() } ?: 1f,
                    )
                },
            )
        },
        createdAt = createdAt
            ?: throw IllegalArgumentException("mastodon Status.createdAt should not be null"),
        content = content.orEmpty(),
        contentToken = parseContent(this, user.userKey.host),
        contentWarningText = spoilerText,
        user = user,
        matrices = UiStatus.Mastodon.Matrices(
            replyCount = repliesCount ?: 0,
            reblogCount = reblogsCount ?: 0,
            favouriteCount = favouritesCount ?: 0,
        ),
        reblogStatus = reblog?.toUi(accountKey),
        visibility = visibility?.let { visibility ->
            when (visibility) {
                Visibility.Public -> UiStatus.Mastodon.Visibility.Public
                Visibility.Unlisted -> UiStatus.Mastodon.Visibility.Unlisted
                Visibility.Private -> UiStatus.Mastodon.Visibility.Private
                Visibility.Direct -> UiStatus.Mastodon.Visibility.Direct
            }
        } ?: UiStatus.Mastodon.Visibility.Public,
        media = mediaAttachments?.mapNotNull { attachment ->
            attachment.toUi()
        }?.toPersistentList() ?: persistentListOf(),
        reaction = UiStatus.Mastodon.Reaction(
            liked = favourited ?: false,
            reblogged = reblogged ?: false,
            bookmarked = bookmarked ?: false,
        ),
        accountKey = accountKey,
    )
}

private fun parseContent(
    status: Status,
    host: String,
): Element {
    val emoji = status.emojis.orEmpty()
    val mentions = status.mentions.orEmpty()
//    val tags = status.tags.orEmpty()
    var content = status.content.orEmpty()
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<emoji target=\"${it.url}\">:${it.shortcode}:</emoji>",
        )
    }
    val body = Jsoup.parse(content).body()
    body.childNodes().forEach {
        replaceMentionAndHashtag(mentions, it, host)
    }
    return body
}

private fun replaceMentionAndHashtag(
    mentions: List<Mention>,
    node: Node,
    host: String,
) {
    if (mentions.any { it.url == node.attr("href") }) {
        val mention = mentions.firstOrNull { it.url == node.attr("href") }
        val id = mention?.id
        if (id != null) {
            node.attr(
                "href",
                ProfileRouteDestination(userKey = MicroBlogKey(id, host)).deeplink(),
            )
        }
    } else if (node is Element && node.normalName() == "a" && node.hasText() && node.text()
            .startsWith('#')
    ) {
        node.attr(
            "href",
            AppDeepLink.Search(node.text().trimStart('#')),
        )
    } else if (node.hasAttr("class") && node.attr("class") == "invisible") {
        node.remove()
    } else {
        node.childNodes().forEach { replaceMentionAndHashtag(mentions, it, host) }
    }
}

private fun Attachment.toUi(): UiMedia? {
    return when (type) {
        MediaType.Image -> UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.GifV -> UiMedia.Gif(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.Video -> UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.Audio -> UiMedia.Audio(
            url = url.orEmpty(),
            description = description,
        )

        else -> null
    }
}

internal fun Account.toUi(
    host: String,
): UiUser.Mastodon {
    val remoteHost = if (acct != null && acct.contains('@')) {
        acct.substring(acct.indexOf('@') + 1)
    } else {
        host
    }
    return UiUser.Mastodon(
        userKey = MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
            host = host,
        ),
        name = displayName.orEmpty(),
        avatarUrl = avatar.orEmpty(),
        nameElement = parseContent(this),
        bannerUrl = header,
        description = note,
        descriptionElement = parseNote(this),
        matrices = UiUser.Mastodon.Matrices(
            fansCount = followersCount ?: 0,
            followsCount = followingCount ?: 0,
            statusesCount = statusesCount ?: 0,
        ),
        locked = locked ?: false,
        handleInternal = username.orEmpty(),
        remoteHost = remoteHost,
    )
}

private fun parseNote(account: Account): Element? {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<emoji target=\"${it.url}\">:${it.shortcode}:</emoji>",
        )
    }
    return Jsoup.parse(content).body()
}

private fun parseContent(status: Account): Element {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<emoji target=\"${it.url}\">:${it.shortcode}:</emoji>",
        )
    }
    return Jsoup.parse(content).body()
}

internal fun RelationshipResponse.toUi(): UiRelation.Mastodon {
    return UiRelation.Mastodon(
        following = following ?: false,
        isFans = followedBy ?: false,
        blocking = blocking ?: false,
        muting = muting ?: false,
        requested = requested ?: false,
        domainBlocking = domainBlocking ?: false,
    )
}

internal fun DbEmoji.toUi(): List<UiEmoji> {
    return when (content) {
        is EmojiContent.Mastodon -> {
            content.data.filter { it.visibleInPicker == true }.map {
                UiEmoji(
                    shortcode = it.shortcode.orEmpty(),
                    url = it.url.orEmpty(),
                )
            }
        }

        is EmojiContent.Misskey -> {
            content.data.map {
                UiEmoji(
                    shortcode = it.name,
                    url = it.url,
                )
            }
        }
    }
}
