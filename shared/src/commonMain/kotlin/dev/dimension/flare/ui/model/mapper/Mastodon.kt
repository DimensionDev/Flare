package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
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
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.Instant
import kotlin.collections.orEmpty

internal fun Notification.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Mastodon,
    references: Map<ReferenceType, StatusContent>,
): UiTimeline {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey, host = accountKey.host)
    val status =
        (references[ReferenceType.Notification] as? StatusContent.Mastodon)
            ?.data
            ?.renderStatus(
                host = accountKey.host,
                accountKey = accountKey,
                dataSource = event,
            )
    val topMessageType =
        when (type) {
            NotificationTypes.Follow ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Follow(id = id.orEmpty())
            NotificationTypes.Favourite ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Favourite(id = id.orEmpty())
            NotificationTypes.Reblog ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Reblogged(id = id.orEmpty())
            NotificationTypes.Mention ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Mention(id = id.orEmpty())
            NotificationTypes.Poll ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Poll(id = id.orEmpty())
            NotificationTypes.FollowRequest ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .FollowRequest(id = id.orEmpty())
            NotificationTypes.Status ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Status(id = id.orEmpty())
            NotificationTypes.Update ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .Update(id = id.orEmpty())
            null ->
                UiTimeline.TopMessage.MessageType.Mastodon
                    .UnKnown(id = id.orEmpty())
        }
    val topMessage =
        topMessageType.let {
            UiTimeline.TopMessage(
                user = user,
                icon =
                    when (type) {
                        NotificationTypes.Follow -> UiTimeline.TopMessage.Icon.Follow
                        NotificationTypes.Favourite -> UiTimeline.TopMessage.Icon.Favourite
                        NotificationTypes.Reblog -> UiTimeline.TopMessage.Icon.Retweet
                        NotificationTypes.Mention -> UiTimeline.TopMessage.Icon.Mention
                        NotificationTypes.Poll -> UiTimeline.TopMessage.Icon.Poll
                        NotificationTypes.FollowRequest -> UiTimeline.TopMessage.Icon.Follow
                        NotificationTypes.Status -> UiTimeline.TopMessage.Icon.Edit
                        NotificationTypes.Update -> UiTimeline.TopMessage.Icon.Edit
                        null -> UiTimeline.TopMessage.Icon.Info
                    },
                type = it,
                onClicked = {
                    launcher.launch(
                        AppDeepLink.Profile(
                            accountKey = accountKey,
                            userKey = user.key,
                        ),
                    )
                },
                statusKey = MicroBlogKey(id ?: "", accountKey.host),
            )
        }
    return UiTimeline(
        topMessage = topMessage,
        content =
            when {
                type in listOf(NotificationTypes.Follow, NotificationTypes.FollowRequest) ->
                    UiTimeline.ItemContent.User(user)

                else -> status ?: UiTimeline.ItemContent.User(user)
            },
    )
}

internal fun Status.renderGuest(host: String) =
    render(
        host = host,
        accountKey = null,
        event = null,
        references =
            reblog
                ?.let { reblog ->
                    mapOf(
                        ReferenceType.Retweet to StatusContent.Mastodon(reblog),
                    )
                }.orEmpty(),
    )

internal fun Status.render(
    host: String,
    accountKey: MicroBlogKey?,
    event: StatusEvent.Mastodon?,
    references: Map<ReferenceType, StatusContent> = mapOf(),
): UiTimeline {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey, host)
    val currentStatus = this.renderStatus(host, accountKey, event)
    val actualStatus = (references[ReferenceType.Retweet] as? StatusContent.Mastodon)?.data ?: this
    val topMessage =
        if (reblog == null) {
            null
        } else {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Retweet,
                type =
                    UiTimeline.TopMessage.MessageType.Mastodon
                        .Reblogged(id = id.orEmpty()),
                onClicked = {
                    launcher.launch(
                        AppDeepLink.Profile(
                            accountKey = accountKey,
                            userKey = user.key,
                        ),
                    )
                },
                statusKey = currentStatus.statusKey,
            )
        }
    return UiTimeline(
        topMessage = topMessage,
        content = actualStatus.renderStatus(host, accountKey, event),
    )
}

private fun Status.renderStatus(
    host: String,
    accountKey: MicroBlogKey?,
    dataSource: StatusEvent.Mastodon?,
): UiTimeline.ItemContent.Status {
    requireNotNull(account) { "actualStatus.account is null" }
    val actualUser = account.render(accountKey, host)
    val isFromMe = actualUser.key == accountKey
    val canReblog = visibility in listOf(Visibility.Public, Visibility.Unlisted) || (isFromMe && visibility != Visibility.Direct)
    val statusKey =
        MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Status.id should not be null"),
            host = actualUser.key.host,
        )
    val renderedVisibility =
        when (visibility) {
            Visibility.Public -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public
            Visibility.Unlisted -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home
            Visibility.Private -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers
            Visibility.Direct -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified
            null -> null
        }
    return UiTimeline.ItemContent.Status(
        images =
            mediaAttachments
                ?.mapNotNull { attachment ->
                    attachment.toUi(sensitive = sensitive ?: false)
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning =
            spoilerText?.let {
                Element("span")
                    .apply {
                        appendText(it)
                    }.toUi()
            },
        user = actualUser,
        quote = persistentListOf(),
        content = parseMastodonContent(this, accountKey, host).toUi(),
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
            if (dataSource != null && accountKey != null) {
                listOfNotNull(
                    StatusAction.Item.Reply(
                        count = repliesCount ?: 0,
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
                                                AppDeepLink.Mastodon.ReportStatus(
                                                    accountKey = accountKey,
                                                    statusKey = statusKey,
                                                    userKey = actualUser.key,
                                                ),
                                            )
                                        },
                                    )
                                },
                            ).toImmutableList(),
                    ),
                )
            } else {
                emptyList()
            }.toImmutableList(),
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
                    onVote = { options ->
                        if (it.id != null && dataSource != null) {
                            dataSource.vote(statusKey, it.id, options)
                        }
                    },
                    enabled = dataSource != null && !isFromMe,
                )
            },
        statusKey = statusKey,
        createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
        topEndContent =
            renderedVisibility?.let {
                UiTimeline.ItemContent.Status.TopEndContent
                    .Visibility(it)
            },
        sensitive = sensitive ?: false,
        onClicked = {
            launcher.launch(
                AppDeepLink.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
        platformType = PlatformType.Mastodon,
        onMediaClicked = { media, index ->
            launcher.launch(
                AppDeepLink.StatusMedia(
                    accountKey = accountKey,
                    statusKey = statusKey,
                    mediaIndex = index,
                    preview =
                        when (media) {
                            is UiMedia.Image -> media.previewUrl
                            is UiMedia.Video -> media.thumbnailUrl
                            is UiMedia.Audio -> null
                            is UiMedia.Gif -> media.previewUrl
                        },
                ),
            )
        },
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

internal fun Account.render(
    accountKey: MicroBlogKey?,
    host: String,
): UiProfile {
    val remoteHost =
        if (acct != null && acct.contains('@')) {
            acct.substring(acct.indexOf('@') + 1)
        } else {
            host
        }
    val userKey =
        MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
            host = host,
        )
    return UiProfile(
        avatar = avatar.orEmpty(),
        name = parseName(this).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = header,
        description = parseNote(this).toUi(),
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount ?: 0,
                followsCount = followingCount ?: 0,
                statusesCount = statusesCount ?: 0,
            ),
        mark =
            listOfNotNull(
                if (locked == true) {
                    UiProfile.Mark.Locked
                } else {
                    null
                },
                if (bot == true) {
                    UiProfile.Mark.Bot
                } else {
                    null
                },
            ).toImmutableList(),
        bottomContent =
            fields
                ?.takeIf {
                    it.any()
                }?.let {
                    UiProfile.BottomContent.Fields(
                        fields =
                            it
                                .mapNotNull { (name, value) ->
                                    name?.let {
                                        value?.let {
                                            name to parseHtml(value).toUi()
                                        }
                                    }
                                }.toMap()
                                .toImmutableMap(),
                    )
                },
        platformType = PlatformType.Mastodon,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

private fun parseNote(account: Account): Element {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return parseHtml(content)
}

internal fun RelationshipResponse.toUi(): UiRelation =
    UiRelation(
        following = following ?: false,
        isFans = followedBy ?: false,
        blocking = blocking ?: false,
        muted = muting ?: false,
        hasPendingFollowRequestFromYou = requested ?: false,
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
    return parseHtml(content) as? Element ?: Element("body")
}

internal fun parseMastodonContent(
    status: Status,
//    text: String,
    accountKey: MicroBlogKey?,
    host: String,
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
    val body = parseHtml(content)
    body.childNodes().forEach {
        replaceMentionAndHashtag(mentions, it, accountKey, host)
    }
    return body
}

private fun replaceMentionAndHashtag(
    mentions: List<Mention>,
    node: Node,
    accountKey: MicroBlogKey?,
    host: String,
) {
    if (node is Element) {
        val href = node.attribute("href")?.value
        val c = node.attribute("class")?.value
        val mention = mentions.firstOrNull { it.url == href }
        if (mention != null) {
            val id = mention.id
            if (id != null) {
                node.attributes().put(
                    "href",
                    AppDeepLink.Profile(
                        accountKey = accountKey,
                        userKey = MicroBlogKey(id, host),
                    ),
                )
            }
        } else if (node.text().startsWith("#")) {
            node.attributes().put("href", AppDeepLink.Search(accountKey, node.text()))
        } else if (!href.isNullOrEmpty() && c != null && c.contains("mention")) {
            val url = Url(href)
            val host = url.host
            val name = url.segments.getOrNull(1)?.removePrefix("@")
            if (!name.isNullOrEmpty() && host.isNotEmpty()) {
                node.attributes().put(
                    "href",
                    AppDeepLink.ProfileWithNameAndHost(
                        accountKey = accountKey,
                        userName = name,
                        host = host,
                    ),
                )
            }
        }
        node.childNodes().forEach { replaceMentionAndHashtag(mentions, it, accountKey, host) }
    }
}
