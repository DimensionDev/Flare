package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.launch
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

@Immutable
public sealed interface ActionMenu {
    @Immutable
    public data class Group internal constructor(
        val displayItem: Item,
        val actions: ImmutableList<ActionMenu>,
    ) : ActionMenu

    @Immutable
    public data class AsyncActionMenuItem internal constructor(
        val flow: Flow<Item>,
    ) : ActionMenu

    @Immutable
    public data object Divider : ActionMenu

    @Immutable
    public data class Item internal constructor(
        val icon: Icon? = null,
        val text: Text? = null,
        val count: UiNumber? = null,
        val onClicked: (ClickContext.() -> Unit)? = null,
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

        @Immutable
        public sealed interface Text {
            @Immutable
            public data class Raw(
                val text: String,
            ) : Text

            @Immutable
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
                    BlockWithHandleParameter,
                    MuteWithHandleParameter,
                }
            }
        }
    }
}

internal fun userActionsMenu(
    accountKey: MicroBlogKey?,
    userKey: MicroBlogKey,
    handle: String,
): List<ActionMenu> =
    listOfNotNull(
        ActionMenu.Item(
            icon = ActionMenu.Item.Icon.Mute,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.MuteWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            onClicked = {
                launcher.launch(DeeplinkRoute.MuteUser(accountKey, userKey))
            },
        ),
        ActionMenu.Item(
            icon = ActionMenu.Item.Icon.Block,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.BlockWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            onClicked = {
                launcher.launch(DeeplinkRoute.BlockUser(accountKey, userKey))
            },
        ),
    )
