package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.bookmark.BookmarkView
import app.bsky.bookmark.BookmarkViewItemUnion
import app.bsky.embed.ExternalView
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GeneratorView
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.Repost
import app.bsky.graph.ListView
import app.bsky.notification.ListNotificationsNotification
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
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.toAccountType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toHtml
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import io.ktor.http.Url
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.collections.immutable.ImmutableMap
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
import sh.christian.ozone.api.AtUri
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

internal fun BookmarkView.render(accountKey: MicroBlogKey): UiTimelineV2? =
    when (val content = item) {
        is BookmarkViewItemUnion.BlockedPost -> null
        is BookmarkViewItemUnion.NotFoundPost -> null
        is BookmarkViewItemUnion.PostView -> content.value.render(accountKey)
        is BookmarkViewItemUnion.Unknown -> null
    }

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
                                    DeeplinkRoute.Profile
                                        .User(
                                            accountType = AccountType.Specific(accountKey),
                                            userKey =
                                                MicroBlogKey(
                                                    id = feature.value.did.did,
                                                    host = accountKey.host,
                                                ),
                                        ).toUri(),
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
                                    DeeplinkRoute
                                        .Search(
                                            accountType = AccountType.Specific(accountKey),
                                            query = facetText,
                                        ).toUri(),
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

private val ListNotificationsNotificationReason.icon: UiIcon
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like -> UiIcon.Favourite
            ListNotificationsNotificationReason.Repost -> UiIcon.Retweet
            ListNotificationsNotificationReason.Follow -> UiIcon.Follow
            ListNotificationsNotificationReason.Mention -> UiIcon.Mention
            ListNotificationsNotificationReason.Reply -> UiIcon.Reply
            ListNotificationsNotificationReason.Quote -> UiIcon.Reply
            is ListNotificationsNotificationReason.Unknown -> UiIcon.Info
            ListNotificationsNotificationReason.StarterpackJoined -> UiIcon.Info
            ListNotificationsNotificationReason.Unverified -> UiIcon.Info
            ListNotificationsNotificationReason.Verified -> UiIcon.Info
            ListNotificationsNotificationReason.LikeViaRepost -> UiIcon.Favourite
            ListNotificationsNotificationReason.RepostViaRepost -> UiIcon.Retweet
            ListNotificationsNotificationReason.SubscribedPost -> UiIcon.Info
            ListNotificationsNotificationReason.ContactMatch -> UiIcon.Info
        }

private val ListNotificationsNotificationReason.type: UiTimelineV2.Message.Type
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Favourite,
                )

            ListNotificationsNotificationReason.Repost ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                )

            ListNotificationsNotificationReason.Follow ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Follow,
                )

            ListNotificationsNotificationReason.Mention ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Mention,
                )

            ListNotificationsNotificationReason.Reply ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Reply,
                )

            ListNotificationsNotificationReason.Quote ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Quote,
                )

            is ListNotificationsNotificationReason.Unknown ->
                UiTimelineV2.Message.Type.Unknown(
                    rawType = rawValue,
                )

            ListNotificationsNotificationReason.StarterpackJoined ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.StarterpackJoined,
                )

            ListNotificationsNotificationReason.Unverified ->
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "Unverified",
                )

            ListNotificationsNotificationReason.Verified ->
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "Verified",
                )

            ListNotificationsNotificationReason.LikeViaRepost ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Favourite,
                )

            ListNotificationsNotificationReason.RepostViaRepost ->
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                )

            ListNotificationsNotificationReason.SubscribedPost ->
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "SubscribedPost",
                )

            ListNotificationsNotificationReason.ContactMatch ->
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "ContactMatch",
                )
        }

internal fun List<FeedViewPost>.render(accountKey: MicroBlogKey): List<UiTimelineV2> {
    return this.map { it.render(accountKey) }
}

internal fun List<ListNotificationsNotification>.render(
    accountKey: MicroBlogKey,
    references: ImmutableMap<AtUri, PostView>,
): List<UiTimelineV2> {
    val grouped = this.groupBy { it.reason }.filter { it.value.any() }
    return grouped.flatMap { (reason, items) ->
        when (reason) {
            ListNotificationsNotificationReason.Repost, ListNotificationsNotificationReason.Like -> {
                val post =
                    items
                        .first()
                        .record
                        .let {
                            when (reason) {
                                ListNotificationsNotificationReason.Repost -> it.decodeAs<Repost>().subject
                                ListNotificationsNotificationReason.Like -> it.decodeAs<Like>().subject
                            }
                        }.uri
                        .let {
                            references[it]
                        }
                val idSuffix =
                    when (reason) {
                        ListNotificationsNotificationReason.Repost -> "_repost"
                        ListNotificationsNotificationReason.Like -> "_like"
                    }
                listOf(
                    UiTimelineV2.UserList(
                        message =
                            UiTimelineV2.Message(
                                user = null,
                                icon = reason.icon,
                                type = reason.type,
                                statusKey =
                                    MicroBlogKey(
                                        id = items.joinToString("_") { it.uri.atUri } + idSuffix,
                                        host = accountKey.host,
                                    ),
                                clickEvent = ClickEvent.Noop,
                                createdAt = items.first().indexedAt.toUi(),
                                accountType = accountKey.toAccountType(),
                            ),
                        users =
                            items
                                .map {
                                    it.author.render(accountKey)
                                }.toImmutableList(),
                        createdAt = items.first().indexedAt.toUi(),
                        post = post?.render(accountKey),
                        statusKey =
                            MicroBlogKey(
                                id = items.joinToString("_") { it.uri.atUri } + idSuffix,
                                host = accountKey.host,
                            ),
                        accountType = accountKey.toAccountType(),
                    ),
                )
            }

            ListNotificationsNotificationReason.Follow -> {
                listOf(
                    UiTimelineV2.UserList(
                        message =
                            UiTimelineV2.Message(
                                user = null,
                                icon = reason.icon,
                                type = reason.type,
                                statusKey =
                                    MicroBlogKey(
                                        id = items.joinToString("_") { it.uri.atUri } + "_follow",
                                        host = accountKey.host,
                                    ),
                                clickEvent = ClickEvent.Noop,
                                createdAt = items.first().indexedAt.toUi(),
                                accountType = accountKey.toAccountType(),
                            ),
                        users =
                            items
                                .map {
                                    it.author.render(accountKey)
                                }.toImmutableList(),
                        createdAt = items.first().indexedAt.toUi(),
                        post = null,
                        statusKey =
                            MicroBlogKey(
                                id = items.joinToString("_") { it.uri.atUri } + "_follow",
                                host = accountKey.host,
                            ),
                        accountType = accountKey.toAccountType(),
                    ),
                )
            }

            ListNotificationsNotificationReason.LikeViaRepost,
            ListNotificationsNotificationReason.RepostViaRepost,
            ListNotificationsNotificationReason.SubscribedPost,
            ListNotificationsNotificationReason.ContactMatch,
            ListNotificationsNotificationReason.Mention,
            ListNotificationsNotificationReason.Reply,
            ListNotificationsNotificationReason.Quote,
            -> {
                items.mapNotNull {
                    references[it.uri]
                        ?.render(
                            accountKey,
                        )?.copy(
                            message =
                                UiTimelineV2.Message(
                                    user = it.author.render(accountKey),
                                    icon = reason.icon,
                                    type = reason.type,
                                    statusKey =
                                        MicroBlogKey(
                                            id = it.uri.atUri,
                                            host = accountKey.host,
                                        ),
                                    clickEvent = ClickEvent.Noop,
                                    createdAt = it.indexedAt.toUi(),
                                    accountType = accountKey.toAccountType(),
                                ),
                        )
                }
            }

            else -> {
                items.map {
                    UiTimelineV2.User(
                        message =
                            UiTimelineV2.Message(
                                user = it.author.render(accountKey),
                                icon = reason.icon,
                                type = reason.type,
                                statusKey =
                                    MicroBlogKey(
                                        id = it.uri.atUri,
                                        host = accountKey.host,
                                    ),
                                clickEvent = ClickEvent.Noop,
                                createdAt = it.indexedAt.toUi(),
                                accountType = accountKey.toAccountType(),
                            ),
                        value = it.author.render(accountKey),
                        statusKey =
                            MicroBlogKey(
                                id = it.uri.atUri,
                                host = accountKey.host,
                            ),
                        createdAt = it.indexedAt.toUi(),
                        accountType = accountKey.toAccountType(),
                    )
                }
            }
        }
    }
}

private fun FeedViewPost.render(accountKey: MicroBlogKey): UiTimelineV2 {
    val renderedPost = post.render(accountKey)
    val message =
        when (val reason = reason) {
            is FeedViewPostReasonUnion.ReasonPin ->
                UiTimelineV2.Message(
                    user = renderedPost.user,
                    icon = UiIcon.Pin,
                    type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Pinned),
                    statusKey =
                        MicroBlogKey(
                            id = post.uri.atUri + "_pin_${renderedPost.user?.key}",
                            host = accountKey.host,
                        ),
                    createdAt = renderedPost.createdAt,
                    accountType = accountKey.toAccountType(),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                statusKey = renderedPost.statusKey,
                                accountType = accountKey.toAccountType(),
                            ),
                        ),
                )

            is FeedViewPostReasonUnion.ReasonRepost -> {
                val user = reason.value.by.render(accountKey)
                UiTimelineV2.Message(
                    user = user,
                    icon = UiIcon.Retweet,
                    type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Repost),
                    statusKey =
                        MicroBlogKey(
                            id = post.uri.atUri + "_reblog_${user.key}",
                            host = accountKey.host,
                        ),
                    createdAt = renderedPost.createdAt,
                    accountType = accountKey.toAccountType(),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Profile.User(
                                accountType = AccountType.Specific(accountKey),
                                userKey = user.key,
                            ),
                        ),
                )
            }

            else -> null
        }
    val postForTimeline =
        when (reason) {
            is FeedViewPostReasonUnion.ReasonRepost ->
                renderedPost.copy(
                    statusKey = message?.statusKey ?: renderedPost.statusKey,
                    internalRepost = renderedPost,
                )
            else -> renderedPost
        }

    val reply =
        when (val reply = reply?.parent) {
            is ReplyRefParentUnion.PostView ->
                if (reply.value.uri != post.uri) {
                    reply.value
                } else {
                    null
                }

            else -> null
        }?.render(accountKey)
    return postForTimeline.copy(
        message = message,
        parents = listOfNotNull(reply).toImmutableList(),
    )
}

internal fun PostView.render(accountKey: MicroBlogKey): UiTimelineV2.Post {
    val user = author.render(accountKey)
    val isFromMe = user.key == accountKey
    val statusKey =
        MicroBlogKey(
            id = uri.atUri,
            host = accountKey.host,
        )

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

    return UiTimelineV2.Post(
        platformType = PlatformType.Bluesky,
        user = user,
        images = findMedias(this),
        card = findCard(this),
        statusKey = statusKey,
        content = parseBlueskyJson(record, accountKey),
        poll = null,
        quote = listOfNotNull(findQuote(accountKey, this)).toImmutableList(),
        contentWarning = null,
        parents = persistentListOf(),
        actions =
            listOfNotNull(
                ActionMenu.Item(
                    icon = UiIcon.Reply,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                    count = UiNumber(replyCount ?: 0),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Compose
                                .Reply(
                                    accountKey = accountKey,
                                    statusKey = statusKey,
                                ),
                        ),
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = if (viewer?.repost?.atUri != null) UiIcon.Unretweet else UiIcon.Retweet,
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
                            ActionMenu.blueskyReblog(
                                accountKey = accountKey,
                                postKey = statusKey,
                                cid = cid.cid,
                                uri = uri.atUri,
                                count = repostCount ?: 0,
                                repostUri = viewer?.repost?.atUri,
                            ),
                            ActionMenu.Item(
                                icon = UiIcon.Quote,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                count = UiNumber(quoteCount ?: 0),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Compose
                                            .Quote(
                                                accountKey = accountKey,
                                                statusKey = statusKey,
                                            ),
                                    ),
                            ),
                        ).toImmutableList(),
                ),
                ActionMenu.blueskyLike(
                    accountKey = accountKey,
                    postKey = statusKey,
                    cid = cid.cid,
                    uri = uri.atUri,
                    count = likeCount ?: 0,
                    likedUri = viewer?.like?.atUri,
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        buildList {
                            add(
                                ActionMenu.blueskyBookmark(
                                    accountKey = accountKey,
                                    postKey = statusKey,
                                    cid = cid.cid,
                                    uri = uri.atUri,
                                    count = bookmarkCount ?: 0,
                                    bookmarked = viewer?.bookmarked == true,
                                ),
                            )
                            add(
                                ActionMenu.Item(
                                    icon = UiIcon.Share,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                    clickEvent =
                                        ClickEvent.Deeplink(
                                            DeeplinkRoute.Status
                                                .ShareSheet(
                                                    statusKey = statusKey,
                                                    accountType = AccountType.Specific(accountKey),
                                                    shareUrl = url,
                                                    fxShareUrl = fxUrl,
                                                ),
                                        ),
                                ),
                            )

                            if (isFromMe) {
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Delete,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                        color = ActionMenu.Item.Color.Red,
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .DeleteConfirm(
                                                        accountType =
                                                            AccountType.Specific(
                                                                accountKey,
                                                            ),
                                                        statusKey = statusKey,
                                                    ),
                                            ),
                                    ),
                                )
                            } else {
                                add(ActionMenu.Divider)
                                addAll(
                                    userActionsMenu(
                                        accountKey = accountKey,
                                        userKey = user.key,
                                        handle = user.handle.canonical,
                                    ),
                                )
                                add(ActionMenu.Divider)
                                add(
                                    ActionMenu.Item(
                                        icon = UiIcon.Report,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                        color = ActionMenu.Item.Color.Red,
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .BlueskyReport(
                                                        statusKey = statusKey,
                                                        accountType =
                                                            AccountType.Specific(
                                                                accountKey,
                                                            ),
                                                    ),
                                            ),
                                    ),
                                )
                            }
                        }.toImmutableList(),
                ),
            ).toImmutableList(),
        createdAt = indexedAt.toUi(),
        sensitive = sensitive,
        accountType = AccountType.Specific(accountKey),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status
                    .Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
            ),
    )
}

internal fun ActionMenu.Companion.blueskyReblog(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    count: Long,
    cid: String,
    uri: String,
    repostUri: String?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_reblog_$postKey",
        icon = if (repostUri != null) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (repostUri != null) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(count),
        color = if (repostUri != null) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Reblog(
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    repostUri = repostUri,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

internal fun ActionMenu.Companion.blueskyLike(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    count: Long,
    cid: String,
    uri: String,
    likedUri: String?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_like_$postKey",
        icon = if (likedUri != null) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (likedUri != null) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        color = if (likedUri != null) ActionMenu.Item.Color.Red else null,
        count = UiNumber(count), // like count is updated via websocket, so we don't update it here
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Like(
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    likedUri = likedUri,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

internal fun ActionMenu.Companion.blueskyBookmark(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    uri: String,
    cid: String,
    count: Long,
    bookmarked: Boolean,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_bookmark_$postKey",
        icon = if (bookmarked) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) {
                    ActionMenu.Item.Text.Localized.Type.Unbookmark
                } else {
                    ActionMenu.Item.Text.Localized.Type.Bookmark
                },
            ),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Bookmark(
                    postKey = postKey,
                    uri = uri,
                    cid = cid,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                    count = count,
                )
            },
    )

internal fun chat.bsky.actor.ProfileViewBasic.render(accountKey: MicroBlogKey): UiProfile {
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
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
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
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
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
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
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
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
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
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
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
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
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
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
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
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile
                    .User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = userKey,
                    ),
            ),
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

private fun findMedias(postView: PostView): SerializableImmutableList<UiMedia> =
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
): UiTimelineV2.Post? =
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
): UiTimelineV2.Post? =
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
            UiTimelineV2.Post(
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
                                                    height =
                                                        it.aspectRatio?.height?.toFloat()
                                                            ?: 0f,
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
                            icon = UiIcon.Reply,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                            count = UiNumber(record.value.replyCount ?: 0),
                            clickEvent =
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Compose
                                        .Reply(
                                            accountKey = accountKey,
                                            statusKey = statusKey,
                                        ),
                                ),
                        ),
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = UiIcon.Retweet,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
                                    count = UiNumber(record.value.repostCount ?: 0),
                                ),
                            actions =
                                listOfNotNull(
                                    ActionMenu.blueskyReblog(
                                        accountKey = accountKey,
                                        postKey = statusKey,
                                        cid = record.value.cid.cid,
                                        uri = record.value.uri.atUri,
                                        count = record.value.repostCount ?: 0,
                                        repostUri = null,
                                    ),
                                    ActionMenu.Item(
                                        icon = UiIcon.Quote,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                        count = UiNumber(record.value.quoteCount ?: 0),
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Compose
                                                    .Quote(
                                                        accountKey = accountKey,
                                                        statusKey = statusKey,
                                                    ),
                                            ),
                                    ),
                                ).toImmutableList(),
                        ),
                        ActionMenu.blueskyLike(
                            accountKey = accountKey,
                            postKey = statusKey,
                            cid = record.value.cid.cid,
                            uri = record.value.uri.atUri,
                            count = record.value.likeCount ?: 0,
                            likedUri = null,
                        ),
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = UiIcon.More,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                ),
                            actions =
                                listOfNotNull(
                                    ActionMenu.Item(
                                        icon = UiIcon.Share,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Status
                                                    .ShareSheet(
                                                        statusKey = statusKey,
                                                        accountType = AccountType.Specific(accountKey),
                                                        shareUrl = url,
                                                        fxShareUrl = fxUrl,
                                                    ),
                                            ),
                                    ),
                                    if (isFromMe) {
                                        ActionMenu.Item(
                                            icon = UiIcon.Delete,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status
                                                        .DeleteConfirm(
                                                            accountType =
                                                                AccountType.Specific(
                                                                    accountKey,
                                                                ),
                                                            statusKey = statusKey,
                                                        ),
                                                ),
                                        )
                                    } else {
                                        ActionMenu.Item(
                                            icon = UiIcon.Report,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status
                                                        .BlueskyReport(
                                                            statusKey = statusKey,
                                                            accountType =
                                                                AccountType.Specific(
                                                                    accountKey,
                                                                ),
                                                        ),
                                                ),
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
                platformType = PlatformType.Bluesky,
                accountType = AccountType.Specific(accountKey),
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Status
                            .Detail(
                                statusKey = statusKey,
                                accountType = AccountType.Specific(accountKey),
                            ),
                    ),
            )
        }

        else -> null
    }

internal fun GeneratorView.render(accountKey: MicroBlogKey) =
    UiList.Feed(
        id = uri.atUri,
        title = displayName,
        description = description,
        avatar = avatar?.uri,
        creator = creator.render(accountKey),
        likedCount = UiNumber(likeCount ?: 0),
        liked = viewer?.like?.atUri != null,
    )

internal fun ListView.render(accountKey: MicroBlogKey) =
    UiList.List(
        id = uri.atUri,
        title = name,
        description = description,
        avatar = avatar?.uri,
        creator = creator.render(accountKey),
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
