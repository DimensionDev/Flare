package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.notification.ListNotificationsNotification
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.deeplink
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.screen.destinations.ProfileWithUserNameAndHostRouteDestination
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.jsonPrimitive
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun FeedViewPostReasonUnion.toUi(
    accountKey: MicroBlogKey,
    data: DbPagingTimelineWithStatus,
): UiStatus.Bluesky {
    val actualPost = data.status.references.firstOrNull { it.reference.referenceType == ReferenceType.Retweet }
    requireNotNull(actualPost)
    require(actualPost.status.data.content is StatusContent.Bluesky)
    return actualPost.status.data.content.data.toUi(accountKey).copy(
        repostBy = (this as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.toUi(accountKey.host),
    )
}

internal fun FeedViewPost.toUi(
    accountKey: MicroBlogKey,
): UiStatus.Bluesky {
    return with(post) {
        UiStatus.Bluesky(
            user = author.toUi(accountKey.host),
            statusKey = MicroBlogKey(
                id = uri.atUri,
                host = accountKey.host,
            ),
            accountKey = accountKey,
            content = record.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content.orEmpty(),
            contentToken = record.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content?.let {
                parseDescription(it, accountKey.host)
            } ?: Element("span"),
            indexedAt = indexedAt,
            repostBy = (reason as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.toUi(accountKey.host),
            quote = findQuote(accountKey, this),
            medias = findMedias(this),
            card = findCard(this),
            reaction = UiStatus.Bluesky.Reaction(
                liked = viewer?.like?.atUri != null,
                reposted = viewer?.repost?.atUri != null,
            ),
            matrices = UiStatus.Bluesky.Matrices(
                replyCount = replyCount ?: 0,
                likeCount = likeCount ?: 0,
                repostCount = repostCount ?: 0,
            ),
        )
    }
}

internal fun ListNotificationsNotification.toUi(
    accountKey: MicroBlogKey,
): UiStatus.BlueskyNotification {
    return UiStatus.BlueskyNotification(
        user = author.toUi(accountKey.host),
        statusKey = MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        ),
        accountKey = accountKey,
        reason = reason,
        indexedAt = indexedAt,
    )
}

internal fun PostView.toUi(
    accountKey: MicroBlogKey,
): UiStatus.Bluesky {
    return UiStatus.Bluesky(
        user = author.toUi(accountKey.host),
        statusKey = MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        ),
        accountKey = accountKey,
        content = record.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content.orEmpty(),
        contentToken = record.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content?.let {
            parseDescription(it, accountKey.host)
        } ?: Element("span"),
        indexedAt = indexedAt,
        repostBy = null,
        quote = findQuote(accountKey, this),
        medias = findMedias(this),
        card = findCard(this),
        reaction = UiStatus.Bluesky.Reaction(
            liked = viewer?.like?.atUri != null,
            reposted = viewer?.repost?.atUri != null,
        ),
        matrices = UiStatus.Bluesky.Matrices(
            replyCount = replyCount ?: 0,
            likeCount = likeCount ?: 0,
            repostCount = repostCount ?: 0,
        ),
    )
}

private fun findCard(postView: PostView): UiCard? {
    return if (postView.embed is PostViewEmbedUnion.ExternalView) {
        val embed = postView.embed as PostViewEmbedUnion.ExternalView
        UiCard(
            url = embed.value.external.uri.uri,
            title = embed.value.external.title,
            description = embed.value.external.description,
            media = embed.value.external.thumb?.let {
                UiMedia.Image(
                    url = it,
                    previewUrl = it,
                    description = null,
                    aspectRatio = 1f,
                )
            },
        )
    } else {
        null
    }
}

private fun findMedias(postView: PostView): ImmutableList<UiMedia> {
    return if (postView.embed is PostViewEmbedUnion.ImagesView) {
        val embed = postView.embed as PostViewEmbedUnion.ImagesView
        embed.value.images.map {
            UiMedia.Image(
                url = it.fullsize,
                previewUrl = it.thumb,
                description = it.alt,
                aspectRatio = 1f,
            )
        }
    } else {
        emptyList()
    }.toImmutableList()
}

private fun findQuote(
    accountKey: MicroBlogKey,
    postView: PostView,
): UiStatus.Bluesky? {
    return when (val embed = postView.embed) {
        is PostViewEmbedUnion.RecordView -> toUi(accountKey, embed.value.record)
        is PostViewEmbedUnion.RecordWithMediaView -> toUi(
            accountKey,
            embed.value.record.record,
        )

        else -> null
    }
}

private fun toUi(
    accountKey: MicroBlogKey,
    record: RecordViewRecordUnion,
): UiStatus.Bluesky? {
    return when (record) {
        is RecordViewRecordUnion.ViewRecord -> UiStatus.Bluesky(
            accountKey = accountKey,
            statusKey = MicroBlogKey(
                id = record.value.uri.atUri,
                host = accountKey.host,
            ),
            content = record.value.value.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content.orEmpty(),
            contentToken = record.value.value.jsonObjectOrNull?.get("text")?.jsonPrimitive?.content?.let {
                parseDescription(it, accountKey.host)
            } ?: Element("span"),
            indexedAt = record.value.indexedAt,
            repostBy = null,
            quote = null,
            medias = record.value.embeds.mapNotNull {
                when (it) {
                    is RecordViewRecordEmbedUnion.ImagesView -> it.value.images.map {
                        UiMedia.Image(
                            url = it.fullsize,
                            previewUrl = it.thumb,
                            description = it.alt,
                            aspectRatio = 1f,
                        )
                    }

                    else -> null
                }
            }.flatten().toImmutableList(),
            card = record.value.embeds.mapNotNull {
                when (it) {
                    is RecordViewRecordEmbedUnion.ExternalView -> UiCard(
                        url = it.value.external.uri.uri,
                        title = it.value.external.title,
                        description = it.value.external.description,
                        media = it.value.external.thumb?.let {
                            UiMedia.Image(
                                url = it,
                                previewUrl = it,
                                description = null,
                                aspectRatio = 1f,
                            )
                        },
                    )

                    else -> null
                }
            }.firstOrNull(),
            user = record.value.author.toUi(accountKey.host),
            reaction = UiStatus.Bluesky.Reaction(
                liked = false,
                reposted = false,
            ),
            matrices = UiStatus.Bluesky.Matrices(
                replyCount = 0,
                likeCount = 0,
                repostCount = 0,
            ),
        )

        else -> null
    }
}

internal fun ProfileViewDetailed.toUi(accountHost: String): UiUser = UiUser.Bluesky(
    userKey = MicroBlogKey(
        id = did.did,
        host = accountHost,
    ),
    name = displayName.orEmpty(),
    handleInternal = handle.handle,
    avatarUrl = avatar.orEmpty(),
    bannerUrl = banner,
    nameElement = Element("span").apply {
        text(displayName.orEmpty())
    },
    description = description,
    descriptionElement = description?.let { parseDescription(it, accountHost) },
    matrices = UiUser.Bluesky.Matrices(
        fansCount = followersCount ?: 0,
        followsCount = followsCount ?: 0,
        statusesCount = postsCount ?: 0,
    ),
    relation = UiRelation.Bluesky(
        following = viewer?.following?.atUri,
        followedBy = viewer?.followedBy?.atUri,
        blocked = viewer?.blockedBy ?: false,
        muted = viewer?.muted ?: false,
    ),
)

internal fun ProfileViewBasic.toUi(accountHost: String): UiUser.Bluesky {
    return UiUser.Bluesky(
        userKey = MicroBlogKey(
            id = did.did,
            host = accountHost,
        ),
        name = displayName.orEmpty(),
        handleInternal = handle.handle,
        avatarUrl = avatar.orEmpty(),
        bannerUrl = null,
        nameElement = Element("span").apply {
            text(displayName.orEmpty())
        },
        description = null,
        descriptionElement = null,
        matrices = UiUser.Bluesky.Matrices(
            fansCount = 0,
            followsCount = 0,
            statusesCount = 0,
        ),
        relation = UiRelation.Bluesky(
            following = viewer?.following?.atUri,
            followedBy = viewer?.followedBy?.atUri,
            blocked = viewer?.blockedBy ?: false,
            muted = viewer?.muted ?: false,
        ),
    )
}

internal fun ProfileView.toUi(accountHost: String): UiUser.Bluesky {
    return UiUser.Bluesky(
        userKey = MicroBlogKey(
            id = did.did,
            host = accountHost,
        ),
        name = displayName.orEmpty(),
        handleInternal = handle.handle,
        avatarUrl = avatar.orEmpty(),
        bannerUrl = null,
        nameElement = Element("span").apply {
            text(displayName.orEmpty())
        },
        description = description,
        descriptionElement = description?.let { parseDescription(it, accountHost) },
        matrices = UiUser.Bluesky.Matrices(
            fansCount = 0,
            followsCount = 0,
            statusesCount = 0,
        ),
        relation = UiRelation.Bluesky(
            following = viewer?.following?.atUri,
            followedBy = viewer?.followedBy?.atUri,
            blocked = viewer?.blockedBy ?: false,
            muted = viewer?.muted ?: false,
        ),
    )
}

private val blueskyParser by lazy {
    TwitterParser(enableDotInUserName = true)
}

private fun parseDescription(description: String, accountHost: String): Element {
    val element = Element("body")
    val token = blueskyParser.parse(description)
    token.forEach {
        element.appendChild(it.toElement(accountHost))
    }
    return element
}

private fun Token.toElement(
    accountHost: String,
): Node {
    return when (this) {
        is CashTagToken -> Element("a").apply {
            attr("href", AppDeepLink.Search(value))
            text(value)
        }

        is EmojiToken -> {
            Element("span").apply {
                attr("aria-label", value)
                attr("role", "img")
                text(value)
            }
        }

        is HashTagToken -> Element("a").apply {
            attr("href", AppDeepLink.Search(value))
            text(value)
        }

        is StringToken -> TextNode(value)
        is UrlToken -> Element("a").apply {
            attr("href", value)
            text(value)
        }

        is UserNameToken -> Element("a").apply {
            val trimmed = value.trimStart('@')
            if (trimmed.contains('@')) {
                val (username, host) = trimmed.split('@')
                attr("href", ProfileWithUserNameAndHostRouteDestination(username, host).deeplink())
            } else {
                attr(
                    "href",
                    ProfileWithUserNameAndHostRouteDestination(trimmed, accountHost).deeplink(),
                )
            }
            text(value)
        }
    }
}
