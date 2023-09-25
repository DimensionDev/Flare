package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.data.network.mastodon.api.model.Account
import java.text.Bidi
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Immutable
actual data class UiUserExtra(
    val nameElement: Element,
    val nameDirection: LayoutDirection,
    val descriptionElement: Element?,
    val descriptionDirection: LayoutDirection,
)

val UiUser.nameElement get() = extra.nameElement
val UiUser.nameDirection get() = extra.nameDirection
val UiUser.descriptionElement get() = extra.descriptionElement
val UiUser.descriptionDirection get() = extra.descriptionDirection

internal actual fun createUiUserExtra(user: UiUser): UiUserExtra {
    return when (user) {
        is UiUser.Mastodon -> UiUserExtra(
            nameElement = parseName(user.raw),
            nameDirection = if (Bidi(
                    user.name,
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
            descriptionElement = parseNote(user.raw),
            descriptionDirection = if (Bidi(
                    user.raw.note ?: "",
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
        )
        is UiUser.Misskey -> UiUserExtra(
            nameElement = parseName(user.name, user.accountHost),
            nameDirection = if (Bidi(
                    user.name,
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
            descriptionElement = parseDescription(user.description, user.accountHost),
            descriptionDirection = if (Bidi(
                    user.description ?: "",
                    Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT
                ).baseIsLeftToRight()
            ) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            },
        )
    }
}


private fun parseNote(account: Account): Element? {
    val emoji = account.emojis.orEmpty()
    var content = account.note.orEmpty()
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
        )
    }
    return Jsoup.parse(content).body()
}

private fun parseName(status: Account): Element {
    val emoji = status.emojis.orEmpty()
    var content = status.displayName.orEmpty().ifEmpty { status.username.orEmpty() }
    emoji.forEach {
        content = content.replace(
            ":${it.shortcode}:",
            "<img src=\"${it.url}\" alt=\"${it.shortcode}\" />",
        )
    }
    return Jsoup.parse(content).body()
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
