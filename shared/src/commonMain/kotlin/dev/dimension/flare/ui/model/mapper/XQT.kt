package dev.dimension.flare.ui.model.mapper

import de.cketti.codepoints.codePointCount
import de.cketti.codepoints.deluxe.codePointSequence
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.mapper.XQTTimeline
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.bookmark
import dev.dimension.flare.data.datasource.microblog.like
import dev.dimension.flare.data.datasource.microblog.repost
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.xqt.model.Admin
import dev.dimension.flare.data.network.xqt.model.AudioSpace
import dev.dimension.flare.data.network.xqt.model.Entities
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.InboxMessageData
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.Media
import dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichTextTag
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddToModule
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTwitterList
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacy
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacyBindingValueData
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetUnion
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.TwitterArticleBlock
import dev.dimension.flare.data.network.xqt.model.TwitterArticleEntity
import dev.dimension.flare.data.network.xqt.model.TwitterArticleInlineStyleRange
import dev.dimension.flare.data.network.xqt.model.TwitterArticleMedia
import dev.dimension.flare.data.network.xqt.model.TwitterArticleResult
import dev.dimension.flare.data.network.xqt.model.TwitterList
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTwitterArticle
import dev.dimension.flare.ui.render.RenderBlockStyle
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.time.Clock
import kotlin.time.Instant

private val twitterParser by lazy {
    TwitterParser(enableNonAsciiInUrl = false)
}

private fun TweetUnion.toTweetOrNull(): Tweet? =
    when (this) {
        is Tweet -> this
        is TweetWithVisibilityResults -> tweet
        is TweetTombstone -> null
    }

private fun TweetUnion.getRetweet(): TweetUnion? =
    when (this) {
        is Tweet -> legacy?.retweetedStatusResult?.result
        is TweetTombstone -> null
        is TweetWithVisibilityResults -> tweet.legacy?.retweetedStatusResult?.result
    }

private fun TweetUnion.getQuoted(): TweetUnion? =
    when (this) {
        is Tweet -> quotedStatusResult?.result
        is TweetTombstone -> null
        is TweetWithVisibilityResults -> tweet.quotedStatusResult?.result
    }

internal fun TopLevel.renderNotifications(accountKey: MicroBlogKey): List<UiTimelineV2> {
    return timeline
        ?.instructions
        ?.asSequence()
        ?.flatMap {
            it.addEntries?.entries.orEmpty()
        }?.mapNotNull { entry ->
            entry.content?.item?.content
        }?.mapNotNull { content ->
            val notification = content.notification
            val mentionTweet = content.tweet
            if (notification != null) {
                val url = notification.url?.url
                val data = globalObjects?.notifications?.get(notification.id)
                val message = data?.message?.text
                val users =
                    data?.template?.aggregateUserActionsV1?.fromUsers?.mapNotNull { ref ->
                        globalObjects
                            .users
                            ?.get(ref.user?.id)
                            ?.let { userLegacy ->
                                User(
                                    legacy = userLegacy,
                                    isBlueVerified = userLegacy.verified,
                                    restId = ref.user?.id.orEmpty(),
                                )
                            }?.render(accountKey)
                    }
                val notificationTweet =
                    notification.targetTweets?.mapNotNull {
                        globalObjects?.tweets?.get(it)
                    }
                val createdAt =
                    data?.timestampMS?.toLongOrNull()?.let {
                        Instant.fromEpochMilliseconds(it)
                    }
                val icon =
                    when (data?.icon?.id) {
                        "person_icon" -> UiIcon.Follow
                        "heart_icon" -> UiIcon.Like
                        "bird_icon" -> UiIcon.Info
                        else -> UiIcon.Info
                    }
                val post =
                    notificationTweet?.firstOrNull()?.let {
                        Tweet(
                            restId = it.idStr,
                            core =
                                UserResultCore(
                                    userResults =
                                        UserResults(
                                            result =
                                                User(
                                                    legacy =
                                                        globalObjects?.users?.get(it.userIdStr)
                                                            ?: return@let null,
                                                    isBlueVerified = false,
                                                    restId = it.userIdStr ?: return@let null,
                                                ),
                                        ),
                                ),
                            legacy = it,
                        ).renderStatus(accountKey)
                    }

                val statusKey =
                    MicroBlogKey(
                        id = notification.id.orEmpty(),
                        host = accountKey.host,
                    )
                val clickEvent =
                    when {
                        url == "/2/notifications/device_follow.json" ->
                            ClickEvent.Deeplink(
                                DeeplinkRoute.Timeline
                                    .XQTDeviceFollow(
                                        accountType = AccountType.Specific(accountKey),
                                    ),
                            )

                        post == null && !url.isNullOrEmpty() && !url.startsWith("/") ->
                            ClickEvent.Deeplink(
                                DeeplinkRoute.OpenLinkDirectly(url),
                            )

                        else -> ClickEvent.Noop
                    }
                val messageItem =
                    UiTimelineV2.Message(
                        user = null,
                        statusKey = statusKey,
                        icon = icon,
                        type = UiTimelineV2.Message.Type.Raw(message.orEmpty()),
                        createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
                        clickEvent = clickEvent,
                        accountType = AccountType.Specific(accountKey),
                    )
                when {
                    data?.icon?.id == "person_icon" && users?.size == 1 ->
                        UiTimelineV2.User(
                            message = messageItem,
                            value = users.first(),
                            createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
                            statusKey = users.first().key,
                            accountType = AccountType.Specific(accountKey),
                        )

                    data?.icon?.id == "person_icon" && !users.isNullOrEmpty() ->
                        UiTimelineV2.UserList(
                            message = messageItem,
                            users = users.toImmutableList(),
                            createdAt = createdAt?.toUi() ?: Clock.System.now().toUi(),
                            statusKey = statusKey,
                            post = null,
                            accountType = AccountType.Specific(accountKey),
                        )

                    post != null -> post.copy(message = messageItem)
                    else -> messageItem
                }
            } else if (mentionTweet != null) {
                val tweet = globalObjects?.tweets?.get(mentionTweet.id) ?: return@mapNotNull null
                if (tweet.userIdStr == null) {
                    return@mapNotNull null
                }
                val user = globalObjects.users?.get(tweet.userIdStr) ?: return@mapNotNull null
                if (mentionTweet.id == null) {
                    return@mapNotNull null
                }
                val renderedUser =
                    user
                        .let { userLegacy ->
                            User(
                                legacy = userLegacy,
                                isBlueVerified = userLegacy.verified,
                                restId = tweet.userIdStr,
                            )
                        }.render(accountKey)
                val data =
                    Tweet(
                        restId = mentionTweet.id,
                        core =
                            UserResultCore(
                                userResults =
                                    UserResults(
                                        result =
                                            User(
                                                legacy = user,
                                                isBlueVerified = user.verified,
                                                restId = tweet.userIdStr,
                                            ),
                                    ),
                            ),
                        legacy = tweet,
                    ).renderStatus(accountKey)
                data.copy(
                    message =
                        UiTimelineV2.Message(
                            user = renderedUser,
                            statusKey =
                                MicroBlogKey(
                                    id = notification?.id.orEmpty(),
                                    host = accountKey.host,
                                ),
                            icon = UiIcon.Retweet,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Mention,
                                ),
                            createdAt = data.createdAt,
                            clickEvent =
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Profile
                                        .User(
                                            accountType = AccountType.Specific(accountKey),
                                            userKey = renderedUser.key,
                                        ),
                                ),
                            accountType = AccountType.Specific(accountKey),
                        ),
                )
            } else {
                null
            }
        }?.toList()
        .orEmpty()
}

internal fun Tweet.render(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val retweetUnion = this.getRetweet()
    val quoteUnion = retweetUnion?.getQuoted() ?: this.getQuoted()
    val quote = quoteUnion?.toTweetOrNull()?.renderStatus(accountKey = accountKey)
    val retweet = retweetUnion?.toTweetOrNull()?.renderStatus(accountKey, quote = quote)
    val currentTweet = renderStatus(accountKey, quote = quote)
    val actualTweet =
        if (retweet != null) {
            currentTweet.copy(internalRepost = retweet)
        } else {
            currentTweet
        }
    val user = currentTweet.user
    val message =
        if (retweet != null && user != null) {
            UiTimelineV2.Message(
                user = user,
                icon = UiIcon.Retweet,
                type =
                    UiTimelineV2.Message.Type.Localized(
                        UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                    ),
                createdAt = currentTweet.createdAt,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile
                            .User(
                                accountType = AccountType.Specific(accountKey),
                                userKey = user.key,
                            ),
                    ),
                statusKey = currentTweet.statusKey,
                accountType = AccountType.Specific(accountKey),
            )
        } else {
            null
        }
    return if (message != null) {
        actualTweet.copy(message = message)
    } else {
        actualTweet
    }
}

internal fun XQTTimeline.render(accountKey: MicroBlogKey): UiTimelineV2? {
    val result = tweets.tweetResults.result ?: return null
    val retweetUnion = result.getRetweet()
    val quoteUnion = retweetUnion?.getQuoted() ?: result.getQuoted()
    val quote = quoteUnion?.toTweetOrNull()?.renderStatus(accountKey = accountKey)
    val parentStatuses =
        parents
            .mapNotNull {
                it.tweets.tweetResults.result
                    ?.toTweetOrNull()
            }.map { it.renderStatus(accountKey = accountKey) }
            .toImmutableList()

    val currentTweet =
        result
            .toTweetOrNull()
            ?.renderStatus(
                accountKey = accountKey,
                parents = parentStatuses,
                quote = quote,
            ) ?: return null
    val retweet =
        retweetUnion
            ?.toTweetOrNull()
            ?.renderStatus(
                accountKey = accountKey,
                quote = quote,
            )
    val actualTweet =
        if (retweet != null) {
            currentTweet.copy(internalRepost = retweet)
        } else {
            currentTweet
        }
    val user = currentTweet.user
    val message =
        if (retweet != null && user != null) {
            UiTimelineV2.Message(
                user = user,
                icon = UiIcon.Retweet,
                type =
                    UiTimelineV2.Message.Type.Localized(
                        UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                    ),
                createdAt = currentTweet.createdAt,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Profile
                            .User(
                                accountType = AccountType.Specific(accountKey),
                                userKey = user.key,
                            ),
                    ),
                statusKey = currentTweet.statusKey,
                accountType = AccountType.Specific(accountKey),
            )
        } else {
            null
        }
    return if (message != null) {
        actualTweet.copy(message = message)
    } else {
        actualTweet
    }
}

internal fun Tweet.renderStatus(
    accountKey: MicroBlogKey,
    parents: List<UiTimelineV2.Post> = emptyList(),
    quote: UiTimelineV2.Post? = null,
): UiTimelineV2.Post {
    val actualParents = parents.toImmutableList()
    val actualQuote = quote ?: quotedStatusResult?.result?.toTweetOrNull()?.renderStatus(accountKey = accountKey)
    val user =
        core
            ?.userResults
            ?.result
            ?.let {
                when (it) {
                    is User -> it
                    is UserUnavailable -> null
                }
            }?.render(accountKey = accountKey)
    val uiCard =
        articleResult
            ?.let { article ->
                val title =
                    article.title?.takeIf { it.isNotBlank() }
                        ?: article.previewText
                            ?.lineSequence()
                            ?.firstOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                val tweetId = legacy?.idStr ?: restId
                title?.let {
                    UiCard(
                        title = it,
                        media = article.coverMedia.toUiCardImage(),
                        description = article.previewText?.trim()?.takeIf { preview -> preview.isNotEmpty() },
                        url =
                            DeeplinkRoute
                                .TwitterArticle(
                                    accountType = AccountType.Specific(accountKey),
                                    tweetId = tweetId,
                                    articleId = article.restId,
                                ).toUri(),
                    )
                }
            } ?: card?.legacy?.let {
            val title = it.get("title")?.stringValue
            val image = it.get("photo_image_full_size_original")?.imageValue
            val description = it.get("description")?.stringValue
            val cardUrl =
                it.get("card_url")?.stringValue?.let {
                    legacy
                        ?.entities
                        ?.urls
                        ?.firstOrNull { url -> url.url == it }
                        ?.expandedUrl
                }
            if (title != null && cardUrl != null) {
                UiCard(
                    title = title,
                    media =
                        image?.url?.let {
                            UiMedia.Image(
                                url = it,
                                previewUrl = it,
                                height = image.height?.toFloat() ?: 0f,
                                width = image.width?.toFloat() ?: 0f,
                                sensitive = false,
                                description = null,
                            )
                        },
                    description = description,
                    url = cardUrl,
                )
            } else if (it.name.endsWith("audiospace", ignoreCase = true) == true) {
                val displayUrl =
                    it.url.let {
                        legacy
                            ?.entities
                            ?.urls
                            ?.firstOrNull { url -> url.url == it }
                            ?.displayUrl
                    }
                val id = it.get("id")?.stringValue
                if (displayUrl != null && id != null) {
                    UiCard(
                        title = displayUrl,
                        media = null,
                        description = null,
                        url =
                            DeeplinkRoute.Media
                                .Podcast(
                                    accountType =
                                        dev.dimension.flare.model.AccountType
                                            .Specific(accountKey),
                                    id = id,
                                ).toUri(),
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }
    val poll =
        card?.legacy?.let { cardLegacy ->
            if (cardLegacy.get("choice1_label") != null) {
                val optionData =
                    (1..4).mapNotNull { index ->
                        cardLegacy.get("choice${index}_label")?.stringValue?.let { label ->
                            val count = cardLegacy.get("choice${index}_count")?.stringValue?.toLongOrNull() ?: 0L
                            Triple(index - 1, label, count)
                        }
                    }
                val totalVotes = optionData.sumOf { it.third }.coerceAtLeast(1L)
                val options =
                    optionData
                        .map { (_, label, count) ->
                            UiPoll.Option(
                                title = label,
                                votesCount = count,
                                percentage = count.toFloat() / totalVotes.toFloat(),
                            )
                        }.toImmutableList()
                val ownVotes =
                    optionData
                        .mapNotNull { (index, _, _) ->
                            if (cardLegacy.get("choice${index + 1}_selected")?.booleanValue == true) {
                                index
                            } else {
                                null
                            }
                        }.toImmutableList()
                UiPoll(
                    // xqt dose not have id
                    id = "",
                    options = options,
                    multiple = false,
                    ownVotes = ownVotes,
                    voteMutation = null,
                    expiresAt =
                        cardLegacy.get("end_datetime_utc")?.stringValue?.let {
                            parseXQTCustomDateTime(
                                it,
                            )
                        },
                    enabled = false,
                )
            } else {
                null
            }
        }
    val medias =
        if (noteTweet
                ?.noteTweetResults
                ?.result
                ?.media
                ?.inlineMedia
                .isNullOrEmpty()
        ) {
            legacy
                ?.entities
                ?.media
                ?.map { media ->
                    when (media.type) {
                        Media.Type.photo ->
                            UiMedia.Image(
                                url = media.mediaUrlHttps + "?name=orig",
                                previewUrl = media.mediaUrlHttps,
                                height = media.originalInfo.height.toFloat(),
                                width = media.originalInfo.width.toFloat(),
                                sensitive = legacy.possiblySensitive == true,
                                description = media.ext_alt_text,
                            )

                        Media.Type.video, Media.Type.animatedGif ->
                            UiMedia.Video(
                                url =
                                    media.videoInfo
                                        ?.variants
                                        ?.maxByOrNull { it.bitrate ?: 0 }
                                        ?.url ?: "",
                                thumbnailUrl = media.mediaUrlHttps,
                                height = media.originalInfo.height.toFloat(),
                                width = media.originalInfo.width.toFloat(),
                                description = media.ext_alt_text,
                            )
                    }
                }.orEmpty()
                .toImmutableList()
        } else {
            persistentListOf()
        }
    val content = renderContent(accountKey)

    val replyToHandle = legacy?.in_reply_to_screen_name?.let { "@$it" }

    val isFromMe = user?.key == accountKey
    val createAt =
        legacy?.createdAt?.let { parseXQTCustomDateTime(it) } ?: Clock.System.now()
    val statusKey =
        MicroBlogKey(
            id = legacy?.idStr ?: restId,
            host = accountKey.host,
        )
    val statusUrl =
        buildString {
            append("https://${accountKey.host}/")
            append(user?.handleWithoutAtAndHost)
            append("/status/")
            append(legacy?.idStr ?: restId)
        }
    val fxUrl =
        buildString {
            append("https://fixupx.com/")
            append(user?.handleWithoutAtAndHost)
            append("/status/")
            append(legacy?.idStr ?: restId)
        }
    val fixvxUrl =
        buildString {
            append("https://fixvx.com/")
            append(user?.handleWithoutAtAndHost)
            append("/status/")
            append(legacy?.idStr ?: restId)
        }

    return UiTimelineV2.Post(
        message = null,
        platformType = PlatformType.xQt,
        images = medias,
        sensitive = legacy?.possiblySensitive == true,
        contentWarning = null,
        user = user,
        sourceLanguages = listOfNotNull(legacy?.lang).toPersistentList(),
        quote = listOfNotNull(actualQuote).toImmutableList(),
        content = content,
        actions =
            listOfNotNull(
                ActionMenu.Item(
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(legacy?.replyCount?.toLong() ?: 0),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Compose
                                .Reply(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                        ),
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.repost(
                            statusKey = statusKey,
                            accountKey = accountKey,
                            toggled = legacy?.retweeted == true,
                            count = legacy?.retweetCount?.toLong() ?: 0,
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.repost(
                                statusKey = statusKey,
                                accountKey = accountKey,
                                toggled = legacy?.retweeted == true,
                                count = legacy?.retweetCount?.toLong() ?: 0,
                            ),
                            ActionMenu.Item(
                                icon = UiIcon.Quote,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                count = UiNumber(legacy?.quoteCount?.toLong() ?: 0),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Compose
                                            .Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                    ),
                            ),
                        ).toImmutableList(),
                ),
                ActionMenu.like(
                    statusKey = statusKey,
                    accountKey = accountKey,
                    toggled = legacy?.favorited == true,
                    count = legacy?.favoriteCount?.toLong() ?: 0,
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        buildList {
                            add(
                                ActionMenu.bookmark(
                                    statusKey = statusKey,
                                    accountKey = accountKey,
                                    toggled = legacy?.bookmarked == true,
                                    count = legacy?.bookmarkCount?.toLong() ?: 0,
                                ),
                            )
                            add(
                                ActionMenu.Item(
                                    icon = UiIcon.Share,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Status
                                                .ShareSheet(
                                                    statusKey = statusKey,
                                                    accountType = AccountType.Specific(accountKey),
                                                    shareUrl = statusUrl,
                                                    fxShareUrl = fxUrl,
                                                    fixvxShareUrl = fixvxUrl,
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
                                                        statusKey = statusKey,
                                                        accountType =
                                                            dev.dimension.flare.model.AccountType
                                                                .Specific(accountKey),
                                                    ),
                                            ),
                                    ),
                                )
                            } else {
                                if (user != null) {
                                    add(ActionMenu.Divider)
                                    addAll(
                                        userActionsMenu(
                                            accountKey = accountKey,
                                            userKey = user.key,
                                            handle = user.handle.canonical,
                                        ),
                                    )
                                    add(ActionMenu.Divider)
                                }
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Report,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                        color = ActionMenu.Item.Color.Red,
                                        clickEvent = ClickEvent.Noop,
                                    ),
                                )
                            }
                        }.toImmutableList(),
                ),
            ).toImmutableList(),
        poll = poll,
        statusKey = statusKey,
        card = uiCard,
        createdAt = createAt.toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = replyToHandle,
        parents = actualParents,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

internal fun User.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = restId,
            host = accountKey.host,
        )
    return UiProfile(
        key = userKey,
        avatar = avatarUrl,
        nameInternal = name.toUiPlainText(),
        handle =
            UiHandle(
                raw = screenName,
                host = accountKey.host,
            ),
        banner = legacy.profileBannerUrl,
        description =
            legacy.description?.takeIf { it.isNotEmpty() }?.let {
                twitterParser
                    .parse(it)
                    .map { token ->
                        if (token is UrlToken) {
                            val actual =
                                legacy.entities
                                    ?.description
                                    ?.urls
                                    ?.firstOrNull { it.url == token.value.trim() }
                                    ?.expandedUrl
                            if (actual != null) {
                                UrlToken(actual)
                            } else {
                                token
                            }
                        } else {
                            token
                        }
                    }.toUiRichText(accountKey)
            },
        matrices =
            UiProfile.Matrices(
                fansCount = legacy.followersCount.toLong(),
                followsCount = legacy.friendsCount.toLong(),
                statusesCount = legacy.statusesCount.toLong(),
            ),
        mark =
            listOfNotNull(
                if (legacy.verified) {
                    UiProfile.Mark.Verified
                } else {
                    null
                },
                if (legacy.protected == true) {
                    UiProfile.Mark.Locked
                } else {
                    null
                },
            ).toImmutableList(),
        bottomContent =
            run {
                val location = legacy.location
                val profileUrl = legacy.url
                if (location == null && profileUrl == null) {
                    return@run null
                }
                UiProfile.BottomContent.Iconify(
                    items =
                        listOfNotNull(
                            if (!location.isNullOrEmpty()) {
                                UiProfile.BottomContent.Iconify.Icon.Location to
                                    location.toUiPlainText()
                            } else {
                                null
                            },
                            if (!profileUrl.isNullOrEmpty()) {
                                val actualUrl =
                                    legacy.entities
                                        ?.url
                                        ?.urls
                                        ?.firstOrNull { it.url == profileUrl }
                                val displayUrl = actualUrl?.displayUrl ?: profileUrl
                                val url = actualUrl?.expandedUrl ?: profileUrl
                                UiProfile.BottomContent.Iconify.Icon.Url to
                                    uiRichTextOf(
                                        renderRuns =
                                            listOf(
                                                RenderContent.Text(
                                                    runs =
                                                        listOf(
                                                            RenderRun.Text(
                                                                text = displayUrl,
                                                                style = RenderTextStyle(link = url),
                                                            ),
                                                        ).toImmutableList(),
                                                ),
                                            ),
                                    )
                            } else {
                                null
                            },
                            if (legacy.verified) {
                                UiProfile.BottomContent.Iconify.Icon.Verify to "".toUiPlainText()
                            } else {
                                null
                            },
                        ).toMap().toPersistentMap(),
                )
            },
        platformType = PlatformType.xQt,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
    )
}

private fun TweetCardLegacy.get(key: String): TweetCardLegacyBindingValueData? = bindingValues.firstOrNull { it.key == key }?.value

internal fun GetProfileSpotlightsQuery200Response.toUi(muting: Boolean): UiRelation {
    with(data.userResultByScreenName.result.legacy) {
        return UiRelation(
            following = following ?: false,
            isFans = followedBy ?: false,
            blocking = blocking ?: false,
            blockedBy = blockedBy ?: false,
            muted = muting,
        )
    }
}

private fun String.replaceWithOriginImageUrl() = this.replace("_normal.", ".")

internal fun parseXQTCustomDateTime(dateTimeStr: String): Instant? {
    val months =
        mapOf(
            "Jan" to 1,
            "Feb" to 2,
            "Mar" to 3,
            "Apr" to 4,
            "May" to 5,
            "Jun" to 6,
            "Jul" to 7,
            "Aug" to 8,
            "Sep" to 9,
            "Oct" to 10,
            "Nov" to 11,
            "Dec" to 12,
        )

    val parts = dateTimeStr.trim().split(Regex("\\s+"))
    if (parts.size != 6) return null

    try {
        val month = months[parts[1]] ?: return null
        val day = parts[2].toInt()
        val timeParts = parts[3].split(":").map { it.toInt() }
        val year = parts[5].toInt()
        val timezoneOffset = parts[4]
        if (timezoneOffset.length != 5 || (timezoneOffset[0] != '+' && timezoneOffset[0] != '-')) return null
        val offsetHours = timezoneOffset.substring(1, 3).toInt()
        val offsetMinutes = timezoneOffset.substring(3, 5).toInt()

        val hour = timeParts[0]
        val minute = timeParts[1]
        val second = timeParts[2]

        val dateTime = LocalDateTime(year, month, day, hour, minute, second)
        val offsetSign = if (timezoneOffset.startsWith("+")) 1 else -1
        val totalOffsetMillis = ((offsetHours * 60L) + offsetMinutes) * 60_000L
        val utcEpochMillis = dateTime.toInstant(TimeZone.UTC).toEpochMilliseconds() - (offsetSign * totalOffsetMillis)
        return Instant.fromEpochMilliseconds(utcEpochMillis)
    } catch (e: Exception) {
        return null
    }
}

internal fun List<InstructionUnion>.list(accountKey: MicroBlogKey): List<UiList.List> =
    flatMap {
        when (it) {
            is TimelineAddEntries ->
                it.propertyEntries.flatMap {
                    when (val content = it.content) {
                        is TimelineTimelineModule ->
                            content.items.orEmpty().mapNotNull {
                                when (val itemContent = it.item.itemContent) {
                                    is TimelineTwitterList -> itemContent.list
                                    else -> null
                                }
                            }

                        else -> emptyList()
                    }
                }

            is TimelineAddToModule ->
                it.moduleItems.flatMap {
                    when (val itemContent = it.item.itemContent) {
                        is TimelineTwitterList -> listOfNotNull(itemContent.list)
                        else -> emptyList()
                    }
                }

            else -> emptyList()
        }
    }.filter {
        it.following == true
    }.map {
        it.render(accountKey = accountKey)
    }

internal fun TwitterList.render(accountKey: MicroBlogKey): UiList.List {
    val user =
        userResults?.result?.let {
            when (it) {
                is User -> it.render(accountKey = accountKey)
                else -> null
            }
        }
    return UiList.List(
        id = idStr.orEmpty(),
        title = name.orEmpty(),
        description = description.orEmpty(),
        creator = user,
        avatar =
            customBannerMedia?.mediaInfo?.originalImgURL
                ?: defaultBannerMedia?.mediaInfo?.originalImgURL,
        readonly = user?.key != accountKey,
    )
}

internal fun renderXQTDirectMessage(
    data: String,
    accountKey: MicroBlogKey,
    chocolate: String?,
): UiDMItem.Message {
    val messageData = data.decodeJson<InboxMessageData>()
    val attachment = messageData.attachment
    val photo = attachment?.photo
    val animatedGif = attachment?.animatedGif
    val video = attachment?.video
    val tweet = attachment?.tweet
    if (!photo
            ?.url
            .isNullOrEmpty() &&
        messageData.text
            .orEmpty()
            .endsWith(photo.url) &&
        !photo.mediaUrlHttps
            .isNullOrEmpty() &&
        chocolate != null
    ) {
        return UiDMItem.Message.Media(
            UiMedia.Image(
                url = photo.mediaUrlHttps,
                previewUrl = photo.mediaUrlHttps,
                height =
                    photo.originalInfo
                        ?.height
                        ?.toFloat() ?: 0f,
                width =
                    photo.originalInfo
                        ?.width
                        ?.toFloat() ?: 0f,
                sensitive = false,
                description = photo.extAltText,
                customHeaders =
                    persistentMapOf(
                        "Cookie" to chocolate,
                        "Referer" to "https://${accountKey.host}/",
                    ),
            ),
        )
    } else if (!animatedGif
            ?.url
            .isNullOrEmpty() &&
        messageData.text
            .orEmpty()
            .endsWith(animatedGif.url) &&
        !animatedGif.mediaUrlHttps
            .isNullOrEmpty() &&
        chocolate != null
    ) {
        return UiDMItem.Message.Media(
            UiMedia.Gif(
                url = animatedGif.mediaUrlHttps,
                previewUrl = animatedGif.mediaUrlHttps,
                height =
                    animatedGif.originalInfo
                        ?.height
                        ?.toFloat() ?: 0f,
                width =
                    animatedGif.originalInfo
                        ?.width
                        ?.toFloat() ?: 0f,
                description = animatedGif.extAltText,
                customHeaders =
                    persistentMapOf(
                        "Cookie" to chocolate,
                        "Referer" to "https://${accountKey.host}/",
                    ),
            ),
        )
    } else if (!video
            ?.url
            .isNullOrEmpty() &&
        messageData.text
            .orEmpty()
            .endsWith(video.url) &&
        !video.mediaUrlHttps
            .isNullOrEmpty() &&
        chocolate != null
    ) {
        val url =
            video.videoInfo
                ?.variants
                ?.firstOrNull()
                ?.url
        if (url != null) {
            if (video.audioOnly == true) {
                return UiDMItem.Message.Media(
                    UiMedia.Audio(
                        url = url,
                        previewUrl = video.url,
                        description = video.extAltText,
                        customHeaders =
                            persistentMapOf(
                                "Cookie" to chocolate,
                                "Referer" to "https://${accountKey.host}/",
                            ),
                    ),
                )
            } else {
                return UiDMItem.Message.Media(
                    UiMedia.Video(
                        url = url,
                        thumbnailUrl = video.mediaUrlHttps,
                        height =
                            video.originalInfo
                                ?.height
                                ?.toFloat() ?: 0f,
                        width =
                            video.originalInfo
                                ?.width
                                ?.toFloat() ?: 0f,
                        description = video.extAltText,
                        customHeaders =
                            persistentMapOf(
                                "Cookie" to chocolate,
                                "Referer" to "https://${accountKey.host}/",
                            ),
                    ),
                )
            }
        } else {
            return UiDMItem.Message.Text(
                twitterParser.parse(messageData.text.orEmpty()).toUiRichText(accountKey),
            )
        }
    } else if (!tweet
            ?.url
            .isNullOrEmpty() &&
        messageData.text
            .orEmpty()
            .endsWith(tweet.url) &&
        tweet.status != null
    ) {
        val tweetLegacy = tweet.status
        val status =
            Tweet(
                restId = tweetLegacy.idStr,
                core =
                    tweetLegacy.user?.let {
                        UserResultCore(
                            userResults =
                                UserResults(
                                    result =
                                        User(
                                            legacy = tweetLegacy.user.encodeJson().decodeJson(),
                                            isBlueVerified = tweetLegacy.user.isBlueVerified == true,
                                            restId = tweetLegacy.user.idStr.orEmpty(),
                                        ),
                                ),
                        )
                    },
                legacy = tweetLegacy,
            ).renderStatus(accountKey)
        return UiDMItem.Message.Status(
            status = status,
        )
    } else {
        return UiDMItem.Message.Text(
            twitterParser.parse(messageData.text.orEmpty()).toUiRichText(accountKey),
        )
    }
}

internal fun UserResults.render(accountKey: MicroBlogKey): UiProfile? =
    when (val result = result) {
        is User -> result.render(accountKey)
        is UserUnavailable -> null
        null -> null
    }

internal fun AudioSpace.render(
    accountKey: MicroBlogKey,
    url: String?,
): UiPodcast {
    val metadata = metadata ?: throw Exception("No ID")
    return UiPodcast(
        id = metadata.restID ?: throw Exception("No ID"),
        title = metadata.title.orEmpty(),
        playbackUrl = url,
        creator =
            metadata.creatorResults
                ?.render(accountKey) ?: throw Exception("No creator"),
        hosts =
            participants
                ?.admins
                ?.map {
                    it.render(accountKey)
                }.orEmpty()
                .toImmutableList(),
        speakers =
            participants
                ?.speakers
                ?.map {
                    it.render(accountKey)
                }.orEmpty()
                .toImmutableList(),
        listeners =
            participants
                ?.listeners
                ?.map {
                    it.render(accountKey)
                }.orEmpty()
                .toImmutableList(),
        ended = metadata.state == "Ended" || metadata.endedAt != null,
    )
}

private fun Admin.render(accountKey: MicroBlogKey): UiProfile {
    val key =
        MicroBlogKey(
            id = userResults?.restID ?: throw Exception("No ID"),
            host = accountKey.host,
        )
    return UiProfile(
        key = key,
        avatar = avatarURL?.replaceWithOriginImageUrl().orEmpty(),
        nameInternal = displayName.orEmpty().toUiPlainText(),
        handle =
            UiHandle(
                raw = twitterScreenName.orEmpty(),
                host = accountKey.host,
            ),
        platformType = PlatformType.xQt,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
                        userKey = key,
                    ),
            ),
        description = null,
        banner = null,
        matrices = UiProfile.Matrices(0, 0, 0),
        mark = persistentListOf(),
        bottomContent = null,
    )
}

internal val User.name: String
    get() = legacy.name ?: core?.name ?: "Unknown"

internal val User.screenName: String
    get() = legacy.screenName ?: core?.screenName ?: "Unknown"

internal val User.avatarUrl: String
    get() =
        legacy.profileImageUrlHttps?.replaceWithOriginImageUrl()
            ?: avatar?.imageUrl?.replaceWithOriginImageUrl()
            ?: "https://abs.twimg.com/sticky/default_profile_images/default_profile_normal.png"

private val Tweet.articleResult: TwitterArticleResult?
    get() = article?.articleResults?.result

private fun TwitterArticleMedia?.toUiCardImage(): UiMedia.Image? {
    val media = this ?: return null
    val imageUrl = media.displayImageUrl() ?: return null
    return UiMedia.Image(
        url = imageUrl,
        previewUrl = imageUrl,
        height = media.mediaInfo?.displayHeight()?.toFloat() ?: 0f,
        width = media.mediaInfo?.displayWidth()?.toFloat() ?: 0f,
        sensitive = false,
        description = null,
    )
}

private fun TwitterArticleMedia.displayImageUrl(): String? = mediaInfo?.displayImageUrl()

private fun dev.dimension.flare.data.network.xqt.model.TwitterArticleMediaInfo.displayImageUrl(): String? =
    originalImgUrl ?: previewImage?.originalImgUrl

private fun dev.dimension.flare.data.network.xqt.model.TwitterArticleMediaInfo.displayWidth(): Int? =
    originalImgWidth ?: previewImage?.originalImgWidth

private fun dev.dimension.flare.data.network.xqt.model.TwitterArticleMediaInfo.displayHeight(): Int? =
    originalImgHeight ?: previewImage?.originalImgHeight

private fun TwitterArticleMedia.href(): String? =
    mediaInfo
        ?.variants
        ?.filter { it.contentType?.startsWith("video/") == true }
        ?.maxByOrNull { it.bitRate ?: 0 }
        ?.url
        ?: displayImageUrl()?.let {
            DeeplinkRoute.Media
                .Image(
                    uri = it,
                    previewUrl = it,
                ).toUri()
        }

internal fun TweetUnion.renderArticle(
    accountKey: MicroBlogKey,
    expectedArticleId: String? = null,
): UiTwitterArticle? = toTweetOrNull()?.renderArticle(accountKey, expectedArticleId)

internal fun Tweet.renderArticle(
    accountKey: MicroBlogKey,
    expectedArticleId: String? = null,
): UiTwitterArticle? {
    val article = articleResult ?: return null
    if (expectedArticleId != null && article.restId != expectedArticleId) {
        return null
    }
    val profile =
        core
            ?.userResults
            ?.result
            ?.let {
                when (it) {
                    is User -> it.render(accountKey)
                    is UserUnavailable -> null
                }
            } ?: return null
    val title =
        article.title
            ?.takeIf { it.isNotBlank() }
            ?: article.previewText
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: return null
    return UiTwitterArticle(
        profile = profile,
        image = article.coverMedia?.displayImageUrl(),
        title = title,
        content = article.renderContent(accountKey),
    )
}

internal fun Tweet.renderContent(accountKey: MicroBlogKey): UiRichText {
    val noteTweet = noteTweet
    if (noteTweet == null) {
        val legacy = legacy
        val text =
            legacy
                ?.fullText
                ?.let {
                    if (legacy.displayTextRange.size == 2) {
                        it
                            .codePointSequence()
                            .drop(legacy.displayTextRange[0])
                            .take(legacy.displayTextRange[1] - legacy.displayTextRange[0])
                            .flatMap { codePoint ->
                                codePoint
                                    .toChars()
                                    .toList()
                            }.joinToString("")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                    } else {
                        it
                    }
                }.orEmpty()
        return renderRichText(text, legacy?.entities, accountKey)
    } else {
        val result = noteTweet.noteTweetResults.result
        val text = result.text
        val entities = result.entitySet
        val codePoints = text.codePointSequence().toList()

        val sortedEntities =
            buildList {
                entities.hashtags?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        RichEntity(
                            it.indices[0],
                            it.indices[1],
                            RichEntityContent.Text(
                                RenderRun.Text(
                                    text = "#${it.text ?: ""}",
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute
                                                    .Search(
                                                        dev.dimension.flare.model.AccountType
                                                            .Specific(accountKey),
                                                        "#${it.text}",
                                                    ).toUri(),
                                        ),
                                ),
                            ),
                        ),
                    )
                }
                entities.urls?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        RichEntity(
                            it.indices[0],
                            it.indices[1],
                            RichEntityContent.Text(
                                RenderRun.Text(
                                    text = it.displayUrl ?: (it.expandedUrl ?: it.url).trimUrl(),
                                    style =
                                        RenderTextStyle(
                                            link = it.expandedUrl ?: it.url,
                                        ),
                                ),
                            ),
                        ),
                    )
                }
                entities.userMentions?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        RichEntity(
                            it.indices[0],
                            it.indices[1],
                            RichEntityContent.Text(
                                RenderRun.Text(
                                    text = "@${it.screenName ?: ""}",
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute.Profile
                                                    .UserNameWithHost(
                                                        dev.dimension.flare.model.AccountType
                                                            .Specific(accountKey),
                                                        it.screenName?.trimStart('@') ?: "",
                                                        accountKey.host,
                                                    ).toUri(),
                                        ),
                                ),
                            ),
                        ),
                    )
                }
                entities.symbols?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        RichEntity(
                            it.indices[0],
                            it.indices[1],
                            RichEntityContent.Text(
                                RenderRun.Text(
                                    text = "$${it.text ?: ""}",
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute
                                                    .Search(
                                                        dev.dimension.flare.model.AccountType
                                                            .Specific(accountKey),
                                                        "$${it.text}",
                                                    ).toUri(),
                                        ),
                                ),
                            ),
                        ),
                    )
                }
                result.richtext?.richtextTags?.forEach { tag ->
                    val str =
                        codePoints
                            .subList(text.codePointCount(0, tag.fromIndex), text.codePointCount(0, tag.toIndex))
                            .flatMap { it.toChars().toList() }
                            .joinToString("")
                    add(
                        RichEntity(
                            text.codePointCount(0, tag.fromIndex),
                            text.codePointCount(0, tag.toIndex),
                            RichEntityContent.Text(
                                RenderRun.Text(
                                    text = str,
                                    style =
                                        RenderTextStyle(
                                            bold =
                                                tag.richtextTypes.any {
                                                    it == NoteTweetResultRichTextTag.RichtextTypes.bold
                                                },
                                            italic =
                                                tag.richtextTypes.any {
                                                    it == NoteTweetResultRichTextTag.RichtextTypes.italic
                                                },
                                        ),
                                ),
                            ),
                        ),
                    )
                }
                result.media?.inlineMedia?.forEach { inlineMedia ->
                    val media = legacy?.entities?.media?.firstOrNull { it.idStr == inlineMedia.mediaId }
                    if (media != null) {
                        val mediaIndexCodePoint = text.codePointCount(0, inlineMedia.index)
                        add(
                            RichEntity(
                                mediaIndexCodePoint,
                                mediaIndexCodePoint,
                                RichEntityContent.BlockImage(
                                    url = media.mediaUrlHttps,
                                    href =
                                        DeeplinkRoute.Media
                                            .Image(
                                                uri = media.mediaUrlHttps,
                                                previewUrl = media.mediaUrlHttps,
                                            ).toUri(),
                                ),
                            ),
                        )
                    }
                }
            }.sortedBy { it.start }

        val contents = mutableListOf<RenderContent>()
        val currentRuns = mutableListOf<RenderRun>()

        fun flushTextContent() {
            if (currentRuns.isEmpty()) return
            contents.add(RenderContent.Text(runs = currentRuns.toImmutableList()))
            currentRuns.clear()
        }

        fun appendText(
            text: String,
            style: RenderTextStyle = RenderTextStyle(),
        ) {
            if (text.isEmpty()) return
            val lastRun = currentRuns.lastOrNull()
            if (lastRun is RenderRun.Text && lastRun.style == style) {
                currentRuns[currentRuns.lastIndex] = lastRun.copy(text = lastRun.text + text)
            } else {
                currentRuns.add(RenderRun.Text(text = text, style = style))
            }
        }

        fun codePointText(
            start: Int,
            end: Int,
        ): String =
            codePoints
                .subList(start, end)
                .flatMap { it.toChars().toList() }
                .joinToString("")

        var current = 0
        sortedEntities.forEach { entity ->
            if (entity.start < current) return@forEach
            if (entity.start > current) {
                appendText(codePointText(current, entity.start))
            }
            when (val content = entity.content) {
                is RichEntityContent.BlockImage -> {
                    flushTextContent()
                    contents.add(
                        RenderContent.BlockImage(
                            url = content.url,
                            href = content.href,
                        ),
                    )
                }

                is RichEntityContent.Text -> appendText(content.run.text, content.run.style)
            }
            current = entity.end
        }

        if (current < codePoints.size) {
            appendText(codePointText(current, codePoints.size))
        }
        flushTextContent()
        return uiRichTextOf(contents)
    }
}

private sealed interface RichEntityContent {
    data class Text(
        val run: RenderRun.Text,
    ) : RichEntityContent

    data class BlockImage(
        val url: String,
        val href: String?,
    ) : RichEntityContent
}

private data class RichEntity(
    val start: Int,
    val end: Int,
    val content: RichEntityContent,
)

private fun List<Token>.toUiRichText(accountKey: MicroBlogKey): UiRichText {
    val runs =
        buildList<RenderRun> {
            this@toUiRichText.forEach { token ->
                when (token) {
                    is CashTagToken ->
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style =
                                    RenderTextStyle(
                                        link = DeeplinkRoute.Search(AccountType.Specific(accountKey), token.value).toUri(),
                                    ),
                            ),
                        )

                    is EmojiToken, is StringToken ->
                        add(
                            RenderRun.Text(
                                text = token.value,
                            ),
                        )

                    is HashTagToken ->
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style =
                                    RenderTextStyle(
                                        link = DeeplinkRoute.Search(AccountType.Specific(accountKey), token.value).toUri(),
                                    ),
                            ),
                        )

                    is UrlToken ->
                        add(
                            RenderRun.Text(
                                text = token.value.trimUrl(),
                                style =
                                    RenderTextStyle(
                                        link = token.value,
                                    ),
                            ),
                        )

                    is UserNameToken ->
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style =
                                    RenderTextStyle(
                                        link =
                                            DeeplinkRoute.Profile
                                                .UserNameWithHost(
                                                    accountType = AccountType.Specific(accountKey),
                                                    userName = token.value.trimStart('@'),
                                                    host = accountKey.host,
                                                ).toUri(),
                                    ),
                            ),
                        )
                }
            }
        }
    return uiRichTextOf(
        renderRuns =
            if (runs.isEmpty()) {
                emptyList()
            } else {
                listOf(RenderContent.Text(runs = runs.toImmutableList()))
            },
    )
}

private fun String.trimUrl(): String =
    this
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .removeSuffix("/")
        .let {
            if (it.length > 30) {
                it.substring(0, 30) + "..."
            } else {
                it
            }
        }

private fun renderRichText(
    text: String,
    entities: Entities?,
    accountKey: MicroBlogKey,
): UiRichText =
    twitterParser
        .parse(text)
        .map { token ->
            if (token is UrlToken) {
                val actual =
                    entities
                        ?.urls
                        ?.firstOrNull { it.url == token.value.trim() }
                        ?.expandedUrl
                if (actual != null) {
                    UrlToken(actual)
                } else {
                    token
                }
            } else {
                token
            }
        }.toUiRichText(accountKey)

private fun TwitterArticleResult.renderContent(accountKey: MicroBlogKey): UiRichText {
    val blocks = contentState?.blocks.orEmpty()
    if (blocks.isEmpty()) {
        val fallback = previewText?.trim().orEmpty()
        return fallback.toUiPlainText()
    }
    val entities = contentState?.entityMap.orEmpty()
    val mediaMap = mediaEntities.associateBy { it.mediaId }
    val contents = mutableListOf<RenderContent>()
    val rawBlocks = mutableListOf<String>()
    var orderedListIndex = 0

    blocks.forEach { block ->
        val normalizedType = block.type.lowercase()
        if (normalizedType == "ordered-list-item") {
            orderedListIndex += 1
        } else {
            orderedListIndex = 0
        }
        val renderContents =
            block.toRenderContents(
                entities = entities,
                mediaMap = mediaMap,
                accountKey = accountKey,
                orderedListIndex = orderedListIndex,
            )
        if (renderContents.isNotEmpty()) {
            contents += renderContents
        }
        if (block.text.isNotBlank()) {
            rawBlocks += block.text
        }
    }
    val innerText = rawBlocks.joinToString("\n")
    return uiRichTextOf(
        renderRuns = contents,
        raw = innerText,
        innerText = innerText,
    )
}

private fun TwitterArticleBlock.toRenderContents(
    entities: List<dev.dimension.flare.data.network.xqt.model.TwitterArticleEntityEntry>,
    mediaMap: Map<String?, TwitterArticleMedia>,
    accountKey: MicroBlogKey,
    orderedListIndex: Int,
): List<RenderContent> {
    if (type.equals("atomic", ignoreCase = true)) {
        val entityKey = entityRanges.firstOrNull()?.key ?: return emptyList()
        val entity = entities.entityValue(entityKey) ?: return emptyList()
        if (entity.type.equals("MEDIA", ignoreCase = true)) {
            val media =
                entity.data.mediaItems
                    .firstNotNullOfOrNull { mediaItem ->
                        mediaMap[mediaItem.mediaId]
                    } ?: return emptyList()
            val imageUrl = media.displayImageUrl() ?: return emptyList()
            return listOf(
                RenderContent.BlockImage(
                    url = imageUrl,
                    href = media.href(),
                ),
            )
        }
        return emptyList()
    }

    val blockStyle =
        when (type.lowercase()) {
            "header-one" -> RenderBlockStyle(headingLevel = 1)
            "header-two" -> RenderBlockStyle(headingLevel = 2)
            "header-three" -> RenderBlockStyle(headingLevel = 3)
            "header-four" -> RenderBlockStyle(headingLevel = 4)
            "header-five" -> RenderBlockStyle(headingLevel = 5)
            "header-six" -> RenderBlockStyle(headingLevel = 6)
            "blockquote" -> RenderBlockStyle(isBlockQuote = true)
            "unordered-list-item", "ordered-list-item" -> RenderBlockStyle(isListItem = true)
            else -> RenderBlockStyle()
        }

    val runs = mutableListOf<RenderRun>()
    if (type.equals("unordered-list-item", ignoreCase = true)) {
        runs += RenderRun.Text("\u2022 ")
    } else if (type.equals("ordered-list-item", ignoreCase = true)) {
        runs += RenderRun.Text("$orderedListIndex. ")
    }

    val boundaries = mutableSetOf(0, text.length)
    entityRanges.forEach {
        boundaries += it.offset
        boundaries += (it.offset + it.length).coerceAtMost(text.length)
    }
    inlineStyleRanges.forEach {
        boundaries += it.offset
        boundaries += (it.offset + it.length).coerceAtMost(text.length)
    }
    data.urls.forEach {
        it.fromIndex?.let(boundaries::add)
        it.toIndex?.let { end -> boundaries += end.coerceAtMost(text.length) }
    }

    boundaries
        .filter { it in 0..text.length }
        .sorted()
        .zipWithNext()
        .forEach { (start, end) ->
            if (start >= end) return@forEach
            val rawSegment = text.substring(start, end)
            if (rawSegment.isEmpty()) return@forEach
            val entity =
                entityRanges.firstOrNull { start >= it.offset && start < it.offset + it.length }?.let { range ->
                    entities.entityValue(range.key)
                }
            val link =
                when {
                    entity?.type.equals("LINK", ignoreCase = true) -> entity?.data?.url
                    else ->
                        data.urls
                            .firstOrNull { url ->
                                val from = url.fromIndex ?: return@firstOrNull false
                                val to = url.toIndex ?: return@firstOrNull false
                                start >= from && start < to
                            }?.text
                }
            runs +=
                RenderRun.Text(
                    text = rawSegment,
                    style =
                        RenderTextStyle(
                            link = link,
                            bold = inlineStyleRanges.hasStyle(start, "bold"),
                            italic = inlineStyleRanges.hasStyle(start, "italic"),
                            underline = inlineStyleRanges.hasStyle(start, "underline"),
                            monospace = inlineStyleRanges.hasStyle(start, "code"),
                            code = inlineStyleRanges.hasStyle(start, "code"),
                            strikethrough = inlineStyleRanges.hasStyle(start, "strikethrough"),
                        ),
                )
        }

    return if (runs.isEmpty()) {
        emptyList()
    } else {
        listOf(
            RenderContent.Text(
                runs = runs.toImmutableList(),
                block = blockStyle,
            ),
        )
    }
}

private fun List<TwitterArticleInlineStyleRange>.hasStyle(
    offset: Int,
    style: String,
): Boolean =
    any {
        offset >= it.offset &&
            offset < it.offset + it.length &&
            it.style.equals(style, ignoreCase = true)
    }

private fun List<dev.dimension.flare.data.network.xqt.model.TwitterArticleEntityEntry>.entityValue(key: Int): TwitterArticleEntity? =
    getOrNull(key)?.value ?: firstOrNull { it.key == key.toString() }?.value
