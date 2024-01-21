package dev.dimension.flare.ui.model.mapper

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
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
                    expiresAt = cardLegacy.get("end_datetime_utc")?.stringValue?.let { Instant.parse(it) } ?: Clock.System.now(),
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
            } else {
                it
            }
        }.orEmpty()
    return UiStatus.XQT(
        accountKey = accountKey,
        statusKey =
            MicroBlogKey(
                id = legacy?.idStr ?: throw Exception("no id for tweet"),
                host = accountKey.host,
            ),
        user = user,
        createdAt = legacy.createdAt.let { Instant.parse(it) },
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
            ),
        retweet = retweet,
        quote = quote,
        inReplyToScreenName = legacy.in_reply_to_screen_name,
        inReplyToStatusId = legacy.in_reply_to_status_id_str,
        inReplyToUserId = legacy.in_reply_to_user_id_str,
        poll = poll,
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
        handleInternal = legacy.screenName,
        avatarUrl = legacy.profileImageUrlHttps.replaceWithOriginImageUrl(),
        bannerUrl = legacy.profileBannerUrl,
        description = legacy.description.takeIf { it.isNotEmpty() },
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
        location = legacy.location.takeIf { it.isNotEmpty() },
        url =
            legacy.url?.takeIf { it.isNotEmpty() }?.let { url ->
                legacy.entities.urls?.firstOrNull { it.url == url }?.expandedUrl
            },
    )

private fun String.replaceWithOriginImageUrl() = this.replace("_normal.jpg", ".jpg")
