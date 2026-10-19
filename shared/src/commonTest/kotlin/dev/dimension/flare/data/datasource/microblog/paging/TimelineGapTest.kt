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
            val mediator = TimelineRemoteMediator(loader, database, false, rememberPosition = true)
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            val oldRows = database.pagingTimelineDao().getByPagingKey("home:home")
            response = PagingResult(listOf(post(1), post(2)), nextKey = "gap-one")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals(listOf("1", "2", gap.statusKey.id, "10", "11"), contents().map { it.statusKey.id })
            assertEquals("tail", database.pagingTimelineDao().getPagingKey("home:home")?.nextKey)
            response = PagingResult(listOf(post(0)), nextKey = "gap-two")
            mediator.load(LoadType.REFRESH, state)
            val partialFailure = IllegalStateException("one source failed")
            response = PagingResult(listOf(post(3), post(10)), nextKey = "gap-one-next", continuationFailure = partialFailure)
            val filled = mediator.loadGap(gap)
            val inserted = TimelinePagingMapper.toDb(listOf(post(3)), "home:home").single().timeline
            assertEquals(listOf("home:home_${inserted.statusId}"), filled.insertedKeys)
            assertEquals(partialFailure, filled.failure)
            assertEquals(
                listOf("0", "1", "2", "3", "10", "11"),
                contents().filterNot { it is UiTimelineV2.Gap }.map { it.statusKey.id },
            )
            assertEquals(listOf("gap-two", "gap-one-next"), contents().filterIsInstance<UiTimelineV2.Gap>().map { it.nextKey })
            assertEquals(
                oldRows,
                database.pagingTimelineDao().getByPagingKey("home:home").filter {
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
            val mediator = TimelineRemoteMediator(loader, database, false, rememberPosition = true)
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
    fun aThousandPrependsKeepABoundedWindowAroundTheSamePost() =
        runTest {
            var response = PagingResult<UiTimelineV2>((1000..1199).map { post(it) })
            val mediator = mediator { response }
            mediator.load(LoadType.REFRESH, state)
            val loader = TimelineDbPageLoader(database, "home:home", TimelineDbPageCache(), windowed = true)
            val item = loader.load(100, 1).single()
            val anchor = loader.windowKey(item)
            response = PagingResult((0..999).map { post(it) })
            mediator.load(LoadType.REFRESH, state)
            val source = OffsetFromStartPagingSource(loader) { anchor }
            try {
                val page =
                    assertIs<androidx.paging.PagingSource.LoadResult.Page<OffsetFromStartPagingKey, TimelinePageItem>>(
                        source.load(
                            androidx.paging.PagingSource.LoadParams
                                .Refresh(null, 40, false),
                        ),
                    )
                assertEquals(40, page.data.size)
                assertEquals(item.baseItem.stableItemKey, page.data[20].baseItem.stableItemKey)
                assertIs<OffsetFromStartPagingKey.Window>(page.prevKey)
                assertIs<OffsetFromStartPagingKey.Window>(page.nextKey)
            } finally {
                source.invalidate()
            }
        }

    @Test
    fun bookmarksRestorePerTabAndAnUnchangedOldWindowCannotOverwriteANewerOne() =
        runTest {
            val mediator = mediator { PagingResult((0..99).map { post(it) }) }
            mediator.load(LoadType.REFRESH, state)
            val loader = TimelineDbPageLoader(database, "home:home", TimelineDbPageCache())
            val items = loader.load(0, 40)
            val old = TimelineViewport(database, "home:home", "tab", this, this)
            val newer = TimelineViewport(database, "home:home", "tab", this, this)
            old.restore()
            newer.restore()
            old.positionChanged(items[10].baseItem.stableItemKey, -18.0)
            newer.positionChanged(items[20].baseItem.stableItemKey, -27.0)
            newer.savePosition()
            runCurrent()
            old.savePosition()
            runCurrent()
            val restored = TimelineViewport(database, "home:home", "tab", this, this)
            restored.restore()
            assertEquals(items[20].baseItem.stableItemKey, restored.target.value?.itemKey)
            assertEquals(-27.0, restored.target.value?.offset)
            val otherTab = TimelineViewport(database, "home:home", "other", this, this)
            otherTab.restore()
            assertEquals(items.first().baseItem.stableItemKey, otherTab.target.value?.itemKey)
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
    fun aModeChangeInvalidatesAllHomeGapsAndTheirLateResponses() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail")
            val mediator = mediator { response }
            mediator.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(0)), nextKey = "gap")
            mediator.load(LoadType.REFRESH, state)
            val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
            val disabled = mediator(remember = false) { PagingResult(listOf(post(100))) }
            disabled.prepareHomeTimeline()
            mediator.loadGap(gap)
            assertEquals(emptyList(), contents())
            disabled.load(LoadType.REFRESH, state)
            assertEquals(listOf("100"), contents().map { it.statusKey.id })
        }

    @Test
    fun newContentIsAnnouncedEvenWhenTheCurrentPostUsedToBeTheHead() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(post(10)))
            val mediator = mediator { response }
            mediator.load(LoadType.REFRESH, state)
            val viewport = TimelineViewport(database, "home:home", "tab", this, this)
            viewport.restore()
            viewport.target.value = null
            val original = viewport.current.value
            response = PagingResult(listOf(post(0)))
            mediator.load(LoadType.REFRESH, state)
            viewport.refreshHead()
            kotlin.test.assertTrue(viewport.hasNewContent.value)
            assertEquals(original, viewport.current.value)
            viewport.showLatest()
            runCurrent()
            kotlin.test.assertFalse(viewport.hasNewContent.value)
            kotlin.test.assertNotEquals(original?.itemKey, viewport.target.value?.itemKey)
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
            val reader = TimelineViewport(database, "home:home", "ranked-tab", this, this)
            reader.restore()
            val lastKey = contents().last().stableItemKey
            reader.positionChanged(lastKey, -9.0)
            reader.savePosition()
            runCurrent()
            repeat(10) { index ->
                val gap = contents().filterIsInstance<UiTimelineV2.Gap>().single()
                response = PagingResult(((index * 20 + 1)..(index * 20 + 20)).map { post(it) }, nextKey = "gap-${index + 1}")
                mediator.loadGap(gap)
            }
            val all = database.pagingTimelineDao().getTimelinePage("home:home", 0, 300).map { it.statusData.content }
            assertEquals(originalKey, all.filterIsInstance<UiTimelineV2.Gap>().single().statusKey)
            assertEquals((0..200).map(Int::toString) + "1000", all.filterNot { it is UiTimelineV2.Gap }.map { it.statusKey.id })
            val rows = database.pagingTimelineDao().getByPagingKey("home:home")
            assertEquals(rows.size, rows.map { it.sortId }.toSet().size)
            assertEquals(
                database.pagingTimelineDao().getRowByItemKey("home:home", lastKey)?.sortId,
                database.pagingTimelineDao().getPosition("ranked-tab")?.sortId,
            )
        }

    @Test
    fun aFilteredOrDeletedBookmarkFallsBackToANearbyRealPost() =
        runTest {
            mediator { PagingResult((0..99).map { post(it) }) }.load(LoadType.REFRESH, state)
            val items = TimelineDbPageLoader(database, "home:home", TimelineDbPageCache()).load(0, 100)
            val original = TimelineViewport(database, "home:home", "tab", this, this)
            original.restore()
            original.positionChanged(items[50].baseItem.stableItemKey, -31.0)
            original.savePosition()
            runCurrent()
            val filtered = TimelineViewport(database, "home:home", "tab", this, this)
            filtered.setVisibility("filter") {
                it.statusKey.id
                    .toIntOrNull()
                    ?.let { id -> id < 50 } == true
            }
            filtered.restore()
            assertEquals(items[49].baseItem.stableItemKey, filtered.target.value?.itemKey)
            assertEquals(0.0, filtered.target.value?.offset)
            database.pagingTimelineDao().delete(
                listOf(checkNotNull(database.pagingTimelineDao().getRowByItemKey("home:home", items[50].baseItem.stableItemKey))),
            )
            val deleted = TimelineViewport(database, "home:home", "tab", this, this)
            deleted.restore()
            assertEquals(items[51].baseItem.stableItemKey, deleted.target.value?.itemKey)
            filtered.setVisibility("all-hidden") { false }
            runCurrent()
            assertEquals(null, filtered.target.value)
            assertEquals(null, filtered.current.value)
        }

    @Test
    fun disabledHomeRefreshDoesNotDeleteTheSameSourcesOrdinaryCache() =
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
            assertEquals(1, database.pagingTimelineDao().getByPagingKey("home").size)
        }

    @Test
    fun openingAnUnscrolledWindowCannotOverwriteAnotherWindowsRealReadingPosition() =
        runTest {
            mediator { PagingResult((0..99).map { post(it) }) }.load(LoadType.REFRESH, state)
            val items = TimelineDbPageLoader(database, "home:home", TimelineDbPageCache()).load(0, 100)
            val active = TimelineViewport(database, "home:home", "tab", this, this)
            active.restore()
            active.positionChanged(items[80].baseItem.stableItemKey, -15.0)
            val untouched = TimelineViewport(database, "home:home", "tab", this, this)
            untouched.restore()
            untouched.savePosition()
            runCurrent()
            active.savePosition()
            runCurrent()
            assertEquals(items[80].baseItem.stableItemKey, database.pagingTimelineDao().getPosition("tab")?.itemKey)
            database.pagingTimelineDao().delete(
                listOf(checkNotNull(database.pagingTimelineDao().getRowByItemKey("home:home", items[80].baseItem.stableItemKey))),
            )
            active.refreshHead()
            assertEquals(items[81].baseItem.stableItemKey, active.target.value?.itemKey)
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
    fun queuedPositionSaveCannotResurrectADeletedTabBeforeItsPresenterIsDisposed() =
        runTest {
            mediator { PagingResult((0..10).map { post(it) }) }.load(LoadType.REFRESH, state)
            var tabExists = true
            val viewport = TimelineViewport(database, "home:home", "tab", this, this) { tabExists }
            viewport.restore()
            viewport.savePosition()
            runCurrent()
            viewport.positionChanged(checkNotNull(viewport.current.value).itemKey, -12.0)
            viewport.savePosition()
            tabExists = false
            database.pagingTimelineDao().deleteTabPosition("tab")
            runCurrent()
            assertEquals(null, database.pagingTimelineDao().getPosition("tab"))
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
            val reader = TimelineViewport(database, "home:home", "tab", this, this)
            reader.restore()
            reader.savePosition()
            runCurrent()
            database.clearHomeTimelinesForAccount(MicroBlogKey("unrelated", "example.com"))
            assertEquals(gap, contents().filterIsInstance<UiTimelineV2.Gap>().single())
            database.clearHomeTimelinesForAccount(account)
            response = PagingResult(listOf(post(1)))
            home.loadGap(gap)
            reader.positionChanged(checkNotNull(reader.current.value).itemKey, -5.0)
            reader.savePosition()
            runCurrent()
            assertEquals(emptyList(), contents())
            assertEquals(null, database.pagingTimelineDao().getPagingKey("home:home"))
            assertEquals(null, database.pagingTimelineDao().getPosition("tab"))
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
        rememberPosition = remember,
        accountKeys = accountKeys,
    )

    private suspend fun contents() = database.pagingTimelineDao().getTimelinePage("home:home", 0, 300).map { it.statusData.content }

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
