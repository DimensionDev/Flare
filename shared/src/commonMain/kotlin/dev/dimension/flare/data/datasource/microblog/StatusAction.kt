package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.Digit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

public sealed interface StatusAction {
    public data class Group internal constructor(
        val displayItem: Item,
        val actions: ImmutableList<StatusAction>,
    ) : StatusAction

    public data class AsyncActionItem internal constructor(
        val flow: Flow<Item>,
    ) : StatusAction

    public sealed interface Item : StatusAction {
        public sealed interface Clickable {
            public val onClicked: ClickContext.() -> Unit
        }

        public sealed interface Shareable {
            public val content: String
        }

        public sealed interface Numbered {
            public val count: Long
            public val humanizedCount: String
            public val digits: ImmutableList<Digit>
        }

        public sealed interface Colorized {
            public val color: Color

            public enum class Color {
                Red,
                Error,
                ContentColor,
                PrimaryColor,
            }
        }

        public data object More : Item

        public data class Like internal constructor(
            override val count: Long,
            val liked: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Colorized,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()

            override val color: Colorized.Color
                get() = if (liked) Colorized.Color.Red else Colorized.Color.ContentColor
        }

        public data class Retweet internal constructor(
            override val count: Long,
            val retweeted: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Colorized,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()

            override val color: Colorized.Color
                get() = if (retweeted) Colorized.Color.PrimaryColor else Colorized.Color.ContentColor
        }

        public data class Reply internal constructor(
            override val count: Long,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()
        }

        public data class Comment internal constructor(
            override val count: Long,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()
        }

        public data class Quote internal constructor(
            override val count: Long,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()
        }

        public data class Bookmark internal constructor(
            override val count: Long,
            val bookmarked: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable,
            Numbered {
            public override val humanizedCount: String by lazy {
                count
                    .takeIf {
                        it > 0
                    }?.humanize()
                    .orEmpty()
            }

            override val digits: ImmutableList<Digit>
                get() =
                    humanizedCount
                        .mapIndexed { index, char ->
                            Digit(char, index, count)
                        }.toImmutableList()
        }

        public data class Delete internal constructor(
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Colorized,
            Clickable {
            override val color: Colorized.Color
                get() = Colorized.Color.Error
        }

        public data class Report internal constructor(
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Colorized,
            Clickable {
            override val color: Colorized.Color
                get() = Colorized.Color.Error
        }

        public data class Reaction internal constructor(
            val reacted: Boolean,
            override val onClicked: ClickContext.() -> Unit,
        ) : Item,
            Clickable

        public data class Share internal constructor(
            override val content: String,
        ) : Item,
            Shareable

        public data class FxShare internal constructor(
            override val content: String,
        ) : Item,
            Shareable
    }
}
