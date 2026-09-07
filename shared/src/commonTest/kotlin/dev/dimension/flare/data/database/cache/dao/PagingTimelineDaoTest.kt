package dev.dimension.flare.data.database.cache.dao

import androidx.room3.Room
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
                    statusId = "leaf",
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
                    parentReference.copy(pagingKey = "other", referenceStatusId = "other-parent"),
                    parentReference.copy(statusId = "deleted-root", referenceStatusId = "orphaned-parent"),
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
