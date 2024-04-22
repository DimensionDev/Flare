package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import app.bsky.notification.ListNotificationsReason
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.misskey.api.model.NotificationType
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.fullHumanize
import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text
import moe.tlaster.mfm.parser.MFMParser
import moe.tlaster.mfm.parser.tree.BoldNode
import moe.tlaster.mfm.parser.tree.CashNode
import moe.tlaster.mfm.parser.tree.CenterNode
import moe.tlaster.mfm.parser.tree.CodeBlockNode
import moe.tlaster.mfm.parser.tree.EmojiCodeNode
import moe.tlaster.mfm.parser.tree.FnNode
import moe.tlaster.mfm.parser.tree.HashtagNode
import moe.tlaster.mfm.parser.tree.InlineCodeNode
import moe.tlaster.mfm.parser.tree.ItalicNode
import moe.tlaster.mfm.parser.tree.LinkNode
import moe.tlaster.mfm.parser.tree.MathBlockNode
import moe.tlaster.mfm.parser.tree.MathInlineNode
import moe.tlaster.mfm.parser.tree.MentionNode
import moe.tlaster.mfm.parser.tree.QuoteNode
import moe.tlaster.mfm.parser.tree.RootNode
import moe.tlaster.mfm.parser.tree.SearchNode
import moe.tlaster.mfm.parser.tree.SmallNode
import moe.tlaster.mfm.parser.tree.StrikeNode
import moe.tlaster.mfm.parser.tree.UrlNode
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.Token
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken

internal val misskeyParser by lazy {
    MFMParser()
}

internal val blueskyParser by lazy {
    TwitterParser(enableDotInUserName = true)
}

internal val twitterParser by lazy {
    TwitterParser(enableNonAsciiInUrl = false)
}

@Immutable
expect class UiStatusExtra

internal expect fun createStatusExtra(status: UiStatus): UiStatusExtra

@Immutable
sealed class UiStatus {
    abstract val statusKey: MicroBlogKey
    abstract val accountKey: MicroBlogKey
    val extra by lazy {
        createStatusExtra(this)
    }

    open val itemKey: String by lazy {
        statusKey.toString()
    }

    val itemType: String by lazy {
        when (this) {
            is Mastodon ->
                buildString {
                    append("mastodon")
                    if (reblogStatus != null) append("_reblog")
                    with(reblogStatus ?: this@UiStatus) {
                        if (media.isNotEmpty()) append("_media")
                        if (poll != null) append("_poll")
                        if (card != null) append("_card")
                    }
                }

            is MastodonNotification ->
                buildString {
                    append("mastodon_notification")
                    append("_${type.name.lowercase()}")
                    if (status != null) {
                        append(status.itemType)
                    }
                }

            is Misskey ->
                buildString {
                    append("misskey")
                    if (renote != null) {
                        append("_reblog")
                        append("_${renote.itemType}")
                    }
                    if (quote != null) {
                        append("_quote")
                        append("_${quote.itemType}")
                    }
                    if (media.isNotEmpty()) append("_media")
                    if (poll != null) append("_poll")
                    if (card != null) append("_card")
                }

            is MisskeyNotification ->
                buildString {
                    append("misskey_notification")
                    append("_${type.name.lowercase()}")
                    if (note != null) {
                        append(note.itemType)
                    }
                }

            is Bluesky ->
                buildString {
                    append("bluesky")
                    if (repostBy != null) append("_reblog")
                    if (medias.isNotEmpty()) append("_media")
                    if (quote != null) {
                        append("_quote")
                        append("_${quote.itemType}")
                    }
                }

            is BlueskyNotification ->
                buildString {
                    append("bluesky_notification")
                    append("_${reason.name.lowercase()}")
                }

            is XQT ->
                buildString {
                    append("xqt")
                    if (retweet != null) {
                        append("_retweet_")
                        append(retweet.itemType)
                    }
                    if (quote != null) {
                        append("_quote_")
                        append(quote.itemType)
                    }
                    if (card != null) append("_card")
                }

            is XQTNotification ->
                buildString {
                    append("xqt_notification")
                }
        }
    }

    @Immutable
    data class MastodonNotification(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val createdAt: Instant,
        val status: Mastodon?,
        val type: NotificationTypes,
    ) : UiStatus() {
        val humanizedTime by lazy {
            createdAt.humanize()
        }
    }

    @Immutable
    data class Mastodon internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val content: String,
        val contentWarningText: String?,
        val matrices: Matrices,
        val media: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: UiPoll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val reblogStatus: Mastodon?,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Status,
    ) : UiStatus() {
        val humanizedTime by lazy {
            createdAt.humanize()
        }

        val expandedTime by lazy {
            createdAt.fullHumanize()
        }

        val contentToken by lazy {
            parseContent(raw, content, accountKey)
        }

        val isFromMe by lazy {
            user.userKey == accountKey
        }

        val canReblog by lazy {
            visibility == Visibility.Public || visibility == Visibility.Unlisted
        }

        data class Reaction(
            val liked: Boolean,
            val reblogged: Boolean,
            val bookmarked: Boolean,
        )

        enum class Visibility {
            Public,
            Unlisted,
            Private,
            Direct,
        }

        data class Matrices(
            val replyCount: Long,
            val reblogCount: Long,
            val favouriteCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.humanize() else null }
            val humanizedReblogCount by lazy { if (reblogCount > 0) reblogCount.humanize() else null }
            val humanizedFavouriteCount by lazy { if (favouriteCount > 0) favouriteCount.humanize() else null }
        }
    }

    @Immutable
    data class Misskey(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Misskey,
        val content: String,
        val contentWarningText: String?,
        val matrices: Matrices,
        val media: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: UiPoll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val quote: Misskey?,
        val renote: Misskey?,
    ) : UiStatus() {
        val humanizedTime: String by lazy {
            createdAt.humanize()
        }

        val expandedTime by lazy {
            createdAt.fullHumanize()
        }

        val contentToken by lazy {
            misskeyParser.parse(content).toHtml(accountKey)
        }

        val isFromMe by lazy {
            user.userKey == accountKey
        }

        val canRenote by lazy {
            visibility != Visibility.Specified
        }

        data class Reaction(
            val emojiReactions: ImmutableList<EmojiReaction>,
            val myReaction: String?,
        )

        data class EmojiReaction(
            val name: String,
            val url: String,
            val count: Long,
        ) {
            val humanizedCount by lazy {
                count.humanize()
            }
            val isImageReaction by lazy {
                name.startsWith(":") && name.endsWith(":")
            }
        }

        enum class Visibility {
            Public,
            Home,
            Followers,
            Specified,
        }

        data class Matrices(
            val replyCount: Long,
            val renoteCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.humanize() else null }
            val humanizedReNoteCount by lazy { if (renoteCount > 0) renoteCount.humanize() else null }
        }
    }

    @Immutable
    data class MisskeyNotification internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Misskey?,
        val createdAt: Instant,
        val note: Misskey?,
        val type: NotificationType,
        val achievement: String?,
    ) : UiStatus() {
        val humanizedTime by lazy {
            createdAt.humanize()
        }
    }

    @Immutable
    data class Bluesky(
        override val accountKey: MicroBlogKey,
        override val statusKey: MicroBlogKey,
        val user: UiUser.Bluesky,
        val indexedAt: Instant,
        val repostBy: UiUser.Bluesky?,
        val quote: Bluesky?,
        val content: String,
        val medias: ImmutableList<UiMedia>,
        val card: UiCard?,
        val matrices: Matrices,
        val reaction: Reaction,
        val cid: String,
        val uri: String,
    ) : UiStatus() {
        val humanizedTime by lazy {
            indexedAt.humanize()
        }

        val expandedTime by lazy {
            indexedAt.fullHumanize()
        }

        val contentToken by lazy {
            blueskyParser.parse(content).toHtml(accountKey)
        }

        val isFromMe by lazy {
            user.userKey == accountKey
        }

        data class Matrices(
            val replyCount: Long,
            val likeCount: Long,
            val repostCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.humanize() else null }
            val humanizedLikeCount by lazy { if (likeCount > 0) likeCount.humanize() else null }
            val humanizedRepostCount by lazy { if (repostCount > 0) repostCount.humanize() else null }
        }

        data class Reaction(
            val repostUri: String?,
            val likedUri: String?,
        ) {
            val liked by lazy {
                likedUri != null
            }

            val reposted by lazy {
                repostUri != null
            }
        }

        override val itemKey: String by lazy {
            statusKey.toString() + repostBy?.let { "_reblog_${it.userKey}" }.orEmpty()
        }
    }

    @Immutable
    data class BlueskyNotification(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Bluesky,
        val reason: ListNotificationsReason,
        val indexedAt: Instant,
    ) : UiStatus() {
        override val itemKey: String by lazy {
            statusKey.toString() + "_${user.userKey}"
        }
        val humanizedTime by lazy {
            indexedAt.humanize()
        }
    }

    @Immutable
    data class XQT internal constructor(
        override val accountKey: MicroBlogKey,
        override val statusKey: MicroBlogKey,
        val user: UiUser.XQT,
        val createdAt: Instant,
        val content: String,
        val medias: ImmutableList<UiMedia>,
        val sensitive: Boolean,
        val card: UiCard?,
        val matrices: Matrices,
        val reaction: Reaction,
        val poll: UiPoll?,
        val retweet: XQT?,
        val quote: XQT?,
        val inReplyToScreenName: String?,
        val inReplyToStatusId: String?,
        val inReplyToUserId: String?,
        internal val raw: Tweet,
    ) : UiStatus() {
        val isFromMe by lazy {
            user.userKey == accountKey
        }

        val contentToken by lazy {
            twitterParser
                .parse(content)
                .map { token ->
                    if (token is UrlToken) {
                        val actual =
                            raw.legacy?.entities?.urls
                                ?.firstOrNull { it.url == token.value.trim() }?.expandedUrl
                                ?: raw.noteTweet?.noteTweetResults?.result?.entitySet?.urls
                                    ?.firstOrNull { it.url == token.value.trim() }?.expandedUrl
                        if (actual != null) {
                            UrlToken(actual)
                        } else {
                            token
                        }
                    } else {
                        token
                    }
                }
                .toHtml(accountKey)
        }

        val humanizedTime by lazy {
            createdAt.humanize()
        }

        val expandedTime by lazy {
            createdAt.fullHumanize()
        }

        val canRetweet by lazy {
            !(retweet?.user ?: user).protected
        }

        val replyHandle by lazy {
            inReplyToScreenName?.let {
                "@$it"
            }
        }

        data class Matrices(
            val replyCount: Long,
            val likeCount: Long,
            val retweetCount: Long,
        ) {
            val humanizedReplyCount by lazy { if (replyCount > 0) replyCount.humanize() else null }
            val humanizedLikeCount by lazy { if (likeCount > 0) likeCount.humanize() else null }
            val humanizedRetweetCount by lazy { if (retweetCount > 0) retweetCount.humanize() else null }
        }

        data class Reaction(
            val liked: Boolean,
            val retweeted: Boolean,
            val bookmarked: Boolean,
        )
    }

    @Immutable
    data class XQTNotification internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val url: String,
        val text: String,
        val type: Type,
        val users: ImmutableList<UiUser.XQT>,
        val data: XQT?,
        val createdAt: Instant,
    ) : UiStatus() {
        enum class Type {
            Follow,
            Like,
            Recommendation,
            Logo,
        }
    }
}

internal fun List<Token>.toHtml(accountKey: MicroBlogKey): Element {
    val body = Element("body")
    forEach {
        body.children.add(it.toHtml(accountKey))
    }
    return body
}

private fun Token.toHtml(accountKey: MicroBlogKey): Node {
    return when (this) {
        is CashTagToken ->
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, value)
                children.add(Text(value))
            }
        // not supported
        is EmojiToken -> Text(value)
        is HashTagToken ->
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, value)
                children.add(Text(value))
            }

        is StringToken -> Text(value)
        is UrlToken ->
            Element("a").apply {
                attributes["href"] = value
                children.add(Text(value))
            }

        is UserNameToken ->
            Element("a").apply {
                attributes["href"] = AppDeepLink.ProfileWithNameAndHost(accountKey, value, accountKey.host)
                children.add(Text(value))
            }
    }
}

private fun parseContent(
    status: Status,
    text: String,
    accountKey: MicroBlogKey,
): Element {
    val emoji = status.emojis.orEmpty()
    val mentions = status.mentions.orEmpty()
    var content = text
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    val body = Ktml.parse(content)
    body.children.forEach {
        replaceMentionAndHashtag(mentions, it, accountKey)
    }
    return body
}

private fun replaceMentionAndHashtag(
    mentions: List<Mention>,
    node: Node,
    accountKey: MicroBlogKey,
) {
    if (node is Element) {
        val href = node.attributes["href"]
        val mention = mentions.firstOrNull { it.url == href }
        if (mention != null) {
            val id = mention.id
            if (id != null) {
                node.attributes["href"] = AppDeepLink.Profile(accountKey, userKey = MicroBlogKey(id, accountKey.host))
            }
        } else if (node.innerText.startsWith("#")) {
            node.attributes["href"] = AppDeepLink.Search(accountKey, node.innerText)
        }
        node.children.forEach { replaceMentionAndHashtag(mentions, it, accountKey) }
    }
}

internal fun moe.tlaster.mfm.parser.tree.Node.toHtml(accountKey: MicroBlogKey): Element {
    return when (this) {
        is CenterNode -> {
            Element("center").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is CodeBlockNode -> {
            Element("pre").apply {
                children.add(
                    Element("code").apply {
                        language?.let { attributes["lang"] = it }
                        children.add(Text(code))
                    },
                )
            }
        }

        is MathBlockNode -> {
            Element("pre").apply {
                children.add(
                    Element("code").apply {
                        attributes["lang"] = "math"
                        children.add(Text(formula))
                    },
                )
            }
        }

        is QuoteNode -> {
            Element("blockquote").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is SearchNode -> {
            Element("search").apply {
                children.add(Text(query))
            }
        }

        is BoldNode -> {
            Element("strong").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is FnNode -> {
            Element("fn").apply {
                attributes["name"] = name
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is ItalicNode -> {
            Element("em").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is RootNode -> {
            Element("body").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is SmallNode -> {
            Element("small").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is StrikeNode -> {
            Element("s").apply {
                content.forEach {
                    children.add(it.toHtml(accountKey))
                }
            }
        }

        is CashNode -> {
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, "$$content")
                children.add(Text("$$content"))
            }
        }

        is EmojiCodeNode -> {
            Element("img").apply {
                attributes["src"] = resolveMisskeyEmoji(emoji, accountKey.host)
                attributes["alt"] = emoji
            }
        }

        is HashtagNode -> {
            Element("a").apply {
                attributes["href"] = AppDeepLink.Search(accountKey, "#$tag")
                children.add(Text("#$tag"))
            }
        }

        is InlineCodeNode -> {
            Element("code").apply {
                children.add(Text(code))
            }
        }

        is LinkNode -> {
            Element("a").apply {
                attributes["href"] = url
                children.add(Text(content))
            }
        }

        is MathInlineNode -> {
            Element("code").apply {
                attributes["lang"] = "math"
                children.add(Text(formula))
            }
        }

        is MentionNode -> {
            Element("a").apply {
                val deeplink =
                    host?.let {
                        AppDeepLink.ProfileWithNameAndHost(accountKey, userName, it)
                    } ?: AppDeepLink.ProfileWithNameAndHost(accountKey, userName, accountKey.host)
                attributes["href"] = deeplink
                children.add(
                    Text(
                        buildString {
                            append("@")
                            append(userName)
                            if (host != null) {
                                append("@")
                                append(host)
                            }
                        },
                    ),
                )
            }
        }

        is moe.tlaster.mfm.parser.tree.TextNode -> {
            Element("span").apply {
                children.add(Text(content))
            }
        }

        is UrlNode -> {
            Element("a").apply {
                attributes["href"] = url
                children.add(Text(url))
            }
        }
    }
}

private fun resolveMisskeyEmoji(
    name: String,
    accountHost: String,
): String {
    return name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }
}

fun createSampleStatus(user: UiUser) =
    when (user) {
        is UiUser.Bluesky -> createBlueskyStatus(user)
        is UiUser.Mastodon -> createMastodonStatus(user)
        is UiUser.Misskey -> createMisskeyStatus(user)
        is UiUser.XQT -> createXQTStatus(user)
    }

private fun createMastodonStatus(user: UiUser.Mastodon): UiStatus.Mastodon {
    return UiStatus.Mastodon(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Sample content for Mastodon status",
        contentWarningText = null,
        matrices =
            UiStatus.Mastodon.Matrices(
                replyCount = 10,
                reblogCount = 5,
                favouriteCount = 15,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Mastodon.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Mastodon.Reaction(
                liked = false,
                reblogged = false,
                bookmarked = false,
            ),
        sensitive = false,
        reblogStatus = null,
        raw = Status(),
    )
}

private fun createBlueskyStatus(user: UiUser.Bluesky): UiStatus.Bluesky {
    return UiStatus.Bluesky(
        accountKey = MicroBlogKey(id = "123", host = user.userKey.host),
        statusKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        indexedAt = Clock.System.now(),
        repostBy = null,
        quote = null,
        content = "Bluesky post content",
        medias = persistentListOf(),
        card = null,
        matrices =
            UiStatus.Bluesky.Matrices(
                replyCount = 20,
                likeCount = 30,
                repostCount = 40,
            ),
        reaction =
            UiStatus.Bluesky.Reaction(
                repostUri = null,
                likedUri = null,
            ),
        cid = "cid_sample",
        uri = "https://bluesky.post/uri",
    )
}

private fun createMisskeyStatus(user: UiUser.Misskey): UiStatus.Misskey {
    return UiStatus.Misskey(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Misskey post content",
        contentWarningText = null,
        matrices =
            UiStatus.Misskey.Matrices(
                replyCount = 15,
                renoteCount = 25,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Misskey.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Misskey.Reaction(
                emojiReactions = persistentListOf(),
                myReaction = null,
            ),
        sensitive = false,
        quote = null,
        renote = null,
    )
}

fun createXQTStatus(user: UiUser.XQT): UiStatus.XQT {
    return UiStatus.XQT(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Misskey post content",
        matrices =
            UiStatus.XQT.Matrices(
                likeCount = 15,
                replyCount = 25,
                retweetCount = 35,
            ),
        medias = persistentListOf(),
        createdAt = Clock.System.now(),
        poll = null,
        card = null,
        reaction =
            UiStatus.XQT.Reaction(
                liked = false,
                retweeted = false,
                bookmarked = false,
            ),
        quote = null,
        retweet = null,
        inReplyToScreenName = null,
        inReplyToStatusId = null,
        inReplyToUserId = null,
        sensitive = false,
        raw = Tweet(restId = ""),
    )
}

val UiStatus.medias: ImmutableList<UiMedia>
    get() {
        return when (this) {
            is UiStatus.Mastodon -> media
            is UiStatus.Misskey -> media
            is UiStatus.Bluesky -> medias
            is UiStatus.XQT -> medias
            else -> persistentListOf()
        }
    }
