package dev.dimension.flare.data.repository

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.memoryDatabaseBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineReadingRepositoryTest : RobolectricTest() {
    @Test
    fun failedBackgroundCheckpointKeepsApplicationScopeAndLaterSavesAlive() =
        checkpointAfterFailure(IllegalStateException("Checkpoint failed"))

    @Test
    fun cancelledBackgroundCheckpointDoesNotCancelApplicationScope() = checkpointAfterFailure(CancellationException("Checkpoint cancelled"))

    private fun checkpointAfterFailure(injected: Exception) =
        runTest {
            var failure: Exception? = null
            val driver = createDatabaseDriver()
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(
                        object : SQLiteDriver by driver {
                            override fun open(fileName: String): SQLiteConnection {
                                val connection = driver.open(fileName)
                                return object : SQLiteConnection by connection {
                                    override fun prepare(sql: String): SQLiteStatement {
                                        val statement = connection.prepare(sql)
                                        if (sql != "SELECT * FROM DbTimelineReadingSession WHERE tabId = ?") return statement
                                        return object : SQLiteStatement by statement {
                                            override fun step(): Boolean {
                                                failure?.let {
                                                    failure = null
                                                    throw it
                                                }
                                                return statement.step()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    ).setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val lifetime = Job()
            val unhandled = mutableListOf<Throwable>()
            val scope =
                CoroutineScope(
                    lifetime + StandardTestDispatcher(testScheduler) +
                        CoroutineExceptionHandler { _, error -> unhandled += error },
                )
            try {
                val repository = TimelineReadingRepository(database, scope)
                repository.enterHome { true }
                val session = assertNotNull(repository.open("home", "source"))
                val snapshot = assertNotNull(repository.acquire(session))
                val row = DbPagingTimeline(snapshot.activeKey, "post", 123)
                database.pagingTimelineDao().insertAll(listOf(row))
                val anchor = "${snapshot.activeKey}_${row.statusId}"
                repository.checkpoint(snapshot, snapshot.activeKey, anchor, -10.0)
                failure = injected
                repository.savePosition(snapshot, snapshot.activeKey, anchor, -20.0)
                var completionFailure: Throwable? = null
                lifetime.children.single().invokeOnCompletion { completionFailure = it }
                advanceUntilIdle()
                assertEquals(injected is CancellationException, completionFailure is CancellationException)
                assertTrue(lifetime.isActive, "One checkpoint failure must not cancel the application's ordinary Job")
                assertTrue(unhandled.isEmpty(), "Checkpoint errors must be isolated at the asynchronous entry point")
                assertEquals(-10.0, database.timelineReadingSessionDao().snapshot(snapshot.key)?.offset)
                repository.savePosition(snapshot, snapshot.activeKey, anchor, -30.25)
                advanceUntilIdle()
                repository.release(snapshot)
                val reopened = assertNotNull(repository.acquire(session))
                assertEquals(anchor, reopened.anchorId)
                assertEquals(-30.25, reopened.offset)
                assertEquals(listOf(row), database.pagingTimelineDao().getByPagingKey(snapshot.activeKey))
                repository.release(reopened)
            } finally {
                lifetime.cancel()
                database.close()
            }
        }
}
