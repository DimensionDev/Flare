package dev.dimension.flare.data.database.cache.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    indices = [
        Index(
            value = ["accountType", "roomKey"],
            unique = true,
        ),
        Index(
            value = ["accountType", "sortId"],
        ),
    ],
)
public data class DbDirectMessageTimeline(
    val accountType: DbAccountType,
    val roomKey: MicroBlogKey,
    val sortId: Long,
    val unreadCount: Long,
    @PrimaryKey
    val _id: String = "$accountType-$roomKey",
)

public data class DbDirectMessageTimelineWithRoom(
    @Embedded
    val timeline: DbDirectMessageTimeline,
    @Relation(
        parentColumn = "roomKey",
        entityColumn = "roomKey",
        entity = DbMessageRoom::class,
    )
    val room: DbMessageRoomWithLastMessageAndUser,
)

@Entity
public data class DbMessageRoom(
    @PrimaryKey
    val roomKey: MicroBlogKey,
    val platformType: PlatformType,
    val messageKey: MicroBlogKey?,
)

@Entity(
    indices = [
        Index(
            value = ["roomKey"],
        ),
    ],
)
public data class DbMessageRoomReference(
    val roomKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    @PrimaryKey
    val _id: String = "$roomKey-$userKey",
)

public data class DbMessageRoomReferenceWithUser(
    @Embedded
    val reference: DbMessageRoomReference,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "userKey",
    )
    val user: DbUser,
)

public data class DbMessageRoomWithLastMessageAndUser(
    @Embedded
    val room: DbMessageRoom,
    @Relation(
        parentColumn = "messageKey",
        entityColumn = "messageKey",
        entity = DbMessageItem::class,
    )
    val lastMessage: DbMessageItemWithUser?,
    @Relation(
        parentColumn = "roomKey",
        entityColumn = "roomKey",
        entity = DbMessageRoomReference::class,
    )
    val users: List<DbMessageRoomReferenceWithUser>,
)

@Entity(
    indices = [
        Index(
            value = ["roomKey", "timestamp"],
        ),
    ],
)
public data class DbMessageItem(
    @PrimaryKey
    val messageKey: MicroBlogKey,
    val roomKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    val timestamp: Long,
    val content: MessageContent,
    val showSender: Boolean,
    val isLocal: Boolean = false,
)

public data class DbMessageItemWithUser(
    @Embedded
    val message: DbMessageItem,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "userKey",
    )
    val user: DbUser,
)

@Serializable
public sealed interface MessageContent {
    @Serializable
    sealed interface Bluesky : MessageContent {
        @Serializable
        @SerialName("bluesky-message")
        data class Message(
            val data: chat.bsky.convo.MessageView,
        ) : Bluesky

        @Serializable
        @SerialName("bluesky-deleted")
        data class Deleted(
            val data: chat.bsky.convo.DeletedMessageView,
        ) : Bluesky
    }

    @Serializable
    sealed interface XQT : MessageContent {
        @Serializable
        @SerialName("xqt-message")
        data class Message(
            val data: String,
        ) : XQT
    }

    @Serializable
    @SerialName("local")
    data class Local(
        val text: String,
        val state: State,
    ) : MessageContent {
        @Serializable
        enum class State {
            SENDING,
            FAILED,
        }
    }
}

public class MessageContentConverters {
    @TypeConverter
    fun fromMessageContent(content: MessageContent): String = content.encodeJson()

    @TypeConverter
    fun toMessageContent(value: String): MessageContent = value.decodeJson()
}
