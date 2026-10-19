package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.clearHomeTimelinesForAccount
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.data.repository.MxgaSnapshot
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.home.TimelineVisibility
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimelineGapTest : RobolectricTest() {
    private lateinit var database: CacheDatabase
    private val state = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(20), 0)

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
    fun tearDown() {
        database.close()
    }

    @Test
    fun rankedDuplicateRefreshDoesNotAddLeadingGaps() =
        runTest {
            val response = PagingResult<UiTimelineV2>(listOf(post(30), post(10)), nextKey = "ranked-tail")
            val home = mediator { response }
            home.load(LoadType.REFRESH, state)
            repeat(3) { home.load(LoadType.REFRESH, state) }
            val rows = contents()
            assertEquals(0, rows.filterIsInstance<UiTimelineV2.Gap>().size)
        }

    @Test
    fun timeMixedPartialFailureDoesNotAddLeadingGaps() =
        runTest {
            for (ranked in listOf(false, true)) {
                var offline = false
                val healthy =
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "healthy-$ranked"
                        override val canJoinTimeline = !ranked

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ) = PagingResult<UiTimelineV2>(
                            listOf(
                                post(30).copy(createdAt = Instant.fromEpochMilliseconds(30).toUi()),
                                post(10).copy(createdAt = Instant.fromEpochMilliseconds(10).toUi()),
                            ),
                            nextKey = "tail",
                        )
                    }
                val failing =
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "failing-$ranked"
                        override val canJoinTimeline = true

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            check(!offline) { "offline" }
                            return PagingResult(listOf(post(20).copy(createdAt = Instant.fromEpochMilliseconds(20).toUi())))
                        }
                    }
                val mixed =
                    dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator(
                        database,
                        listOf(healthy, failing),
                        dev.dimension.flare.data.model.tab.TimelineMergePolicy.Time,
                    )
                val home = TimelineRemoteMediator(mixed, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                offline = true
                repeat(3) { home.load(LoadType.REFRESH, state) }
                val rows = database.pagingTimelineDao().getTimelinePage(home.pagingKey, 0, 50).map { it.statusData.content }
                assertEquals(0, rows.filterIsInstance<UiTimelineV2.Gap>().size, "ranked=$ranked")
            }
        }

    @Test
    fun timeMixedRecommendedRefreshKeepsGlobalTimeOrder() =
        runTest {
            var fresh = false
            var tail = false

            fun dated(id: Int): UiTimelineV2 = post(id).copy(createdAt = Instant.fromEpochMilliseconds(id.toLong()).toUi())
            val ranked =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "ranked"

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ) = if (request is PagingRequest.Append) {
                        PagingResult(if (tail) listOf(dated(5)) else listOf(dated(50), dated(25)))
                    } else {
                        PagingResult(if (fresh) listOf(dated(40), dated(10)) else listOf(dated(30)), nextKey = "ranked-tail")
                    }
                }
            val chronological =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "chronological"
                    override val canJoinTimeline = true

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ) = PagingResult(listOf(dated(20)))
                }
            val mixed =
                dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator(
                    database,
                    listOf(ranked, chronological),
                    dev.dimension.flare.data.model.tab.TimelineMergePolicy.Time,
                )
            val home = TimelineRemoteMediator(mixed, database, false, retainHistory = true)
            home.load(LoadType.REFRESH, state)
            fresh = true
            home.load(LoadType.REFRESH, state)
            val ids =
                database
                    .pagingTimelineDao()
                    .getTimelinePage(home.pagingKey, 0, 50)
                    .map {
                        it.statusData.content
                    }.filterNot { it.isPaginationGap }
                    .map { it.statusKey.id }
            assertEquals(listOf("40", "30", "20", "10"), ids)
            val gap =
                database
                    .pagingTimelineDao()
                    .getTimelinePage(home.pagingKey, 0, 50)
                    .map { it.statusData.content }
                    .filterIsInstance<UiTimelineV2.Gap>()
                    .single()
            home.loadGap(gap)
            val filled = database.pagingTimelineDao().getTimelinePage(home.pagingKey, 0, 50).map { it.statusData.content }
            assertEquals(listOf("50", "40", "30", "25", "20", "10"), filled.map { it.statusKey.id })
            tail = true
            home.load(LoadType.APPEND, state)
            assertEquals(
                listOf("50", "40", "30", "25", "20", "10", "5"),
                database.pagingTimelineDao().getTimelinePage(home.pagingKey, 0, 50).map { it.statusData.content.statusKey.id },
            )
        }

    @Test
    fun refreshOverlapDoesNotDropNewPostsFollowingADuplicate() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(30), post(20), post(10)), nextKey = "tail")
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "home"
                    override val canJoinTimeline = true

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ) = response
                }
            val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(40), post(20), post(35), post(30)), nextKey = "refresh-tail")
            home.load(LoadType.REFRESH, state)
            val keys = contents().filterNot { it.isPaginationGap }.map { it.statusKey.id }
            kotlin.test.assertTrue("35" in keys)
        }

    @Test
    fun refreshAndGapKeepOldRowsAndTailCursor() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10), post(11)), nextKey = "tail")
            val requests = mutableListOf<PagingRequest>()
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "home"

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        requests += request
                        return response
                    }
                }
            val mediator = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            val oldRows = database.pagingTimelineDao().getByPagingKey("home")
            response = PagingResult(listOf(post(1), post(2)), nextKey = "gap-one")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals(listOf("1", "2", gap.statusKey.id, "10", "11"), contents().map { it.statusKey.id })
            assertEquals("tail", database.pagingTimelineDao().getPagingKey("home")?.nextKey)
            response = PagingResult(listOf(post(0)), nextKey = "gap-two")
            mediator.load(LoadType.REFRESH, state)
            val partialFailure = IllegalStateException("one source failed")
            response = PagingResult(listOf(post(3), post(10)), nextKey = "gap-one-next", continuationFailure = partialFailure)
            val filled = mediator.loadGap(gap)
            val inserted = TimelinePagingMapper.toDb(listOf(post(3)), "home").single().timeline
            assertEquals(listOf("home_${inserted.statusId}"), filled.insertedKeys)
            assertEquals(partialFailure, filled.failure)
            assertEquals(
                listOf("0", "1", "2", "3", "10", "11"),
                contents().filterNot { it is UiTimelineV2.Gap }.map { it.statusKey.id },
            )
            assertEquals(listOf("gap-two", "gap-one-next"), contents().filterIsInstance<UiTimelineV2.Gap>().map { it.nextKey })
            assertEquals(
                oldRows,
                database.pagingTimelineDao().getByPagingKey("home").filter {
                    it._id in
                        oldRows.map { old ->
                            old._id
                        }
                },
            )
            assertEquals(PagingRequest.Append("gap-one"), requests.last())
            response = PagingResult(listOf(post(12)))
            mediator.load(LoadType.APPEND, state)
            assertEquals(PagingRequest.Append("tail"), requests.last())
            assertEquals("12", contents().last().statusKey.id)
        }

    @Test
    fun orderedSourceClosesAtBoundaryAndStaleGapResponseCannotResurrectClearedCache() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            var clear = false
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "home"
                    override val canJoinTimeline = true

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        if (clear) database.clearAllTables()
                        return response
                    }
                }
            val mediator = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            mediator.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(1)), nextKey = "gap")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            response = PagingResult(listOf(post(2), post(10), post(11)), nextKey = "after-boundary")
            mediator.loadGap(gap)
            assertEquals(listOf("1", "2", "10"), contents().map { it.statusKey.id })
            response = PagingResult(listOf(post(0)), nextKey = "other-gap")
            mediator.load(LoadType.REFRESH, state)
            val secondGap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            clear = true
            response = PagingResult(listOf(post(3)))
            assertEquals(emptyList(), mediator.loadGap(secondGap).insertedKeys)
            assertEquals(emptyList(), contents())
        }

    @Test
    fun gapRoundTripsWithoutPresentationState() =
        runTest {
            val gap =
                UiTimelineV2.Gap(
                    MicroBlogKey("gap", "timeline-gap"),
                    Instant.fromEpochMilliseconds(1).toUi(),
                    "cursor",
                    mapOf("home" to listOf("post")),
                    loading = true,
                    onActivate = {},
                )
            val converter =
                dev.dimension.flare.data.database.cache.model
                    .DbStatusContentConverter()
            val stored =
                dev.dimension.flare.data.database.cache.model.DbStatusContent
                    .encode(gap)
            val decoded = converter.toContent(converter.fromContent(stored))
            assertEquals(gap.copy(loading = false, onActivate = null), decoded.value)
        }

    @Test
    fun duplicatePagesAdvanceButFailedOrStuckPagesKeepTheirCursor() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            var failure = false
            val mediator =
                mediator {
                    if (failure) error("offline")
                    response
                }
            mediator.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            failure = true
            kotlin.test.assertFailsWith<IllegalStateException> { mediator.loadGap(gap) }
            assertEquals(gap, contents().filterIsInstance<UiTimelineV2.Gap>().single())
            failure = false
            response = PagingResult(listOf(post(10)), nextKey = "gap-next")
            mediator.loadGap(gap)
            val progressed = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals("gap-next", progressed.nextKey)
            kotlin.test.assertFailsWith<IllegalStateException> { mediator.loadGap(progressed) }
            assertEquals(progressed, contents().filterIsInstance<UiTimelineV2.Gap>().single())
        }

    @Test
    fun ordinaryRefreshReplacesHistoryAndRejectsRemovedGaps() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            val mediator = mediator { response }
            mediator.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            val disabled = mediator(remember = false) { PagingResult(listOf(post(100))) }
            disabled.load(LoadType.REFRESH, state)
            mediator.loadGap(gap)
            assertEquals(listOf("100"), contents().map { it.statusKey.id })
        }

    @Test
    fun visibilityKeepsGapsInTheList() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)))
            val home = mediator { response }
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            home.load(LoadType.REFRESH, state)
            val items = TimelineDbPageLoader(database, "home", TimelineDbPageCache()).load(0, 20)
            val visible =
                TimelineVisibility(
                    filters = listOf(KeywordFilterPattern("Post 0", isRegex = false)),
                    config = TimelineFilterConfig(),
                    mxgaEnabled = false,
                    mxgaSnapshot = MxgaSnapshot(),
                    platformRegistry = testPlatformRegistry(),
                )
            val displayed = items.map { it.baseItem }.filter(visible::isVisible)
            assertIs<UiTimelineV2.Gap>(displayed.first())
            val changed = visible.copy(filters = listOf(KeywordFilterPattern("Post 10", isRegex = false)))
            val updated = items.map { it.baseItem }.filter(changed::isVisible)
            assertIs<UiTimelineV2.Gap>(updated.last())
        }

    @Test
    fun repeatedlyFillingAGapRebalancesRanksWithoutChangingItsOrderOrKey() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(1000)))
            val mediator = mediator { response }
            mediator.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap-0")
            mediator.load(LoadType.REFRESH, state)
            val originalKey = contents().filterIsInstance<UiTimelineV2.Gap>().single().statusKey
            repeat(10) { index ->
                val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
                response = PagingResult(((index * 20 + 1)..(index * 20 + 20)).map { post(it) }, nextKey = "gap-${index + 1}")
                mediator.loadGap(gap)
            }
            val all = database.pagingTimelineDao().getTimelinePage("home", 0, 300).map { it.statusData.content }
            assertEquals(originalKey, all.filterIsInstance<UiTimelineV2.Gap>().single().statusKey)
            assertEquals((0..200).map(Int::toString) + "1000", all.filterNot { it is UiTimelineV2.Gap }.map { it.statusKey.id })
            val rows = database.pagingTimelineDao().getByPagingKey("home")
            assertEquals(rows.size, rows.map { it.sortId }.toSet().size)
        }

    @Test
    fun homeUsesTheOrdinarySourceCache() =
        runTest {
            val ordinary =
                TimelineRemoteMediator(
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "home"

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ) = PagingResult<UiTimelineV2>(listOf(post(99)))
                    },
                    database,
                    false,
                )
            ordinary.load(LoadType.REFRESH, state)
            var response = PagingResult<UiTimelineV2>(listOf(post(0)))
            val home = mediator(remember = false) { response }
            home.load(LoadType.REFRESH, state)
            response = PagingResult()
            home.load(LoadType.REFRESH, state)
            assertEquals(emptyList(), contents())
            assertEquals(emptyList(), database.pagingTimelineDao().getByPagingKey("home"))
        }

    @Test
    fun ordinaryMixedTimelineStillReportsPartialSourceErrors() =
        runTest {
            val error = IllegalStateException("account expired")
            val errors = mutableListOf<Throwable>()
            val sources =
                (0..1).map { index ->
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "source-$index"

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            if (index == 1) throw error
                            return PagingResult(listOf(post(1)))
                        }
                    }
                }
            val loader =
                dev.dimension.flare.data.datasource.microblog
                    .MixedRemoteMediator(database, sources)
            val ordinary = TimelineRemoteMediator(loader, database, false, notifyError = { errors += it })
            assertIs<androidx.paging.RemoteMediator.MediatorResult.Success>(ordinary.load(LoadType.REFRESH, state))
            kotlin.test.assertSame(error, errors.single())
            assertEquals(1, database.pagingTimelineDao().getByPagingKey(loader.pagingKey).size)
        }

    @Test
    fun cancelledGapCannotCommitEvenWhenTheTransportReturnsALateResponse() =
        runTest {
            val completion = CompletableDeferred<Unit>()
            var waitForResponse = false
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            val home =
                mediator {
                    if (waitForResponse) withContext(NonCancellable) { completion.await() }
                    response
                }
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            home.load(LoadType.REFRESH, state)
            val before = contents()
            val gap = before.filterIsInstance<UiTimelineV2.Gap>().single()
            waitForResponse = true
            response = PagingResult(listOf(post(1)), nextKey = "after-gap")
            val job = launch { home.loadGap(gap) }
            runCurrent()
            job.cancel()
            completion.complete(Unit)
            job.join()
            assertEquals(before, contents())
        }

    @Test
    fun deletingAnAccountRemovesItsOpaqueGapContinuationAndRejectsTheOldMediator() =
        runTest {
            val account = MicroBlogKey("reader", "example.com")
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            val home = mediator(accountKeys = setOf(account)) { response }
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            home.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            database.clearHomeTimelinesForAccount(MicroBlogKey("unrelated", "example.com"))
            assertEquals(gap, contents().filterIsInstance<UiTimelineV2.Gap>().single())
            database.clearHomeTimelinesForAccount(account)
            response = PagingResult(listOf(post(1)))
            home.loadGap(gap)
            assertEquals(emptyList(), contents())
            assertEquals(null, database.pagingTimelineDao().getPagingKey("home"))
        }

    private fun mediator(
        remember: Boolean = true,
        accountKeys: Set<MicroBlogKey> = emptySet(),
        response: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) = TimelineRemoteMediator(
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey = "home"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ) = response(request)
        },
        database,
        false,
        retainHistory = remember,
        accountKeys = accountKeys,
    )

    private suspend fun contents() = database.pagingTimelineDao().getTimelinePage("home", 0, 300).map { it.statusData.content }

    private fun post(
        id: Int,
        text: String = "Post $id",
    ) = UiTimelineV2.Post(
        platformId = "xQt",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(text.toUiPlainText()),
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
