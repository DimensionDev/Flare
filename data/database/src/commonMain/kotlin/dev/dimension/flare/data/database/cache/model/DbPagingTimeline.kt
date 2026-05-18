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
public data class DbPagingTimeline(
    public val pagingKey: String,
    public val statusId: String,
    public val sortId: Long,
    @PrimaryKey
    public val _id: String = Uuid.random().toString(),
)

@Entity
public data class DbPagingKey(
    @PrimaryKey
    public val pagingKey: String,
    public val nextKey: String? = null,
    public val prevKey: String? = null,
)

public data class DbPagingTimelineWithStatus(
    @Embedded
    public val timeline: DbPagingTimeline,
    @Relation(
        parentColumn = "statusId",
        entityColumn = "id",
        entity = DbStatus::class,
    )
    public val status: DbStatusWithReference,
)

public data class DbStatusWithUser(
    @Embedded
    public val data: DbStatus,
    @Relation(
        parentColumn = "id",
        entityColumn = "entityKey",
        entity = DbTranslation::class,
    )
    public val translations: List<DbTranslation> = emptyList(),
)

public data class DbStatusReferenceWithStatus(
    @Embedded
    public val reference: DbStatusReference,
    @Relation(
        parentColumn = "referenceStatusId",
        entityColumn = "id",
        entity = DbStatus::class,
    )
    public val status: DbStatusWithUser?,
)

public data class DbStatusWithReference(
    @Embedded
    public val status: DbStatusWithUser,
    @Relation(
        parentColumn = "id",
        entityColumn = "statusId",
        entity = DbStatusReference::class,
    )
    public val references: List<DbStatusReferenceWithStatus>,
)

public class StatusConverter {
    @TypeConverter
    public fun fromReferenceType(value: ReferenceType): String = value.name

    @TypeConverter
    public fun toReferenceType(value: String): ReferenceType = ReferenceType.valueOf(value)

    @TypeConverter
    public fun fromTimestamp(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    public fun toTimestamp(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}
