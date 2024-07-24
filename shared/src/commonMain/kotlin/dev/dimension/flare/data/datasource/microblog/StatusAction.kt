package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.ClickContext
import kotlinx.collections.immutable.ImmutableList

sealed interface StatusAction {
    data class Group(
        val displayItem: Item,
        val actions: ImmutableList<StatusAction>,
    ) : StatusAction

    sealed interface Item : StatusAction {
        sealed interface Clickable : Item {
            val onClicked: ClickContext.() -> Unit
        }

        sealed interface Colorized : Item {
            val color: Color

            enum class Color {
                Red,
                Error,
                ContentColor,
                PrimaryColor,
            }
        }

        data object More : Item

        data class Like(
            val count: Long,
            val liked: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Colorized {
            val humanizedCount by lazy {
                count.humanize()
            }

            override val color: Colorized.Color
                get() = if (liked) Colorized.Color.Red else Colorized.Color.ContentColor
        }

        data class Retweet(
            val count: Long,
            val retweeted: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Colorized {
            val humanizedCount by lazy {
                count.humanize()
            }

            override val color: Colorized.Color
                get() = if (retweeted) Colorized.Color.PrimaryColor else Colorized.Color.ContentColor
        }

        data class Reply(
            val count: Long,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Quote(
            val count: Long,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Bookmark(
            val count: Long,
            val bookmarked: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable {
            val humanizedCount by lazy {
                count.humanize()
            }
        }

        data class Delete(
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Colorized,
            Clickable {
            override val color: Colorized.Color
                get() = Colorized.Color.Error
        }

        data class Report(
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Colorized,
            Clickable {
            override val color: Colorized.Color
                get() = Colorized.Color.Error
        }

        data class Reaction(
            val reacted: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable
    }
}
