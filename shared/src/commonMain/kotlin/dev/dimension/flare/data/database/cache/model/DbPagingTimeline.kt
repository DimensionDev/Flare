package dev.dimension.flare.data.database.cache.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import androidx.room3.TypeConverter
import dev.dimension.flare.model.ReferenceType
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    indices = [
        Index(
            value = ["statusId", "pagingKey"],
            unique = true,
        ),
        Index(
            value = ["pagingKey", "sortId"],
        ),
    ],
)
internal data class DbPagingTimeline(
    val pagingKey: String,
    val statusId: String,
    val sortId: Long,
    @PrimaryKey
    val _id: String = Uuid.random().toString(),
)

@Entity
internal data class DbPagingKey(
    @PrimaryKey
    val pagingKey: String,
    val nextKey: String? = null,
    val prevKey: String? = null,
)

internal data class DbPagingTimelineWithStatus(
    @Embedded
    val timeline: DbPagingTimeline,
    @Relation(
        parentColumn = "statusId",
        entityColumn = "id",
        entity = DbStatus::class,
    )
    val status: DbStatusWithReference,
)

internal data class DbStatusWithUser(
    @Embedded
    val data: DbStatus,
    @Relation(
        parentColumn = "id",
        entityColumn = "entityKey",
        entity = DbTranslation::class,
    )
    val translations: List<DbTranslation> = emptyList(),
)

internal data class DbStatusReferenceWithStatus(
    @Embedded
    val reference: DbStatusReference,
    @Relation(
        parentColumn = "referenceStatusId",
        entityColumn = "id",
        entity = DbStatus::class,
    )
    val status: DbStatusWithUser?,
)

internal data class DbStatusWithReference(
    @Embedded
    val status: DbStatusWithUser,
    @Relation(
        parentColumn = "id",
        entityColumn = "statusId",
        entity = DbStatusReference::class,
    )
    val references: List<DbStatusReferenceWithStatus>,
)

internal class StatusConverter {
    @TypeConverter
    fun fromReferenceType(value: ReferenceType): String = value.name

    @TypeConverter
    fun toReferenceType(value: String): ReferenceType = ReferenceType.valueOf(value)

    @TypeConverter
    fun fromTimestamp(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    fun toTimestamp(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}
