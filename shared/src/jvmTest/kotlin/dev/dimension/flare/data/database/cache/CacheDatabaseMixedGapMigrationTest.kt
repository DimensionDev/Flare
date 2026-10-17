package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheDatabaseMixedGapMigrationTest {
    @Test
    fun addingSourceProgressPreservesReadingPositionsGapsAndCursors() =
        runTest {
            val path = Files.createTempFile("flare-cache-mixed-gap", ".db")

            fun open() =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .addMigrations(CacheDatabase.MIGRATION_55_56)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val snapshot =
                DbTimelineReadingSnapshot("snapshot/", "home", "session/", anchorId = "anchor", anchorSortId = 123, offset = -17.25)
            val segment = DbTimelineReadingSegment("snapshot/segment/", snapshot.key, "post", 123)
            val row = DbPagingTimeline(snapshot.activeKey, "post", 123)
            val cursor = DbPagingKey("${segment.key}source/child/staging", "next", "previous")
            try {
                open().also { db ->
                    db.timelineReadingSessionDao().saveSnapshot(snapshot)
                    db.timelineReadingSessionDao().saveSegment(segment)
                    db.pagingTimelineDao().insertAll(listOf(row))
                    db.pagingTimelineDao().insertPagingKey(cursor)
                    db.close()
                }
                BundledSQLiteDriver().open(path.toString()).use {
                    it.execSQL("ALTER TABLE DbPagingKey DROP COLUMN readingState")
                    it.execSQL("ALTER TABLE DbTimelineReadingSegment DROP COLUMN failed")
                    it.execSQL("PRAGMA user_version = 55")
                }
                open().also { db ->
                    try {
                        assertEquals(snapshot, db.timelineReadingSessionDao().snapshot(snapshot.key))
                        assertEquals(listOf(segment), db.timelineReadingSessionDao().segments(snapshot.key))
                        assertEquals(listOf(row), db.pagingTimelineDao().getByPagingKey(snapshot.activeKey))
                        assertEquals(cursor, db.pagingTimelineDao().getPagingKey(cursor.pagingKey))
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
