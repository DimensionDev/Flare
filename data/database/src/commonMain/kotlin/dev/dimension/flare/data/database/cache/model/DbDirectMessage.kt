package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
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
    val accountType: DbAccountType,
    val roomKey: MicroBlogKey,
    val sortId: Long,
    val unreadCount: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiDMRoom,
    @PrimaryKey
    val _id: String = "$accountType-$roomKey",
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
    val timestamp: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiDMItem,
    val isLocal: Boolean = false,
)
