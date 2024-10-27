package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class UiDMRoom(
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
    val id by lazy {
        key.toString()
    }
    val user by lazy {
        users.firstOrNull()
    }
}

@Immutable
data class UiDMItem(
    val key: MicroBlogKey,
    val user: UiUserV2,
    val content: Message,
    val timestamp: UiDateTime,
    val isFromMe: Boolean,
    val sendState: SendState?,
) {
    sealed interface Message {
        data class Text(
            val text: UiRichText,
        ) : Message

        data object Deleted : Message
    }

    enum class SendState {
        Sending,
        Failed,
    }

    val id by lazy {
        key.toString()
    }
}
