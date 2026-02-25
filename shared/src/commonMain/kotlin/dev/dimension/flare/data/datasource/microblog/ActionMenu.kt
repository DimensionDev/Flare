package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.onClicked
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
public sealed class ActionMenu {
    @Immutable
    @Serializable
    public data class Group internal constructor(
        val displayItem: Item,
        val actions: ImmutableList<ActionMenu>,
    ) : ActionMenu()

//    @Immutable
//    @Serializable
//    public data class AsyncActionMenuItem internal constructor(
//        @Transient
//        val flow: Flow<Item> = emptyFlow(),
//    ) : ActionMenu()

    @Immutable
    @Serializable
    public data object Divider : ActionMenu()

    @Immutable
    @Serializable
    public data class Item internal constructor(
        val icon: UiIcon? = null,
        val text: Text? = null,
        val count: UiNumber? = null,
        val color: Color? = null,
        private val clickEvent: ClickEvent? = null,
        @Transient
        private val clickAction: (ClickContext.() -> Unit)? = null,
    ) : ActionMenu() {
        init {
            require(icon != null || text != null) {
                "icon and text cannot be both null"
            }
        }

        val onClicked: (ClickContext.() -> Unit)? by lazy {
            clickAction ?: clickEvent?.onClicked
        }

        @Serializable
        public enum class Color {
            Red,
            ContentColor,
            PrimaryColor,
        }

        @Immutable
        @Serializable
        public sealed interface Text {
            @Immutable
            @Serializable
            public data class Raw(
                val text: String,
            ) : Text

            @Immutable
            @Serializable
            public data class Localized(
                val type: Type,
                val parameters: ImmutableList<String> = persistentListOf(),
            ) : Text {
                @Serializable
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
            icon = UiIcon.Mute,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.MuteWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.MuteUser(accountKey, userKey),
                ),
        ),
        ActionMenu.Item(
            icon = UiIcon.Block,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.BlockWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.BlockUser(accountKey, userKey),
                ),
        ),
    )
