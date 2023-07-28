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
    val nameElement: Element

    data class Mastodon(
        override val userKey: MicroBlogKey,
        override val name: String,
        override val handle: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        override val nameElement: Element,
        val description: String?,
        val descriptionElement: Element?,
        val matrices: Matrices,
    ) : UiUser {
        val displayHandle = "@$handle@${userKey.host}"
        val nameDirection = if (Bidi(name, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
            LayoutDirection.Ltr
        } else {
            LayoutDirection.Rtl
        }

        val descriptionDirection = description?.let {
            if (Bidi(it, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            }
        }

        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long,
        )
    }
}
