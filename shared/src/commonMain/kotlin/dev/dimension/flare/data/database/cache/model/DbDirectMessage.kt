package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    indices = [
        androidx.room.Index(
            value = ["accountKey", "roomKey"],
            unique = true,
        ),
    ],
)
data class DbDirectMessageTimeline(
    val accountKey: MicroBlogKey,
    val roomKey: MicroBlogKey,
    val sortId: Long,
    val unreadCount: Long,
    @PrimaryKey
    val _id: String = "$accountKey-$roomKey",
)

data class DbDirectMessageTimelineWithRoom(
    @Embedded
    val timeline: DbDirectMessageTimeline,
    @Relation(
        parentColumn = "roomKey",
        entityColumn = "roomKey",
        entity = DbMessageRoom::class,
    )
    val room: DbMessageRoomWithLastMessageAndUser,
)

// data class DbDirectMessageTimelineWithRoom(
//    @Embedded
//    val timeline: DbDirectMessageTimeline,
//    @Relation(
//        parentColumn = "accountKey",
//        entityColumn = "accountKey",
//        entity = DbDirectMessageTimelineReference::class,
//    )
//    val rooms: List<DbDirectMessageTimelineReferenceWithRoom>,
// )

@Entity
data class DbMessageRoom(
    @PrimaryKey
    val roomKey: MicroBlogKey,
    val platformType: PlatformType,
    val messageKey: MicroBlogKey?,
)

@Entity
data class DbMessageRoomReference(
    val roomKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    @PrimaryKey
    val _id: String = "$roomKey-$userKey",
)

data class DbMessageRoomReferenceWithUser(
    @Embedded
    val reference: DbMessageRoomReference,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "userKey",
    )
    val user: DbUser,
)

data class DbMessageRoomWithLastMessageAndUser(
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

@Entity
data class DbMessageItem(
    @PrimaryKey
    val messageKey: MicroBlogKey,
    val roomKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    val timestamp: Long,
    val content: MessageContent,
    val isLocal: Boolean = false,
)

data class DbMessageItemWithUser(
    @Embedded
    val message: DbMessageItem,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "userKey",
    )
    val user: DbUser,
)

@Serializable
sealed interface MessageContent {
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

class MessageContentConverters {
    @TypeConverter
    fun fromMessageContent(content: MessageContent): String = content.encodeJson()

    @TypeConverter
    fun toMessageContent(value: String): MessageContent = value.decodeJson()
}
