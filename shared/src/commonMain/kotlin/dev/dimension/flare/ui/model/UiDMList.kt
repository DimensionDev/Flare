package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText

@Immutable
data class UiDMList(
    val id: String,
    val user: UiUserV2,
    val lastMessage: UiDMItem,
) {
    val lastMessageText: String by lazy {
        when (val message = lastMessage.message) {
            is UiDMItem.Message.Text -> message.text.raw
        }
    }
}

@Immutable
data class UiDMItem(
    val id: String,
    val user: UiUserV2,
    val message: Message,
    val timestamp: UiDateTime,
    val isFromMe: Boolean,
) {
    sealed interface Message {
        data class Text(
            val text: UiRichText,
        ) : Message
    }
}
