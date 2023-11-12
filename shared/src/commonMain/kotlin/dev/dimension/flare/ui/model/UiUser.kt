package dev.dimension.flare.ui.model

import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import moe.tlaster.ktml.Ktml
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Text

expect class UiUserExtra

internal expect fun createUiUserExtra(user: UiUser): UiUserExtra

sealed class UiUser {
    abstract val userKey: MicroBlogKey
    abstract val handle: String
    abstract val avatarUrl: String
    abstract val nameElement: Element
    val extra by lazy {
        createUiUserExtra(this)
    }

    data class Mastodon(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val locked: Boolean,
        internal val raw: dev.dimension.flare.data.network.mastodon.api.model.Account,
    ) : UiUser() {
        override val handle = "@$handleInternal@$remoteHost"
        override val nameElement by lazy {
            parseName(raw)
        }

        val descriptionElement by lazy {
            parseNote(raw)
        }

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

    data class Misskey(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val isCat: Boolean,
        val isBot: Boolean,
        val relation: UiRelation.Misskey,
        internal val accountHost: String,
    ) : UiUser() {
        override val nameElement by lazy {
            parseName(name, accountHost)
        }

        val descriptionElement by lazy {
            parseDescription(description, accountHost)
        }

        override val handle = "@$handleInternal@$remoteHost"

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

    data class Bluesky(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        val description: String?,
        val matrices: Matrices,
        val relation: UiRelation.Bluesky,
        internal val accountHost: String,
    ) : UiUser() {
        override val nameElement by lazy {
            Element("span").apply {
                children.add(Text(name))
            }
        }

        val descriptionElement by lazy {
            parseDescription(description, accountHost)
        }
        override val handle: String = "@$handleInternal"

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
}

private fun parseNote(account: Account): Element? {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content =
            content.replace(
                ":${it.shortcode}:",
                "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
            )
    }
    return Ktml.parse(content) as? Element
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
    accountHost: String,
): Element {
    if (name.isEmpty()) {
        return Element("body")
    }
    return misskeyParser.parse(name).toHtml(accountHost) as? Element ?: Element("body")
}

private fun parseDescription(
    description: String?,
    accountHost: String,
): Element? {
    if (description.isNullOrEmpty()) {
        return null
    }
    return misskeyParser.parse(description).toHtml(accountHost)
}
