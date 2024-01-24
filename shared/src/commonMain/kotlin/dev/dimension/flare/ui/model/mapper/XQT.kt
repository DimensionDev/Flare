package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.Media
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacy
import dev.dimension.flare.data.network.xqt.model.TweetCardLegacyBindingValueData
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiPoll
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

fun Tweet.toUi(accountKey: MicroBlogKey): UiStatus.XQT {
    val retweet =
        legacy?.retweetedStatusResult?.result?.let {
            when (it) {
                is Tweet -> it
                is TweetTombstone -> null
                is TweetWithVisibilityResults -> it.tweet
            }
        }?.toUi(accountKey = accountKey)
    val quote =
        quotedStatusResult?.result?.let {
            when (it) {
                is Tweet -> it
                is TweetTombstone -> null
                is TweetWithVisibilityResults -> it.tweet
            }
        }?.toUi(accountKey = accountKey)
    val user =
        core?.userResults?.result?.let {
            when (it) {
                is User -> it
                is UserUnavailable -> null
            }
        }?.toUi()
    requireNotNull(user)
    val uiCard =
        card?.legacy?.let {
            val title = it.get("title")?.stringValue
            val image = it.get("photo_image_full_size_original")?.imageValue
            val description = it.get("description")?.stringValue
            val cardUrl = it.get("card_url")?.stringValue
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
                    (1..4).mapNotNull { index ->
                        val count = cardLegacy.get("choice${index}_count")?.stringValue?.toLong() ?: 0
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
                    expiresAt = cardLegacy.get("end_datetime_utc")?.stringValue?.let { parseCustomDateTime(it) } ?: Clock.System.now(),
                )
            } else {
                null
            }
        }
    val medias =
        legacy?.entities?.media?.map { media ->
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
                        url = media.videoInfo?.variants?.maxByOrNull { it.bitrate ?: 0 }?.url ?: "",
                        thumbnailUrl = media.mediaUrlHttps,
                        height = media.originalInfo.height.toFloat(),
                        width = media.originalInfo.width.toFloat(),
                        description = media.ext_alt_text,
                    )
            }
        }?.toImmutableList() ?: persistentListOf()
    val text =
        legacy?.fullText?.let {
            if (legacy.displayTextRange.size == 2) {
                it.substring(legacy.displayTextRange[0], legacy.displayTextRange[1])
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
                id = legacy?.idStr ?: throw Exception("no id for tweet: ${this.encodeJson()}"),
                host = accountKey.host,
            ),
        user = user,
        createdAt = legacy.createdAt.let { parseCustomDateTime(it) } ?: Clock.System.now(),
        content = text,
        medias = medias,
        card = uiCard,
        matrices =
            UiStatus.XQT.Matrices(
                replyCount = legacy.replyCount.toLong(),
                likeCount = legacy.favoriteCount.toLong(),
                retweetCount = legacy.retweetCount.toLong(),
            ),
        reaction =
            UiStatus.XQT.Reaction(
                liked = legacy.favorited,
                retweeted = legacy.retweeted,
                bookmarked = legacy.bookmarked ?: false,
            ),
        retweet = retweet,
        quote = quote,
        inReplyToScreenName = legacy.in_reply_to_screen_name,
        inReplyToStatusId = legacy.in_reply_to_status_id_str,
        inReplyToUserId = legacy.in_reply_to_user_id_str,
        poll = poll,
        sensitive = legacy.possiblySensitive == true,
    )
}

private fun TweetCardLegacy.get(key: String): TweetCardLegacyBindingValueData? = bindingValues.firstOrNull { it.key == key }?.value

fun User.toUi() =
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
                legacy.entities.urls?.firstOrNull { it.url == url }?.expandedUrl
            },
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

private fun String.replaceWithOriginImageUrl() = this.replace("_normal.jpg", ".jpg")

private fun parseCustomDateTime(dateTimeStr: String): Instant? {
    val months =
        mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12,
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
