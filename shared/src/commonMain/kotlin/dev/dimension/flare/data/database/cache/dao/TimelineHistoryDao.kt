package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.dimension.flare.data.database.cache.model.DbTimelineBatch
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.cache.model.DbTimelineEntry
import dev.dimension.flare.data.database.cache.model.DbTimelineHistory

internal const val HISTORY_GAP_PREFIX = "timeline-gap:"

internal data class TimelineHistoryRow(
    val itemKey: String,
    val timelineId: String?,
    val batchId: String,
    val ordinal: Long,
    val position: Long,
)

private const val HISTORY_ROWS =
    "SELECT e.itemKey, e.timelineId, b.id AS batchId, b.ordinal, e.position " +
        "FROM DbTimelineEntry e INNER JOIN DbTimelineBatch b ON b.id = e.batchId " +
        "INNER JOIN DbPagingTimeline t ON t._id = e.timelineId WHERE e.historyKey = :historyKey " +
        "UNION ALL SELECT 'timeline-gap:' || id AS itemKey, NULL AS timelineId, id AS batchId, ordinal, " +
        "9223372036854775807 AS position FROM DbTimelineBatch " +
        "WHERE historyKey = :historyKey AND manual = 1 AND nextKey IS NOT NULL"

@Dao
internal interface TimelineHistoryDao {
    @Query("SELECT * FROM DbTimelineHistory WHERE `key` = :key")
    suspend fun history(key: String): DbTimelineHistory?

    @Upsert
    suspend fun save(history: DbTimelineHistory)

    @Upsert
    suspend fun save(batch: DbTimelineBatch)

    @Upsert
    suspend fun save(entries: List<DbTimelineEntry>)

    @Upsert
    suspend fun save(bookmark: DbTimelineBookmark)

    @Query("SELECT * FROM DbTimelineBatch WHERE historyKey = :historyKey ORDER BY ordinal")
    suspend fun batches(historyKey: String): List<DbTimelineBatch>

    @Query("SELECT * FROM DbTimelineBatch WHERE id = :id")
    suspend fun batch(id: String): DbTimelineBatch?

    @Query("SELECT * FROM DbTimelineBatch WHERE historyKey = :historyKey ORDER BY ordinal LIMIT 1")
    suspend fun newestBatch(historyKey: String): DbTimelineBatch?

    @Query("SELECT * FROM DbTimelineBatch WHERE historyKey = :historyKey AND manual = 0 LIMIT 1")
    suspend fun tail(historyKey: String): DbTimelineBatch?

    @Query("SELECT MAX(position) FROM DbTimelineEntry WHERE batchId = :batchId")
    suspend fun lastPosition(batchId: String): Long?

    @Query("SELECT * FROM DbTimelineEntry WHERE historyKey = :historyKey AND itemKey IN (:keys)")
    suspend fun entries(
        historyKey: String,
        keys: List<String>,
    ): List<DbTimelineEntry>

    @Query("SELECT * FROM (" + HISTORY_ROWS + ") ORDER BY ordinal, position LIMIT :limit OFFSET :offset")
    suspend fun page(
        historyKey: String,
        offset: Int,
        limit: Int,
    ): List<TimelineHistoryRow>

    @Query("SELECT * FROM (" + HISTORY_ROWS + ") WHERE itemKey = :itemKey LIMIT 1")
    suspend fun row(
        historyKey: String,
        itemKey: String,
    ): TimelineHistoryRow?

    @Query(
        "SELECT COUNT(*) FROM (" + HISTORY_ROWS + ") " +
            "WHERE ordinal < :ordinal OR (ordinal = :ordinal AND position < :position)",
    )
    suspend fun offset(
        historyKey: String,
        ordinal: Long,
        position: Long,
    ): Int

    @Query("SELECT * FROM (" + HISTORY_ROWS + ") WHERE timelineId IS NOT NULL ORDER BY ordinal, position LIMIT 1")
    suspend fun head(historyKey: String): TimelineHistoryRow?

    @Query(
        "SELECT * FROM (" + HISTORY_ROWS + ") WHERE timelineId IS NOT NULL " +
            "AND (ordinal > :ordinal OR (ordinal = :ordinal AND position >= :position)) " +
            "ORDER BY ordinal, position LIMIT 1",
    )
    suspend fun following(
        historyKey: String,
        ordinal: Long,
        position: Long,
    ): TimelineHistoryRow?

    @Query(
        "SELECT * FROM (" + HISTORY_ROWS + ") WHERE timelineId IS NOT NULL " +
            "AND (ordinal < :ordinal OR (ordinal = :ordinal AND position < :position)) " +
            "ORDER BY ordinal DESC, position DESC LIMIT 1",
    )
    suspend fun preceding(
        historyKey: String,
        ordinal: Long,
        position: Long,
    ): TimelineHistoryRow?

    @Query("SELECT * FROM DbTimelineBookmark WHERE tabId = :tabId AND historyKey = :historyKey")
    suspend fun bookmark(
        tabId: String,
        historyKey: String,
    ): DbTimelineBookmark?

    @Query("DELETE FROM DbTimelineBookmark WHERE tabId NOT IN (:tabIds)")
    suspend fun retainBookmarks(tabIds: List<String>)

    @Query("DELETE FROM DbTimelineBookmark")
    suspend fun clearBookmarks()

    @Query("DELETE FROM DbTimelineEntry")
    suspend fun clearEntries()

    @Query("DELETE FROM DbTimelineBatch")
    suspend fun clearBatches()

    @Query("DELETE FROM DbTimelineHistory")
    suspend fun clearHistories()
}
