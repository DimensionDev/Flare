package dev.dimension.flare.ui.model.mapper

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import de.cketti.codepoints.deluxe.codePointSequence
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.xqt.model.Admin
import dev.dimension.flare.data.network.xqt.model.AudioSpace
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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPodcast
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.toUi
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
                            ?.users
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
                                    launcher.launch(url)
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
                                    AppDeepLink.Profile(
                                        accountKey = accountKey,
                                        userKey = renderedUser.key,
                                    ),
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
                        AppDeepLink.Profile(
                            accountKey = accountKey,
                            userKey = user.key,
                        ),
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
                            AppDeepLink
                                .Podcast(
                                    accountKey = accountKey,
                                    id = id,
                                ).toString(),
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
            }?.toImmutableList() ?: persistentListOf()
    val text =
        noteTweet?.noteTweetResults?.result?.text
            ?: legacy
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
    val content =
        twitterParser
            .parse(text)
            .map { token ->
                if (token is UrlToken) {
                    val actual =
                        legacy
                            ?.entities
                            ?.urls
                            ?.firstOrNull { it.url == token.value.trim() }
                            ?.expandedUrl
                            ?: noteTweet
                                ?.noteTweetResults
                                ?.result
                                ?.entitySet
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

    return UiTimeline.ItemContent.Status(
        statusKey = statusKey,
        user = user,
        content = content.toUi(),
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
                StatusAction.Item.Reply(
                    count = legacy?.replyCount?.toLong() ?: 0,
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
                    },
                ),
                StatusAction.Group(
                    displayItem =
                        StatusAction.Item.Retweet(
                            count = legacy?.retweetCount?.toLong() ?: 0,
                            retweeted = legacy?.retweeted ?: false,
                            onClicked = {
                            },
                        ),
                    actions =
                        listOfNotNull(
                            StatusAction.Item.Retweet(
                                count = legacy?.retweetCount?.toLong() ?: 0,
                                retweeted = legacy?.retweeted ?: false,
                                onClicked = {
                                    event.retweet(statusKey, legacy?.retweeted ?: false)
                                },
                                onClickedWithState = { shouldRetweet ->
                                    event.retweetWithResult(statusKey, shouldRetweet)
                                },
                            ),
                            StatusAction.Item.Quote(
                                count = 0,
                                onClicked = {
                                    launcher.launch(
                                        AppDeepLink.Compose.Quote(
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ),
                                    )
                                },
                            ),
                        ).toImmutableList(),
                ),
                StatusAction.Item.Like(
                    count = legacy?.favoriteCount?.toLong() ?: 0,
                    liked = legacy?.favorited ?: false,
                    onClicked = {
                        event.like(statusKey, legacy?.favorited ?: false)
                    },
                    onClickedWithState = { shouldLike ->
                        event.likeWithResult(statusKey, shouldLike)
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            StatusAction.Item.Bookmark(
                                count = legacy?.bookmarkCount?.toLong() ?: 0,
                                bookmarked = legacy?.bookmarked ?: false,
                                onClicked = {
                                    event.bookmark(statusKey, legacy?.bookmarked ?: false)
                                },
                                onClickedWithState = { shouldBookmark ->
                                    event.bookmarkWithResult(statusKey, shouldBookmark)
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
                                        // TODO: implement report
                                    },
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        sensitive = legacy?.possiblySensitive == true,
        onClicked = {
            launcher.launch(
                AppDeepLink.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
        platformType = PlatformType.xQt,
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
        url =
            buildString {
                append("https://${accountKey.host}/")
                append(user?.handleWithoutAt)
                append("/status/")
                append(legacy?.idStr ?: restId)
            },
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
        name =
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
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
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

internal fun List<InstructionUnion>.list(accountKey: MicroBlogKey): List<UiList> =
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

internal fun TwitterList.render(accountKey: MicroBlogKey): UiList {
    val user =
        userResults?.result?.let {
            when (it) {
                is User -> it.render(accountKey = accountKey)
                else -> null
            }
        }
    return UiList(
        id = idStr.orEmpty(),
        title = name.orEmpty(),
        description = description.orEmpty(),
        platformType = PlatformType.xQt,
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
        name =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@$twitterScreenName@${accountKey.host}",
        platformType = PlatformType.xQt,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey, key))
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
