package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Text
import moe.tlaster.twitter.parser.UrlToken

@Immutable
expect class UiUserExtra

internal expect fun createUiUserExtra(user: UiUser): UiUserExtra

@Immutable
sealed class UiUser {
    abstract val userKey: MicroBlogKey
    abstract val handle: String
    abstract val avatarUrl: String
    abstract val bannerUrl: String?
    abstract val nameElement: Element
    abstract val descriptionElement: Element?
    val extra by lazy {
        createUiUserExtra(this)
    }

    val itemKey by lazy {
        userKey.toString()
    }

    @Immutable
    data class Mastodon internal constructor(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        override val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val locked: Boolean,
        val fields: ImmutableMap<String, String>,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Account,
    ) : UiUser() {
        override val handle = "@$handleInternal@$remoteHost"
        override val nameElement by lazy {
            parseName(raw)
        }

        val fieldsParsed by lazy {
            fields.map { (key, value) ->
                key to Ktml.parse(value)
            }.toMap().toPersistentMap()
        }

        override val descriptionElement by lazy {
            parseNote(raw)
        }

        @Immutable
        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }

    @Immutable
    data class Misskey(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        override val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val isCat: Boolean,
        val isBot: Boolean,
        val relation: UiRelation.Misskey,
        val fields: ImmutableMap<String, String>,
        private val accountKey: MicroBlogKey,
    ) : UiUser() {
        override val nameElement by lazy {
            parseName(name, accountKey)
        }

        val fieldsParsed by lazy {
            fields.map { (key, value) ->
                key to misskeyParser.parse(value).toHtml(accountKey)
            }.toMap().toPersistentMap()
        }

        override val descriptionElement by lazy {
            parseDescription(description, accountKey)
        }

        override val handle = "@$handleInternal@$remoteHost"

        @Immutable
        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }

    @Immutable
    data class Bluesky(
        override val userKey: MicroBlogKey,
        val displayName: String,
        val handleInternal: String,
        override val avatarUrl: String,
        override val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val relation: UiRelation.Bluesky,
        private val accountKey: MicroBlogKey,
    ) : UiUser() {
        override val nameElement by lazy {
            Element("span").apply {
                children.add(Text(displayName))
            }
        }

        override val descriptionElement by lazy {
            parseDescription(description, accountKey)
        }
        override val handle: String = "@$handleInternal"

        @Immutable
        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }

    @Immutable
    data class XQT internal constructor(
        override val userKey: MicroBlogKey,
        val displayName: String,
        val rawHandle: String,
        override val avatarUrl: String,
        override val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val verifyType: VerifyType?,
        val location: String?,
        val url: String?,
        val protected: Boolean,
        internal val raw: User,
        private val accountKey: MicroBlogKey,
    ) : UiUser() {
        override val handle: String = "@$rawHandle@$xqtHost"

        val fieldsParsed by lazy {
            hashMapOf<String, Element>().apply {
                location?.let {
                    put("location", Element("span").apply { children.add(Text(it)) })
                }
                url?.let { url ->
                    put(
                        "url",
                        Element("a").apply {
                            children.add(Text(url))
                            attributes["href"] = url
                        },
                    )
                }
            }.toImmutableMap()
        }

        @Immutable
        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }

        override val nameElement: Element by lazy {
            Element("span").apply {
                children.add(Text(displayName))
            }
        }
        override val descriptionElement: Element? by lazy {
            description?.let {
                twitterParser.parse(it)
                    .map { token ->
                        if (token is UrlToken) {
                            val actual =
                                raw.legacy.entities.description?.urls
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
        }

        enum class VerifyType {
            Money,
            Company,
        }
    }

    @Immutable
    data class VVO(
        override val userKey: MicroBlogKey,
        override val avatarUrl: String,
        override val bannerUrl: String?,
        val rawHandle: String,
        val rawDescription: String?,
        val matrices: Matrices,
        val verified: Boolean,
        val verifiedReason: String?,
        val relation: UiRelation.VVO,
        val accountKey: MicroBlogKey,
        override val handle: String = "@$rawHandle",
    ) : UiUser() {
        override val nameElement: Element by lazy {
            Element("span").apply {
                children.add(Text(rawHandle))
            }
        }
        override val descriptionElement: Element? by lazy {
            rawDescription?.let {
                Element("span").apply {
                    children.add(Text(it))
                }
            }
        }

        @Immutable
        data class Matrices(
            val fansCount: String,
            val followsCount: Long,
            val statusesCount: Long,
        ) {
            val fansCountHumanized = fansCount
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }
}

private fun parseNote(account: Account): Element {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return Ktml.parse(content)
}

private fun parseName(status: Account): Element {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return Ktml.parse(content) as? Element ?: Element("body")
}

private fun parseName(
    name: String,
    accountKey: MicroBlogKey,
): Element {
    if (name.isEmpty()) {
        return Element("body")
    }
    return misskeyParser.parse(name).toHtml(accountKey) as? Element ?: Element("body")
}

private fun parseDescription(
    description: String?,
    accountKey: MicroBlogKey,
): Element? {
    if (description.isNullOrEmpty()) {
        return null
    }
    return misskeyParser.parse(description).toHtml(accountKey)
}
