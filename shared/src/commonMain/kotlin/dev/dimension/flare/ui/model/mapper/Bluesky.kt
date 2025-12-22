package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.ExternalView
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GeneratorView
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.graph.ListView
import app.bsky.notification.ListNotificationsNotificationReason
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetByteSlice
import app.bsky.richtext.FacetFeatureUnion
import app.bsky.richtext.FacetLink
import app.bsky.richtext.FacetMention
import app.bsky.richtext.FacetTag
import chat.bsky.convo.MessageView
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import io.ktor.http.Url
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent

private val sensitiveLabels =
    listOf(
        "!warn",
        "porn",
        "sexual",
        "graphic-media",
        "nudity",
    )

private val parser =
    TwitterParser(
        validMarkInUserName = listOf('.', '-'),
        enableEscapeInUrl = true,
        validMarkInHashTag = listOf('.', ':'),
        enableDomainDetection = true,
    )

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

internal fun parseBlueskyJson(
    json: JsonContent,
    accountKey: MicroBlogKey,
): UiRichText {
    try {
        return parseBluesky(post = json.decodeAs(), accountKey = accountKey)
    } catch (e: Exception) {
        val jobj = json.decodeAs<JsonObject>()
        val text = jobj["text"]?.jsonPrimitive?.contentOrNull
        val facets =
            jobj["facets"]?.jsonArray?.let {
                runCatching {
                    bskyJson.decodeFromJsonElement<List<Facet>>(it)
                }.getOrNull()
            }
        if (facets != null) {
            return parseBluesky(
                text = text ?: "",
                facets = facets,
                accountKey = accountKey,
            )
        } else {
            return Element("span")
                .apply {
                    if (text != null) {
                        appendText(text)
                    }
                }.toUi()
        }
    }
}

internal suspend fun parseBskyFacets(
    content: String,
    resolveMentionDid: suspend (handle: String) -> String,
): List<Facet> {
    val tokens = parser.parse(content)
    if (tokens.isEmpty()) {
        return emptyList()
    }

    val facets = mutableListOf<Facet>()
    var byteIndex = 0

    for (token in tokens) {
        val tokenBytes = token.value.toByteArray(charset = Charsets.UTF_8)
        val start = byteIndex
        val end = byteIndex + tokenBytes.size

        val feature =
            when (token) {
                is UrlToken ->
                    FacetFeatureUnion.Link(
                        FacetLink(
                            uri = Uri(token.value),
                        ),
                    )

                is HashTagToken ->
                    FacetFeatureUnion.Tag(
                        FacetTag(
                            tag = token.value.trimStart('#'),
                        ),
                    )

                is UserNameToken -> {
                    val handle = token.value.removePrefix("@")
                    val didString = resolveMentionDid(handle)
                    FacetFeatureUnion.Mention(
                        FacetMention(
                            did = Did(didString),
                        ),
                    )
                }

                else -> null
            }

        if (feature != null) {
            facets.add(
                Facet(
                    index =
                        FacetByteSlice(
                            byteStart = start.toLong(),
                            byteEnd = end.toLong(),
                        ),
                    features = listOf(feature),
                ),
            )
        }

        byteIndex = end
    }

    return facets
}

private fun parseBluesky(
    post: Post,
    accountKey: MicroBlogKey,
): UiRichText =
    parseBluesky(
        text = post.text,
        facets = post.facets.orEmpty(),
        accountKey = accountKey,
    )

private fun parseBluesky(
    text: String,
    facets: List<Facet>,
    accountKey: MicroBlogKey,
): UiRichText {
    val element = Element("span")

    val codePoints = text.toByteArray(charset = Charsets.UTF_8)
    var codePointIndex = 0
    for (facet in facets) {
        val start = facet.index.byteStart.toInt()
        val end = facet.index.byteEnd.toInt()
        // some facets may have same start
        // for example: https://bsky.app/profile/technews4869.bsky.social/post/3l4vfqetv7t25
        if (start - codePointIndex < 0) {
            continue
        }
        val beforeFacetText =
            codePoints.drop(codePointIndex).take(start - codePointIndex).stringify()
        element.appendTextWithBr(beforeFacetText)
        if (end - start < 0) {
            continue
        }
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

                is FacetFeatureUnion.Unknown -> {
                    element.addChildren(
                        Element("span")
                            .apply {
                                appendTextWithBr(facetText)
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
    references: Map<ReferenceType, List<StatusContent>>,
): UiTimeline {
    val data = (references[ReferenceType.Retweet]?.firstOrNull() as? StatusContent.Bluesky)?.data
    val topMessage =
        when (this) {
            is FeedViewPostReasonUnion.ReasonPin -> {
                val user = data?.author?.render(accountKey = accountKey)
                UiTimeline.TopMessage(
                    user = user,
                    icon = UiTimeline.TopMessage.Icon.Pin,
                    type = UiTimeline.TopMessage.MessageType.Bluesky.Pinned,
                    onClicked = { },
                    statusKey =
                        MicroBlogKey(
                            id = data?.uri?.atUri.orEmpty(),
                            host = accountKey.host,
                        ),
                )
            }

            is FeedViewPostReasonUnion.ReasonRepost -> {
                val user = value.by.render(accountKey)
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
                    statusKey =
                        MicroBlogKey(
                            id = data?.uri?.atUri.orEmpty(),
                            host = accountKey.host,
                        ),
                )
            }

            is FeedViewPostReasonUnion.Unknown -> null
        }
    return UiTimeline(
        topMessage = topMessage,
        content = data?.renderStatus(accountKey, event),
    )
}

internal fun StatusContent.BlueskyNotification.renderBlueskyNotification(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
    references: Map<ReferenceType, List<StatusContent>> = emptyMap(),
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
                        launcher.launch(
                            AppDeepLink.Profile(
                                accountKey = accountKey,
                                userKey = user.key,
                            ),
                        )
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
            )
        }

        is StatusContent.BlueskyNotification.Post ->
            references[ReferenceType.Notification]?.firstOrNull()?.render(event) ?: post.render(
                accountKey,
                event = event,
            )

        is StatusContent.BlueskyNotification.UserList -> {
            val reason = this.data.firstOrNull()?.reason
            val uri =
                this.data
                    .firstOrNull()
                    ?.uri
                    ?.atUri ?: ""
            val topMessage =
                if (reason != null) {
                    UiTimeline.TopMessage(
                        user = null,
                        icon = reason.icon,
                        type = reason.type,
                        onClicked = {
//                        launcher.launch(AppDeepLink.Profile(accountKey = accountKey, userKey = user.key))
                        },
                        statusKey = MicroBlogKey(id = uri, host = accountKey.host),
                    )
                } else {
                    null
                }
            val content =
                UiTimeline.ItemContent.UserList(
                    users =
                        this.data
                            .map { it.author.render(accountKey = accountKey) }
                            .toImmutableList(),
                    status =
                        references[ReferenceType.Notification]
                            ?.firstOrNull()
                            ?.let { it as? StatusContent.Bluesky }
                            ?.data
                            ?.renderStatus(
                                accountKey,
                                event,
                            ),
                )
            return UiTimeline(
                topMessage = topMessage,
                content = content,
            )
        }
    }
}

private val ListNotificationsNotificationReason.icon: UiTimeline.TopMessage.Icon
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like -> UiTimeline.TopMessage.Icon.Favourite
            ListNotificationsNotificationReason.Repost -> UiTimeline.TopMessage.Icon.Retweet
            ListNotificationsNotificationReason.Follow -> UiTimeline.TopMessage.Icon.Follow
            ListNotificationsNotificationReason.Mention -> UiTimeline.TopMessage.Icon.Mention
            ListNotificationsNotificationReason.Reply -> UiTimeline.TopMessage.Icon.Reply
            ListNotificationsNotificationReason.Quote -> UiTimeline.TopMessage.Icon.Reply
            is ListNotificationsNotificationReason.Unknown -> UiTimeline.TopMessage.Icon.Info
            ListNotificationsNotificationReason.StarterpackJoined -> UiTimeline.TopMessage.Icon.Info
            ListNotificationsNotificationReason.Unverified -> UiTimeline.TopMessage.Icon.Info
            ListNotificationsNotificationReason.Verified -> UiTimeline.TopMessage.Icon.Info
            ListNotificationsNotificationReason.LikeViaRepost -> UiTimeline.TopMessage.Icon.Favourite
            ListNotificationsNotificationReason.RepostViaRepost -> UiTimeline.TopMessage.Icon.Retweet
            ListNotificationsNotificationReason.SubscribedPost -> UiTimeline.TopMessage.Icon.Info
        }

private val ListNotificationsNotificationReason.type: UiTimeline.TopMessage.MessageType
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like -> UiTimeline.TopMessage.MessageType.Bluesky.Like
            ListNotificationsNotificationReason.Repost -> UiTimeline.TopMessage.MessageType.Bluesky.Repost
            ListNotificationsNotificationReason.Follow -> UiTimeline.TopMessage.MessageType.Bluesky.Follow
            ListNotificationsNotificationReason.Mention -> UiTimeline.TopMessage.MessageType.Bluesky.Mention
            ListNotificationsNotificationReason.Reply -> UiTimeline.TopMessage.MessageType.Bluesky.Reply
            ListNotificationsNotificationReason.Quote -> UiTimeline.TopMessage.MessageType.Bluesky.Quote
            is ListNotificationsNotificationReason.Unknown -> UiTimeline.TopMessage.MessageType.Bluesky.UnKnown
            ListNotificationsNotificationReason.StarterpackJoined -> UiTimeline.TopMessage.MessageType.Bluesky.StarterpackJoined
            ListNotificationsNotificationReason.Unverified -> UiTimeline.TopMessage.MessageType.Bluesky.UnKnown
            ListNotificationsNotificationReason.Verified -> UiTimeline.TopMessage.MessageType.Bluesky.UnKnown
            ListNotificationsNotificationReason.LikeViaRepost -> UiTimeline.TopMessage.MessageType.Bluesky.Like
            ListNotificationsNotificationReason.RepostViaRepost -> UiTimeline.TopMessage.MessageType.Bluesky.Repost
            ListNotificationsNotificationReason.SubscribedPost -> UiTimeline.TopMessage.MessageType.Bluesky.UnKnown
        }

internal fun PostView.render(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
    references: Map<ReferenceType, List<StatusContent>> = mapOf(),
) = UiTimeline(
    topMessage = null,
    content = renderStatus(accountKey, event, references),
)

internal fun PostView.renderStatus(
    accountKey: MicroBlogKey,
    event: StatusEvent.Bluesky,
    references: Map<ReferenceType, List<StatusContent>> = mapOf(),
): UiTimeline.ItemContent.Status {
    val user = author.render(accountKey)
    val isFromMe = user.key == accountKey
    val statusKey =
        MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        )
    val parent =
        references[ReferenceType.Reply]?.firstOrNull()?.let {
            when (it) {
                is StatusContent.Bluesky -> it.data
                else -> null
            }
        }

    val sensitive =
        this.labels.orEmpty().any {
            it.`val` in sensitiveLabels
        }
    val url =
        buildString {
            append("https://bsky.app/profile/")
            append(author.handle)
            append("/post/")
            append(uri.atUri.substringAfterLast("/"))
        }
    val fxUrl =
        buildString {
            append("https://fxbsky.app/profile/")
            append(author.handle)
            append("/post/")
            append(uri.atUri.substringAfterLast("/"))
        }

    return UiTimeline.ItemContent.Status(
        platformType = PlatformType.Bluesky,
        user = user,
        images = findMedias(this),
        card = findCard(this),
        statusKey = statusKey,
        content = parseBlueskyJson(record, accountKey),
        poll = null,
        quote = listOfNotNull(findQuote(accountKey, this, event)).toImmutableList(),
        contentWarning = null,
        parents =
            listOfNotNull(
                parent?.renderStatus(accountKey, event),
            ).toPersistentList(),
        actions =
            listOfNotNull(
                ActionMenu.Item(
                    icon = ActionMenu.Item.Icon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(replyCount ?: 0),
                    onClicked = {
                        launcher.launch(
                            AppDeepLink.Compose.Reply(
                                accountKey = accountKey,
                                statusKey = statusKey,
                            ),
                        )
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = if (viewer?.repost?.atUri != null) ActionMenu.Item.Icon.Unretweet else ActionMenu.Item.Icon.Retweet,
                            text =
                                ActionMenu.Item.Text.Localized(
                                    if (viewer?.repost?.atUri !=
                                        null
                                    ) {
                                        ActionMenu.Item.Text.Localized.Type.Unretweet
                                    } else {
                                        ActionMenu.Item.Text.Localized.Type.Retweet
                                    },
                                ),
                            count = UiNumber(repostCount ?: 0),
                            color = if (viewer?.repost?.atUri != null) ActionMenu.Item.Color.PrimaryColor else null,
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon =
                                    if (viewer?.repost?.atUri !=
                                        null
                                    ) {
                                        ActionMenu.Item.Icon.Unretweet
                                    } else {
                                        ActionMenu.Item.Icon.Retweet
                                    },
                                text =
                                    ActionMenu.Item.Text.Localized(
                                        if (viewer?.repost?.atUri !=
                                            null
                                        ) {
                                            ActionMenu.Item.Text.Localized.Type.Unretweet
                                        } else {
                                            ActionMenu.Item.Text.Localized.Type.Retweet
                                        },
                                    ),
                                count = UiNumber(repostCount ?: 0),
                                color = if (viewer?.repost?.atUri != null) ActionMenu.Item.Color.PrimaryColor else null,
                                onClicked = {
                                    event.reblog(
                                        statusKey = statusKey,
                                        cid = cid.cid,
                                        uri = uri.atUri,
                                        repostUri = viewer?.repost?.atUri,
                                    )
                                },
                            ),
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Quote,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                count = UiNumber(quoteCount ?: 0),
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
                ActionMenu.Item(
                    icon = if (viewer?.like?.atUri != null) ActionMenu.Item.Icon.Unlike else ActionMenu.Item.Icon.Like,
                    text =
                        ActionMenu.Item.Text.Localized(
                            if (viewer?.like?.atUri !=
                                null
                            ) {
                                ActionMenu.Item.Text.Localized.Type.Unlike
                            } else {
                                ActionMenu.Item.Text.Localized.Type.Like
                            },
                        ),
                    count = UiNumber(likeCount ?: 0),
                    color = if (viewer?.like?.atUri != null) ActionMenu.Item.Color.Red else null,
                    onClicked = {
                        event.like(
                            statusKey = statusKey,
                            cid = cid.cid,
                            uri = uri.atUri,
                            likedUri = viewer?.like?.atUri,
                        )
                    },
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        listOfNotNull(
                            ActionMenu.Item(
                                icon =
                                    if (viewer?.bookmarked ==
                                        true
                                    ) {
                                        ActionMenu.Item.Icon.Unbookmark
                                    } else {
                                        ActionMenu.Item.Icon.Bookmark
                                    },
                                text =
                                    ActionMenu.Item.Text.Localized(
                                        if (viewer?.bookmarked ==
                                            true
                                        ) {
                                            ActionMenu.Item.Text.Localized.Type.Unbookmark
                                        } else {
                                            ActionMenu.Item.Text.Localized.Type.Bookmark
                                        },
                                    ),
                                count = UiNumber(bookmarkCount ?: 0),
                                onClicked = {
                                    if (viewer?.bookmarked == true) {
                                        event.unbookmark(
                                            statusKey = statusKey,
                                            uri = uri.atUri,
                                        )
                                    } else {
                                        event.bookmark(
                                            statusKey = statusKey,
                                            uri = uri.atUri,
                                            cid = cid.cid,
                                        )
                                    }
                                },
                            ),
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                shareContent = url,
                            ),
                            ActionMenu.Item(
                                icon = ActionMenu.Item.Icon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.FxShare),
                                shareContent = fxUrl,
                            ),
                            if (isFromMe) {
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Delete,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                    color = ActionMenu.Item.Color.Red,
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
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Report,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                    color = ActionMenu.Item.Color.Red,
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
        sensitive = sensitive,
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
        url = url,
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
        nameInternal =
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
        nameInternal =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key = userKey,
        banner = null,
        description = description?.let { parser.parse(it) }?.toHtml(accountKey)?.toUi(),
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
        nameInternal =
            Element("span")
                .apply {
                    addChildren(TextNode(displayName.orEmpty()))
                }.toUi(),
        handle = "@${handle.handle}",
        key = userKey,
        banner = banner?.uri,
        description = description?.let { parser.parse(it) }?.toHtml(accountKey)?.toUi(),
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
            title =
                embed.value.external.title.takeIf {
                    it.isNotEmpty()
                } ?: embed.value.external.uri.uri,
            description =
                embed.value.external.description.takeIf {
                    it.isNotEmpty()
                } ?: if (embed.value.external.title
                        .isEmpty()
                ) {
                    null
                } else {
                    embed.value.external.uri.uri
                },
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

        is PostViewEmbedUnion.RecordWithMediaView -> {
            when (val media = embed.value.media) {
                is RecordWithMediaViewMediaUnion.ExternalView -> {
                    findMediaFromExternal(media.value)
                }

                is RecordWithMediaViewMediaUnion.ImagesView ->
                    media.value.images
                        .map {
                            UiMedia.Image(
                                url = it.fullsize.uri,
                                previewUrl = it.thumb.uri,
                                description = it.alt,
                                width = it.aspectRatio?.width?.toFloat() ?: 0f,
                                height = it.aspectRatio?.height?.toFloat() ?: 0f,
                                sensitive = false,
                            )
                        }.toPersistentList()

                is RecordWithMediaViewMediaUnion.VideoView ->
                    persistentListOf(
                        UiMedia.Video(
                            url = media.value.playlist.uri,
                            thumbnailUrl = media.value.thumbnail?.uri ?: "",
                            description = media.value.alt,
                            width =
                                media.value.aspectRatio
                                    ?.width
                                    ?.toFloat() ?: 0f,
                            height =
                                media.value.aspectRatio
                                    ?.height
                                    ?.toFloat() ?: 0f,
                        ),
                    )

                is RecordWithMediaViewMediaUnion.Unknown -> persistentListOf()
            }
        }

        is PostViewEmbedUnion.ExternalView -> {
            findMediaFromExternal(embed.value)
        }

        else -> persistentListOf()
    }

private fun findMediaFromExternal(value: ExternalView): PersistentList<UiMedia> {
    val url = Url(value.external.uri.uri)
    return url.segments.lastOrNull()?.let {
        if (it.endsWith(".gif", ignoreCase = true)) {
            val height = url.parameters["hh"]?.toFloatOrNull() ?: 0f
            val width = url.parameters["ww"]?.toFloatOrNull() ?: 0f
            persistentListOf(
                UiMedia.Gif(
                    url = value.external.uri.uri,
                    previewUrl = value.external.thumb?.uri ?: "",
                    description = value.external.description,
                    width = width,
                    height = height,
                ),
            )
        } else if (
            it.endsWith(".png", ignoreCase = true) ||
            it.endsWith(".jpg", ignoreCase = true) ||
            it.endsWith(".jpeg", ignoreCase = true) ||
            it.endsWith(".webp", ignoreCase = true)
        ) {
            persistentListOf(
                UiMedia.Image(
                    url = value.external.uri.uri,
                    previewUrl = value.external.thumb?.uri ?: "",
                    description = value.external.description,
                    width = 0f,
                    height = 0f,
                    sensitive = false,
                ),
            )
        } else {
            persistentListOf()
        }
    } ?: persistentListOf()
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
            val url =
                buildString {
                    append("https://bsky.app/profile/")
                    append(record.value.author.handle)
                    append("/post/")
                    append(
                        record.value.uri.atUri
                            .substringAfterLast("/"),
                    )
                }
            val fxUrl =
                buildString {
                    append("https://fxbsky.app/profile/")
                    append(record.value.author.handle)
                    append("/post/")
                    append(
                        record.value.uri.atUri
                            .substringAfterLast("/"),
                    )
                }
            UiTimeline.ItemContent.Status(
                user = user,
                images =
                    record.value.embeds
                        .orEmpty()
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
                                is RecordViewRecordEmbedUnion.RecordWithMediaView ->
                                    when (val media = it.value.media) {
                                        is RecordWithMediaViewMediaUnion.ImagesView ->
                                            media.value.images.map {
                                                UiMedia.Image(
                                                    url = it.fullsize.uri,
                                                    previewUrl = it.thumb.uri,
                                                    description = it.alt,
                                                    width = it.aspectRatio?.width?.toFloat() ?: 0f,
                                                    height = it.aspectRatio?.height?.toFloat() ?: 0f,
                                                    sensitive = false,
                                                )
                                            }
                                        is RecordWithMediaViewMediaUnion.VideoView ->
                                            persistentListOf(
                                                UiMedia.Video(
                                                    url = media.value.playlist.uri,
                                                    thumbnailUrl = media.value.thumbnail?.uri ?: "",
                                                    description = media.value.alt,
                                                    width =
                                                        media.value.aspectRatio
                                                            ?.width
                                                            ?.toFloat() ?: 0f,
                                                    height =
                                                        media.value.aspectRatio
                                                            ?.height
                                                            ?.toFloat() ?: 0f,
                                                ),
                                            )
                                        else -> null
                                    }

                                else -> null
                            }
                        }.flatten()
                        .toImmutableList(),
                card =
                    record.value.embeds?.firstNotNullOfOrNull {
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
                    },
                statusKey = statusKey,
                content = parseBlueskyJson(record.value.value, accountKey),
                actions =
                    listOfNotNull(
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.Reply,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                            count = UiNumber(record.value.replyCount ?: 0),
                            onClicked = {
                                launcher.launch(
                                    AppDeepLink.Compose.Reply(
                                        accountKey = accountKey,
                                        statusKey = statusKey,
                                    ),
                                )
                            },
                        ),
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.Retweet,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                                    count = UiNumber(record.value.repostCount ?: 0),
                                ),
                            actions =
                                listOfNotNull(
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Retweet,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                                        count = UiNumber(record.value.repostCount ?: 0),
                                        onClicked = {
                                            event.reblog(
                                                statusKey = statusKey,
                                                cid = record.value.cid.cid,
                                                uri = record.value.uri.atUri,
                                                repostUri = null,
                                            )
                                        },
                                    ),
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Quote,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                        count = UiNumber(record.value.quoteCount ?: 0),
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
                        ActionMenu.Item(
                            icon = ActionMenu.Item.Icon.Like,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                            count = UiNumber(record.value.likeCount ?: 0),
                            onClicked = {
                                event.like(
                                    statusKey = statusKey,
                                    cid = record.value.cid.cid,
                                    uri = record.value.uri.atUri,
                                    likedUri = null,
                                )
                            },
                        ),
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = ActionMenu.Item.Icon.More,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                ),
                            actions =
                                listOfNotNull(
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Share,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                        shareContent = url,
                                    ),
                                    ActionMenu.Item(
                                        icon = ActionMenu.Item.Icon.Share,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.FxShare),
                                        shareContent = fxUrl,
                                    ),
                                    if (isFromMe) {
                                        ActionMenu.Item(
                                            icon = ActionMenu.Item.Icon.Delete,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
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
                                        ActionMenu.Item(
                                            icon = ActionMenu.Item.Icon.Report,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
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
                createdAt =
                    record.value.indexedAt
                        .toUi(),
                sensitive =
                    record.value.labels.orEmpty().any {
                        it.`val` in sensitiveLabels
                    },
                onClicked = {
                    launcher.launch(
                        AppDeepLink.StatusDetail(
                            accountKey = accountKey,
                            statusKey = statusKey,
                        ),
                    )
                },
                platformType = PlatformType.Bluesky,
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
                url = url,
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
        likedCount = UiNumber(likeCount ?: 0),
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
        text = parseBluesky(text, facets.orEmpty(), accountKey),
    )
