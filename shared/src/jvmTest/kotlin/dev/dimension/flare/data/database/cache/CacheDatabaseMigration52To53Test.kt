package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheDatabaseMigration52To53Test {
    @Test
    fun migrationPreservesPostsPositionAndIndependentCursors() =
        runTest {
            val path = Files.createTempFile("flare-cache-v52", ".db")

            fun open() =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .addMigrations(CacheDatabase.MIGRATION_52_53)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val snapshot =
                DbTimelineReadingSnapshot("snapshot/", "home", "session/", anchorId = "anchor", anchorSortId = 123, offset = -17.25)
            val row = DbPagingTimeline(snapshot.activeKey, "post", 123)
            val segment = DbTimelineReadingSegment("snapshot/segment/", snapshot.key, "post", 123)
            try {
                open().also { db ->
                    db.timelineReadingSessionDao().save(DbTimelineReadingSession("home", "source", "session/", snapshot.key))
                    db.timelineReadingSessionDao().saveSnapshot(snapshot)
                    db.timelineReadingSessionDao().saveSegment(segment)
                    db.pagingTimelineDao().insertAll(listOf(row))
                    db.pagingTimelineDao().insertPagingKey(DbPagingKey(snapshot.activeKey, "original"))
                    db.pagingTimelineDao().insertPagingKey(DbPagingKey(segment.key, "refreshed"))
                    db.close()
                }
                // Version 52 has the same tables except for the new continuation checkpoints.
                BundledSQLiteDriver().open(path.toString()).use {
                    it.execSQL("DROP TABLE DbTimelineReadingCursor")
                    it.execSQL("PRAGMA user_version = 52")
                }
                open().also { db ->
                    try {
                        db.useWriterConnection { }
                        assertEquals(snapshot, db.timelineReadingSessionDao().snapshot(snapshot.key))
                        assertEquals(listOf(row), db.pagingTimelineDao().getByPagingKey(snapshot.activeKey))
                        assertEquals(listOf(segment), db.timelineReadingSessionDao().segments(snapshot.key))
                        assertEquals("original", db.pagingTimelineDao().getPagingKey(snapshot.activeKey)?.nextKey)
                        assertEquals("refreshed", db.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
                        assertTrue(db.timelineReadingSessionDao().cursors(snapshot.key).isEmpty())
                    } finally {
                        db.close()
                    }
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + "-wal"))
                Files.deleteIfExists(path.resolveSibling(path.fileName.toString() + "-shm"))
            }
        }
}
