package dev.dimension.flare.data.datasource.microblog.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.clearHomeTimelinesForAccount
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.repository.KeywordFilterPattern
import dev.dimension.flare.data.repository.MxgaSnapshot
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    fun cursorOnlyWritesInvalidatePagingMetadata() =
        runTest {
            val dao = database.pagingTimelineDao()
            dao.insertPagingKey(DbPagingKey("cursor-version", nextKey = "next", prevKey = "previous"))
            val before = assertNotNull(dao.getPagingKeyMetadata("cursor-version"))
            dao.updatePagingKeyNextKey("cursor-version", "replacement")
            val appended = assertNotNull(dao.getPagingKeyMetadata("cursor-version"))
            assertEquals(before.generation, appended.generation)
            assertEquals(before.revision + 1, appended.revision)
            dao.updatePagingKeyPrevKey("cursor-version", "new-previous")
            val prepended = assertNotNull(dao.getPagingKeyMetadata("cursor-version"))
            assertEquals(appended.revision + 1, prepended.revision)
            assertEquals("replacement", dao.getNextKey("cursor-version"))
            assertEquals("new-previous", prepended.prevKey)
        }

    @Test
    fun mixedAppendHonorsADirectCursorWriterAfterCachingTheFrontier() =
        runTest {
            val source =
                source("direct-frontier") { request ->
                    assertEquals(PagingRequest.Refresh, request)
                    PagingResult(listOf(datedPost(300), datedPost(200)))
                }
            val loader = MixedRemoteMediator(database, listOf(source), TimelineMergePolicy.Time)
            val home = TimelineRemoteMediator(loader, database, false, retainHistory = false)
            val one = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
            home.load(LoadType.REFRESH, one)
            database.pagingTimelineDao().updatePagingKeyNextKey(home.pagingKey, null)
            val result = home.load(LoadType.APPEND, one)
            assertTrue(assertIs<RemoteMediator.MediatorResult.Success>(result).endOfPaginationReached)
            assertEquals(listOf("300"), contents(home).map { it.statusKey.id })
        }

    @Test
    fun mixedAppendObservesAnotherWriterAndCanReopenAfterPendingIsConsumed() =
        runTest {
            val requests = mutableListOf<PagingRequest>()
            val source =
                source("shared-frontier") { request ->
                    requests += request
                    assertEquals(PagingRequest.Refresh, request)
                    PagingResult(listOf(datedPost(300), datedPost(200), datedPost(100)))
                }
            val loader = MixedRemoteMediator(database, listOf(source), TimelineMergePolicy.Time)

            fun home() = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            val oneItem = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
            val first = home()
            assertIs<RemoteMediator.MediatorResult.Success>(first.load(LoadType.REFRESH, oneItem))
            assertEquals(listOf("300"), contents(first).map { it.statusKey.id })
            assertIs<RemoteMediator.MediatorResult.Success>(home().load(LoadType.APPEND, oneItem))
            assertEquals(listOf("300", "200"), contents(first).map { it.statusKey.id })
            assertIs<RemoteMediator.MediatorResult.Success>(first.load(LoadType.APPEND, oneItem))
            assertEquals(listOf("300", "200", "100"), contents(first).map { it.statusKey.id })
            assertTrue(assertIs<RemoteMediator.MediatorResult.Success>(home().load(LoadType.APPEND, oneItem)).endOfPaginationReached)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), requests)
        }

    @Test
    fun timeMixedRefreshKeepsTheSamePagingFrontierWithOrWithoutRememberingPosition() =
        runTest {
            for (kind in listOf("ordered", "ranked", "featured")) {
                for (batchSize in listOf(1, 20)) {
                    suspend fun replay(remember: Boolean): List<List<String>> {
                        val name = "$kind-$batchSize-$remember"

                        fun page(
                            platform: String,
                            head: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> {
                            val start = if (request is PagingRequest.Append) request.nextKey.toInt() else head
                            return PagingResult(
                                (start downTo start - batchSize + 1).map {
                                    datedPost(it).copy(statusKey = MicroBlogKey("$platform-$it", "$platform.example"))
                                },
                                nextKey = (start - batchSize).toString(),
                            )
                        }
                        val loader =
                            MixedRemoteMediator(
                                database,
                                listOf(
                                    source("fast-$name") { page("fast", 400, it) },
                                    source("slow-$name", ordered = kind == "ordered", refreshGapOncePerSession = kind == "featured") {
                                        page("slow", 100, it)
                                    },
                                ),
                                TimelineMergePolicy.Time,
                            )
                        val home =
                            TimelineRemoteMediator(
                                loader,
                                database,
                                false,
                                retainHistory = remember,
                                refreshSession = HomeTimelineRefreshSessions().forTimeline("home", loader.pagingKey),
                            )
                        val snapshots = mutableListOf<List<String>>()
                        repeat(5) { index ->
                            val type = if (index < 3) LoadType.REFRESH else LoadType.APPEND
                            assertIs<RemoteMediator.MediatorResult.Success>(home.load(type, state))
                            snapshots += contents(home).map { it.statusKey.id }
                        }
                        assertEquals((400 downTo 401 - batchSize).map { "fast-$it" }, snapshots.first())
                        return snapshots
                    }
                    assertEquals(replay(false), replay(true), "$kind batchSize=$batchSize")
                }
            }
        }

    @Test
    fun timeMixedRefreshKeepsOlderFetchedPostsBufferedAndOrderedAcrossReopening() =
        runTest {
            for (ordered in listOf(false, true)) {
                for (nestedPolicy in listOf(null) + TimelineMergePolicy.entries) {
                    var fresh = false
                    val requests = mutableListOf<PagingRequest>()
                    val name = "$ordered-$nestedPolicy"
                    val fast =
                        source("fast-pending-$name") { request ->
                            if (request is PagingRequest.Append) {
                                PagingResult(listOf(datedPost(300)))
                            } else {
                                PagingResult(listOf(datedPost(400)), nextKey = "fast-tail")
                            }
                        }
                    val slow =
                        source("slow-pending-$name", ordered = ordered) { request ->
                            requests += request
                            if (request is PagingRequest.Append) {
                                assertEquals(if (ordered) "old-tail" else "new-tail", request.nextKey)
                                PagingResult(listOf(datedPost(if (ordered) 50 else 75)))
                            } else {
                                PagingResult(listOf(datedPost(if (fresh) 200 else 100)), nextKey = if (fresh) "new-tail" else "old-tail")
                            }
                        }
                    val sources =
                        listOf(fast, slow).map {
                            if (nestedPolicy != null) MixedRemoteMediator(database, listOf(it), nestedPolicy) else it
                        }
                    val loader = MixedRemoteMediator(database, sources, TimelineMergePolicy.Time)

                    fun home() = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                    val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
                    home().load(LoadType.REFRESH, oneRow)
                    fresh = true
                    repeat(2) { home().load(LoadType.REFRESH, oneRow) }
                    assertEquals(listOf("400"), contents(home()).map { it.statusKey.id })
                    assertTrue(contents(home()).none { it is UiTimelineV2.Gap })
                    requests.clear()
                    val expected = listOf("400", "300", "200", "100", if (ordered) "50" else "75")
                    repeat(4) { index ->
                        val reopened = home()
                        assertIs<RemoteMediator.MediatorResult.Success>(reopened.load(LoadType.APPEND, oneRow))
                        assertEquals(expected.take(index + 2), contents(reopened).map { it.statusKey.id }, name)
                    }
                    assertEquals(1, requests.size)
                    assertEquals(null, database.pagingTimelineDao().getNextKey(loader.pagingKey))
                }
            }
        }

    @Test
    fun timeMixedRecommendationsRefreshTheOrdinaryTailWithoutCreatingGaps() =
        runTest {
            for ((nestedPolicy, once) in (listOf(null) + TimelineMergePolicy.entries).flatMap { policy ->
                listOf(false, true).map { policy to it }
            }) {
                var fresh = false
                val requests = mutableListOf<PagingRequest>()
                val ranked =
                    source("recommendations-$nestedPolicy-$once", ordered = false, refreshGapOncePerSession = once) { request ->
                        requests += request
                        when (request) {
                            is PagingRequest.Append -> {
                                PagingResult(listOf(datedPost(50)))
                            }

                            else -> {
                                PagingResult(
                                    listOf(datedPost(if (fresh) 200 else 100)),
                                    nextKey = if (fresh) "new-tail" else "old-tail",
                                )
                            }
                        }
                    }
                val inner = if (nestedPolicy == null) ranked else MixedRemoteMediator(database, listOf(ranked), nestedPolicy)
                val loader = MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.Time)
                val home =
                    TimelineRemoteMediator(
                        loader,
                        database,
                        false,
                        retainHistory = true,
                        refreshSession = HomeTimelineRefreshSessions().forTimeline("home", loader.pagingKey),
                    )
                home.load(LoadType.REFRESH, state)
                fresh = true
                repeat(2) { home.load(LoadType.REFRESH, state) }
                assertEquals(listOf("200", "100"), contents(home).map { it.statusKey.id }, "$nestedPolicy")
                requests.clear()
                home.load(LoadType.APPEND, state)
                assertEquals(listOf<PagingRequest>(PagingRequest.Append("new-tail")), requests)
                assertEquals(listOf("200", "100", "50"), contents(home).map { it.statusKey.id })
            }
        }

    @Test
    fun timeMixedGapsOnlyFetchOrderedSourcesAndKeepTheirOriginalTail() =
        runTest {
            for (nestedPolicy in listOf(null) + TimelineMergePolicy.entries) {
                var fresh = false
                val requests = mutableListOf<Pair<String, PagingRequest>>()
                val requestsMutex = Mutex()
                val ordered =
                    source("ordered-$nestedPolicy") { request ->
                        requestsMutex.withLock { requests += "ordered" to request }
                        when (request) {
                            is PagingRequest.Append -> {
                                if (request.nextKey == "ordered-gap") {
                                    PagingResult(listOf(datedPost(250), datedPost(100)), nextKey = "joined")
                                } else {
                                    PagingResult(listOf(datedPost(80)))
                                }
                            }

                            else -> {
                                PagingResult(
                                    listOf(datedPost(if (fresh) 300 else 100)),
                                    nextKey = if (fresh) "ordered-gap" else "ordered-tail",
                                )
                            }
                        }
                    }
                val ranked =
                    source("ranked-$nestedPolicy", ordered = false) { request ->
                        requestsMutex.withLock { requests += "ranked" to request }
                        if (request is PagingRequest.Append) {
                            PagingResult(listOf(datedPost(50)))
                        } else {
                            PagingResult(listOf(datedPost(if (fresh) 200 else 90)), nextKey = if (fresh) "ranked-new" else "ranked-old")
                        }
                    }
                val sources =
                    if (nestedPolicy == null) {
                        listOf(ordered, ranked)
                    } else {
                        listOf(MixedRemoteMediator(database, listOf(ordered, ranked), nestedPolicy))
                    }
                val home = mixed(*sources.toTypedArray())
                home.load(LoadType.REFRESH, state)
                fresh = true
                home.load(LoadType.REFRESH, state)
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                requests.clear()
                home.loadGap(gap)
                assertEquals(
                    listOf<Pair<String, PagingRequest>>("ordered" to PagingRequest.Append("ordered-gap")),
                    requests,
                    "$nestedPolicy",
                )
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                requests.clear()
                home.load(LoadType.APPEND, state)
                assertEquals(
                    setOf<Pair<String, PagingRequest>>(
                        "ordered" to PagingRequest.Append("ordered-tail"),
                        "ranked" to PagingRequest.Append("ranked-new"),
                    ),
                    requests.toSet(),
                    "$nestedPolicy",
                )
                assertEquals(listOf("300", "250", "200", "100", "90", "80", "50"), contents(home).map { it.statusKey.id })
            }
        }

    @Test
    fun timeMixedRecommendationRefreshPreservesPendingAndHandlesEmptyOrFailedPages() =
        runTest {
            for ((nestedPolicy, outcome) in (listOf(null) + TimelineMergePolicy.entries).flatMap { policy ->
                listOf("empty", "ended", "failed").map { policy to it }
            }) {
                var fresh = false
                val requests = mutableListOf<PagingRequest>()
                val ranked =
                    source("buffered-$outcome-$nestedPolicy", ordered = false) { request ->
                        requests += request
                        when {
                            request is PagingRequest.Append -> PagingResult(listOf(datedPost(70)))
                            !fresh -> PagingResult(listOf(datedPost(90), datedPost(80)), nextKey = "old-tail")
                            outcome == "failed" -> error("offline")
                            outcome == "empty" -> PagingResult()
                            else -> PagingResult(listOf(datedPost(200)))
                        }
                    }
                val ordered = source("joined-$outcome-$nestedPolicy") { PagingResult(listOf(datedPost(100))) }
                val home =
                    if (nestedPolicy == null) {
                        mixed(ordered, ranked)
                    } else {
                        mixed(MixedRemoteMediator(database, listOf(ordered, ranked), nestedPolicy))
                    }
                val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
                home.load(LoadType.REFRESH, oneRow)
                assertEquals(listOf("100"), contents(home).map { it.statusKey.id })
                fresh = true
                home.load(LoadType.REFRESH, oneRow)
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                requests.clear()
                repeat(2) { home.load(LoadType.APPEND, state) }
                assertTrue(contents(home).map { it.statusKey.id }.containsAll(listOf("100", "90", "80")), outcome)
                assertEquals(
                    if (outcome ==
                        "failed"
                    ) {
                        listOf<PagingRequest>(PagingRequest.Append("old-tail"))
                    } else {
                        emptyList()
                    },
                    requests,
                    outcome,
                )
            }
        }

    @Test
    fun timeMixedEndedRecommendationRefreshClearsTheOldTail() =
        runTest {
            for (empty in listOf(false, true)) {
                var fresh = false
                val requests = mutableListOf<PagingRequest>()
                val ranked =
                    source("ended-$empty", ordered = false) { request ->
                        requests += request
                        if (!fresh) {
                            PagingResult(listOf(datedPost(100)), nextKey = "old-tail")
                        } else {
                            PagingResult(if (empty) emptyList() else listOf(datedPost(200)))
                        }
                    }
                val home = mixed(ranked)
                home.load(LoadType.REFRESH, state)
                fresh = true
                home.load(LoadType.REFRESH, state)
                assertEquals(null, database.pagingTimelineDao().getNextKey(home.pagingKey))
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                requests.clear()
                home.load(LoadType.APPEND, state)
                assertEquals(emptyList(), requests)
                assertTrue(contents(home).any { it.statusKey.id == "100" })
            }
        }

    @Test
    fun legacyTimeMixedGapsPublishPendingAndMigrateOnlyTheirOrderedContinuations() =
        runTest {
            for (nestedPolicy in listOf(null) + TimelineMergePolicy.entries) {
                for ((onlyRecommendations, ended) in listOf(false to false, true to false, true to true)) {
                    var generation = 0
                    val requests = mutableListOf<Pair<String, PagingRequest>>()
                    val requestsMutex = Mutex()
                    val ranked =
                        source("legacy-ranked-$nestedPolicy-$onlyRecommendations-$ended", ordered = false) { request ->
                            requestsMutex.withLock { requests += "ranked" to request }
                            if (request is PagingRequest.Append) {
                                PagingResult(listOf(datedPost(50)))
                            } else if (generation == 2 && ended) {
                                PagingResult()
                            } else {
                                PagingResult(
                                    listOf(datedPost(90 + generation * 100), datedPost(80 + generation * 100)),
                                    nextKey = "ranked-$generation",
                                )
                            }
                        }
                    val ordered =
                        source("legacy-ordered-$nestedPolicy-$onlyRecommendations-$ended") { request ->
                            requestsMutex.withLock { requests += "ordered" to request }
                            if (request is PagingRequest.Append) {
                                if (request.nextKey == "ordered-1") {
                                    PagingResult(listOf(datedPost(150), datedPost(100)), nextKey = "joined")
                                } else {
                                    PagingResult(listOf(datedPost(70)))
                                }
                            } else {
                                PagingResult(listOf(datedPost(if (generation == 0) 100 else 300)), nextKey = "ordered-$generation")
                            }
                        }
                    val sources = if (onlyRecommendations) listOf(ranked) else listOf(ordered, ranked)
                    val inner = if (nestedPolicy == null) sources else listOf(MixedRemoteMediator(database, sources, nestedPolicy))
                    val loader = MixedRemoteMediator(database, inner, TimelineMergePolicy.Time)
                    val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                    home.load(LoadType.REFRESH, state)
                    val cached = contents(home).map { it.statusKey.id }
                    val metadata = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey))
                    generation = 1
                    val page = loader.pageLoader().load(1, PagingRequest.Refresh)
                    val gap =
                        UiTimelineV2.Gap(
                            statusKey = MicroBlogKey("legacy-$nestedPolicy-$onlyRecommendations-$ended", "timeline-gap"),
                            createdAt = Instant.fromEpochMilliseconds(300).toUi(),
                            nextKey = assertNotNull(page.nextKey),
                            lowerBound = Json.decodeFromString(metadata.sourceHeads),
                        )
                    val sortId = assertNotNull(database.pagingTimelineDao().getFirstRow(home.pagingKey)).sortId - 1
                    saveToDatabase(
                        database,
                        TimelinePagingMapper.toDb(
                            page.data + gap,
                            home.pagingKey,
                            sortIds = page.data.map { assertNotNull(loader.sortId(it)) * 2 } + sortId,
                        ),
                    )
                    if (onlyRecommendations) {
                        generation = 2
                        home.load(LoadType.REFRESH, state)
                    }
                    requests.clear()
                    home.loadGap(gap)
                    assertEquals(
                        if (onlyRecommendations) {
                            emptyList()
                        } else {
                            listOf<Pair<String, PagingRequest>>("ordered" to PagingRequest.Append("ordered-1"))
                        },
                        requests,
                    )
                    assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                    assertTrue(contents(home).map { it.statusKey.id }.containsAll(listOf("190", "180") + cached))
                    requests.clear()
                    home.load(LoadType.APPEND, state)
                    assertTrue(contents(home).map { it.statusKey.id }.containsAll(listOf("90", "80")))
                    if (ended) {
                        assertEquals(emptyList(), requests)
                    } else {
                        assertTrue(("ranked" to PagingRequest.Append(if (onlyRecommendations) "ranked-2" else "ranked-0")) in requests)
                    }
                }
            }
        }

    @Test
    fun orderedRefreshOverlapClosesForSingleAndEveryMixedPolicy() =
        runTest {
            for (policy in listOf(null) + TimelineMergePolicy.entries) {
                var refreshing = false
                val ordered =
                    source("overlapping-$policy") {
                        PagingResult((if (refreshing) listOf(100, 90) else listOf(90, 80, 60)).map(::datedPost), nextKey = "tail")
                    }
                val other = source("unchanged-$policy") { PagingResult(listOf(datedPost(95), datedPost(85))) }
                val loader = if (policy == null) ordered else MixedRemoteMediator(database, listOf(ordered, other), policy)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                val tail = database.pagingTimelineDao().getNextKey(home.pagingKey)
                refreshing = true
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                assertTrue(contents(home).none { it is UiTimelineV2.Gap }, "$policy")
                assertEquals(listOf("100", "90", "80", "60"), contents(home).map { it.statusKey.id }.filter { it !in listOf("95", "85") })
                assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
            }
        }

    @Test
    fun legacyJoinedSingleSourceClosesWithoutFetchingOlderHistory() =
        runTest {
            val accounts =
                listOf(AccountType.Guest, AccountType.GuestHost("guest:host"), AccountType.Specific(MicroBlogKey("a:@,b", "host")))
            for ((account, feed) in accounts.flatMap { listOf(it to false, it to true) }) {
                for (alreadyFilled in listOf(false, true)) {
                    fun item(id: Int): UiTimelineV2 =
                        if (feed) {
                            UiTimelineV2.Feed(
                                title = "Article $id",
                                description = null,
                                url = "https://creator.fanbox.cc/posts/$id",
                                createdAt = datedPost(id).createdAt,
                                source = UiTimelineV2.Feed.Source("Creator", null),
                                accountType = account,
                            )
                        } else {
                            datedPost(id).copy(accountType = account, statusKey = MicroBlogKey("$id:🎨@,", "x:host"))
                        }
                    var ordered = false
                    var generation = 0
                    val requests = mutableListOf<PagingRequest>()
                    val original =
                        source("legacy-single-$account-$feed-$alreadyFilled", ordered = false) { request ->
                            requests += request
                            val ids =
                                when (request) {
                                    is PagingRequest.Append -> {
                                        if (request.nextKey == "tail-0") listOf(70) else listOf(85)
                                    }

                                    else -> {
                                        when (generation) {
                                            0 -> listOf(90, 80)
                                            1 -> listOf(100, 90)
                                            else -> listOf(110, 100)
                                        }
                                    }
                                }
                            PagingResult(ids.map(::item), nextKey = "tail-$generation")
                        }
                    val upgraded =
                        object : CacheableRemoteLoader<UiTimelineV2> by original {
                            override val canJoinTimeline: Boolean get() = ordered
                        }
                    val home = TimelineRemoteMediator(upgraded, database, false, retainHistory = true)
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                    generation = 1
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                    var gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                    if (alreadyFilled) {
                        home.loadGap(gap)
                        gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                    }
                    ordered = true
                    generation = 2
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                    val before = contents(home).filterNot { it is UiTimelineV2.Gap }
                    val tail = database.pagingTimelineDao().getNextKey(home.pagingKey)
                    requests.clear()
                    val result = home.loadGap(gap)
                    assertEquals(emptyList(), requests, "The original boundary was crossed before this cursor")
                    assertEquals(emptyList(), result.insertedKeys)
                    assertEquals(null, result.failure)
                    assertEquals(before, contents(home))
                    assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.APPEND, state))
                    assertEquals(listOf<PagingRequest>(PagingRequest.Append("tail-0")), requests)
                    assertEquals(item(70).statusKey, contents(home).last().statusKey)
                }
            }
        }

    @Test
    fun singleSourceRecoveryDoesNotGuessUnknownOrForeignBoundaries() =
        runTest {
            val post = datedPost(90)
            val notification = UiTimelineV2.TimelinePostItem(post, UiTimelineV2.PostPresentation(notificationKey = post.statusKey))
            val boundaries =
                listOf(
                    emptyList(),
                    listOf("invalid", "-1:", "2147483647:x", post.stableItemKey.dropLast(1)),
                    listOf(notification.stableItemKey),
                    listOf(post.copy(accountType = AccountType.Specific(MicroBlogKey("other", "x.com"))).stableItemKey),
                    listOf(datedPost(80).stableItemKey),
                )
            for ((index, boundary) in boundaries.withIndex()) {
                var phase = 0
                var requests = 0
                val loader =
                    source("uncertain-boundary-$index") { request ->
                        if (request is PagingRequest.Append) requests++
                        when (phase) {
                            0 -> PagingResult(listOf(post, datedPost(80)), nextKey = "tail")
                            1 -> PagingResult(listOf(datedPost(110), datedPost(100)), nextKey = "gap")
                            else -> PagingResult(listOf(datedPost(95)), nextKey = "gap-next")
                        }
                    }
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                phase = 1
                home.load(LoadType.REFRESH, state)
                val gap =
                    contents(
                        home,
                    ).filterIsInstance<UiTimelineV2.Gap>().single().copy(lowerBound = mapOf(loader.pagingKey to boundary))
                val row =
                    assertNotNull(
                        database.pagingTimelineDao().getRow(home.pagingKey, TimelinePagingMapper.toDb(post, home.pagingKey).timeline._id),
                    )
                saveToDatabase(database, listOf(TimelinePagingMapper.toDb(gap, home.pagingKey, row.sortId + 1)))
                phase = 2
                assertEquals(1, home.loadGap(gap).insertedKeys.size)
                assertEquals(1, requests)
                assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            }
        }

    @Test
    fun legacyJoinedMixedSourceClosesWithoutFetchingOlderHistory() =
        runTest {
            for (nestedPolicy in listOf(null) + TimelineMergePolicy.entries) {
                var ordered = false
                var generation = 0
                val requests = mutableListOf<PagingRequest>()
                val original =
                    source("formerly-ranked-$nestedPolicy", ordered = false) { request ->
                        requests += request
                        if (request is PagingRequest.Append) {
                            PagingResult(listOf(datedPost(70)))
                        } else {
                            PagingResult(
                                when (generation) {
                                    0 -> listOf(90, 80)
                                    1 -> listOf(100, 90)
                                    else -> listOf(110, 100, 90)
                                }.map(::datedPost),
                                nextKey = "tail-$generation",
                            )
                        }
                    }
                val upgraded =
                    object : CacheableRemoteLoader<UiTimelineV2> by original {
                        override val canJoinTimeline: Boolean get() = ordered
                    }
                val sources =
                    if (nestedPolicy == null) listOf(upgraded) else listOf(MixedRemoteMediator(database, listOf(upgraded), nestedPolicy))
                val loader = MixedRemoteMediator(database, sources, TimelineMergePolicy.TimePerPage)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                val tail = database.pagingTimelineDao().getNextKey(loader.pagingKey)
                generation = 1
                home.load(LoadType.REFRESH, state)
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                ordered = true
                requests.clear()
                val result = home.loadGap(gap)
                assertEquals(emptyList(), requests, "$nestedPolicy: this source already crossed its old boundary")
                assertEquals(emptyList(), result.insertedKeys)
                assertEquals(null, result.failure)
                assertEquals(listOf("100", "90", "80"), contents(home).map { it.statusKey.id })
                assertEquals(tail, database.pagingTimelineDao().getNextKey(loader.pagingKey))
                generation = 2
                home.load(LoadType.REFRESH, state)
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                requests.clear()
                home.load(LoadType.APPEND, state)
                assertEquals(listOf<PagingRequest>(PagingRequest.Append("tail-0")), requests)
                assertEquals("70", contents(home).last().statusKey.id)
            }
        }

    @Test
    fun legacyMixedGapKeepsRankedAndUnjoinedSourcesRetryable() =
        runTest {
            for (ranked in listOf(false, true)) {
                var ordered = false
                var phase = 0
                val requests = mutableListOf<String>()
                val failure = IllegalStateException("offline")
                val original =
                    source("joined-$ranked", ordered = false) { request ->
                        if (request is PagingRequest.Append) {
                            requests += "joined"
                            error("Already joined source must not fetch older history")
                        }
                        PagingResult((if (phase == 0) listOf(90, 70) else listOf(100, 90)).map(::datedPost), nextKey = "joined-tail")
                    }
                val upgraded =
                    object : CacheableRemoteLoader<UiTimelineV2> by original {
                        override val canJoinTimeline: Boolean get() = ordered
                    }
                val other =
                    source("other-$ranked", ordered = !ranked) { request ->
                        if (request is PagingRequest.Append) requests += "other"
                        when (phase) {
                            0 -> PagingResult(listOf(datedPost(95), datedPost(75)), nextKey = "ordinary-tail")
                            1 -> PagingResult((if (ranked) listOf(105, 95) else listOf(115, 105)).map(::datedPost), nextKey = "gap-tail")
                            2 -> throw failure
                            3 -> PagingResult(listOf(datedPost(if (ranked) 65 else 102)), nextKey = "gap-tail-2")
                            else -> PagingResult(listOf(datedPost(if (ranked) 65 else 95)), nextKey = "unused-tail")
                        }
                    }
                val inner = MixedRemoteMediator(database, listOf(upgraded, other), TimelineMergePolicy.TimePerPage)
                val loader = MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.TimePerPage)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                val tail = database.pagingTimelineDao().getNextKey(loader.pagingKey)
                phase = 1
                home.load(LoadType.REFRESH, state)
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                ordered = true
                requests.clear()
                phase = 2
                assertEquals(failure.message, kotlin.test.assertFailsWith<IllegalStateException> { home.loadGap(gap) }.message)
                assertEquals(listOf("other"), requests)
                assertEquals(gap, contents(home).filterIsInstance<UiTimelineV2.Gap>().single())
                phase = 3
                val partial = home.loadGap(gap)
                assertEquals(null, partial.failure)
                assertEquals(1, partial.insertedKeys.size)
                val remaining = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                assertEquals(gap.revision + 1, remaining.revision)
                phase = 4
                home.loadGap(remaining)
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                assertEquals(listOf("other", "other", "other"), requests)
                assertEquals(tail, database.pagingTimelineDao().getNextKey(loader.pagingKey))
            }
        }

    @Test
    fun legacyJoinedMixedGapPublishesPendingPostsBeforeClosing() =
        runTest {
            for (nested in listOf(false, true)) {
                var refreshing = false
                val requests = mutableListOf<PagingRequest>()
                val ordered =
                    source("joined-pending-$nested") { request ->
                        requests += request
                        check(request is PagingRequest.Refresh)
                        PagingResult((if (refreshing) listOf(100, 90, 80) else listOf(90)).map(::datedPost), nextKey = "tail")
                    }
                val inner = MixedRemoteMediator(database, listOf(ordered), TimelineMergePolicy.Time)
                val loader = if (nested) MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.Time) else inner
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                val metadata = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(loader.pagingKey))
                val tail = database.pagingTimelineDao().getNextKey(loader.pagingKey)
                refreshing = true
                val page = loader.pageLoader().load(1, PagingRequest.Refresh)
                val gap =
                    UiTimelineV2.Gap(
                        statusKey = MicroBlogKey("joined-pending-$nested", "timeline-gap"),
                        createdAt = Instant.fromEpochMilliseconds(100).toUi(),
                        nextKey = assertNotNull(page.nextKey),
                        lowerBound = Json.decodeFromString(metadata.sourceHeads),
                    )
                val gapSortId = assertNotNull(database.pagingTimelineDao().getFirstRow(loader.pagingKey)).sortId - 1
                saveToDatabase(
                    database,
                    TimelinePagingMapper.toDb(
                        page.data + gap,
                        loader.pagingKey,
                        sortIds = page.data.map { assertNotNull(loader.sortId(it)) * 2 } + gapSortId,
                    ),
                )
                requests.clear()
                val result = home.loadGap(gap)
                assertEquals(emptyList(), requests)
                assertEquals(null, result.failure)
                assertEquals(1, result.insertedKeys.size)
                assertEquals(listOf("100", "90", "80"), contents(home).map { it.statusKey.id })
                assertEquals(tail, database.pagingTimelineDao().getNextKey(loader.pagingKey))
            }
        }

    @Test
    fun joinedMixedSourcesBufferOlderPostsWithoutAnotherFeaturedGap() =
        runTest {
            for (nestedPolicy in listOf(null, TimelineMergePolicy.Time, TimelineMergePolicy.TimePerPage)) {
                var generation = 0
                val requests = mutableListOf<Pair<String, PagingRequest>>()
                val requestsMutex = Mutex()
                val featured =
                    source("featured-$nestedPolicy", ordered = false, refreshGapOncePerSession = true) { request ->
                        requestsMutex.withLock { requests += "featured" to request }
                        if (request is PagingRequest.Append) {
                            PagingResult(listOf(datedPost(100)))
                        } else {
                            PagingResult(
                                listOf(datedPost(100 + generation * 100)),
                                nextKey =
                                    if (generation ==
                                        0
                                    ) {
                                        null
                                    } else {
                                        "featured-$generation"
                                    },
                            )
                        }
                    }
                val ordered =
                    source("ordered-$nestedPolicy") { request ->
                        requestsMutex.withLock { requests += "ordered" to request }
                        if (request is PagingRequest.Append) {
                            PagingResult(listOf(datedPost(50)), nextKey = "older")
                        } else {
                            PagingResult(
                                (if (generation == 0) listOf(90) else listOf(90, 80, 70, 60)).map(::datedPost),
                                nextKey = "original-tail",
                            )
                        }
                    }
                val sources =
                    if (nestedPolicy ==
                        null
                    ) {
                        listOf(featured, ordered)
                    } else {
                        listOf(MixedRemoteMediator(database, listOf(featured, ordered), nestedPolicy))
                    }
                val loader = MixedRemoteMediator(database, sources, TimelineMergePolicy.Time)
                val smallPage = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(2), 0)
                TimelineRemoteMediator(loader, database, false, retainHistory = true).load(LoadType.REFRESH, smallPage)
                val home =
                    TimelineRemoteMediator(
                        loader,
                        database,
                        false,
                        retainHistory = true,
                        refreshSession = HomeTimelineRefreshSessions().forTimeline("home", loader.pagingKey),
                    )
                generation = 1
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, smallPage))
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                generation = 2
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, smallPage))
                assertTrue(contents(home).none { it is UiTimelineV2.Gap }, "$nestedPolicy")
                assertEquals(
                    listOf("300", "200", "100", "90"),
                    contents(home).filterNot { it is UiTimelineV2.Gap }.map { it.statusKey.id },
                )
                requests.clear()
                for (appendState in listOf(smallPage, state)) {
                    val result = home.load(LoadType.APPEND, appendState)
                    assertIs<RemoteMediator.MediatorResult.Success>(result)
                }
                assertEquals(
                    setOf<Pair<String, PagingRequest>>(
                        "featured" to PagingRequest.Append("featured-2"),
                        "ordered" to PagingRequest.Append("original-tail"),
                    ),
                    requests.toSet(),
                    "$nestedPolicy",
                )
                assertEquals(listOf("300", "200", "100", "90", "80", "70", "60", "50"), contents(home).map { it.statusKey.id })
            }
        }

    @Test
    fun fullCachedOrderedGapPageClosesEvenWithoutItsOriginalBoundary() =
        runTest {
            for (mixed in listOf(false, true)) {
                var response = PagingResult<UiTimelineV2>(listOf(datedPost(10)), nextKey = "ordinary-tail")
                val ordered = source("ordered-cached-$mixed") { response }
                val loader = if (mixed) MixedRemoteMediator(database, listOf(ordered), TimelineMergePolicy.Time) else ordered
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                val tail = database.pagingTimelineDao().getNextKey(loader.pagingKey)
                val gap =
                    UiTimelineV2.Gap(
                        statusKey = MicroBlogKey("old-gap-$mixed", "timeline-gap"),
                        createdAt = Instant.fromEpochMilliseconds(20).toUi(),
                        nextKey = if (mixed) """[{"started":true,"next":"gap-tail"}]""" else "gap-tail",
                    )
                saveToDatabase(database, TimelinePagingMapper.toDb(listOf(gap), loader.pagingKey))
                response = PagingResult(listOf(datedPost(10)), nextKey = "changed-cursor")
                val result = home.loadGap(gap)
                assertEquals(emptyList(), result.insertedKeys)
                assertEquals(null, result.failure)
                assertTrue(contents(home).none { it is UiTimelineV2.Gap }, "mixed=$mixed")
                assertEquals(tail, database.pagingTimelineDao().getNextKey(loader.pagingKey))
            }
        }

    @Test
    fun legacyMixedGapReturnsUnboundedHistoryToTheOrdinaryTail() =
        runTest {
            for ((hadTail, nested) in listOf(false to false, true to false, false to true, true to true)) {
                var refreshing = false
                val requests = mutableListOf<PagingRequest>()
                val primary = source("primary-$hadTail-$nested") { PagingResult(listOf(datedPost(if (refreshing) 200 else 100))) }
                val secondary =
                    source("secondary-$hadTail-$nested") { request ->
                        requests += request
                        when {
                            request is PagingRequest.Append -> PagingResult(listOf(datedPost(5)))
                            refreshing -> PagingResult(listOf(datedPost(90)), nextKey = "new-tail")
                            hadTail -> PagingResult(listOf(datedPost(10)), nextKey = "original-tail")
                            else -> error("Offline before this source first became available")
                        }
                    }
                val inner = MixedRemoteMediator(database, listOf(primary, secondary), TimelineMergePolicy.Time)
                val loader = if (nested) MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.Time) else inner
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
                home.load(LoadType.REFRESH, oneRow)
                val metadata = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(loader.pagingKey))
                refreshing = true
                val page = loader.pageLoader().load(1, PagingRequest.Refresh)
                val gap =
                    UiTimelineV2.Gap(
                        statusKey = MicroBlogKey("legacy-$hadTail-$nested", "timeline-gap"),
                        createdAt = Instant.fromEpochMilliseconds(200).toUi(),
                        nextKey = assertNotNull(page.nextKey),
                        lowerBound = Json.decodeFromString(metadata.sourceHeads),
                    )
                saveToDatabase(database, TimelinePagingMapper.toDb(page.data + gap, loader.pagingKey))
                requests.clear()
                val result = home.loadGap(gap, pageSize = 1)
                assertEquals(null, result.failure)
                assertTrue(contents(home).any { it.statusKey.id == "90" })
                assertTrue(contents(home).none { it is UiTimelineV2.Gap })
                val updatedHeads =
                    Json.decodeFromString<Map<String, List<String>>>(
                        assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(loader.pagingKey)).sourceHeads,
                    )
                assertEquals(listOf(datedPost(90).stableItemKey), updatedHeads[secondary.pagingKey])
                assertEquals(emptyList(), requests, "Already fetched history needs no network request")
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.APPEND, state))
                assertEquals(listOf<PagingRequest>(PagingRequest.Append(if (hadTail) "original-tail" else "new-tail")), requests)
                assertTrue(contents(home).any { it.statusKey.id == "5" })
            }
        }

    @Test
    fun recoveredMixedSourceWithoutAnOldBoundaryUsesOrdinaryPagination() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                var refreshing = false
                val appended = mutableListOf<PagingRequest>()
                val primary = source("primary-$policy") { PagingResult(listOf(datedPost(if (refreshing) 200 else 100))) }
                val secondary =
                    source("recovered-$policy") { request ->
                        check(refreshing) { "Initially unavailable" }
                        if (request is PagingRequest.Append) {
                            appended += request
                            PagingResult(listOf(datedPost(5)))
                        } else {
                            PagingResult(listOf(datedPost(90), datedPost(80)), nextKey = "history")
                        }
                    }
                val inner = MixedRemoteMediator(database, listOf(primary, secondary), policy)
                val loader = MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.Time)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
                home.load(LoadType.REFRESH, oneRow)
                if (policy == TimelineMergePolicy.Time) {
                    val dao = database.pagingTimelineDao()
                    val metadata = assertNotNull(dao.getPagingKeyMetadata(loader.pagingKey))
                    dao.updatePage(loader.pagingKey, null, true, metadata.prevKey, "{}", metadata.accountKeys)
                }
                refreshing = true
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, oneRow))
                assertEquals(listOf("200", "100"), contents(home).map { it.statusKey.id })
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.APPEND, state))
                assertEquals(listOf<PagingRequest>(PagingRequest.Append("history")), appended)
                assertEquals(listOf("200", "100", "90", "80", "5"), contents(home).map { it.statusKey.id })
            }
        }

    @Test
    fun nestedMixedGapKeepsSparseAndFailedContinuationsUntilTheyJoin() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                var phase = 0
                val offline = IllegalStateException("offline")
                val left =
                    source("left-$policy") {
                        when (phase) {
                            0 -> PagingResult(listOf(datedPost(10)))
                            1 -> PagingResult(listOf(datedPost(20)), nextKey = "left-one")
                            2 -> PagingResult(emptyList(), nextKey = "left-two")
                            else -> PagingResult(listOf(datedPost(10)), nextKey = "left-tail")
                        }
                    }
                val right =
                    source("right-$policy") {
                        when (phase) {
                            0 -> PagingResult(listOf(datedPost(5)))
                            1 -> PagingResult(listOf(datedPost(30)), nextKey = "right-one")
                            2 -> throw offline
                            else -> PagingResult(listOf(datedPost(25), datedPost(5)), nextKey = "right-tail")
                        }
                    }
                val inner = MixedRemoteMediator(database, listOf(left, right), policy)
                val loader = MixedRemoteMediator(database, listOf(inner), TimelineMergePolicy.Time)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                phase = 1
                home.load(LoadType.REFRESH, state)
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                phase = 2
                val partial = home.loadGap(gap)
                assertEquals(emptyList(), partial.insertedKeys)
                assertSame(offline, partial.failure)
                val remaining = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                assertEquals(gap.statusKey, remaining.statusKey)
                assertEquals(gap.revision + 1, remaining.revision)
                phase = 3
                val retry = home.loadGap(remaining)
                assertEquals(null, retry.failure)
                assertEquals(1, retry.insertedKeys.size)
                assertEquals(listOf("30", "25", "20", "10", "5"), contents(home).map { it.statusKey.id })
            }
        }

    @Test
    fun partialOverlapContinuationMustRemainAfterItsRefreshPage() =
        runTest {
            val violations = mutableListOf<String>()
            for (mixedPolicy in listOf<TimelineMergePolicy?>(
                null,
                TimelineMergePolicy.TimePerPage,
                TimelineMergePolicy.Staggered,
                TimelineMergePolicy.Time,
            )) {
                var response = PagingResult<UiTimelineV2>(listOf(datedPost(10), datedPost(9)), nextKey = "old-tail")
                val leaf = source("ranked-$mixedPolicy", ordered = false) { response }
                val loader = if (mixedPolicy == null) leaf else MixedRemoteMediator(database, listOf(leaf), mixedPolicy)
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                home.load(LoadType.REFRESH, state)
                response = PagingResult(listOf(datedPost(12), datedPost(10)), nextKey = "after-10")
                home.load(LoadType.REFRESH, state)
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().singleOrNull()
                response = PagingResult(listOf(datedPost(8)))
                if (mixedPolicy == TimelineMergePolicy.Time) {
                    home.load(LoadType.APPEND, state)
                } else {
                    home.loadGap(checkNotNull(gap))
                }
                val ids = contents(home).map { it.statusKey.id }
                if (ids.indexOf("8") < ids.indexOf("10")) violations += "$mixedPolicy: $ids"
            }
            assertEquals(emptyList(), violations, "Continuation item 8 must follow refresh-page item 10")
        }

    @Test
    fun singleRankedGapMustCloseOnAFullCachedPage() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(datedPost(10), datedPost(9)), nextKey = "old-tail")
            val featured = source("featured", ordered = false, refreshGapOncePerSession = true) { response }
            val home = TimelineRemoteMediator(featured, database, false, retainHistory = true)
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(datedPost(12)), nextKey = "gap-page")
            home.load(LoadType.REFRESH, state)
            val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            response = PagingResult(listOf(datedPost(10), datedPost(9)), nextKey = "another-page")
            home.loadGap(gap)
            val remaining = contents(home).filterIsInstance<UiTimelineV2.Gap>()
            assertTrue(remaining.isEmpty(), "A successful full-cached recommended page must finish backfill")
        }

    @Test
    fun gapBoundaryUpdateMustNotSilentlyDiscardConcurrentRefresh() =
        runTest {
            var primaryId = 30
            var delayRefresh = false
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val loader =
                MixedRemoteMediator(
                    database,
                    listOf(
                        source("primary") {
                            if (delayRefresh) {
                                entered.complete(Unit)
                                release.await()
                            }
                            PagingResult(listOf(datedPost(primaryId)))
                        },
                        source("secondary") { PagingResult(listOf(datedPost(20))) },
                    ),
                    TimelineMergePolicy.Time,
                )
            val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
            home.load(LoadType.REFRESH, oneRow)
            primaryId = 40
            val before = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey))
            val page = loader.pageLoader().load(1, PagingRequest.Refresh)
            val gap =
                UiTimelineV2.Gap(
                    statusKey = MicroBlogKey("legacy-concurrent", "timeline-gap"),
                    createdAt = Instant.fromEpochMilliseconds(40).toUi(),
                    nextKey = assertNotNull(page.nextKey),
                    lowerBound = Json.decodeFromString(before.sourceHeads),
                )
            saveToDatabase(database, TimelinePagingMapper.toDb(page.data + gap, home.pagingKey))
            primaryId = 50
            delayRefresh = true
            var result: RemoteMediator.MediatorResult? = null
            val refreshing = launch { result = home.load(LoadType.REFRESH, oneRow) }
            entered.await()
            home.loadGap(gap)
            val after = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey))
            release.complete(Unit)
            refreshing.join()
            assertTrue(after.revision > before.revision, "Gap fill must publish the missing source boundary")
            assertTrue(result is RemoteMediator.MediatorResult.Error, "A failed compare-and-set must be retryable")
            assertFalse(contents(home).any { it.statusKey.id == "50" })
            delayRefresh = false
            assertTrue(home.load(LoadType.REFRESH, oneRow) is RemoteMediator.MediatorResult.Success)
            assertTrue(contents(home).any { it.statusKey.id == "50" }, "Retry must read the new revision and commit")
        }

    @Test
    fun sharedPrependSourceRetriesItsInitialRefreshButDoesNotRepeatItAfterSuccess() =
        runTest {
            for (cached in listOf(false, true)) {
                var failure = false
                var requests = 0
                val source =
                    source("prepend-$cached") {
                        requests++
                        check(!failure) { "Offline" }
                        PagingResult(listOf(datedPost(20)), previousKey = "head")
                    }
                val loader =
                    object : CacheableRemoteLoader<UiTimelineV2> by source {
                        override val supportPrepend = true
                    }
                val home = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                if (cached) home.load(LoadType.REFRESH, state)
                requests = 0
                val ordinary = TimelineRemoteMediator(loader, database, false, retainedHome = { home })
                ordinary.initialize()
                if (cached) {
                    failure = true
                    assertIs<RemoteMediator.MediatorResult.Error>(ordinary.load(LoadType.PREPEND, state))
                    failure = false
                    assertIs<RemoteMediator.MediatorResult.Success>(ordinary.load(LoadType.PREPEND, state))
                    assertEquals(2, requests)
                } else {
                    assertIs<RemoteMediator.MediatorResult.Success>(ordinary.load(LoadType.REFRESH, state))
                    assertEquals(1, requests)
                }
                val completed = requests
                ordinary.load(LoadType.PREPEND, state)
                assertEquals(completed, requests, "The boundary after initial refresh must not hit the network again")
                ordinary.load(LoadType.REFRESH, state)
                ordinary.load(LoadType.PREPEND, state)
                assertEquals(completed + 1, requests, "Manual refresh must also finish its prepend boundary locally")
            }
        }

    @Test
    fun ordinaryAndHomeTimeAppendUseTheSameRankScaleWithReadingDisabled() =
        runTest {
            var id = 20
            val loader =
                MixedRemoteMediator(
                    database,
                    listOf(
                        source("time") {
                            PagingResult(listOf(datedPost(id)), nextKey = "after-$id")
                        },
                    ),
                    TimelineMergePolicy.Time,
                )
            val home = TimelineRemoteMediator(loader, database, false, retainHistory = false)
            val ordinary = TimelineRemoteMediator(loader, database, false)
            home.load(LoadType.REFRESH, state)
            id = 19
            ordinary.load(LoadType.APPEND, state)
            id = 18
            home.load(LoadType.APPEND, state)
            assertEquals(listOf("20", "19", "18"), contents(home).map { it.statusKey.id })
        }

    @Test
    fun partiallyOverlappingGapPageMovesOnlyItsContinuationPastCachedPosts() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(datedPost(10), datedPost(9), datedPost(7)), nextKey = "tail")
            val home = mediator { response }
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(datedPost(12)), nextKey = "gap-one")
            home.load(LoadType.REFRESH, state)
            val firstGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            response = PagingResult(listOf(datedPost(11), datedPost(9)), nextKey = "gap-two")
            home.loadGap(firstGap)
            val nextGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals(firstGap.statusKey, nextGap.statusKey)
            assertEquals(listOf("12", "11", "10", "9", nextGap.statusKey.id, "7"), contents(home).map { it.statusKey.id })
            response = PagingResult(listOf(datedPost(8)))
            home.loadGap(nextGap)
            assertEquals(listOf("12", "11", "10", "9", "8", "7"), contents(home).map { it.statusKey.id })
        }

    @Test
    fun featuredRefreshCreatesOneGapPerRunAndKeepsExistingGapsLoadable() =
        runTest {
            var generation = 0
            val requests = mutableListOf<PagingRequest>()
            val featured =
                source("featured", ordered = false, refreshGapOncePerSession = true) { request ->
                    requests += request
                    if (request is PagingRequest.Append) {
                        PagingResult(listOf(datedPost(5)))
                    } else {
                        PagingResult(listOf(datedPost(++generation * 10)), nextKey = "page-$generation")
                    }
                }

            fun home(sessions: HomeTimelineRefreshSessions? = null) =
                TimelineRemoteMediator(
                    featured,
                    database,
                    false,
                    retainHistory = true,
                    refreshSession = sessions?.forTimeline("home", featured.pagingKey),
                )
            home().load(LoadType.REFRESH, state)
            val sessions = HomeTimelineRefreshSessions()
            val first = home(sessions)
            listOf(
                launch { first.load(LoadType.REFRESH, state) },
                launch { home(sessions).load(LoadType.REFRESH, state) },
            ).forEach { it.join() }
            val gap = contents(first).filterIsInstance<UiTimelineV2.Gap>().single()
            home(sessions).load(LoadType.REFRESH, state)
            assertEquals(listOf(gap), contents(first).filterIsInstance<UiTimelineV2.Gap>())
            assertEquals(listOf("40", "30", "20", "10"), contents(first).filterIsInstance<UiTimelineV2.Post>().map { it.statusKey.id })
            assertEquals("page-1", database.pagingTimelineDao().getNextKey(first.pagingKey))
            home(HomeTimelineRefreshSessions()).load(LoadType.REFRESH, state)
            assertEquals(2, contents(first).filterIsInstance<UiTimelineV2.Gap>().size, "A new process permits another gap")
            first.loadGap(gap)
            assertEquals(PagingRequest.Append("page-2"), requests.last())
            assertEquals(1, contents(first).filterIsInstance<UiTimelineV2.Gap>().size)
        }

    @Test
    fun featuredEmptyOrDuplicateFirstRefreshConsumesItsOpportunity() =
        runTest {
            for (empty in listOf(false, true)) {
                var response = PagingResult<UiTimelineV2>(listOf(datedPost(10)), nextKey = "tail")
                val featured = source("featured-$empty", ordered = false, refreshGapOncePerSession = true) { response }
                TimelineRemoteMediator(featured, database, false, retainHistory = true).load(LoadType.REFRESH, state)
                val home =
                    TimelineRemoteMediator(
                        featured,
                        database,
                        false,
                        retainHistory = true,
                        refreshSession = HomeTimelineRefreshSessions().forTimeline("home", featured.pagingKey),
                    )
                if (empty) response = PagingResult()
                home.load(LoadType.REFRESH, state)
                response = PagingResult(listOf(datedPost(20)), nextKey = "unused")
                home.load(LoadType.REFRESH, state)
                assertEquals(0, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            }
        }

    @Test
    fun featuredFailureDoesNotConsumeItsFirstSuccessfulRefresh() =
        runTest {
            var fail = false
            var response = PagingResult<UiTimelineV2>(listOf(datedPost(10)), nextKey = "tail")
            val featured =
                source("featured", ordered = false, refreshGapOncePerSession = true) {
                    check(!fail) { "offline" }
                    response
                }
            TimelineRemoteMediator(featured, database, false, retainHistory = true).load(LoadType.REFRESH, state)
            val home =
                TimelineRemoteMediator(
                    featured,
                    database,
                    false,
                    retainHistory = true,
                    refreshSession = HomeTimelineRefreshSessions().forTimeline("home", featured.pagingKey),
                )
            fail = true
            assertIs<RemoteMediator.MediatorResult.Error>(home.load(LoadType.REFRESH, state))
            fail = false
            response = PagingResult(listOf(datedPost(20)), nextKey = "gap")
            home.load(LoadType.REFRESH, state)
            assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            response = PagingResult(listOf(datedPost(30)), nextKey = "unused")
            home.load(LoadType.REFRESH, state)
            assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
        }

    @Test
    fun mixedFeaturedRefreshPublishesBufferedPostsAndKeepsOnlyOtherSourcesInNewGaps() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                for (nested in listOf(false, true)) {
                    var generation = 0
                    val appended = mutableListOf<String>()
                    val featured =
                        source("featured-$policy-$nested", ordered = false, refreshGapOncePerSession = true) { request ->
                            if (request is PagingRequest.Append) {
                                appended += "featured"
                                PagingResult(listOf(datedPost(5)))
                            } else {
                                PagingResult(
                                    (generation * 100 + 40 downTo generation * 100 + 1).map(::datedPost),
                                    nextKey = "featured-$generation",
                                )
                            }
                        }
                    val ordered =
                        source("ordered") { request ->
                            if (request is PagingRequest.Append) {
                                appended += "ordered"
                                PagingResult(listOf(datedPost(15)))
                            } else {
                                PagingResult(listOf(datedPost(generation * 100 + 60)), nextKey = "ordered-$generation")
                            }
                        }
                    val inner = MixedRemoteMediator(database, listOf(featured, ordered), policy)
                    val loader = if (nested) MixedRemoteMediator(database, listOf(inner), policy) else inner
                    val seed = TimelineRemoteMediator(loader, database, false, retainHistory = true)
                    seed.load(LoadType.REFRESH, state)
                    seed.load(LoadType.APPEND, state)
                    appended.clear()
                    val home =
                        TimelineRemoteMediator(
                            loader,
                            database,
                            false,
                            retainHistory = true,
                            refreshSession = HomeTimelineRefreshSessions().forTimeline("home", loader.pagingKey),
                        )
                    generation = 1
                    home.load(LoadType.REFRESH, state)
                    val oldGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                    generation = 2
                    home.load(LoadType.REFRESH, state)
                    val newGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single { it.statusKey != oldGap.statusKey }
                    assertTrue(
                        contents(home).map { it.statusKey.id }.containsAll((201..240).map(Int::toString)),
                        "All fetched featured posts must be published: $policy nested=$nested",
                    )
                    home.loadGap(newGap)
                    assertEquals(listOf("ordered"), appended, "A new gap must not fetch featured pages")
                    assertEquals(listOf(oldGap), contents(home).filterIsInstance<UiTimelineV2.Gap>())
                    repeat(4) {
                        contents(home).filterIsInstance<UiTimelineV2.Gap>().find { it.statusKey == oldGap.statusKey }?.let {
                            home.loadGap(it)
                        }
                    }
                    assertEquals(
                        policy != TimelineMergePolicy.Time,
                        "featured" in appended,
                        "Only page-based gaps retain recommendation continuations",
                    )
                }
            }
        }

    @Test
    fun mixedFailedFeaturedSourceKeepsItsOwnFirstRefreshOpportunity() =
        runTest {
            var generation = 0
            var fail = false
            val appended = mutableListOf<Int>()
            val sources =
                (1..2).map { index ->
                    source("featured-$index", ordered = false, refreshGapOncePerSession = true) { request ->
                        check(index != 2 || !fail) { "offline" }
                        if (request is PagingRequest.Append) {
                            appended += index
                            PagingResult(listOf(datedPost(index)))
                        } else {
                            PagingResult(listOf(datedPost(generation * 100 + index * 10)), nextKey = "source-$index-$generation")
                        }
                    }
                }
            val loader = MixedRemoteMediator(database, sources, TimelineMergePolicy.TimePerPage)
            val seed = TimelineRemoteMediator(loader, database, false, retainHistory = true)
            seed.load(LoadType.REFRESH, state)
            seed.load(LoadType.APPEND, state)
            appended.clear()
            val home =
                TimelineRemoteMediator(
                    loader,
                    database,
                    false,
                    retainHistory = true,
                    refreshSession = HomeTimelineRefreshSessions().forTimeline("home", loader.pagingKey),
                )
            generation = 1
            fail = true
            home.load(LoadType.REFRESH, state)
            val firstGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            generation = 2
            fail = false
            home.load(LoadType.REFRESH, state)
            val retryGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single { it.statusKey != firstGap.statusKey }
            home.loadGap(retryGap)
            assertEquals(listOf(2), appended, "Only the source that failed its first refresh may create a new gap")
            generation = 3
            home.load(LoadType.REFRESH, state)
            assertEquals(listOf(firstGap), contents(home).filterIsInstance<UiTimelineV2.Gap>())
        }

    @Test
    fun cancelledOrStaleFeaturedRefreshDoesNotConsumeItsOpportunity() =
        runTest {
            for (cancel in listOf(false, true)) {
                var delayed = false
                var response = PagingResult<UiTimelineV2>(listOf(datedPost(10)), nextKey = "tail")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val featured =
                    source("featured-$cancel", ordered = false, refreshGapOncePerSession = true) {
                        if (delayed) {
                            entered.complete(Unit)
                            withContext(NonCancellable) { release.await() }
                        }
                        response
                    }
                TimelineRemoteMediator(featured, database, false, retainHistory = true).load(LoadType.REFRESH, state)
                val home =
                    TimelineRemoteMediator(
                        featured,
                        database,
                        false,
                        retainHistory = true,
                        refreshSession = HomeTimelineRefreshSessions().forTimeline("home", featured.pagingKey),
                    )
                delayed = true
                response = PagingResult(listOf(datedPost(20)), nextKey = "discarded")
                val job = launch { home.load(LoadType.REFRESH, state) }
                entered.await()
                if (cancel) {
                    job.cancel()
                } else {
                    val dao = database.pagingTimelineDao()
                    val key = assertNotNull(dao.getPagingKeyMetadata(home.pagingKey))
                    dao.updatePage(home.pagingKey, null, true, "changed", key.sourceHeads, key.accountKeys)
                }
                release.complete(Unit)
                job.join()
                assertEquals(listOf("10"), contents(home).map { it.statusKey.id })
                delayed = false
                home.load(LoadType.REFRESH, state)
                assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
                response = PagingResult(listOf(datedPost(30)), nextKey = "unused")
                home.load(LoadType.REFRESH, state)
                assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            }
        }

    @Test
    fun rolledBackFeaturedRefreshDoesNotConsumeItsOpportunity() =
        runTest {
            for (cancel in listOf(false, true)) {
                var reject = true
                var refreshJob: Job? = null
                var response = PagingResult<UiTimelineV2>(listOf(datedPost(10)), nextKey = "tail")
                val featured = source("featured-$cancel", ordered = false, refreshGapOncePerSession = true) { response }
                TimelineRemoteMediator(featured, database, false, retainHistory = true).load(LoadType.REFRESH, state)
                val home =
                    TimelineRemoteMediator(
                        featured,
                        database,
                        false,
                        retainHistory = true,
                        refreshSession = HomeTimelineRefreshSessions().forTimeline("home", featured.pagingKey),
                        preTranslationService =
                            object : PreTranslationService by NoopPreTranslationService {
                                override fun enqueueStatuses(
                                    statuses: List<DbStatus>,
                                    allowLongText: Boolean,
                                ) {
                                    if (reject) {
                                        if (cancel) checkNotNull(refreshJob).cancel() else error("Reject transaction")
                                    }
                                }
                            },
                    )
                response = PagingResult(listOf(datedPost(20)), nextKey = "gap")
                refreshJob = launch(start = CoroutineStart.LAZY) { home.load(LoadType.REFRESH, state) }
                refreshJob.start()
                refreshJob.join()
                assertEquals(listOf("10"), contents(home).map { it.statusKey.id })
                reject = false
                home.load(LoadType.REFRESH, state)
                assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
                response = PagingResult(listOf(datedPost(30)), nextKey = "unused")
                home.load(LoadType.REFRESH, state)
                assertEquals(1, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            }
        }

    @Test
    fun featuredSessionStillAllowsNormalPagingAfterAnEmptyCacheLoadOrCacheClear() =
        runTest {
            var generation = 0
            val requests = mutableListOf<PagingRequest>()
            val featured =
                source("featured", ordered = false, refreshGapOncePerSession = true) { request ->
                    requests += request
                    if (request is PagingRequest.Append) {
                        PagingResult(listOf(datedPost(1)))
                    } else {
                        PagingResult(listOf(datedPost(++generation * 10)), nextKey = "tail-$generation")
                    }
                }
            val session = HomeTimelineRefreshSessions().forTimeline("home", featured.pagingKey)
            val home = TimelineRemoteMediator(featured, database, false, retainHistory = true, refreshSession = session)
            repeat(2) { home.load(LoadType.REFRESH, state) }
            assertEquals(0, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            home.load(LoadType.APPEND, state)
            assertEquals(PagingRequest.Append("tail-1"), requests.last())
            database.connect {
                val dao = database.pagingTimelineDao()
                dao.delete(dao.getByPagingKey(home.pagingKey))
                dao.deletePagingKey(home.pagingKey)
            }
            home.load(LoadType.REFRESH, state)
            home.load(LoadType.APPEND, state)
            assertEquals(PagingRequest.Append("tail-3"), requests.last())
            assertEquals(listOf("30", "1"), contents(home).map { it.statusKey.id })
        }

    @Test
    fun homeRefreshSessionSurvivesSettingChangesButIsScopedToTheTabAndSourceConfiguration() =
        runTest {
            var generation = 0
            val featured =
                source("featured", ordered = false, refreshGapOncePerSession = true) {
                    PagingResult(listOf(datedPost(++generation * 10)), nextKey = "tail-$generation")
                }
            val sessions = HomeTimelineRefreshSessions()

            fun home(
                tab: String,
                remember: Boolean,
            ) = TimelineRemoteMediator(
                featured,
                database,
                false,
                retainHistory = remember,
                refreshSession = sessions.forTimeline(tab, featured.pagingKey),
            )
            home("first", false).load(LoadType.REFRESH, state)
            val first = home("first", true)
            first.load(LoadType.REFRESH, state)
            assertEquals(0, contents(first).filterIsInstance<UiTimelineV2.Gap>().size)
            home("second", true).load(LoadType.REFRESH, state)
            assertEquals(1, contents(first).filterIsInstance<UiTimelineV2.Gap>().size)
            assertTrue(sessions.forTimeline("first", "another-account").refreshedSources.isEmpty())
        }

    @Test
    fun otherRankedSourcesStillCreateGapsOnEveryRefresh() =
        runTest {
            var generation = 0
            val ranked =
                source("ranked", ordered = false) {
                    PagingResult(listOf(datedPost(++generation * 10)), nextKey = "tail-$generation")
                }
            val home =
                TimelineRemoteMediator(
                    ranked,
                    database,
                    false,
                    retainHistory = true,
                    refreshSession = HomeTimelineRefreshSessions().forTimeline("home", ranked.pagingKey),
                )
            repeat(3) { home.load(LoadType.REFRESH, state) }
            assertEquals(2, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
        }

    @Test
    fun rankedRefreshKeepsTheOldAnchorAndItsTrailingWindowAfterALargePrepend() =
        runTest {
            for (anchor in listOf(0, 35)) checkRefreshWindow(anchor = anchor)
        }

    @Test
    fun mixedRefreshKeepsTheOldAnchorAndItsTrailingWindowAfterALargePrepend() =
        runTest {
            for (policy in TimelineMergePolicy.entries) checkRefreshWindow(policy)
        }

    @Test
    fun coalescedChronologicalRefreshesKeepTheOldAnchorAfterMultipleNewPages() =
        runTest {
            checkRefreshWindow(TimelineMergePolicy.Time, refreshes = 3)
        }

    @Test
    fun refreshingAfterTheAnchorIsDeletedFallsBackToTheOrdinaryWindow() =
        runTest {
            checkRefreshWindow(deleteAnchor = true)
        }

    @Test
    fun refreshingWithoutRememberingPositionKeepsAnOverlappingAnchor() =
        runTest {
            checkRefreshWindow(remember = false)
        }

    private suspend fun checkRefreshWindow(
        policy: TimelineMergePolicy? = null,
        anchor: Int = 12,
        refreshes: Int = 1,
        deleteAnchor: Boolean = false,
        remember: Boolean = true,
    ) {
        var generation = 0
        val old = (80 downTo 1).map(::datedPost)
        val ranked =
            source("ranked-$policy-$anchor", ordered = false) {
                PagingResult(
                    (generation * 100 + 40 downTo generation * 100 + 1).map(::datedPost) + if (remember) emptyList() else old,
                    nextKey = "ranked-$generation",
                )
            }
        val loader =
            if (policy == null) {
                ranked
            } else {
                MixedRemoteMediator(
                    database,
                    listOf(
                        ranked,
                        source("ordered") {
                            PagingResult(listOf(datedPost(generation * 100 + 41)), nextKey = "ordered-$generation")
                        },
                    ),
                    policy,
                )
            }
        database.connect {
            saveToDatabase(
                database,
                TimelinePagingMapper.toDb(
                    old,
                    loader.pagingKey,
                    old.mapIndexed { index, item ->
                        (loader as? SortIdProvider)?.sortId(item)?.times(2) ?: index * 1_000_000L
                    },
                ),
            )
            database.pagingTimelineDao().insertPagingKey(
                DbPagingKey(
                    loader.pagingKey,
                    nextKey =
                        if (policy ==
                            null
                        ) {
                            "old-tail"
                        } else {
                            """[{"started":true,"next":"ranked-old"},{"started":true,"next":"ordered-old"}]"""
                        },
                    sourceHeads =
                        Json.encodeToString(
                            mapOf(ranked.pagingKey to old.map { it.stableItemKey }) +
                                if (policy == null) emptyMap() else mapOf("ordered" to listOf(datedPost(80).stableItemKey)),
                        ),
                ),
            )
        }
        val home = TimelineRemoteMediator(loader, database, false, retainHistory = remember)
        val cache = TimelineDbPageCache()

        fun newSource() = OffsetFromStartPagingSource(TimelineDbPageLoader(database, loader.pagingKey, cache))
        val before = newSource()
        val after = newSource()
        val cold = OffsetFromStartPagingSource(TimelineDbPageLoader(database, loader.pagingKey, TimelineDbPageCache()))
        try {
            val initial =
                assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, TimelinePageItem>>(
                    before.doLoad(PagingSource.LoadParams.Refresh(null, maxOf(20, anchor + 1), false)),
                )
            val key =
                assertIs<OffsetFromStartPagingKey.Refresh>(
                    before.getRefreshKey(PagingState(listOf(initial), anchor, offsetPagingConfig, 0)),
                )
            val anchorId = initial.data[anchor].identity.timelineId
            val display = TranslationDisplayOptions(false, false, "")
            val previousUi = initial.data.associate { it.identity to it.toUi(display) }
            repeat(refreshes) {
                generation++
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
            }
            if (deleteAnchor) {
                val row = assertNotNull(database.pagingTimelineDao().getRow(loader.pagingKey, anchorId))
                database.pagingTimelineDao().delete(listOf(row))
            }
            val all = database.pagingTimelineDao().getTimelinePageIdentities(loader.pagingKey, 0, 300)
            val expectedSize =
                if (deleteAnchor) key.limit else maxOf(key.limit, all.indexOfFirst { it.timelineId == anchorId } + key.limit - anchor)
            val refreshed =
                assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, TimelinePageItem>>(
                    after.doLoad(PagingSource.LoadParams.Refresh(key, 20, false)),
                )
            assertEquals(
                all.take(expectedSize),
                refreshed.data.map { it.identity },
                "Refresh must retain the anchor and its trailing window",
            )
            val previousItems = initial.data.associateBy { it.identity }
            val unchanged = refreshed.data.filter { it.identity in previousItems }
            if (remember && !deleteAnchor) assertTrue(unchanged.isNotEmpty())
            unchanged.forEach { item ->
                assertSame(previousItems[item.identity], item, "Unchanged cached posts must survive a large prepend")
                assertSame(previousUi[item.identity], item.toUi(display), "The cached UI model must also be reused")
            }
            assertEquals(OffsetFromStartPagingKey.Append(expectedSize), refreshed.nextKey)
            val appended =
                assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, TimelinePageItem>>(
                    after.doLoad(PagingSource.LoadParams.Append(assertNotNull(refreshed.nextKey), 20, false)),
                )
            assertEquals(all.drop(expectedSize).take(20), appended.data.map { it.identity }, "Append must start after the extended prefix")
            val restarted =
                assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, TimelinePageItem>>(
                    cold.doLoad(PagingSource.LoadParams.Refresh(null, 20, false)),
                )
            assertEquals(all.take(20), restarted.data.map { it.identity }, "Cold starts must still read only the normal first page")
        } finally {
            before.invalidate()
            after.invalidate()
            cold.invalidate()
        }
    }

    @Test
    fun mixedRankedCachedPageDoesNotCreateAGapForAnotherSourcesNewHead() =
        runTest {
            for (policy in TimelineMergePolicy.entries) {
                for (nested in listOf(false, true)) {
                    var fresh = false
                    val ranked =
                        source("ranked-$nested", ordered = false) { request ->
                            if (request is PagingRequest.Append) {
                                PagingResult(listOf(datedPost(5)))
                            } else {
                                PagingResult(listOf(datedPost(20), datedPost(10)), nextKey = "ranked-tail")
                            }
                        }
                    val chronological =
                        source("chronological") {
                            PagingResult(if (fresh) listOf(datedPost(40), datedPost(30)) else listOf(datedPost(30)))
                        }
                    val sources =
                        if (nested) {
                            listOf(MixedRemoteMediator(database, listOf(ranked, chronological), policy))
                        } else {
                            listOf(ranked, chronological)
                        }
                    val home = TimelineRemoteMediator(MixedRemoteMediator(database, sources, policy), database, false, retainHistory = true)
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                    val tail = assertNotNull(database.pagingTimelineDao().getNextKey(home.pagingKey))
                    fresh = true
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                    assertEquals(0, contents(home).filterIsInstance<UiTimelineV2.Gap>().size, "$policy nested=$nested")
                    assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
                    assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.APPEND, state))
                    assertTrue(contents(home).any { it.statusKey.id == "5" })
                }
            }
        }

    @Test
    fun mixedCachedPendingRowsDoNotCreateGapsWithOrWithoutAFailedSource() =
        runTest {
            for (fail in listOf(false, true)) {
                var fresh = false
                val primary =
                    source("primary-$fail") { PagingResult(if (fresh) listOf(datedPost(40), datedPost(30)) else listOf(datedPost(30))) }
                val secondary =
                    source("secondary") {
                        PagingResult(
                            (20 downTo 2).map { datedPost(it).copy(content = UiTranslatableText("updated=$fresh".toUiPlainText())) },
                        )
                    }
                val failing =
                    source("failing") {
                        check(!fresh || !fail) { "offline" }
                        PagingResult()
                    }
                val home = mixed(primary, secondary, failing)
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                fresh = true
                assertIs<RemoteMediator.MediatorResult.Success>(home.load(LoadType.REFRESH, state))
                val rows = contents(home)
                assertEquals(listOf("40", "30") + (20 downTo 2).map(Int::toString), rows.map { it.statusKey.id })
                assertEquals(UiTranslatableText("updated=true".toUiPlainText()), (rows.last() as UiTimelineV2.Post).content)
            }
        }

    @Test
    fun mixedUncachedPendingRowsArePublishedWithoutCreatingAGap() =
        runTest {
            var fresh = false
            val primary = source("primary") { PagingResult(if (fresh) (120 downTo 101).map(::datedPost) else listOf(datedPost(100))) }
            val secondary = source("secondary") { PagingResult(((if (fresh) 21 else 20) downTo 2).map(::datedPost)) }
            val home = mixed(primary, secondary)
            home.load(LoadType.REFRESH, state)
            fresh = true
            home.load(LoadType.REFRESH, state)
            assertEquals(0, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            assertEquals((120 downTo 100).map(Int::toString) + (21 downTo 2).map(Int::toString), contents(home).map { it.statusKey.id })
        }

    @Test
    fun mixedAppendPublishesMissingSourceBoundaryWithoutReplacingKnownHeads() =
        runTest {
            var fresh = false
            val healthy =
                source("healthy") { request ->
                    when {
                        request is PagingRequest.Append -> PagingResult(listOf(datedPost(1)))
                        fresh -> PagingResult(listOf(datedPost(70)) + (20 downTo 2).map(::datedPost), nextKey = "tail")
                        else -> PagingResult((20 downTo 1).map(::datedPost), nextKey = "tail")
                    }
                }
            val failing =
                source("failing") {
                    check(!fresh) { "offline" }
                    PagingResult((60 downTo 41).map(::datedPost))
                }
            val home = mixed(healthy, failing)
            home.load(LoadType.REFRESH, state)
            val before =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(setOf("failing"), before.keys)
            home.load(LoadType.APPEND, state)
            val after =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(before.getValue("failing"), after.getValue("failing"))
            assertEquals((20 downTo 1).map { datedPost(it).stableItemKey }, after["healthy"])
            fresh = true
            home.load(LoadType.REFRESH, state)
            assertEquals(0, contents(home).filterIsInstance<UiTimelineV2.Gap>().size)
            assertEquals("70", contents(home).first().statusKey.id)
        }

    @Test
    fun timeMixedRecommendationsKeepNewItemsAfterDuplicatesDuringOrdinaryPaging() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(datedPost(30), datedPost(20)), nextKey = "tail")
            val home = mixed(source("ranked", ordered = false) { response })
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(datedPost(30), datedPost(40)), nextKey = "gap-one")
            home.load(LoadType.REFRESH, state)
            assertTrue(contents(home).none { it is UiTimelineV2.Gap })
            response = PagingResult(listOf(datedPost(30), datedPost(35)), nextKey = "gap-two")
            home.load(LoadType.APPEND, state)
            response = PagingResult(listOf(datedPost(30), datedPost(20)))
            home.load(LoadType.APPEND, state)
            assertEquals(listOf("40", "35", "30", "20"), contents(home).map { it.statusKey.id })
            assertEquals(null, database.pagingTimelineDao().getNextKey(home.pagingKey))
        }

    @Test
    fun mixedRefreshDefersOlderSourceBoundaryWithoutChangingTheTail() =
        runTest {
            var fresh = false
            val home =
                mixed(
                    source("primary") { PagingResult(listOf(datedPost(if (fresh) 40 else 30))) },
                    source("secondary") { PagingResult(listOf(datedPost(20))) },
                )
            val singleRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
            home.load(LoadType.REFRESH, singleRow)
            val tail = database.pagingTimelineDao().getNextKey(home.pagingKey)
            val before =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(setOf("primary"), before.keys)
            fresh = true
            home.load(LoadType.REFRESH, singleRow)
            assertTrue(contents(home).none { it is UiTimelineV2.Gap })
            val after =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(listOf(datedPost(40).stableItemKey), after.getValue("primary"))
            assertEquals(null, after["secondary"])
            assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
            home.load(LoadType.APPEND, singleRow)
            assertEquals(listOf("40", "30", "20"), contents(home).map { it.statusKey.id })
            val appended =
                Json.decodeFromString<Map<String, List<String>>>(
                    assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(listOf(datedPost(20).stableItemKey), appended["secondary"])
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
                        PagingResult(
                            if (tail) listOf(dated(5)) else listOf(dated(50), dated(25)),
                            nextKey = if (tail) null else "next-recommendations",
                        )
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
            assertEquals(listOf("40", "30"), ids)
            assertTrue(contents(home).none { it is UiTimelineV2.Gap })
            home.load(LoadType.APPEND, state)
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
    fun emptyPagesAdvanceButFailedOrStuckPagesKeepTheirCursor() =
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
            response = PagingResult(emptyList(), nextKey = "gap-next")
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

    private suspend fun contents(home: TimelineRemoteMediator) =
        database.pagingTimelineDao().getTimelinePage(home.pagingKey, 0, 300).map { it.statusData.content }

    private fun datedPost(id: Int) = post(id).copy(createdAt = Instant.fromEpochMilliseconds(id.toLong()).toUi())

    private fun source(
        name: String,
        ordered: Boolean = true,
        refreshGapOncePerSession: Boolean = false,
        response: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) = object : CacheableRemoteLoader<UiTimelineV2> {
        override val pagingKey = name
        override val canJoinTimeline = ordered
        override val refreshGapOncePerSession = refreshGapOncePerSession

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ) = response(request)
    }

    private fun mixed(vararg sources: CacheableRemoteLoader<UiTimelineV2>) =
        TimelineRemoteMediator(
            MixedRemoteMediator(database, sources.toList(), TimelineMergePolicy.Time),
            database,
            false,
            retainHistory = true,
        )

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
