package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiNumber
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

public sealed interface ActionMenu {
    public data class Group internal constructor(
        val displayItem: Item,
        val actions: ImmutableList<ActionMenu>,
    ) : ActionMenu

    public data class AsyncActionMenuItem internal constructor(
        val flow: Flow<Item>,
    ) : ActionMenu

    public data object Divider : ActionMenu

    public data class Item internal constructor(
        val icon: Icon? = null,
        val text: Text? = null,
        val count: UiNumber? = null,
        val onClicked: (ClickContext.() -> Unit)? = null,
        val shareContent: String? = null,
        val color: Color? = null,
    ) : ActionMenu {
        init {
            require(icon != null || text != null) {
                "icon and text cannot be both null"
            }
        }

        public enum class Color {
            Red,
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
            MoreVerticel,
            Delete,
            Report,
            React,
            UnReact,
            Share,
            List,
            ChatMessage,
            Mute,
            UnMute,
            Block,
            UnBlock,
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
                    EditUserList,
                    SendMessage,
                    Mute,
                    UnMute,
                    Block,
                    UnBlock,
                }
            }
        }
    }
}
