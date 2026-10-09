package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TimelineReadingSessionDao {
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

    @Query(
        "UPDATE DbTimelineReadingSession SET anchorId = :anchorId, anchorSortId = :sortId, offset = :offset WHERE tabId = :tabId AND `key` = :key",
    )
    suspend fun savePosition(
        tabId: String,
        key: String,
        anchorId: String,
        sortId: Long,
        offset: Double,
    )

    @Query("SELECT tabId FROM DbHomeTimelineSelection WHERE id = 0")
    fun selectedTab(): Flow<String?>

    @Query("DELETE FROM DbHomeTimelineSelection WHERE tabId NOT IN (:tabIds)")
    suspend fun retainSelection(tabIds: List<String>)

    @Upsert
    suspend fun select(selection: DbHomeTimelineSelection)
}
