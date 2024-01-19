package dev.dimension.flare.ui.model.mapper

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
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

internal fun Notification.toUi(accountKey: MicroBlogKey): UiStatus.MisskeyNotification {
    val user = user?.toUi(accountKey.host)
    return UiStatus.MisskeyNotification(
        statusKey =
            MicroBlogKey(
                id,
                host = accountKey.host,
            ),
        user = user,
        createdAt = createdAt.toInstant(),
        note = note?.toUi(accountKey),
        type = type,
        accountKey = accountKey,
        achievement = achievement,
    )
}

internal fun Note.toUi(accountKey: MicroBlogKey): UiStatus.Misskey {
    val user = user.toUi(accountKey.host)
    return UiStatus.Misskey(
        statusKey =
            MicroBlogKey(
                id,
                host = user.userKey.host,
            ),
        sensitive = files?.any { it.isSensitive } ?: false,
        poll =
            poll?.let {
                UiPoll(
                    // misskey poll doesn't have id
                    id = "",
                    options =
                        poll.choices.map { option ->
                            UiPoll.Option(
                                title = option.text,
                                votesCount = option.votes.toLong(),
                                percentage =
                                    option.votes.toFloat().div(
                                        poll.choices.sumOf { it.votes }.toFloat(),
                                    ).takeUnless { it.isNaN() } ?: 0f,
                            )
                        }.toPersistentList(),
                    expiresAt = poll.expiresAt ?: Instant.DISTANT_PAST,
                    multiple = poll.multiple,
                    ownVotes = List(poll.choices.filter { it.isVoted }.size) { index -> index }.toPersistentList(),
                )
            },
        // TODO: parse card content lazily
        card = null,
        createdAt = createdAt.toInstant(),
        content = text.orEmpty(),
        contentWarningText = cw?.takeIf { it.isNotEmpty() },
        user = user,
        matrices =
            UiStatus.Misskey.Matrices(
                replyCount = repliesCount.toLong(),
                renoteCount = renoteCount.toLong(),
            ),
        renote =
            if (text.isNullOrEmpty()) {
                renote?.toUi(accountKey)
            } else {
                null
            },
        quote =
            if (text != null || !files.isNullOrEmpty() || cw != null) {
                renote?.toUi(accountKey)
            } else {
                null
            },
        visibility =
            when (visibility) {
                Visibility.Public -> UiStatus.Misskey.Visibility.Public
                Visibility.Home -> UiStatus.Misskey.Visibility.Home
                Visibility.Followers -> UiStatus.Misskey.Visibility.Followers
                Visibility.Specified -> UiStatus.Misskey.Visibility.Specified
            },
        media =
            files?.mapNotNull { file ->
                file.toUi()
            }?.toPersistentList() ?: persistentListOf(),
        reaction =
            UiStatus.Misskey.Reaction(
                myReaction = myReaction,
                emojiReactions =
                    reactions.map { emoji ->
                        UiStatus.Misskey.EmojiReaction(
                            name = emoji.key,
                            count = emoji.value,
                            url = resolveMisskeyEmoji(emoji.key, accountKey.host),
                        )
                    }.toPersistentList(),
            ),
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

internal fun UserLite.toUi(accountHost: String): UiUser.Misskey {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountHost
        } else {
            host
        }
    return UiUser.Misskey(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        name = name.orEmpty(),
        avatarUrl = avatarUrl.orEmpty(),
        bannerUrl = null,
        description = null,
        matrices =
            UiUser.Misskey.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        handleInternal = username,
        remoteHost = remoteHost,
        isCat = isCat ?: false,
        isBot = isBot ?: false,
        relation =
            UiRelation.Misskey(
                following = false,
                isFans = false,
                blocking = false,
                blocked = false,
                muted = false,
                hasPendingFollowRequestFromYou = false,
                hasPendingFollowRequestToYou = false,
            ),
        accountHost = accountHost,
        fields = persistentMapOf(),
    )
}

internal fun User.toUi(accountHost: String): UiUser.Misskey {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountHost
        } else {
            host
        }
    return UiUser.Misskey(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        name = name.orEmpty(),
        avatarUrl = avatarUrl.orEmpty(),
        bannerUrl = bannerUrl,
        description = description,
        matrices =
            UiUser.Misskey.Matrices(
                fansCount = followersCount.toLong(),
                followsCount = followingCount.toLong(),
                statusesCount = notesCount.toLong(),
            ),
        handleInternal = username,
        remoteHost = remoteHost,
        isCat = isCat ?: false,
        isBot = isBot ?: false,
        relation =
            UiRelation.Misskey(
                following = isFollowing ?: false,
                isFans = isFollowed ?: false,
                blocking = isBlocking ?: false,
                blocked = isBlocked ?: false,
                muted = isMuted ?: false,
                hasPendingFollowRequestFromYou = hasPendingFollowRequestFromYou ?: false,
                hasPendingFollowRequestToYou = hasPendingFollowRequestToYou ?: false,
            ),
        accountHost = accountHost,
        fields =
            fields.map {
                it.name to it.value
            }.filter { it.first.isNotEmpty() }.toMap().toPersistentMap(),
    )
}

internal fun EmojiSimple.toUi(): UiEmoji {
    return UiEmoji(
        shortcode = name,
        url = url,
    )
}

internal fun resolveMisskeyEmoji(
    name: String,
    accountHost: String,
): String {
    return name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }
}
