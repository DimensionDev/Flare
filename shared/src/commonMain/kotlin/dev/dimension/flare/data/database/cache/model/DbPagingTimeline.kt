package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.time.Instant

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
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val message: UiTimelineV2.Message? = null,
    val messageRenderHash: Int? = message?.renderHash,
    val statusReferenceHash: Long = stableDatabaseHash("status-reference-set"),
    val presentationReferenceHash: Long = stableDatabaseHash("presentation-reference-set"),
    /**
     * Monotonic cache revision bumped when this row's status, references, or translations change.
     *
     * Keeping the revision on the timeline row lets stable-prefix refreshes detect dependency
     * changes without materializing every dependency row across the SQLite/Kotlin boundary.
     */
    val dependencyRevision: Long = 0,
    @PrimaryKey
    val _id: String = createId(pagingKey = pagingKey, statusId = statusId),
) {
    companion object {
        fun createId(
            pagingKey: String,
            statusId: String,
        ): String = stableDatabaseId("timeline", pagingKey, statusId)
    }
}

@Entity(
    tableName = "timeline_item_presentation_reference",
    indices = [
        Index(
            value = ["pagingKey", "statusId"],
        ),
        Index(
            value = [
                "pagingKey",
                "statusId",
                "presentationType",
                "referenceStatusId",
            ],
            unique = true,
        ),
        Index(value = ["referenceStatusId"]),
    ],
)
internal data class DbTimelineItemPresentationReference(
    @PrimaryKey
    val _id: String,
    val pagingKey: String,
    val statusId: String,
    val referenceStatusId: String,
    val presentationType: DbTimelineItemPresentationType,
    val referenceOrder: Int = 0,
) {
    companion object {
        fun createId(
            pagingKey: String,
            statusId: String,
            referenceStatusId: String,
            presentationType: DbTimelineItemPresentationType,
        ): String =
            stableDatabaseId(
                "presentation-reference",
                pagingKey,
                statusId,
                presentationType.name,
                referenceStatusId,
            )
    }
}

internal enum class DbTimelineItemPresentationType {
    InlineParent,
    Quote,
    Repost,
}

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
        parentColumns = ["status_id"],
        entityColumns = ["entityKey"],
        entity = DbTranslation::class,
    )
    val statusTranslations: List<DbTranslation> = emptyList(),
    @Relation(
        parentColumns = ["status_id"],
        entityColumns = ["statusId"],
        entity = DbStatusReference::class,
    )
    val references: List<DbStatusReferenceWithStatus> = emptyList(),
    @Embedded(prefix = "status_")
    val statusData: DbStatus,
    @Relation(
        parentColumns = ["pagingKey", "statusId"],
        entityColumns = ["pagingKey", "statusId"],
        entity = DbTimelineItemPresentationReference::class,
    )
    val presentationReferences: List<DbTimelineItemPresentationReferenceWithStatus> = emptyList(),
) {
    constructor(
        timeline: DbPagingTimeline,
        status: DbStatusWithReference,
        presentationReferences: List<DbTimelineItemPresentationReferenceWithStatus> = emptyList(),
    ) : this(
        timeline = timeline,
        statusTranslations = status.status.translations,
        references = status.references,
        statusData = status.status.data,
        presentationReferences = presentationReferences,
    )

    val status: DbStatusWithReference
        get() =
            DbStatusWithReference(
                status =
                    DbStatusWithUser(
                        data = statusData,
                        translations = statusTranslations,
                    ),
                references = references,
            )
}

internal data class DbStatusWithUser(
    @Embedded
    val data: DbStatus,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["entityKey"],
        entity = DbTranslation::class,
    )
    val translations: List<DbTranslation> = emptyList(),
)

internal data class DbStatusReferenceWithStatus(
    @Embedded
    val reference: DbStatusReference,
    @Relation(
        parentColumns = ["referenceStatusId"],
        entityColumns = ["id"],
        entity = DbStatus::class,
    )
    val status: DbStatusWithUser?,
)

internal data class DbTimelineItemPresentationReferenceWithStatus(
    @Embedded
    val reference: DbTimelineItemPresentationReference,
    @Relation(
        parentColumns = ["referenceStatusId"],
        entityColumns = ["id"],
        entity = DbStatus::class,
    )
    val status: DbStatusWithUser?,
)

internal data class DbStatusWithReference(
    @Embedded
    val status: DbStatusWithUser,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["statusId"],
        entity = DbStatusReference::class,
    )
    val references: List<DbStatusReferenceWithStatus>,
)

internal class StatusConverter {
    @ColumnTypeConverter
    fun fromReferenceType(value: ReferenceType): String = value.name

    @ColumnTypeConverter
    fun toReferenceType(value: String): ReferenceType = ReferenceType.valueOf(value)

    @ColumnTypeConverter
    fun fromTimelineItemPresentationType(value: DbTimelineItemPresentationType): String = value.name

    @ColumnTypeConverter
    fun toTimelineItemPresentationType(value: String): DbTimelineItemPresentationType = DbTimelineItemPresentationType.valueOf(value)

    @ColumnTypeConverter
    fun fromTimelineMessage(value: UiTimelineV2.Message?): ByteArray? = value?.encodeProtobuf<UiTimelineV2.Message>()

    @ColumnTypeConverter
    fun toTimelineMessage(value: ByteArray?): UiTimelineV2.Message? = value?.decodeProtobuf<UiTimelineV2.Message>()

    @ColumnTypeConverter
    fun fromTimestamp(value: Instant): Long = value.toEpochMilliseconds()

    @ColumnTypeConverter
    fun toTimestamp(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}

internal fun stableDatabaseId(
    type: String,
    vararg parts: String,
): String =
    buildString {
        append(type)
        parts.forEach { part ->
            append('|')
            append(part.length)
            append(':')
            append(part)
        }
    }

internal fun stableDatabaseHash(
    type: String,
    vararg parts: String,
): Long = stableDatabaseHash(type, parts.asIterable())

/** Folds the stable id format directly, avoiding a temporary combined String. */
internal fun stableDatabaseHash(
    type: String,
    parts: Iterable<String>,
): Long {
    var hash = FNV_1A_64_OFFSET_BASIS

    fun add(value: String) {
        value.forEach { character ->
            hash = (hash xor character.code.toLong()) * FNV_1A_64_PRIME
        }
    }

    add(type)
    parts.forEach { part ->
        add("|")
        add(part.length.toString())
        add(":")
        add(part)
    }
    return hash
}

private const val FNV_1A_64_OFFSET_BASIS = -3750763034362895579L
private const val FNV_1A_64_PRIME = 1099511628211L
