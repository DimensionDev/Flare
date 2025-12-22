package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiNumber
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

public sealed interface StatusAction {
    public data class Group internal constructor(
        val displayItem: Item,
        val actions: ImmutableList<StatusAction>,
    ) : StatusAction

    public data class AsyncActionItem internal constructor(
        val flow: Flow<Item>,
    ) : StatusAction

    public data class Item internal constructor(
        val icon: Icon? = null,
        val text: Text? = null,
        val count: UiNumber? = null,
        val onClicked: (ClickContext.() -> Unit)? = null,
        val shareContent: String? = null,
    ) : StatusAction {
        init {
            require(icon != null || text != null) {
                "icon and text cannot be both null"
            }
        }

        public enum class Color {
            Red,
            Error,
            ContentColor,
            PrimaryColor,
        }

        public enum class Icon {
            Like,
            Unlike,
            Retweet,
            Unretweet,
            Reply,
            Comment,
            Quote,
            Bookmark,
            Unbookmark,
            More,
            Delete,
            Report,
            React,
            UnReact,
            Share,
        }

        public sealed interface Text {
            public data class Raw(
                val text: String,
            ) : Text

            public data class Localized(
                val type: Type,
                val parameters: ImmutableList<String> = persistentListOf(),
            ) : Text {
                public enum class Type {
                    Like,
                    Unlike,
                    Retweet,
                    Unretweet,
                    Reply,
                    Comment,
                    Quote,
                    Bookmark,
                    Unbookmark,
                    More,
                    Delete,
                    Report,
                    React,
                    UnReact,
                    Share,
                    FxShare,
                }
            }
        }
    }
}
