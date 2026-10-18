package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingCursor
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheDatabaseReadingCacheResetTest {
    @Test
    fun oldCacheWithoutContinuationCheckpointsIsDiscarded() = resetCache(52)

    @Test
    fun previousDevelopmentCacheIsDiscarded() = resetCache(53)

    @Test
    fun cacheWithoutSegmentIndexIsDiscarded() = resetCache(54)

    @Test
    fun cacheBeforeSourceProgressIsDiscarded() = resetCache(55)

    @Test
    fun cacheBeforeGapConfirmationIsDiscarded() = resetCache(56)

    @Test
    fun cacheWithOldFalseGapEvidenceIsDiscarded() = resetCache(57)

    private fun resetCache(version: Int) =
        runTest {
            val path = Files.createTempFile("flare-cache-v$version", ".db")

            fun open() =
                Room
                    .databaseBuilder<CacheDatabase>(name = path.toString())
                    .fallbackToDestructiveMigration(dropAllTables = true)
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
                    db.timelineReadingSessionDao().saveCursor(DbTimelineReadingCursor(snapshot.activeKey, snapshot.key, 1, null))
                    db.pagingTimelineDao().insertAll(listOf(row))
                    db.pagingTimelineDao().insertPagingKey(DbPagingKey(snapshot.activeKey, "original"))
                    db.pagingTimelineDao().insertPagingKey(DbPagingKey(segment.key, "refreshed"))
                    db.close()
                }
                // Older development caches kept an exhausted flag; version 52 also lacked cursor checkpoints.
                BundledSQLiteDriver().open(path.toString()).use {
                    if (version <= 54) it.execSQL("DROP INDEX index_DbTimelineReadingSegment_snapshotKey_boundarySortId")
                    if (version <= 53) it.execSQL("ALTER TABLE DbTimelineReadingSegment ADD COLUMN exhausted INTEGER NOT NULL DEFAULT 0")
                    if (version == 52) it.execSQL("DROP TABLE DbTimelineReadingCursor")
                    if (version in 55..56) {
                        it.execSQL("ALTER TABLE DbTimelineReadingSegment DROP COLUMN confirmed")
                        it.execSQL("ALTER TABLE DbTimelineReadingCursor DROP COLUMN paused")
                        it.execSQL("ALTER TABLE DbTimelineReadingCursor DROP COLUMN retryFailed")
                    }
                    if (version == 55) {
                        it.execSQL("ALTER TABLE DbPagingKey DROP COLUMN readingState")
                        it.execSQL("ALTER TABLE DbTimelineReadingSegment DROP COLUMN failed")
                    }
                    it.execSQL("PRAGMA user_version = $version")
                }
                open().also { db ->
                    try {
                        db.useWriterConnection { }
                        assertNull(db.timelineReadingSessionDao().get("home"))
                        assertNull(db.timelineReadingSessionDao().snapshot(snapshot.key))
                        assertTrue(db.pagingTimelineDao().getByPagingKey(snapshot.activeKey).isEmpty())
                        assertTrue(db.timelineReadingSessionDao().segments(snapshot.key).isEmpty())
                        assertNull(db.pagingTimelineDao().getPagingKey(snapshot.activeKey))
                        assertNull(db.pagingTimelineDao().getPagingKey(segment.key))
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
