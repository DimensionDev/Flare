package dev.dimension.flare.ui.model.mapper

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
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.Render
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.datetime.Instant
import moe.tlaster.ktml.dom.Element
import moe.tlaster.mfm.parser.MFMParser

internal fun Notification.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): Render.Item {
    requireNotNull(user) { "account is null" }
    val user = user.render(accountKey)
    val status = note?.renderStatus(accountKey, event)
    val topMessageType =
        when (this.type) {
            NotificationType.Follow -> Render.TopMessage.MessageType.Misskey.Follow
            NotificationType.Mention -> Render.TopMessage.MessageType.Misskey.Mention
            NotificationType.Reply -> Render.TopMessage.MessageType.Misskey.Reply
            NotificationType.Renote -> Render.TopMessage.MessageType.Misskey.Renote
            NotificationType.Quote -> Render.TopMessage.MessageType.Misskey.Quote
            NotificationType.Reaction -> Render.TopMessage.MessageType.Misskey.Reaction
            NotificationType.PollEnded -> Render.TopMessage.MessageType.Misskey.PollEnded
            NotificationType.ReceiveFollowRequest -> Render.TopMessage.MessageType.Misskey.ReceiveFollowRequest
            NotificationType.FollowRequestAccepted -> Render.TopMessage.MessageType.Misskey.FollowRequestAccepted
            NotificationType.AchievementEarned -> Render.TopMessage.MessageType.Misskey.AchievementEarned
            NotificationType.App -> Render.TopMessage.MessageType.Misskey.App
        }
    val topMessage =
        Render.TopMessage(
            user = user,
            icon = Render.TopMessage.Icon.Retweet,
            type = topMessageType,
        )
    return Render.Item(
        topMessage = topMessage,
        content =
            when {
                type in
                    listOf(
                        NotificationType.Follow,
                        NotificationType.FollowRequestAccepted,
                        NotificationType.ReceiveFollowRequest,
                    )
                ->
                    user

                else ->
                    status ?: user
            },
    )
}

internal fun Note.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): Render.Item {
    requireNotNull(user) { "account is null" }
    val user = user.render(accountKey)
    val topMessage =
        if (renote == null || !text.isNullOrEmpty()) {
            null
        } else {
            Render.TopMessage(
                user = user,
                icon = Render.TopMessage.Icon.Retweet,
                type = Render.TopMessage.MessageType.Mastodon.Reblogged,
            )
        }
    val actualStatus = renote ?: this
    return Render.Item(
        topMessage = topMessage,
        content = actualStatus.renderStatus(accountKey, event),
    )
}

internal fun Note.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Misskey,
): Render.ItemContent.Status {
    val user = user.render(accountKey)
    val isFromMe = user.key == accountKey
    val canReblog = visibility in listOf(Visibility.Public, Visibility.Home)
    val renderedVisibility =
        when (visibility) {
            Visibility.Public -> Render.ItemContent.Status.TopEndContent.Visibility.Type.Public
            Visibility.Home -> Render.ItemContent.Status.TopEndContent.Visibility.Type.Home
            Visibility.Followers -> Render.ItemContent.Status.TopEndContent.Visibility.Type.Followers
            Visibility.Specified -> Render.ItemContent.Status.TopEndContent.Visibility.Type.Specified
        }
    val statusKey =
        MicroBlogKey(
            id,
            host = user.key.host,
        )
    val reaction =
        reactions
            .map { emoji ->
                Render.ItemContent.Status.BottomContent.Reaction.EmojiReaction(
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
    return Render.ItemContent.Status(
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
                                ),
                            ).toImmutableList(),
                    )
                } else {
                    null
                },
                StatusAction.Item.Reaction(
                    reacted = myReaction != null,
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
            Render.ItemContent.Status.TopEndContent
                .Visibility(renderedVisibility),
        bottomContent =
            Render.ItemContent.Status.BottomContent
                .Reaction(reaction, myReaction),
    )
}

internal fun Notification.toUi(accountKey: MicroBlogKey): UiStatus.MisskeyNotification {
    val user = user?.toUi(accountKey)
    return UiStatus.MisskeyNotification(
        statusKey =
            MicroBlogKey(
                id,
                host = accountKey.host,
            ),
        user = user,
        createdAt = Instant.parse(createdAt),
        note = note?.toUi(accountKey),
        type = type,
        accountKey = accountKey,
        achievement = achievement,
    )
}

internal fun Note.toUi(accountKey: MicroBlogKey): UiStatus.Misskey {
    val user = user.toUi(accountKey)
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
        // TODO: parse card content lazily
        card = null,
        createdAt = Instant.parse(createdAt),
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
        medias =
            files
                ?.mapNotNull { file ->
                    file.toUi()
                }?.toPersistentList() ?: persistentListOf(),
        reaction =
            UiStatus.Misskey.Reaction(
                myReaction = myReaction,
                emojiReactions =
                    reactions
                        .map { emoji ->
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

internal fun UserLite.render(accountKey: MicroBlogKey): Render.ItemContent.User {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    return Render.ItemContent.User(
        avatar = avatarUrl.orEmpty(),
        name = parseName(name.orEmpty(), accountKey).toUi(),
        handle = "@$username@$remoteHost",
        key =
            MicroBlogKey(
                id = id,
                host = accountKey.host,
            ),
    )
}

internal fun UserLite.toUi(accountKey: MicroBlogKey): UiUser.Misskey {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    return UiUser.Misskey(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountKey.host,
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
        accountKey = accountKey,
        fields = persistentMapOf(),
    )
}

internal fun User.render(accountKey: MicroBlogKey): Render.ItemContent.User {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    return Render.ItemContent.User(
        avatar = avatarUrl.orEmpty(),
        name = parseName(name.orEmpty(), accountKey).toUi(),
        handle = "@$username@$remoteHost",
        key =
            MicroBlogKey(
                id = id,
                host = accountKey.host,
            ),
    )
}

internal fun User.toUi(accountKey: MicroBlogKey): UiUser.Misskey {
    val remoteHost =
        if (host.isNullOrEmpty()) {
            accountKey.host
        } else {
            host
        }
    return UiUser.Misskey(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountKey.host,
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
        accountKey = accountKey,
        fields =
            fields
                .map {
                    it.name to it.value
                }.filter { it.first.isNotEmpty() }
                .toMap()
                .toPersistentMap(),
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
