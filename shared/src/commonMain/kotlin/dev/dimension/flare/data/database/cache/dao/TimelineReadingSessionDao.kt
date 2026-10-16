package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TimelineReadingSessionDao {
    @Query("SELECT * FROM DbTimelineReadingSegment WHERE snapshotKey = :snapshotKey ORDER BY boundarySortId")
    suspend fun segments(snapshotKey: String): List<DbTimelineReadingSegment>

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
