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
import app.bsky.notification.ListNotificationsReason
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.datasource.bluesky.jsonElement
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.Render
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
): Render.Item =
    Render.Item(
        topMessage =
            (this as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.let {
                Render.TopMessage(
                    user = it.render(accountKey),
                    icon = Render.TopMessage.Icon.Retweet,
                    type = Render.TopMessage.MessageType.Bluesky.Repost,
                )
            },
        content = data.renderStatus(accountKey, event),
    )

internal fun ListNotificationsNotification.render(accountKey: MicroBlogKey): Render.Item {
    UiStatus.BlueskyNotification(
        user = author.toUi(accountKey),
        statusKey =
            MicroBlogKey(
                id = uri.atUri,
                host = accountKey.host,
            ),
        accountKey = accountKey,
        reason = reason,
        indexedAt = indexedAt,
    )

    return Render.Item(
        topMessage =
            Render.TopMessage(
                user = author.render(accountKey),
                icon = null,
                type =
                    when (reason) {
                        ListNotificationsReason.LIKE -> Render.TopMessage.MessageType.Bluesky.Like
                        ListNotificationsReason.REPOST -> Render.TopMessage.MessageType.Bluesky.Repost
                        ListNotificationsReason.FOLLOW -> Render.TopMessage.MessageType.Bluesky.Follow
                        ListNotificationsReason.MENTION -> Render.TopMessage.MessageType.Bluesky.Mention
                        ListNotificationsReason.REPLY -> Render.TopMessage.MessageType.Bluesky.Reply
                        ListNotificationsReason.QUOTE -> Render.TopMessage.MessageType.Bluesky.Quote
                    },
            ),
        content = author.render(accountKey),
    )
}

internal fun PostView.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
) = Render.Item(
    topMessage = null,
    content = renderStatus(accountKey, event),
)

internal fun PostView.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
): Render.ItemContent.Status {
    val user = author.render(accountKey)
    val isFromMe = user.key == accountKey
    val statusKey =
        MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        )
    return Render.ItemContent.Status(
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
        quote = listOfNotNull(findQuote2(accountKey, this)).toImmutableList(),
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
    )
}

internal fun ProfileViewBasic.render(accountKey: MicroBlogKey): Render.ItemContent.User =
    Render.ItemContent.User(
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
    )

internal fun ProfileView.render(accountKey: MicroBlogKey): Render.ItemContent.User =
    Render.ItemContent.User(
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
    )

internal fun FeedViewPostReasonUnion.toUi(
    accountKey: MicroBlogKey,
    data: PostView,
): UiStatus.Bluesky =
    data.toUi(accountKey).copy(
        repostBy = (this as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.toUi(accountKey),
    )

internal fun FeedViewPost.toUi(accountKey: MicroBlogKey): UiStatus.Bluesky =
    with(post) {
        UiStatus.Bluesky(
            user = author.toUi(accountKey),
            statusKey =
                MicroBlogKey(
                    id = uri.atUri,
                    host = accountKey.host,
                ),
            accountKey = accountKey,
            content =
                record
                    .jsonElement()
                    .jsonObjectOrNull
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty(),
            indexedAt = indexedAt,
            repostBy = (reason as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.toUi(accountKey),
            quote = findQuote(accountKey, this),
            medias = findMedias(this),
            card = findCard(this),
            reaction =
                UiStatus.Bluesky.Reaction(
                    repostUri = viewer?.repost?.atUri,
                    likedUri = viewer?.like?.atUri,
                ),
            matrices =
                UiStatus.Bluesky.Matrices(
                    replyCount = replyCount ?: 0,
                    likeCount = likeCount ?: 0,
                    repostCount = repostCount ?: 0,
                ),
            cid = cid.cid,
            uri = uri.atUri,
        )
    }

internal fun ListNotificationsNotification.toUi(accountKey: MicroBlogKey): UiStatus.BlueskyNotification =
    UiStatus.BlueskyNotification(
        user = author.toUi(accountKey),
        statusKey =
            MicroBlogKey(
                id = uri.atUri,
                host = accountKey.host,
            ),
        accountKey = accountKey,
        reason = reason,
        indexedAt = indexedAt,
    )

internal fun PostView.toUi(accountKey: MicroBlogKey): UiStatus.Bluesky =
    UiStatus.Bluesky(
        user = author.toUi(accountKey),
        statusKey =
            MicroBlogKey(
                id = uri.atUri,
                host = accountKey.host,
            ),
        accountKey = accountKey,
        content =
            record
                .jsonElement()
                .jsonObjectOrNull
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                .orEmpty(),
        indexedAt = indexedAt,
        repostBy = null,
        quote = findQuote(accountKey, this),
        medias = findMedias(this),
        card = findCard(this),
        reaction =
            UiStatus.Bluesky.Reaction(
                repostUri = viewer?.repost?.atUri,
                likedUri = viewer?.like?.atUri,
            ),
        matrices =
            UiStatus.Bluesky.Matrices(
                replyCount = replyCount ?: 0,
                likeCount = likeCount ?: 0,
                repostCount = repostCount ?: 0,
            ),
        cid = cid.cid,
        uri = uri.atUri,
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
): UiStatus.Bluesky? =
    when (val embed = postView.embed) {
        is PostViewEmbedUnion.RecordView -> toUi(accountKey, embed.value.record)
        is PostViewEmbedUnion.RecordWithMediaView ->
            toUi(
                accountKey,
                embed.value.record.record,
            )

        else -> null
    }

private fun findQuote2(
    accountKey: MicroBlogKey,
    postView: PostView,
): Render.ItemContent.Status? =
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
): Render.ItemContent.Status? =
    when (record) {
        is RecordViewRecordUnion.ViewRecord -> {
            val user = record.value.author.render(accountKey)
            val isFromMe = user.key == accountKey
            Render.ItemContent.Status(
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
            )
        }

        else -> null
    }

private fun toUi(
    accountKey: MicroBlogKey,
    record: RecordViewRecordUnion,
): UiStatus.Bluesky? =
    when (record) {
        is RecordViewRecordUnion.ViewRecord ->
            UiStatus.Bluesky(
                accountKey = accountKey,
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
                        .orEmpty(),
                indexedAt = record.value.indexedAt,
                repostBy = null,
                quote = null,
                medias =
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
                user = record.value.author.toUi(accountKey),
                // TODO: add reaction
                reaction =
                    UiStatus.Bluesky.Reaction(
                        repostUri = null,
                        likedUri = null,
                    ),
                matrices =
                    UiStatus.Bluesky.Matrices(
                        replyCount = 0,
                        likeCount = 0,
                        repostCount = 0,
                    ),
                cid = record.value.cid.cid,
                uri = record.value.uri.atUri,
            )

        else -> null
    }

internal fun ProfileViewDetailed.toUi(accountKey: MicroBlogKey): UiUser =
    UiUser.Bluesky(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        displayName = displayName.orEmpty(),
        handleInternal = handle.handle,
        avatarUrl = avatar?.uri.orEmpty(),
        bannerUrl = banner?.uri,
        description = description,
        matrices =
            UiUser.Bluesky.Matrices(
                fansCount = followersCount ?: 0,
                followsCount = followsCount ?: 0,
                statusesCount = postsCount ?: 0,
            ),
        relation =
            UiRelation.Bluesky(
                following = viewer?.following?.atUri != null,
                isFans = viewer?.followedBy?.atUri != null,
                blocking = viewer?.blockedBy ?: false,
                muting = viewer?.muted ?: false,
            ),
        accountKey = accountKey,
    )

internal fun ProfileViewBasic.toUi(accountKey: MicroBlogKey): UiUser.Bluesky =
    UiUser.Bluesky(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        displayName = displayName.orEmpty(),
        handleInternal = handle.handle,
        avatarUrl = avatar?.uri.orEmpty(),
        bannerUrl = null,
        description = null,
        matrices =
            UiUser.Bluesky.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        relation =
            UiRelation.Bluesky(
                following = viewer?.following?.atUri != null,
                isFans = viewer?.followedBy?.atUri != null,
                blocking = viewer?.blockedBy ?: false,
                muting = viewer?.muted ?: false,
            ),
        accountKey = accountKey,
    )

internal fun ProfileView.toUi(accountKey: MicroBlogKey): UiUser.Bluesky =
    UiUser.Bluesky(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = accountKey.host,
            ),
        displayName = displayName.orEmpty(),
        handleInternal = handle.handle,
        avatarUrl = avatar?.uri.orEmpty(),
        bannerUrl = null,
        description = description,
        matrices =
            UiUser.Bluesky.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        relation =
            UiRelation.Bluesky(
                following = viewer?.following?.atUri != null,
                isFans = viewer?.followedBy?.atUri != null,
                blocking = viewer?.blockedBy ?: false,
                muting = viewer?.muted ?: false,
            ),
        accountKey = accountKey,
    )
