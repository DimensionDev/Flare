package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.cache.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.render.Render
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.datetime.Instant
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node

internal fun Notification.render(
    accountKey: MicroBlogKey,
    dataSource: MastodonDataSource,
): Render.Item {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey.host)
    val status = status?.renderStatus(accountKey, dataSource)
    val topMessageType =
        when (type) {
            NotificationTypes.Follow -> Render.TopMessage.MessageType.Mastodon.Follow
            NotificationTypes.Favourite -> Render.TopMessage.MessageType.Mastodon.Favourite
            NotificationTypes.Reblog -> Render.TopMessage.MessageType.Mastodon.Reblog
            NotificationTypes.Mention -> Render.TopMessage.MessageType.Mastodon.Mention
            NotificationTypes.Poll -> Render.TopMessage.MessageType.Mastodon.Poll
            NotificationTypes.FollowRequest -> Render.TopMessage.MessageType.Mastodon.FollowRequest
            NotificationTypes.Status -> Render.TopMessage.MessageType.Mastodon.Status
            NotificationTypes.Update -> Render.TopMessage.MessageType.Mastodon.Update
            null -> null
        }
    val topMessage =
        topMessageType?.let {
            Render.TopMessage(
                user = user,
                icon = null,
                type = it,
            )
        }
    return Render.Item(
        topMessage = topMessage,
        content =
            when {
                type in listOf(NotificationTypes.Follow, NotificationTypes.FollowRequest) ->
                    user

                else -> status ?: user
            },
    )
}

internal fun Status.render(
    accountKey: MicroBlogKey,
    dataSource: MastodonDataSource,
): Render.Item {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey.host)
    val topMessage =
        if (reblog == null) {
            null
        } else {
            Render.TopMessage(
                user = user,
                icon = Render.TopMessage.Icon.Retweet,
                type = Render.TopMessage.MessageType.Mastodon.Reblogged,
            )
        }
    val actualStatus = reblog ?: this
    return Render.Item(
        topMessage = topMessage,
        content = actualStatus.renderStatus(accountKey, dataSource),
    )
}

private fun Status.renderStatus(
    accountKey: MicroBlogKey,
    dataSource: MastodonDataSource,
): Render.ItemContent.Status {
    requireNotNull(account) { "actualStatus.account is null" }
    val actualUser = account.render(accountKey.host)
    val isFromMe = actualUser.key == accountKey
    val canReblog = visibility in listOf(Visibility.Public, Visibility.Unlisted)
    val statusKey =
        MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = actualUser.key.host,
        )
    return Render.ItemContent.Status(
        images =
            mediaAttachments
                ?.mapNotNull { attachment ->
                    attachment.toUi(sensitive = sensitive ?: false)
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning = spoilerText,
        user = actualUser,
        quote = persistentListOf(),
        content = parseContent(this, accountKey).toUi(),
        card =
            card?.url?.let { url ->
                UiCard(
                    url = url,
                    title = card.title.orEmpty(),
                    description = card.description?.takeIf { it.isNotEmpty() && it.isNotBlank() },
                    media =
                        card.image?.let {
                            UiMedia.Image(
                                url = card.image,
                                previewUrl = card.image,
                                description = card.description,
                                width = card.width?.toFloat() ?: 0f,
                                height = card.height?.toFloat() ?: 0f,
                                sensitive = false,
                            )
                        },
                )
            },
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = repliesCount ?: 0,
                ),
                if (canReblog) {
                    StatusAction.Item.Retweet(
                        count = reblogsCount ?: 0,
                        retweeted = reblogged ?: false,
                        onClicked = {
                            dataSource.reblog(statusKey, reblogged ?: false)
                        },
                    )
                } else {
                    null
                },
                StatusAction.Item.Like(
                    count = favouritesCount ?: 0,
                    liked = favourited ?: false,
                    onClicked = {
                        dataSource.like(statusKey, favourited ?: false)
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            StatusAction.Item.Bookmark(
                                count = 0,
                                bookmarked = bookmarked ?: false,
                                onClicked = {
                                    dataSource.bookmark(statusKey, bookmarked ?: false)
                                },
                            ),
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
                    id = it.id ?: "",
                    options =
                        it.options
                            ?.map { option ->
                                UiPoll.Option(
                                    title = option.title.orEmpty(),
                                    votesCount = option.votesCount ?: 0,
                                    percentage =
                                        option.votesCount
                                            ?.toFloat()
                                            ?.div(
                                                if (it.multiple == true) {
                                                    it.votersCount ?: 1
                                                } else {
                                                    it.votesCount ?: 1
                                                },
                                            )?.takeUnless { it.isNaN() } ?: 0f,
                                )
                            }?.toPersistentList() ?: persistentListOf(),
                    expiresAt = it.expiresAt ?: Instant.DISTANT_PAST,
                    multiple = it.multiple ?: false,
                    ownVotes = it.ownVotes?.toPersistentList() ?: persistentListOf(),
                )
            },
        statusKey = statusKey,
        createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
    )
}

internal fun Notification.toUi(accountKey: MicroBlogKey): UiStatus {
    requireNotNull(account) { "account is null" }
    val user = account.toUi(accountKey.host)
    return UiStatus.MastodonNotification(
        statusKey =
            MicroBlogKey(
                id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
                host = user.userKey.host,
            ),
        user = user,
        createdAt = createdAt ?: Instant.DISTANT_PAST,
        status = status?.toUi(accountKey),
        type =
            type
                ?: throw IllegalArgumentException("mastodon Notification.type should not be null"),
        accountKey = accountKey,
    )
}

internal fun Status.toUi(accountKey: MicroBlogKey): UiStatus.Mastodon {
    requireNotNull(account) { "account is null" }
    val user = account.toUi(accountKey.host)
    return UiStatus.Mastodon(
        statusKey =
            MicroBlogKey(
                id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
                host = user.userKey.host,
            ),
        sensitive = sensitive ?: false,
        poll =
            poll?.let {
                UiPoll(
                    id = poll.id ?: "",
                    options =
                        poll.options
                            ?.map { option ->
                                UiPoll.Option(
                                    title = option.title.orEmpty(),
                                    votesCount = option.votesCount ?: 0,
                                    percentage =
                                        option.votesCount
                                            ?.toFloat()
                                            ?.div(
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
                    multiple = poll.multiple ?: false,
                    ownVotes = poll.ownVotes?.toPersistentList() ?: persistentListOf(),
                )
            },
        card =
            card?.url?.let { url ->
                UiCard(
                    url = url,
                    title = card.title.orEmpty(),
                    description = card.description?.takeIf { it.isNotEmpty() && it.isNotBlank() },
                    media =
                        card.image?.let {
                            UiMedia.Image(
                                url = card.image,
                                previewUrl = card.image,
                                description = card.description,
                                width = card.width?.toFloat() ?: 0f,
                                height = card.height?.toFloat() ?: 0f,
                                sensitive = false,
                            )
                        },
                )
            },
        createdAt =
            createdAt
                ?: throw IllegalArgumentException("mastodon Status.createdAt should not be null"),
        content = content.orEmpty(),
        contentWarningText = spoilerText?.takeIf { it.isNotEmpty() },
        user = user,
        matrices =
            UiStatus.Mastodon.Matrices(
                replyCount = repliesCount ?: 0,
                reblogCount = reblogsCount ?: 0,
                favouriteCount = favouritesCount ?: 0,
            ),
        reblogStatus = reblog?.toUi(accountKey),
        visibility =
            visibility?.let { visibility ->
                when (visibility) {
                    Visibility.Public -> UiStatus.Mastodon.Visibility.Public
                    Visibility.Unlisted -> UiStatus.Mastodon.Visibility.Unlisted
                    Visibility.Private -> UiStatus.Mastodon.Visibility.Private
                    Visibility.Direct -> UiStatus.Mastodon.Visibility.Direct
                }
            } ?: UiStatus.Mastodon.Visibility.Public,
        medias =
            mediaAttachments
                ?.mapNotNull { attachment ->
                    attachment.toUi(sensitive = sensitive ?: false)
                }?.toPersistentList() ?: persistentListOf(),
        reaction =
            UiStatus.Mastodon.Reaction(
                liked = favourited ?: false,
                reblogged = reblogged ?: false,
                bookmarked = bookmarked ?: false,
            ),
        accountKey = accountKey,
        raw = this,
    )
}

private fun Attachment.toUi(sensitive: Boolean): UiMedia? =
    when (type) {
        MediaType.Image ->
            UiMedia.Image(
                url = url.orEmpty(),
                previewUrl = previewURL.orEmpty(),
                description = description,
                width = meta?.width?.toFloat() ?: meta?.original?.width?.toFloat() ?: 0f,
                height = meta?.height?.toFloat() ?: meta?.original?.height?.toFloat() ?: 0f,
                sensitive = sensitive,
            )

        MediaType.GifV ->
            UiMedia.Gif(
                url = url.orEmpty(),
                previewUrl = previewURL.orEmpty(),
                description = description,
                width = meta?.width?.toFloat() ?: meta?.original?.width?.toFloat() ?: 0f,
                height = meta?.height?.toFloat() ?: meta?.original?.height?.toFloat() ?: 0f,
            )

        MediaType.Video ->
            UiMedia.Video(
                url = url.orEmpty(),
                thumbnailUrl = previewURL.orEmpty(),
                description = description,
                width = meta?.width?.toFloat() ?: meta?.original?.width?.toFloat() ?: 0f,
                height = meta?.height?.toFloat() ?: meta?.original?.height?.toFloat() ?: 0f,
            )

        MediaType.Audio ->
            UiMedia.Audio(
                url = url.orEmpty(),
                description = description,
                previewUrl = previewURL,
            )

        else -> null
    }

internal fun Account.render(host: String): Render.ItemContent.User {
    val remoteHost =
        if (acct != null && acct.contains('@')) {
            acct.substring(acct.indexOf('@') + 1)
        } else {
            host
        }
    return Render.ItemContent.User(
        avatar = avatar.orEmpty(),
        name = parseName(this).toUi(),
        handle = "@$username@$remoteHost",
        key =
            MicroBlogKey(
                id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
                host = host,
            ),
    )
}

internal fun Account.toUi(host: String): UiUser.Mastodon {
    val remoteHost =
        if (acct != null && acct.contains('@')) {
            acct.substring(acct.indexOf('@') + 1)
        } else {
            host
        }
    return UiUser.Mastodon(
        userKey =
            MicroBlogKey(
                id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
                host = host,
            ),
        name = displayName.orEmpty(),
        avatarUrl = avatar.orEmpty(),
        bannerUrl = header,
        description = note,
        matrices =
            UiUser.Mastodon.Matrices(
                fansCount = followersCount ?: 0,
                followsCount = followingCount ?: 0,
                statusesCount = statusesCount ?: 0,
            ),
        locked = locked ?: false,
        handleInternal = username.orEmpty(),
        remoteHost = remoteHost,
        fields =
            fields
                ?.map {
                    it.name.orEmpty() to it.value.orEmpty()
                }?.filter { it.first.isNotEmpty() }
                ?.toMap()
                ?.toPersistentMap() ?: persistentMapOf(),
        raw = this,
    )
}

internal fun RelationshipResponse.toUi(): UiRelation.Mastodon =
    UiRelation.Mastodon(
        following = following ?: false,
        isFans = followedBy ?: false,
        blocking = blocking ?: false,
        muting = muting ?: false,
        requested = requested ?: false,
        domainBlocking = domainBlocking ?: false,
    )

internal fun DbEmoji.toUi(): List<UiEmoji> =
    when (content) {
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

private fun parseName(status: Account): Element {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return Ktml.parse(content) as? Element ?: Element("body")
}

private fun parseContent(
    status: Status,
//    text: String,
    accountKey: MicroBlogKey,
): Element {
    val emoji = status.emojis.orEmpty()
    val mentions = status.mentions.orEmpty()
    var content = status.content.orEmpty()
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    val body = Ktml.parse(content)
    body.children.forEach {
        replaceMentionAndHashtag(mentions, it, accountKey)
    }
    return body
}

private fun replaceMentionAndHashtag(
    mentions: List<Mention>,
    node: Node,
    accountKey: MicroBlogKey,
) {
    if (node is Element) {
        val href = node.attributes["href"]
        val mention = mentions.firstOrNull { it.url == href }
        if (mention != null) {
            val id = mention.id
            if (id != null) {
                node.attributes["href"] =
                    AppDeepLink.Profile(
                        accountKey,
                        userKey = MicroBlogKey(id, accountKey.host),
                    )
            }
        } else if (node.innerText.startsWith("#")) {
            node.attributes["href"] = AppDeepLink.Search(accountKey, node.innerText)
        }
        node.children.forEach { replaceMentionAndHashtag(mentions, it, accountKey) }
    }
}
