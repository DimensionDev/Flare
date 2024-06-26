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
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.datasource.bluesky.jsonElement
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.jsonPrimitive

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
