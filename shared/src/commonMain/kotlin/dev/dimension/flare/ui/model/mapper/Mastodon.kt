package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Attachment
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
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
import dev.dimension.flare.model.toAccountType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.parseHtml
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import io.ktor.http.Url
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UserNameToken

private val mastodonParser by lazy {
    TwitterParser(
        validMarkInUserName = listOf('@', '.'),
    )
}

internal fun Notification.render(accountKey: MicroBlogKey): UiTimelineV2 {
    requireNotNull(account) { "account is null" }
    val user = account.render(accountKey, host = accountKey.host)
    val messageType =
        if (type == null) {
            UiTimelineV2.Message.Type.Unknown(rawType = "")
        } else {
            when (type) {
                NotificationTypes.Follow ->
                    UiTimelineV2.Message.Type.Localized.MessageId.Follow

                NotificationTypes.Favourite ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .Favourite

                NotificationTypes.Reblog ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .Repost

                NotificationTypes.Mention ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .Mention

                NotificationTypes.Poll ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .PollEnded

                NotificationTypes.FollowRequest ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .FollowRequest

                NotificationTypes.Status ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .NewPost

                NotificationTypes.Update ->
                    UiTimelineV2.Message.Type.Localized.MessageId
                        .PostUpdated
            }.let {
                UiTimelineV2.Message.Type.Localized(it)
            }
        }
    val message =
        UiTimelineV2.Message(
            user = user,
            icon =
                when (type) {
                    NotificationTypes.Follow -> UiIcon.Follow
                    NotificationTypes.Favourite -> UiIcon.Favourite
                    NotificationTypes.Reblog -> UiIcon.Retweet
                    NotificationTypes.Mention -> UiIcon.Mention
                    NotificationTypes.Poll -> UiIcon.Poll
                    NotificationTypes.FollowRequest -> UiIcon.Follow
                    NotificationTypes.Status -> UiIcon.Edit
                    NotificationTypes.Update -> UiIcon.Edit
                    null -> UiIcon.Info
                },
            type = messageType,
            statusKey = MicroBlogKey(id ?: "", accountKey.host),
            createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile
                        .User(
                            accountType = accountKey.toAccountType(),
                            userKey = user.key,
                        ),
                ),
            accountType = accountKey.toAccountType(),
        )
    if (type in listOf(NotificationTypes.FollowRequest)) {
        return UiTimelineV2.User(
            value = user,
            message = message,
            button =
                persistentListOf(
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest),
                        clickEvent =
                            ClickEvent.event(
                                accountKey,
                                PostEvent.Mastodon.AcceptFollowRequest(
                                    userKey = user.key,
                                    postKey =
                                        MicroBlogKey(
                                            id ?: "",
                                            accountKey.host,
                                        ),
                                ),
                            ),
                        color = ActionMenu.Item.Color.PrimaryColor,
                        icon = UiIcon.Check,
                    ),
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.RejectFollowRequest),
                        clickEvent =
                            ClickEvent.event(
                                accountKey,
                                PostEvent.Mastodon.RejectFollowRequest(
                                    userKey = user.key,
                                    postKey =
                                        MicroBlogKey(
                                            id ?: "",
                                            accountKey.host,
                                        ),
                                ),
                            ),
                        color = ActionMenu.Item.Color.Red,
                    ),
                ),
            createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
            statusKey = MicroBlogKey(id ?: "", accountKey.host),
            accountType = accountKey.toAccountType(),
        )
    } else if (status != null) {
        return status
            .renderStatus(
                host = accountKey.host,
                accountKey = accountKey,
            ).copy(
                message = message,
            )
    } else {
        return UiTimelineV2.User(
            value = user,
            message = message,
            createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
            statusKey = MicroBlogKey(id ?: "", accountKey.host),
            accountType = accountKey.toAccountType(),
        )
    }
}

internal fun List<Status>.render(accountKey: MicroBlogKey) = this.map { it.render(host = accountKey.host, accountKey = accountKey) }

internal fun Status.render(
    host: String,
    accountKey: MicroBlogKey?,
): UiTimelineV2 {
    requireNotNull(account) { "account is null" }
    val currentStatus = this.renderStatus(host, accountKey)
    val topMessage =
        if (pinned == true) {
            UiTimelineV2.Message(
                user = null,
                icon = UiIcon.Pin,
                type =
                    UiTimelineV2.Message.Type.Localized(
                        UiTimelineV2.Message.Type.Localized.MessageId.Pinned,
                    ),
                statusKey = currentStatus.statusKey,
                createdAt = currentStatus.createdAt,
                clickEvent = ClickEvent.Noop,
                accountType = accountKey.toAccountType(),
            )
        } else if (reblog != null) {
            val userKey = currentStatus.user?.key
            UiTimelineV2.Message(
                user = currentStatus.user,
                icon = UiIcon.Retweet,
                type =
                    UiTimelineV2.Message.Type.Localized(
                        UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                    ),
                statusKey = currentStatus.statusKey,
                createdAt = currentStatus.createdAt,
                clickEvent =
                    if (userKey != null) {
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Profile
                                .User(
                                    accountType = accountKey.toAccountType(),
                                    userKey = userKey,
                                ),
                        )
                    } else {
                        ClickEvent.Noop
                    },
                accountType = accountKey.toAccountType(),
            )
        } else {
            null
        }
    return if (reblog != null) {
        currentStatus.copy(
            message = topMessage,
            internalRepost =
                reblog.renderStatus(
                    host = host,
                    accountKey = accountKey,
                ),
        )
    } else {
        currentStatus.copy(message = topMessage)
    }
}

private fun Status.renderStatus(
    host: String,
    accountKey: MicroBlogKey?,
): UiTimelineV2.Post {
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
        if (emojiReactions != null) { // assuming that is pleroma
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
            Visibility.Public -> UiTimelineV2.Post.Visibility.Public
            Visibility.Unlisted -> UiTimelineV2.Post.Visibility.Home
            Visibility.Private -> UiTimelineV2.Post.Visibility.Followers
            Visibility.Direct -> UiTimelineV2.Post.Visibility.Specified
            Visibility.List -> UiTimelineV2.Post.Visibility.Followers
            Visibility.Local -> UiTimelineV2.Post.Visibility.Home
            null -> null
        }
    val reactions =
        emojiReactions
            .orEmpty()
            .map {
                UiTimelineV2.Post.EmojiReaction(
                    name = it.name.orEmpty(),
                    count = UiNumber(it.count ?: 0),
                    me = it.me ?: false,
                    url = it.url.orEmpty(),
                    isUnicode = it.url.isNullOrEmpty(),
                    clickEvent =
                        ClickEvent.event(
                            accountKey = accountKey,
                            PostEvent.Pleroma.React(
                                postKey = statusKey,
                                hasReacted = true,
                                reaction = it.name.orEmpty(),
                            ),
                        ),
                )
            }.toImmutableList()
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
    val quoteStatus = quote?.renderStatus(host, accountKey)
    return UiTimelineV2.Post(
        images =
            mediaAttachments
                ?.mapNotNull { attachment ->
                    attachment.toUi(sensitive = sensitive ?: false)
                }?.toPersistentList() ?: persistentListOf(),
        contentWarning =
            spoilerText?.takeIf { it.isNotEmpty() && it.isNotBlank() }?.toUiPlainText(),
        user = actualUser,
        quote = listOfNotNull(quoteStatus).toImmutableList(),
        content = parseMastodonContent(this, accountKey, host),
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
            buildList {
                add(
                    ActionMenu.Item(
                        icon = UiIcon.Reply,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                        count = UiNumber(repliesCount ?: 0),
                        clickEvent =
                            if (accountKey == null) {
                                ClickEvent.Noop
                            } else {
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Compose
                                        .Reply(
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ),
                                )
                            },
                    ),
                )
                if (canReblog && canQuote && accountKey != null) {
                    add(
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.mastodonRepost(
                                    reblogged = reblogged ?: false,
                                    reblogsCount = reblogsCount ?: 0,
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                            actions =
                                buildList {
                                    if (canQuote) {
                                        add(
                                            ActionMenu.Item(
                                                icon = UiIcon.Quote,
                                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                                count = UiNumber(quotesCount ?: 0),
                                                clickEvent =
                                                    ClickEvent.Deeplink(
                                                        DeeplinkRoute.Compose
                                                            .Quote(
                                                                accountKey = accountKey,
                                                                statusKey = statusKey,
                                                            ),
                                                    ),
                                            ),
                                        )
                                    }
                                    add(
                                        ActionMenu.mastodonRepost(
                                            reblogged = reblogged ?: false,
                                            reblogsCount = reblogsCount ?: 0,
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ),
                                    )
                                }.toImmutableList(),
                        ),
                    )
                } else if (canQuote) {
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Quote,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                            count = UiNumber(quotesCount ?: 0),
                            clickEvent =
                                if (accountKey == null) {
                                    ClickEvent.Noop
                                } else {
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Compose
                                            .Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                    )
                                },
                        ),
                    )
                } else if (canReblog) {
                    add(
                        ActionMenu.mastodonRepost(
                            reblogged = reblogged ?: false,
                            reblogsCount = reblogsCount ?: 0,
                            accountKey = accountKey,
                            statusKey = statusKey,
                        ),
                    )
                } else if (accountKey == null) {
                    add(
                        ActionMenu.mastodonRepost(
                            reblogged = reblogged ?: false,
                            reblogsCount = reblogsCount ?: 0,
                            accountKey = accountKey,
                            statusKey = statusKey,
                        ),
                    )
                }
                add(
                    ActionMenu.mastodonLike(
                        favourited = favourited ?: false,
                        favouritesCount = favouritesCount ?: 0,
                        accountKey = accountKey,
                        statusKey = statusKey,
                    ),
                )
                if (canReact) {
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.React,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.React),
                            clickEvent =
                                if (accountKey == null) {
                                    ClickEvent.Noop
                                } else {
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status
                                            .AddReaction(
                                                statusKey = statusKey,
                                                accountType = AccountType.Specific(accountKey),
                                            ),
                                    )
                                },
                        ),
                    )
                }
                add(
                    ActionMenu.Group(
                        displayItem =
                            ActionMenu.Item(
                                icon = UiIcon.More,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                            ),
                        actions =
                            buildList {
                                if (accountKey != null) {
                                    add(
                                        ActionMenu.mastodonBookmark(
                                            bookmarked ?: false,
                                            accountKey,
                                            statusKey,
                                        ),
                                    )
                                }
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Share,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
//                                        onClicked = {
//                                            launcher.launch(
//                                                DeeplinkRoute.Status
//                                                    .ShareSheet(
//                                                        statusKey = statusKey,
//                                                        accountType =
//                                                            if (accountKey != null) {
//                                                                AccountType.Specific(accountKey)
//                                                            } else {
//                                                                AccountType.Guest
//                                                            },
//                                                        shareUrl = postUrl,
//                                                    ).toUri(),
//                                            )
//                                        },
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .ShareSheet(
                                                        statusKey = statusKey,
                                                        accountType =
                                                            if (accountKey != null) {
                                                                AccountType.Specific(accountKey)
                                                            } else {
                                                                AccountType.Guest
                                                            },
                                                        shareUrl = postUrl,
                                                    ),
                                            ),
                                    ),
                                )

                                if (isFromMe) {
                                    add(
                                        ActionMenu.Item(
                                            icon = UiIcon.Delete,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                            color = ActionMenu.Item.Color.Red,
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status
                                                        .DeleteConfirm(
                                                            accountType =
                                                                AccountType.Specific(
                                                                    accountKey,
                                                                ),
                                                            statusKey = statusKey,
                                                        ),
                                                ),
                                        ),
                                    )
                                } else {
                                    add(ActionMenu.Divider)
                                    addAll(
                                        userActionsMenu(
                                            accountKey = accountKey,
                                            userKey = actualUser.key,
                                            handle = actualUser.handle.canonical,
                                        ),
                                    )
                                    add(ActionMenu.Divider)
                                    add(
                                        ActionMenu.Item(
                                            icon = UiIcon.Report,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                            color = ActionMenu.Item.Color.Red,
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status
                                                        .MastodonReport(
                                                            statusKey = statusKey,
                                                            userKey = actualUser.key,
                                                            accountType =
                                                                if (accountKey != null) {
                                                                    AccountType.Specific(accountKey)
                                                                } else {
                                                                    AccountType.Guest
                                                                },
                                                        ),
                                                ),
                                        ),
                                    )
                                }
                            }.toImmutableList(),
                    ),
                )
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
                    expiresAt = it.expiresAt,
                    multiple = it.multiple ?: false,
                    ownVotes = it.ownVotes?.toPersistentList() ?: persistentListOf(),
//                    onVote = { options ->
//                        if (it.id != null && dataSource != null) {
//                            dataSource.vote(statusKey, it.id, options)
//                        }
//                    },
                    enabled = accountKey != null && !isFromMe,
                    voteEvent =
                        if (accountKey != null) {
                            PostEvent.Mastodon.Vote(
                                id = it.id ?: "",
                                postKey = statusKey,
                                accountKey = accountKey,
                                options = persistentListOf(),
                            )
                        } else {
                            null
                        },
                )
            },
        statusKey = statusKey,
        createdAt = createdAt?.toUi() ?: Instant.DISTANT_PAST.toUi(),
        visibility = renderedVisibility,
        sensitive = sensitive ?: false,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status
                    .Detail(
                        statusKey = statusKey,
                        accountType =
                            accountKey?.let { AccountType.Specific(it) }
                                ?: AccountType.Guest,
                    ),
            ),
        platformType = PlatformType.Mastodon,
        emojiReactions = reactions,
        accountType = accountKey.toAccountType(),
    )
}

internal fun ActionMenu.Companion.mastodonLike(
    favourited: Boolean,
    favouritesCount: Long,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_like_$statusKey",
        icon = if (favourited) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (favourited) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(favouritesCount),
        color = if (favourited) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Like(
                    postKey = statusKey,
                    liked = favourited,
                    accountKey = accountKey,
                    count = favouritesCount,
                )
            },
    )

internal fun ActionMenu.Companion.mastodonRepost(
    reblogged: Boolean,
    reblogsCount: Long,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_repost_$statusKey",
        icon = if (reblogged) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (reblogged) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(reblogsCount),
        color = if (reblogged) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Reblog(
                    postKey = statusKey,
                    reblogged = reblogged,
                    count = reblogsCount,
                    accountKey = accountKey,
                )
            },
    )

internal fun ActionMenu.Companion.mastodonBookmark(
    bookmarked: Boolean,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_bookmark_$statusKey",
        icon =
            if (bookmarked) {
                UiIcon.Unbookmark
            } else {
                UiIcon.Bookmark
            },
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) {
                    ActionMenu.Item.Text.Localized.Type.Unbookmark
                } else {
                    ActionMenu.Item.Text.Localized.Type.Bookmark
                },
            ),
        count = UiNumber(0),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                )
            },
    )

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
        nameInternal = parseName(this),
        handle =
            UiHandle(
                raw = username.orEmpty(),
                host = remoteHost,
            ),
        key = userKey,
        banner = header,
        description =
            parseNote(
                this,
                accountKey = accountKey,
            ),
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
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            accountKey?.let {
                                AccountType.Specific(
                                    it,
                                )
                            } ?: AccountType.Guest,
                        userKey = userKey,
                    ),
            ),
    )
}

private fun parseNote(
    account: Account,
    accountKey: MicroBlogKey?,
): UiRichText {
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
        it.toUi()
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

private fun parseName(status: Account): UiRichText {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return parseHtml(content).toUi()
}

internal fun parseMastodonContent(
    status: Status,
//    text: String,
    accountKey: MicroBlogKey?,
    host: String,
): UiRichText {
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
    return body.toUi()
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

internal fun MastodonList.render(): UiList.List =
    UiList.List(
        id = id.toString(),
        title = title.orEmpty(),
    )
