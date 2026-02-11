package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import de.cketti.codepoints.codePointCount
import de.cketti.codepoints.deluxe.codePointSequence
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.network.xqt.model.Admin
import dev.dimension.flare.data.network.xqt.model.AudioSpace
import dev.dimension.flare.data.network.xqt.model.Entities
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.Media
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddToModule
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTwitterList
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacy
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacyBindingValueData
import dev.dimension.flare.data.network.xqt.model.TwitterList
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val twitterParser by lazy {
    TwitterParser(enableNonAsciiInUrl = false)
}

internal fun TopLevel.renderNotifications(
    accountKey: MicroBlogKey,
    event: StatusEvent.XQT,
): List<UiTimeline> {
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
                        "person_icon" -> UiTimeline.TopMessage.Icon.Follow
                        "heart_icon" -> UiTimeline.TopMessage.Icon.Favourite
                        "bird_icon" -> UiTimeline.TopMessage.Icon.Info
                        else -> UiTimeline.TopMessage.Icon.Info
                    }
                val tweet =
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
                        ).renderStatus(accountKey, event, emptyMap())
                    }
                val itemContent =
                    when {
                        data?.icon?.id in listOf("person_icon") && users != null ->
                            if (users.size > 1) {
                                UiTimeline.ItemContent.UserList(
                                    users = users.toImmutableList(),
                                )
                            } else if (users.size == 1) {
                                UiTimeline.ItemContent.User(
                                    value = users.first(),
                                )
                            } else {
                                null
                            }

                        tweet != null -> tweet
                        else -> null
                    }

                UiTimeline(
                    topMessage =
                        UiTimeline.TopMessage(
                            user = null, // users?.firstOrNull(),
                            icon = icon,
                            type =
                                UiTimeline.TopMessage.MessageType.XQT
                                    .Custom(
                                        message = message.orEmpty(),
                                        id = notification.id ?: Uuid.random().toString(),
                                    ),
                            onClicked = {
                                if (itemContent == null && url != null) {
                                    if (url == "/2/notifications/device_follow.json") {
                                        launcher.launch(
                                            DeeplinkRoute.Timeline
                                                .XQTDeviceFollow(
                                                    accountType = AccountType.Specific(accountKey),
                                                ).toUri(),
                                        )
                                    } else if (!url.startsWith("/")) {
                                        launcher.launch(url)
                                    }
                                }
                            },
                            statusKey =
                                MicroBlogKey(
                                    id = notification.id.orEmpty(),
                                    host = accountKey.host,
                                ),
                        ),
                    content = itemContent,
                )
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
                    ).renderStatus(accountKey, event, emptyMap())
                UiTimeline(
                    topMessage =
                        UiTimeline.TopMessage(
                            user = renderedUser,
                            icon = UiTimeline.TopMessage.Icon.Retweet,
                            type = UiTimeline.TopMessage.MessageType.XQT.Mention,
                            onClicked = {
                                launcher.launch(
                                    DeeplinkRoute.Profile
                                        .User(
                                            accountType =
                                                dev.dimension.flare.model.AccountType
                                                    .Specific(accountKey),
                                            userKey = renderedUser.key,
                                        ).toUri(),
                                )
                            },
                            statusKey =
                                MicroBlogKey(
                                    id = notification?.id.orEmpty(),
                                    host = accountKey.host,
                                ),
                        ),
                    content = data,
                )
            } else {
                null
            }
        }?.toList()
        .orEmpty()
}

internal fun Tweet.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.XQT,
    references: Map<ReferenceType, List<StatusContent>> = emptyMap(),
): UiTimeline {
    val retweet =
        (references[ReferenceType.Retweet]?.firstOrNull() as? StatusContent.XQT)
            ?.data
            ?.renderStatus(
                accountKey = accountKey,
                event = event,
                references = mapOf(ReferenceType.Quote to references[ReferenceType.Quote].orEmpty()),
            )
    val currentTweet = renderStatus(accountKey, event, references)
    val actualTweet = retweet ?: currentTweet
    val user = currentTweet.user
    val topMessage =
        if (retweet != null && user != null) {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Retweet,
                type = UiTimeline.TopMessage.MessageType.XQT.Retweet,
                onClicked = {
                    launcher.launch(
                        DeeplinkRoute.Profile
                            .User(
                                accountType =
                                    dev.dimension.flare.model.AccountType
                                        .Specific(accountKey),
                                userKey = user.key,
                            ).toUri(),
                    )
                },
                statusKey = currentTweet.statusKey,
            )
        } else {
            null
        }
    return UiTimeline(
        content = actualTweet,
        topMessage = topMessage,
    )
}

internal fun Tweet.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.XQT,
    references: Map<ReferenceType, List<StatusContent>>,
): UiTimeline.ItemContent.Status {
    val parents =
        references[ReferenceType.Reply]
            ?.mapNotNull { it as? StatusContent.XQT }
            ?.map { it.data.renderStatus(accountKey, event, emptyMap()) }
            ?.toImmutableList()
            ?: persistentListOf()
    val quote =
        (references[ReferenceType.Quote]?.firstOrNull() as? StatusContent.XQT)
            ?.data
            ?.renderStatus(accountKey = accountKey, event = event, references = emptyMap())
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
        card?.legacy?.let {
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
                val max =
                    (1..4).sumOf { index ->
                        cardLegacy.get("choice${index}_count")?.stringValue?.toLong() ?: 0
                    }
                val options =
                    (1..4)
                        .mapNotNull { index ->
                            val count =
                                cardLegacy.get("choice${index}_count")?.stringValue?.toLong() ?: 0
                            cardLegacy.get("choice${index}_label")?.stringValue?.let {
                                UiPoll.Option(
                                    title = it,
                                    votesCount = count,
                                    percentage = count.toFloat() / max.coerceAtLeast(1).toFloat(),
                                )
                            }
                        }.toImmutableList()
                UiPoll(
                    // xqt dose not have id
                    id = "",
                    options = options,
                    multiple = false,
                    ownVotes = persistentListOf(),
                    expiresAt =
                        cardLegacy.get("end_datetime_utc")?.stringValue?.let {
                            parseXQTCustomDateTime(
                                it,
                            )
                        }
                            ?: Clock.System.now(),
                    onVote = { options -> },
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

    val aboveTextContent =
        legacy?.in_reply_to_screen_name?.let { screenName ->
            UiTimeline.ItemContent.Status.AboveTextContent.ReplyTo(
                handle = "@$screenName",
            )
        }

    val isFromMe = user?.key == accountKey
    val createAt =
        legacy?.createdAt?.let { parseXQTCustomDateTime(it) } ?: Clock.System.now()
    val statusKey =
        MicroBlogKey(
            id = legacy?.idStr ?: restId,
            host = accountKey.host,
        )
    val url =
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

    return UiTimeline.ItemContent.Status(
        statusKey = statusKey,
        user = user,
        content = content,
        card = uiCard,
        quote = listOfNotNull(quote).toImmutableList(),
        poll = poll,
        images = medias,
        contentWarning = null,
        createdAt = createAt.toUi(),
        aboveTextContent = aboveTextContent,
        parents = parents,
        actions =
            listOfNotNull(
                ActionMenu.Item(
                    icon = ActionMenu.Item.Icon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(legacy?.replyCount?.toLong() ?: 0),
                    onClicked = {
                        launcher.launch(
                            DeeplinkRoute.Compose
                                .Reply(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ).toUri(),
                        )
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = if (legacy?.retweeted == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                            text =
                                ActionMenu.Item.Text.Localized(
                                    if (legacy?.retweeted ==
                                        true
                                    ) {
                                        ActionMenu.Item.Text.Localized.Type.Unretweet
                                    } else {
                                        ActionMenu.Item.Text.Localized.Type.Retweet
                                    },
                                ),
                            count = UiNumber(legacy?.retweetCount?.toLong() ?: 0),
                            color = if (legacy?.retweeted == true) ActionMenu.Item.Color.PrimaryColor else null,
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon = if (legacy?.retweeted == true) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                                text =
                                    ActionMenu.Item.Text.Localized(
                                        if (legacy?.retweeted ==
                                            true
                                        ) {
                                            ActionMenu.Item.Text.Localized.Type.Unretweet
                                        } else {
                                            ActionMenu.Item.Text.Localized.Type.Retweet
                                        },
                                    ),
                                count = UiNumber(legacy?.retweetCount?.toLong() ?: 0),
                                color = if (legacy?.retweeted == true) ActionMenu.Item.Color.PrimaryColor else null,
                                onClicked = {
                                    event.retweet(statusKey, legacy?.retweeted ?: false)
                                },
                            ),
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Quote,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                count = UiNumber(legacy?.quoteCount?.toLong() ?: 0),
                                onClicked = {
                                    launcher.launch(
                                        DeeplinkRoute.Compose
                                            .Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ).toUri(),
                                    )
                                },
                            ),
                        ).toImmutableList(),
                ),
                ActionMenu.Item(
                    icon = if (legacy?.favorited == true) ActionMenu.Item.Icon.Unlike else ActionMenu.Item.Icon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (legacy?.favorited ==
                                true
                            ) {
                                ActionMenu.Item.Text.Localized.Type.Unlike
                            } else {
                                ActionMenu.Item.Text.Localized.Type.Like
                            },
                        ),
                    count = UiNumber(legacy?.favoriteCount?.toLong() ?: 0),
                    color = if (legacy?.favorited == true) ActionMenu.Item.Color.Red else null,
                    onClicked = {
                        event.like(statusKey, legacy?.favorited ?: false)
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        buildList {
                            add(
                                ActionMenu.Item(
                                    icon =
                                        if (legacy?.bookmarked ==
                                            true
                                        ) {
                                            ActionMenu.Item.Icon.Unbookmark
                                        } else {
                                            ActionMenu.Item.Icon.Bookmark
                                        },
                                    text =
                                        ActionMenu.Item.Text.Localized(
                                            if (legacy?.bookmarked ==
                                                true
                                            ) {
                                                ActionMenu.Item.Text.Localized.Type.Unbookmark
                                            } else {
                                                ActionMenu.Item.Text.Localized.Type.Bookmark
                                            },
                                        ),
                                    count = UiNumber(legacy?.bookmarkCount?.toLong() ?: 0),
                                    onClicked = {
                                        event.bookmark(statusKey, legacy?.bookmarked ?: false)
                                    },
                                ),
                            )
                            add(
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Share,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                    onClicked = {
                                        launcher.launch(
                                            DeeplinkRoute.Status
                                                .ShareSheet(
                                                    statusKey = statusKey,
                                                    accountType = AccountType.Specific(accountKey),
                                                    shareUrl = url,
                                                    fxShareUrl = fxUrl,
                                                    fixvxShareUrl = fixvxUrl,
                                                ).toUri(),
                                        )
                                    },
                                ),
                            )

                            if (isFromMe) {
                                add(
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Delete,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                        color = ActionMenu.Item.Color.Red,
                                        onClicked = {
                                            launcher.launch(
                                                DeeplinkRoute.Status
                                                    .DeleteConfirm(
                                                        statusKey = statusKey,
                                                        accountType =
                                                            dev.dimension.flare.model.AccountType
                                                                .Specific(accountKey),
                                                    ).toUri(),
                                            )
                                        },
                                    ),
                                )
                            } else {
                                if (user != null) {
                                    add(ActionMenu.Divider)
                                    addAll(
                                        userActionsMenu(
                                            accountKey = accountKey,
                                            userKey = user.key,
                                            handle = user.handle,
                                        ),
                                    )
                                    add(ActionMenu.Divider)
                                }
                                add(
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Report,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                        color = ActionMenu.Item.Color.Red,
                                        onClicked = {
                                            // TODO: implement report
                                        },
                                    ),
                                )
                            }
                        }.toImmutableList(),
                ),
            ).toImmutableList(),
        sensitive = legacy?.possiblySensitive == true,
        onClicked = {
            launcher.launch(
                DeeplinkRoute.Status
                    .Detail(
                        statusKey = statusKey,
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
                    ).toUri(),
            )
        },
        platformType = PlatformType.xQt,
        onMediaClicked = { media, index ->
            launcher.launch(
                DeeplinkRoute.Media
                    .StatusMedia(
                        statusKey = statusKey,
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
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
        url = url,
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
        nameInternal =
            Element("span")
                .apply {
                    addChildren(TextNode(name))
                }.toUi(),
        handle = "@$screenName@${accountKey.host}",
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
                    }.toHtml(accountKey)
                    .toUi()
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
            if (legacy.location != null || legacy.url != null) {
                UiProfile.BottomContent.Iconify(
                    items =
                        listOfNotNull(
                            if (!legacy.location.isNullOrEmpty()) {
                                UiProfile.BottomContent.Iconify.Icon.Location to
                                    Element("span")
                                        .apply {
                                            addChildren(TextNode(legacy.location))
                                        }.toUi()
                            } else {
                                null
                            },
                            if (!legacy.url.isNullOrEmpty()) {
                                val actualUrl =
                                    legacy.entities
                                        ?.url
                                        ?.urls
                                        ?.firstOrNull { it.url == legacy.url }
                                val displayUrl = actualUrl?.displayUrl ?: legacy.url
                                val url = actualUrl?.expandedUrl ?: legacy.url
                                UiProfile.BottomContent.Iconify.Icon.Url to
                                    Element("a")
                                        .apply {
                                            addChildren(TextNode(displayUrl))
                                            attributes().put("href", url)
                                        }.toUi()
                            } else {
                                null
                            },
                            if (legacy.verified) {
                                UiProfile.BottomContent.Iconify.Icon.Verify to Element("span").toUi()
                            } else {
                                null
                            },
                        ).toMap().toPersistentMap(),
                )
            } else {
                null
            },
        platformType = PlatformType.xQt,
        onClicked = {
            launcher.launch(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
                        userKey = userKey,
                    ).toUri(),
            )
        },
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

    val parts = dateTimeStr.split(" ")
    if (parts.size != 6) return null

    try {
        val month = months[parts[1]] ?: return null
        val day = parts[2].toInt()
        val timeParts = parts[3].split(":").map { it.toInt() }
        val year = parts[5].toInt()

        val hour = timeParts[0]
        val minute = timeParts[1]
        val second = timeParts[2]

        // Assuming the timezone is always in the format of +HHMM or -HHMM
//        val timezoneOffset = parts[4]
//        val offsetHours = timezoneOffset.substring(1, 3).toInt()
//        val offsetMinutes = timezoneOffset.substring(3, 5).toInt()
//        val totalOffsetMinutes = offsetHours * 60 + offsetMinutes
//        val offsetSign = if (timezoneOffset.startsWith("+")) 1 else -1

        val dateTime = LocalDateTime(year, month, day, hour, minute, second)
        return dateTime.toInstant(TimeZone.UTC)
    } catch (e: Exception) {
        return null
    }
}

internal fun List<InstructionUnion>.list(accountKey: MicroBlogKey): List<UiList.List> =
    flatMap {
        when (it) {
            is TimelineAddEntries ->
                it.propertyEntries.flatMap {
                    when (it.content) {
                        is TimelineTimelineModule ->
                            it.content.items.orEmpty().mapNotNull {
                                when (it.item.itemContent) {
                                    is TimelineTwitterList -> it.item.itemContent.list
                                    else -> null
                                }
                            }

                        else -> emptyList()
                    }
                }

            is TimelineAddToModule ->
                it.moduleItems.flatMap {
                    when (it.item.itemContent) {
                        is TimelineTwitterList -> listOfNotNull(it.item.itemContent.list)
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

internal fun MessageContent.XQT.render(
    accountKey: MicroBlogKey,
    credential: UiAccount.Credential,
    statusEvent: StatusEvent,
): UiDMItem.Message =
    when (this) {
        is MessageContent.XQT.Message -> render(accountKey, credential, statusEvent)
    }

private fun MessageContent.XQT.Message.render(
    accountKey: MicroBlogKey,
    credential: UiAccount.Credential,
    statusEvent: StatusEvent,
): UiDMItem.Message {
    if (!data.attachment
            ?.photo
            ?.url
            .isNullOrEmpty() &&
        data.text
            .orEmpty()
            .endsWith(data.attachment.photo.url) &&
        !data.attachment.photo.mediaUrlHttps
            .isNullOrEmpty() &&
        credential is UiAccount.XQT.Credential
    ) {
        return UiDMItem.Message.Media(
            UiMedia.Image(
                url = data.attachment.photo.mediaUrlHttps,
                previewUrl = data.attachment.photo.mediaUrlHttps,
                height =
                    data.attachment.photo.originalInfo
                        ?.height
                        ?.toFloat() ?: 0f,
                width =
                    data.attachment.photo.originalInfo
                        ?.width
                        ?.toFloat() ?: 0f,
                sensitive = false,
                description = data.attachment.photo.extAltText,
                customHeaders =
                    persistentMapOf(
                        "Cookie" to credential.chocolate,
                        "Referer" to "https://${accountKey.host}/",
                    ),
            ),
        )
    } else if (!data.attachment
            ?.animatedGif
            ?.url
            .isNullOrEmpty() &&
        data.text
            .orEmpty()
            .endsWith(data.attachment.animatedGif.url) &&
        !data.attachment.animatedGif.mediaUrlHttps
            .isNullOrEmpty() &&
        credential is UiAccount.XQT.Credential
    ) {
        return UiDMItem.Message.Media(
            UiMedia.Gif(
                url = data.attachment.animatedGif.mediaUrlHttps,
                previewUrl = data.attachment.animatedGif.mediaUrlHttps,
                height =
                    data.attachment.animatedGif.originalInfo
                        ?.height
                        ?.toFloat() ?: 0f,
                width =
                    data.attachment.animatedGif.originalInfo
                        ?.width
                        ?.toFloat() ?: 0f,
                description = data.attachment.animatedGif.extAltText,
                customHeaders =
                    persistentMapOf(
                        "Cookie" to credential.chocolate,
                        "Referer" to "https://${accountKey.host}/",
                    ),
            ),
        )
    } else if (!data.attachment
            ?.video
            ?.url
            .isNullOrEmpty() &&
        data.text
            .orEmpty()
            .endsWith(data.attachment.video.url) &&
        !data.attachment.video.mediaUrlHttps
            .isNullOrEmpty() &&
        credential is UiAccount.XQT.Credential
    ) {
        val url =
            data.attachment.video.videoInfo
                ?.variants
                ?.firstOrNull()
                ?.url
        if (url != null) {
            if (data.attachment.video.audioOnly == true) {
                return UiDMItem.Message.Media(
                    UiMedia.Audio(
                        url = url,
                        previewUrl = data.attachment.video.url,
                        description = data.attachment.video.extAltText,
                        customHeaders =
                            persistentMapOf(
                                "Cookie" to credential.chocolate,
                                "Referer" to "https://${accountKey.host}/",
                            ),
                    ),
                )
            } else {
                return UiDMItem.Message.Media(
                    UiMedia.Video(
                        url = url,
                        thumbnailUrl = data.attachment.video.mediaUrlHttps,
                        height =
                            data.attachment.video.originalInfo
                                ?.height
                                ?.toFloat() ?: 0f,
                        width =
                            data.attachment.video.originalInfo
                                ?.width
                                ?.toFloat() ?: 0f,
                        description = data.attachment.video.extAltText,
                        customHeaders =
                            persistentMapOf(
                                "Cookie" to credential.chocolate,
                                "Referer" to "https://${accountKey.host}/",
                            ),
                    ),
                )
            }
        } else {
            return UiDMItem.Message.Text(
                twitterParser.parse(this.data.text.orEmpty()).toHtml(accountKey).toUi(),
            )
        }
    } else if (!data.attachment
            ?.tweet
            ?.url
            .isNullOrEmpty() &&
        data.text
            .orEmpty()
            .endsWith(data.attachment.tweet.url) &&
        data.attachment.tweet.status != null &&
        statusEvent is StatusEvent.XQT
    ) {
        val tweetLegacy = data.attachment.tweet.status
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
            ).renderStatus(accountKey, statusEvent, emptyMap())
        return UiDMItem.Message.Status(
            status = status,
        )
    } else {
        return UiDMItem.Message.Text(
            twitterParser.parse(this.data.text.orEmpty()).toHtml(accountKey).toUi(),
        )
    }
}

internal fun UserResults.render(accountKey: MicroBlogKey): UiProfile? =
    when (result) {
        is User -> result.render(accountKey)
        is UserUnavailable -> null
        null -> null
    }

internal fun AudioSpace.render(
    accountKey: MicroBlogKey,
    url: String?,
) = UiPodcast(
    id = metadata?.restID ?: throw Exception("No ID"),
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

private fun Admin.render(accountKey: MicroBlogKey): UiUserV2 {
    val key =
        MicroBlogKey(
            id = userResults?.restID ?: throw Exception("No ID"),
            host = accountKey.host,
        )
    return UiProfile(
        key = key,
        avatar = avatarURL?.replaceWithOriginImageUrl().orEmpty(),
        nameInternal =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@$twitterScreenName@${accountKey.host}",
        platformType = PlatformType.xQt,
        onClicked = {
            launcher.launch(
                DeeplinkRoute.Profile
                    .User(
                        accountType =
                            dev.dimension.flare.model.AccountType
                                .Specific(accountKey),
                        userKey = key,
                    ).toUri(),
            )
        },
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

internal fun Tweet.renderContent(accountKey: MicroBlogKey): UiRichText {
    if (noteTweet == null) {
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

        val tokens = mutableListOf<Node>()
        val sortedEntities =
            buildList {
                entities.hashtags?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        Entity(
                            it.indices[0],
                            it.indices[1],
                            Element("a").apply {
                                attributes().put(
                                    "href",
                                    DeeplinkRoute
                                        .Search(
                                            dev.dimension.flare.model.AccountType
                                                .Specific(accountKey),
                                            "#${it.text}",
                                        ).toUri(),
                                )
                                addChildren(TextNode("#${it.text ?: ""}"))
                            },
                        ),
                    )
                }
                entities.urls?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        Entity(
                            it.indices[0],
                            it.indices[1],
                            Element("a").apply {
                                attributes().put("href", it.expandedUrl ?: it.url)
                                addChildren(
                                    TextNode(
                                        it.displayUrl ?: (it.expandedUrl ?: it.url).trimUrl(),
                                    ),
                                )
                            },
                        ),
                    )
                }
                entities.userMentions?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        Entity(
                            it.indices[0],
                            it.indices[1],
                            Element("a").apply {
                                attributes().put(
                                    "href",
                                    DeeplinkRoute.Profile
                                        .UserNameWithHost(
                                            dev.dimension.flare.model.AccountType
                                                .Specific(accountKey),
                                            it.screenName ?: "",
                                            accountKey.host,
                                        ).toUri(),
                                )
                                addChildren(TextNode("@${it.screenName ?: ""}"))
                            },
                        ),
                    )
                }
                entities.symbols?.filter { it.indices.size >= 2 }?.forEach {
                    add(
                        Entity(
                            it.indices[0],
                            it.indices[1],
                            Element("a").apply {
                                attributes().put(
                                    "href",
                                    DeeplinkRoute
                                        .Search(
                                            dev.dimension.flare.model.AccountType
                                                .Specific(accountKey),
                                            "$${it.text}",
                                        ).toUri(),
                                )
                                addChildren(TextNode("$${it.text ?: ""}"))
                            },
                        ),
                    )
                }
                result.richtext?.richtextTags?.forEach { tag ->
                    val str =
                        codePoints
                            .subList(text.codePointCount(0, tag.fromIndex), text.codePointCount(0, tag.toIndex))
                            .flatMap { it.toChars().toList() }
                            .joinToString("")
                    var node: Node = TextNode(str)
                    if (tag.richtextTypes.any {
                            it == dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichTextTag.RichtextTypes.bold
                        }
                    ) {
                        node = Element("b").apply { appendChild(node) }
                    }
                    if (tag.richtextTypes.any {
                            it == dev.dimension.flare.data.network.xqt.model.NoteTweetResultRichTextTag.RichtextTypes.italic
                        }
                    ) {
                        node = Element("i").apply { appendChild(node) }
                    }
                    add(Entity(text.codePointCount(0, tag.fromIndex), text.codePointCount(0, tag.toIndex), node))
                }
                result.media?.inlineMedia?.forEachIndexed { index, inlineMedia ->
                    val media = legacy?.entities?.media?.firstOrNull { it.idStr == inlineMedia.mediaId }
                    if (media != null) {
                        val node =
                            Element("figure").apply {
                                appendChild(
                                    Element("img").apply {
                                        attributes().apply {
                                            put("src", media.mediaUrlHttps)
                                            put(
                                                "href",
                                                DeeplinkRoute.Media
                                                    .Image(
                                                        uri = media.mediaUrlHttps,
                                                        previewUrl = media.mediaUrlHttps,
                                                    ).toUri(),
                                            )
                                        }
                                    },
                                )
                            }
                        val mediaIndexCodePoint = text.codePointCount(0, inlineMedia.index)
                        add(Entity(mediaIndexCodePoint, mediaIndexCodePoint, node))
                    }
                }
            }.sortedBy { it.start }

        var current = 0
        sortedEntities.forEach { entity ->
            if (entity.start < current) return@forEach // Skip overlapping entities
            if (entity.start > current) {
                val str =
                    codePoints
                        .subList(current, entity.start)
                        .flatMap { it.toChars().toList() }
                        .joinToString("")
                str.split("\n").forEachIndexed { index, s ->
                    tokens.add(TextNode(s))
                    if (index != str.split("\n").lastIndex) {
                        tokens.add(Element("br"))
                    }
                }
            }
            tokens.add(entity.node)
            current = entity.end
        }

        if (current < codePoints.size) {
            val str =
                codePoints
                    .subList(current, codePoints.size)
                    .flatMap { it.toChars().toList() }
                    .joinToString("")
            str.split("\n").forEachIndexed { index, s ->
                tokens.add(TextNode(s))
                if (index != str.split("\n").lastIndex) {
                    tokens.add(Element("br"))
                }
            }
        }

        return Element("body")
            .apply {
                tokens.forEach { appendChild(it) }
            }.toUi()
    }
}

private data class Entity(
    val start: Int,
    val end: Int,
    val node: Node,
)

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
        }.toHtml(accountKey)
        .toUi()
