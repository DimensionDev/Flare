package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GeneratorView
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.graph.ListView
import app.bsky.notification.ListNotificationsReason
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetFeatureUnion
import chat.bsky.convo.MessageView
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.bluesky.bskyJson
import dev.dimension.flare.data.datasource.microblog.StatusAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.model.JsonContent
import sh.christian.ozone.api.model.ReadOnlyList

internal val bskyJson by lazy {
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "${'$'}type"
    }
}

private fun List<Byte>.stringify(): String = this.toByteArray().decodeToString()

private fun Element.appendTextWithBr(text: String) {
    val parts = text.split("\n")
    for (i in parts.indices) {
        addChildren(TextNode(parts[i]))
        if (i < parts.size - 1) {
            addChildren(Element("br"))
        }
    }
}

private fun parseBluesky(
    post: Post,
    accountKey: MicroBlogKey,
): UiRichText =
    parseBluesky(
        text = post.text,
        facets = post.facets,
        accountKey = accountKey,
    )

private fun parseBluesky(
    text: String,
    facets: ReadOnlyList<Facet>,
    accountKey: MicroBlogKey,
): UiRichText {
    val element = Element("p")

    val codePoints = text.toByteArray(charset = Charsets.UTF_8)
    var codePointIndex = 0
    for (facet in facets) {
        val start = facet.index.byteStart.toInt()
        val end = facet.index.byteEnd.toInt()
        // some facets may have same start
        // for example: https://bsky.app/profile/technews4869.bsky.social/post/3l4vfqetv7t25
        if (codePointIndex > start) {
            continue
        }
        val beforeFacetText = codePoints.drop(codePointIndex).take(start - codePointIndex).stringify()
        element.appendTextWithBr(beforeFacetText)
        val facetText = codePoints.drop(start).take(end - start).stringify()
        // TODO: multiple features
        val feature = facet.features.firstOrNull()
        if (feature != null) {
            when (feature) {
                is FacetFeatureUnion.Link -> {
                    element.addChildren(
                        Element("a")
                            .apply {
                                appendTextWithBr(facetText)
                                attributes().put("href", feature.value.uri.uri)
                            },
                    )
                }

                is FacetFeatureUnion.Mention -> {
                    element.addChildren(
                        Element("a")
                            .apply {
                                appendTextWithBr(facetText)
                                attributes().put(
                                    "href",
                                    AppDeepLink.Profile.invoke(
                                        accountKey = accountKey,
                                        userKey =
                                            MicroBlogKey(
                                                id = feature.value.did.did,
                                                host = accountKey.host,
                                            ),
                                    ),
                                )
                            },
                    )
                }

                is FacetFeatureUnion.Tag -> {
                    element.addChildren(
                        Element("a")
                            .apply {
                                appendTextWithBr(facetText)
                                attributes().put(
                                    "href",
                                    AppDeepLink.Search(
                                        accountKey = accountKey,
                                        keyword = facetText,
                                    ),
                                )
                            },
                    )
                }
            }
        } else {
            element.appendTextWithBr(facetText)
        }
        codePointIndex = end
    }
    val afterFacetText = codePoints.drop(codePointIndex).stringify()
    element.appendTextWithBr(afterFacetText)
    return element.toUi()
}

internal fun FeedViewPostReasonUnion.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
    references: Map<ReferenceType, StatusContent>,
): UiTimeline {
    val data = (references[ReferenceType.Retweet] as? StatusContent.Bluesky)?.data
    return UiTimeline(
        topMessage =
            (this as? FeedViewPostReasonUnion.ReasonRepost)?.value?.by?.let {
                val user = it.render(accountKey)
                UiTimeline.TopMessage(
                    user = user,
                    icon = UiTimeline.TopMessage.Icon.Retweet,
                    type = UiTimeline.TopMessage.MessageType.Bluesky.Repost,
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Profile(
                                accountKey = accountKey,
                                userKey = user.key,
                            ),
                        )
                    },
                    statusKey = MicroBlogKey(id = data?.uri?.atUri.orEmpty(), host = accountKey.host),
                )
            },
        content = data?.renderStatus(accountKey, event),
        platformType = PlatformType.Bluesky,
    )
}

internal fun StatusContent.BlueskyNotification.renderBlueskyNotification(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
    references: Map<ReferenceType, StatusContent> = emptyMap(),
): UiTimeline {
    return when (this) {
        is StatusContent.BlueskyNotification.Normal -> {
            val user = data.author.render(accountKey = accountKey)
            val topMessage =
                UiTimeline.TopMessage(
                    user = user,
                    icon = data.reason.icon,
                    type = data.reason.type,
                    onClicked = {
                        launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = user.key))
                    },
                    statusKey = MicroBlogKey(id = data.uri.atUri, host = accountKey.host),
                )
            val content =
                UiTimeline.ItemContent.User(
                    value = user,
                )
            UiTimeline(
                topMessage = topMessage,
                content = content,
                platformType = PlatformType.Bluesky,
            )
        }
        is StatusContent.BlueskyNotification.Post ->
            references[ReferenceType.Notification]?.render(accountKey, event) ?: post.render(accountKey, event = event)
        is StatusContent.BlueskyNotification.UserList -> {
            val reason = this.data.firstOrNull()?.reason ?: ListNotificationsReason.UNKNOWN
            val uri =
                this.data
                    .firstOrNull()
                    ?.uri
                    ?.atUri ?: ""
            val topMessage =
                UiTimeline.TopMessage(
                    user = null,
                    icon = reason.icon,
                    type = reason.type,
                    onClicked = {
//                        launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = user.key))
                    },
                    statusKey = MicroBlogKey(id = uri, host = accountKey.host),
                )
            val content =
                UiTimeline.ItemContent.UserList(
                    users = this.data.map { it.author.render(accountKey = accountKey) }.toImmutableList(),
                    status =
                        references[ReferenceType.Notification]?.let { it as? StatusContent.Bluesky }?.data?.renderStatus(
                            accountKey,
                            event,
                        ),
                )
            return UiTimeline(
                topMessage = topMessage,
                content = content,
                platformType = PlatformType.Bluesky,
            )
        }
    }
}

private val ListNotificationsReason.icon: UiTimeline.TopMessage.Icon
    get() =
        when (this) {
            ListNotificationsReason.LIKE -> UiTimeline.TopMessage.Icon.Favourite
            ListNotificationsReason.REPOST -> UiTimeline.TopMessage.Icon.Retweet
            ListNotificationsReason.FOLLOW -> UiTimeline.TopMessage.Icon.Follow
            ListNotificationsReason.MENTION -> UiTimeline.TopMessage.Icon.Mention
            ListNotificationsReason.REPLY -> UiTimeline.TopMessage.Icon.Reply
            ListNotificationsReason.QUOTE -> UiTimeline.TopMessage.Icon.Reply
            ListNotificationsReason.UNKNOWN -> UiTimeline.TopMessage.Icon.Info
            ListNotificationsReason.STARTERPACK_JOINED -> UiTimeline.TopMessage.Icon.Info
        }

private val ListNotificationsReason.type: UiTimeline.TopMessage.MessageType
    get() =
        when (this) {
            ListNotificationsReason.LIKE -> UiTimeline.TopMessage.MessageType.Bluesky.Like
            ListNotificationsReason.REPOST -> UiTimeline.TopMessage.MessageType.Bluesky.Repost
            ListNotificationsReason.FOLLOW -> UiTimeline.TopMessage.MessageType.Bluesky.Follow
            ListNotificationsReason.MENTION -> UiTimeline.TopMessage.MessageType.Bluesky.Mention
            ListNotificationsReason.REPLY -> UiTimeline.TopMessage.MessageType.Bluesky.Reply
            ListNotificationsReason.QUOTE -> UiTimeline.TopMessage.MessageType.Bluesky.Quote
            ListNotificationsReason.UNKNOWN -> UiTimeline.TopMessage.MessageType.Bluesky.UnKnown
            ListNotificationsReason.STARTERPACK_JOINED -> UiTimeline.TopMessage.MessageType.Bluesky.StarterpackJoined
        }

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
        content = parseBluesky(record.bskyJson<JsonContent, Post>(), accountKey),
        poll = null,
        quote = listOfNotNull(findQuote(accountKey, this, event)).toImmutableList(),
        contentWarning = null,
        actions =
            listOfNotNull(
                StatusAction.Item.Reply(
                    count = replyCount ?: 0,
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
                                        launcher.launch(
                                            AppDeepLink.Bluesky.ReportStatus(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                        )
                                    },
                                )
                            },
                        ).toImmutableList(),
                ),
            ).toImmutableList(),
        createdAt = indexedAt.toUi(),
        sensitive = false,
        onClicked = {
            launcher.launch(
                AppDeepLink.StatusDetail(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            )
        },
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
    )
}

internal fun ProfileViewBasic.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = did.did,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key = userKey,
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
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

internal fun ProfileView.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = did.did,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key = userKey,
        banner = null,
        description = Element("span").apply { addChildren(TextNode(description.orEmpty())) }.toUi(),
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Bluesky,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

internal fun ProfileViewDetailed.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = did.did,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatar?.uri.orEmpty(),
        name =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key = userKey,
        banner = banner?.uri,
        description = Element("span").apply { addChildren(TextNode(description.orEmpty())) }.toUi(),
        matrices =
            UiProfile.Matrices(
                fansCount = followersCount ?: 0,
                followsCount = followsCount ?: 0,
                statusesCount = postsCount ?: 0,
            ),
        mark = persistentListOf(),
        bottomContent = null,
        platformType = PlatformType.Bluesky,
        onClicked = {
            launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = userKey))
        },
    )
}

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
    when (val embed = postView.embed) {
        is PostViewEmbedUnion.ImagesView -> {
            embed.value.images
                .map {
                    UiMedia.Image(
                        url = it.fullsize.uri,
                        previewUrl = it.thumb.uri,
                        description = it.alt,
                        width = it.aspectRatio?.width?.toFloat() ?: 0f,
                        height = it.aspectRatio?.height?.toFloat() ?: 0f,
                        sensitive = false,
                    )
                }.toImmutableList()
        }

        is PostViewEmbedUnion.VideoView -> {
            persistentListOf(
                UiMedia.Video(
                    url = embed.value.playlist.uri,
                    thumbnailUrl = embed.value.thumbnail?.uri ?: "",
                    description = embed.value.alt,
                    width =
                        embed.value.aspectRatio
                            ?.width
                            ?.toFloat() ?: 0f,
                    height =
                        embed.value.aspectRatio
                            ?.height
                            ?.toFloat() ?: 0f,
                ),
            )
        }

        else -> persistentListOf()
    }

private fun findQuote(
    accountKey: MicroBlogKey,
    postView: PostView,
    event: StatusEvent.Bluesky,
): UiTimeline.ItemContent.Status? =
    when (val embed = postView.embed) {
        is PostViewEmbedUnion.RecordView -> render(accountKey, embed.value.record, event)
        is PostViewEmbedUnion.RecordWithMediaView ->
            render(
                accountKey,
                embed.value.record.record,
                event = event,
            )

        else -> null
    }

private fun render(
    accountKey: MicroBlogKey,
    record: RecordViewRecordUnion,
    event: StatusEvent.Bluesky,
): UiTimeline.ItemContent.Status? =
    when (record) {
        is RecordViewRecordUnion.ViewRecord -> {
            val user = record.value.author.render(accountKey)
            val isFromMe = user.key == accountKey
            val statusKey =
                MicroBlogKey(
                    id = record.value.uri.atUri,
                    host = accountKey.host,
                )
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
                statusKey = statusKey,
                content = parseBluesky(record.value.value.bskyJson<JsonContent, Post>(), accountKey),
                actions =
                    listOfNotNull(
                        StatusAction.Item.Reply(
                            count = record.value.replyCount ?: 0,
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
                                            event.reblog(
                                                statusKey = statusKey,
                                                cid = record.value.cid.cid,
                                                uri = record.value.uri.atUri,
                                                repostUri = null,
                                            )
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
                            count = record.value.likeCount ?: 0,
                            liked = false,
                            onClicked = {
                                event.like(
                                    statusKey = statusKey,
                                    cid = record.value.cid.cid,
                                    uri = record.value.uri.atUri,
                                    likedUri = null,
                                )
                            },
                        ),
                        StatusAction.Group(
                            displayItem = StatusAction.Item.More,
                            actions =
                                listOfNotNull(
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
                                                launcher.launch(
                                                    AppDeepLink.Bluesky.ReportStatus(
                                                        accountKey = accountKey,
                                                        statusKey = statusKey,
                                                    ),
                                                )
                                            },
                                        )
                                    },
                                ).toImmutableList(),
                        ),
                    ).toImmutableList(),
                contentWarning = null,
                poll = null,
                quote = persistentListOf(),
                createdAt = record.value.indexedAt.toUi(),
                sensitive = false,
                onClicked = {
                    launcher.launch(
                        AppDeepLink.StatusDetail(
                            accountKey = accountKey,
                            statusKey = statusKey,
                        ),
                    )
                },
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
            )
        }

        else -> null
    }

internal fun GeneratorView.render(accountKey: MicroBlogKey) =
    UiList(
        id = uri.atUri,
        title = displayName,
        description = description,
        avatar = avatar?.uri,
        creator = creator.render(accountKey),
        likedCount = likeCount ?: 0,
        liked = viewer?.like?.atUri != null,
        platformType = PlatformType.Bluesky,
        type = UiList.Type.Feed,
    )

internal fun ListView.render(accountKey: MicroBlogKey) =
    UiList(
        id = uri.atUri,
        title = name,
        description = description,
        avatar = avatar?.uri,
        creator = creator.render(accountKey),
        platformType = PlatformType.Bluesky,
    )

internal fun MessageContent.Bluesky.render(accountKey: MicroBlogKey) =
    when (this) {
        is MessageContent.Bluesky.Deleted -> UiDMItem.Message.Deleted
        is MessageContent.Bluesky.Message -> data.render(accountKey)
    }

internal fun MessageView.render(accountKey: MicroBlogKey) =
    UiDMItem.Message.Text(
        text = parseBluesky(text, facets, accountKey),
    )
