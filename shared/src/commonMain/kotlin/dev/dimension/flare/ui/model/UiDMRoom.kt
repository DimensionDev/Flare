package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList

@Immutable
public data class UiDMRoom internal constructor(
    val key: MicroBlogKey,
    val users: ImmutableList<UiUserV2>,
    val lastMessage: UiDMItem?,
    val unreadCount: Long,
) {
    val lastMessageText: String by lazy {
        when (val message = lastMessage?.content) {
            is UiDMItem.Message.Text -> message.text.raw
            UiDMItem.Message.Deleted -> ""
            null -> ""
        }
    }
    val id: String by lazy {
        key.toString()
    }
    val hasUser: Boolean by lazy {
        users.isNotEmpty()
    }
}

@Immutable
public data class UiDMItem internal constructor(
    val key: MicroBlogKey,
    val user: UiUserV2,
    val content: Message,
    val timestamp: UiDateTime,
    val isFromMe: Boolean,
    val sendState: SendState?,
) {
    public sealed interface Message {
        public data class Text(
            val text: UiRichText,
        ) : Message

        public data object Deleted : Message
    }

    public enum class SendState {
        Sending,
        Failed,
    }

    val id: String by lazy {
        key.toString()
    }
}
