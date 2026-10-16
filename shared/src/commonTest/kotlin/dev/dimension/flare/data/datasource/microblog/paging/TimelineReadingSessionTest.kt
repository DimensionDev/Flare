package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class TimelineReadingSessionTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private var afterPagingDelete: (String) -> Unit = {}
    private var afterCommit: () -> Unit = {}

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
                                    if (sql == "END TRANSACTION") {
                                        return object : SQLiteStatement by statement {
                                            override fun step(): Boolean = statement.step().also { afterCommit() }
                                        }
                                    }
                                    if (sql != "DELETE FROM DbPagingTimeline WHERE pagingKey = ?") return statement
                                    return object : SQLiteStatement by statement {
                                        private var pagingKey = ""

                                        override fun bindText(
                                            index: Int,
                                            value: String,
                                        ) {
                                            if (index == 1) pagingKey = value
                                            statement.bindText(index, value)
                                        }

                                        override fun step(): Boolean = statement.step().also { afterPagingDelete(pagingKey) }
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
    fun cancellationAtPublicationCommitKeepsReferencesConsistent() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            loader.response = page(100..110, "fresh-cursor")
            session.refresh(false)
            val publication = launch(start = CoroutineStart.LAZY) { session.showLatest() }
            afterPagingDelete = { key ->
                if (key == session.activeKey) {
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
            assertEquals(snapshot(session).activeKey, session.activeKey)
            assertEquals((100..110).map(Int::toString), ids(session))
            loader.response = page(200..210)
            session.refresh(false)
            assertEquals((100..110).map(Int::toString), ids(session), "The next refresh must preserve the published page")
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
    fun viewLatestResetsExhaustedRemotePagination() =
        runTest {
            var showNewPage = false
            val requests = mutableListOf<PagingRequest>()
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "home"

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
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(5_000) { snapshots.first { it.lastOrNull() == "179" } }
            }
            assertEquals(
                "179",
                snapshots.value.lastOrNull(),
                "View latest must load beyond the first fresh page; requests=$requests",
            )
            assertEquals(80, rows(session).size)
            assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Refresh, PagingRequest.Append("new-next")), requests)
        }

    @Test
    fun nestedMixedFailureDoesNotAdvanceChildCursor() = nestedMixedFailure(TimelineMergePolicy.Staggered, TimelineMergePolicy.Staggered)

    @Test
    fun timeParentFailureDoesNotAdvanceChildCursor() = nestedMixedFailure(TimelineMergePolicy.Time, TimelineMergePolicy.Staggered)

    @Test
    fun timeChildFailureDoesNotChangeStaging() = nestedMixedFailure(TimelineMergePolicy.Staggered, TimelineMergePolicy.Time)

    @Test
    fun nestedTimeFailureDoesNotChangeStaging() = nestedMixedFailure(TimelineMergePolicy.Time, TimelineMergePolicy.Time)

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
        val session = session(outer, backgroundScope)
        session.refresh(false, initial = true)
        val before = rows(session)
        val dao = database.pagingTimelineDao()

        suspend fun mixedData() = dao.keysWithPrefix(session.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) }
        val beforeMixedData = mixedData()
        val suffix = if (childPolicy == TimelineMergePolicy.Time) "staging" else "cursor"
        val childCursorKey = dao.keysWithPrefix(session.activeKey).single { it.endsWith("/source/5:inner/$suffix") }
        val oldCursor = database.pagingTimelineDao().getPagingKey(childCursorKey)
        assertEquals("old-cursor", oldCursor?.nextKey)
        sibling.response = chronologicalPage(30..39).copy(nextKey = null)
        val failSibling = CompletableDeferred<Unit>()
        sibling.beforeLoad = {
            failSibling.await()
            error("sibling offline")
        }
        val mediator =
            TimelineRemoteMediator(
                outer.forReadingSession(session.activeKey) { true },
                database,
                allowLongText = false,
            )
        val request = PagingRequest.Append("mixed_next_key")
        val append = async { runCatching { mediator.load(40, request) } }
        advanceUntilIdle()
        assertEquals(listOf(PagingRequest.Refresh, PagingRequest.Append("old-cursor")), nestedRequests)
        failSibling.complete(Unit)
        val failure = append.await().exceptionOrNull()
        assertTrue(failure is IllegalStateException, "Expected sibling failure, got $failure")
        assertEquals(before, rows(session))
        assertEquals(beforeMixedData, mixedData(), "A sibling failure must not commit child cursors or staging rows")
        val failedCursor = database.pagingTimelineDao().getPagingKey(childCursorKey)
        sibling.beforeLoad = {}
        mediator.saveResponse(request, mediator.load(40, request))
        val actualIds = ids(session)
        assertTrue(
            (100..109).all {
                it.toString() in actualIds
            },
            "Retry skipped a child page; cursor after failure=$failedCursor requests=$nestedRequests ids=$actualIds",
        )
        assertEquals(oldCursor, failedCursor)
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

    private fun mixedAppendCancellation(policy: TimelineMergePolicy) =
        runTest {
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
    fun viewLatestSwitchesTheStoredReferenceWithoutRekeyingThePage() =
        runTest {
            val repository = TimelineReadingRepository(database, backgroundScope)
            val loader = Loader()
            val session = session(loader, backgroundScope, repository = repository)
            session.refresh(false, initial = true)
            val tabBefore = database.timelineReadingSessionDao().get("home")
            val oldKey = session.activeKey
            loader.response = page(100..139, "next")
            session.refresh(false)
            val pendingKey = snapshot(session).pendingKey
            val dao = database.pagingTimelineDao()
            val pendingRows = dao.getByPagingKey(pendingKey)
            val pendingCursor = dao.getPagingKey(pendingKey)
            session.showLatest()
            assertEquals(pendingKey, session.activeKey)
            assertEquals(pendingRows, rows(session), "Publishing must retain the exact stored row IDs")
            assertEquals(pendingCursor, dao.getPagingKey(session.activeKey))
            assertTrue(dao.keysWithPrefix(oldKey).isEmpty())
            assertEquals(tabBefore, database.timelineReadingSessionDao().get("home"), "Reading state belongs only to the snapshot")
            session.positionRestored(assertNotNull(session.state.value.position).requestId)
            val key = "${session.activeKey}_${pendingRows[20].statusId}"
            repository.checkpoint(session.record, session.activeKey, key, -27.5)
            session.close()
            val reopened = session(Loader(), backgroundScope, repository = repository)
            reopened.prepare()
            assertEquals(pendingKey, reopened.activeKey)
            assertEquals(
                key,
                reopened.state.value.position
                    ?.itemKey,
            )
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
    fun manualRefreshRetainsDiscontinuousListAndCursorUntilLatestIsChosen() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            loader.response = page(100..110, "new-cursor")
            session.refresh(false)
            assertEquals(original, rows(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertTrue(session.state.value.hasNewContent)
            assertNull(session.state.value.position)
            session.showLatest()
            assertEquals((100..110).map(Int::toString), ids(session))
            assertEquals("new-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
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
    fun startupStagesEvenContinuousNewPostsUntilRestorationFinishes() =
        runTest {
            val loader = Loader(prepend = true)
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val oldRows = rows(initial)
            loader.response = page(10..20)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            restored.refresh(false, initial = true)
            assertEquals(oldRows.map { it.statusId to it.sortId }, rows(restored).map { it.statusId to it.sortId })
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
            assertEquals((100..110).map(Int::toString), ids(session))
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
    fun mixedPendingCursorsAreIndependentAndPartialFailurePreservesActiveData() =
        runTest {
            val first = Loader("first")
            val second = Loader("second").apply { response = page(20..29, "second-old") }
            val mixed = MixedRemoteMediator(database, listOf(first, second), TimelineMergePolicy.Staggered)
            val session = session(mixed, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            val cursorKeys = database.pagingTimelineDao().keysWithPrefix(session.activeKey)
            val cursors = cursorKeys.map { database.pagingTimelineDao().getPagingKey(it) }
            first.response = page(100..110, "first-new")
            second.beforeLoad = { error("offline") }
            session.refresh(false)
            assertEquals(original, rows(session))
            assertEquals(cursors, cursorKeys.map { database.pagingTimelineDao().getPagingKey(it) })
            assertFalse(session.state.value.hasNewContent)
            second.beforeLoad = {}
            second.response = page(120..130, "second-new")
            session.refresh(false)
            assertEquals(original, rows(session))
            session.showLatest()
            assertEquals("first-new", database.pagingTimelineDao().getPagingKey("${session.activeKey}/source/5:first/cursor")?.nextKey)
            assertEquals("second-new", database.pagingTimelineDao().getPagingKey("${session.activeKey}/source/6:second/cursor")?.nextKey)
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
            assertEquals(old, rows(session))
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
            val anchorKey = "${originalKey}_${originalRows[5].statusId}"
            session.updateViewport(ReadingViewport(anchorKey, -37.25, atTop = false, interacting = false))
            repository.checkpoint(session.record, originalKey, anchorKey, -37.25)
            loader.response = page(100..110, "fresh-cursor")
            session.refresh(false)
            val pending = snapshot(session)
            val retained = database.timelineReadingSessionDao().get("home")
            assertTrue(session.state.value.hasNewContent)
            assertTrue(pending.hasNewContent && pending.pendingReady)

            hideLatest = true
            if (refreshBeforeClick) session.refresh(false)
            val requestCount = loader.requests.size
            repeat(2) { session.showLatest() }

            assertFalse(session.state.value.hasNewContent)
            assertEquals(pending.copy(hasNewContent = false, pendingReady = false), snapshot(session))
            assertEquals(originalKey, session.activeKey)
            assertEquals(originalRows, rows(session))
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
            assertEquals(original, rows(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertNull(session.state.value.position)
            val record = snapshot(session)
            assertTrue(record.pendingReady)
            assertTrue(record.hasNewContent)
            assertTrue(session.state.value.hasNewContent)
            session.showLatest()
            assertEquals((100..110).map(Int::toString), ids(session))
            assertEquals("fresh-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertFalse(session.state.value.hasNewContent)
            assertEquals(3, loader.requests.size)
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
            assertEquals(if (automatic) 21 else 10, rows(session).size)
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertTrue(snapshot(session).pendingReady)

            session.showLatest()
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertEquals((10..20).map(Int::toString), ids(session))
            assertEquals("fresh-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertFalse(session.state.value.hasNewContent)
        }

    @Test
    fun gestureDuringPublicationWritesRollsBackRowsAndCursors() =
        runTest {
            val loader = Loader()
            val session = session(loader, backgroundScope)
            session.refresh(false, initial = true)
            val original = rows(session)
            loader.response = page(100..110, "fresh-cursor")
            session.refresh(false)
            val staged = snapshot(session)
            var interrupted = false
            afterPagingDelete = { key ->
                if (key == session.activeKey && !interrupted) {
                    interrupted = true
                    session.cancelRestoration()
                }
            }
            session.showLatest()
            assertTrue(interrupted, "Cancel after the active rows have been deleted inside the transaction")
            assertNull(session.state.value.position)
            assertEquals(original, rows(session))
            assertEquals("old-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
            assertEquals(staged, snapshot(session))
            assertTrue(session.state.value.hasNewContent)
            assertFalse(session.state.value.refreshing)

            session.showLatest()
            assertTrue(assertNotNull(session.state.value.position).latest)
            assertEquals((100..110).map(Int::toString), ids(session))
            assertEquals("fresh-cursor", database.pagingTimelineDao().getPagingKey(session.activeKey)?.nextKey)
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
            val prepended = rows(initial).take(40)
            assertTrue(prepended.first().sortId > (1L shl 53), "Use production Snowflake ranks")
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
    fun windowsKeepIndependentPendingPagesAndPersistTheLastReadWindow() =
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
            assertEquals((100..139).map(Int::toString), ids(first))
            assertEquals(secondRows, rows(second))
            assertTrue(second.state.value.hasNewContent)
            second.showLatest()
            assertEquals((200..239).map(Int::toString), ids(second))
            assertEquals("first-next", database.pagingTimelineDao().getPagingKey(first.activeKey)?.nextKey)
            assertEquals("second-next", database.pagingTimelineDao().getPagingKey(second.activeKey)?.nextKey)
            second.positionRestored(assertNotNull(second.state.value.position).requestId)
            val anchor = rows(second)[20]
            second.updateViewport(ReadingViewport("${second.activeKey}_${anchor.statusId}", -30.0, false, true))
            second.close()
            first.close()
            assertTrue(database.pagingTimelineDao().keysWithPrefix(first.record.key).isEmpty())
            assertEquals(40, rows(second).size)
            val reopened = session(Loader(), backgroundScope, repository = repository)
            assertEquals(second.activeKey, reopened.activeKey, "An unoccupied retained snapshot must be reused without copying history")
            reopened.prepare()
            assertEquals("${reopened.activeKey}_${anchor.statusId}", assertNotNull(reopened.state.value.position).itemKey)
            assertEquals(
                -30.0,
                reopened.state.value.position
                    ?.offset,
            )
            assertEquals("second-next", database.pagingTimelineDao().getPagingKey(reopened.activeKey)?.nextKey)
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
            assertEquals(old, dao.keysWithPrefix(second.activeKey).associateWith { dao.getByPagingKey(it) to dao.getPagingKey(it) })
            assertEquals("first-new", dao.getPagingKey("${first.activeKey}/source/5:first/cursor")?.nextKey)
            assertEquals("second-new", dao.getPagingKey("${first.activeKey}/source/6:second/cursor")?.nextKey)
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
