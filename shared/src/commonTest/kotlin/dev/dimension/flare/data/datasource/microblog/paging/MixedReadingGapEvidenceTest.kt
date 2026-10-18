package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.data.repository.TimelineReadingRepository
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.home.matchesKeywordFilters
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class MixedReadingGapEvidenceTest : RobolectricTest() {
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

    @Test fun rankedOldTailWithFailedSource() = oldTailGap(TimelineOrder.Ranked, fails = true)

    @Test fun rankedOldTailWithAllSourcesHealthy() = oldTailGap(TimelineOrder.Ranked)

    @Test fun continuousOldTailControl() = oldTailGap(TimelineOrder.Continuous)

    @Test fun firstHealthyContinuousHeadContainsOnlyOldTail() = oldTailGap(TimelineOrder.Continuous, firstHead = true)

    @Test fun noOldTailControl() = oldTailGap(TimelineOrder.Ranked, noOldTail = true)

    @Test fun firstContinuousOldTailWithCursorDoesNotConfirmAHeadGap() =
        oldTailGap(TimelineOrder.Continuous, firstHead = true, tailCursor = "older")

    @Test fun nestedTimeOldTailDoesNotConfirmAHeadGap() = oldTailGap(TimelineOrder.Ranked, nested = TimelineMergePolicy.Time)

    @Test fun nestedPerPageOldTailDoesNotConfirmAHeadGap() = oldTailGap(TimelineOrder.Ranked, nested = TimelineMergePolicy.TimePerPage)

    private fun oldTailGap(
        order: TimelineOrder,
        fails: Boolean = false,
        firstHead: Boolean = false,
        noOldTail: Boolean = false,
        tailCursor: String? = null,
        nested: TimelineMergePolicy? = null,
    ) = runTest {
        val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(100..139) }
        val low = Loader("low", order).apply { response = if (firstHead || noOldTail) PagingResult() else timed(0..0) }
        val expired = Loader("expired", TimelineOrder.Continuous).apply { response = PagingResult() }
        val sources = if (fails) listOf(high, low, expired) else listOf(high, low)
        val children = if (nested == null) sources else listOf(MixedRemoteMediator(database, sources, nested))
        val current = session(MixedRemoteMediator(database, children, TimelineMergePolicy.Time), backgroundScope)
        current.refresh(false, initial = true)
        assertEquals((100..139).reversed().map(Int::toString), ids(current))
        high.response = timed(101..140)
        if (firstHead) low.response = timed(0..0, tailCursor)
        expired.beforeLoad = { error("expired session") }
        current.refresh(false)
        val actual = rows(current)
        assertEquals((100..140).reversed().map(Int::toString), ids(current))
        assertTrue("gap" !in actual, "Only post 0 is pending; no posts are missing between 140 and 139")
        if (!noOldTail) {
            low.response = PagingResult()
            repeat(3) { current.load(LoadType.APPEND, PagingState(emptyList(), null, offsetPagingConfig, 0)) }
            assertEquals("0", ids(current).last(), "Hidden gap markers must not discard normal tail continuation")
            assertEquals(listOf("140", "139"), ids(current).take(2))
        }
    }

    @Test fun invisibleMiddleBufferMustNotCreateAVisibleGap() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(0..39) }
            val other = Loader("other", TimelineOrder.Continuous).apply { response = PagingResult() }
            val filters = listOf(KeywordFilterPattern("hidden-buffer", isRegex = false))

            fun visible(item: UiTimelineV2) = item.matchesKeywordFilters(filters)
            val current =
                session(
                    MixedRemoteMediator(database, listOf(high, other), TimelineMergePolicy.Time),
                    backgroundScope,
                    visible = ::visible,
                )

            suspend fun visibleRows() =
                readPage(current)
                    .map { it.baseItem }
                    .filter { it.isReadingGap || visible(it) } // Same production predicate/order as TimelinePresenter.createPager.
                    .map { if (it.isReadingGap) "gap" else it.statusKey.id }
            current.refresh(false, initial = true)
            val next = timed(40..119)
            high.response =
                next.copy(
                    data =
                        next.data.map {
                            if (it.statusKey.id.toInt() in
                                40..79
                            ) {
                                (it as UiTimelineV2.Post).copy(content = UiTranslatableText("hidden-buffer".toUiPlainText()))
                            } else {
                                it
                            }
                        },
                )
            current.refresh(false)
            val actual = visibleRows()
            assertTrue("gap" !in actual, "The only buffered middle posts (40..79) are all excluded by the active keyword filter")
        }

    @Test fun filteredBufferRevalidatesWhenFiltersChangeAndAfterReopen() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(0..39) }
            val mixed = MixedRemoteMediator(database, listOf(high), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var hide = false
            val visible: suspend (UiTimelineV2) -> Boolean = { !hide || it.statusKey.id.toInt() !in 40..79 }
            var current = session(mixed, backgroundScope, visible, repository)
            current.refresh(false, initial = true)
            high.response = timed(40..119)
            current.refresh(false)
            val segment = database.timelineReadingSessionDao().segments(current.record.key).single()
            assertTrue("gap" in rows(current))
            val requests = high.requests.size
            hide = true
            current.filtersChanged()
            assertTrue("gap" !in rows(current), "Changing filters must recheck an already confirmed segment")
            current.close()
            current = session(mixed, backgroundScope, visible, repository)
            current.prepare()
            assertTrue("gap" !in rows(current), "A saved confirmed flag must not override active filters")
            hide = false
            current.filtersChanged()
            assertTrue("gap" in rows(current), "Unblocking known buffered posts must restore the real gap")
            assertEquals(requests, high.requests.size, "Revalidation must not fetch remote pages")
            assertEquals(null, current.loadGap(segment.key))
            assertEquals((0..119).reversed().map(Int::toString), ids(current))
            assertTrue("gap" !in rows(current))
            assertEquals(requests, high.requests.size, "Filling the known buffer needs no remote requests")
        }

    @Test fun anotherRefreshPublishingTheBufferClosesAnExistingGap() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(0..39) }
            val current = session(MixedRemoteMediator(database, listOf(high), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            high.response = timed(40..119)
            current.refresh(false)
            assertTrue("gap" in rows(current))
            high.response = timed(40..79)
            current.refresh(false)
            assertTrue("gap" !in rows(current), "Already published content cannot keep a previous buffer's gap open")
            assertEquals((0..119).reversed().map(Int::toString), ids(current))
        }

    @Test fun actualUnseenMiddleBufferControl() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(0..39) }
            val other = Loader("other", TimelineOrder.Continuous).apply { response = PagingResult() }
            val current = session(MixedRemoteMediator(database, listOf(high, other), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            high.response = timed(40..119)
            current.refresh(false)
            assertTrue("gap" in rows(current))
        }

    @Test fun oldTailRemainsUnconfirmedAcrossReopenAndRefresh() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(100..139) }
            val low = Loader("low", TimelineOrder.Ranked).apply { response = timed(0..0) }
            val mixed = MixedRemoteMediator(database, listOf(high, low), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            high.response = timed(101..140)
            current.refresh(false)
            val first = rows(current)
            assertTrue("gap" !in first)
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            assertTrue("gap" !in rows(current))
            high.response = timed(102..141)
            low.response = PagingResult()
            current.refresh(false)
            val refreshed = rows(current)
            assertTrue("gap" !in refreshed, "The false gap survives both reopening and a later successful refresh")
        }

    @Test fun currentUnconfirmedCursorReopenAndRefreshControl() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(100..101) }
            val other = Loader("other", TimelineOrder.Continuous).apply { response = timed(0..1) }
            val mixed = MixedRemoteMediator(database, listOf(high, other), TimelineMergePolicy.Time)
            val repository = TimelineReadingRepository(database, backgroundScope)
            var current = session(mixed, backgroundScope, repository = repository)
            current.refresh(false, initial = true)
            high.response = timed(100..102, "ranked-next")
            current.refresh(false)
            val dao = database.timelineReadingSessionDao()
            val unknown = dao.segments(current.record.key).single()
            assertTrue(!unknown.confirmed)
            current.close()
            current = session(mixed, backgroundScope, repository = repository)
            current.prepare()
            assertTrue("gap" !in rows(current), "Reopening must preserve the unconfirmed continuation without a marker")
            high.response = timed(100..103, "another-ranked-next")
            current.refresh(false)
            val after = rows(current)
            assertTrue("gap" !in after, "Refreshing an unknown ranked continuation must not confirm a gap")
            assertNotNull(dao.cursor(unknown.key), "Revalidation must preserve the continuation")
            high.response = timed(50..51)
            assertEquals(null, current.loadGap(unknown.key, retryFailed = false))
            assertTrue(ids(current).containsAll(listOf("50", "51")), "Unconfirmed cursors must still publish unseen posts")
        }

    @Test
    fun establishedContinuousSourceBelowTheGlobalHeadCannotConfirmAHeadGap() =
        runTest {
            val high = Loader("high", TimelineOrder.Ranked).apply { response = timed(100..139) }
            val low = Loader("low", TimelineOrder.Continuous).apply { response = timed(0..0) }
            val current = session(MixedRemoteMediator(database, listOf(high, low), TimelineMergePolicy.Time), backgroundScope)
            current.refresh(false, initial = true)
            high.response = timed(101..140)
            low.response = timed(1..10, "older")
            current.refresh(false)
            assertTrue("gap" !in rows(current), "The low source's entire continuation is below the 140/139 seam")
            val segment = database.timelineReadingSessionDao().segments(current.record.key).single()
            assertNotNull(database.timelineReadingSessionDao().cursor(segment.key))
            low.response = timed(0..0)
            repeat(3) { current.load(LoadType.APPEND, PagingState(emptyList(), null, offsetPagingConfig, 0)) }
            assertTrue(ids(current).containsAll((0..10).map(Int::toString)))
        }

    private suspend fun rows(current: TimelineReadingSession) =
        readPage(current).map {
            if (it.baseItem.stableItemKey.startsWith("reading-gap:")) "gap" else it.baseItem.statusKey.id
        }

    private fun timed(
        range: IntRange,
        cursor: String? = null,
    ) = PagingResult<UiTimelineV2>(
        range.reversed().map { post(it).copy(createdAt = Instant.fromEpochMilliseconds(it * 1000L).toUi()) },
        nextKey = cursor,
    )

    private suspend fun readPage(current: TimelineReadingSession): List<TimelinePageItem> {
        val source = TimelineReadingPagingSource(database, current)
        return try {
            (
                source.load(
                    PagingSource.LoadParams.Refresh(OffsetFromStartPagingKey.Append(0), 1000, false),
                ) as PagingSource.LoadResult.Page
            ).data
        } finally {
            source.invalidate()
        }
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

    private suspend fun ids(session: TimelineReadingSession) =
        database.pagingTimelineDao().getTimelinePage(session.activeKey, 0, 2000).map {
            it.statusData.statusKey.id
        }

    private class Loader(
        override val pagingKey: String,
        override val timelineOrder: TimelineOrder = TimelineOrder.Continuous,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        var response = PagingResult<UiTimelineV2>()
        var beforeLoad: suspend () -> Unit = {}
        val requests = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests += request
            beforeLoad()
            return response
        }
    }

    companion object {
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
