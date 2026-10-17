package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingCursor
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSegment
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.memoryDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineReadingQueryTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private val plans = mutableMapOf<String, List<String>>()

    @BeforeTest
    fun setup() {
        val driver = createDatabaseDriver()
        database =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(
                    object : SQLiteDriver by driver {
                        override fun open(fileName: String): SQLiteConnection {
                            val connection = driver.open(fileName)
                            return object : SQLiteConnection by connection {
                                override fun prepare(sql: String): SQLiteStatement {
                                    val statement = connection.prepare(sql)
                                    val tracked =
                                        when {
                                            sql.startsWith("SELECT DISTINCT pagingKey") -> "prefix"
                                            sql.startsWith("DELETE FROM DbTimelineReadingCursor") -> "delete"
                                            sql.startsWith("SELECT EXISTS(SELECT 1 FROM DbTimelineReadingSegment") -> "segments"
                                            else -> return statement
                                        }
                                    val explain = connection.prepare("EXPLAIN QUERY PLAN $sql")
                                    return object : SQLiteStatement by statement {
                                        private var firstStep = true

                                        override fun bindText(
                                            index: Int,
                                            value: String,
                                        ) {
                                            statement.bindText(index, value)
                                            explain.bindText(index, value)
                                        }

                                        override fun reset() {
                                            statement.reset()
                                            explain.reset()
                                            firstStep = true
                                        }

                                        override fun step(): Boolean {
                                            if (firstStep) {
                                                plans[tracked] = buildList { while (explain.step()) add(explain.getText(3)) }
                                                firstStep = false
                                            }
                                            return statement.step()
                                        }

                                        override fun close() {
                                            explain.close()
                                            statement.close()
                                        }
                                    }
                                }
                            }
                        }
                    },
                ).setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun close() = database.close()

    @Test
    fun namespaceLookupSeeksBothTablesWithGrowingUnrelatedHistory() =
        runTest {
            val dao = database.pagingTimelineDao()
            val prefix = "reading/target/"
            val expected = listOf(prefix, "${prefix}nested/staging", "${prefix}row-only")
            dao.insertPagingKey(DbPagingKey(prefix, "next"))
            dao.insertPagingKey(DbPagingKey("${prefix}nested/staging", "nested"))
            dao.insertAll(listOf(DbPagingTimeline(expected[1], "a", 1), DbPagingTimeline(expected[2], "b", 2)))
            for (size in listOf(240, 24_000)) {
                database.connect {
                    dao.insertAll((0 until size).map { DbPagingTimeline("reading/other/active/", "$it", it.toLong()) })
                    dao.insertPagingKey(DbPagingKey("reading/other/active/", "other"))
                }
                assertEquals(expected, dao.keysWithPrefix(prefix).sorted())
                val plan = plans.getValue("prefix")
                for (table in listOf("DbPagingTimeline", "DbPagingKey")) {
                    assertTrue(plan.any { "SEARCH $table" in it && "pagingKey>? AND pagingKey<?" in it }, "$size rows: $plan")
                }
            }
        }

    @Test
    fun prefixMatchingAndCursorDeletionAreLiteralAndCaseSensitive() =
        runTest {
            val dao = database.pagingTimelineDao()
            val reading = database.timelineReadingSessionDao()
            val prefixes = listOf("reading/Case/", "reading/case/", "reading/[a]*?_%/", "reading/中文😀/", "reading/\uDBFF\uDFFF/")
            val keys = prefixes.flatMap { listOf(it, "${it}child/") } + "reading/a-wildcard/"
            database.connect {
                keys.forEachIndexed { index, key ->
                    dao.insertPagingKey(DbPagingKey(key, "next"))
                    dao.insertAll(listOf(DbPagingTimeline(key, "$index", index.toLong())))
                    reading.saveCursor(DbTimelineReadingCursor(key, "snapshot/", index.toLong(), null))
                }
            }
            assertEquals(keys.sorted(), dao.keysWithPrefix("").sorted())
            for (prefix in prefixes) {
                assertEquals(keys.filter { it.startsWith(prefix) }.sorted(), dao.keysWithPrefix(prefix).sorted())
                reading.deleteCursors(prefix)
                assertFalse(reading.cursors("snapshot/").any { it.key.startsWith(prefix) })
            }
            assertEquals(listOf("reading/a-wildcard/"), reading.cursors("snapshot/").map { it.key })
            reading.deleteCursors("")
            assertTrue(reading.cursors("snapshot/").isEmpty())
        }

    @Test
    fun cursorDeletionSeeksOnlyTheSelectedNamespace() =
        runTest {
            val dao = database.timelineReadingSessionDao()
            database.connect {
                repeat(240) { dao.saveCursor(DbTimelineReadingCursor("reading/other/$it/", "other/", it.toLong(), null)) }
                dao.saveCursor(DbTimelineReadingCursor("reading/target/cursor", "target/", 1, null))
            }
            dao.deleteCursors("reading/target/")
            assertEquals(240, dao.cursors("other/").size)
            assertTrue(dao.cursors("target/").isEmpty())
            val plan = plans.getValue("delete")
            assertTrue(plan.any { "SEARCH DbTimelineReadingCursor" in it && "key>? AND key<?" in it }, "$plan")
        }

    @Test
    fun emptySnapshotBoundarySeeksPastOtherReadersSegments() =
        runTest {
            val dao = database.timelineReadingSessionDao()
            for (size in listOf(240, 24_000)) {
                database.connect {
                    repeat(size) { dao.saveSegment(DbTimelineReadingSegment("other/$it/", "other/", "$it", it.toLong())) }
                }
                assertFalse(dao.hasSegments("target/"))
                val plan = plans.getValue("segments")
                assertTrue(plan.any { "SEARCH DbTimelineReadingSegment" in it && "snapshotKey=?" in it }, "$size segments: $plan")
            }
            dao.saveSegment(DbTimelineReadingSegment("target/gap/", "target/", "anchor", 1))
            assertTrue(dao.hasSegments("target/"))
            assertEquals(listOf("target/gap/"), dao.segments("target/").map { it.key })
        }
}
