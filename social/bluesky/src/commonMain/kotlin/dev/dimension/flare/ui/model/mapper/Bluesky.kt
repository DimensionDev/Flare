package dev.dimension.flare.ui.model.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.bookmark.BookmarkView
import app.bsky.bookmark.BookmarkViewItemUnion
import app.bsky.embed.ExternalView
import app.bsky.embed.GalleryViewItemUnion
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
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.MessageView
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.model.toAccountType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiMedia.Image
import dev.dimension.flare.ui.model.UiMedia.Video
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
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
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
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

internal fun parseBlueskyJson(
    json: JsonContent,
    accountKey: MicroBlogKey,
    sourceLanguages: List<String> = emptyList(),
): UiRichText {
    try {
        return parseBluesky(post = json.decodeAs(), accountKey = accountKey, sourceLanguages = sourceLanguages)
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
                sourceLanguages = sourceLanguages,
            )
        } else {
            return text.orEmpty().toUiPlainText(sourceLanguages)
        }
    }
}

private fun JsonContent.sourceLanguages(): PersistentList<String> {
    val jsonObject = runCatching { decodeAs<JsonObject>() }.getOrNull() ?: return persistentListOf()
    return jsonObject["langs"]
        ?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?.toPersistentList()
        ?: persistentListOf()
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
                is UrlToken -> {
                    FacetFeatureUnion.Link(
                        FacetLink(
                            uri = Uri(token.value),
                        ),
                    )
                }

                is HashTagToken -> {
                    FacetFeatureUnion.Tag(
                        FacetTag(
                            tag = token.value.trimStart('#'),
                        ),
                    )
                }

                is UserNameToken -> {
                    val handle = token.value.removePrefix("@")
                    val didString = resolveMentionDid(handle)
                    FacetFeatureUnion.Mention(
                        FacetMention(
                            did = Did(didString),
                        ),
                    )
                }

                else -> {
                    null
                }
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
    sourceLanguages: List<String> = emptyList(),
): UiRichText =
    parseBluesky(
        text = post.text,
        facets = post.facets.orEmpty(),
        accountKey = accountKey,
        sourceLanguages = sourceLanguages,
    )

private fun parseBluesky(
    text: String,
    facets: List<Facet>,
    accountKey: MicroBlogKey,
    sourceLanguages: List<String> = emptyList(),
): UiRichText {
    val codePoints = text.toByteArray(charset = Charsets.UTF_8)
    val runs = mutableListOf<RenderRun>()

    fun appendText(
        text: String,
        style: RenderTextStyle = RenderTextStyle(),
    ) {
        if (text.isEmpty()) return
        val lastRun = runs.lastOrNull()
        if (lastRun is RenderRun.Text && lastRun.style == style) {
            runs[runs.lastIndex] = lastRun.copy(text = lastRun.text + text)
        } else {
            runs.add(RenderRun.Text(text = text, style = style))
        }
    }

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
        appendText(beforeFacetText)
        if (end - start < 0) {
            continue
        }
        val facetText = codePoints.drop(start).take(end - start).stringify()
        // TODO: multiple features
        val feature = facet.features.firstOrNull()
        if (feature != null) {
            when (feature) {
                is FacetFeatureUnion.Link -> {
                    appendText(
                        facetText,
                        RenderTextStyle(
                            link = feature.value.uri.uri,
                        ),
                    )
                }

                is FacetFeatureUnion.Mention -> {
                    appendText(
                        facetText,
                        RenderTextStyle(
                            link =
                                DeeplinkRoute.Profile
                                    .User(
                                        accountType = AccountType.Specific(accountKey),
                                        userKey =
                                            MicroBlogKey(
                                                id = feature.value.did.did,
                                                host = accountKey.host,
                                            ),
                                    ).toUri(),
                        ),
                    )
                }

                is FacetFeatureUnion.Tag -> {
                    appendText(
                        facetText,
                        RenderTextStyle(
                            link =
                                DeeplinkRoute
                                    .Search(
                                        accountType = AccountType.Specific(accountKey),
                                        query = facetText,
                                    ).toUri(),
                        ),
                    )
                }

                is FacetFeatureUnion.Unknown -> {
                    appendText(facetText)
                }
            }
        } else {
            appendText(facetText)
        }
        codePointIndex = end
    }
    val afterFacetText = codePoints.drop(codePointIndex).stringify()
    appendText(afterFacetText)
    return uiRichTextOf(
        renderRuns =
            if (runs.isEmpty()) {
                emptyList()
            } else {
                listOf(RenderContent.Text(runs = runs.toImmutableList()))
            },
        sourceLanguages = sourceLanguages,
    )
}

private fun List<Token>.toUiRichText(accountKey: MicroBlogKey): UiRichText {
    val runs =
        buildList<RenderRun> {
            this@toUiRichText.forEach { token ->
                when (token) {
                    is CashTagToken -> {
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style = RenderTextStyle(link = DeeplinkRoute.Search(AccountType.Specific(accountKey), token.value).toUri()),
                            ),
                        )
                    }

                    is EmojiToken, is StringToken -> {
                        add(
                            RenderRun.Text(
                                text = token.value,
                            ),
                        )
                    }

                    is HashTagToken -> {
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style = RenderTextStyle(link = DeeplinkRoute.Search(AccountType.Specific(accountKey), token.value).toUri()),
                            ),
                        )
                    }

                    is UrlToken -> {
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style = RenderTextStyle(link = token.value),
                            ),
                        )
                    }

                    is UserNameToken -> {
                        add(
                            RenderRun.Text(
                                text = token.value,
                                style =
                                    RenderTextStyle(
                                        link =
                                            DeeplinkRoute.Profile
                                                .UserNameWithHost(
                                                    accountType = AccountType.Specific(accountKey),
                                                    userName = token.value.trimStart('@'),
                                                    host = accountKey.host,
                                                ).toUri(),
                                    ),
                            ),
                        )
                    }
                }
            }
        }
    return uiRichTextOf(
        renderRuns =
            if (runs.isEmpty()) {
                emptyList()
            } else {
                listOf(RenderContent.Text(runs = runs.toImmutableList()))
            },
    )
}

private val ListNotificationsNotificationReason.icon: UiIcon
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like -> UiIcon.Like
            ListNotificationsNotificationReason.Repost -> UiIcon.Retweet
            ListNotificationsNotificationReason.Follow -> UiIcon.Follow
            ListNotificationsNotificationReason.Mention -> UiIcon.Mention
            ListNotificationsNotificationReason.Reply -> UiIcon.Reply
            ListNotificationsNotificationReason.Quote -> UiIcon.Reply
            is ListNotificationsNotificationReason.Unknown -> UiIcon.Info
            ListNotificationsNotificationReason.StarterpackJoined -> UiIcon.Info
            ListNotificationsNotificationReason.Unverified -> UiIcon.Info
            ListNotificationsNotificationReason.Verified -> UiIcon.Info
            ListNotificationsNotificationReason.LikeViaRepost -> UiIcon.Like
            ListNotificationsNotificationReason.RepostViaRepost -> UiIcon.Retweet
            ListNotificationsNotificationReason.SubscribedPost -> UiIcon.Info
            ListNotificationsNotificationReason.ContactMatch -> UiIcon.Info
        }

private val ListNotificationsNotificationReason.type: UiTimelineV2.Message.Type
    get() =
        when (this) {
            ListNotificationsNotificationReason.Like -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Like,
                )
            }

            ListNotificationsNotificationReason.Repost -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                )
            }

            ListNotificationsNotificationReason.Follow -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Follow,
                )
            }

            ListNotificationsNotificationReason.Mention -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Mention,
                )
            }

            ListNotificationsNotificationReason.Reply -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Reply,
                )
            }

            ListNotificationsNotificationReason.Quote -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Quote,
                )
            }

            is ListNotificationsNotificationReason.Unknown -> {
                UiTimelineV2.Message.Type.Unknown(
                    rawType = rawValue,
                )
            }

            ListNotificationsNotificationReason.StarterpackJoined -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.StarterpackJoined,
                )
            }

            ListNotificationsNotificationReason.Unverified -> {
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "Unverified",
                )
            }

            ListNotificationsNotificationReason.Verified -> {
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "Verified",
                )
            }

            ListNotificationsNotificationReason.LikeViaRepost -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Like,
                )
            }

            ListNotificationsNotificationReason.RepostViaRepost -> {
                UiTimelineV2.Message.Type.Localized(
                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                )
            }

            ListNotificationsNotificationReason.SubscribedPost -> {
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "SubscribedPost",
                )
            }

            ListNotificationsNotificationReason.ContactMatch -> {
                UiTimelineV2.Message.Type.Unknown(
                    rawType = "ContactMatch",
                )
            }
        }

internal fun List<FeedViewPost>.render(accountKey: MicroBlogKey): List<UiTimelineV2> = this.map { it.render(accountKey) }

internal fun List<ListNotificationsNotification>.render(
    accountKey: MicroBlogKey,
    references: ImmutableMap<AtUri, PostView>,
): List<UiTimelineV2> {
    val grouped =
        this
            .groupBy {
                when (it.reason) {
                    ListNotificationsNotificationReason.Repost -> {
                        it.reason to
                            it.record
                                .decodeAs<Repost>()
                                .subject.uri
                    }

                    ListNotificationsNotificationReason.Like -> {
                        it.reason to
                            it.record
                                .decodeAs<Like>()
                                .subject.uri
                    }

                    else -> {
                        it.reason to null
                    }
                }
            }.filter { it.value.any() }
    return grouped.flatMap { (groupKey, items) ->
        val reason = groupKey.first
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
                        ?.renderTimelineItem(
                            accountKey = accountKey,
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
    val feedReason = reason
    val message =
        when (val reason = feedReason) {
            is FeedViewPostReasonUnion.ReasonPin -> {
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
            }

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

            else -> {
                null
            }
        }
    val reply =
        when (val reply = reply?.parent) {
            is ReplyRefParentUnion.PostView -> {
                if (reply.value.uri != post.uri) {
                    reply.value
                } else {
                    null
                }
            }

            else -> {
                null
            }
        }?.render(accountKey)
    val quote = findQuote(accountKey, post)
    val inlineParents = listOfNotNull(reply).toImmutableList()
    val quotes = listOfNotNull(quote).toImmutableList()
    val presentation =
        UiTimelineV2.PostPresentation(
            message = message,
            inlineParents = inlineParents,
            quotes = quotes,
            repost =
                if (feedReason is FeedViewPostReasonUnion.ReasonRepost) {
                    renderedPost
                } else {
                    null
                },
        )
    val rootPost =
        if (feedReason is FeedViewPostReasonUnion.ReasonRepost) {
            val repostUser = feedReason.value.by.render(accountKey)
            renderedPost.copy(
                statusKey = message?.statusKey ?: renderedPost.statusKey,
                user = repostUser,
                images = persistentListOf(),
                content = UiTranslatableText(original = uiRichTextOf(emptyList())),
                contentWarning = null,
                card = null,
                poll = null,
                references =
                    listOf(
                        UiTimelineV2.Post.Reference(
                            statusKey = renderedPost.statusKey,
                            type = ReferenceType.Retweet,
                        ),
                    ).toImmutableList(),
            )
        } else {
            renderedPost
        }
    return if (
        presentation.message != null ||
        presentation.inlineParents.isNotEmpty() ||
        presentation.quotes.isNotEmpty() ||
        presentation.repost != null
    ) {
        UiTimelineV2.TimelinePostItem(
            post = rootPost,
            presentation = presentation,
        )
    } else {
        rootPost
    }
}

private fun PostView.renderTimelineItem(
    accountKey: MicroBlogKey,
    message: UiTimelineV2.Message? = null,
): UiTimelineV2 {
    val post = render(accountKey)
    val quotes = listOfNotNull(findQuote(accountKey, this)).toImmutableList()
    return if (message != null || quotes.isNotEmpty()) {
        UiTimelineV2.TimelinePostItem(
            post = post,
            presentation =
                UiTimelineV2.PostPresentation(
                    message = message,
                    quotes = quotes,
                ),
        )
    } else {
        post
    }
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

    val sourceLanguages = record.sourceLanguages()
    val quote = findQuote(accountKey, this)
    return UiTimelineV2.Post(
        platformType = PlatformType.Bluesky,
        user = user,
        images = findMedias(this),
        card = findCard(this),
        statusKey = statusKey,
        sourceLanguages = sourceLanguages,
        content = UiTranslatableText(original = parseBlueskyJson(record, accountKey, sourceLanguages)),
        poll = null,
        contentWarning = null,
        references =
            listOfNotNull(
                quote?.let {
                    UiTimelineV2.Post.Reference(
                        statusKey = it.statusKey,
                        type = ReferenceType.Quote,
                    )
                },
            ).toImmutableList(),
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
                    actionFamily = PostActionFamily.Reply,
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
                                actionFamily = PostActionFamily.Quote,
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
                                    actionFamily = PostActionFamily.Share,
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
                                        actionFamily = PostActionFamily.Delete,
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
                                        actionFamily = PostActionFamily.Report,
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

internal fun chat.bsky.actor.ProfileViewBasic.render(accountKey: MicroBlogKey): UiProfile {
    val userKey =
        MicroBlogKey(
            id = did.did,
            host = accountKey.host,
        )
    return UiProfile(
        avatar = avatar?.uri.toUiImage(),
        nameInternal = displayName.orEmpty().toUiPlainText(),
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
        avatar = avatar?.uri.toUiImage(),
        nameInternal = displayName.orEmpty().toUiPlainText(),
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
        avatar = avatar?.uri.toUiImage(),
        nameInternal = displayName.orEmpty().toUiPlainText(),
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
        key = userKey,
        banner = null,
        description = description?.let { parser.parse(it) }?.toUiRichText(accountKey),
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
        avatar = avatar?.uri.toUiImage(),
        nameInternal = displayName.orEmpty().toUiPlainText(),
        handle =
            UiHandle(
                raw = handle.handle,
                host = accountKey.host,
            ),
        key = userKey,
        banner = banner?.uri.toUiImage(),
        description = description?.let { parser.parse(it) }?.toUiRichText(accountKey),
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

                is RecordWithMediaViewMediaUnion.ImagesView -> {
                    media.value.images
                        .map {
                            Image(
                                url = it.fullsize.uri,
                                previewUrl = it.thumb.uri,
                                description = it.alt,
                                width = it.aspectRatio?.width?.toFloat() ?: 0f,
                                height = it.aspectRatio?.height?.toFloat() ?: 0f,
                                sensitive = false,
                            )
                        }.toPersistentList()
                }

                is RecordWithMediaViewMediaUnion.VideoView -> {
                    persistentListOf(
                        Video(
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
                }

                is RecordWithMediaViewMediaUnion.Unknown -> {
                    persistentListOf()
                }

                is RecordWithMediaViewMediaUnion.GalleryView -> {
                    media.value.items
                        .mapNotNull { item ->
                            when (item) {
                                is GalleryViewItemUnion.Unknown -> {
                                    null
                                }

                                is GalleryViewItemUnion.ViewImage -> {
                                    UiMedia.Image(
                                        url = item.value.fullsize.uri,
                                        previewUrl = item.value.thumbnail.uri,
                                        description = item.value.alt,
                                        width =
                                            item.value.aspectRatio.width
                                                .toFloat(),
                                        height =
                                            item.value.aspectRatio.height
                                                .toFloat(),
                                        sensitive = false,
                                    )
                                }
                            }
                        }.toImmutableList()
                }
            }
        }

        is PostViewEmbedUnion.ExternalView -> {
            findMediaFromExternal(embed.value)
        }

        else -> {
            persistentListOf()
        }
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
        is PostViewEmbedUnion.RecordView -> {
            render(accountKey, embed.value.record)
        }

        is PostViewEmbedUnion.RecordWithMediaView -> {
            render(
                accountKey,
                embed.value.record.record,
            )
        }

        else -> {
            null
        }
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
            val sourceLanguages = record.value.value.sourceLanguages()
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
                                is RecordViewRecordEmbedUnion.ImagesView -> {
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
                                }

                                is RecordViewRecordEmbedUnion.RecordWithMediaView -> {
                                    when (val media = it.value.media) {
                                        is RecordWithMediaViewMediaUnion.ImagesView -> {
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
                                        }

                                        is RecordWithMediaViewMediaUnion.VideoView -> {
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
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                }

                                else -> {
                                    null
                                }
                            }
                        }.flatten()
                        .toImmutableList(),
                card =
                    record.value.embeds?.firstNotNullOfOrNull {
                        when (it) {
                            is RecordViewRecordEmbedUnion.ExternalView -> {
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
                            }

                            else -> {
                                null
                            }
                        }
                    },
                statusKey = statusKey,
                sourceLanguages = sourceLanguages,
                content =
                    UiTranslatableText(
                        original =
                            parseBlueskyJson(
                                record.value.value,
                                accountKey,
                                sourceLanguages,
                            ),
                    ),
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
                            actionFamily = PostActionFamily.Reply,
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
                                        actionFamily = PostActionFamily.Quote,
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
                                        actionFamily = PostActionFamily.Share,
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
                                            actionFamily = PostActionFamily.Delete,
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
                                            actionFamily = PostActionFamily.Report,
                                        )
                                    },
                                ).toImmutableList(),
                        ),
                    ).toImmutableList(),
                contentWarning = null,
                poll = null,
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

        else -> {
            null
        }
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

internal fun DeletedMessageView.render(): UiDMItem.Message = UiDMItem.Message.Deleted

internal fun MessageView.render(accountKey: MicroBlogKey) =
    UiDMItem.Message.Text(
        text = parseBluesky(text, facets.orEmpty(), accountKey),
    )
