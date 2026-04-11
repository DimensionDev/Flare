package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class UiDMRoom public constructor(
    val key: MicroBlogKey,
    val users: SerializableImmutableList<UiProfile>,
    val lastMessage: UiDMItem?,
    val unreadCount: Long,
) {
    val lastMessageText: String by lazy {
        when (val message = lastMessage?.content) {
            is UiDMItem.Message.Text -> message.text.raw
            UiDMItem.Message.Deleted -> ""
            null -> ""
            is UiDMItem.Message.Media -> ""
            is UiDMItem.Message.Status -> message.status.content.raw
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
@Serializable
public data class UiDMItem public constructor(
    val key: MicroBlogKey,
    val user: UiProfile,
    val content: Message,
    val timestamp: UiDateTime,
    val isFromMe: Boolean,
    val sendState: SendState?,
    val showSender: Boolean,
) {
    @Serializable
    public sealed interface Message {
        @Serializable
        public data class Text(
            val text: UiRichText,
        ) : Message

        @Serializable
        public data class Media(
            val media: UiMedia,
        ) : Message

        @Serializable
        public data class Status(
            val status: UiTimelineV2.Post,
        ) : Message

        @Serializable
        public data object Deleted : Message
    }

    @Serializable
    public enum class SendState {
        Sending,
        Failed,
    }

    val id: String by lazy {
        key.toString()
    }
}
