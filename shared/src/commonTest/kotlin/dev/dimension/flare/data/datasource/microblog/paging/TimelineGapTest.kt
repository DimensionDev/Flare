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
                val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
                response = PagingResult(listOf(datedPost(8)))
                home.loadGap(gap)
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
            val home =
                mixed(
                    source("primary") {
                        if (delayRefresh) {
                            entered.complete(Unit)
                            release.await()
                        }
                        PagingResult(listOf(datedPost(primaryId)))
                    },
                    source("secondary") { PagingResult(listOf(datedPost(20))) },
                )
            val oneRow = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(1), 0)
            home.load(LoadType.REFRESH, oneRow)
            primaryId = 40
            home.load(LoadType.REFRESH, oneRow)
            val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            val before = assertNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey))
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
                    TimelineRemoteMediator(loader, database, false, retainHistory = true).load(LoadType.REFRESH, state)
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
                    assertTrue("featured" in appended, "An existing gap keeps its original continuation")
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
            val loader = MixedRemoteMediator(database, sources, TimelineMergePolicy.Time)
            TimelineRemoteMediator(loader, database, false, retainHistory = true).load(LoadType.REFRESH, state)
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
            database.pagingTimelineDao().insertPagingKey(DbPagingKey(loader.pagingKey, nextKey = "old-tail"))
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
    fun mixedUncachedPendingRowsRemainLoadableThroughTheGap() =
        runTest {
            var fresh = false
            val primary = source("primary") { PagingResult(if (fresh) (120 downTo 101).map(::datedPost) else listOf(datedPost(100))) }
            val secondary = source("secondary") { PagingResult(((if (fresh) 21 else 20) downTo 2).map(::datedPost)) }
            val home = mixed(primary, secondary)
            home.load(LoadType.REFRESH, state)
            fresh = true
            home.load(LoadType.REFRESH, state)
            val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals(1, home.loadGap(gap).insertedKeys.size)
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
    fun mixedRankedGapKeepsNewItemsAfterDuplicatesAndClosesOnlyOnAFullCachedPage() =
        runTest {
            var response = PagingResult<UiTimelineV2>(listOf(datedPost(30), datedPost(20)), nextKey = "tail")
            val home = mixed(source("ranked", ordered = false) { response })
            home.load(LoadType.REFRESH, state)
            val tail = database.pagingTimelineDao().getNextKey(home.pagingKey)
            response = PagingResult(listOf(datedPost(30), datedPost(40)), nextKey = "gap-one")
            home.load(LoadType.REFRESH, state)
            val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            response = PagingResult(listOf(datedPost(30), datedPost(35)), nextKey = "gap-two")
            assertEquals(1, home.loadGap(gap).insertedKeys.size)
            val nextGap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            response = PagingResult(listOf(datedPost(30), datedPost(20)), nextKey = "unused-tail")
            assertEquals(emptyList(), home.loadGap(nextGap).insertedKeys)
            assertEquals(listOf("40", "35", "30", "20"), contents(home).map { it.statusKey.id })
            assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
        }

    @Test
    fun mixedGapPublishesMissingSourceBoundaryWithoutChangingTheTailOrKnownHeads() =
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
            fresh = true
            home.load(LoadType.REFRESH, singleRow)
            val before =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(setOf("primary"), before.keys)
            val gap = contents(home).filterIsInstance<UiTimelineV2.Gap>().single()
            assertEquals(1, home.loadGap(gap).insertedKeys.size)
            val after =
                Json.decodeFromString<Map<String, List<String>>>(
                    checkNotNull(database.pagingTimelineDao().getPagingKeyMetadata(home.pagingKey)).sourceHeads,
                )
            assertEquals(before.getValue("primary"), after.getValue("primary"))
            assertEquals(listOf(datedPost(20).stableItemKey), after["secondary"])
            assertEquals(tail, database.pagingTimelineDao().getNextKey(home.pagingKey))
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
