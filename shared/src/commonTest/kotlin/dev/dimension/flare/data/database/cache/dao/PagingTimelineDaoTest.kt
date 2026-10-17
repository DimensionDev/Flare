package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationType
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.memoryDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagingTimelineDaoTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private var lookupPlan = emptyList<String>()

    @BeforeTest
    fun setup() {
        val driver = createDatabaseDriver()
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(
                    object : SQLiteDriver by driver {
                        override fun open(fileName: String): SQLiteConnection {
                            val connection = driver.open(fileName)
                            return object : SQLiteConnection by connection {
                                override fun prepare(sql: String): SQLiteStatement {
                                    if (sql.startsWith("SELECT statusId FROM DbPagingTimeline WHERE pagingKey = ? AND statusId IN")) {
                                        lookupPlan =
                                            connection.prepare("EXPLAIN QUERY PLAN $sql").use { statement ->
                                                buildList { while (statement.step()) add(statement.getText(3)) }
                                            }
                                    }
                                    return connection.prepare(sql)
                                }
                            }
                        }
                    },
                ).setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun existingReadingStatusIdsIncludeOnlyRequestedCurrentRootsAndInlineParents() =
        runTest {
            val dao = db.pagingTimelineDao()
            dao.insertAll(
                listOf(
                    DbPagingTimeline(pagingKey = "mixed", statusId = "leaf", sortId = 0),
                    DbPagingTimeline(pagingKey = "mixed", statusId = "parent", sortId = 1),
                    DbPagingTimeline(pagingKey = "other", statusId = "leaf", sortId = 0),
                ),
            )
            val parentReference =
                DbTimelineItemPresentationReference(
                    pagingKey = "mixed",
                    timelineId = DbPagingTimeline.createId("mixed", "leaf"),
                    referenceStatusId = "parent",
                    presentationType = DbTimelineItemPresentationType.InlineParent,
                )
            dao.insertPresentationReferences(
                listOf(
                    parentReference,
                    parentReference.copy(referenceStatusId = "ancestor"),
                    parentReference.copy(
                        referenceStatusId = "quote",
                        presentationType = DbTimelineItemPresentationType.Quote,
                    ),
                    parentReference.copy(
                        referenceStatusId = "repost",
                        presentationType = DbTimelineItemPresentationType.Repost,
                    ),
                    parentReference.copy(
                        pagingKey = "other",
                        timelineId = DbPagingTimeline.createId("other", "leaf"),
                        referenceStatusId = "other-parent",
                    ),
                    parentReference.copy(timelineId = "deleted-root", referenceStatusId = "orphaned-parent"),
                ),
            )

            val candidates = listOf("leaf", "parent", "ancestor", "quote", "repost", "other-parent", "orphaned-parent", "missing")
            assertEquals(
                listOf("ancestor", "leaf", "parent"),
                dao.existingReadingStatusIds("mixed", candidates).sorted(),
            )
            assertEquals(
                listOf("leaf", "other-parent"),
                dao.existingReadingStatusIds("other", candidates).sorted(),
            )
            assertEquals(listOf("ancestor"), dao.existingReadingStatusIds("mixed", listOf("ancestor", "missing")))
            assertTrue(
                lookupPlan.any { it.contains("referenceStatusId=? AND pagingKey=?") },
                "Inline-parent lookup must seek candidate IDs rather than scan the retained namespace: $lookupPlan",
            )
        }
}
