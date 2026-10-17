package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.repository.QueuedPositionSaveDispatcher
import dev.dimension.flare.data.repository.TimelineReadingRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class TimelineReadingSessionTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private var afterPagingDelete: (String) -> Unit = {}
    private var afterPagingRowsDelete: (String) -> Unit = {}
    private var afterCommit: () -> Unit = {}
    private var onPagingRowsRead: (String) -> Unit = {}
    private var onSelect: () -> Unit = {}

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
                                    val prepared = connection.prepare(sql)
                                    val statement =
                                        if (sql.startsWith("SELECT", ignoreCase = true)) {
                                            object : SQLiteStatement by prepared {
                                                private var firstStep = true

                                                override fun reset() {
                                                    prepared.reset()
                                                    firstStep = true
                                                }

                                                override fun step(): Boolean {
                                                    if (firstStep) onSelect()
                                                    firstStep = false
                                                    return prepared.step()
                                                }
                                            }
                                        } else {
                                            prepared
                                        }
                                    if (sql == "SELECT * FROM DbPagingTimeline WHERE pagingKey = ? ORDER BY sortId" ||
                                        sql.startsWith("SELECT statusId FROM DbPagingTimeline WHERE pagingKey = ? UNION ")
                                    ) {
                                        return object : SQLiteStatement by statement {
                                            override fun bindText(
                                                index: Int,
                                                value: String,
                                            ) {
                                                if (index == 1) onPagingRowsRead(value)
                                                statement.bindText(index, value)
                                            }
                                        }
                                    }
                                    if (sql == "END TRANSACTION") {
                                        return object : SQLiteStatement by statement {
                                            override fun step(): Boolean = statement.step().also { afterCommit() }
                                        }
                                    }
                                    val partialDelete = sql.startsWith("DELETE FROM DbPagingTimeline WHERE pagingKey = ? AND _id IN")
                                    if (!partialDelete && sql != "DELETE FROM DbPagingTimeline WHERE pagingKey = ?") return statement
                                    return object : SQLiteStatement by statement {
                                        private var pagingKey = ""

                                        override fun bindText(
                                            index: Int,
                                            value: String,
                                        ) {
                                            if (index == 1) pagingKey = value
                                            statement.bindText(index, value)
                                        }

                                        override fun step(): Boolean =
                                            statement.step().also {
                                                if (partialDelete) afterPagingRowsDelete(pagingKey) else afterPagingDelete(pagingKey)
                                            }
                                    }
                                }
                            }
                        }
                    },
                ).setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @Test
    fun timeMixedRefreshFailureAloneDoesNotCreateAContentGap() =
        runTest {
            val healthy = Loader("healthy").apply { response = timedPage(10..11, "old-healthy") }
            val failing = Loader("failing").apply { response = timedPage(0..1, "old-failing") }
            val session = session(MixedRemoteMediator(database, listOf(healthy, failing), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            healthy.response = timedPage(10..12, "new-healthy")
            failing.beforeLoad = { error("offline") }
            session.refresh(false)
            assertTrue("12" in ids(session))
            assertTrue("gap" !in readingPage(session), "An unavailable source head is not evidence of missing content")
        }

    @Test
    fun timeMixedPartialFailureKeepsHealthyAppendRunningWithoutRetryingTheFailedSource() =
        runTest {
            val healthy = Loader("healthy").apply { response = timedPage(10..11) }
            val failing = Loader("failing").apply { response = timedPage(20..21) }
            val session = session(MixedRemoteMediator(database, listOf(healthy, failing), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            healthy.response = timedPage(110..111, "new-healthy")
            failing.response = timedPage(120..121, "new-failing")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            healthy.response = timedPage(100..101, "middle-healthy")
            failing.beforeLoad = { error("offline") }
            assertNull(session.loadGap(gap), "A healthy source made progress; this is not a failed load")
            assertTrue(ids(session).containsAll(listOf("100", "101")))
            assertFalse(
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .failed,
            )
            val attempts = failing.requests.size
            val beforeEmptyPage = ids(session)
            healthy.response = PagingResult(nextKey = "empty-page-advanced")
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(beforeEmptyPage, ids(session), "Advancing an empty remote page is still progress")
            assertEquals(attempts, failing.requests.size)
            healthy.response = timedPage(90..91, "next-healthy")
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue(ids(session).containsAll(listOf("90", "91")))
            assertEquals(attempts, failing.requests.size, "Automatic continuation must leave the failed source paused")
            healthy.response = timedPage(10..11)
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue(
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .failed,
            )
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(attempts, failing.requests.size)
            failing.beforeLoad = {}
            failing.response = timedPage(20..21)
            assertNull(session.loadGap(gap))
            assertTrue("gap" !in readingPage(session))
            assertEquals(listOf("new-failing", "new-failing"), appendCursors(failing))
        }

    @Test
    fun timeMixedUnknownHeadRecoversOnRefreshWithoutAGapWhenItOverlaps() = unknownHeadRecovery(overlap = true)

    @Test
    fun timeMixedUnknownHeadRecoversAtItsSavedBoundaryAfterReopening() = unknownHeadRecovery(overlap = false)

    @Test
    fun timeMixedAllSourcesFailWithoutDiscardingTheGapOrRetryCursors() =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1) }
            val second = Loader("second").apply { response = timedPage(10..11) }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            first.response = timedPage(100..101, "first-gap")
            second.response = timedPage(110..111, "second-gap")
            current.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .key
            // Drain the buffered first-source head before testing a completely blocked load.
            first.response = timedPage(90..91, "first-middle")
            second.response = timedPage(80..81, "second-middle")
            assertNull(current.loadGap(gap))
            first.beforeLoad = { error("first offline") }
            second.beforeLoad = { error("second offline") }
            // A remaining staged row is also progress, so allow it to commit before the error.
            assertNull(current.loadGap(gap))
            assertNotNull(current.loadGap(gap))
            assertTrue("gap" in readingPage(current))
            assertTrue(
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .failed,
            )
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            val attempts = listOf(first.requests.size, second.requests.size)
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(attempts, listOf(first.requests.size, second.requests.size))
            first.beforeLoad = {}
            second.beforeLoad = {}
            first.response = timedPage(0..1)
            second.response = timedPage(10..11)
            val restoredGap =
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .key
            assertNull(current.loadGap(restoredGap))
            assertEquals("first-middle", appendCursors(first).last())
            assertEquals("second-middle", appendCursors(second).last())
            assertTrue("gap" !in readingPage(current))
        }

    @Test
    fun timeMixedRepeatedFailedRefreshKeepsOnlyTheOriginalUnknownBoundary() =
        runTest {
            val healthy = Loader("healthy").apply { response = timedPage(10..11) }
            val failing = Loader("failing").apply { response = timedPage(20..21) }
            val current = session(MixedRemoteMediator(database, listOf(healthy, failing), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            failing.beforeLoad = { error("offline") }
            repeat(4) { index ->
                healthy.response = timedPage(10..(30 + index), "healthy-$index")
                val attempts = failing.requests.size
                current.refresh(false)
                assertEquals(attempts + 1, failing.requests.size)
                assertTrue("gap" !in readingPage(current))
                assertEquals(1, database.timelineReadingSessionDao().segments(current.record.key).size)
            }
            failing.beforeLoad = {}
            failing.response = timedPage(100..101, "recovered")
            current.refresh(false)
            val page = readingPage(current)
            assertEquals(1, page.count { it == "gap" })
            assertEquals("21", page[page.indexOf("gap") + 1])
        }

    private fun unknownHeadRecovery(overlap: Boolean) =
        runTest {
            val healthy = Loader("healthy").apply { response = timedPage(10..11) }
            val failing = Loader("failing").apply { response = timedPage(20..21) }
            val mixed = MixedRemoteMediator(database, listOf(healthy, failing), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            healthy.response = timedPage(10..12, "healthy-next")
            failing.beforeLoad = { error("offline") }
            current.refresh(false)
            assertTrue("gap" !in readingPage(current))
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            assertTrue("gap" !in readingPage(current))
            val attempts = failing.requests.size
            healthy.response = timedPage(10..13, "healthy-latest")
            failing.beforeLoad = {}
            failing.response = if (overlap) timedPage(20..22, "recovered") else timedPage(110..111, "recovered")
            current.refresh(false)
            assertEquals(attempts + 1, failing.requests.size, "Recovery and head refresh must share a single source refresh")
            val page = readingPage(current)
            if (overlap) {
                assertTrue("gap" !in page)
                assertTrue("22" in page)
                assertTrue(database.timelineReadingSessionDao().segments(current.record.key).isEmpty())
            } else {
                assertEquals(1, page.count { it == "gap" })
                assertEquals("21", page[page.indexOf("gap") + 1], "Recovery must retain the original new/old boundary")
                failing.response = timedPage(20..21)
                val gap =
                    database
                        .timelineReadingSessionDao()
                        .segments(current.record.key)
                        .single()
                        .key
                assertNull(current.loadGap(gap))
                assertTrue("gap" !in readingPage(current))
            }
            assertTrue("13" in page)
        }

    @Test
    fun timeMixedRefreshDoesNotFetchHistoricalFailedGapPages() =
        runTest {
            val loader =
                Loader().apply {
                    response = timedPage(0..1)
                    beforeLoad = { if (requests.last() is PagingRequest.Append) error("offline") }
                }
            val mixed = MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            repeat(4) { index ->
                loader.response = timedPage(100 * (index + 1)..100 * (index + 1) + 1, "gap-$index")
                val requests = loader.requests.size
                current.refresh(false)
                assertEquals(listOf(PagingRequest.Refresh), loader.requests.drop(requests), "Refresh must not page through historical gaps")
                for (gap in database.timelineReadingSessionDao().segments(current.record.key)) assertNotNull(current.loadGap(gap.key))
            }
            loader.beforeLoad = {}
            val requests = loader.requests.size
            current.refresh(false)
            assertEquals(listOf(PagingRequest.Refresh), loader.requests.drop(requests))
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            loader.response = timedPage(0..1)
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue(
                loader.requests.last() is PagingRequest.Append,
                "Refresh must reactivate old failures within the next normal page budget",
            )
            assertEquals(requests + 2, loader.requests.size)
        }

    @Test
    fun timeMixedRefreshReactivatesSourcesInPartiallyFailedCursors() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11) }
            val second = Loader("second").apply { response = timedPage(20..21) }
            val current = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            first.response = timedPage(110..111, "fresh-first")
            second.response = timedPage(120..121, "fresh-second")
            current.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .key
            first.response = timedPage(100..101, "middle-first")
            second.beforeLoad = { error("offline") }
            assertNull(current.loadGap(gap))
            assertFalse(
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .failed,
            )
            first.response = timedPage(110..112, "latest-first")
            second.beforeLoad = {}
            second.response = timedPage(120..122, "latest-second")
            val attempts = second.requests.size
            current.refresh(false)
            assertEquals(listOf(PagingRequest.Refresh), second.requests.drop(attempts))
            first.response = timedPage(90..91, "continued-first")
            second.response = timedPage(80..81, "continued-second")
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(PagingRequest.Append("fresh-second"), second.requests.last())
            assertTrue("81" in ids(current) || "91" in ids(current))
        }

    @Test
    fun timeMixedUnknownHeadEmptyPageRemainsHiddenWhileItsCursorAdvances() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11) }
            val second = Loader("second").apply { response = timedPage(20..21) }
            val current = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            first.response = timedPage(10..25)
            second.beforeLoad = { error("offline") }
            current.refresh(false)
            second.beforeLoad = {}
            second.response = PagingResult(nextKey = "empty-head")
            current.refresh(false)
            assertTrue("gap" !in readingPage(current), "An empty advancing page has not established a disjoint head")
            assertEquals(1, database.timelineReadingSessionDao().segments(current.record.key).size)
            assertFalse(
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .confirmed,
            )
            second.response = timedPage(100..101, "disjoint-head")
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(1, readingPage(current).count { it == "gap" })
            second.response = timedPage(20..21)
            assertNull(
                current.loadGap(
                    database
                        .timelineReadingSessionDao()
                        .segments(current.record.key)
                        .single()
                        .key,
                ),
            )
            assertTrue("gap" !in readingPage(current))
        }

    @Test
    fun timeMixedRefreshDoesNotFetchAnEmptyHeadsFailedContinuation() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11) }
            val second = Loader("second").apply { response = timedPage(20..21) }
            val current = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            first.response = timedPage(10..25)
            second.beforeLoad = { error("offline") }
            current.refresh(false)
            second.beforeLoad = {}
            second.response = PagingResult(nextKey = "empty-head")
            current.refresh(false)
            second.beforeLoad = { error("offline") }
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Error)
            val requests = second.requests.size
            second.beforeLoad = {}
            current.refresh(false)
            assertEquals(listOf(PagingRequest.Refresh), second.requests.drop(requests))
            assertTrue("gap" !in readingPage(current))
            assertEquals(
                1,
                database.timelineReadingSessionDao().segments(current.record.key).size,
                "A retry must not duplicate the same unknown interval",
            )
            second.response = timedPage(100..101, "disjoint-head")
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(PagingRequest.Append("empty-head"), second.requests.last())
            val page = readingPage(current)
            assertEquals(1, page.count { it == "gap" })
            assertEquals("21", page[page.indexOf("gap") + 1])
        }

    @Test
    fun timeMixedRecoveredHeadShowsNewContentWithoutMovingTheReadingPosition() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11) }
            val second = Loader("second").apply { response = timedPage(20..21) }
            val current = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            val anchor = rows(current).last()
            savePosition(current, "${current.activeKey}_${anchor.statusId}", anchor.sortId, -17.0)
            second.beforeLoad = { error("offline") }
            current.refresh(false)
            assertFalse(current.state.value.hasNewContent)
            val before = snapshot(current)
            second.beforeLoad = {}
            second.response = timedPage(20..22)
            current.refresh(false)
            assertTrue("22" in readingPage(current))
            assertTrue(current.state.value.hasNewContent, "The recovered source alone must trigger View latest")
            assertEquals(before.anchorId, snapshot(current).anchorId)
            assertEquals(before.offset, snapshot(current).offset)
        }

    @Test
    fun timeMixedOriginalCursorCanRetryAfterEverySourceFails() =
        runTest {
            val loader =
                Loader().apply {
                    response = timedPage(0..9, "older")
                    beforeLoad = { if (requests.last() is PagingRequest.Append) error("offline") }
                }
            val current = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            val presenter =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) {
                        if (size > 0) get(size - 1)
                    }
                }
            backgroundScope.launch { current.pagingData.collectLatest(presenter::collectFrom) }
            assertTrue(
                presenter.loadStateFlow
                    .filterNotNull()
                    .first { it.hasError }
                    .append is androidx.paging.LoadState.Error,
            )
            loader.beforeLoad = {}
            loader.response = timedPage(-10..-1)
            current.prepareRetry()
            presenter.retry()
            presenter.loadStateFlow.filterNotNull().first { it.isIdle && it.append.endOfPaginationReached }
            assertEquals(listOf("older", "older"), appendCursors(loader))
            if (presenter.size != 20) presenter.onPagesUpdatedFlow.first { presenter.size == 20 }
            assertEquals(20, presenter.size)
        }

    @AfterTest
    fun close() = database.close()

    @Test
    fun timeMixedGapSeparatesNewAndOldPostsBeforeTheOldTailIsLoaded() =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1, "old-first") }
            val second = Loader("second").apply { response = timedPage(2..3, "old-second") }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            first.response = timedPage(100..101, "new-first")
            second.response = timedPage(102..103, "new-second")
            session.refresh(false)
            assertEquals(listOf("103", "102", "gap", "3"), readingPage(session, 3))
            assertEquals(listOf("103", "102", "gap", "3", "2"), readingPage(session))
        }

    @Test
    fun timeMixedGapWaitsForEverySourceAndThenContinuesOriginalHistory() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11, "old-first") }
            val second = Loader("second").apply { response = timedPage(20..21, "old-second") }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time)
            val session = session(mixed, backgroundScope)
            session.refresh(false, initial = true)
            first.response = timedPage(110..111, "new-first")
            second.response = timedPage(120..121, "new-second")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            first.response = timedPage(10..11, "joined-first")
            second.response = timedPage(100..101, "middle-second")
            assertNull(session.loadGap(gap))
            assertTrue("gap" in readingPage(session), "One source reaching its old head must not close another source's gap")
            val firstRequests = first.requests.size
            second.response = timedPage(20..21, "joined-second")
            repeat(4) { assertNull(session.loadGap(gap)) }
            assertFalse("gap" in readingPage(session), "Every source joined; the gap must close without exhausting the entire feed")
            assertEquals(firstRequests, first.requests.size, "A joined source must not be fetched again")
            first.response = timedPage(0..1)
            second.response = timedPage(2..3)
            repeat(4) { assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success) }
            assertTrue(readingPage(session).containsAll(listOf("0", "1", "2", "3")), "Closing a gap must preserve older-history cursors")
            assertTrue("old-first" in appendCursors(first))
            assertTrue("old-second" in appendCursors(second))
        }

    @Test
    fun timeMixedGapRetainsFailedSourceAndJoinedProgressAfterReopening() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11, "old-first") }
            val second = Loader("second").apply { response = timedPage(20..21, "old-second") }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var session = session(mixed, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            first.response = timedPage(110..111, "new-first")
            second.response = timedPage(120..121, "new-second")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            first.response = timedPage(10..11, "joined-first")
            second.beforeLoad = { error("offline") }
            assertNull(session.loadGap(gap), "Joining a healthy source is progress even while another source needs retry")
            session.close()
            session = session(mixed, backgroundScope, repository = repository)
            session.prepare()
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 100, false),
                    ) as PagingSource.LoadResult.Page
                val marker = page.data.single { it.baseItem.isReadingGap }.baseItem as UiTimelineV2.Message
                assertTrue(assertNotNull(marker.readingGap).failed, "Retry state must survive reopening")
            } finally {
                source.invalidate()
            }
            val firstRequests = first.requests.size
            second.beforeLoad = {}
            second.response = timedPage(20..21, "joined-second")
            repeat(4) { assertNull(session.loadGap(gap)) }
            assertFalse("gap" in readingPage(session))
            assertEquals(firstRequests, first.requests.size, "The previously joined source must stay joined after reopening")
            assertEquals(listOf("new-second", "new-second"), appendCursors(second))
        }

    @Test
    fun timeMixedGapProjectionDoesNotScanDistantGaps() =
        runTest {
            val loader = Loader().apply { response = timedPage(0..39, "original") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            val counts = mutableListOf<Int>()
            repeat(24) { index ->
                loader.response = timedPage((index + 1) * 100..(index + 1) * 100 + 39, "refresh-$index")
                session.refresh(false)
                if (index == 3 || index == 23) {
                    var queries = 0
                    onSelect = { queries++ }
                    assertEquals(1, readingPage(session, 60).count { it == "gap" })
                    onSelect = {}
                    counts += queries
                }
            }
            println("MIXED_BOUNDARY_AUDIT gaps=4,24 selects=$counts")
            assertTrue(counts.last() <= counts.first() + 4, "A visible page must not resolve every distant gap: $counts")
        }

    @Test
    fun timeMixedRefreshFailureRetriesTheUninitializedSource() =
        runTest {
            val first = Loader("first").apply { response = timedPage(10..11, "old-first") }
            val second = Loader("second").apply { response = timedPage(20..21, "old-second") }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            first.response = timedPage(110..111, "new-first")
            second.beforeLoad = { error("offline") }
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            assertTrue("gap" in readingPage(session))
            second.beforeLoad = {}
            first.response = timedPage(10..11, "joined-first")
            second.response = timedPage(20..21, "joined-second")
            repeat(4) { assertNull(session.loadGap(gap)) }
            assertFalse("gap" in readingPage(session))
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Refresh, PagingRequest.Refresh), second.requests)
        }

    @Test
    fun timeMixedRankedSourceDoesNotCloseGapAtAnOverlappingPost() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = timedPage(0..1, "old") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            loader.response = timedPage(100..101, "fresh")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            loader.response = timedPage(0..1, "reordered")
            assertNull(session.loadGap(gap))
            assertTrue("gap" in readingPage(session))
            loader.response = timedPage(50..51)
            assertNull(session.loadGap(gap))
            assertFalse("gap" in readingPage(session))
            assertTrue(readingPage(session).containsAll(listOf("50", "51")))
        }

    @Test
    fun timeMixedRefreshWithReusedPaginationTokenRetainsItsDistinctBoundary() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = timedPage(0..1, "page-2") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            loader.response = timedPage(100..101, "page-2")
            session.refresh(false)
            assertEquals(listOf("101", "100", "gap", "1", "0"), readingPage(session))
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            loader.response = timedPage(50..51)
            assertNull(session.loadGap(gap))
            assertFalse("gap" in readingPage(session))
            assertTrue(readingPage(session).containsAll(listOf("50", "51")))
        }

    @Test
    fun timeMixedDoesNotJoinOneSourceAtAnotherSourcesPost() =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1, "old-first") }
            val second = Loader("second").apply { response = timedPage(10..11, "old-second") }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            first.response = timedPage(100..101, "new-first")
            second.response = timedPage(110..111, "new-second")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            first.response = timedPage(10..11, "first-still-missing")
            second.response = timedPage(10..11, "second-joined")
            assertNull(session.loadGap(gap))
            assertTrue("gap" in readingPage(session), "The second source's cached posts cannot prove continuity of the first source")
            first.response = timedPage(0..1, "first-joined")
            repeat(3) { assertNull(session.loadGap(gap)) }
            assertFalse("gap" in readingPage(session))
        }

    @Test
    fun timeMixedRepeatedRefreshKeepsEachGapAtItsOwnBoundary() =
        runTest {
            val loader = Loader().apply { response = timedPage(0..1, "old") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            loader.response = timedPage(100..101, "first")
            session.refresh(false)
            loader.response = timedPage(200..201, "second")
            session.refresh(false)
            assertEquals(listOf("201", "200", "gap", "101", "100", "gap", "1", "0"), readingPage(session))
            val gaps = database.timelineReadingSessionDao().segments(session.record.key)
            loader.response = timedPage(100..101, "second-joined")
            assertNull(session.loadGap(gaps.first().key))
            assertEquals(listOf("201", "200", "101", "100", "gap", "1", "0"), readingPage(session))
            loader.response = timedPage(0..1, "first-joined")
            assertNull(session.loadGap(gaps.last().key))
            assertEquals(listOf("201", "200", "101", "100", "1", "0"), readingPage(session))
        }

    @Test
    fun timeMixedGapFollowsAFilteredBoundaryIntoTheNextLocalPage() =
        runTest {
            val loader = Loader().apply { response = timedPage(0..1, "old") }
            val session =
                session(
                    MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time),
                    backgroundScope,
                    visible = { it.statusKey.id != "1" },
                )
            session.refresh(false, initial = true)
            loader.response = timedPage(100..101, "fresh")
            session.refresh(false)
            assertEquals(listOf("101", "100", "1"), readingPage(session, 3))
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Append(OffsetFromStartPagingKey.Append(3), 1, false),
                    ) as PagingSource.LoadResult.Page
                assertEquals(listOf("gap", "0"), page.data.map { if (it.baseItem.isReadingGap) "gap" else it.baseItem.statusKey.id })
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun timeMixedSourceRecoveredDuringGapBecomesBoundaryForTheNextRefresh() =
        runTest {
            val first = Loader("first").apply { beforeLoad = { error("offline") } }
            val second = Loader("second").apply { response = timedPage(0..1, "old-second") }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            second.response = timedPage(100..101, "first-second")
            session.refresh(false)
            val firstGap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            first.beforeLoad = {}
            first.response = timedPage(150..151, "recovered-first")
            second.response = timedPage(0..1, "joined-second")
            assertNull(session.loadGap(firstGap))
            first.response = timedPage(250..251, "fresh-first")
            second.response = timedPage(200..201, "fresh-second")
            session.refresh(false)
            val nextGap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single { it.key != firstGap }
                    .key
            first.response = timedPage(150..151, "joined-first")
            second.response = timedPage(100..101, "joined-second-again")
            assertNull(session.loadGap(nextGap))
            assertEquals(
                listOf(firstGap),
                database.timelineReadingSessionDao().segments(session.record.key).map { it.key },
                "The latest gap must join the recovered source's head, while the earlier unresolved gap remains",
            )
        }

    @Test
    fun timeMixedNestedPerPageFailureRetainsTheFailedCursor() = timeMixedNestedFailure(TimelineMergePolicy.TimePerPage)

    @Test
    fun timeMixedNestedStaggeredFailureRetainsTheFailedCursor() = timeMixedNestedFailure(TimelineMergePolicy.Staggered)

    @Test
    fun timeMixedNestedStaggeredRetrySurvivesBufferedPages() = nestedRetrySurvivesBufferedPages(TimelineMergePolicy.Staggered)

    @Test
    fun timeMixedNestedPerPageRetrySurvivesBufferedPages() = nestedRetrySurvivesBufferedPages(TimelineMergePolicy.TimePerPage)

    private fun nestedRetrySurvivesBufferedPages(policy: TimelineMergePolicy) =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1) }
            val second = Loader("second").apply { response = timedPage(10..11) }
            val third = Loader("third").apply { response = timedPage(20..21) }
            val child = MixedRemoteMediator(database, listOf(first, second, third), policy)
            val mixed = MixedRemoteMediator(database, listOf(child), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            first.response = timedPage(300..301, "gap-first")
            second.response = timedPage(400..401, "gap-second")
            third.response = timedPage(500..501, "gap-third")
            current.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(current.record.key)
                    .single()
                    .key
            first.response = timedPage(100..139)
            second.response = timedPage(200..239)
            third.beforeLoad = { error("offline") }
            assertNull(current.loadGap(gap))
            first.response = timedPage(300..301)
            second.response = timedPage(400..401)
            third.beforeLoad = {}
            third.response = timedPage(500..501)
            current.refresh(false)
            val requests = third.requests.size
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(requests, third.requests.size, "The first page consumes the remaining 40 staged rows")
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            third.response = timedPage(50..51)
            assertTrue(append(current) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(requests + 1, third.requests.size, "Refresh retry eligibility must survive staged pages and reopening")
            assertEquals(listOf("gap-third", "gap-third"), appendCursors(third))
            assertTrue(ids(current).containsAll(((100..139) + (200..239) + (50..51)).map(Int::toString)))
            assertTrue("gap" !in readingPage(current))
        }

    private fun timeMixedNestedFailure(policy: TimelineMergePolicy) =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1, "old-first") }
            val second = Loader("second").apply { response = timedPage(10..11, "old-second") }
            val child = MixedRemoteMediator(database, listOf(first, second), policy)
            val mixed = MixedRemoteMediator(database, listOf(child), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var session = session(mixed, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            first.response = timedPage(100..101, "new-first")
            second.response = timedPage(110..111, "new-second")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            first.beforeLoad = { error("offline") }
            second.response = timedPage(50..51)
            assertNull(session.loadGap(gap), "A healthy nested source must commit without failing the whole load")
            assertTrue(
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .failed,
            )
            assertTrue("gap" in readingPage(session))
            session.close()
            session = session(mixed, backgroundScope, repository = repository)
            session.prepare()
            val secondRequests = second.requests.size
            first.beforeLoad = {}
            first.response = timedPage(40..41)
            repeat(3) { assertNull(session.loadGap(gap)) }
            assertEquals(listOf("new-first", "new-first"), appendCursors(first))
            assertEquals(secondRequests, second.requests.size)
            assertFalse("gap" in readingPage(session))
            assertTrue(readingPage(session).containsAll(listOf("40", "41", "50", "51")))
        }

    @Test
    fun timeMixedMigratedGapRetainsFailedSourcesWithoutReadingMetadata() =
        runTest {
            val first = Loader("first").apply { response = timedPage(0..1, "old-first") }
            val second = Loader("second").apply { response = timedPage(10..11, "old-second") }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var session = session(mixed, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            first.response = timedPage(100..101, "new-first")
            second.response = timedPage(110..111, "new-second")
            session.refresh(false)
            val gap =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            val dao = database.pagingTimelineDao()
            for (key in dao.keysWithPrefix(session.record.key)) {
                dao.getPagingKey(key)?.let { dao.insertPagingKey(it.copy(readingState = null)) }
            }
            session.close()
            session = session(mixed, backgroundScope, repository = repository)
            session.prepare()
            first.beforeLoad = { error("offline") }
            second.response = timedPage(50..51)
            assertNull(session.loadGap(gap), "Migrated continuations must also accept healthy-source progress")
            assertTrue("gap" in readingPage(session))
            first.beforeLoad = {}
            first.response = timedPage(40..41)
            repeat(3) { assertNull(session.loadGap(gap)) }
            assertEquals(listOf("new-first", "new-first"), appendCursors(first))
            assertFalse("gap" in readingPage(session))
            assertTrue(readingPage(session).containsAll(listOf("40", "41", "50", "51")))
        }

    @Test
    fun timeMixedAutomaticallyLoadsAtTheSeamWhileTheOldTailIsFarAway() =
        runTest {
            val loader = Loader().apply { response = timedPage(0..39, "old") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            val oldHead = rows(session).first()
            loader.response = timedPage(100..139, "fresh")
            session.refresh(false)
            loader.response = timedPage(39..99, "joined")
            val generation = session.gapGeneration.value
            session.updateViewport(ReadingViewport("${session.activeKey}_${oldHead.statusId}", -19.5, false, false))
            withContext(Dispatchers.Default) {
                assertNotNull(withTimeoutOrNull(5_000) { session.gapGeneration.first { it >= generation + 2 } })
            }
            assertEquals(listOf("fresh"), appendCursors(loader))
            assertTrue("99" in readingPage(session))
        }

    private suspend fun readingPage(
        session: TimelineReadingSession,
        size: Int = 100,
    ): List<String> {
        val source = TimelineReadingPagingSource(database, session)
        return try {
            val page =
                source.load(
                    PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), size, false),
                ) as PagingSource.LoadResult.Page
            page.data.map { if (it.baseItem.isReadingGap) "gap" else it.baseItem.statusKey.id }
        } finally {
            source.invalidate()
        }
    }

    private fun timedPage(
        range: IntRange,
        cursor: String? = null,
    ) = PagingResult<UiTimelineV2>(
        data = range.reversed().map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(it * 1000L).toUi()) },
        nextKey = cursor,
    )

    @Test
    fun originalContinuousCursorStopsOnRepeatedPostsAndCanRetry() =
        runTest {
            val loader =
                Loader().apply {
                    response = page(0..9, "stalled").copy(previousKey = null)
                    beforeLoad = { check(requests.size <= 8) { "Repeated request for a stalled page" } }
                }
            val session = session(loader, backgroundScope)
            val presenter =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) {
                        if (size > 0) get(size - 1)
                    }
                }
            backgroundScope.launch { session.pagingData.collectLatest(presenter::collectFrom) }
            val failure = presenter.loadStateFlow.filterNotNull().first { it.hasError }
            assertTrue(failure.append is androidx.paging.LoadState.Error)
            assertEquals(listOf("stalled"), appendCursors(loader), "A stalled page must stop after one request")
            assertEquals(10, presenter.size)
            loader.response = page(10..19).copy(nextKey = null, previousKey = null)
            presenter.retry()
            presenter.loadStateFlow.filterNotNull().first { it.isIdle && it.append.endOfPaginationReached }
            assertEquals(listOf("stalled", "stalled"), appendCursors(loader))
            if (presenter.size != 20) presenter.onPagesUpdatedFlow.first { presenter.size == 20 }
            assertEquals(20, presenter.size)
        }

    @Test
    fun originalContinuousCursorCanRepeatWhileAddingPosts() =
        runTest {
            val loader = Loader().apply { response = page(0..9, "same").copy(previousKey = null) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(10..19, "same").copy(previousKey = null)
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals((0..19).map(Int::toString), ids(session))
            assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Error)
            assertEquals(listOf("same", "same"), appendCursors(loader))
        }

    @Test
    fun advancingContinuousAppendHasBoundedDatabaseWork() =
        runTest {
            val loader = Loader().apply { response = page(0..39, "page-0").copy(previousKey = null) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val selects = mutableListOf<Int>()
            var fullScans = 0
            onPagingRowsRead = { if (it == session.activeKey) fullScans++ }
            repeat(25) { page ->
                val first = (page + 1) * 40
                loader.response = page(first..first + 39, "page-${page + 1}").copy(previousKey = null)
                var queries = 0
                onSelect = { queries++ }
                assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Success)
                onSelect = {}
                selects += queries
            }
            onPagingRowsRead = {}
            println("CONTINUOUS_APPEND_AUDIT history=40..1040 rows_per_page=40 selects=$selects full_scans=$fullScans")
            assertEquals(0, fullScans, "Append must not enumerate retained history")
            assertTrue(selects.max() <= 21, "Append must stay within the existing query budget regardless of history: $selects")
            assertEquals(1040, rows(session).size)
        }

    @Test
    fun emptyInitialPageSettlesWithoutRepeatedRefresh() =
        runTest {
            val loader =
                Loader().apply {
                    response = PagingResult()
                    beforeLoad = { check(requests.size <= 2) { "Repeated empty refresh" } }
                }
            val session = session(loader, backgroundScope)
            val presenter =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) = Unit
                }
            backgroundScope.launch { session.pagingData.collectLatest(presenter::collectFrom) }
            presenter.loadStateFlow.filterNotNull().first { it.hasError || (it.isIdle && it.append.endOfPaginationReached) }
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), loader.requests)
            assertEquals(0, presenter.size)
            assertEquals(session.activeKey, snapshot(session).activeKey)
        }

    @Test
    fun gestureDuringInitialPublicationStillDisplaysTheFirstPage() = initialPublicationSurvivesGesture(duringWrite = false)

    @Test
    fun gestureDuringInitialPublicationWritesStillDisplaysTheFirstPage() = initialPublicationSurvivesGesture(duringWrite = true)

    private fun initialPublicationSurvivesGesture(duringWrite: Boolean) =
        runTest {
            val resolvingFirst = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var visibilityCalls = 0
            val loader = Loader().apply { response = page(0..9).copy(nextKey = null) }
            val session =
                session(loader, backgroundScope, visible = {
                    visibilityCalls += 1
                    // The third lookup resolves the first pending row during publication.
                    if (!duringWrite && visibilityCalls == 3) {
                        resolvingFirst.complete(Unit)
                        resume.await()
                    }
                    true
                })
            var interrupted = false
            if (duringWrite) {
                database.pagingTimelineDao().insertPagingKey(DbPagingKey(session.activeKey))
                afterPagingDelete = { key ->
                    if (key == session.activeKey && !interrupted) {
                        interrupted = true
                        session.cancelRestoration()
                    }
                }
            }
            val paging =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) = Unit
                }
            val refresh = if (duringWrite) async { session.refresh(false, initial = true) } else null
            backgroundScope.launch { session.pagingData.collectLatest(paging::collectFrom) }
            if (!duringWrite) {
                resolvingFirst.await()
                session.cancelRestoration()
                resume.complete(Unit)
            }
            refresh?.await()
            withContext(Dispatchers.Default) {
                assertNotNull(
                    withTimeoutOrNull(5_000) {
                        paging.loadStateFlow.filterNotNull().first { it.isIdle && it.append.endOfPaginationReached }
                    },
                )
            }
            if (duringWrite) assertTrue(interrupted)
            assertEquals(10, paging.size)
            assertEquals((0..9).map(Int::toString), ids(session))
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), loader.requests)
            assertEquals(session.activeKey, snapshot(session).activeKey)
            assertFalse(snapshot(session).pendingReady)
            assertFalse(session.state.value.hasNewContent)
            assertNull(session.state.value.position, "Filling an empty list must not request a scroll")
        }

    @Test
    fun cancellationAtAttachmentCommitKeepsReferencesAndRowsConsistent() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val key = session.activeKey
            val pendingKey = snapshot(session).pendingKey
            loader.response = page(100..110).copy(nextKey = null)
            var armed = false
            loader.beforeLoad = { armed = true }
            val publication = launch(start = CoroutineStart.LAZY) { session.refresh(false) }
            afterPagingDelete = { deleted ->
                if (armed && deleted == pendingKey) {
                    afterCommit = {
                        afterCommit = {}
                        publication.cancel()
                    }
                }
            }
            publication.start()
            publication.join()
            afterPagingDelete = {}
            assertTrue(publication.isCancelled)
            assertEquals(key, session.activeKey)
            assertEquals(key, snapshot(session).activeKey)
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            loader.response = page(200..210)
            session.refresh(false)
            assertEquals((200..210).map(Int::toString) + (100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
        }

    @Test
    fun failedRefreshDoesNotRestoreACancelledAnchor() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            session.prepare()
            assertNotNull(session.state.value.position)
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                started.complete(Unit)
                finish.await()
                error("offline")
            }
            val refresh = async { session.refresh(false) }
            started.await()
            session.cancelRestoration()
            assertNull(session.state.value.position)
            finish.complete(Unit)
            refresh.await()
            assertNull(session.state.value.position, "A failed refresh must not resurrect the cancelled scroll request")
        }

    @Test
    fun newSegmentPagesIndependentlyOfTheExhaustedOldTimeline() =
        runTest {
            var showNewPage = false
            val requests = mutableListOf<PagingRequest>()
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "home"
                    override val timelineOrder = TimelineOrder.Continuous

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        requests += request
                        return when (request) {
                            PagingRequest.Refresh -> if (showNewPage) page(100..139, "new-next") else page(0..9).copy(nextKey = null)
                            is PagingRequest.Append -> page(140..179).copy(nextKey = null)
                            is PagingRequest.Prepend -> PagingResult()
                        }
                    }
                }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val originalHead = rows(session).first()
            val snapshots = MutableStateFlow(emptyList<String>())
            val presenter =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) {
                        snapshots.value = snapshot().items.map { it.baseItem.statusKey.id }
                        if (size > 0) get(size - 1)
                    }
                }
            backgroundScope.launch { session.pagingData.collectLatest(presenter::collectFrom) }
            presenter.loadStateFlow.filterNotNull().first { it.isIdle && it.append.endOfPaginationReached && snapshots.value.size == 10 }
            showNewPage = true
            session.refresh(false)
            session.showLatest()
            session.positionRestored(assertNotNull(session.state.value.position).requestId)
            session.updateViewport(ReadingViewport("${session.activeKey}_${originalHead.statusId}", 0.0, false, false))
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(5_000) { snapshots.first { "179" in it } }
            }
            assertEquals(
                true,
                "179" in snapshots.value,
                "View latest must load beyond the first fresh page; requests=$requests",
            )
            assertEquals(90, rows(session).size)
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Refresh, PagingRequest.Append("new-next")), requests)
        }

    @Test
    fun nestedMixedFailureCommitsOnlySuccessfulSources() = nestedMixedFailure(TimelineMergePolicy.Staggered, TimelineMergePolicy.Staggered)

    @Test
    fun timeParentFailureCommitsOnlySuccessfulSources() = nestedMixedFailure(TimelineMergePolicy.Time, TimelineMergePolicy.Staggered)

    @Test
    fun timeChildFailureCommitsOnlySuccessfulSources() = nestedMixedFailure(TimelineMergePolicy.Staggered, TimelineMergePolicy.Time)

    @Test
    fun nestedTimeFailureCommitsOnlySuccessfulSources() = nestedMixedFailure(TimelineMergePolicy.Time, TimelineMergePolicy.Time)

    private fun nestedMixedFailure(
        parentPolicy: TimelineMergePolicy,
        childPolicy: TimelineMergePolicy,
    ) = runTest {
        fun chronologicalPage(
            range: IntRange,
            cursor: String = "old-cursor",
        ) = page(range, cursor).copy(
            data = range.map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(1_000L - it).toUi()) },
        )
        val nestedRequests = mutableListOf<PagingRequest>()
        val nestedLoader =
            object : CacheableRemoteLoader<UiTimelineV2> {
                override val pagingKey = "inner"

                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiTimelineV2> {
                    nestedRequests += request
                    return when (request) {
                        PagingRequest.Refresh -> {
                            chronologicalPage(0..9)
                        }

                        is PagingRequest.Append -> {
                            if (request.nextKey == "old-cursor") {
                                chronologicalPage(100..109, "inner-next")
                            } else {
                                chronologicalPage(200..209).copy(nextKey = null)
                            }
                        }

                        is PagingRequest.Prepend -> {
                            PagingResult()
                        }
                    }
                }
            }
        val sibling = Loader("sibling").apply { response = chronologicalPage(20..29, "sibling-old") }
        val nested = MixedRemoteMediator(database, listOf(nestedLoader), childPolicy)
        val outer = MixedRemoteMediator(database, listOf(nested, sibling), parentPolicy)
        val repository = TimelineReadingRepository(database, backgroundScope)
        val session = session(outer, backgroundScope, repository = repository)
        session.refresh(false, initial = true)
        val before = rows(session)
        val anchor = "${session.activeKey}_${before[5].statusId}"
        session.updateViewport(ReadingViewport(anchor, -37.25, atTop = false, interacting = false))
        repository.checkpoint(session.record, session.activeKey, anchor, -37.25)
        val dao = database.pagingTimelineDao()

        suspend fun mixedData() = dao.keysWithPrefix(session.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) }
        val beforeMixedData = mixedData()
        val suffix = if (childPolicy == TimelineMergePolicy.Time) "staging" else "cursor"
        val childCursorKey = dao.keysWithPrefix(session.activeKey).single { it.endsWith("/source/5:inner/$suffix") }
        val oldCursor = database.pagingTimelineDao().getPagingKey(childCursorKey)
        assertEquals("old-cursor", oldCursor?.nextKey)
        val siblingSuffix = if (parentPolicy == TimelineMergePolicy.Time) "staging" else "cursor"
        val siblingCursorKey = dao.keysWithPrefix(session.activeKey).single { it.endsWith("/source/7:sibling/$siblingSuffix") }
        sibling.response = chronologicalPage(30..39).copy(nextKey = null)
        val failSibling = CompletableDeferred<Unit>()
        sibling.beforeLoad = {
            failSibling.await()
            error("sibling offline")
        }
        val mediator =
            TimelineRemoteMediator(
                outer.forReadingSession(session.activeKey, session.activeKey.takeIf { parentPolicy == TimelineMergePolicy.Time }) { true },
                database,
                allowLongText = false,
            )
        val request = PagingRequest.Append("mixed_next_key")
        val append = async { mediator.load(40, request) }
        advanceUntilIdle()
        assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Append("old-cursor")), nestedRequests)
        failSibling.complete(Unit)
        val partial = append.await()
        assertEquals(before, rows(session))
        assertEquals(beforeMixedData, mixedData(), "Fetching must not commit child cursors or staging rows")
        mediator.saveResponse(request, partial)
        assertTrue((100..109).all { it.toString() in ids(session) }, "Healthy child page must remain available")
        assertEquals("inner-next", dao.getPagingKey(childCursorKey)?.nextKey)
        val retriesSibling = parentPolicy == TimelineMergePolicy.Time
        assertEquals(if (retriesSibling) "sibling-old" else null, assertNotNull(dao.getPagingKey(siblingCursorKey)).nextKey)
        assertEquals(before, rows(session).take(before.size), "Partial append must preserve existing list order")
        sibling.beforeLoad = {}
        mediator.saveResponse(request, mediator.load(40, request))
        assertEquals(2, sibling.requests.size, "Automatic continuation must leave the failed source paused")
        if (retriesSibling) {
            val retry =
                TimelineRemoteMediator(
                    outer.forReadingSession(session.activeKey, session.activeKey, retryFailed = true) { true },
                    database,
                    allowLongText = false,
                )
            retry.saveResponse(request, retry.load(40, request))
        }
        val actualIds = ids(session)
        assertEquals(retriesSibling, (30..39).all { it.toString() in actualIds }, "Time merged reading must retry the recovered sibling")
        assertTrue((100..109).all { it.toString() in actualIds }, "Append must retain the earlier successful page")
        assertTrue((200..209).all { it.toString() in actualIds }, "Healthy nested source must keep paging")
        assertEquals(
            listOf(PagingRequest.Refresh, PagingRequest.Append("old-cursor"), PagingRequest.Append("inner-next")),
            nestedRequests,
        )
        val expectedSiblingRequests =
            listOf(PagingRequest.Refresh, PagingRequest.Append("sibling-old")) +
                if (retriesSibling) listOf(PagingRequest.Append("sibling-old")) else emptyList()
        assertEquals(expectedSiblingRequests, sibling.requests)
        val beforeRefresh = rows(session)
        sibling.response = chronologicalPage(-20..-11).copy(nextKey = null)
        session.refresh(false)
        assertEquals(expectedSiblingRequests + PagingRequest.Refresh, sibling.requests)
        assertEquals(
            beforeRefresh.map { it._id to it.sortId },
            rows(session).filter { row -> beforeRefresh.any { it._id == row._id } }.map { it._id to it.sortId },
            "Recovery must retain every existing row and its order",
        )
        assertEquals(anchor, snapshot(session).anchorId)
        assertEquals(-37.25, snapshot(session).offset)
        assertTrue(session.state.value.hasNewContent)
        assertNull(session.state.value.position, "Recovery must not move the reader")
        assertTrue((-20..-11).all { it.toString() in ids(session) }, "Recovery must be readable before View latest")
        session.showLatest()
    }

    @Test
    fun changingPreferenceWaitsForEntryThenClearsOnlyReadingData() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            var preference = false
            repository.enterHome { preference }
            assertFalse(repository.enabled.value == true)
            assertNull(repository.open("home", "source"))
            repository.selectTab("home")
            assertNull(repository.selectedTab.first())

            preference = true
            assertFalse(repository.enabled.value == true)
            repository.enterHome { preference }
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            repository.selectTab("home")
            repository.copyData(session.activeKey, "ordinary-cache")
            val ordinaryRows = database.pagingTimelineDao().getByPagingKey("ordinary-cache")
            loader.response = page(100..110)
            session.refresh(false)
            val statusCount = database.statusDao().count().first()

            preference = false
            assertTrue(repository.enabled.value == true)
            assertEquals("home", repository.selectedTab.first())
            assertNotNull(database.timelineReadingSessionDao().get("home"))
            repository.enterHome { preference }
            assertFalse(repository.enabled.value == true)
            assertNull(database.timelineReadingSessionDao().get("home"))
            assertNull(database.timelineReadingSessionDao().selectedTab().first())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.sessionKey).isEmpty())
            assertEquals(ordinaryRows, database.pagingTimelineDao().getByPagingKey("ordinary-cache"))
            assertEquals(statusCount, database.statusDao().count().first())
            repository.selectTab("stale-tab")
            assertNull(repository.open("home", "source"))
            assertNull(repository.selectedTab.first())

            preference = true
            repository.enterHome { preference }
            val fresh = assertNotNull(repository.open("home", "source"))
            assertNotEquals(session.record.sessionKey, fresh.key)
            assertNull(fresh.snapshotKey)
            assertNull(repository.selectedTab.first())
        }

    @Test
    fun mixedAppendCommitsChildCursorsAndOuterRowsTogether() = mixedAppendCancellation(TimelineMergePolicy.Staggered)

    @Test
    fun timeMixedAppendCommitsStagingAndOuterRowsTogether() = mixedAppendCancellation(TimelineMergePolicy.Time)

    @Test
    fun partialMixedAppendCommitsChildCursorsAndOuterRowsTogether() = mixedAppendCancellation(TimelineMergePolicy.Staggered, true)

    @Test
    fun partialTimeAppendCommitsStagingAndOuterRowsTogether() = mixedAppendCancellation(TimelineMergePolicy.Time, true)

    private fun mixedAppendCancellation(
        policy: TimelineMergePolicy,
        failingSibling: Boolean = false,
    ) = runTest {
        val first = Loader("first")
        val second = Loader("second").apply { response = page(20..29) }
        val mixed = MixedRemoteMediator(database, listOf(first, second), policy)
        val session = session(mixed, backgroundScope)
        session.refresh(false, initial = true)
        val dao = database.pagingTimelineDao()

        suspend fun stored() = dao.keysWithPrefix(session.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) }
        val before = stored()
        first.response = page(100..109, "first-next")
        second.response = page(120..129, "second-next")
        if (failingSibling) second.beforeLoad = { error("offline") }
        var cancelSave = true
        val mediator =
            object : TimelineRemoteMediator(mixed.forReadingSession(session.activeKey) { true }, database, false) {
                override suspend fun onSaveCache(
                    request: PagingRequest,
                    data: List<dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus>,
                ) {
                    super.onSaveCache(request, data)
                    if (cancelSave) throw CancellationException("Page cancelled before commit")
                }
            }
        val request = PagingRequest.Append("mixed_next_key")
        val response = mediator.load(40, request)
        assertEquals(before, stored(), "Fetching a page must not advance child cursors or staging")
        assertFailsWith<CancellationException> { mediator.saveResponse(request, response) }
        assertEquals(before, stored(), "Cancellation must roll back the complete mixed page")
        cancelSave = false
        val retry = mediator.load(40, request)
        val expectedIds = response.data.map { it.statusData.statusKey.id }
        assertTrue(expectedIds.isNotEmpty())
        assertEquals(expectedIds, retry.data.map { it.statusData.statusKey.id }, "Retry must fetch the cancelled page again")
        mediator.saveResponse(request, retry)
        assertTrue(expectedIds.all { it in ids(session) })
        assertEquals(listOf(request.copy(nextKey = "old-cursor"), request.copy(nextKey = "old-cursor")), first.requests.takeLast(2))
    }

    @Test
    fun viewLatestOnlyScrollsAndReopeningKeepsThePublishedSegments() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val oldKey = session.activeKey
            val oldRows = rows(session)
            loader.response = page(100..139, "next")
            session.refresh(false)
            val published = rows(session)
            val segments = database.timelineReadingSessionDao().segments(session.record.key)
            session.showLatest()
            assertEquals(oldKey, session.activeKey)
            assertEquals(published, rows(session), "View latest only changes the viewport")
            assertEquals(oldRows, rows(session).takeLast(oldRows.size))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(oldKey)?.nextKey)
            assertEquals("next", database.pagingTimelineDao().getPagingKey(segments.single().key)?.nextKey)
            session.positionRestored(assertNotNull(session.state.value.position).requestId)
            val key = "${session.activeKey}_${published[20].statusId}"
            session.updateViewport(ReadingViewport(key, -27.5, false, false))
            repository.checkpoint(session.record, oldKey, key, -27.5)
            session.close()
            val reopened = session(Loader(), backgroundScope, repository = repository)
            reopened.prepare()
            assertEquals(oldKey, reopened.activeKey)
            assertEquals(published, rows(reopened))
            assertEquals(segments, database.timelineReadingSessionDao().segments(reopened.record.key))
            assertEquals(key, assertNotNull(reopened.state.value.position).itemKey)
            assertEquals(
                -27.5,
                reopened.state.value.position
                    ?.offset,
            )
        }

    @Test
    fun disabledColdEntryDiscardsRecordsFromAnEarlierVersion() =
        runTest {
            val old = session(Loader(), backgroundScope)
            old.refresh(false, initial = true)
            val repository = TimelineReadingRepository(database, backgroundScope)
            repository.enterHome { false }
            assertTrue(database.timelineReadingSessionDao().all().isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(old.record.sessionKey).isEmpty())
        }

    @Test
    fun leavingThePageDuringDisableStillFinishesClearing() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session = session(Loader(), backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val locked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker =
                launch(Dispatchers.Default) {
                    database.connect {
                        locked.complete(Unit)
                        release.await()
                    }
                }
            locked.await()
            val disabling = launch { repository.enterHome { false } }
            repository.enabled.first { it == false }
            disabling.cancel()
            release.complete(Unit)
            disabling.join()
            blocker.join()
            assertTrue(database.timelineReadingSessionDao().all().isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.sessionKey).isEmpty())
        }

    @Test
    fun disablingDuringRefreshRejectsLateDataEvenAfterReenabling() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                started.complete(Unit)
                finish.await()
            }
            loader.response = page(100..110)
            val refresh = async { session.refresh(false) }
            started.await()
            repository.enterHome { false }
            repository.enterHome { true }
            val fresh = assertNotNull(repository.open("home", "source"))
            finish.complete(Unit)
            refresh.await()
            assertEquals(fresh, database.timelineReadingSessionDao().get("home"))
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.sessionKey).isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(fresh.key).isEmpty())
        }

    @Test
    fun manualRefreshMakesDisjointPostsScrollableWithoutMovingTheBookmark() =
        runTest {
            val loader = Loader().apply { response = page(0..99) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = original[50]
            val key = "${session.activeKey}_${anchor.statusId}"
            savePosition(session, key, anchor.sortId, -12.25)
            session.updateViewport(ReadingViewport(key, -12.25, false, false))
            loader.response = page(200..239, "fresh-cursor")

            session.refresh(false)

            assertEquals((200..239).map(Int::toString) + (0..99).map(Int::toString), ids(session))
            assertEquals(original, rows(session).takeLast(original.size))
            assertEquals(key, snapshot(session).anchorId)
            assertEquals(-12.25, snapshot(session).offset)
            assertNull(session.state.value.position)
            val source = TimelineReadingPagingSource(database, session)
            try {
                val top =
                    source.load(PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 40, false)) as
                        PagingSource.LoadResult.Page
                assertEquals((200..239).map(Int::toString), top.data.map { it.baseItem.statusKey.id })
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun manualRefreshAndViewLatestKeepBothPagesAndIndependentCursors() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            loader.response = page(100..110, "new-cursor")
            session.refresh(false)
            assertEquals(original, rows(session).takeLast(original.size))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertTrue(session.state.value.hasNewContent)
            assertNull(session.state.value.position)
            session.showLatest()
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            assertEquals("new-cursor", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
            assertTrue(database.pagingTimelineDao().keysWithPrefix(snapshot(session).pendingKey).isEmpty())
        }

    @Test
    fun topBookmarkRestoresThePostAndOffsetWithoutStartingNetwork() =
        runTest {
            val loader = Loader()
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val head = rows(initial).first()
            val key = "${initial.activeKey}_${head.statusId}"
            savePosition(initial, key, head.sortId, 12.25)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            assertEquals(
                "${restored.activeKey}_${head.statusId}",
                restored.state.value.position
                    ?.itemKey,
            )
            assertEquals(
                12.25,
                restored.state.value.position
                    ?.offset,
            )
            assertFalse(
                restored.state.value.position
                    ?.latest == true,
            )
            assertEquals(1, loader.requests.size)
            restored.positionRestored(assertNotNull(restored.state.value.position).requestId)
            assertEquals(1, loader.requests.size)
        }

    @Test
    fun deepBookmarkLoadsOnlyItsLocalWindowAndCanPageBothWays() =
        runTest {
            val loader = Loader().apply { response = page(0..999) }
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val anchor = rows(initial)[800]
            val key = "${initial.activeKey}_${anchor.statusId}"
            savePosition(initial, key, anchor.sortId, -51.5)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            val source = TimelineReadingPagingSource(database, restored)
            try {
                val page = source.load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
                assertEquals(40, page.data.size)
                assertEquals(
                    "780",
                    page.data
                        .first()
                        .baseItem.statusKey.id,
                )
                assertEquals("${restored.activeKey}_${anchor.statusId}", page.data[20].baseItem.stableItemKey)
                assertNotNull(page.prevKey)
                assertNotNull(page.nextKey)
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun restoredWindowInvalidatesForInsertedPostsButIgnoresUnrelatedWrites() =
        runTest {
            val loader = Loader().apply { response = page(0..199) }
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val anchor = rows(initial)[100]
            savePosition(initial, "${initial.activeKey}_${anchor.statusId}", anchor.sortId, -51.5)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            val position = assertNotNull(restored.state.value.position)
            val original = rows(restored)
            val cursor = database.pagingTimelineDao().getPagingKey(restored.activeKey)
            val cache = TimelineDbPageCache()
            val pageLoader = TimelineDbPageLoader(database, restored.activeKey, cache)
            val window = pageLoader.load(80, 40)
            assertFalse(cache.hasCurrentWindowChanged(database.pagingTimelineDao(), restored.activeKey))
            val invalidations = MutableStateFlow(0)
            val subscription = pageLoader.observeInvalidations { invalidations.value += 1 }
            try {
                subscription.ready?.await()
                database.pagingTimelineDao().insertNew(
                    listOf(DbPagingTimeline("unrelated-home", anchor.statusId, anchor.sortId)),
                )
                assertFalse(cache.hasCurrentWindowChanged(database.pagingTimelineDao(), restored.activeKey))
                withContext(Dispatchers.Default) {
                    assertNull(withTimeoutOrNull(250) { invalidations.first { it > 0 } })
                }
                assertEquals(window.map { it.identity }, pageLoader.load(80, 40).map { it.identity })
                assertEquals(original, rows(restored))
                loader.response = page(1000..1039)
                restored.refresh(false)
                assertTrue(cache.hasCurrentWindowChanged(database.pagingTimelineDao(), restored.activeKey))
                assertEquals(original, rows(restored).takeLast(original.size))
                pageLoader.load(120, 40)
                assertEquals(cursor, database.pagingTimelineDao().getPagingKey(restored.activeKey))
                assertEquals(position, restored.state.value.position)
                assertEquals(position.itemKey, snapshot(restored).anchorId)
                assertEquals(-51.5, snapshot(restored).offset)

                database.pagingTimelineDao().insertAll(listOf(anchor.copy(contentRevision = anchor.contentRevision + 1)))
                withContext(Dispatchers.Default) {
                    assertNotNull(withTimeoutOrNull(5_000) { invalidations.first { it > 0 } })
                }
                val updated = pageLoader.load(120, 40)
                assertEquals(window.map { it.statusId }, updated.map { it.statusId })
                assertEquals(anchor.contentRevision + 1, updated[20].identity.contentRevision)
                assertFalse(cache.hasCurrentWindowChanged(database.pagingTimelineDao(), restored.activeKey))
                assertEquals(position, restored.state.value.position)
            } finally {
                subscription.job.cancel()
            }
        }

    @Test
    fun restoredWindowTracksBothPagingDirectionsAndBoundaryChanges() =
        runTest {
            val session = session(Loader().apply { response = page(0..199) }, backgroundScope)
            session.refresh(false, initial = true)
            val dao = database.pagingTimelineDao()
            val cache = TimelineDbPageCache()
            val loader = TimelineDbPageLoader(database, session.activeKey, cache)
            for (offset in listOf(80, 120, 40, 0)) {
                assertEquals(40, loader.load(offset, 40).size)
                assertFalse(cache.hasCurrentWindowChanged(dao, session.activeKey))
            }
            val original = rows(session)
            // Include the row just beyond the loaded window so newly available pages are detected.
            for (index in listOf(0, 40, 80, 120, 160)) {
                val row = original[index]
                dao.insertAll(listOf(row.copy(contentRevision = row.contentRevision + 1)))
                assertTrue(cache.hasCurrentWindowChanged(dao, session.activeKey), "Missed a change at offset $index")
                loader.load(0, 160)
                assertFalse(cache.hasCurrentWindowChanged(dao, session.activeKey))
            }
            val outside = original[190]
            dao.insertAll(listOf(outside.copy(contentRevision = outside.contentRevision + 1)))
            assertFalse(cache.hasCurrentWindowChanged(dao, session.activeKey))
            dao.delete(listOf(original.first()))
            assertTrue(cache.hasCurrentWindowChanged(dao, session.activeKey))
        }

    @Test
    fun startupAttachesNewPostsWithoutOverridingRestoration() =
        runTest {
            val loader = Loader(prepend = true)
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val oldRows = rows(initial)
            loader.response = page(10..20)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            restored.refresh(false, initial = true)
            assertEquals(oldRows.map { it.statusId to it.sortId }, rows(restored).takeLast(oldRows.size).map { it.statusId to it.sortId })
            assertTrue(restored.state.value.hasNewContent)
            assertFalse(loader.requests.any { it is PagingRequest.Prepend })
        }

    @Test
    fun provenPrependKeepsExistingOrderAndManualRefreshDoesNotJump() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val old = rows(session)
            session.updateViewport(ReadingViewport("${session.activeKey}_${old.first().statusId}", 0.0, true, false))
            loader.response = page(10..20)
            loader.forward = page(10..20)
            session.refresh(false)
            assertEquals((10..20).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertEquals(old, rows(session).takeLast(old.size))
            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
        }

    @Test
    fun reachingContinuousHeadWhileScrollingDismissesNoticeWithoutChangingPositionOrCursors() =
        dismissNoticeWhileScrolling(TimelineOrder.Continuous)

    @Test
    fun reachingRankedHeadWhileScrollingDismissesNoticeWithoutChangingPositionOrCursors() =
        dismissNoticeWhileScrolling(TimelineOrder.Ranked)

    private fun dismissNoticeWhileScrolling(order: TimelineOrder) =
        runTest {
            val loader = Loader(timelineOrder = order)
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() % 2 == 0 })
            session.refresh(false, initial = true)
            val anchor = rows(session)[4]
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -12.5)
            loader.response = page(11..20, "new-cursor")
            session.refresh(false)
            assertTrue(session.state.value.hasNewContent)
            val before = snapshot(session)
            val beforeRows = rows(session)
            val beforeCursors = database.timelineReadingSessionDao().cursors(session.record.key)
            val beforeSegments = database.timelineReadingSessionDao().segments(session.record.key)
            val requests = loader.requests.toList()

            // 11 is filtered out; 12 is the actual visible head even while the gesture continues.
            assertEquals(listOf("11", "12"), ids(session).take(2))
            session.updateViewport(
                ReadingViewport("${session.activeKey}_${beforeRows[1].statusId}", 0.0, atTop = true, interacting = true),
            )
            withContext(Dispatchers.Default) {
                assertNotNull(withTimeoutOrNull(2_000) { session.state.first { !it.hasNewContent } })
            }

            assertNull(session.state.value.position)
            assertEquals(before.copy(hasNewContent = false, newContentBoundaryId = null), snapshot(session))
            assertEquals(beforeRows, rows(session))
            assertEquals(beforeCursors, database.timelineReadingSessionDao().cursors(session.record.key))
            assertEquals(beforeSegments, database.timelineReadingSessionDao().segments(session.record.key))
            assertEquals(requests, loader.requests, "Dismissing the notice must not start gap loading during a gesture")

            session.updateViewport(
                ReadingViewport("${session.activeKey}_${beforeRows[3].statusId}", -8.0, atTop = false, interacting = true),
            )
            assertFalse(session.state.value.hasNewContent)
            loader.response = page(21..30, "next-cursor")
            session.refresh(false)
            assertTrue(session.state.value.hasNewContent, "A later refresh must still offer its new content")
        }

    @Test
    fun olderWindowTopKeepsNoticeWithoutRepeatingQueriesForOffsetUpdates() =
        runTest {
            var headChecked: CompletableDeferred<Unit>? = null
            val loader = Loader().apply { response = page(0..99) }
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session =
                session(
                    loader,
                    backgroundScope,
                    repository = repository,
                    visible = {
                        headChecked?.complete(Unit)
                        true
                    },
                )
            session.refresh(false, initial = true)
            val key = "${session.activeKey}_${rows(session)[60].statusId}"
            loader.response = page(100..110)
            session.refresh(false)
            val checked = CompletableDeferred<Unit>()
            headChecked = checked

            session.updateViewport(ReadingViewport(key, 0.0, atTop = true, interacting = true))
            withContext(Dispatchers.Default) {
                assertNotNull(withTimeoutOrNull(2_000) { checked.await() })
                repository.selectedTab.first { it == "home" }
            }
            session.filtersChanged()
            assertTrue(session.state.value.hasNewContent)

            var queries = 0
            onSelect = { queries++ }
            try {
                repeat(60) { offset ->
                    session.updateViewport(ReadingViewport(key, offset.toDouble(), atTop = true, interacting = true))
                    testScheduler.runCurrent()
                }
                assertEquals(0, queries)
            } finally {
                onSelect = {}
            }
            assertTrue(session.state.value.hasNewContent)
            assertNull(session.state.value.position)
        }

    @Test
    fun timerFollowsLatestFromTheFirstVisiblePostBeyondAFilteredPage() =
        runTest {
            val loader = Loader(prepend = true).apply { response = page(0..79) }
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() >= 45 })
            session.refresh(false, initial = true)
            val head = rows(session)[45]
            session.updateViewport(ReadingViewport("${session.activeKey}_${head.statusId}", 0.0, true, false))
            loader.response = page(100..110)
            loader.forward = loader.response

            session.refresh(true)

            assertTrue(loader.requests.last() is PagingRequest.Prepend)
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
            assertEquals((100..110).map(Int::toString) + (0..79).map(Int::toString), ids(session))
        }

    @Test
    fun timerDoesNotFollowLatestFromTheTopOfAnOlderWindow() =
        runTest {
            val loader = Loader(prepend = true).apply { response = page(0..99) }
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() >= 45 })
            session.refresh(false, initial = true)
            val source = TimelineReadingPagingSource(database, session)
            try {
                val window =
                    source.load(PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(60), 40, false)) as
                        PagingSource.LoadResult.Page
                val first = window.data.first().baseItem
                assertEquals("60", first.statusKey.id)
                assertNotNull(window.prevKey)
                session.updateViewport(ReadingViewport(first.stableItemKey, 0.0, true, false))
                loader.response = page(100..110)
                loader.forward = loader.response

                session.refresh(true)

                assertNull(session.state.value.position)
                assertTrue(session.state.value.hasNewContent)
                assertEquals((100..110).map(Int::toString) + (0..99).map(Int::toString), ids(session))
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun timerDoesNotRequestAScrollWhenTheActivePostsAreAllFiltered() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() >= 100 })
            session.refresh(false, initial = true)
            session.updateViewport(ReadingViewport(null, 0.0, true, false))
            loader.response = page(100..110)
            loader.forward = loader.response

            session.refresh(true)

            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
        }

    @Test
    fun timerFollowsLatestOnlyWhenStillIdleAtTheRealHead() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val head = rows(session).first()
            session.updateViewport(ReadingViewport("${session.activeKey}_${head.statusId}", 0.0, true, false))
            loader.response = page(10..20)
            loader.forward = page(10..20)
            session.refresh(true)
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
        }

    @Test
    fun gestureStartedDuringRequestPreventsTimerJump() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val head = rows(session).first()
            val key = "${session.activeKey}_${head.statusId}"
            session.updateViewport(ReadingViewport(key, 0.0, true, false))
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                started.complete(Unit)
                finish.await()
            }
            loader.response = page(10..20)
            loader.forward = page(10..20)
            val refresh = async { session.refresh(true) }
            started.await()
            session.updateViewport(ReadingViewport(key, -18.0, false, true))
            finish.complete(Unit)
            refresh.await()
            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
            assertEquals(21, rows(session).size)
        }

    @Test
    fun cacheClearRejectsAResponseAlreadyInFlight() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                started.complete(Unit)
                finish.await()
            }
            val refresh = async { session.refresh(false) }
            started.await()
            database.clearAllTables()
            finish.complete(Unit)
            refresh.await()
            assertNull(database.timelineReadingSessionDao().get("home"))
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.sessionKey).isEmpty())
        }

    @Test
    fun emptyMixedSessionPublishesHealthySource() =
        runTest {
            val failing = Loader("failing").apply { beforeLoad = { error("offline") } }
            val healthy = Loader("healthy")
            val mixed = MixedRemoteMediator(database, listOf(failing, healthy), TimelineMergePolicy.Time)
            val session = session(mixed, backgroundScope)
            session.refresh(false, initial = true)
            assertEquals((0..9).map(Int::toString).toSet(), ids(session).toSet())
            assertNotNull(database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertFalse(session.state.value.hasNewContent)
            assertNull(session.state.value.position)
        }

    @Test
    fun mixedSegmentCursorsAreIndependentAndPartialFailurePreservesActiveData() =
        runTest {
            val first = Loader("first")
            val second = Loader("second").apply { response = page(20..29, "second-old") }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Staggered)
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session = session(mixed, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[5].statusId}"
            session.updateViewport(ReadingViewport(anchor, -37.25, atTop = false, interacting = false))
            repository.checkpoint(session.record, session.activeKey, anchor, -37.25)
            val cursorKeys = database.pagingTimelineDao().keysWithPrefix(session.activeKey)
            val cursors = cursorKeys.map { database.pagingTimelineDao().getPagingKey(it) }
            first.response = page(100..110, "first-new")
            second.beforeLoad = { error("offline") }
            session.refresh(false)
            assertEquals(original, rows(session).takeLast(original.size))
            assertEquals(cursors, cursorKeys.map { database.pagingTimelineDao().getPagingKey(it) })
            assertTrue(session.state.value.hasNewContent)
            val pending = snapshot(session)
            assertFalse(pending.pendingReady)
            assertEquals(
                0,
                database
                    .pagingTimelineDao()
                    .getByPagingKey(
                        database
                            .timelineReadingSessionDao()
                            .segments(session.record.key)
                            .single()
                            .key,
                    ).size,
            )
            assertEquals(anchor, pending.anchorId)
            assertEquals(-37.25, pending.offset)
            assertNull(session.state.value.position)
            second.beforeLoad = {}
            second.response = page(120..130, "second-new")
            session.refresh(false)
            assertEquals(original, rows(session).takeLast(original.size))
            session.showLatest()
            val segmentKey =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .minBy { it.boundarySortId }
                    .key
            assertEquals("first-new", database.pagingTimelineDao().getPagingKey("$segmentKey/source/5:first/cursor")?.nextKey)
            assertEquals("second-new", database.pagingTimelineDao().getPagingKey("$segmentKey/source/6:second/cursor")?.nextKey)
        }

    @Test
    fun removedTabAndSourceChangesInvalidateRowsButOpeningSameSourceDoesNot() =
        runTest {
            val repo = TimelineReadingRepository(database, backgroundScope)
            repo.enterHome { true }
            val session = session(Loader(), backgroundScope)
            session.refresh(false, initial = true)
            assertEquals(session.record.sessionKey, assertNotNull(repo.open("home", "source")).key)
            val replacement = assertNotNull(repo.open("home", "different-source"))
            assertNotEquals(session.record.sessionKey, replacement.key)
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.sessionKey).isEmpty())
            repo.retainTabs(emptyMap())
            assertNull(database.timelineReadingSessionDao().get("home"))
        }

    @Test
    fun filteredNewPostsDoNotOfferAJumpToAnInvisibleSnapshot() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() < 10 })
            session.refresh(false, initial = true)
            val old = rows(session)
            loader.response = page(100..110)
            session.refresh(false)
            assertEquals(old, rows(session).takeLast(old.size))
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertFalse(session.state.value.hasNewContent)
        }

    @Test
    fun filteringPendingPostsDismissesTheNoticeWithoutMovingTheReader() = dismissFilteredPendingPosts(refreshBeforeClick = false)

    @Test
    fun refreshingFilteredPendingPostsDismissesTheNoticeWithoutMovingTheReader() = dismissFilteredPendingPosts(refreshBeforeClick = true)

    private fun dismissFilteredPendingPosts(refreshBeforeClick: Boolean) =
        runTest {
            var hideLatest = false
            val loader = Loader()
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session =
                session(
                    loader,
                    backgroundScope,
                    visible = { !hideLatest || it.statusKey.id.toInt() < 100 },
                    repository = repository,
                )
            session.refresh(false, initial = true)
            val originalRows = rows(session)
            val originalKey = session.activeKey
            val originalCursor = database.pagingTimelineDao().getPagingKey(originalKey)
            val anchorKey = "${originalKey}_${originalRows[9].statusId}"
            session.updateViewport(ReadingViewport(anchorKey, -37.25, atTop = false, interacting = false))
            repository.checkpoint(session.record, originalKey, anchorKey, -37.25)
            loader.response = page(100..110, "fresh-cursor")
            session.refresh(false)
            val pending = snapshot(session)
            val retained = database.timelineReadingSessionDao().get("home")
            assertTrue(session.state.value.hasNewContent)
            assertTrue(pending.hasNewContent)
            assertFalse(pending.pendingReady)

            hideLatest = true
            if (refreshBeforeClick) session.refresh(false)
            val requestCount = loader.requests.size
            repeat(2) { session.showLatest() }

            assertFalse(session.state.value.hasNewContent)
            assertEquals(pending.copy(hasNewContent = false, pendingReady = false, newContentBoundaryId = null), snapshot(session))
            assertEquals(originalKey, session.activeKey)
            assertEquals(originalRows, rows(session).takeLast(originalRows.size))
            assertEquals(originalCursor, database.pagingTimelineDao().getPagingKey(originalKey))
            assertEquals(retained, database.timelineReadingSessionDao().get("home"))
            assertNull(session.state.value.position, "Repeated clicks must not request a scroll after the notice is dismissed")
            assertEquals(requestCount, loader.requests.size, "Dismissing filtered content must not fetch another page")
            session.close()

            val reopened = session(Loader(), backgroundScope, repository = repository)
            reopened.prepare()
            assertFalse(reopened.state.value.hasNewContent)
            val position = assertNotNull(reopened.state.value.position)
            assertEquals(anchorKey, position.itemKey)
            assertEquals(-37.25, position.offset)
            assertFalse(position.latest)
            reopened.close()
        }

    @Test
    fun repeatedRefreshStillAllowsViewingTheAlreadyInsertedNewPosts() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(10..20)
            loader.forward = loader.response
            session.refresh(false)
            session.refresh(false)
            session.showLatest()
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
        }

    @Test
    fun initiallyFilteredPageStillKeepsTheCursorForLoadingOlderPosts() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope, visible = { false })
            session.refresh(false, initial = true)
            assertEquals(10, rows(session).size)
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
        }

    @Test
    fun filteredActivePostsDoNotHideVisibleRefreshedContent() =
        runTest {
            val loader = Loader().apply { response = page(0..9).copy(nextKey = null) }
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() >= 100 })
            session.refresh(false, initial = true)
            loader.response = page(100..110).copy(nextKey = null)

            session.refresh(false)

            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertTrue(session.state.value.hasNewContent)
            assertFalse(snapshot(session).pendingReady)
            session.showLatest()
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertFalse(session.state.value.hasNewContent)
            assertTrue(assertNotNull(session.state.value.position).latest)
        }

    @Test
    fun continuityFailureStillOffersSuccessfullyFetchedLatest() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchorKey = "${session.activeKey}_${original[4].statusId}"
            session.updateViewport(ReadingViewport(anchorKey, -12.0, false, false))
            loader.response = page(100..110, "fresh-cursor")
            loader.beforeLoad = {
                if (loader.requests.last() is PagingRequest.Prepend) error("continuity request offline")
            }
            session.refresh(false)
            assertEquals(original, rows(session).takeLast(original.size))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertNull(session.state.value.position)
            val record = snapshot(session)
            assertFalse(record.pendingReady)
            assertTrue(record.hasNewContent)
            assertTrue(session.state.value.hasNewContent)
            session.showLatest()
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertEquals(
                "fresh-cursor",
                database
                    .pagingTimelineDao()
                    .getPagingKey(
                        database
                            .timelineReadingSessionDao()
                            .segments(session.record.key)
                            .single()
                            .key,
                    )?.nextKey,
            )
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Refresh, PagingRequest.Prepend("head-0")), loader.requests.take(3))
            assertTrue(loader.requests.drop(3).all { it is PagingRequest.Append })
        }

    @Test
    fun continuityCancellationPropagatesWithoutPublishingLatest() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            loader.response = page(100..110)
            loader.beforeLoad = {
                if (loader.requests.last() is PagingRequest.Prepend) throw CancellationException("cancelled continuity request")
            }
            assertFailsWith<CancellationException> { session.refresh(false) }
            assertEquals(original, rows(session))
            assertFalse(session.state.value.hasNewContent)
            assertFalse(session.state.value.refreshing)
            assertNull(session.state.value.position)
        }

    @Test
    fun completedGestureDuringTimerFetchDoesNotFollowLatest() = completedGestureDuringRefresh(waitForPrepend = false)

    @Test
    fun closeKeepsItsFinalViewportDespiteQueuedBackgroundAndDebouncedSaves() =
        runTest {
            val lifetime = Job()
            val dispatcher = QueuedPositionSaveDispatcher()
            val repository = TimelineReadingRepository(database, CoroutineScope(lifetime + dispatcher))
            val session = session(Loader(), backgroundScope, repository = repository)
            try {
                session.refresh(false, initial = true)
                val before = rows(session)
                val oldAnchor = "${session.activeKey}_${before[0].statusId}"
                val newAnchor = "${session.activeKey}_${before[1].statusId}"
                session.updateViewport(ReadingViewport(oldAnchor, -1.25, atTop = false, interacting = false))
                session.savePosition()
                session.updateViewport(ReadingViewport(newAnchor, -2.5, atTop = false, interacting = false))
                session.close()
                dispatcher.runAllNewestFirst()
                val reopened = session(Loader(), backgroundScope, repository = repository)
                reopened.prepare()
                val position = assertNotNull(reopened.state.value.position)
                assertEquals(newAnchor, position.itemKey)
                assertEquals(-2.5, position.offset)
                reopened.close()
            } finally {
                lifetime.cancel()
            }
        }

    @Test
    fun pendingDebounceCannotReplaceANewerCheckpoint() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session = session(Loader(), backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val before = rows(session)
            val oldAnchor = "${session.activeKey}_${before[0].statusId}"
            val newAnchor = "${session.activeKey}_${before[1].statusId}"
            try {
                val lifetime = requireNotNull(backgroundScope.coroutineContext[Job])
                val existingJobs = lifetime.children.toSet()
                session.updateViewport(ReadingViewport(oldAnchor, -1.25, atTop = false, interacting = false))
                val debounce = lifetime.children.single { it !in existingJobs }
                repository.checkpoint(session.record, session.activeKey, newAnchor, -2.5)
                debounce.join()
                val saved = assertNotNull(repository.current(session.record))
                assertEquals(newAnchor, saved.anchorId)
                assertEquals(-2.5, saved.offset)
            } finally {
                session.close()
            }
        }

    @Test
    fun viewportInteractionCancelsRestorationAndKeepsTheFinalOffset() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(10..20)
            session.refresh(false)
            session.showLatest()
            val position = assertNotNull(session.state.value.position)
            repeat(100) {
                session.updateViewport(ReadingViewport(position.itemKey, -it - 0.25, false, true))
            }
            assertNull(session.state.value.position)
            session.positionRestored(position.requestId)
            session.updateViewport(ReadingViewport(position.itemKey, -99.25, false, false))
            session.close()
            assertEquals(position.itemKey, snapshot(session).anchorId)
            assertEquals(-99.25, snapshot(session).offset)
        }

    @Test
    fun viewLatestDoesNotPublishARestorationDuringAnActiveGesture() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val anchor = rows(session)[5]._id
            session.updateViewport(ReadingViewport(anchor, -37.25, false, true))
            loader.response = page(10..20)
            session.refresh(false)
            session.showLatest()
            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
            session.updateViewport(ReadingViewport(anchor, -37.25, false, false))
            session.showLatest()
            assertTrue(assertNotNull(session.state.value.position).latest)
        }

    @Test
    fun completedGestureDuringContinuityCheckDoesNotFollowLatest() = completedGestureDuringRefresh(waitForPrepend = true)

    private fun completedGestureDuringRefresh(waitForPrepend: Boolean) =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val head = rows(session).first()
            val key = "${session.activeKey}_${head.statusId}"
            session.updateViewport(ReadingViewport(key, 0.0, true, false))
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                if ((loader.requests.last() is PagingRequest.Prepend) == waitForPrepend) {
                    started.complete(Unit)
                    finish.await()
                }
            }
            loader.response = page(10..20)
            loader.forward = loader.response
            val refresh = async { session.refresh(true) }
            started.await()
            session.updateViewport(ReadingViewport(key, -18.0, false, true))
            session.cancelRestoration()
            session.updateViewport(ReadingViewport(key, 0.0, true, false))
            finish.complete(Unit)
            refresh.await()
            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
            assertEquals(21, rows(session).size)
        }

    @Test
    fun gestureDuringAutomaticPublicationDoesNotJump() = gestureDuringPublication(automatic = true)

    @Test
    fun gestureDuringViewLatestPublicationDoesNotJump() = gestureDuringPublication(automatic = false)

    private fun gestureDuringPublication(automatic: Boolean) =
        runTest {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var resolvingLatest = false
            val loader = Loader(prepend = automatic)
            val session =
                session(loader, backgroundScope, visible = {
                    if (resolvingLatest) {
                        started.complete(Unit)
                        finish.await()
                    }
                    true
                })
            session.refresh(false, initial = true)
            val original = rows(session)
            val key = "${session.activeKey}_${original.first().statusId}"
            session.updateViewport(ReadingViewport(key, 0.0, true, false))
            loader.response = page(10..20, "fresh-cursor")
            loader.forward = loader.response
            if (automatic) {
                loader.beforeLoad = {
                    if (loader.requests.last() is PagingRequest.Prepend) resolvingLatest = true
                }
            } else {
                session.refresh(false)
                resolvingLatest = true
            }
            val publication = async { if (automatic) session.refresh(true) else session.showLatest() }
            started.await()
            session.updateViewport(ReadingViewport(key, -18.0, false, true))
            session.cancelRestoration()
            session.updateViewport(ReadingViewport(key, -18.0, false, false))
            finish.complete(Unit)
            publication.await()
            assertNull(session.state.value.position)
            assertTrue(session.state.value.hasNewContent)
            assertEquals(original, rows(session).takeLast(original.size))
            assertEquals(21, rows(session).size)
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertFalse(snapshot(session).pendingReady)

            session.showLatest()
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertEquals((10..20).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertFalse(session.state.value.hasNewContent)
        }

    @Test
    fun failedAttachmentRollsBackRowsSegmentAndCursorTogether() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val pendingKey = snapshot(session).pendingKey
            loader.response = page(100..110).copy(nextKey = null)
            var armed = false
            var interrupted = false
            loader.beforeLoad = { armed = true }
            afterPagingDelete = { key ->
                if (armed && key == pendingKey) {
                    interrupted = true
                    error("attachment storage failure")
                }
            }
            session.refresh(false)
            afterPagingDelete = {}
            assertTrue(interrupted, "Fail after inserting the visible rows, inside the attachment transaction")
            assertEquals(original, rows(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertFalse(session.state.value.hasNewContent)
            assertFalse(session.state.value.refreshing)
            session.refresh(false)
            assertEquals((100..110).map(Int::toString) + (0..9).map(Int::toString), ids(session))
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
        }

    @Test
    fun deletedBookmarkUsesTheNearestSurvivingPrependPost() =
        runTest {
            val loader = Loader(prepend = true).apply { response = page(0..39) }
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            loader.response = page(100..139)
            loader.forward = loader.response
            initial.refresh(false)
            // Retained snapshots from older versions can still contain dense Snowflake ranks.
            database.pagingTimelineDao().insertAll(rows(initial).mapIndexed { index, row -> row.copy(sortId = (1L shl 60) + index) })
            val prepended = rows(initial).take(40)
            assertTrue(prepended.first().sortId > (1L shl 53), "Cover legacy Snowflake ranks")
            assertEquals(List(40) { it.toLong() }, prepended.map { it.sortId - prepended.first().sortId })
            val bucket = prepended.groupBy { it.sortId.toDouble() }.values.maxBy { it.size }
            assertTrue(bucket.size > 10, "Production contiguous ranks lose precision in REAL")
            val deleted = bucket[bucket.size - 2]
            val expected = bucket[bucket.size - 3]
            savePosition(
                initial,
                "${initial.activeKey}_${deleted.statusId}",
                deleted.sortId,
                -24.0,
            )
            database.pagingTimelineDao().delete(listOf(deleted))

            val restored = session(loader, backgroundScope)
            restored.prepare()

            val actual = assertNotNull(restored.state.value.position).itemKey
            assertEquals(
                "${restored.activeKey}_${expected.statusId}",
                actual,
                "A deleted bookmark must restore the immediately preceding survivor rather than the beginning of a rounded REAL bucket",
            )
        }

    @Test
    fun secondSceneKeepsItsCurrentPostWhenFirstSceneViewsLatest() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val firstLoader = Loader().apply { response = page(0..99) }
            val firstScene = session(firstLoader, backgroundScope, repository = repository)
            firstScene.refresh(false, initial = true)
            val secondScene = session(Loader(), backgroundScope, repository = repository)
            assertEquals(firstScene.record.sessionKey, secondScene.record.sessionKey)
            val source = TimelineReadingPagingSource(database, secondScene)
            try {
                val oldPage =
                    source.load(PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(60), 40, false)) as
                        PagingSource.LoadResult.Page
                val oldAnchor = oldPage.data[20].baseItem
                assertEquals("80", oldAnchor.statusKey.id)
                secondScene.updateViewport(ReadingViewport(oldAnchor.stableItemKey, -30.0, false, true))
                repository.checkpoint(secondScene.record, secondScene.activeKey, oldAnchor.stableItemKey, -30.0)
                val refreshKey =
                    source.getRefreshKey(
                        androidx.paging.PagingState(
                            listOf(oldPage),
                            20,
                            dev.dimension.flare.data.datasource.microblog.offsetPagingConfig,
                            0,
                        ),
                    )
                assertEquals(OffsetFromStartPagingKey.Around(oldAnchor.stableItemKey), refreshKey)

                firstLoader.response = page(100..139)
                firstScene.refresh(false)
                assertTrue(firstScene.state.value.hasNewContent)
                assertFalse(secondScene.state.value.hasNewContent)
                firstScene.showLatest()
                assertNull(secondScene.state.value.position, "Second scene did not request a scroll")
                val replacement = TimelineReadingPagingSource(database, secondScene)
                try {
                    val page = replacement.load(PagingSource.LoadParams.Refresh(refreshKey, 40, false)) as PagingSource.LoadResult.Page
                    val actual = page.data.map { it.baseItem.statusKey.id }
                    assertTrue("80" in actual, "Viewing latest in the first scene removed the second scene's current post: $actual")
                    val resumed = replacement.load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
                    assertTrue(
                        resumed.data.any { it.baseItem.statusKey.id == "80" },
                        "A null refresh key must use this window's saved anchor",
                    )
                } finally {
                    replacement.invalidate()
                }
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun windowsKeepIndependentSegmentsAndPersistTheLastReadWindow() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val firstLoader = Loader().apply { response = page(0..99) }
            val first = session(firstLoader, backgroundScope, repository = repository)
            first.refresh(false, initial = true)
            val secondLoader = Loader()
            val second = session(secondLoader, backgroundScope, repository = repository)
            val secondRows = rows(second)
            firstLoader.response = page(100..139, "first-next")
            secondLoader.response = page(200..239, "second-next")
            first.refresh(false)
            second.refresh(false)
            first.showLatest()
            assertEquals((100..139).map(Int::toString) + (0..99).map(Int::toString), ids(first))
            assertEquals(secondRows, rows(second).takeLast(secondRows.size))
            assertTrue(second.state.value.hasNewContent)
            second.showLatest()
            assertEquals((200..239).map(Int::toString) + (0..99).map(Int::toString), ids(second))
            assertEquals(
                "first-next",
                database
                    .pagingTimelineDao()
                    .getPagingKey(
                        database
                            .timelineReadingSessionDao()
                            .segments(first.record.key)
                            .single()
                            .key,
                    )?.nextKey,
            )
            assertEquals(
                "second-next",
                database
                    .pagingTimelineDao()
                    .getPagingKey(
                        database
                            .timelineReadingSessionDao()
                            .segments(second.record.key)
                            .single()
                            .key,
                    )?.nextKey,
            )
            second.positionRestored(assertNotNull(second.state.value.position).requestId)
            val anchor = rows(second)[20]
            second.updateViewport(ReadingViewport("${second.activeKey}_${anchor.statusId}", -30.0, false, true))
            second.close()
            first.close()
            assertTrue(database.pagingTimelineDao().keysWithPrefix(first.record.key).isEmpty())
            assertEquals(140, rows(second).size)
            val reopened = session(Loader(), backgroundScope, repository = repository)
            assertEquals(second.activeKey, reopened.activeKey, "An unoccupied retained snapshot must be reused without copying history")
            reopened.prepare()
            assertEquals("${reopened.activeKey}_${anchor.statusId}", assertNotNull(reopened.state.value.position).itemKey)
            assertEquals(
                -30.0,
                reopened.state.value.position
                    ?.offset,
            )
            assertEquals(
                "second-next",
                database
                    .pagingTimelineDao()
                    .getPagingKey(
                        database
                            .timelineReadingSessionDao()
                            .segments(reopened.record.key)
                            .single()
                            .key,
                    )?.nextKey,
            )
            // A disposed reader must not overwrite the bookmark of its successor.
            repository.checkpoint(second.record, second.activeKey, "${second.activeKey}_${rows(second).first().statusId}", 0.0)
            assertEquals(-30.0, snapshot(reopened).offset)
            repository.enterHome { false }
            assertTrue(database.pagingTimelineDao().keysWithPrefix(first.record.sessionKey).isEmpty())
            assertTrue(database.timelineReadingSessionDao().snapshots(first.record.sessionKey).isEmpty())
        }

    @Test
    fun mixedWindowsKeepChildCursorsIndependentOnPublication() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val a = Loader("first")
            val b = Loader("second").apply { response = page(20..29, "second-old") }
            val first =
                session(
                    MixedRemoteMediator(database, listOf(a, b), TimelineMergePolicy.Staggered),
                    backgroundScope,
                    repository = repository,
                )
            first.refresh(false, initial = true)
            val second =
                session(
                    MixedRemoteMediator(database, listOf(Loader("first"), Loader("second")), TimelineMergePolicy.Staggered),
                    backgroundScope,
                    repository = repository,
                )
            val dao = database.pagingTimelineDao()
            val old = dao.keysWithPrefix(second.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) }
            a.response = page(100..109, "first-new")
            b.response = page(120..129, "second-new")
            first.refresh(false)
            first.showLatest()
            val segmentKey =
                database
                    .timelineReadingSessionDao()
                    .segments(first.record.key)
                    .single()
                    .key
            assertEquals(old, dao.keysWithPrefix(second.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) })
            assertEquals("first-new", dao.getPagingKey("$segmentKey/source/5:first/cursor")?.nextKey)
            assertEquals("second-new", dao.getPagingKey("$segmentKey/source/6:second/cursor")?.nextKey)
        }

    @Test
    fun nearestBookmarkKeepsIntegerPrecisionAcrossTheSignedBoundary() =
        runTest {
            val session = session(Loader(), backgroundScope)
            session.refresh(false, initial = true)
            val dao = database.pagingTimelineDao()
            val original = rows(session)
            dao.delete(session.activeKey)
            val low = original[0].copy(sortId = Long.MIN_VALUE)
            val high = original[1].copy(sortId = Long.MAX_VALUE)
            dao.insertAll(listOf(low, high))
            assertEquals(low, dao.nearestReadingItem(session.activeKey, -1))
            assertEquals(high, dao.nearestReadingItem(session.activeKey, 0))
            assertEquals(low, dao.nearestReadingItem(session.activeKey, Long.MIN_VALUE))
            assertEquals(high, dao.nearestReadingItem(session.activeKey, Long.MAX_VALUE))
            dao.delete(listOf(high))
            assertEquals(low, dao.nearestReadingItem(session.activeKey, Long.MAX_VALUE))
            dao.delete(listOf(low))
            assertNull(dao.nearestReadingItem(session.activeKey, 0))
        }

    @Test
    fun repeatedDisjointRefreshesAndReopeningKeepEveryPageAndBookmark() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[7].statusId}"
            session.updateViewport(ReadingViewport(anchor, -33.125, false, false))
            repository.checkpoint(session.record, session.activeKey, anchor, -33.125)
            for (start in listOf(100, 200, 300)) {
                loader.response = page(start..start + 9, "next-$start")
                session.refresh(false)
            }
            val expected = (300..309).toList() + (200..209).toList() + (100..109).toList() + (0..9).toList()
            assertEquals(expected.map(Int::toString), ids(session))
            assertEquals(original, rows(session).takeLast(10))
            assertEquals(3, database.timelineReadingSessionDao().segments(session.record.key).size)
            session.refresh(false)
            assertEquals(expected.map(Int::toString), ids(session))
            assertEquals(3, database.timelineReadingSessionDao().segments(session.record.key).size)
            session.close()
            val reopened = session(loader, backgroundScope, repository = repository)
            reopened.prepare()
            assertEquals(anchor, assertNotNull(reopened.state.value.position).itemKey)
            assertEquals(
                -33.125,
                reopened.state.value.position
                    ?.offset,
            )
            assertEquals(expected.map(Int::toString), ids(reopened))
            val originalSegments = database.timelineReadingSessionDao().segments(reopened.record.key)
            val other = session(Loader(), backgroundScope, repository = repository)
            val copiedSegments = database.timelineReadingSessionDao().segments(other.record.key)
            assertEquals(3, copiedSegments.size)
            assertTrue(copiedSegments.none { copied -> originalSegments.any { it.key == copied.key } })
            assertEquals(
                originalSegments.map { database.pagingTimelineDao().getPagingKey(it.key)?.nextKey }.toSet(),
                copiedSegments.map { database.pagingTimelineDao().getPagingKey(it.key)?.nextKey }.toSet(),
            )
        }

    @Test
    fun pagingUpFromTheSavedWindowReachesRefreshedPostsWithoutViewLatest() =
        runTest {
            val loader = Loader().apply { response = page(0..199) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val anchor = rows(session)[100]
            val key = "${session.activeKey}_${anchor.statusId}"
            savePosition(session, key, anchor.sortId, -21.5)
            loader.response = page(1000..1039)
            session.refresh(false)
            val source = TimelineReadingPagingSource(database, session)
            try {
                var page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Around(key), 40, false),
                    ) as PagingSource.LoadResult.Page
                assertTrue(page.data.any { it.baseItem.stableItemKey == key })
                var steps = 0
                while (page.prevKey != null) {
                    check(++steps < 10)
                    page =
                        source.load(PagingSource.LoadParams.Prepend(assertNotNull(page.prevKey), 40, false)) as PagingSource.LoadResult.Page
                }
                assertEquals(
                    "1000",
                    page.data
                        .first()
                        .baseItem.statusKey.id,
                )
                assertNull(session.state.value.position)
                assertEquals(key, snapshot(session).anchorId)
                assertEquals(-21.5, snapshot(session).offset)
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun gapLoadsAtMostTwoPagesAndRetainsItsCursorAcrossReopening() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val old = rows(session)
            loader.response = page(100..109, "gap-1")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            loader.beforeLoad = {
                loader.response =
                    when ((loader.requests.last() as PagingRequest.Append).nextKey) {
                        "gap-1" -> page(90..99, "gap-2")
                        "gap-2" -> page(80..89, "gap-3")
                        else -> page(70..79).copy(nextKey = null)
                    }
            }
            session.loadGap(segment.key)
            assertEquals(listOf(PagingRequest.Append("gap-1"), PagingRequest.Append("gap-2")), loader.requests.takeLast(2))
            assertEquals("gap-3", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
            assertEquals(
                (100..109).map(Int::toString) + (90..99).map(Int::toString) + (80..89).map(Int::toString) + (0..9).map(Int::toString),
                ids(session),
            )
            assertEquals(old, rows(session).takeLast(10))
            session.close()
            val reopened = session(loader, backgroundScope, repository = repository)
            reopened.loadGap(segment.key)
            assertEquals(PagingRequest.Append("gap-3"), loader.requests.last())
            assertTrue((70..79).all { it.toString() in ids(reopened) })
            assertTrue(database.timelineReadingSessionDao().segments(reopened.record.key).isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(segment.key).isEmpty(), "Completed source caches are released")
        }

    @Test
    fun mixedGapCommitFailureRollsBackChildCursorsAndVisibleRows() =
        runTest {
            val first = Loader("first")
            val second = Loader("second").apply { response = page(20..29) }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Staggered), backgroundScope)
            session.refresh(false, initial = true)
            first.response = page(100..109, "first-gap")
            second.response = page(120..129, "second-gap")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            val dao = database.pagingTimelineDao()
            val cursors = dao.keysWithPrefix(segment.key).associateWith { dao.getPagingKey(it) to dao.getByPagingKey(it) }
            val original = rows(session)
            first.response = page(90..99).copy(nextKey = null)
            second.response = page(110..119).copy(nextKey = null)
            afterPagingDelete = { key -> if (key == segment.key) error("commit failed") }
            session.loadGap(segment.key)
            assertEquals(original, rows(session))
            assertEquals(cursors, dao.keysWithPrefix(segment.key).associateWith { dao.getPagingKey(it) to dao.getByPagingKey(it) })
            assertEquals(segment.copy(failed = true), database.timelineReadingSessionDao().segments(session.record.key).single())
            afterPagingDelete = {}
            session.loadGap(segment.key)
            assertEquals(listOf(PagingRequest.Append("first-gap"), PagingRequest.Append("first-gap")), first.requests.takeLast(2))
            assertEquals(listOf(PagingRequest.Append("second-gap"), PagingRequest.Append("second-gap")), second.requests.takeLast(2))
            assertEquals(60, rows(session).size)
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
        }

    @Test
    fun failedGapKeepsSuccessfulPagesAndRetriesTheSameCursor() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(100..109, "gap-1")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            var fail = true
            loader.beforeLoad = {
                val cursor = (loader.requests.last() as PagingRequest.Append).nextKey
                if (cursor == "gap-2" && fail) error("offline")
                loader.response = if (cursor == "gap-1") page(90..99, "gap-2") else page(80..89).copy(nextKey = null)
            }
            session.loadGap(segment.key)
            assertTrue((90..99).all { it.toString() in ids(session) })
            assertEquals("gap-2", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 100, false),
                    ) as PagingSource.LoadResult.Page
                val gap =
                    page.data
                        .map { it.baseItem }
                        .filterIsInstance<UiTimelineV2.Message>()
                        .single()
                assertTrue(assertNotNull(gap.readingGap).failed)
            } finally {
                source.invalidate()
            }
            fail = false
            session.loadGap(segment.key)
            assertEquals(listOf(PagingRequest.Append("gap-2"), PagingRequest.Append("gap-2")), loader.requests.takeLast(2))
            assertEquals(40, ids(session).size)
            assertEquals(ids(session).size, ids(session).toSet().size)
        }

    @Test
    fun duplicateAndFilteredGapPagesAdvanceButStalledCursorsDoNotLoop() =
        runTest {
            var hideMiddle = false
            val loader = Loader()
            val session = session(loader, backgroundScope, visible = { !hideMiddle || it.statusKey.id.toInt() !in 80..99 })
            session.refresh(false, initial = true)
            loader.response = page(100..109, "gap-1")
            session.refresh(false)
            val key =
                database
                    .timelineReadingSessionDao()
                    .segments(session.record.key)
                    .single()
                    .key
            loader.beforeLoad = {
                loader.response =
                    when ((loader.requests.last() as PagingRequest.Append).nextKey) {
                        "gap-1" -> page(100..109, "gap-2")
                        else -> page(80..99, "gap-3")
                    }
            }
            hideMiddle = true
            session.loadGap(key)
            assertEquals("gap-3", database.pagingTimelineDao().getPagingKey(key)?.nextKey)
            assertEquals(40, ids(session).size)
            val before = loader.requests.size
            session.loadGap(key)
            assertEquals(before + 1, loader.requests.size, "A repeated cursor and page must stop after one attempt")
            assertEquals("gap-3", database.pagingTimelineDao().getPagingKey(key)?.nextKey)
            assertEquals(40, ids(session).size)
        }

    @Test
    fun changingFiltersRemovesOrphanGapsAndInvalidatesTheLoadedPage() =
        runTest {
            var hideLatest = false
            val loader = Loader()
            val session = session(loader, backgroundScope, visible = { !hideLatest || it.statusKey.id.toInt() < 100 })
            session.refresh(false, initial = true)
            loader.response = page(100..109)
            session.refresh(false)
            val source = TimelineReadingPagingSource(database, session)
            val params = PagingSource.LoadParams.Refresh<OffsetFromStartPagingKey>(OffsetFromStartPagingKey.Append(0), 40, false)
            val page = source.load(params) as PagingSource.LoadResult.Page
            assertTrue(page.data.any { it.baseItem.isReadingGap })
            val invalidated = CompletableDeferred<Unit>()
            source.registerInvalidatedCallback { invalidated.complete(Unit) }
            hideLatest = true
            session.filtersChanged()
            withContext(Dispatchers.Default) { assertNotNull(withTimeoutOrNull(5_000) { invalidated.await() }) }
            assertFalse(session.state.value.hasNewContent)
            assertTrue(session.withGapMarkers(page.data.filterNot { it.baseItem.isReadingGap }).none { it.baseItem.isReadingGap })
            hideLatest = false
            session.filtersChanged()
            assertTrue(session.withGapMarkers(page.data.filterNot { it.baseItem.isReadingGap }).any { it.baseItem.isReadingGap })
            assertEquals(2, loader.requests.size)
        }

    @Test
    fun filteredBoundaryStillLoadsWhenItsFirstVisiblePostIsReached() =
        runTest {
            val loader = Loader().apply { response = page(0..19) }
            val session = session(loader, backgroundScope, visible = { it.statusKey.id.toInt() >= 9 })
            session.refresh(false, initial = true)
            loader.response = page(100..109, "gap")
            session.refresh(false)
            loader.response = page(90..99).copy(nextKey = null)
            val generation = session.gapGeneration.value
            val visibleBoundary = rows(session).takeLast(20)[9]
            session.updateViewport(ReadingViewport("${session.activeKey}_${visibleBoundary.statusId}", -19.5, false, false))
            withContext(Dispatchers.Default) {
                assertNotNull(withTimeoutOrNull(5_000) { session.gapGeneration.first { it >= generation + 2 } })
            }
            assertEquals(PagingRequest.Append("gap"), loader.requests.last())
            assertTrue((90..99).all { it.toString() in ids(session) })
            assertNull(session.state.value.position)
        }

    @Test
    fun refreshingWithAGapAtTheViewportUsesTheFollowingPostAsAnchor() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val oldHead = rows(session).first()
            loader.response = page(100..109)
            session.refresh(false)
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 40, false),
                    ) as PagingSource.LoadResult.Page
                val gapIndex = page.data.indexOfFirst { it.baseItem.isReadingGap }
                assertTrue(gapIndex >= 0)
                assertEquals(
                    OffsetFromStartPagingKey.Around("${session.activeKey}_${oldHead.statusId}"),
                    source.getRefreshKey(
                        PagingState(listOf(page), gapIndex, dev.dimension.flare.data.datasource.microblog.offsetPagingConfig, 0),
                    ),
                )
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun longGapRebasesRanksWithoutLosingItsBoundaryOrSavedPost() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[7].statusId}"
            savePosition(session, anchor, original[7].sortId, -17.25)
            loader.response = page(1000..1009, "0")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            loader.beforeLoad = {
                val cursor = (loader.requests.last() as PagingRequest.Append).nextKey.toInt()
                loader.response = page(100 + cursor * 10..109 + cursor * 10, (cursor + 1).toString())
            }
            repeat(8) { session.loadGap(segment.key) }
            val savedSegment = database.timelineReadingSessionDao().segments(session.record.key).single()
            val boundary = rows(session).first { it.statusId == segment.boundaryId }
            assertNotEquals(original.first().sortId, boundary.sortId, "The fixture must exhaust the available ranks")
            assertEquals(boundary.sortId, savedSegment.boundarySortId)
            assertEquals(original.map { it._id }, rows(session).takeLast(original.size).map { it._id })
            assertEquals(rows(session).first { it._id == original[7]._id }.sortId, snapshot(session).anchorSortId)
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-17.25, snapshot(session).offset)
            assertEquals(180, ids(session).size)
            assertEquals("16", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
        }

    @Test
    fun strictlyTimeOrderedRecoveryInsertsOlderPostsAtTheirTimestamp() =
        runTest {
            fun timed(vararg values: Pair<Int, Long>) =
                PagingResult<UiTimelineV2>(
                    data =
                        values.map { (id, time) ->
                            post(id).copy(createdAt = Instant.fromEpochMilliseconds(time).toUi())
                        },
                )
            val first = Loader("first").apply { response = timed(1 to 300, 3 to 100) }
            val second = Loader("second").apply { beforeLoad = { error("offline") } }
            val session = session(MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original.last().statusId}"
            savePosition(session, anchor, original.last().sortId, -15.75)
            second.beforeLoad = {}
            second.response = timed(2 to 200, 4 to 50)
            session.refresh(false)
            assertEquals(listOf("1", "2", "3", "4"), ids(session))
            assertEquals(
                original.map { it._id to it.sortId },
                rows(session).filter { it.statusId in original.map { it.statusId } }.map {
                    it._id to
                        it.sortId
                },
            )
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-15.75, snapshot(session).offset)
            assertNull(session.state.value.position)
        }

    @Test
    fun overlappingRefreshClosesTheGapAndPreservesTheBookmark() =
        runTest {
            val loader = Loader().apply { response = page(0..39) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val anchor = rows(session)[7]
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -19.25)
            loader.response = page(-3..36, "refresh-cursor")
            session.refresh(false)
            assertEquals((-3..39).map(Int::toString), ids(session))
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertEquals(listOf(session.activeKey), database.pagingTimelineDao().keysWithPrefix(session.record.key))
            assertEquals(anchor, rows(session).first { it.statusId == anchor.statusId })
            assertEquals(-19.25, snapshot(session).offset)
            assertNull(session.state.value.position)
        }

    @Test
    fun gapCrossingTheOldWindowKeepsEveryPostInOrder() = gapCrossingOldWindow(prepend = true)

    @Test
    fun gapCrossingTheOldWindowWithoutForwardCursorKeepsEveryPostInOrder() = gapCrossingOldWindow(prepend = false)

    private fun gapCrossingOldWindow(prepend: Boolean) =
        runTest {
            val loader = Loader(prepend = prepend)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            loader.response = page(-50..-11, "refresh-cursor")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            loader.response = page(-10..29).copy(nextKey = null)
            session.loadGap(segment.key)
            assertEquals((-50..29).map(Int::toString), ids(session))
            assertEquals(original, rows(session).filter { it.statusId in original.map { row -> row.statusId } })
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(segment.key).isEmpty())
        }

    @Test
    fun rankedRefreshKeepsNewPostsAfterOverlapAndContinuesAtTheTail() =
        runTest {
            val loader = Loader().apply { response = page(0..1) }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Staggered), backgroundScope)
            session.refresh(false, initial = true)
            val anchor = rows(session).first()
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -12.5)
            loader.response = PagingResult(listOf(post(0), post(2), post(1)), nextKey = "ranked-next")
            session.refresh(false)
            assertEquals(listOf("2", "0", "1"), ids(session))
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            loader.response = PagingResult(listOf(post(1), post(3)), nextKey = null)
            session.loadGap(segment.key)
            assertEquals(listOf("2", "0", "1", "3"), ids(session))
            assertEquals(anchor, rows(session).first { it.statusId == anchor.statusId })
            assertEquals(-12.5, snapshot(session).offset)
            assertNull(session.state.value.position)
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
        }

    @Test
    fun repeatedOverlappingRefreshesReleaseTheirSourceCaches() =
        runTest {
            val loader = Loader().apply { response = page(0..39) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            repeat(20) { index ->
                val start = -3 * (index + 1)
                loader.response = page(start..(start + 39), "refresh-$index")
                session.refresh(false)
                assertEquals((start..39).map(Int::toString), ids(session))
                assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
                assertEquals(listOf(session.activeKey), database.pagingTimelineDao().keysWithPrefix(session.record.key))
            }
        }

    @Test
    fun openGapRetainsOnlyTheLastSourcePageWhileKeepingAllVisiblePosts() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(1000..1009, "0")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            loader.beforeLoad = {
                val cursor = (loader.requests.last() as PagingRequest.Append).nextKey.toInt()
                loader.response = page(100 + cursor * 10..109 + cursor * 10, (cursor + 1).toString())
            }
            repeat(5) { session.loadGap(segment.key) }
            assertEquals(120, rows(session).size)
            assertEquals(10, database.pagingTimelineDao().getByPagingKey(segment.key).size)
            assertEquals("10", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
        }

    @Test
    fun pagerCancellationAndSessionCloseReleaseAllCollectors() =
        runTest {
            val loader = Loader().apply { response = page(0..9).copy(nextKey = null) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val presenter =
                object : PagingDataPresenter<TimelinePageItem>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<TimelinePageItem>) = Unit
                }
            val collector = backgroundScope.launch { session.pagingData.collectLatest(presenter::collectFrom) }
            presenter.loadStateFlow.filterNotNull().first { it.isIdle && it.append.endOfPaginationReached }
            collector.cancel()
            collector.join()
            val source = TimelineReadingPagingSource(database, session)
            try {
                session.close()
                withContext(Dispatchers.Default) {
                    assertNotNull(withTimeoutOrNull(1_000) { session.gapGeneration.subscriptionCount.first { it == 0 } })
                    assertNotNull(withTimeoutOrNull(1_000) { session.state.subscriptionCount.first { it == 0 } })
                }
                assertTrue(source.invalid)
            } finally {
                source.invalidate()
                session.gapGeneration.value += 1
            }
        }

    @Test
    fun offsetOnlyScrollUpdatesDoNotStartBoundaryTransactions() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val session = session(Loader(), backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val anchor = "${session.activeKey}_${rows(session).first().statusId}"
            session.updateViewport(ReadingViewport(anchor, 0.0, atTop = false, interacting = true))
            repository.selectedTab.first { it == "home" }
            testScheduler.runCurrent()
            var transactions = 0
            afterCommit = { transactions += 1 }
            repeat(60) { offset ->
                session.updateViewport(ReadingViewport(anchor, -offset.toDouble(), atTop = false, interacting = true))
                testScheduler.runCurrent()
            }
            afterCommit = {}
            assertEquals(0, transactions)
        }

    @Test
    fun continuousRefreshFillsHolesBeyondTheFirstOverlapWithoutLosingTheAnchor() =
        runTest {
            val loader = Loader().apply { response = PagingResult(listOf(post(0), post(2), post(4)), nextKey = "old") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[1].statusId}"
            savePosition(session, anchor, original[1].sortId, -8.75)
            loader.response = page(-1..5)
            session.refresh(false)
            assertEquals((-1..5).map(Int::toString), ids(session))
            assertTrue(original.all { old -> rows(session).any { it._id == old._id } })
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-8.75, snapshot(session).offset)
            assertEquals(rows(session).first { it.statusId == original[1].statusId }.sortId, snapshot(session).anchorSortId)
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
        }

    @Test
    fun rankedOverlappingFirstPageKeepsItsDistinctCursorAndDeduplicatesRepeatedRefreshes() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "old") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(0..1, "new")
            repeat(5) { session.refresh(false) }
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            assertEquals(listOf("0", "1"), ids(session))
            assertFalse(session.state.value.hasNewContent)
            loader.response = PagingResult(listOf(post(1), post(2)), nextKey = null)
            session.loadGap(segment.key)
            assertEquals(listOf("0", "1", "2"), ids(session))
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertTrue(database.pagingTimelineDao().keysWithPrefix(segment.key).isEmpty())
        }

    @Test
    fun rankedFooterStaysAtTheTailAndRefreshesAroundThePreviousPost() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..9) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(100..109, "new")
            session.refresh(false)
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 100, false),
                    ) as PagingSource.LoadResult.Page
                assertTrue(
                    page.data
                        .last()
                        .baseItem.isReadingGap,
                )
                assertEquals(
                    "9",
                    page.data[page.data.lastIndex - 1]
                        .baseItem.statusKey.id,
                )
                assertEquals(
                    OffsetFromStartPagingKey.Around("${session.activeKey}_${rows(session).last().statusId}"),
                    source.getRefreshKey(
                        PagingState(listOf(page), page.data.lastIndex, dev.dimension.flare.data.datasource.microblog.offsetPagingConfig, 0),
                    ),
                )
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun rankedAppendInterleavesTheOriginalAndRefreshedTimeline() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = PagingResult(listOf(post(0), post(4)), nextKey = "fresh")
            session.refresh(false)
            loader.beforeLoad = {
                loader.response =
                    when ((loader.requests.last() as PagingRequest.Append).nextKey) {
                        "fresh" -> PagingResult(listOf(post(1), post(5)), nextKey = "fresh-2")
                        "fresh-2" -> PagingResult(listOf(post(5), post(6)))
                        "original" -> PagingResult(listOf(post(2), post(3)))
                        else -> error("Unexpected cursor")
                    }
            }
            val state =
                PagingState<OffsetFromStartPagingKey, TimelinePageItem>(
                    emptyList(),
                    null,
                    dev.dimension.flare.data.datasource.microblog.offsetPagingConfig,
                    0,
                )
            session.load(androidx.paging.LoadType.APPEND, state)
            assertEquals(listOf("4", "0", "1", "2", "3"), ids(session))
            session.load(androidx.paging.LoadType.APPEND, state)
            assertEquals(listOf("4", "0", "1", "2", "3", "5"), ids(session))
            session.load(androidx.paging.LoadType.APPEND, state)
            assertEquals(listOf("4", "0", "1", "2", "3", "5", "6"), ids(session))
            assertEquals(
                listOf("original", "fresh", "fresh-2"),
                loader.requests.filterIsInstance<PagingRequest.Append>().map { it.nextKey },
            )
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertEquals(listOf(session.activeKey), database.pagingTimelineDao().keysWithPrefix(session.record.key))
        }

    @Test
    fun idleLayoutUpdatesDoNotCancelAnAutomaticGapLoad() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(100..109, "gap")
            session.refresh(false)
            val started = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            loader.beforeLoad = {
                started.complete(Unit)
                try {
                    resume.await()
                } catch (error: CancellationException) {
                    cancelled.complete(Unit)
                    throw error
                }
            }
            loader.response = page(90..99).copy(nextKey = null)
            val viewport = ReadingViewport("${session.activeKey}_${rows(session)[10].statusId}", -19.5, false, false)
            session.updateViewport(viewport)
            withContext(Dispatchers.Default) { assertNotNull(withTimeoutOrNull(5_000) { started.await() }) }
            session.updateViewport(viewport.copy(offset = -20.0))
            withContext(Dispatchers.Default) { assertNull(withTimeoutOrNull(500) { cancelled.await() }) }
            resume.complete(Unit)
            withContext(Dispatchers.Default) {
                assertNotNull(
                    withTimeoutOrNull(5_000) {
                        session.gapGeneration.first { (90..99).all { id -> id.toString() in ids(session) } }
                    },
                )
            }
            assertEquals(1, loader.requests.filterIsInstance<PagingRequest.Append>().size)
            assertNull(session.state.value.position)
        }

    @Test
    fun continuousAppendFillsHolesInTheRefreshedTailWithoutMovingTheBookmark() =
        runTest {
            val loader = Loader().apply { response = page(0..1, "old") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val anchorRow = rows(session).last()
            val anchor = "${session.activeKey}_${anchorRow.statusId}"
            savePosition(session, anchor, anchorRow.sortId, -8.75)
            loader.response = PagingResult(listOf(post(-1), post(0), post(1), post(3)), nextKey = "fresh")
            session.refresh(false)
            assertEquals(listOf("-1", "0", "1", "3"), ids(session))
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            loader.response = page(2..4).copy(nextKey = null)
            val state =
                PagingState<OffsetFromStartPagingKey, TimelinePageItem>(
                    emptyList(),
                    null,
                    dev.dimension.flare.data.datasource.microblog.offsetPagingConfig,
                    0,
                )
            session.load(androidx.paging.LoadType.APPEND, state)
            assertEquals(PagingRequest.Append("old"), loader.requests.last())
            assertEquals((-1..4).map(Int::toString), ids(session))
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-8.75, snapshot(session).offset)
            assertNull(database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
        }

    @Test
    fun timeMixedAppendChecksOnlyItsCandidatesAndKeepsUnseenPosts() =
        runTest {
            fun timedPage(range: IntRange) = range.map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(it.toLong()).toUi()) }
            val loader = Loader().apply { response = PagingResult(timedPage(0..49), nextKey = "old") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            assertEquals(40, ids(session).size)
            loader.response = PagingResult(timedPage(40..59))
            var fullScans = 0
            onPagingRowsRead = { key -> if (key == session.activeKey) fullScans += 1 }
            val state =
                PagingState<OffsetFromStartPagingKey, TimelinePageItem>(
                    emptyList(),
                    null,
                    dev.dimension.flare.data.datasource.microblog.offsetPagingConfig,
                    0,
                )
            session.load(androidx.paging.LoadType.APPEND, state)
            onPagingRowsRead = {}
            assertEquals(0, fullScans)
            assertEquals((59 downTo 0).map(Int::toString), ids(session))
            assertEquals(PagingRequest.Append("old"), loader.requests.last())
            assertNull(database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            database.pagingTimelineDao().keysWithPrefix(session.activeKey).filter { it.endsWith("staging") }.forEach {
                assertTrue(database.pagingTimelineDao().getByPagingKey(it).isEmpty())
            }
        }

    @Test
    fun ordinaryRefreshDoesNotReadAllRetainedTimelineRows() =
        runTest {
            val loader = Loader().apply { response = page(0..199) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            var fullScans = 0
            onPagingRowsRead = { key -> if (key == session.activeKey) fullScans += 1 }
            loader.response = page(-3..36)
            session.refresh(false)
            onPagingRowsRead = {}
            assertEquals(0, fullScans)
            assertEquals((-3..199).map(Int::toString), ids(session))
        }

    @Test
    fun shorterForwardProbeDoesNotDiscardPostsFromTheRefreshPage() =
        runTest {
            val loader = Loader(prepend = true)
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(-3..6)
            loader.forward = page(-3..-2)
            session.refresh(false)
            assertEquals((-3..9).map(Int::toString), ids(session))
            assertTrue(database.timelineReadingSessionDao().segments(session.record.key).isEmpty())
            assertNull(session.state.value.position)
        }

    @Test
    fun rotatingRankedCursorsKeepAllContinuationsWithoutDuplicatingStoredPages() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..39, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[20].statusId}"
            savePosition(session, anchor, original[20].sortId, -17.25)
            repeat(5) { index ->
                loader.response = page(0..39, "refresh-$index")
                session.refresh(false)
            }
            assertEquals(5, database.timelineReadingSessionDao().segments(session.record.key).size)
            assertEquals(
                40,
                database.pagingTimelineDao().keysWithPrefix(session.record.key).sumOf {
                    database.pagingTimelineDao().getByPagingKey(it).size
                },
            )
            var page = 100
            loader.beforeLoad = {
                loader.response = PagingResult(listOf(post(page++)), nextKey = "continued-$page")
            }
            repeat(6) { append(session) }
            assertEquals(listOf("original") + (0..4).map { "refresh-$it" }, appendCursors(loader))
            assertEquals(((0..39) + (100..105)).map(Int::toString), ids(session))
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-17.25, snapshot(session).offset)
            assertNull(session.state.value.position)
            assertEquals(original, rows(session).take(40))
            assertEquals(
                46,
                database.pagingTimelineDao().keysWithPrefix(session.record.key).sumOf {
                    database.pagingTimelineDao().getByPagingKey(it).size
                },
            )
        }

    @Test
    fun rankedRotationSurvivesReopeningAndConcurrentReaders() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "original") }
            val first = session(loader, backgroundScope, repository = repository)
            first.refresh(false, initial = true)
            loader.response = page(10..11, "first")
            first.refresh(false)
            loader.response = page(20..21, "second")
            first.refresh(false)
            loader.beforeLoad = {
                loader.response =
                    PagingResult(listOf(post(100 + appendCursors(loader).size)), nextKey = "continued-${appendCursors(loader).size}")
            }
            append(first)
            first.close()
            val restored = session(loader, backgroundScope, repository = repository)
            restored.prepare()
            val other = session(loader, backgroundScope, repository = repository)
            append(restored)
            append(other)
            append(restored)
            assertEquals(listOf("original", "first", "first", "second"), appendCursors(loader))
            assertEquals(listOf("20", "21", "10", "11", "0", "1", "101", "102", "104"), ids(restored))
            assertEquals(listOf("20", "21", "10", "11", "0", "1", "101", "103"), ids(other))
        }

    @Test
    fun newRankedRefreshesAndFailuresDoNotStarveExistingCursors() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            repeat(3) { index ->
                loader.beforeLoad = {}
                loader.response = page(0..1, "refresh-$index")
                session.refresh(false)
                loader.beforeLoad = { error("offline") }
                assertTrue(append(session) is androidx.paging.RemoteMediator.MediatorResult.Error)
            }
            assertEquals(listOf("original", "refresh-0", "original"), appendCursors(loader))
            loader.beforeLoad = { loader.response = PagingResult(listOf(post(99))) }
            append(session)
            assertEquals("refresh-1", appendCursors(loader).last())
            assertEquals(listOf("0", "1", "99"), ids(session))
            val source = TimelineReadingPagingSource(database, session)
            try {
                val page =
                    source.load(
                        PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 100, false),
                    ) as PagingSource.LoadResult.Page
                assertTrue((page.data.last().baseItem as UiTimelineV2.Message).readingGap?.failed == true)
            } finally {
                source.invalidate()
            }
            assertEquals(3, database.timelineReadingSessionDao().cursors(session.record.key).size)
        }

    @Test
    fun rankedStalledPageIsDetectedAfterItsRawRowsAreReclaimed() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(0..1, "fresh")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            assertTrue(database.pagingTimelineDao().getByPagingKey(segment.key).isEmpty())
            assertNotNull(session.loadGap(segment.key))
            assertEquals("fresh", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
            loader.response = PagingResult(listOf(post(2)), nextKey = "fresh")
            assertNull(session.loadGap(segment.key))
            assertEquals(listOf("0", "1", "2"), ids(session))
            assertNotNull(session.loadGap(segment.key))
            assertEquals(3, rows(session).size)
        }

    @Test
    fun rankedReorderedCollectionRetainsUnseenPostsBehindAnOverlappingPage() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..1, "old") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = "${session.activeKey}_${original[1].statusId}"
            savePosition(session, anchor, original[1].sortId, -8.5)
            loader.response = PagingResult(listOf(post(1), post(0)), nextKey = "new-collection")
            session.refresh(false)
            loader.beforeLoad = {
                loader.response =
                    if ((loader.requests.last() as PagingRequest.Append).nextKey == "new-collection") {
                        PagingResult(listOf(post(99)))
                    } else {
                        PagingResult()
                    }
            }
            repeat(2) { append(session) }
            assertEquals(listOf("old", "new-collection"), appendCursors(loader))
            assertEquals(listOf("0", "1", "99"), ids(session))
            assertEquals(original, rows(session).take(2))
            assertEquals(anchor, snapshot(session).anchorId)
            assertEquals(-8.5, snapshot(session).offset)
        }

    @Test
    fun rankedMixedCursorsRetainUnconsumedStagingAfterRawPagesAreReclaimed() =
        runTest {
            fun timed(range: IntRange) = range.map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(it.toLong()).toUi()) }
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = PagingResult(timed(0..49), nextKey = "old") }
            val session = session(MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time), backgroundScope)
            session.refresh(false, initial = true)
            loader.response = PagingResult(timed(0..49), nextKey = "fresh")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            assertTrue(database.pagingTimelineDao().getByPagingKey(segment.key).isEmpty())
            val staging = database.pagingTimelineDao().keysWithPrefix(segment.key).filter { it.endsWith("staging") }
            assertEquals(10, staging.sumOf { database.pagingTimelineDao().getByPagingKey(it).size })
            loader.beforeLoad = {
                val cursor = (loader.requests.last() as PagingRequest.Append).nextKey
                loader.response = PagingResult(timed(if (cursor == "old") 50..59 else 100..109))
            }
            repeat(2) { append(session) }
            assertEquals(listOf("old", "fresh"), appendCursors(loader))
            assertEquals(((109 downTo 100) + (59 downTo 0)).map(Int::toString), ids(session))
            assertTrue(database.pagingTimelineDao().keysWithPrefix(segment.key).isEmpty())
        }

    @Test
    fun initialAndForwardPagesLeaveRoomForHolesWithoutRewritingHistory() =
        runTest {
            val loader = Loader(prepend = true).apply { response = page(0..399) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(-40..-1)
            loader.forward = loader.response
            session.refresh(false)
            val before = rows(session)
            val anchor = before[240]
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -16.25)
            var fullScans = 0
            onPagingRowsRead = { if (it == session.activeKey) fullScans++ }
            loader.forward = PagingResult()
            loader.response = PagingResult(listOf(post(-20), post(998), post(-19), post(199), post(999), post(200)))
            session.refresh(false)
            onPagingRowsRead = {}
            val after = rows(session).associateBy { it._id }
            assertEquals(0, fullScans)
            assertTrue(before.all { after[it._id]?.sortId == it.sortId })
            assertEquals(((-40..-20) + listOf(998) + (-19..199) + listOf(999) + (200..399)).map(Int::toString), ids(session))
            assertEquals("${session.activeKey}_${anchor.statusId}", snapshot(session).anchorId)
            assertEquals(-16.25, snapshot(session).offset)
            assertNull(session.state.value.position)
        }

    @Test
    fun forwardPageKeepsFinalRanksWhenALaterHoleRebasesEarlierInsertions() =
        runTest {
            val loader = Loader(prepend = true).apply { response = page(0..4).copy(data = listOf(post(0), post(2), post(4))) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val dao = database.pagingTimelineDao()
            dao.insertAll(rows(session).mapIndexed { index, row -> row.copy(sortId = index.toLong()) })
            val anchor = rows(session)[1]
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -18.75)
            loader.response = page(-1..2)
            loader.forward = loader.response
            session.refresh(false)
            val after = rows(session)
            assertEquals(listOf("-1", "0", "1", "2", "4"), ids(session))
            assertEquals(after.size, after.map { it.sortId }.toSet().size)
            assertEquals("${session.activeKey}_${anchor.statusId}", snapshot(session).anchorId)
            assertEquals(after.single { it._id == anchor._id }.sortId, snapshot(session).anchorSortId)
            assertEquals(-18.75, snapshot(session).offset)
            assertNull(session.state.value.position)
        }

    @Test
    fun denseHoleRebasesOnlyItsNeighboursAndKeepsTheBookmark() =
        runTest {
            val loader = Loader().apply { response = page(0..399) }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val dao = database.pagingTimelineDao()
            val initial = rows(session)
            dao.insertAll(initial.slice(190..210).mapIndexed { index, row -> row.copy(sortId = initial[190].sortId + index) })
            val before = rows(session)
            val anchor = before[200]
            savePosition(session, "${session.activeKey}_${anchor.statusId}", anchor.sortId, -19.75)
            var fullScans = 0
            onPagingRowsRead = { if (it == session.activeKey) fullScans++ }
            loader.response = PagingResult(listOf(post(199), post(999), post(200)))
            session.refresh(false)
            onPagingRowsRead = {}
            val after = rows(session).associateBy { it._id }
            assertEquals(0, fullScans)
            assertTrue(before.count { after[it._id]?.sortId != it.sortId } in 1..64)
            assertEquals(((0..199) + listOf(999) + (200..399)).map(Int::toString), ids(session))
            assertEquals("${session.activeKey}_${anchor.statusId}", snapshot(session).anchorId)
            assertEquals(after.getValue(anchor._id).sortId, snapshot(session).anchorSortId)
            assertEquals(-19.75, snapshot(session).offset)
        }

    @Test
    fun distinctRankedRefreshesRetainOnlyVisibleRowsAndCursorCheckpoints() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..39, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            repeat(5) { index ->
                val start = -(index + 1) * 40
                loader.response = page(start..start + 39, "refresh-$index")
                session.refresh(false)
            }
            assertEquals((-200..39).map(Int::toString), ids(session))
            assertEquals(5, database.timelineReadingSessionDao().segments(session.record.key).size)
            assertEquals(6, database.timelineReadingSessionDao().cursors(session.record.key).size)
            assertEquals(
                240,
                database.pagingTimelineDao().keysWithPrefix(session.record.key).sumOf {
                    database.pagingTimelineDao().getByPagingKey(it).size
                },
            )
        }

    @Test
    fun reopeningTimeMergedCacheReadsOnlyTheVisibleWindow() = cachedReopenDoesNotMaintainContinuations(TimelineMergePolicy.Time)

    @Test
    fun reopeningPageMergedCacheReadsOnlyTheVisibleWindow() = cachedReopenDoesNotMaintainContinuations(TimelineMergePolicy.TimePerPage)

    private fun cachedReopenDoesNotMaintainContinuations(policy: TimelineMergePolicy) =
        runTest {
            val loaders =
                (0..2).map { source ->
                    Loader(pagingKey = "source-$source").apply { response = page(source * 40 until (source + 1) * 40, "original") }
                }
            val mixed = MixedRemoteMediator(database, loaders, policy)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            val selects = mutableListOf<Int>()
            val rawReads = mutableListOf<String>()
            for (refreshes in listOf(0, 32)) {
                repeat(refreshes) { turn ->
                    loaders.forEach { it.response = it.response.copy(nextKey = "refresh-$turn") }
                    current.refresh(false)
                }
                val original = rows(current)
                val anchor = "${current.activeKey}_${original[20].statusId}"
                savePosition(current, anchor, original[20].sortId, -31.5)
                val cursors = database.timelineReadingSessionDao().cursors(current.record.key)
                val requests = loaders.sumOf { it.requests.size }
                current.close()
                current = session(mixed, backgroundScope, repository = repository)
                var count = 0
                onSelect = { count++ }
                onPagingRowsRead = { rawReads += it }
                current.prepare()
                val source = TimelineReadingPagingSource(database, current)
                try {
                    val page = source.load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
                    assertEquals(anchor, page.data[20].baseItem.stableItemKey)
                } finally {
                    source.invalidate()
                    onSelect = {}
                    onPagingRowsRead = {}
                }
                selects += count
                assertEquals(original, rows(current))
                assertEquals(cursors, database.timelineReadingSessionDao().cursors(current.record.key))
                assertEquals(
                    anchor,
                    current.state.value.position
                        ?.itemKey,
                )
                assertEquals(
                    -31.5,
                    current.state.value.position
                        ?.offset,
                )
                assertEquals(requests, loaders.sumOf { it.requests.size })
            }
            current.close()
            assertTrue(selects.last() <= selects.first() + 10, "Cached startup must not scan each continuation: $selects")
            assertTrue(rawReads.isEmpty(), "Cached startup must not read staging or whole history: $rawReads")
        }

    @Test
    fun consumingNestedStagingCountsAsProgressEvenWhenOuterPageAndCursorsRepeat() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..39, "old") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(0..39, "fresh")
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            val staging = "${segment.key}source/staging"
            val dao = database.pagingTimelineDao()
            dao.insertPagingKey(DbPagingKey(staging, "child-next"))
            dao.insertAll(rows(session).map { it.copy(pagingKey = staging, _id = DbPagingTimeline.createId(staging, it.statusId)) })
            loader.response = loader.response.copy(saveInTransaction = { dao.delete(staging) })
            assertNull(session.loadGap(segment.key))
            assertEquals((0..39).map(Int::toString), ids(session))
            assertEquals("fresh", dao.getPagingKey(segment.key)?.nextKey)
            assertNotNull(session.loadGap(segment.key), "Once staging also stops changing, the page really is stalled")
        }

    @Test
    fun rankedRefreshQueryCountDoesNotGrowWithIndependentCursors() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..3, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val anchor = original[2]
            savePosition(session, anchor._id, anchor.sortId, -12.5)
            val queries = mutableListOf<Int>()
            repeat(25) { index ->
                loader.response = page(0..3, "refresh-$index")
                var count = 0
                onSelect = { count++ }
                session.refresh(false)
                onSelect = {}
                queries += count
            }
            assertTrue(queries.takeLast(5).max() <= queries.drop(1).take(5).max() + 4, "Queries grew with cursor count: $queries")
            assertEquals(26, database.timelineReadingSessionDao().cursors(session.record.key).size)
            assertEquals(original, rows(session))
            assertEquals(anchor._id, snapshot(session).anchorId)
            assertEquals(-12.5, snapshot(session).offset)
            loader.beforeLoad = {
                loader.response = PagingResult(listOf(post(100 + appendCursors(loader).size)))
            }
            repeat(26) { append(session) }
            assertEquals(listOf("original") + (0..24).map { "refresh-$it" }, appendCursors(loader))
            assertEquals(((0..3) + (101..126)).map(Int::toString), ids(session))
        }

    @Test
    fun rankedCursorEquivalenceTracksAdvancementAndHashCollisions() =
        runTest {
            assertEquals("Aa".hashCode(), "BB".hashCode())
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..3, "Aa") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(0..3, "BB")
            session.refresh(false)
            assertEquals(2, database.timelineReadingSessionDao().cursors(session.record.key).size)
            loader.response = page(0..3, "BB")
            session.refresh(false)
            assertEquals(2, database.timelineReadingSessionDao().cursors(session.record.key).size)
            loader.response = page(4..7, "advanced")
            append(session)
            assertEquals(listOf("Aa"), appendCursors(loader))
            loader.response = page(0..3, "Aa")
            session.refresh(false)
            assertEquals(3, database.timelineReadingSessionDao().cursors(session.record.key).size, "The original Aa cursor has advanced")
            loader.response = page(0..3, "advanced")
            session.refresh(false)
            assertEquals(3, database.timelineReadingSessionDao().cursors(session.record.key).size)
            assertEquals((0..7).map(Int::toString), ids(session))
        }

    @Test
    fun mixedRefreshReclaimsPublishedStagingWithoutDroppingIndependentTails() = mixedPublishedStaging(nested = false)

    @Test
    fun nestedMixedRefreshReclaimsPublishedStagingWithoutDroppingIndependentTails() = mixedPublishedStaging(nested = true)

    private fun mixedPublishedStaging(nested: Boolean) =
        runTest {
            fun timed(range: IntRange) = range.map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(-it.toLong()).toUi()) }
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = PagingResult(timed(0..119), nextKey = "original") }
            val mixed = MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time)
            val session =
                session(if (nested) MixedRemoteMediator(database, listOf(mixed), TimelineMergePolicy.Time) else mixed, backgroundScope)
            session.refresh(false, initial = true)
            repeat(2) { append(session) }
            assertEquals((0..119).map(Int::toString), ids(session))
            val original = rows(session)
            val anchor = original[70]
            savePosition(session, anchor._id, anchor.sortId, -21.25)
            repeat(10) { index ->
                loader.response = PagingResult(timed(0..119), nextKey = "refresh-$index")
                session.refresh(false)
            }
            val staging = database.pagingTimelineDao().keysWithPrefix(session.record.key).filter { it.endsWith("staging") }
            assertEquals(
                0,
                staging.sumOf {
                    database.pagingTimelineDao().getByPagingKey(it).size
                },
                "Already published rows must not accumulate in child staging",
            )
            assertEquals(11, database.timelineReadingSessionDao().cursors(session.record.key).size)
            assertEquals(original.map { it._id to it.sortId }, rows(session).map { it._id to it.sortId })
            loader.beforeLoad = {
                loader.response = PagingResult(timed(200 + appendCursors(loader).size..200 + appendCursors(loader).size))
            }
            repeat(11) { append(session) }
            assertEquals(listOf("original") + (0..9).map { "refresh-$it" }, appendCursors(loader))
            assertEquals(((0..119) + (201..211)).map(Int::toString), ids(session))
            assertEquals(anchor._id, snapshot(session).anchorId)
            assertEquals(-21.25, snapshot(session).offset)
        }

    @Test
    fun rankedCursorIndexReloadsTheCommittedStateAfterRollback() =
        runTest {
            val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = page(0..3, "original") }
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(0..3, "fresh")
            session.refresh(false)
            session.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(session.record.key).single()
            val original = rows(session)
            afterPagingDelete = { if (it == segment.key) error("Roll back continuation after checkpoint update") }
            loader.response = page(4..7, "advanced")
            assertNotNull(session.loadGap(segment.key))
            afterPagingDelete = {}
            assertEquals("fresh", database.pagingTimelineDao().getPagingKey(segment.key)?.nextKey)
            assertEquals(original, rows(session))
            loader.response = page(0..3, "fresh")
            session.refresh(false)
            assertEquals(2, database.timelineReadingSessionDao().cursors(session.record.key).size)
            loader.response = page(0..3, "advanced")
            session.refresh(false)
            assertEquals(3, database.timelineReadingSessionDao().cursors(session.record.key).size)
        }

    @Test
    fun reopeningMixedSessionReclaimsOnlyTheSelectedCursorBeforeContinuing() = mixedReopenContinuesFairly()

    @Test
    fun failedMaintenanceAfterReopeningDoesNotLoseRowsOrStarveOtherCursors() =
        mixedReopenContinuesFairly(IllegalStateException("Staging read failed"))

    @Test
    fun maintenanceFailureAfterDeletingStagingRollsBackBeforeContinuing() =
        mixedReopenContinuesFairly(IllegalStateException("Staging deletion failed"), afterDelete = true)

    @Test
    fun maintenanceCancellationAfterDeletingStagingRollsBackBeforeContinuing() =
        mixedReopenContinuesFairly(CancellationException("Staging deletion cancelled"), afterDelete = true)

    private fun mixedReopenContinuesFairly(
        maintenanceFailure: Exception? = null,
        afterDelete: Boolean = false,
    ) = runTest {
        fun timed(range: IntRange) = range.map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(-it.toLong()).toUi()) }
        val loader = Loader(timelineOrder = TimelineOrder.Ranked).apply { response = PagingResult(timed(0..49), nextKey = "original") }
        val mixed = MixedRemoteMediator(database, listOf(loader), TimelineMergePolicy.Time)
        val repository = TimelineReadingRepository(database, backgroundScope)
        val first = session(mixed, backgroundScope, repository = repository)
        first.refresh(false, initial = true)
        loader.response = PagingResult(timed(0..49), nextKey = "fresh")
        first.refresh(false)
        val original = rows(first)
        val anchor = "${first.activeKey}_${original[20].statusId}"
        savePosition(first, anchor, original[20].sortId, -15.25)
        val dao = database.pagingTimelineDao()
        val staging = dao.keysWithPrefix(first.record.key).filter { it.endsWith("staging") }
        staging.forEach { key ->
            dao.insertAll(original.map { it.copy(pagingKey = key, _id = DbPagingTimeline.createId(key, it.statusId)) })
        }
        val stagedRows = staging.associateWith { dao.getByPagingKey(it) }
        val stagedCursors = staging.associateWith { dao.getPagingKey(it) }
        first.close()
        val restored = session(mixed, backgroundScope, repository = repository)
        restored.prepare()
        assertEquals(stagedRows, staging.associateWith { dao.getByPagingKey(it) }, "First paint must not maintain staging")
        assertEquals(original.map { it._id to it.sortId }, rows(restored).map { it._id to it.sortId })
        assertEquals(
            anchor,
            restored.state.value.position
                ?.itemKey,
        )
        assertEquals(
            -15.25,
            restored.state.value.position
                ?.offset,
        )
        loader.beforeLoad = {
            val cursor = (loader.requests.last() as PagingRequest.Append).nextKey
            loader.response = PagingResult(timed(if (cursor == "original") 100..109 else 200..209))
        }
        if (maintenanceFailure != null) {
            var reachedFailure = false
            val fail: (String) -> Unit = {
                if (it.endsWith("staging")) {
                    reachedFailure = true
                    throw maintenanceFailure
                }
            }
            if (afterDelete) afterPagingRowsDelete = fail else onPagingRowsRead = fail
            try {
                if (maintenanceFailure is CancellationException) {
                    assertFailsWith<CancellationException> { append(restored) }
                } else {
                    assertTrue(append(restored) is androidx.paging.RemoteMediator.MediatorResult.Error)
                }
            } finally {
                onPagingRowsRead = {}
                afterPagingRowsDelete = {}
            }
            assertTrue(reachedFailure)
            assertEquals(stagedRows, staging.associateWith { dao.getByPagingKey(it) })
            assertEquals(stagedCursors, staging.associateWith { dao.getPagingKey(it) })
            assertEquals(original, rows(restored))
            assertEquals(emptyList(), appendCursors(loader))
        }
        val selected = assertNotNull(database.timelineReadingSessionDao().nextCursor(restored.record.key)).key
        val reads = mutableListOf<String>()
        onPagingRowsRead = { reads += it }
        append(restored)
        onPagingRowsRead = {}
        val untouched = staging.single { !it.startsWith(selected) }
        assertFalse(untouched in reads, "Continuing one cursor must not maintain another cursor's staging")
        assertEquals(
            stagedRows.getValue(untouched).map { it._id to it.sortId },
            dao.getByPagingKey(untouched).map { it._id to it.sortId },
        )
        append(restored)
        val expectedCursors = if (maintenanceFailure != null) listOf("fresh", "original") else listOf("original", "fresh")
        assertEquals(expectedCursors, appendCursors(loader))
        assertEquals(((0..49) + (100..109) + (200..209)).map(Int::toString), ids(restored))
        assertEquals(anchor, snapshot(restored).anchorId)
        assertEquals(-15.25, snapshot(restored).offset)
    }

    private suspend fun append(session: TimelineReadingSession) =
        session.load(
            androidx.paging.LoadType.APPEND,
            PagingState(emptyList(), null, dev.dimension.flare.data.datasource.microblog.offsetPagingConfig, 0),
        )

    private fun appendCursors(loader: Loader) = loader.requests.filterIsInstance<PagingRequest.Append>().map { it.nextKey }

    private suspend fun session(
        loader: RemoteLoader<UiTimelineV2>,
        scope: CoroutineScope,
        visible: suspend (UiTimelineV2) -> Boolean = {
            true
        },
        repository: TimelineReadingRepository = TimelineReadingRepository(database, scope),
    ): TimelineReadingSession {
        repository.enterHome { true }
        return TimelineReadingSession(
            assertNotNull(repository.acquire(assertNotNull(repository.open("home", "source")))),
            loader,
            database,
            repository,
            scope,
            NoopPreTranslationService,
            false,
            refreshOnLaunch = { false },
            isVisible = visible,
            notifyError = {},
        )
    }

    private suspend fun snapshot(session: TimelineReadingSession) =
        assertNotNull(database.timelineReadingSessionDao().snapshot(session.record.key))

    private suspend fun savePosition(
        session: TimelineReadingSession,
        itemKey: String,
        sortId: Long,
        offset: Double,
    ) {
        database.timelineReadingSessionDao().saveSnapshot(
            snapshot(session).copy(anchorId = itemKey, anchorSortId = sortId, offset = offset),
        )
    }

    private suspend fun rows(session: TimelineReadingSession) = database.pagingTimelineDao().getByPagingKey(session.activeKey)

    private suspend fun ids(session: TimelineReadingSession) =
        database.pagingTimelineDao().getTimelinePage(session.activeKey, 0, 2000).map {
            it.statusData.statusKey.id
        }

    private class Loader(
        override val pagingKey: String = "home",
        prepend: Boolean = false,
        override val timelineOrder: TimelineOrder = TimelineOrder.Continuous,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        override val supportPrepend = prepend
        var response = page(0..9)
        var forward = page(10..20)
        var beforeLoad: suspend () -> Unit = {}
        val requests = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests += request
            beforeLoad()
            return if (request is PagingRequest.Prepend) forward else response
        }
    }

    companion object {
        private fun page(
            range: IntRange,
            cursor: String = "old-cursor",
        ) = PagingResult<UiTimelineV2>(
            range.map(::post),
            nextKey = cursor,
            previousKey = "head-${range.first}",
        )

        private fun post(id: Int) =
            UiTimelineV2.Post(
                platformId = "xQt",
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user = null,
                content = UiTranslatableText(id.toString().toUiPlainText()),
                actions = persistentListOf(),
                poll = null,
                statusKey = MicroBlogKey(id.toString(), "x.com"),
                card = null,
                createdAt = Instant.fromEpochMilliseconds(1).toUi(),
                references = persistentListOf(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Guest,
            )
    }
}
