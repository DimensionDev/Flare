package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

internal fun DbPagingTimelineWithStatus.toUi(): UiStatus {
    return when (val status = status.status.data.content) {
        is StatusContent.Mastodon -> status.data.toUi()
        is StatusContent.MastodonNotification -> status.data.toUi()
    }
}

private fun Notification.toUi(): UiStatus {
    requireNotNull(account) { "account is null" }
    val user = account.toUi()
    return UiStatus.MastodonNotification(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = user.userKey.host,
        ),
        user = user,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        status = status?.toUi(),
        type = type ?: throw IllegalArgumentException("mastodon Notification.type should not be null"),
    )
}

internal fun DbUser.toUi(): UiUser {
    return when (val user = content) {
        is UserContent.Mastodon -> user.data.toUi()
    }
}

internal fun Status.toUi(): UiStatus.Mastodon {
    requireNotNull(account) { "account is null" }
    val user = account.toUi()
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
                            if (poll.multiple == true) poll.votersCount ?: 1 else poll.votesCount
                                ?: 1
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
        contentToken = parseContent(this),
        contentWarningText = spoilerText,
        user = user,
        matrices = UiStatus.Mastodon.Matrices(
            replyCount = repliesCount ?: 0,
            reblogCount = reblogsCount ?: 0,
            favouriteCount = favouritesCount ?: 0,
        ),
        reblogStatus = reblog?.toUi(),
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
    )
}

private fun parseContent(status: Status): Element {
    val emoji = status.emojis.orEmpty()
    val mentions = status.mentions.orEmpty()
//    val tags = status.tags.orEmpty()
    var content = status.content.orEmpty()
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<emoji target=\"${it.url}\">:${it.shortcode}:</emoji>"
        )
    }
    val body = Jsoup.parse(content).body()
    body.childNodes().forEach {
        replaceMentionAndHashtag(mentions, it)
    }
    return body
}

private fun replaceMentionAndHashtag(mentions: List<Mention>, node: Node) {
    if (mentions.any { it.url == node.attr("href") }) {
        val mention = mentions.firstOrNull { it.url == node.attr("href") }
        val id = mention?.id
        val acct = mention?.acct
        val url = mention?.url
        if (id != null && acct != null) {
            val host = if (acct.contains("@")) {
                acct.substring(acct.indexOf("@") + 1)
            } else {
                requireNotNull(url) { "mastodon Account.url should not be null" }
                Url(url).host
            }
            node.attr(
                "href",
                AppDeepLink.User(MicroBlogKey(id, host))
            )
        }
    } else if (node is Element && node.normalName() == "a" && node.hasText() && node.text()
            .startsWith('#')
    ) {
        node.attr(
            "href",
            AppDeepLink.Mastodon.Hashtag(node.text().trimStart('#'))
        )
    } else if (node.hasAttr("class") && node.attr("class") == "invisible") {
        node.remove()
    } else {
        node.childNodes().forEach { replaceMentionAndHashtag(mentions, it) }
    }
}


private fun Attachment.toUi(): UiMedia? {
    return when (type) {
        MediaType.image -> UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.gifv -> UiMedia.Gif(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.video -> UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1)
                ?.coerceAtLeast(0f)
                ?.takeUnless { it.isNaN() } ?: 1f,
        )

        MediaType.audio -> UiMedia.Audio(
            url = url.orEmpty(),
            description = description,
        )

        else -> null
    }
}

private fun Account.toUi(): UiUser.Mastodon {
    requireNotNull(acct) { "mastodon Account.acct should not be null" }
    val host = if (acct.contains("@")) {
        acct.substring(acct.indexOf("@") + 1)
    } else {
        requireNotNull(url) { "mastodon Account.url should not be null" }
        Url(url).host
    }
    return UiUser.Mastodon(
        userKey = MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
            host = host,
        ),
        name = displayName.orEmpty(),
        handle = username.orEmpty(),
        avatarUrl = avatar.orEmpty(),
        nameElement = parseContent(this)
    )
}

fun parseContent(status: Account): Element {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<emoji target=\"${it.url}\">:${it.shortcode}:</emoji>"
        )
    }
    return Jsoup.parse(content).body()
}
