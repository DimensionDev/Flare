package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.PagingSource
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.MixedTimelinePageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelineReader
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        store = TimelineHistoryStore(database)
    }

    @AfterTest
    fun close() = database.close()

    @Test
    fun equivalentRefreshesKeepOnlyOneManualContinuation() =
        runTest {
            var response = page(3, 2, next = "original")
            val history = assertNotNull(store.open(source { response }, false))
            assertTrue(history.refresh())
            response = page(5, 4, next = "same-next")
            repeat(3) { assertTrue(history.refresh()) }
            assertEquals(listOf("5", "4", "gap", "3", "2"), order(history))
        }

    @Test
    fun equivalentBufferedMixedPagesIgnoreChangesToPendingPostPresentation() =
        runTest {
            var edited = false
            val first = source { page(300, 200, next = "first") }
            val second =
                source {
                    PagingResult(listOf(historyTestPost(100).copy(sensitive = edited)), nextKey = "second")
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(first, second), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            edited = true
            assertTrue(history.refresh())
            assertEquals(listOf("300", "200"), order(history), "Updating a pending post is not another continuation")
        }

    @Test
    fun continuousRefreshInsertsMissingPostsBetweenTheirNeighbours() =
        runTest {
            var response = page(4, 2, next = "original")
            val history = assertNotNull(store.open(source(join = true) { response }, false))
            assertTrue(history.refresh())
            response = page(5, 4, 3, 2, 1, next = "new")
            assertTrue(history.refresh())
            assertEquals(listOf("5", "4", "3", "2", "1"), order(history))
        }

    @Test
    fun recoveredTimeSourceFillsChronologicalHolesWithoutMovingExistingPosts() =
        runTest {
            var recovered = false
            val healthy = source { page(300, 100) }
            val unavailable =
                source {
                    check(recovered) { "offline" }
                    page(200, 50)
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(healthy, unavailable), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            recovered = true
            assertTrue(history.refresh())
            assertEquals(listOf("300", "200", "100", "50"), order(history))
        }

    @Test
    fun aSparseContinuousSourceRecordsTheFirstNonEmptyBoundary() =
        runTest {
            var refreshed = false
            val sparse =
                source(key = "sparse", join = true) { request ->
                    when {
                        refreshed -> page(3, 2, next = "overlap")
                        request is PagingRequest.Append -> page(2, 1)
                        else -> page(next = "first-nonempty")
                    }
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(sparse), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            assertTrue(history.append())
            refreshed = true
            assertTrue(history.refresh())
            assertEquals(listOf("3", "2", "1"), order(history))
        }

    @Test
    fun recoveredSourceBoundarySurvivesInsertionIntoAnOlderTimeBatch() =
        runTest {
            var round = 0
            val healthy = source(key = "healthy", join = true) { page(300, 100) }
            val recovered =
                source(key = "recovered", join = true) {
                    when (round) {
                        0 -> error("offline")
                        1 -> page(200, 50)
                        else -> page(250, 200, next = "older")
                    }
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(healthy, recovered), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            round = 1
            assertTrue(history.refresh())
            val checkpoints = database.timelineHistoryDao().batches(history.key).size
            repeat(3) { assertTrue(history.refresh()) }
            assertEquals(
                checkpoints,
                database.timelineHistoryDao().batches(history.key).size,
                "Identical source checkpoints must not accumulate",
            )
            round = 2
            assertTrue(history.refresh())
            assertEquals(listOf("300", "250", "200", "100", "50"), order(history), "The recovered source now has its own boundary")
        }

    @Test
    fun aRecoveredMixedSourceCannotJoinAnotherSourcesOldPost() =
        runTest {
            var recovered = false
            val healthy = source(key = "healthy", join = true) { page(5) }
            val unavailable =
                source(key = "recovered", join = true) { request ->
                    if (!recovered) error("offline")
                    if (request is PagingRequest.Append) page(4, 3) else page(9, 5, next = "older")
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(healthy, unavailable), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            recovered = true
            assertTrue(history.refresh())
            val gap = rows(history).singleOrNull { it.timelineId == null }
            assertNotNull(gap, "The recovered source has never established its own old boundary")
            assertTrue(history.loadGap(gap.batchId))
            assertEquals(listOf("9", "5", "4", "3"), order(history))
        }

    @Test
    fun mixedGapJoinsEachContinuousSourceIndependently() =
        runTest {
            var round = 0
            val requests = mutableListOf<PagingRequest>()
            val first =
                source(key = "first", join = true) {
                    requests += it
                    when (round) {
                        0 -> page(30, 10, next = "first-old")
                        else -> page(50, 30, next = "first-new")
                    }
                }
            val second =
                source(key = "second", join = true) {
                    when (round) {
                        0 -> page(20)
                        1 -> page(40, next = "second-new")
                        else -> page(25, 20, next = "second-end")
                    }
                }
            val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(first, second), TimelineMergePolicy.Time), false))
            assertTrue(history.refresh())
            round = 1
            assertTrue(history.refresh())
            val gap = rows(history).single { it.timelineId == null }.batchId
            round = 2
            assertTrue(history.loadGap(gap))
            assertEquals(2, requests.size, "The first source already joined; clicking the gap only loads the other source")
            assertEquals(listOf("50", "40", "30", "25", "20", "10"), order(history))
        }

    @Test
    fun aFailedMixedSourceDoesNotCreateAGapWhenSuccessfulSourcesHaveNoContinuation() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                var fail = false
                var response = page(3)
                val good = source { response }
                val bad =
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "failing"

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            if (fail) error("offline")
                            return page(2)
                        }
                    }
                val history = assertNotNull(store.open(MixedTimelinePageLoader(listOf(good, bad), policy), false))
                assertTrue(history.refresh())
                val before = rows(history)
                fail = true
                assertTrue(history.refresh())
                assertEquals(before, rows(history), "A failed source is not a missing page ($policy)")
                response = page(4, 3)
                assertTrue(history.refresh())
                assertEquals(listOf("4", "3", "2"), order(history), "Successful new posts remain visible without an error gap ($policy)")
            }
        }

    @Test
    fun filteredNewPostsDoNotAnnounceOrLeaveAnOrphanGapAndLatestUsesTheVisibleHead() =
        runTest {
            var response = page(3, 2, 1)
            val history = assertNotNull(store.open(source { response }, false))
            history.refresh()
            val reader = TimelineReader("filtered", history, database, store, backgroundScope)
            reader.setVisibility("hide-new") { it.statusKey.id.toInt() < 4 }
            reader.restore()
            loadWindow(reader)
            val originalHead = rows(history).first().itemKey
            reader.viewportChanged(rows(history).last().itemKey, 0.0)
            response = page(5, 4, next = "hidden-gap")
            history.refresh()
            loadWindow(reader)
            assertFalse(reader.hasNewContent.first(), "A filtered prepend cannot announce unseen visible content")
            val paging = TimelineHistoryPagingSource(database, reader) { error("A local read cannot activate a gap") }
            val result =
                assertIs<PagingSource.LoadResult.Page<TimelineHistoryPagingKey, TimelinePageItem>>(
                    paging.load(PagingSource.LoadParams.Refresh(null, 20, false)),
                )
            paging.invalidate()
            assertEquals(3, result.data.size)
            assertTrue(result.data.none { it.baseItem.isPaginationGap })
            reader.showLatest()
            reader.target.first { it != null }
            assertEquals(originalHead, reader.target.value?.itemKey)
            assertTrue(reader.isAtLatest.first())
            reader.setVisibility("show-all") { true }
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first(), "Changing filters must re-evaluate the visible head")
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("filtered", history.key)?.itemKey == originalHead
            }
        }

    @Test
    fun unreadContentSurvivesReaderAndStoreRecreation() =
        runTest {
            var response = page(3, 2, 1)
            val loader = source { response }
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("unread", history, database, store, backgroundScope)
            reader.restore()
            loadWindow(reader)
            val anchor = rows(history).last()
            reader.positionChanged(anchor.itemKey, -23.0)
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("unread", history.key) != null
            }
            response = page(5, 4)
            history.refresh()
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first())
            val reopenedStore = TimelineHistoryStore(database)
            val reopened =
                TimelineReader("unread", assertNotNull(reopenedStore.open(loader, false)), database, reopenedStore, backgroundScope)
            reopened.restore()
            loadWindow(reopened)
            assertEquals(anchor.itemKey, reopened.target.value?.itemKey)
            assertEquals(-23.0, reopened.target.value?.offset)
            assertTrue(reopened.hasNewContent.first(), "Reopening must not acknowledge posts the user has not reached")
        }

    @Test
    fun theNewContentNoticeOnlyAppearsAfterAPrependAndStaysDismissedAfterReadingIt() =
        runTest {
            var response = page(3, 2, 1)
            val history = assertNotNull(store.open(source { response }, false))
            history.refresh()
            val reader = TimelineReader("tab", history, database, store, backgroundScope)
            reader.restore()
            loadWindow(reader)
            val oldHead = rows(history).first().itemKey
            val olderPost = rows(history).last().itemKey
            assertFalse(reader.hasNewContent.first())
            assertTrue(reader.isAtLatest.first())
            reader.viewportChanged(olderPost, -10.0)
            assertFalse(reader.hasNewContent.first(), "Scrolling into old posts must not announce new content")
            assertFalse(reader.isAtLatest.first(), "The banner and actual head position have different meanings")

            response = page(5, 4, 3)
            history.refresh()
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first())
            reader.viewportChanged(oldHead, 0.0)
            assertTrue(reader.hasNewContent.first(), "Keeping the old visible post does not consume new posts")
            reader.viewportChanged(rows(history).first().itemKey, -10.0)
            assertFalse(reader.hasNewContent.first(), "Reaching the new head consumes the notice")
            assertTrue(reader.isAtLatest.first())
            reader.viewportChanged(olderPost, 0.0)
            assertFalse(reader.hasNewContent.first(), "Scrolling away must not bring the notice back")

            response = page(6, 5)
            history.refresh()
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first(), "A later prepend can announce new content again")
            reader.showLatest()
            reader.target.first { it?.itemKey == rows(history).first().itemKey }
            assertFalse(reader.hasNewContent.first())
            val latest = rows(history).first().itemKey
            // Bookmark writes intentionally outlive the view. Await the durable
            // write before this test closes its database.
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("tab", history.key)?.itemKey == latest
            }
        }

    @Test
    fun removingTheHeadDoesNotAnnounceOldContentOrConsumeOtherUnreadPosts() =
        runTest {
            var response = page(3, 2, 1)
            val history = assertNotNull(store.open(source { response }, false))
            history.refresh()
            val reader = TimelineReader("tab", history, database, store, backgroundScope)
            reader.restore()
            loadWindow(reader)
            reader.viewportChanged(rows(history).last().itemKey, 0.0)

            suspend fun removeHead() {
                val head = rows(history).first()
                val row = database.pagingTimelineDao().getTimelineRootRowsByIds(history.key, listOf(checkNotNull(head.timelineId))).single()
                database.pagingTimelineDao().delete(listOf(row.timeline))
                loadWindow(reader)
            }
            removeHead()
            assertFalse(reader.hasNewContent.first(), "Deleting the head is not a prepend")
            response = page(5, 4)
            history.refresh()
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first())
            removeHead()
            assertTrue(reader.hasNewContent.first(), "The other prepended item is still unread")
            removeHead()
            assertFalse(reader.hasNewContent.first(), "No prepended posts remain")
        }

    @Test
    fun aRestoredBookmarkDoesNotAnnounceOldContentButALaunchRefreshDoes() =
        runTest {
            var response = page(3, 2, 1)
            val loader = source { response }
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val anchor = rows(history).last()
            database.timelineHistoryDao().save(
                DbTimelineBookmark("tab", history.key, anchor.itemKey, anchor.ordinal, anchor.position, -30.0),
            )
            val reader = TimelineReader("tab", history, database, store, backgroundScope)
            reader.restore()
            loadWindow(reader)
            assertFalse(reader.hasNewContent.first(), "Restoring an older bookmark is not a new-content event")
            val beforeFirstPage = TimelineReader("tab", history, database, store, backgroundScope)
            beforeFirstPage.restore()
            response = page(5, 4)
            history.refresh()
            loadWindow(reader)
            assertTrue(reader.hasNewContent.first())
            assertEquals(anchor.itemKey, reader.current.value?.itemKey)
            loadWindow(beforeFirstPage)
            assertTrue(beforeFirstPage.hasNewContent.first(), "A launch refresh can finish before the first local page")
        }

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
            val history = assertNotNull(store.open(loader, false))
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
            val history = assertNotNull(store.open(source { response }, false))
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
    fun aDuplicateOnlyGapPageAdvancesItsCursorButWaitsForAnotherClick() =
        runTest {
            var response = page(3, 2, 1)
            val requests = mutableListOf<PagingRequest>()
            val history =
                assertNotNull(
                    store.open(
                        source {
                            requests += it
                            response
                        },
                        false,
                    ),
                )
            history.refresh()
            response = page(5, 4, next = "first")
            history.refresh()
            val before = rows(history)
            val gap = before.first { it.timelineId == null }.batchId
            response = page(3, 2, 1, next = "second")
            assertTrue(history.loadGap(gap))
            assertEquals(before, rows(history))
            assertEquals("second", database.timelineHistoryDao().batch(gap)?.nextKey)
            assertNull(history.activity.value.failures[gap])
            repeat(3) { rows(history) }
            assertEquals(3, requests.size, "A duplicate page must not automatically request another page")
            response = page(0, next = "third")
            assertTrue(history.loadGap(gap))
            assertEquals(PagingRequest.Append("second"), requests.last())
            assertEquals(4, requests.size)
            assertEquals(listOf("5", "4", "0", "gap", "3", "2", "1"), order(history))
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
            val first = assertNotNull(store.open(loader, false))
            first.refresh()
            response = page(3, next = "gap-one")
            first.refresh()
            response = page(5, next = "gap-two")
            first.refresh()
            val second = assertNotNull(store.open(loader, false))
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
                assertNotNull(
                    store.open(
                        source {
                            if (fail) error("offline")
                            response
                        },
                        false,
                    ),
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
    fun disablingHistoryDropsBookmarksAndRejectsTheInFlightRefresh() =
        runTest {
            var delayed = false
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val loader =
                source {
                    if (delayed) {
                        entered.complete(Unit)
                        release.await()
                    }
                    page(3, 2, 1)
                }
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val reader = TimelineReader("disabled", history, database, store, backgroundScope)
            reader.restore()
            loadWindow(reader)
            val anchor = rows(history).last().itemKey
            reader.positionChanged(anchor, -8.0)
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("disabled", history.key) != null
            }
            delayed = true
            val request = async { history.refresh() }
            entered.await()
            store.setEnabled(false)
            reader.positionChanged(anchor, -9.0)
            release.complete(Unit)
            request.await()
            assertTrue(rows(history).isEmpty())
            store.setEnabled(true)
            val reopened = assertNotNull(store.open(loader, false))
            assertTrue(history.generation != reopened.generation)
            val newReader = TimelineReader("disabled", reopened, database, store, backgroundScope)
            newReader.restore()
            assertEquals(null, newReader.target.value)
        }

    @Test
    fun loginFailuresAreReportedWithoutInventingAGapOrDroppingHealthyData() =
        runTest {
            val errors = mutableListOf<Throwable>()
            val notifications =
                object : dev.dimension.flare.common.InAppNotification {
                    override fun onProgress(
                        message: dev.dimension.flare.common.Message,
                        progress: Int,
                        total: Int,
                    ) {}

                    override fun onSuccess(message: dev.dimension.flare.common.Message) {}

                    override fun onError(
                        message: dev.dimension.flare.common.Message,
                        throwable: Throwable,
                    ) {
                        errors += throwable
                    }
                }
            val notifiedStore = TimelineHistoryStore(database, notifications)
            var fail = false
            val expired =
                dev.dimension.flare.data.repository
                    .LoginExpiredException(MicroBlogKey("user", "example.com"), "bluesky")
            val source =
                source {
                    if (fail) throw expired
                    page(2)
                }
            val history =
                assertNotNull(
                    notifiedStore.open(MixedTimelinePageLoader(listOf(source, source { page(3) }), TimelineMergePolicy.Time), false),
                )
            history.refresh()
            fail = true
            assertTrue(history.refresh())
            assertEquals<List<Throwable>>(listOf(expired), errors)
            assertEquals(listOf("3", "2"), order(history))
            val failed = assertNotNull(notifiedStore.open(source, false))
            assertFalse(failed.refresh())
            assertEquals<List<Throwable>>(listOf(expired, expired), errors)
        }

    @Test
    fun removingATabRejectsLateBookmarksButRetainsDataUsedByAnotherTab() =
        runTest {
            val history = assertNotNull(store.open(source { page(3, 2, 1) }, false))
            history.refresh()
            val first = TimelineReader("first", history, database, store, backgroundScope)
            val second = TimelineReader("second", history, database, store, backgroundScope)
            first.restore()
            second.restore()
            loadWindow(first)
            loadWindow(second)
            val anchor = rows(history).last().itemKey
            first.positionChanged(anchor, -12.0)
            second.positionChanged(anchor, -24.0)
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("first", history.key) != null &&
                    database.timelineHistoryDao().bookmark("second", history.key) != null
            }
            store.invalidateTabs(setOf("first"))
            assertEquals(null, database.timelineHistoryDao().bookmark("first", history.key))
            assertEquals(listOf("3", "2", "1"), order(history))
            first.positionChanged(anchor, -999.0)
            second.positionChanged(anchor, -25.0)
            database.invalidationTracker.createFlow("DbTimelineBookmark").first {
                database.timelineHistoryDao().bookmark("second", history.key)?.offset == -25.0
            }
            assertEquals(null, database.timelineHistoryDao().bookmark("first", history.key))
            store.invalidateTabs(setOf("second"))
            assertTrue(rows(history).isEmpty())
            assertEquals(null, database.timelineHistoryDao().history(history.key))
        }

    @Test
    fun clearingWhileARequestIsRunningRejectsItsLateResponse() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val history =
                assertNotNull(
                    store.open(
                        source {
                            entered.complete(Unit)
                            release.await()
                            page(1)
                        },
                        false,
                    ),
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
                assertNotNull(
                    store.open(
                        source {
                            requests++
                            if (fail) error("offline")
                            entered.complete(Unit)
                            release.await()
                            page(1)
                        },
                        false,
                    ),
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
            val history = assertNotNull(store.open(loader, false))
            history.refresh()
            val dao = database.timelineHistoryDao()
            val anchor = dao.page(history.key, 120, 1).single()
            dao.save(DbTimelineBookmark("tab", history.key, anchor.itemKey, anchor.ordinal, anchor.position, -42.5))
            val reopenedStore = TimelineHistoryStore(database)
            val reopened = assertNotNull(reopenedStore.open(loader, false))
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
            val history = assertNotNull(store.open(loader, false))
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
                assertNotNull(
                    store.open(
                        object : CacheableRemoteLoader<UiTimelineV2> by loader {
                            override val pagingKey = "other"
                        },
                        false,
                    ),
                )
            val otherReader = TimelineReader("tab", other, database, store, backgroundScope)
            otherReader.restore()
            assertEquals(null, otherReader.target.value)
        }

    private suspend fun rows(history: TimelineHistory) = database.timelineHistoryDao().page(history.key, 0, 100)

    private suspend fun loadWindow(reader: TimelineReader) {
        val paging = TimelineHistoryPagingSource(database, reader) { error("A local read cannot activate a gap") }
        try {
            assertIs<PagingSource.LoadResult.Page<TimelineHistoryPagingKey, TimelinePageItem>>(
                paging.load(PagingSource.LoadParams.Refresh(null, 20, false)),
            )
        } finally {
            paging.invalidate()
        }
    }

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
        key: String = "test",
        join: Boolean = false,
        block: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) = object : CacheableRemoteLoader<UiTimelineV2> {
        override val pagingKey = key
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
