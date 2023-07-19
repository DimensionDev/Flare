package dev.dimension.flare.ui.model

import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.model.MicroBlogKey
import org.jsoup.nodes.Element
import java.text.Bidi

sealed interface UiUser {
    val userKey: MicroBlogKey
    val name: String
    val handle: String
    val avatarUrl: String

    data class Mastodon(
        override val userKey: MicroBlogKey,
        override val name: String,
        override val handle: String,
        override val avatarUrl: String,
        val nameElement: Element,
    ) : UiUser {
        val displayHandle = "@$handle@${userKey.host}"
        val contentDirection = if (Bidi(name, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
            LayoutDirection.Ltr
        } else {
            LayoutDirection.Rtl
        }
    }
}
