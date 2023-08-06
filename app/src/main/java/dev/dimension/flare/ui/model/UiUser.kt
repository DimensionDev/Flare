package dev.dimension.flare.ui.model

import androidx.compose.ui.unit.LayoutDirection
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.humanize
import java.text.Bidi
import org.jsoup.nodes.Element

sealed interface UiUser {
    val userKey: MicroBlogKey
    val handle: String
    val avatarUrl: String
    val nameElement: Element

    data class Mastodon(
        override val userKey: MicroBlogKey,
        val name: String,
        val handleInternal: String,
        val remoteHost: String,
        override val avatarUrl: String,
        val bannerUrl: String?,
        override val nameElement: Element,
        val description: String?,
        val descriptionElement: Element?,
        val matrices: Matrices,
        val locked: Boolean
    ) : UiUser {
        override val handle = "@$handleInternal@$remoteHost"
        val nameDirection by lazy {
            if (Bidi(name, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            }
        }

        val descriptionDirection by lazy {
            description?.let {
                if (Bidi(it, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                    LayoutDirection.Ltr
                } else {
                    LayoutDirection.Rtl
                }
            }
        }

        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long
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
        override val nameElement: Element,
        val description: String?,
        val descriptionElement: Element?,
        val matrices: Matrices,
        val isCat: Boolean,
        val isBot: Boolean,
        val relation: UiRelation.Misskey,
    ) : UiUser {

        override val handle = "@$handleInternal@$remoteHost"
        val nameDirection by lazy {
            if (Bidi(name, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            }
        }

        val descriptionDirection by lazy {
            description?.let {
                if (Bidi(it, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
                    LayoutDirection.Ltr
                } else {
                    LayoutDirection.Rtl
                }
            }
        }

        data class Matrices(
            val fansCount: Long,
            val followsCount: Long,
            val statusesCount: Long
        ) {
            val fansCountHumanized = fansCount.humanize()
            val followsCountHumanized = followsCount.humanize()
            val statusesCountHumanized = statusesCount.humanize()
        }
    }
}
