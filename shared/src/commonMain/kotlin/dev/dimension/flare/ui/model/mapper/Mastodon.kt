package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.cache.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant

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
        bannerUrl = header,
        description = note,
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
