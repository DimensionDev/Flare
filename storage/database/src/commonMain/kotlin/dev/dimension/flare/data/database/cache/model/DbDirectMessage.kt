package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom

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
    public val accountType: DbAccountType,
    public val roomKey: MicroBlogKey,
    public val sortId: Long,
    public val unreadCount: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val content: UiDMRoom,
    @PrimaryKey
    public val _id: String = "$accountType-$roomKey",
)

@Entity
public data class DbMessageRoom(
    @PrimaryKey
    public val roomKey: MicroBlogKey,
    public val platformType: PlatformType,
    public val messageKey: MicroBlogKey?,
)

@Entity(
    indices = [
        Index(
            value = ["roomKey"],
        ),
    ],
)
public data class DbMessageRoomReference(
    public val roomKey: MicroBlogKey,
    public val userKey: MicroBlogKey,
    @PrimaryKey
    public val _id: String = "$roomKey-$userKey",
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
    public val messageKey: MicroBlogKey,
    public val roomKey: MicroBlogKey,
    public val userKey: MicroBlogKey,
    public val timestamp: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val content: UiDMItem,
    public val showSender: Boolean,
    public val isLocal: Boolean = false,
    public val remoteCursor: String? = null,
)
