package dev.dimension.flare.ui.model.mapper

import de.cketti.codepoints.deluxe.codePointSequence
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.Media
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacy
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacyBindingValueData
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Text
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken

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
                    data?.template?.aggregateUserActionsV1?.targetObjects?.mapNotNull {
                        globalObjects?.tweets?.get(it.tweet?.id)
                    }
                val createdAt =
                    data?.timestampMS?.toLongOrNull()?.let {
                        Instant.fromEpochMilliseconds(it)
                    }
                val messageType =
                    when (data?.icon?.id) {
                        "person_icon" -> UiTimeline.TopMessage.MessageType.XQT.Follow
                        "heart_icon" -> UiTimeline.TopMessage.MessageType.XQT.Like
                        "bird_icon" -> UiTimeline.TopMessage.MessageType.XQT.Logo
                        else ->
                            UiTimeline.TopMessage.MessageType.XQT
                                .Custom(message = message.orEmpty())
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
                                                    restId = it.userIdStr,
                                                ),
                                        ),
                                ),
                            legacy = it,
                        ).renderStatus(accountKey, event)
                    }
                val itemContent =
                    when {
                        messageType in listOf(UiTimeline.TopMessage.MessageType.XQT.Follow) && users != null ->
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
                            user = users?.firstOrNull(),
                            icon = UiTimeline.TopMessage.Icon.Retweet,
                            type = messageType,
                        ),
                    content = itemContent,
                    platformType = PlatformType.xQt,
                )
            } else if (mentionTweet != null) {
                val tweet = globalObjects?.tweets?.get(mentionTweet.id) ?: return@mapNotNull null
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
                    ).renderStatus(accountKey, event)
                UiTimeline(
                    topMessage =
                        UiTimeline.TopMessage(
                            user = renderedUser,
                            icon = UiTimeline.TopMessage.Icon.Retweet,
                            type = UiTimeline.TopMessage.MessageType.XQT.Mention,
                        ),
                    content = data,
                    platformType = PlatformType.xQt,
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
): UiTimeline {
    val retweet =
        legacy
            ?.retweetedStatusResult
            ?.result
            ?.let {
                when (it) {
                    is Tweet -> it
                    is TweetTombstone -> null
                    is TweetWithVisibilityResults -> it.tweet
                }
            }
    val actualTweet = retweet ?: this
    val user = renderStatus(accountKey, event).user
    val topMessage =
        if (retweet != null && user != null) {
            UiTimeline.TopMessage(
                user = user,
                icon = UiTimeline.TopMessage.Icon.Retweet,
                type = UiTimeline.TopMessage.MessageType.XQT.Retweet,
            )
        } else {
            null
        }
    return UiTimeline(
        content = actualTweet.renderStatus(accountKey = accountKey, event = event),
        topMessage = topMessage,
        platformType = PlatformType.xQt,
    )
}

internal fun Tweet.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.XQT,
): UiTimeline.ItemContent.Status {
    val quote =
        quotedStatusResult
            ?.result
            ?.let {
                when (it) {
                    is Tweet -> it
                    is TweetTombstone -> null
                    is TweetWithVisibilityResults -> it.tweet
                }
            }?.renderStatus(accountKey = accountKey, event = event)
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
                        cardLegacy.get("end_datetime_utc")?.stringValue?.let { parseCustomDateTime(it) }
                            ?: Clock.System.now(),
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

    val isFromMe = user?.key == accountKey
    val createAt =
        legacy?.createdAt?.let { parseCustomDateTime(it) } ?: Clock.System.now()
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
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = legacy?.replyCount?.toLong() ?: 0,
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
                            ),
                            StatusAction.Item.Quote(
                                count = 0,
                            ),
                        ).toImmutableList(),
                ),
                StatusAction.Item.Like(
                    count = legacy?.favoriteCount?.toLong() ?: 0,
                    liked = legacy?.favorited ?: false,
                    onClicked = {
                        event.like(statusKey, legacy?.favorited ?: false)
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
    )
}

internal fun User.render(accountKey: MicroBlogKey) =
    UiUserV2(
        key =
            MicroBlogKey(
                id = restId,
                host = xqtHost,
            ),
        avatar = legacy.profileImageUrlHttps.replaceWithOriginImageUrl(),
        name =
            Element("span")
                .apply {
                    children.add(Text(legacy.name))
                }.toUi(),
        handle = "@${legacy.screenName}@$xqtHost",
    )

internal fun Tweet.toUi(accountKey: MicroBlogKey): UiStatus.XQT {
    val retweet =
        legacy
            ?.retweetedStatusResult
            ?.result
            ?.let {
                when (it) {
                    is Tweet -> it
                    is TweetTombstone -> null
                    is TweetWithVisibilityResults -> it.tweet
                }
            }?.toUi(accountKey = accountKey)
    val quote =
        quotedStatusResult
            ?.result
            ?.let {
                when (it) {
                    is Tweet -> it
                    is TweetTombstone -> null
                    is TweetWithVisibilityResults -> it.tweet
                }
            }?.toUi(accountKey = accountKey)
    val user =
        core
            ?.userResults
            ?.result
            ?.let {
                when (it) {
                    is User -> it
                    is UserUnavailable -> null
                }
            }?.toUi(accountKey = accountKey)
    requireNotNull(user) { "user is null" }
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
                        cardLegacy.get("end_datetime_utc")?.stringValue?.let { parseCustomDateTime(it) }
                            ?: Clock.System.now(),
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
    return UiStatus.XQT(
        accountKey = accountKey,
        statusKey =
            MicroBlogKey(
                id = legacy?.idStr ?: restId,
                host = accountKey.host,
            ),
        user = user,
        createdAt = legacy?.createdAt?.let { parseCustomDateTime(it) } ?: Clock.System.now(),
        content = text,
        medias = medias,
        card = uiCard,
        matrices =
            UiStatus.XQT.Matrices(
                replyCount = legacy?.replyCount?.toLong() ?: 0,
                likeCount = legacy?.favoriteCount?.toLong() ?: 0,
                retweetCount = legacy?.retweetCount?.toLong() ?: 0,
            ),
        reaction =
            UiStatus.XQT.Reaction(
                liked = legacy?.favorited ?: false,
                retweeted = legacy?.retweeted ?: false,
                bookmarked = legacy?.bookmarked ?: false,
            ),
        retweet = retweet,
        quote = quote,
        inReplyToScreenName = legacy?.in_reply_to_screen_name,
        inReplyToStatusId = legacy?.in_reply_to_status_id_str,
        inReplyToUserId = legacy?.in_reply_to_user_id_str,
        poll = poll,
        sensitive = legacy?.possiblySensitive == true,
        raw = this,
    )
}

private fun TweetCardLegacy.get(key: String): TweetCardLegacyBindingValueData? = bindingValues.firstOrNull { it.key == key }?.value

internal fun User.toUi(accountKey: MicroBlogKey) =
    UiUser.XQT(
        userKey =
            MicroBlogKey(
                id = restId,
                host = xqtHost,
            ),
        displayName = legacy.name,
        rawHandle = legacy.screenName,
        avatarUrl = legacy.profileImageUrlHttps.replaceWithOriginImageUrl(),
        bannerUrl = legacy.profileBannerUrl,
        description = legacy.description?.takeIf { it.isNotEmpty() },
        matrices =
            UiUser.XQT.Matrices(
                fansCount = legacy.followersCount.toLong(),
                followsCount = legacy.friendsCount.toLong(),
                statusesCount = legacy.statusesCount.toLong(),
            ),
        verifyType =
            when {
                isBlueVerified && legacy.verifiedType != null -> UiUser.XQT.VerifyType.Company
                isBlueVerified -> UiUser.XQT.VerifyType.Money
                else -> null
            },
        location = legacy.location?.takeIf { it.isNotEmpty() },
        url =
            legacy.url?.takeIf { it.isNotEmpty() }?.let { url ->
                legacy.entities.url
                    ?.urls
                    ?.firstOrNull { it.url == url }
                    ?.expandedUrl
            } ?: legacy.url,
        protected = legacy.protected ?: false,
        raw = this,
        accountKey = accountKey,
    )

internal fun GetProfileSpotlightsQuery200Response.toUi(muting: Boolean): UiRelation {
    with(data.userResultByScreenName.result.legacy) {
        return UiRelation.XQT(
            following = following ?: false,
            isFans = followedBy ?: false,
            blocking = blocking ?: false,
            blockedBy = blockedBy ?: false,
            protected = protected ?: false,
            muting = muting,
        )
    }
}

private fun String.replaceWithOriginImageUrl() = this.replace("_normal.", ".")

private fun parseCustomDateTime(dateTimeStr: String): Instant? {
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
