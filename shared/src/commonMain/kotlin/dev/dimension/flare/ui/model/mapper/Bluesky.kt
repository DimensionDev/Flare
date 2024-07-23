package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.notification.ListNotificationsNotification
import app.bsky.notification.ListNotificationsReason
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.datasource.bluesky.jsonElement
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.jsonPrimitive
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Text
import moe.tlaster.twitter.parser.TwitterParser

private val blueskyParser by lazy {
    TwitterParser(validMarkInUserName = listOf('.'))
}

internal fun FeedViewPostReasonUnion.render(
    accountKey: MicroBlogKey,
    data: PostView,
    event: StatusEvent.Bluesky,
): UiTimeline =
    UiTimeline(
        topMessage =
            (this as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.let {
                UiTimeline.TopMessage(
                    user = it.render(accountKey),
                    icon = UiTimeline.TopMessage.Icon.Retweet,
                    type = UiTimeline.TopMessage.MessageType.Bluesky.Repost,
                )
            },
        content = data.renderStatus(accountKey, event),
        platformType = PlatformType.Bluesky,
    )

internal fun ListNotificationsNotification.render(accountKey: MicroBlogKey): UiTimeline =
    UiTimeline(
        topMessage =
            UiTimeline.TopMessage(
                user = author.render(accountKey),
                icon =
                    when (reason) {
                        ListNotificationsReason.LIKE -> UiTimeline.TopMessage.Icon.Favourite
                        ListNotificationsReason.REPOST -> UiTimeline.TopMessage.Icon.Retweet
                        ListNotificationsReason.FOLLOW -> UiTimeline.TopMessage.Icon.Follow
                        ListNotificationsReason.MENTION -> UiTimeline.TopMessage.Icon.Mention
                        ListNotificationsReason.REPLY -> UiTimeline.TopMessage.Icon.Reply
                        ListNotificationsReason.QUOTE -> UiTimeline.TopMessage.Icon.Reply
                    },
                type =
                    when (reason) {
                        ListNotificationsReason.LIKE -> UiTimeline.TopMessage.MessageType.Bluesky.Like
                        ListNotificationsReason.REPOST -> UiTimeline.TopMessage.MessageType.Bluesky.Repost
                        ListNotificationsReason.FOLLOW -> UiTimeline.TopMessage.MessageType.Bluesky.Follow
                        ListNotificationsReason.MENTION -> UiTimeline.TopMessage.MessageType.Bluesky.Mention
                        ListNotificationsReason.REPLY -> UiTimeline.TopMessage.MessageType.Bluesky.Reply
                        ListNotificationsReason.QUOTE -> UiTimeline.TopMessage.MessageType.Bluesky.Quote
                    },
            ),
        content = UiTimeline.ItemContent.User(author.render(accountKey)),
        platformType = PlatformType.Bluesky,
    )

internal fun PostView.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
) = UiTimeline(
    topMessage = null,
    content = renderStatus(accountKey, event),
    platformType = PlatformType.Bluesky,
)

internal fun PostView.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
): UiTimeline.ItemContent.Status {
    val user = author.render(accountKey)
    val isFromMe = user.key == accountKey
    val statusKey =
        MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        )
    return UiTimeline.ItemContent.Status(
        user = user,
        images = findMedias(this),
        card = findCard(this),
        statusKey = statusKey,
        content =
            record
                .jsonElement()
                .jsonObjectOrNull
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
                .let {
                    blueskyParser
                        .parse(it)
                        .toHtml(accountKey)
                        .toUi()
                },
        poll = null,
        quote = listOfNotNull(findQuote(accountKey, this)).toImmutableList(),
        contentWarning = null,
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = replyCount ?: 0,
                ),
                StatusAction.Group(
                    displayItem =
                        StatusAction.Item.Retweet(
                            count = repostCount ?: 0,
                            retweeted = viewer?.repost?.atUri != null,
                            onClicked = {
                            },
                        ),
                    actions =
                        listOfNotNull(
                            StatusAction.Item.Retweet(
                                count = repostCount ?: 0,
                                retweeted = viewer?.repost?.atUri != null,
                                onClicked = {
                                    event.reblog(
                                        statusKey = statusKey,
                                        cid = cid.cid,
                                        uri = uri.atUri,
                                        repostUri = viewer?.repost?.atUri,
                                    )
                                },
                            ),
                            StatusAction.Item.Quote(
                                count = 0,
                            ),
                        ).toImmutableList(),
                ),
                StatusAction.Item.Like(
                    count = likeCount ?: 0,
                    liked = viewer?.like?.atUri != null,
                    onClicked = {
                        event.like(
                            statusKey = statusKey,
                            cid = cid.cid,
                            uri = uri.atUri,
                            likedUri = viewer?.like?.atUri,
                        )
                    },
                ),
                StatusAction.Group(
                    displayItem = StatusAction.Item.More,
                    actions =
                        listOfNotNull(
                            if (isFromMe) {
                                StatusAction.Item.Delete
                            } else {
                                StatusAction.Item.Report
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        createdAt = indexedAt.toUi(),
        sensitive = false,
    )
}

internal fun ProfileViewBasic.render(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    children.add(Text(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Bluesky,
    )

internal fun ProfileView.render(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    children.add(Text(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        banner = null,
        description = Element("span").apply { children.add(Text(description.orEmpty())) }.toUi(),
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Bluesky,
    )

internal fun ProfileViewDetailed.render(accountKey: MicroBlogKey): UiProfile =
    UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    children.add(Text(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        banner = banner?.uri,
        description = Element("span").apply { children.add(Text(description.orEmpty())) }.toUi(),
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount ?: 0,
                followsCount = followsCount ?: 0,
                statusesCount = postsCount ?: 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Bluesky,
    )

private fun findCard(postView: PostView): UiCard? =
    if (postView.embed is PostViewEmbedUnion.ExternalView) {
        val embed = postView.embed as PostViewEmbedUnion.ExternalView
        UiCard(
            url = embed.value.external.uri.uri,
            title = embed.value.external.title,
            description = embed.value.external.description,
            media =
                embed.value.external.thumb?.let {
                    UiMedia.Image(
                        url = it.uri,
                        previewUrl = it.uri,
                        description = null,
                        width = 0f,
                        height = 0f,
                        sensitive = false,
                    )
                },
        )
    } else {
        null
    }

private fun findMedias(postView: PostView): ImmutableList<UiMedia> =
    if (postView.embed is PostViewEmbedUnion.ImagesView) {
        val embed = postView.embed as PostViewEmbedUnion.ImagesView
        embed.value.images.map {
            UiMedia.Image(
                url = it.fullsize.uri,
                previewUrl = it.thumb.uri,
                description = it.alt,
                width = it.aspectRatio?.width?.toFloat() ?: 0f,
                height = it.aspectRatio?.height?.toFloat() ?: 0f,
                sensitive = false,
            )
        }
    } else {
        emptyList()
    }.toImmutableList()

private fun findQuote(
    accountKey: MicroBlogKey,
    postView: PostView,
): UiTimeline.ItemContent.Status? =
    when (val embed = postView.embed) {
        is PostViewEmbedUnion.RecordView -> render(accountKey, embed.value.record)
        is PostViewEmbedUnion.RecordWithMediaView ->
            render(
                accountKey,
                embed.value.record.record,
            )

        else -> null
    }

private fun render(
    accountKey: MicroBlogKey,
    record: RecordViewRecordUnion,
): UiTimeline.ItemContent.Status? =
    when (record) {
        is RecordViewRecordUnion.ViewRecord -> {
            val user = record.value.author.render(accountKey)
            val isFromMe = user.key == accountKey
            UiTimeline.ItemContent.Status(
                user = user,
                images =
                    record.value.embeds
                        .mapNotNull {
                            when (it) {
                                is RecordViewRecordEmbedUnion.ImagesView ->
                                    it.value.images.map {
                                        UiMedia.Image(
                                            url = it.fullsize.uri,
                                            previewUrl = it.thumb.uri,
                                            description = it.alt,
                                            width = it.aspectRatio?.width?.toFloat() ?: 0f,
                                            height = it.aspectRatio?.height?.toFloat() ?: 0f,
                                            sensitive = false,
                                        )
                                    }

                                else -> null
                            }
                        }.flatten()
                        .toImmutableList(),
                card =
                    record.value.embeds
                        .mapNotNull {
                            when (it) {
                                is RecordViewRecordEmbedUnion.ExternalView ->
                                    UiCard(
                                        url = it.value.external.uri.uri,
                                        title = it.value.external.title,
                                        description = it.value.external.description,
                                        media =
                                            it.value.external.thumb?.let {
                                                UiMedia.Image(
                                                    url = it.uri,
                                                    previewUrl = it.uri,
                                                    description = null,
                                                    width = 0f,
                                                    height = 0f,
                                                    sensitive = false,
                                                )
                                            },
                                    )

                                else -> null
                            }
                        }.firstOrNull(),
                statusKey =
                    MicroBlogKey(
                        id = record.value.uri.atUri,
                        host = accountKey.host,
                    ),
                content =
                    record.value.value
                        .jsonElement()
                        .jsonObjectOrNull
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                        .let {
                            blueskyParser
                                .parse(it)
                                .toHtml(accountKey)
                                .toUi()
                        },
                actions =
                    listOfNotNull(
                        StatusAction.Item.Reply(
                            count = record.value.replyCount ?: 0,
                        ),
                        StatusAction.Group(
                            displayItem =
                                StatusAction.Item.Retweet(
                                    count = record.value.repostCount ?: 0,
                                    retweeted = false,
                                    onClicked = {
                                    },
                                ),
                            actions =
                                listOfNotNull(
                                    StatusAction.Item.Retweet(
                                        count = record.value.repostCount ?: 0,
                                        retweeted = false,
                                        onClicked = {
                                        },
                                    ),
                                    StatusAction.Item.Quote(
                                        count = 0,
                                    ),
                                ).toImmutableList(),
                        ),
                        StatusAction.Item.Like(
                            count = record.value.likeCount ?: 0,
                            liked = false,
                            onClicked = {
//                            dataSource.like(accountKey, favourited ?: false)
                            },
                        ),
                        StatusAction.Group(
                            displayItem = StatusAction.Item.More,
                            actions =
                                listOfNotNull(
                                    if (isFromMe) {
                                        StatusAction.Item.Delete
                                    } else {
                                        StatusAction.Item.Report
                                    },
                                ).toImmutableList(),
                        ),
                    ).toImmutableList(),
                contentWarning = null,
                poll = null,
                quote = persistentListOf(),
                createdAt = record.value.indexedAt.toUi(),
                sensitive = false,
            )
        }

        else -> null
    }
