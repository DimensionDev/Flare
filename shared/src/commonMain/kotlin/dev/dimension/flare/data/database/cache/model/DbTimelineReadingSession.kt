package dev.dimension.flare.data.database.cache.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity
internal data class DbTimelineReadingSession(
    @PrimaryKey val tabId: String,
    val sourceKey: String,
    val key: String,
    val anchorId: String? = null,
    val anchorSortId: Long? = null,
    val offset: Double = 0.0,
    val hasNewContent: Boolean = false,
    val pendingReady: Boolean = false,
)

@Entity
internal data class DbHomeTimelineSelection(
    @PrimaryKey val id: Int = 0,
    val tabId: String,
)
