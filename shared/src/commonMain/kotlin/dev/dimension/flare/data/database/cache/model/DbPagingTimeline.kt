package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType

@Entity(
    indices = [
        Index(
            value = ["accountKey", "statusKey", "pagingKey"],
            unique = true,
        ),
    ],
)
data class DbPagingTimeline(
    @PrimaryKey
    val _id: String,
    val accountKey: MicroBlogKey,
    val pagingKey: String,
    val statusKey: MicroBlogKey,
    val sortId: Long,
)

data class DbPagingTimelineWithStatus(
    @Embedded
    val timeline: DbPagingTimeline,
    @Relation(
        parentColumn = "statusKey",
        entityColumn = "statusKey",
        entity = DbStatus::class,
    )
    val status: DbStatusWithReference,
)

data class DbStatusWithUser(
    @Embedded
    val data: DbStatus,
    @Relation(parentColumn = "userKey", entityColumn = "userKey")
    val user: DbUser?,
)

data class DbStatusReferenceWithStatus(
    @Embedded
    val reference: DbStatusReference,
    @Relation(
        parentColumn = "referenceStatusKey",
        entityColumn = "statusKey",
        entity = DbStatus::class,
    )
    val status: DbStatusWithUser,
)

data class DbStatusWithReference(
    @Embedded
    val status: DbStatusWithUser,
    @Relation(
        parentColumn = "statusKey",
        entityColumn = "statusKey",
        entity = DbStatusReference::class,
    )
    val references: List<DbStatusReferenceWithStatus>,
)

class StatusConverter {
    @TypeConverter
    fun fromStatusContent(value: StatusContent): String = value.encodeJson()

    @TypeConverter
    fun toStatusContent(value: String): StatusContent = value.decodeJson()

    @TypeConverter
    fun fromReferenceType(value: ReferenceType): String = value.name

    @TypeConverter
    fun toReferenceType(value: String): ReferenceType = ReferenceType.valueOf(value)
}
