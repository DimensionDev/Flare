package dev.dimension.flare.data.database.cache

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheDatabaseDestructiveMigrationTest {
    @Test
    fun version44CacheIsRebuiltDestructively() =
        runTest {
            assertCacheIsRebuilt(fromVersion = 44)
        }

    @Test
    fun version45CacheIsRebuiltDestructively() =
        runTest {
            assertCacheIsRebuilt(fromVersion = 45)
        }

    private suspend fun assertCacheIsRebuilt(fromVersion: Int) {
        val databasePath = Files.createTempFile("flare-cache-v$fromVersion", ".db")
        val path = databasePath.toAbsolutePath().toString()
        try {
            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL("CREATE TABLE old_cache_fixture(id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
                connection.execSQL("INSERT INTO old_cache_fixture(value) VALUES ('discard me')")
                connection.execSQL("PRAGMA user_version = $fromVersion")
            }

            val database =
                Room
                    .databaseBuilder<CacheDatabase>(name = path)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            database.useWriterConnection { }
            database.close()

            BundledSQLiteDriver().open(path).use { connection ->
                connection.prepare("PRAGMA user_version").use { statement ->
                    assertEquals(true, statement.step())
                    assertEquals(CACHE_DATABASE_VERSION.toLong(), statement.getLong(0))
                }
                connection
                    .prepare(
                        "SELECT COUNT(*) FROM sqlite_master " +
                            "WHERE type = 'table' AND name = 'old_cache_fixture'",
                    ).use { statement ->
                        assertEquals(true, statement.step())
                        assertEquals(0L, statement.getLong(0))
                    }
            }
        } finally {
            Files.deleteIfExists(databasePath)
        }
    }
}
