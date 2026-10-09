package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
class TimelineReadingSessionTest : RobolectricTest() {
    private lateinit var database: CacheDatabase

    @BeforeTest
    fun setup() {
        database =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun close() = database.close()

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
            assertTrue(database.pagingTimelineDao().keysWithPrefix("${session.record.key}pending/").isEmpty())
        }

    @Test
    fun topBookmarkRestoresThePostAndOffsetWithoutStartingNetwork() =
        runTest {
            val loader = Loader()
            val initial = session(loader, backgroundScope)
            initial.refresh(false, initial = true)
            val head = rows(initial).first()
            val key = "${initial.activeKey}_${head.statusId}"
            database.timelineReadingSessionDao().savePosition("home", initial.record.key, key, head.sortId, 12.25)
            val restored = session(loader, backgroundScope)
            restored.prepare()
            assertEquals(
                key,
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
            database.timelineReadingSessionDao().savePosition("home", initial.record.key, key, anchor.sortId, -51.5)
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
                assertEquals(key, page.data[20].baseItem.stableItemKey)
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
            assertEquals(oldRows, rows(restored))
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
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.key).isEmpty())
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
            val session = session(Loader(), backgroundScope)
            session.refresh(false, initial = true)
            assertEquals(session.record.key, repo.open("home", "source").key)
            val replacement = repo.open("home", "different-source")
            assertNotEquals(session.record.key, replacement.key)
            assertTrue(database.pagingTimelineDao().keysWithPrefix(session.record.key).isEmpty())
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

    private suspend fun session(
        loader: RemoteLoader<UiTimelineV2>,
        scope: CoroutineScope,
        visible: suspend (UiTimelineV2) -> Boolean = {
            true
        },
    ): TimelineReadingSession {
        val repository = TimelineReadingRepository(database, scope)
        return TimelineReadingSession(
            repository.open("home", "source"),
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
