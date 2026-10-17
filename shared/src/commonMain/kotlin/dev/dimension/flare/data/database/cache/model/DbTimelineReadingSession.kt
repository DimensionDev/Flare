package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity
internal data class DbTimelineReadingSession(
    @PrimaryKey val tabId: String,
    val sourceKey: String,
    val key: String,
    val snapshotKey: String? = null,
)

@Entity
internal data class DbTimelineReadingSnapshot(
    @PrimaryKey val key: String,
    val tabId: String,
    val sessionKey: String,
    val activeKey: String = "${key}active/",
    val pendingKey: String = "${key}pending/",
    val anchorId: String? = null,
    val anchorSortId: Long? = null,
    val offset: Double = 0.0,
    val hasNewContent: Boolean = false,
    val pendingReady: Boolean = false,
    val newContentBoundaryId: String? = null,
)

@Entity(indices = [Index(value = ["snapshotKey", "boundarySortId"])])
internal data class DbTimelineReadingSegment(
    @PrimaryKey val key: String,
    val snapshotKey: String,
    val boundaryId: String,
    val boundarySortId: Long,
    @ColumnInfo(defaultValue = "0") val failed: Boolean = false,
)

@Entity(indices = [Index(value = ["snapshotKey", "lastAttempt"])])
internal data class DbTimelineReadingCursor(
    @PrimaryKey val key: String,
    val snapshotKey: String,
    val lastAttempt: Long,
    val lastPageIds: String?,
)

@Entity
internal data class DbHomeTimelineSelection(
    @PrimaryKey val id: Int = 0,
    val tabId: String,
)
