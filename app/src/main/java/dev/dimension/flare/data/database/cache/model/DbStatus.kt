package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(
    tableName = "status",
    indices = [
        androidx.room.Index(value = ["statusKey", "accountKey"], unique = true)
    ]
)
data class DbStatus(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val statusKey: MicroBlogKey,
    val accountKey: MicroBlogKey,
    val userKey: MicroBlogKey?,
    val platformType: PlatformType,
    val content: StatusContent
)

@Serializable
sealed interface StatusContent {
    @Serializable
    @SerialName("mastodon")
    data class Mastodon(val data: Status) : StatusContent

    @Serializable
    @SerialName("mastodon-notification")
    data class MastodonNotification(val data: Notification) : StatusContent

    @Serializable
    @SerialName("misskey")
    data class Misskey(
        val data: dev.dimension.flare.data.network.misskey.api.model.Note
    ) : StatusContent

    @Serializable
    @SerialName("misskey-notification")
    data class MisskeyNotification(
        val data: dev.dimension.flare.data.network.misskey.api.model.Notification
    ) : StatusContent

//    @Serializable
//    @SerialName("bluesky")
//    data class Bluesky(val data: PostView) : StatusContent
//
//    @Serializable
//    @SerialName("bluesky-notification")
//    data class BlueskyNotification(val data: app.bsky.notification.ListNotificationsNotification) : StatusContent
}

data class DbStatusWithUser(
    @Embedded
    val data: DbStatus,
    @Relation(parentColumn = "userKey", entityColumn = "userKey")
    val user: DbUser?
)

data class DbStatusReferenceWithStatus(
    @Embedded
    val reference: DbStatusReference,
    @Relation(
        parentColumn = "referenceStatusKey",
        entityColumn = "statusKey",
        entity = DbStatus::class
    )
    val status: DbStatusWithUser
)

data class DbStatusWithReference(
    @Embedded
    val status: DbStatusWithUser,
    @Relation(
        parentColumn = "statusKey",
        entityColumn = "statusKey",
        entity = DbStatusReference::class
    )
    val references: List<DbStatusReferenceWithStatus>
)
