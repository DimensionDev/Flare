package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.MediaType
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.QuoteApproval
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.DeeplinkRoute.Companion.toUri
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.time.Instant

private val mastodonParser by lazy {
    TwitterParser(
        validMarkInUserName = listOf('@', '.'),
    )
}

internal fun Notification.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Mastodon,
    references: Map<ReferenceType, List<StatusContent>>,
): UiTimeline {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey, host = accountKey.host)
    val status =
        (references[ReferenceType.Notification]?.firstOrNull() as? StatusContent.Mastodon)
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
                        DeeplinkRoute.Profile
                            .User(
                                accountType = AccountType.Specific(accountKey),
                                userKey = user.key,
                            ).toUri(),
                    )
                },
                statusKey = MicroBlogKey(id ?: "", accountKey.host),
            )
        }
    return UiTimeline(
        topMessage = topMessage,
        content =
            when (type) {
                in listOf(NotificationTypes.Follow) -> UiTimeline.ItemContent.User(user)
                NotificationTypes.FollowRequest ->
                    UiTimeline.ItemContent.User(
                        user,
                        button =
                            persistentListOf(
                                UiTimeline.ItemContent.User.Button.AcceptFollowRequest(
                                    onClicked = {
                                        event.acceptFollowRequest(
                                            userKey = user.key,
                                            notificationStatusKey =
                                                MicroBlogKey(
                                                    id ?: "",
                                                    accountKey.host,
                                                ),
                                        )
                                    },
                                ),
                                UiTimeline.ItemContent.User.Button.RejectFollowRequest(
                                    onClicked = {
                                        event.rejectFollowRequest(
                                            userKey = user.key,
                                            notificationStatusKey =
                                                MicroBlogKey(
                                                    id ?: "",
                                                    accountKey.host,
                                                ),
                                        )
                                    },
                                ),
                            ),
                    )

                else -> status ?: UiTimeline.ItemContent.User(user)
            },
    )
}

internal fun Status.renderGuest(host: String) =
    render(
        host = host,
        event = null,
        references =
            reblog
                ?.let { reblog ->
                    mapOf(
                        ReferenceType.Retweet to listOfNotNull(StatusContent.Mastodon(reblog)),
                    )
                }.orEmpty(),
    )

internal fun Status.render(
    host: String,
    event: StatusEvent.Mastodon?,
    references: Map<ReferenceType, List<StatusContent>> = mapOf(),
): UiTimeline {
    val accountKey = event?.accountKey
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey, host)
    val currentStatus = this.renderStatus(host, accountKey, event)
    val actualStatus =
        (references[ReferenceType.Retweet]?.firstOrNull() as? StatusContent.Mastodon)?.data ?: this
    val topMessage =
        if (pinned == true) {
            UiTimeline.TopMessage(
                user = null,
                icon = UiTimeline.TopMessage.Icon.Pin,
                type =
                    UiTimeline.TopMessage.MessageType.Mastodon
                        .Pinned(id = id.orEmpty()),
                onClicked = { },
                statusKey = currentStatus.statusKey,
            )
        } else if (reblog == null) {
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
                        DeeplinkRoute.Profile
                            .User(
                                accountType =
                                    accountKey?.let { AccountType.Specific(it) }
                                        ?: AccountType.Guest,
                                userKey = user.key,
                            ).toUri(),
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
    val canReblog =
        visibility in
            listOf(
                Visibility.Public,
                Visibility.Unlisted,
            ) ||
            (isFromMe && visibility != Visibility.Direct)
    val canQuote =
        if (dataSource is StatusEvent.Pleroma) {
            canReblog
        } else if (quoteApproval != null && quoteApproval.currentUser != null) {
            when (quoteApproval.currentUser) {
                QuoteApproval.CurrentUser.Automatic -> {
                    if (!quoteApproval.automatic.isNullOrEmpty()) {
                        quoteApproval.automatic.contains(QuoteApproval.Approval.Public)
                    } else {
                        isFromMe
                    }
                }

                QuoteApproval.CurrentUser.Manual -> {
                    if (!quoteApproval.manual.isNullOrEmpty()) {
                        quoteApproval.manual.contains(QuoteApproval.Approval.Public)
                    } else {
                        isFromMe
                    }
                }

                QuoteApproval.CurrentUser.Denied -> false
                QuoteApproval.CurrentUser.Unknown -> false
            }
        } else {
            false
        }
//    val canReact = dataSource is StatusEvent.Pleroma
    // TODO: there are too many actions for Pleroma, disable for now
    val canReact = false
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
            Visibility.List -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers
            Visibility.Local -> UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home
            null -> null
        }
    val bottomContent =
        if (!emojiReactions.isNullOrEmpty()) {
            UiTimeline.ItemContent.Status.BottomContent.Reaction(
                emojiReactions
                    .map {
                        UiTimeline.ItemContent.Status.BottomContent.Reaction.EmojiReaction(
                            name = it.name.orEmpty(),
                            count = UiNumber(it.count ?: 0),
                            me = it.me ?: false,
                            url = it.url.orEmpty(),
                            isUnicode = it.url.isNullOrEmpty(),
                            onClicked = {
                                if (dataSource is StatusEvent.Pleroma) {
                                    dataSource.react(statusKey, true, it.name.orEmpty())
                                }
                            },
                        )
                    }.toImmutableList(),
            )
        } else {
            null
        }
    val postUrl =
        buildString {
            if (!url.isNullOrEmpty()) {
                append(url)
            } else if (!uri.isNullOrEmpty()) {
                append(uri)
            } else {
                append("https://$host/@${account.acct}/$id")
            }
        }
    val quoteStatus = quote?.renderStatus(host, accountKey, dataSource)
    return UiTimeline.ItemContent.Status(
        images =
            mediaAttachments
                ?.mapNotNull { attachment ->
                    attachment.toUi(sensitive = sensitive ?: false)
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning =
            spoilerText?.takeIf { it.isNotEmpty() && it.isNotBlank() }?.let {
                Element("span")
                    .apply {
                        appendText(it)
                    }.toUi()
            },
        user = actualUser,
        quote = listOfNotNull(quoteStatus).toImmutableList(),
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

            listOfNotNull(
                ActionMenu.Item(
                    icon = ActionMenu.Item.Icon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(repliesCount ?: 0),
                    onClicked = {
                        if (accountKey != null) {
                            launcher.launch(
                                DeeplinkRoute.Compose
                                    .Reply(
                                        accountKey = accountKey,
                                        statusKey = statusKey,
                                    ).toUri(),
                            )
                        }
                    },
                ),
                if (canReblog && quoteApproval != null && accountKey != null) {
                    ActionMenu.Group(
                        displayItem =
                            ActionMenu.Item(
                                icon = if (reblogged == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                                text =
                                    ActionMenu.Item.Text.Localized(
                                        if (reblogged ==
                                            true
                                        ) {
                                            ActionMenu.Item.Text.Localized.Type.Unretweet
                                        } else {
                                            ActionMenu.Item.Text.Localized.Type.Retweet
                                        },
                                    ),
                                count = UiNumber(reblogsCount ?: 0),
                                color = if (reblogged == true) ActionMenu.Item.Color.PrimaryColor else null,
                            ),
                        actions =
                            listOfNotNull(
                                if (canQuote) {
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Quote,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                        count = UiNumber(quotesCount ?: 0),
                                        onClicked = {
                                            launcher.launch(
                                                DeeplinkRoute.Compose
                                                    .Quote(
                                                        accountKey = accountKey,
                                                        statusKey = statusKey,
                                                    ).toUri(),
                                            )
                                        },
                                    )
                                } else {
                                    null
                                },
                                ActionMenu.Item(
                                    icon = if (reblogged == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                                    text =
                                        ActionMenu.Item.Text.Localized(
                                            if (reblogged ==
                                                true
                                            ) {
                                                ActionMenu.Item.Text.Localized.Type.Unretweet
                                            } else {
                                                ActionMenu.Item.Text.Localized.Type.Retweet
                                            },
                                        ),
                                    count = UiNumber(reblogsCount ?: 0),
                                    color = if (reblogged == true) ActionMenu.Item.Color.PrimaryColor else null,
                                    onClicked = {
                                        dataSource?.reblog(statusKey, reblogged ?: false)
                                    },
                                ),
                            ).toImmutableList(),
                    )
                } else {
                    ActionMenu.Item(
                        icon = if (reblogged == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                        text =
                            ActionMenu.Item.Text.Localized(
                                if (reblogged ==
                                    true
                                ) {
                                    ActionMenu.Item.Text.Localized.Type.Unretweet
                                } else {
                                    ActionMenu.Item.Text.Localized.Type.Retweet
                                },
                            ),
                        count = UiNumber(reblogsCount ?: 0),
                        color = if (reblogged == true) ActionMenu.Item.Color.PrimaryColor else null,
                    )
                },
                if (quoteApproval == null && canQuote) {
                    ActionMenu.Item(
                        icon = ActionMenu.Item.Icon.Quote,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                        count = UiNumber(quotesCount ?: 0),
                        onClicked = {
                            if (accountKey != null) {
                                launcher.launch(
                                    DeeplinkRoute.Compose
                                        .Quote(
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ).toUri(),
                                )
                            }
                        },
                    )
                } else {
                    null
                },
                if (quoteApproval == null && canReblog) {
                    ActionMenu.Item(
                        icon = if (reblogged == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                        text =
                            ActionMenu.Item.Text.Localized(
                                if (reblogged ==
                                    true
                                ) {
                                    ActionMenu.Item.Text.Localized.Type.Unretweet
                                } else {
                                    ActionMenu.Item.Text.Localized.Type.Retweet
                                },
                            ),
                        count = UiNumber(reblogsCount ?: 0),
                        color = if (reblogged == true) ActionMenu.Item.Color.PrimaryColor else null,
                        onClicked = {
                            dataSource?.reblog(statusKey, reblogged ?: false)
                        },
                    )
                } else {
                    null
                },
                ActionMenu.Item(
                    icon = if (favourited == true) ActionMenu.Item.Icon.Unlike else ActionMenu.Item.Icon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (favourited ==
                                true
                            ) {
                                ActionMenu.Item.Text.Localized.Type.Unlike
                            } else {
                                ActionMenu.Item.Text.Localized.Type.Like
                            },
                        ),
                    count = UiNumber(favouritesCount ?: 0),
                    color = if (favourited == true) ActionMenu.Item.Color.Red else null,
                    onClicked = {
                        dataSource?.like(statusKey, favourited ?: false)
                    },
                ),
                if (canReact) {
                    ActionMenu.Item(
                        icon = ActionMenu.Item.Icon.React,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.React),
                        onClicked = {
                            if (accountKey != null) {
                                launcher.launch(
                                    DeeplinkRoute.Status
                                        .AddReaction(
                                            statusKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                        ).toUri(),
                                )
                            }
                        },
                    )
                } else {
                    null
                },
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            if (accountKey != null) {
                                ActionMenu.Item(
                                    icon = if (bookmarked == true) ActionMenu.Item.Icon.Unbookmark else ActionMenu.Item.Icon.Bookmark,
                                    text =
                                        ActionMenu.Item.Text.Localized(
                                            if (bookmarked ==
                                                true
                                            ) {
                                                ActionMenu.Item.Text.Localized.Type.Unbookmark
                                            } else {
                                                ActionMenu.Item.Text.Localized.Type.Bookmark
                                            },
                                        ),
                                    count = UiNumber(0),
                                    onClicked = {
                                        dataSource?.bookmark(statusKey, bookmarked ?: false)
                                    },
                                )
                            } else {
                                null
                            },
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                shareContent = postUrl,
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        launcher.launch(
                                            DeeplinkRoute.Status
                                                .DeleteConfirm(
                                                    accountType = AccountType.Specific(accountKey!!),
                                                    statusKey = statusKey,
                                                ).toUri(),
                                        )
                                    },
                                )
                            } else {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Report,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                    color = ActionMenu.Item.Color.Red,
                                    onClicked = {
                                        if (accountKey != null) {
                                            launcher.launch(
                                                DeeplinkRoute.Status
                                                    .MastodonReport(
                                                        statusKey = statusKey,
                                                        userKey = actualUser.key,
                                                        accountType = AccountType.Specific(accountKey),
                                                    ).toUri(),
                                            )
                                        } else {
                                            launcher.launch(
                                                DeeplinkRoute.Status
                                                    .MastodonReport(
                                                        statusKey = statusKey,
                                                        userKey = actualUser.key,
                                                        accountType =
                                                            AccountType.Specific(
                                                                MicroBlogKey(
                                                                    "",
                                                                    "",
                                                                ),
                                                            ),
                                                    ).toUri(),
                                            )
                                        }
                                    },
                                )
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
                    expiresAt = it.expiresAt,
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
                DeeplinkRoute.Status
                    .Detail(
                        statusKey = statusKey,
                        accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest,
                    ).toUri(),
            )
        },
        platformType = PlatformType.Mastodon,
        onMediaClicked = { media, index ->
            launcher.launch(
                DeeplinkRoute.Media
                    .StatusMedia(
                        accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest,
                        statusKey = statusKey,
                        index = index,
                        preview =
                            when (media) {
                                is UiMedia.Image -> media.previewUrl
                                is UiMedia.Video -> media.thumbnailUrl
                                is UiMedia.Audio -> null
                                is UiMedia.Gif -> media.previewUrl
                            },
                    ).toUri(),
            )
        },
        bottomContent = bottomContent,
        url = postUrl,
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
            UiMedia.Video(
                url = url.orEmpty(),
                thumbnailUrl = previewURL.orEmpty(),
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

        else ->
            UiMedia.Image(
                url = url.orEmpty(),
                previewUrl = previewURL.orEmpty(),
                description = description,
                width = meta?.width?.toFloat() ?: meta?.original?.width?.toFloat() ?: 0f,
                height = meta?.height?.toFloat() ?: meta?.original?.height?.toFloat() ?: 0f,
                sensitive = sensitive,
            )
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
        nameInternal = parseName(this).toUi(),
        handle = "@$username@$remoteHost",
        key = userKey,
        banner = header,
        description =
            parseNote(
                this,
                accountKey = accountKey,
            ).toUi(),
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
            launcher.launch(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            accountKey?.let {
                                AccountType.Specific(
                                    it,
                                )
                            } ?: AccountType.Guest,
                        userKey = userKey,
                    ).toUri(),
            )
        },
    )
}

private fun parseNote(
    account: Account,
    accountKey: MicroBlogKey?,
): Element {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return parseHtml(content).let {
        updateHtmlTagToken(it, accountKey)
        it
    }
}

private fun updateHtmlTagToken(
    node: Node,
    accountKey: MicroBlogKey?,
) {
    if (node is Element && node.nameIs("a")) {
        val text = node.text()
        val token =
            runCatching {
                mastodonParser.parse(text)
            }.getOrDefault(emptyList()).firstOrNull()
        when (token) {
            is HashTagToken -> {
                node.attributes().put(
                    "href",
                    DeeplinkRoute
                        .Search(
                            accountType =
                                accountKey?.let { AccountType.Specific(it) }
                                    ?: AccountType.Guest,
                            query = "#${token.value.trim('#')}",
                        ).toUri(),
                )
            }

            is UserNameToken -> {
                val nodeHost = node.attribute("href")?.value?.let { Url(it).host } ?: ""
                val acct = token.value.removePrefix("@")
                val name = acct.substringBefore('@')
                val actualHost = acct.substringAfter('@', nodeHost)
                node.attributes().put(
                    "href",
                    DeeplinkRoute.Profile
                        .UserNameWithHost(
                            accountType =
                                accountKey?.let { AccountType.Specific(it) }
                                    ?: AccountType.Guest,
                            userName = name,
                            host = actualHost,
                        ).toUri(),
                )
            }

            else -> Unit
        }
    }
    node.childNodes().forEach {
        updateHtmlTagToken(
            it,
            accountKey = accountKey,
        )
    }
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
                val shortCode =
                    it.shortcode
                        .orEmpty()
                        .let { if (!it.startsWith(':') && !it.endsWith(':')) ":$it:" else it }
                UiEmoji(
                    shortcode = shortCode,
                    url = it.url.orEmpty(),
                    category = it.category.orEmpty(),
                    searchKeywords =
                        listOfNotNull(
                            it.shortcode,
                        ),
                    insertText = " $shortCode ",
                )
            }
        }

        is EmojiContent.Misskey -> {
            content.data.map {
                it.toUi()
            }
        }

        is EmojiContent.VVO ->
            content.data.data
                ?.emoticon
                ?.zhCN
                ?.flatMap { (category, items) ->
                    items.mapNotNull { item ->
                        if (item.phrase.isNullOrEmpty() || item.url.isNullOrEmpty()) {
                            return@mapNotNull null
                        }
                        UiEmoji(
                            shortcode = item.phrase,
                            url = item.url,
                            category = category,
                            searchKeywords =
                                listOfNotNull(
                                    item.phrase,
                                ),
                            insertText = item.phrase,
                        )
                    }
                }.orEmpty()

        is EmojiContent.FavIcon -> emptyList()
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
        if (node.classNames().contains("quote-inline")) {
            node.remove()
        } else {
            val href = node.attribute("href")?.value
            val c = node.attribute("class")?.value
            val mention = mentions.firstOrNull { it.url == href }
            if (mention != null) {
                val id = mention.id
                if (id != null) {
                    node.attributes().put(
                        "href",
                        DeeplinkRoute.Profile
                            .User(
                                accountType =
                                    accountKey?.let { AccountType.Specific(it) }
                                        ?: AccountType.Guest,
                                userKey = MicroBlogKey(id, host),
                            ).toUri(),
                    )
                }
            } else if (node.text().startsWith("#")) {
                node.attributes().put(
                    "href",
                    DeeplinkRoute
                        .Search(
                            accountKey?.let { AccountType.Specific(it) }
                                ?: AccountType.Guest,
                            node.text(),
                        ).toUri(),
                )
            } else if (!href.isNullOrEmpty() && c != null && c.contains("mention")) {
                val url = Url(href)
                val host = url.host
                val name = url.segments.getOrNull(1)?.removePrefix("@")
                if (!name.isNullOrEmpty() && host.isNotEmpty()) {
                    node.attributes().put(
                        "href",
                        DeeplinkRoute.Profile
                            .UserNameWithHost(
                                accountType =
                                    accountKey?.let { AccountType.Specific(it) }
                                        ?: AccountType.Guest,
                                userName = name,
                                host = host,
                            ).toUri(),
                    )
                }
            }
        }
        node.childNodes().forEach { replaceMentionAndHashtag(mentions, it, accountKey, host) }
    }
}

internal fun InstanceData.render(): UiInstanceMetadata {
    val configuration =
        UiInstanceMetadata.Configuration(
            registration =
                UiInstanceMetadata.Configuration.Registration(
                    enabled = this.registrations?.enabled == true,
                ),
            statuses =
                UiInstanceMetadata.Configuration.Statuses(
                    maxCharacters = this.configuration?.statuses?.maxCharacters ?: 500,
                    maxMediaAttachments = this.configuration?.statuses?.maxMediaAttachments ?: 4,
                ),
            mediaAttachment =
                UiInstanceMetadata.Configuration.MediaAttachment(
                    imageSizeLimit = this.configuration?.mediaAttachments?.imageSizeLimit ?: -1,
                    descriptionLimit =
                        this.configuration?.mediaAttachments?.descriptionLimit
                            ?: 1500,
                    supportedMimeTypes =
                        this.configuration
                            ?.mediaAttachments
                            ?.supportedMIMETypes
                            .orEmpty()
                            .toImmutableList(),
                ),
            poll =
                UiInstanceMetadata.Configuration.Poll(
                    maxOptions = this.configuration?.polls?.maxOptions ?: 4,
                    maxCharactersPerOption =
                        this.configuration?.polls?.maxCharactersPerOption
                            ?: 50,
                    minExpiration = this.configuration?.polls?.minExpiration ?: 300,
                    maxExpiration = this.configuration?.polls?.maxExpiration ?: 2592000,
                ),
        )

    val rules =
        this.rules
            .orEmpty()
            .associate { rule ->
                (rule.text ?: "") to (rule.hint ?: "")
            }.toImmutableMap()

    return UiInstanceMetadata(
        instance =
            UiInstance(
                name = this.title ?: "Unknown",
                description = this.description,
                iconUrl = this.icon?.lastOrNull()?.src,
                domain = this.domain ?: "Unknown",
                type = PlatformType.Mastodon,
                bannerUrl = this.thumbnail?.url,
                usersCount = this.usage?.users?.activeMonth ?: 0,
            ),
        rules = rules,
        configuration = configuration,
    )
}
