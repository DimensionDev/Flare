package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.TimelineReader
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class TimelineHistoryTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private lateinit var store: TimelineHistoryStore

    @BeforeTest
    fun setup() {
        database =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        store = TimelineHistoryStore(database, NoopPreTranslationService)
    }

    @AfterTest
    fun close() = database.close()

    @Test
    fun refreshKeepsHistoryAndOnlyClickingAGapAdvancesItsOwnCursor() =
        runTest {
            var response = page(3, 2, 1, next = "old")
            val requests = mutableListOf<PagingRequest>()
            val loader =
                source(join = true) {
                    requests += it
                    response
                }
            val history = store.open(loader, false)
            history.refresh()
            val original = rows(history)
            response = page(8, 7, next = "new")
            history.refresh()
            assertEquals(listOf("8", "7", "gap", "3", "2", "1"), order(history))
            repeat(3) { rows(history) }
            assertEquals(2, requests.size)

            val gap = rows(history).first { it.timelineId == null }.batchId
            response = page(6, 5, next = "middle")
            history.loadGap(gap)
            assertEquals(PagingRequest.Append("new"), requests.last())
            assertEquals(listOf("8", "7", "6", "5", "gap", "3", "2", "1"), order(history))
            assertEquals(original, rows(history).takeLast(3))

            response = page(4, 3, next = "older")
            history.loadGap(gap)
            assertEquals(listOf("8", "7", "6", "5", "4", "3", "2", "1"), order(history))
            response = page(0)
            history.append()
            assertEquals(PagingRequest.Append("old"), requests.last())
            assertEquals("0", order(history).last())
        }

    @Test
    fun rankedOverlapNeverReordersExistingPostsOrEndsAnIndependentContinuation() =
        runTest {
            var response = page(3, 1, 2, next = "original")
            val history = store.open(source { response }, false)
            history.refresh()
            response = page(4, 2, 5, next = "ranked")
            history.refresh()
            assertEquals(listOf("4", "5", "gap", "3", "1", "2"), order(history))
            val gap = rows(history).first { it.timelineId == null }.batchId
            response = page(2, 6, 1, next = "ranked-next")
            history.loadGap(gap)
            assertEquals(listOf("4", "5", "6", "gap", "3", "1", "2"), order(history))
            response = page(7)
            history.loadGap(gap)
            assertEquals(listOf("4", "5", "6", "7", "3", "1", "2"), order(history))
        }

    @Test
    fun severalRefreshesKeepIndependentGapsAndWindowsShareTheirData() =
        runTest {
            var response = page(1, next = "tail")
            val requests = mutableListOf<PagingRequest>()
            val loader =
                source {
                    requests += it
                    response
                }
            val first = store.open(loader, false)
            first.refresh()
            response = page(3, next = "gap-one")
            first.refresh()
            response = page(5, next = "gap-two")
            first.refresh()
            val second = store.open(loader, false)
            assertSame(first, second)
            val gaps = rows(second).filter { it.timelineId == null }
            response = page(2)
            second.loadGap(gaps.last().batchId)
            assertEquals(PagingRequest.Append("gap-one"), requests.last())
            assertEquals(listOf("5", "gap", "3", "2", "1"), order(first))
            response = page(4)
            first.loadGap(gaps.first().batchId)
            assertEquals(PagingRequest.Append("gap-two"), requests.last())
            assertEquals(listOf("5", "4", "3", "2", "1"), order(second))
        }

    @Test
    fun failureAndStalledPagesKeepTheGapRetryableWithoutLosingRows() =
        runTest {
            var response = page(1)
            var fail = false
            val history =
                store.open(
                    source {
                        if (fail) error("offline")
                        response
                    },
                    false,
                )
            history.refresh()
            response = page(3, next = "gap")
            history.refresh()
            val before = rows(history)
            val gap = before.first { it.timelineId == null }.batchId
            fail = true
            assertFalse(history.loadGap(gap))
            assertEquals(before, rows(history))
            assertNotNull(history.activity.value.failures[gap])
            fail = false
            response = page(3, next = "gap")
            assertFalse(history.loadGap(gap))
            assertEquals(before, rows(history))
            response = page(2)
            assertTrue(history.loadGap(gap))
            assertEquals(listOf("3", "2", "1"), order(history))
        }

    @Test
    fun clearingWhileARequestIsRunningRejectsItsLateResponse() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val history =
                store.open(
                    source {
                        entered.complete(Unit)
                        release.await()
                        page(1)
                    },
                    false,
                )
            val request = async { history.refresh() }
            entered.await()
            store.clear()
            release.complete(Unit)
            request.await()
            assertTrue(rows(history).isEmpty())
            assertEquals(null, database.timelineHistoryDao().history(history.key))
        }

    @Test
    fun windowsAwaitTheSameRefreshAndAFailedInitializationCanBeRetried() =
        runTest {
            var fail = true
            var requests = 0
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val history =
                store.open(
                    source {
                        requests++
                        if (fail) error("offline")
                        entered.complete(Unit)
                        release.await()
                        page(1)
                    },
                    false,
                )
            assertFalse(history.initialize(refreshOnLaunch = true))
            fail = false
            val first = async { history.initialize(refreshOnLaunch = true) }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { history.refresh() }
            release.complete(Unit)
            assertTrue(first.await())
            assertTrue(second.await())
            assertEquals(2, requests)
            assertEquals(listOf("1"), order(history))
        }

    @Test
    fun reopeningLoadsABoundedWindowAroundTheBookmarkWithoutNetworkRequests() =
        runTest {
            var requests = 0
            val loader =
                source {
                    requests++
                    PagingResult((1..150).map(::historyTestPost), nextKey = "tail")
                }
            val history = store.open(loader, false)
            history.refresh()
            val dao = database.timelineHistoryDao()
            val anchor = dao.page(history.key, 120, 1).single()
            dao.save(DbTimelineBookmark("tab", history.key, anchor.itemKey, anchor.ordinal, anchor.position, -42.5))
            val reopenedStore = TimelineHistoryStore(database, NoopPreTranslationService)
            val reopened = reopenedStore.open(loader, false)
            val reader = TimelineReader("tab", reopened, database, reopenedStore, backgroundScope)
            reader.restore()
            assertEquals(-42.5, reader.target.value?.offset)
            assertTrue(reopened.initialize(refreshOnLaunch = false))
            val paging = TimelineHistoryPagingSource(database, reader) { error("A local page cannot click a gap") }
            try {
                val page =
                    assertIs<PagingSource.LoadResult.Page<TimelineHistoryPagingKey, TimelinePageItem>>(
                        paging.load(PagingSource.LoadParams.Refresh(null, 15, false)),
                    )
                assertEquals(15, page.data.size)
                assertTrue(page.data.any { it.baseItem.stableItemKey == anchor.itemKey })
                assertNotNull(page.prevKey)
                assertNotNull(page.nextKey)
                assertEquals(1, requests)
            } finally {
                paging.invalidate()
            }
        }

    @Test
    fun aDeletedBookmarkFallsForwardAndAnUnrelatedSourceDoesNotInheritIt() =
        runTest {
            val loader = source { page(4, 3, 2, 1) }
            val history = store.open(loader, false)
            history.refresh()
            val dao = database.timelineHistoryDao()
            val rows = dao.page(history.key, 0, 10)
            val anchor = rows[1]
            dao.save(DbTimelineBookmark("tab", history.key, anchor.itemKey, anchor.ordinal, anchor.position, -30.0))
            val root = database.pagingTimelineDao().getTimelineRootRowsByIds(history.key, listOf(checkNotNull(anchor.timelineId))).single()
            database.pagingTimelineDao().delete(listOf(root.timeline))
            val reader = TimelineReader("tab", history, database, store, backgroundScope)
            reader.restore()
            assertEquals(rows[2].itemKey, reader.target.value?.itemKey)
            assertEquals(0.0, reader.target.value?.offset)
            val other =
                store.open(
                    object : CacheableRemoteLoader<UiTimelineV2> by loader {
                        override val pagingKey = "other"
                    },
                    false,
                )
            val otherReader = TimelineReader("tab", other, database, store, backgroundScope)
            otherReader.restore()
            assertEquals(null, otherReader.target.value)
        }

    private suspend fun rows(history: TimelineHistory) = database.timelineHistoryDao().page(history.key, 0, 100)

    private suspend fun order(history: TimelineHistory): List<String> =
        rows(history).map { row ->
            if (row.timelineId == null) {
                "gap"
            } else {
                database
                    .pagingTimelineDao()
                    .getTimelineRootRowsByIds(history.key, listOf(row.timelineId))
                    .single()
                    .status.statusKey.id
            }
        }

    private fun source(
        join: Boolean = false,
        block: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) = object : CacheableRemoteLoader<UiTimelineV2> {
        override val pagingKey = "test"
        override val canJoinHistory = join

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ) = block(request)
    }

    private fun page(
        vararg ids: Int,
        next: String? = null,
    ): PagingResult<UiTimelineV2> = PagingResult(ids.map(::historyTestPost), nextKey = next)
}

internal fun historyTestPost(id: Int): UiTimelineV2.Post =
    UiTimelineV2.Post(
        platformId = "xQt",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText("Post $id".toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey(id.toString(), "example.com"),
        card = null,
        createdAt = Instant.fromEpochMilliseconds(id.toLong()).toUi(),
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )
