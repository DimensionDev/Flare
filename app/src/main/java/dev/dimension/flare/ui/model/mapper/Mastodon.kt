package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
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

fun DbPagingTimelineWithStatus.toUi(): UiStatus {
    return when (val status = status.status.data.content) {
        is StatusContent.Mastodon -> status.data.toUi()
    }
}

fun Status.toUi(): UiStatus.Mastodon {
    requireNotNull(account) { "account is null" }
    val user = account.toUi()
    return UiStatus.Mastodon(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = user.userKey.host,
        ),
        sensitive = sensitive ?: false,
        poll = UiStatus.Mastodon.Poll(
            id = poll?.id ?: "",
            options = poll?.options?.map { option ->
                UiStatus.Mastodon.PollOption(
                    title = option.title.orEmpty(),
                    votesCount = option.votesCount ?: 0,
                    percentage = option.votesCount?.toFloat()?.div(poll.votesCount ?: 1) ?: 0f,
                )
            }?.toPersistentList() ?: persistentListOf(),
            expiresAt = poll?.expiresAt ?: Instant.DISTANT_PAST,
            expired = poll?.expired ?: false,
            multiple = poll?.multiple ?: false,
            voted = poll?.voted ?: false,
            ownVotes = poll?.ownVotes?.toPersistentList() ?: persistentListOf(),
        ),
        card = card?.url?.let { url ->
            UiCard(
                url = url,
                title = card.title.orEmpty(),
                description = card.description?.takeIf { it.isNotEmpty() && it.isNotBlank() },
                media = UiMedia.Image(
                    url = card.image.orEmpty(),
                    previewUrl = card.image.orEmpty(),
                    description = card.description,
                    aspectRatio = card.width?.toFloat()?.div(card.height ?: 1) ?: 1f,
                ),
            )
        },
        createdAt = createdAt
            ?: throw IllegalArgumentException("mastodon Status.createdAt should not be null"),
        content = content.orEmpty(),
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

private fun Attachment.toUi(): UiMedia? {
    return when (type) {
        MediaType.image -> UiMedia.Image(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1) ?: 1f,
        )

        MediaType.gifv -> UiMedia.Gif(
            url = url.orEmpty(),
            previewUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1) ?: 1f,
        )

        MediaType.video -> UiMedia.Video(
            url = url.orEmpty(),
            thumbnailUrl = previewURL.orEmpty(),
            description = description,
            aspectRatio = meta?.width?.toFloat()?.div(meta.height ?: 1) ?: 1f,
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
    )
}
