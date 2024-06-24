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
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.humanizer.humanize
import io.ktor.http.decodeURLPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    abstract val itemType: String
    abstract val platformType: PlatformType
    abstract val textToFilter: ImmutableList<String>
    abstract val medias: ImmutableList<UiMedia>

    @Immutable
    data class MastodonNotification(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Mastodon,
        val createdAt: Instant,
        val status: Mastodon?,
        val type: NotificationTypes,
    ) : UiStatus() {
        override val itemType: String =
            buildString {
                append("mastodon_notification")
                append("_${type.name.lowercase()}")
                if (status != null) {
                    append(status.itemType)
                }
            }

        override val platformType: PlatformType = PlatformType.Mastodon

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(status?.content, status?.contentWarningText).toImmutableList()
        }

        override val medias: ImmutableList<UiMedia> by lazy {
            persistentListOf()
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
        override val medias: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: UiPoll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val reblogStatus: Mastodon?,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Status,
    ) : UiStatus() {
        companion object {
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
                            node.attributes["href"] =
                                AppDeepLink.Profile(
                                    accountKey,
                                    userKey = MicroBlogKey(id, accountKey.host),
                                )
                        }
                    } else if (node.innerText.startsWith("#")) {
                        node.attributes["href"] = AppDeepLink.Search(accountKey, node.innerText)
                    }
                    node.children.forEach { replaceMentionAndHashtag(mentions, it, accountKey) }
                }
            }
        }

        override val itemType: String =
            buildString {
                append("mastodon")
                if (reblogStatus != null) append("_reblog")
                with(reblogStatus ?: this@Mastodon) {
                    if (medias.isNotEmpty()) append("_media")
                    if (poll != null) append("_poll")
                    if (card != null) append("_card")
                }
            }

        override val platformType: PlatformType = PlatformType.Mastodon

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(
                content,
                contentWarningText,
                reblogStatus?.content,
                reblogStatus?.contentWarningText,
            ).toImmutableList()
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
    data class Misskey internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Misskey,
        val content: String,
        val contentWarningText: String?,
        val matrices: Matrices,
        override val medias: ImmutableList<UiMedia>,
        val createdAt: Instant,
        val visibility: Visibility,
        val poll: UiPoll?,
        val card: UiCard?,
        val reaction: Reaction,
        val sensitive: Boolean,
        val quote: Misskey?,
        val renote: Misskey?,
    ) : UiStatus() {
        override val itemType: String =
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
                if (medias.isNotEmpty()) append("_media")
                if (poll != null) append("_poll")
                if (card != null) append("_card")
            }

        override val platformType: PlatformType = PlatformType.Misskey

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(
                content,
                contentWarningText,
                quote?.content,
                quote?.contentWarningText,
                renote?.content,
                renote?.contentWarningText,
            ).toImmutableList()
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
        override val itemType: String =
            buildString {
                append("misskey_notification")
                append("_${type.name.lowercase()}")
                if (note != null) {
                    append(note.itemType)
                }
            }

        override val platformType: PlatformType = PlatformType.Misskey

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(
                note?.content,
                note?.contentWarningText,
                note?.quote?.content,
                note?.quote?.contentWarningText,
                note?.renote?.content,
                note?.renote?.contentWarningText,
            ).toImmutableList()
        }

        override val medias: ImmutableList<UiMedia> by lazy {
            persistentListOf()
        }
    }

    @Immutable
    data class Bluesky internal constructor(
        override val accountKey: MicroBlogKey,
        override val statusKey: MicroBlogKey,
        val user: UiUser.Bluesky,
        val indexedAt: Instant,
        val repostBy: UiUser.Bluesky?,
        val quote: Bluesky?,
        val content: String,
        override val medias: ImmutableList<UiMedia>,
        val card: UiCard?,
        val matrices: Matrices,
        val reaction: Reaction,
        val cid: String,
        val uri: String,
    ) : UiStatus() {
        override val itemType: String =
            buildString {
                append("bluesky")
                if (repostBy != null) append("_reblog")
                if (medias.isNotEmpty()) append("_media")
                if (quote != null) {
                    append("_quote")
                    append("_${quote.itemType}")
                }
            }

        override val platformType: PlatformType = PlatformType.Bluesky

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(content, quote?.content).toImmutableList()
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
    data class BlueskyNotification internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val user: UiUser.Bluesky,
        val reason: ListNotificationsReason,
        val indexedAt: Instant,
    ) : UiStatus() {
        override val itemType: String =
            buildString {
                append("bluesky_notification")
                append("_${reason.name.lowercase()}")
            }

        override val platformType: PlatformType = PlatformType.Bluesky

        override val textToFilter: ImmutableList<String> by lazy {
            persistentListOf()
        }

        override val medias: ImmutableList<UiMedia> by lazy {
            persistentListOf()
        }

        override val itemKey: String by lazy {
            statusKey.toString() + "_${user.userKey}"
        }
    }

    @Immutable
    data class XQT internal constructor(
        override val accountKey: MicroBlogKey,
        override val statusKey: MicroBlogKey,
        val user: UiUser.XQT,
        val createdAt: Instant,
        val content: String,
        override val medias: ImmutableList<UiMedia>,
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
        override val itemType: String =
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

        override val platformType: PlatformType = PlatformType.xQt

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(content, retweet?.content, quote?.content).toImmutableList()
        }

        val isFromMe by lazy {
            user.userKey == accountKey
        }

        val contentToken by lazy {
            twitterParser
                .parse(content)
                .map { token ->
                    if (token is UrlToken) {
                        val actual =
                            raw.legacy
                                ?.entities
                                ?.urls
                                ?.firstOrNull { it.url == token.value.trim() }
                                ?.expandedUrl
                                ?: raw.noteTweet
                                    ?.noteTweetResults
                                    ?.result
                                    ?.entitySet
                                    ?.urls
                                    ?.firstOrNull { it.url == token.value.trim() }
                                    ?.expandedUrl
                        if (actual != null) {
                            UrlToken(actual)
                        } else {
                            token
                        }
                    } else {
                        token
                    }
                }.toHtml(accountKey)
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
        override val itemType: String = "xqt_notification"

        override val platformType: PlatformType = PlatformType.xQt

        override val textToFilter: ImmutableList<String> by lazy {
            persistentListOf()
        }

        override val medias: ImmutableList<UiMedia> by lazy {
            persistentListOf()
        }

        enum class Type {
            Follow,
            Like,
            Recommendation,
            Logo,
            Mention,
        }
    }

    @Immutable
    data class VVO internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val rawUser: UiUser.VVO?,
        val content: String,
        val rawContent: String,
        val createdAt: Instant,
        override val medias: ImmutableList<UiMedia>,
        val liked: Boolean,
        val matrices: Matrices,
        val regionName: String?,
        val source: String?,
        val quote: VVO?,
        val canReblog: Boolean,
    ) : UiStatus() {
        companion object {
            fun replaceMentionAndHashtag(
                element: Element,
                node: Node,
                accountKey: MicroBlogKey,
            ) {
                if (node is Element) {
                    val href = node.attributes["href"]
                    if (href != null) {
                        if (href.startsWith("/n/")) {
                            val id = href.removePrefix("/n/")
                            if (id.isNotEmpty()) {
                                node.attributes["href"] =
                                    AppDeepLink.ProfileWithNameAndHost(
                                        accountKey = accountKey,
                                        userName = id,
                                        host = accountKey.host,
                                    )
                            }
                        } else if (href.startsWith("https://$vvoHost/search")) {
                            node.attributes["href"] = AppDeepLink.Search(accountKey, node.innerText)
                        } else if (href.startsWith("https://weibo.cn/sinaurl?u=")) {
                            val url =
                                href.removePrefix("https://weibo.cn/sinaurl?u=").decodeURLPart()
                            if (url.contains("sinaimg.cn/")) {
                                node.attributes["href"] = AppDeepLink.RawImage(url)
                            }
                        }
                    }
                    node.children.forEach { replaceMentionAndHashtag(element, it, accountKey) }
                }
            }
        }

        override val itemType: String =
            buildString {
                append("vvo")
                if (medias.isNotEmpty()) append("_media")
            }

        override val platformType: PlatformType = PlatformType.VVo

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(
                content,
                quote?.content,
            ).toImmutableList()
        }

        val contentToken by lazy {
            val element = Ktml.parse(content)
            element.children.forEach {
                replaceMentionAndHashtag(element, it, accountKey)
            }
            element
        }

        val displayUser by lazy {
            rawUser?.copy(
                handle = regionName ?: source ?: rawUser.handle,
            )
        }

        val isFromMe by lazy {
            rawUser?.userKey == accountKey
        }

        @Immutable
        data class Matrices(
            val commentCount: Long,
            val likeCount: Long,
            val repostCount: String,
        ) {
            val humanizedLikeCount by lazy { if (likeCount > 0) likeCount.humanize() else null }
            val humanizedRepostCount by lazy {
                val value = repostCount.toLongOrNull()
                if (value == null) {
                    repostCount
                } else if (value > 0) {
                    value.humanize()
                } else {
                    null
                }
            }
            val humanizedCommentCount by lazy { if (commentCount > 0) commentCount.humanize() else null }
        }
    }

    @Immutable
    data class VVONotification internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val rawUser: UiUser.VVO?,
        val createdAt: Instant,
        val source: String?,
        val status: VVO?,
    ) : UiStatus() {
        override val itemType: String = "vvo_notification"

        override val platformType: PlatformType = PlatformType.VVo

        override val textToFilter: ImmutableList<String> by lazy {
            persistentListOf()
        }

        override val medias: ImmutableList<UiMedia> by lazy {
            persistentListOf()
        }

        val displayUser by lazy {
            rawUser?.copy(
                handle = source ?: rawUser.handle,
            )
        }
    }

    @Immutable
    data class VVOComment internal constructor(
        override val statusKey: MicroBlogKey,
        override val accountKey: MicroBlogKey,
        val rawUser: UiUser.VVO?,
        val text: String,
        override val medias: ImmutableList<UiMedia>,
        val likeCount: Long,
        val liked: Boolean,
        val comments: ImmutableList<VVOComment>,
        val createdAt: Instant,
        val source: String?,
        val status: VVO?,
    ) : UiStatus() {
        override val itemType: String = "vvo_comment"

        override val platformType: PlatformType = PlatformType.VVo

        override val textToFilter: ImmutableList<String> by lazy {
            listOfNotNull(text, status?.content).toImmutableList()
        }

        val contentToken by lazy {
            Ktml.parse(text)
        }
        val humanizedLikeCount by lazy { if (likeCount > 0) likeCount.humanize() else null }
        val isFromMe by lazy {
            rawUser?.userKey == accountKey
        }

        val displayUser by lazy {
            rawUser?.copy(
                handle = source ?: rawUser.handle,
            )
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

private fun Token.toHtml(accountKey: MicroBlogKey): Node =
    when (this) {
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
                children.add(Text(value.trimUrl()))
            }

        is UserNameToken ->
            Element("a").apply {
                attributes["href"] =
                    AppDeepLink.ProfileWithNameAndHost(accountKey, value, accountKey.host)
                children.add(Text(value))
            }
    }

internal fun moe.tlaster.mfm.parser.tree.Node.toHtml(accountKey: MicroBlogKey): Element =
    when (this) {
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
                children.add(Text(url.trimUrl()))
            }
        }
    }

private fun String.trimUrl(): String =
    this
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .removeSuffix("/")
        .let {
            if (it.length > 30) {
                it.substring(0, 30) + "..."
            } else {
                it
            }
        }

private fun resolveMisskeyEmoji(
    name: String,
    accountHost: String,
): String =
    name.trim(':').let {
        if (it.endsWith("@.")) {
            "https://$accountHost/emoji/${it.dropLast(2)}.webp"
        } else {
            "https://$accountHost/emoji/$it.webp"
        }
    }

fun createSampleStatus(user: UiUser) =
    when (user) {
        is UiUser.Bluesky -> createBlueskyStatus(user)
        is UiUser.Mastodon -> createMastodonStatus(user)
        is UiUser.Misskey -> createMisskeyStatus(user)
        is UiUser.XQT -> createXQTStatus(user)
        is UiUser.VVO -> createVVOStatus(user)
    }

private fun createMastodonStatus(user: UiUser.Mastodon): UiStatus.Mastodon =
    UiStatus.Mastodon(
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
        medias = persistentListOf(),
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

private fun createBlueskyStatus(user: UiUser.Bluesky): UiStatus.Bluesky =
    UiStatus.Bluesky(
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

private fun createMisskeyStatus(user: UiUser.Misskey): UiStatus.Misskey =
    UiStatus.Misskey(
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
        medias = persistentListOf(),
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

fun createXQTStatus(user: UiUser.XQT): UiStatus.XQT =
    UiStatus.XQT(
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

fun createVVOStatus(user: UiUser.VVO): UiStatus.VVO =
    UiStatus.VVO(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        rawUser = user,
        content = "VVO post content",
        rawContent = "VVO post content",
        createdAt = Clock.System.now(),
        medias = persistentListOf(),
        liked = false,
        matrices =
            UiStatus.VVO.Matrices(
                commentCount = 15,
                likeCount = 25,
                repostCount = "35",
            ),
        regionName = null,
        source = "From Flare",
        quote = null,
        canReblog = true,
    )
