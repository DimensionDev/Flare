package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationType
import dev.dimension.flare.data.database.cache.model.DbTimelinePosition
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.memoryDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PagingTimelineDaoTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun positionSaveUsesCurrentRankAndCannotReplaceANewerReadingUpdate() =
        runTest {
            val dao = db.pagingTimelineDao()
            val key = DbPagingKey("home:source")
            val row = DbPagingTimeline(key.pagingKey, "post", 10)
            dao.insertPagingKey(key)
            dao.insertAll(listOf(row))
            val position = DbTimelinePosition("tab", key.pagingKey, "${key.pagingKey}_post", 1, -17.0, 20)
            dao.savePositionIfCurrent(position, key.generation) { true }
            assertEquals(position.copy(sortId = row.sortId), dao.getPosition("tab"))

            dao.savePositionIfCurrent(position.copy(updatedAt = 19, offset = 0.0), key.generation) { true }
            assertEquals(position.copy(sortId = row.sortId), dao.getPosition("tab"))
        }

    @Test
    fun positionSaveRejectsInactiveSourcesChangedGenerationsAndMissingRows() =
        runTest {
            val dao = db.pagingTimelineDao()
            val key = DbPagingKey("home:source")
            val row = DbPagingTimeline(key.pagingKey, "post", 10)
            dao.insertPagingKey(key)
            dao.insertAll(listOf(row))
            val position = DbTimelinePosition("tab", key.pagingKey, "${key.pagingKey}_post", 10, -17.0, 20)
            dao.savePositionIfCurrent(position, key.generation) { false }
            assertNull(dao.getPosition("tab"))

            dao.insertPagingKey(key.copy(generation = "replacement"))
            dao.savePositionIfCurrent(position, key.generation) { true }
            assertNull(dao.getPosition("tab"))

            dao.delete(listOf(row))
            dao.savePositionIfCurrent(position, "replacement") { true }
            assertNull(dao.getPosition("tab"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun queuedPositionSaveRechecksTabAfterAcquiringTheWriter() =
        runTest {
            val dao = db.pagingTimelineDao()
            val key = DbPagingKey("home:source")
            dao.insertPagingKey(key)
            dao.insertAll(listOf(DbPagingTimeline(key.pagingKey, "post", 10)))
            val position = DbTimelinePosition("tab", key.pagingKey, "${key.pagingKey}_post", 10, -17.0, 20)
            dao.insertPosition(position)
            var tabExists = true
            var checks = 0
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writer =
                launch {
                    db.connect {
                        entered.complete(Unit)
                        release.await()
                        tabExists = false
                        dao.deleteTabPosition("tab")
                    }
                }
            entered.await()
            val save =
                launch {
                    dao.savePositionIfCurrent(position.copy(updatedAt = 21), key.generation) {
                        checks += 1
                        tabExists
                    }
                }
            try {
                runCurrent()
                assertFalse(save.isCompleted)
                assertEquals(0, checks)
            } finally {
                release.complete(Unit)
            }
            writer.join()
            save.join()
            assertEquals(1, checks)
            assertNull(dao.getPosition("tab"))
        }

    @Test
    fun statusIdsWithInlineParentsIncludeOnlyCurrentTimelineRootsAndParents() =
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

            assertEquals(
                listOf("ancestor", "leaf", "parent"),
                dao.getStatusIdsWithInlineParents("mixed").sorted(),
            )
            assertEquals(
                listOf("leaf", "other-parent"),
                dao.getStatusIdsWithInlineParents("other").sorted(),
            )
        }
}
