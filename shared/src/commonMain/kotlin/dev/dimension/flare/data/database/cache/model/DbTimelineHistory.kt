package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity
internal data class DbTimelineHistory(
    @PrimaryKey val key: String,
    val generation: String,
)

/** A refresh owns its continuation; existing entries never change batches. */
@Entity(indices = [Index(value = ["historyKey", "ordinal"])])
internal data class DbTimelineBatch(
    @PrimaryKey val id: String,
    val historyKey: String,
    val ordinal: Long,
    val nextKey: String?,
    val manual: Boolean,
    val lowerItemKey: String?,
    @ColumnInfo(defaultValue = "NULL") val pageKeys: String? = null,
    @ColumnInfo(defaultValue = "NULL") val gapAfterItemKey: String? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceHeads: String? = null,
)

@Entity(
    primaryKeys = ["historyKey", "itemKey"],
    indices = [Index(value = ["batchId", "position"]), Index(value = ["timelineId"])],
)
internal data class DbTimelineEntry(
    val historyKey: String,
    val itemKey: String,
    val timelineId: String,
    val batchId: String,
    val position: Long,
    @ColumnInfo(defaultValue = "NULL") val createdAt: Long? = null,
)

@Entity
internal data class DbTimelineBookmark(
    @PrimaryKey val tabId: String,
    val historyKey: String,
    val itemKey: String,
    val batchOrdinal: Long,
    val position: Long,
    val offset: Double,
    @ColumnInfo(defaultValue = "NULL") val seenItemKey: String? = null,
    @ColumnInfo(defaultValue = "NULL") val seenOrdinal: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val seenPosition: Long? = null,
)

/** A tab owns its bookmark; many tabs may retain the same list. */
@Entity(indices = [Index(value = ["historyKey"])])
internal data class DbTimelineBinding(
    @PrimaryKey val tabId: String,
    val historyKey: String,
    val generation: String,
)
