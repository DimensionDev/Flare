package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingCursor
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TimelineReadingSessionDao {
    @Query("SELECT * FROM DbTimelineReadingCursor WHERE snapshotKey = :snapshotKey ORDER BY lastAttempt, `key`")
    suspend fun cursors(snapshotKey: String): List<DbTimelineReadingCursor>

    @Query("SELECT * FROM DbTimelineReadingCursor WHERE snapshotKey = :snapshotKey AND paused = 0 ORDER BY lastAttempt, `key` LIMIT 1")
    suspend fun nextCursor(snapshotKey: String): DbTimelineReadingCursor?

    @Query("SELECT * FROM DbTimelineReadingCursor WHERE snapshotKey = :snapshotKey AND paused = 1 ORDER BY lastAttempt, `key`")
    suspend fun pausedCursors(snapshotKey: String): List<DbTimelineReadingCursor>

    @Query("UPDATE DbTimelineReadingCursor SET paused = 0, retryFailed = 1 WHERE snapshotKey = :snapshotKey")
    suspend fun retrySourcesOnNextLoad(snapshotKey: String)

    @Query("UPDATE DbTimelineReadingSegment SET failed = 0 WHERE snapshotKey = :snapshotKey AND confirmed = 1 AND failed = 1")
    suspend fun resumeSegments(snapshotKey: String)

    @Query("SELECT * FROM DbTimelineReadingCursor WHERE `key` = :key")
    suspend fun cursor(key: String): DbTimelineReadingCursor?

    @Upsert
    suspend fun saveCursor(cursor: DbTimelineReadingCursor)

    suspend fun deleteCursors(prefix: String) = deleteCursorsMatchingPrefix(prefix.toPagingKeyPrefixGlob())

    @Query("DELETE FROM DbTimelineReadingCursor WHERE `key` GLOB :pattern")
    suspend fun deleteCursorsMatchingPrefix(pattern: String)

    @Query("SELECT * FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey ORDER BY boundarySortId")
    suspend fun segments(snapshotKey: String): List<DbTimelineReadingSegment>

    @Query(
        "SELECT * FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey AND confirmed = 1 AND boundarySortId >= :first AND boundarySortId <= :last ORDER BY boundarySortId",
    )
    suspend fun segmentsInWindow(
        snapshotKey: String,
        first: Long,
        last: Long,
    ): List<DbTimelineReadingSegment>

    @Query("SELECT EXISTS(SELECT 1 FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey)")
    suspend fun hasSegments(snapshotKey: String): Boolean

    @Query("SELECT * FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey AND `key` = :key")
    suspend fun segment(
        snapshotKey: String,
        key: String,
    ): DbTimelineReadingSegment?

    @Upsert
    suspend fun saveSegment(segment: DbTimelineReadingSegment)

    @Query("DELETE FROM DbTimelineReadingSegment WHERE `key` = :key")
    suspend fun deleteSegment(key: String)

    @Query("DELETE FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey")
    suspend fun deleteSegments(snapshotKey: String)

    @Query("SELECT * FROM DbTimelineReadingSnapshot WHERE `key` = :key")
    suspend fun snapshot(key: String): DbTimelineReadingSnapshot?

    @Query("SELECT * FROM DbTimelineReadingSnapshot WHERE sessionKey = :sessionKey")
    suspend fun snapshots(sessionKey: String): List<DbTimelineReadingSnapshot>

    @Upsert
    suspend fun saveSnapshot(snapshot: DbTimelineReadingSnapshot)

    @Query("DELETE FROM DbTimelineReadingSnapshot WHERE `key` = :key")
    suspend fun deleteSnapshot(key: String)

    @Query("DELETE FROM DbTimelineReadingSnapshot WHERE sessionKey = :sessionKey")
    suspend fun deleteSnapshots(sessionKey: String)

    @Query("SELECT * FROM DbTimelineReadingSession WHERE tabId = :tabId")
    suspend fun get(tabId: String): DbTimelineReadingSession?

    @Query("SELECT * FROM DbTimelineReadingSession")
    suspend fun all(): List<DbTimelineReadingSession>

    @Query("SELECT * FROM DbTimelineReadingSession WHERE tabId = :tabId")
    fun observe(tabId: String): Flow<DbTimelineReadingSession?>

    @Upsert
    suspend fun save(session: DbTimelineReadingSession)

    @Query("DELETE FROM DbTimelineReadingSession WHERE tabId = :tabId")
    suspend fun delete(tabId: String)

    @Query("UPDATE DbTimelineReadingSession SET snapshotKey = :snapshotKey WHERE tabId = :tabId")
    suspend fun retainSnapshot(
        tabId: String,
        snapshotKey: String,
    )

    @Query("SELECT tabId FROM DbHomeTimelineSelection WHERE id = 0")
    fun selectedTab(): Flow<String?>

    @Query("DELETE FROM DbHomeTimelineSelection WHERE tabId NOT IN (:tabIds)")
    suspend fun retainSelection(tabIds: List<String>)

    @Upsert
    suspend fun select(selection: DbHomeTimelineSelection)
}
