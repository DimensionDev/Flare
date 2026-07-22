package dev.dimension.flare.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.data.translation.PreTranslationStoreSupport
import dev.dimension.flare.data.translation.aiPreTranslateConfig
import dev.dimension.flare.data.translation.canonicalTranslationLanguage
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationTokenKind
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MixedRemoteMediatorTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun prependReturnsEndWithoutLoadingSubMediators() =
        runTest {
            val first = FakeLoader("a") { PagingResult(nextKey = "next_a") }
            val second = FakeLoader("b") { PagingResult(nextKey = "next_b") }
            val mediator = MixedRemoteMediator(db, listOf(first, second))

            val result = mediator.load(pageSize = 20, request = PagingRequest.Prepend("ignored"))

            assertNull(result.nextKey)
            assertEquals(0, first.requests.size)
            assertEquals(0, second.requests.size)
        }

    @Test
    fun appendUsesRemainingMediatorsAndRefreshResetsMediatorSet() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/a_refresh", 1000L)),
                                nextKey = "a_next",
                            )
                        }

                        is PagingRequest.Append -> {
                            assertEquals("a_next", request.nextKey)
                            PagingResult(
                                data = listOf(feed("https://example.com/a_append", 900L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> {
                            error("Prepend should not be requested here")
                        }
                    }
                }

            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/b_refresh", 2000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("Second mediator should be filtered out before append")
                        }

                        is PagingRequest.Prepend -> {
                            error("Prepend should not be requested here")
                        }
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))

            mediator.load(pageSize = 20, request = PagingRequest.Refresh)
            val appendResult = mediator.load(pageSize = 20, request = PagingRequest.Append("mixed_next_key"))

            assertNull(appendResult.nextKey)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Append("a_next")), first.requests)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), second.requests)

            mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(3, first.requests.size)
            assertEquals(PagingRequest.Refresh, first.requests.last())
            assertEquals(2, second.requests.size)
            assertEquals(PagingRequest.Refresh, second.requests.last())
        }

    @Test
    fun failingSubMediatorIsIgnoredAndLoadStillSucceeds() =
        runTest {
            val failing =
                FakeLoader("failing") { _ ->
                    error("network failure")
                }
            val healthy =
                FakeLoader("healthy") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/healthy", 3000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(failing, healthy))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertNull(result.nextKey)
            assertEquals(1, failing.requests.size)
            assertEquals(1, healthy.requests.size)
            assertEquals(1, result.data.size)
            assertEquals("https://example.com/healthy", (result.data.first() as UiTimelineV2.Feed).url)
            assertNotNull(db.pagingTimelineDao().getPagingKey("mixed_failing"))
            assertNotNull(db.pagingTimelineDao().getPagingKey("mixed_healthy"))
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timelineMediatorSkipsInitialRefreshWhenPrependCacheExists() =
        runTest {
            val cached = feed("https://example.com/cached", 1000L)
            val loader =
                FakeLoader(
                    pagingKey = "refresh_existing_cache",
                    supportPrepend = true,
                ) { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/fresh", 2000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append,
                        is PagingRequest.Prepend,
                        -> {
                            error("No boundary load expected")
                        }
                    }
                }
            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)

            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(cached, pagingKey = loader.pagingKey)))

            assertEquals(
                androidx.paging.RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize(),
            )
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timelineMediatorSuppressesLaunchRefreshWhenDisabled() =
        runTest {
            val loader =
                FakeLoader(
                    pagingKey = "refresh_disabled",
                    supportPrepend = true,
                ) {
                    error("Launch refresh should be suppressed")
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    refreshOnInitialize = { false },
                )
            saveToDatabase(
                db,
                listOf(TimelinePagingMapper.toDb(feed("https://example.com/cached", 1000L), pagingKey = loader.pagingKey)),
            )
            db.pagingTimelineDao().insertPagingKey(
                DbPagingKey(
                    pagingKey = loader.pagingKey,
                    prevKey = "newer",
                ),
            )

            assertEquals(
                androidx.paging.RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator.initialize(),
            )
            val result =
                mediator.load(
                    loadType = LoadType.PREPEND,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )

            assertTrue(assertIs<androidx.paging.RemoteMediator.MediatorResult.Success>(result).endOfPaginationReached)
            assertTrue(loader.requests.isEmpty())
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timelineRefreshClearsExistingCacheWhenRemoteReturnsEmpty() =
        runTest {
            val cached = feed("https://example.com/cached", 1000L)
            val loader =
                FakeLoader("empty_refresh") { request ->
                    when (request) {
                        PagingRequest.Refresh -> PagingResult(endOfPaginationReached = true)

                        is PagingRequest.Append,
                        is PagingRequest.Prepend,
                        -> error("No boundary load expected")
                    }
                }
            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(cached, pagingKey = loader.pagingKey)))

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )

            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue(db.pagingTimelineDao().getByPagingKey(loader.pagingKey).isEmpty())
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshWithMultipleItemsPerSubPersistsSortedOrderInDatabase() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/a_3000", 3000L),
                                        feed("https://example.com/a_1000", 1000L),
                                    ),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/b_4000", 4000L),
                                        feed("https://example.com/b_2000", 2000L),
                                    ),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mixed = MixedRemoteMediator(db, listOf(first, second))
            val timelineRemoteMediator = TimelineRemoteMediator(loader = mixed, database = db, allowLongText = false)

            val mediatorResult =
                timelineRemoteMediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(timelineRemoteMediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)
            val urls =
                page.data.mapNotNull {
                    (it.status.status.data.content as? UiTimelineV2.Feed)?.url
                }
            assertEquals(
                listOf(
                    "https://example.com/b_4000",
                    "https://example.com/a_3000",
                    "https://example.com/b_2000",
                    "https://example.com/a_1000",
                ),
                urls,
            )
        }

    @Test
    fun staggeredMergePolicyInterleavesSubTimelinePages() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/a_1", 1000L),
                                        feed("https://example.com/a_2", 4000L),
                                    ),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/b_1", 3000L),
                                        feed("https://example.com/b_2", 2000L),
                                    ),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second), TimelineMergePolicy.Staggered)
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(
                listOf(
                    "https://example.com/a_1",
                    "https://example.com/b_1",
                    "https://example.com/a_2",
                    "https://example.com/b_2",
                ),
                result.data.mapNotNull { (it as? UiTimelineV2.Feed)?.url },
            )
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timeMergePolicyKeepsOlderBufferedPageBehindNewerNextPage() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/a_4000", 4000L)),
                                nextKey = "a_next",
                            )
                        }

                        is PagingRequest.Append -> {
                            assertEquals("a_next", request.nextKey)
                            PagingResult(
                                data = listOf(feed("https://example.com/a_3000", 3000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/b_2000", 2000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("B should stay buffered without loading another page")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val state =
                PagingState<OffsetFromStartPagingKey, DbPagingTimelineWithStatus>(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = PagingConfig(pageSize = 1),
                    leadingPlaceholderCount = 0,
                )

            fun mediator() =
                TimelineRemoteMediator(
                    loader = MixedRemoteMediator(db, listOf(first, second), TimelineMergePolicy.Time),
                    database = db,
                    allowLongText = false,
                )

            val refreshMediator = mediator()
            val refreshResult = refreshMediator.load(loadType = LoadType.REFRESH, state = state)
            assertTrue(refreshResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val firstAppend =
                MixedRemoteMediator(db, listOf(first, second), TimelineMergePolicy.Time)
                    .load(pageSize = 1, request = PagingRequest.Append("mixed_next_key"))
            val retriedFirstAppend =
                MixedRemoteMediator(db, listOf(first, second), TimelineMergePolicy.Time)
                    .load(pageSize = 1, request = PagingRequest.Append("mixed_next_key"))
            val committedFirstAppend = mediator().load(loadType = LoadType.APPEND, state = state)
            val committedSecondAppend = mediator().load(loadType = LoadType.APPEND, state = state)

            assertEquals(
                listOf("https://example.com/a_3000"),
                firstAppend.data.mapNotNull { (it as? UiTimelineV2.Feed)?.url },
            )
            assertEquals(firstAppend.data, retriedFirstAppend.data)
            assertEquals(firstAppend.nextKey, retriedFirstAppend.nextKey)
            assertTrue(committedFirstAppend is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertTrue(committedSecondAppend is androidx.paging.RemoteMediator.MediatorResult.Success)
            assertEquals(
                listOf(
                    "https://example.com/a_4000",
                    "https://example.com/a_3000",
                    "https://example.com/b_2000",
                ),
                db
                    .pagingTimelineDao()
                    .getTimelinePage(refreshMediator.pagingKey, offset = 0, limit = 20)
                    .mapNotNull { (it.status.status.data.content as? UiTimelineV2.Feed)?.url },
            )
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Append("a_next")), first.requests)
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), second.requests)
            assertNull(db.pagingTimelineDao().getPagingKey(refreshMediator.pagingKey)?.nextKey)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timeMergePolicyPersistsGlobalTimeOrderAcrossAppends() =
        runTest {
            val loader =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(feed("https://example.com/refresh_old", 1000L)),
                                nextKey = "a_next",
                            )
                        }

                        is PagingRequest.Append -> {
                            assertEquals("a_next", request.nextKey)
                            PagingResult(
                                data = listOf(feed("https://example.com/append_new", 3000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mixed = MixedRemoteMediator(db, listOf(loader), TimelineMergePolicy.Time)
            val timelineRemoteMediator = TimelineRemoteMediator(loader = mixed, database = db, allowLongText = false)
            val state =
                PagingState<OffsetFromStartPagingKey, DbPagingTimelineWithStatus>(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = PagingConfig(pageSize = 20),
                    leadingPlaceholderCount = 0,
                )

            val refreshResult = timelineRemoteMediator.load(loadType = LoadType.REFRESH, state = state)
            assertTrue(refreshResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            val appendResult = timelineRemoteMediator.load(loadType = LoadType.APPEND, state = state)
            assertTrue(appendResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(timelineRemoteMediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)
            assertEquals(
                listOf(
                    "https://example.com/append_new",
                    "https://example.com/refresh_old",
                ),
                page.data.mapNotNull {
                    (it.status.status.data.content as? UiTimelineV2.Feed)?.url
                },
            )
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timeMergePolicySkipsOverlappingItemsAfterMediatorRecreation() =
        runTest {
            val overlapping = feed("https://example.com/overlapping", 4000L)
            val loader =
                FakeLoader("overlap") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(overlapping),
                                nextKey = "overlap_next",
                            )
                        }

                        is PagingRequest.Append -> {
                            assertEquals("overlap_next", request.nextKey)
                            PagingResult(
                                data = listOf(overlapping, feed("https://example.com/older", 3000L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val state =
                PagingState<OffsetFromStartPagingKey, DbPagingTimelineWithStatus>(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = PagingConfig(pageSize = 1),
                    leadingPlaceholderCount = 0,
                )
            val refreshMediator =
                TimelineRemoteMediator(
                    loader = MixedRemoteMediator(db, listOf(loader), TimelineMergePolicy.Time),
                    database = db,
                    allowLongText = false,
                )

            val refreshResult = refreshMediator.load(loadType = LoadType.REFRESH, state = state)
            assertTrue(refreshResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val appendMediator =
                TimelineRemoteMediator(
                    loader = MixedRemoteMediator(db, listOf(loader), TimelineMergePolicy.Time),
                    database = db,
                    allowLongText = false,
                )
            val appendResult = appendMediator.load(loadType = LoadType.APPEND, state = state)
            assertTrue(appendResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val page =
                db.pagingTimelineDao().getTimelinePage(
                    pagingKey = appendMediator.pagingKey,
                    offset = 0,
                    limit = 20,
                )
            assertEquals(
                listOf("https://example.com/overlapping", "https://example.com/older"),
                page.mapNotNull { (it.status.status.data.content as? UiTimelineV2.Feed)?.url },
            )
            assertEquals(
                listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Append("overlap_next")),
                loader.requests,
            )
            assertNull(db.pagingTimelineDao().getPagingKey(appendMediator.pagingKey)?.nextKey)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun timelinePrependInsertsNewItemsBeforeExistingItemsAndKeepsExistingOffsetPage() =
        runTest {
            val refreshItems =
                listOf(
                    feed("https://example.com/old_1", 1_000L),
                    feed("https://example.com/old_2", 900L),
                )
            val prependItems =
                listOf(
                    feed("https://example.com/new_1", 2_000L),
                    feed("https://example.com/new_2", 1_900L),
                )
            val loader =
                FakeLoader(
                    pagingKey = "prepend_offset_page",
                    supportPrepend = true,
                ) { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = refreshItems,
                                nextKey = null,
                                previousKey = "prev_1",
                            )
                        }

                        is PagingRequest.Prepend -> {
                            assertEquals("prev_1", request.previousKey)
                            PagingResult(
                                data = prependItems,
                                nextKey = null,
                                previousKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }
                    }
                }
            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            val state =
                PagingState<OffsetFromStartPagingKey, DbPagingTimelineWithStatus>(
                    pages = emptyList(),
                    anchorPosition = null,
                    config = PagingConfig(pageSize = 20),
                    leadingPlaceholderCount = 0,
                )

            val refreshResult = mediator.load(loadType = LoadType.REFRESH, state = state)
            assertTrue(refreshResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            val prependResult = mediator.load(loadType = LoadType.PREPEND, state = state)
            assertTrue(prependResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            assertEquals(
                listOf(PagingRequest.Refresh, PagingRequest.Prepend("prev_1")),
                loader.requests,
            )
            val page =
                db.pagingTimelineDao().getTimelinePage(
                    pagingKey = loader.pagingKey,
                    offset = 0,
                    limit = 20,
                )
            val urls =
                page.mapNotNull {
                    (it.status.status.data.content as? UiTimelineV2.Feed)?.url
                }
            assertEquals(
                listOf(
                    "https://example.com/new_1",
                    "https://example.com/new_2",
                    "https://example.com/old_1",
                    "https://example.com/old_2",
                ),
                urls,
            )
            assertEquals(urls.size, urls.toSet().size)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshCollapsesLinearReplyChainIntoLatestPost() =
        runTest {
            val accountKey = MicroBlogKey("timeline", "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey("user", "mastodon.example"), "User")
            val postA =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "a", host = "mastodon.example"),
                    text = "A",
                )
            val postB =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "b", host = "mastodon.example"),
                    text = "B",
                    parents = listOf(postA),
                )
            val postC =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "c", host = "mastodon.example"),
                    text = "C",
                    parents = listOf(postB),
                )
            val loader =
                FakeLoader("reply_chain") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(postC, postB, postA),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(mediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)
            assertEquals(1, page.data.size)

            val post =
                assertIs<UiTimelineV2.TimelinePostItem>(
                    TimelinePagingMapper.toUi(
                        item = page.data.single(),
                        pagingKey = mediator.pagingKey,
                        translationDisplayOptions =
                            TranslationDisplayOptions(
                                translationEnabled = false,
                                autoDisplayEnabled = false,
                                providerCacheKey = "",
                            ),
                    ),
                )
            assertEquals(postC.statusKey, post.statusKey)
            assertEquals("${mediator.pagingKey}_${page.data.single().status.status.data.id}", post.itemKey)
            assertEquals(listOf(postA.statusKey, postB.statusKey), post.presentation.inlineParents.map { it.statusKey })
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshCollapsePreservesExistingParentOrderWhenDirectParentIsAlsoVisible() =
        runTest {
            val accountKey = MicroBlogKey(id = "timeline", host = "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey(id = "user", host = "mastodon.example"), "User")
            val parents =
                (0 until 5).map { index ->
                    createPost(
                        accountType = accountType,
                        user = user,
                        statusKey = MicroBlogKey("parent-$index", "mastodon.example"),
                        text = "Parent $index",
                    )
                }
            val leaf =
                timelinePostItem(
                    post =
                        createPost(
                            accountType = accountType,
                            user = user,
                            statusKey = MicroBlogKey("leaf", "mastodon.example"),
                            text = "Leaf",
                        ),
                    inlineParents = parents,
                )
            val loader =
                FakeLoader("reply_chain_parent_order") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(parents.last(), leaf),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            val result = mediator.timeline(pageSize = 20, request = PagingRequest.Refresh)
            val post = assertIs<UiTimelineV2.TimelinePostItem>(result.data.single())

            assertEquals(leaf.statusKey, post.statusKey)
            assertEquals(
                listOf("parent-0", "parent-1", "parent-2", "parent-3", "parent-4"),
                post.presentation.inlineParents.map { it.statusKey.id },
            )
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshCanPreserveLinearReplyChainAsVisiblePosts() =
        runTest {
            val accountKey = MicroBlogKey(id = "timeline", host = "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey(id = "user", host = "mastodon.example"), "User")
            val postA =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "a", host = "mastodon.example"),
                    text = "A",
                )
            val postB =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "b", host = "mastodon.example"),
                    text = "B",
                    parents = listOf(postA),
                )
            val postC =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "c", host = "mastodon.example"),
                    text = "C",
                    parents = listOf(postB),
                )
            val loader =
                FakeLoader(
                    pagingKey = "status_detail_reply_chain",
                    collapseReplyChains = false,
                ) { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(postA, postB, postC),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)
            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val pagingSource = db.pagingTimelineDao().getPagingSource(mediator.pagingKey)
            val page =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(page is PagingSource.LoadResult.Page)

            val posts =
                page.data.map {
                    assertIs<UiTimelineV2.TimelinePostItem>(
                        TimelinePagingMapper.toUi(
                            item = it,
                            pagingKey = mediator.pagingKey,
                            translationDisplayOptions =
                                TranslationDisplayOptions(
                                    translationEnabled = false,
                                    autoDisplayEnabled = false,
                                    providerCacheKey = "",
                                ),
                        ),
                    )
                }
            assertEquals(listOf(postA.statusKey, postB.statusKey, postC.statusKey), posts.map { it.statusKey })
        }

    @Test
    fun timelineDoesNotOverflowWhenReplyChainContainsCycle() =
        runTest {
            val accountKey = MicroBlogKey(id = "timeline", host = "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey(id = "user", host = "mastodon.example"), "User")
            val postAWithoutParent =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "a", host = "mastodon.example"),
                    text = "A",
                )
            val postB =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey(id = "b", host = "mastodon.example"),
                    text = "B",
                    parents = listOf(postAWithoutParent),
                )
            val postA =
                postAWithoutParent.copy(
                    references =
                        persistentListOf(
                            UiTimelineV2.Post.Reference(
                                statusKey = postB.statusKey,
                                type = ReferenceType.Reply,
                            ),
                        ),
                )
            val loader =
                FakeLoader("cyclic_reply_chain") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(postA, postB),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)

            val result = mediator.timeline(pageSize = 20, request = PagingRequest.Refresh)

            val collapsedPost = assertIs<UiTimelineV2.TimelinePostItem>(result.data.single())
            assertEquals(postA.statusKey, collapsedPost.statusKey)
            assertEquals(listOf(postB.statusKey), collapsedPost.presentation.inlineParents.map { it.statusKey })
        }

    @Test
    fun timelineDoesNotOverflowWhenReplyChainIsVeryLong() =
        runTest {
            val accountKey = MicroBlogKey(id = "timeline", host = "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey(id = "user", host = "mastodon.example"), "User")
            var parent: UiTimelineV2.Post? = null
            val posts =
                (0 until 6_000).map { index ->
                    createPost(
                        accountType = accountType,
                        user = user,
                        statusKey = MicroBlogKey(id = "long-$index", host = "mastodon.example"),
                        text = "Long $index",
                        parents = listOfNotNull(parent),
                    ).also {
                        parent = it
                    }
                }
            val loader =
                FakeLoader("long_reply_chain") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = posts.asReversed(),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = TimelineRemoteMediator(loader = loader, database = db, allowLongText = false)

            val result = mediator.timeline(pageSize = 20, request = PagingRequest.Refresh)

            val collapsedPost = assertIs<UiTimelineV2.TimelinePostItem>(result.data.single())
            assertEquals(posts.last().statusKey, collapsedPost.statusKey)
            assertEquals(posts.dropLast(1).map { it.statusKey }, collapsedPost.presentation.inlineParents.map { it.statusKey })
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshSchedulesPreTranslationForRootAndReplyReference() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-pretranslation", host = "test.social")
            val accountType = AccountType.Specific(accountKey)
            val rootUser = profile(MicroBlogKey("root-pretranslation", "test.social"), "Root")
            val parentUser = profile(MicroBlogKey("parent-pretranslation", "test.social"), "Parent")
            val parent =
                createPost(
                    user = parentUser,
                    accountType = accountType,
                    statusKey = MicroBlogKey(id = "parent-status-pretranslation", host = "test.social"),
                    text = "parent source",
                )
            val rootPost =
                timelinePostItem(
                    post =
                        createPost(
                            user = rootUser,
                            accountType = accountType,
                            statusKey = MicroBlogKey(id = "root-status-pretranslation", host = "test.social"),
                            text = "root source",
                        ),
                    inlineParents = listOf(parent),
                )
            val loader =
                FakeLoader("pretranslation") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(rootPost),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)
            val savedRoot = db.statusDao().get(rootPost.statusKey, accountType).first()
            val savedParent = db.statusDao().get(parent.statusKey, accountType).first()
            assertNotNull(savedRoot)
            assertNotNull(savedParent)

            val rootTranslation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedRoot.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }
            val parentTranslation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedParent.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first { it.status == TranslationStatus.Completed }

            assertEquals("root source (${Locale.language})", rootTranslation.payload?.content?.raw)
            assertEquals("parent source (${Locale.language})", parentTranslation.payload?.content?.raw)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsPreTranslationForLongTextPosts() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-longtext-home", host = "test.social")
            val longText = buildString { repeat(520) { append('长') } }
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-longtext-home", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-longtext-home", "test.social"),
                    text = longText,
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                db.translationDao().get(
                    entityType = TranslationEntityType.Status,
                    entityKey = savedStatus.id,
                    targetLanguage = Locale.language,
                )
            assertNull(translation)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsAiTranslationWhenSourceLanguageMatchesTargetLanguage() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-same-language", host = "test.social")
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-same-language", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-same-language", "test.social"),
                    text = "已经是中文",
                ).copy(
                    sourceLanguages = persistentListOf(Locale.language),
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                withTimeout(5_000) {
                    db
                        .translationDao()
                        .find(
                            entityType = TranslationEntityType.Status,
                            entityKey = savedStatus.id,
                            targetLanguage = Locale.language,
                        ).filterNotNull()
                        .first()
                }
            assertEquals(TranslationStatus.Skipped, translation.status)
            assertEquals("source_language_matches_target", translation.statusReason)
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineRequeuesExcludedLanguageSkippedTranslationAfterExclusionRemoved() =
        runTest {
            val excludedLanguage = nonTargetLanguageTag()
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig =
                        aiPreTranslateConfig().copy(
                            autoTranslateExcludedLanguages = listOf(excludedLanguage),
                        ),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = scope,
                )
            try {
                val accountKey = MicroBlogKey(id = "account-excluded-requeue", host = "test.social")
                val post =
                    createPost(
                        accountType = AccountType.Specific(accountKey),
                        user = profile(MicroBlogKey("user-excluded-requeue", "test.social"), "User"),
                        statusKey = MicroBlogKey("status-excluded-requeue", "test.social"),
                        text = "requeue source",
                    ).copy(sourceLanguages = persistentListOf(excludedLanguage))
                val savedStatus =
                    TimelinePagingMapper
                        .toDb(post, pagingKey = "home")
                        .status.status.data
                db.statusDao().insertAll(listOf(savedStatus))

                preTranslationService.enqueueStatuses(listOf(savedStatus), allowLongText = false)

                val skippedTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = savedStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Skipped }
                    }
                assertEquals(PreTranslationStoreSupport.SKIPPED_EXCLUDED_LANGUAGE_REASON, skippedTranslation.statusReason)

                appDataStore.appSettingsStore.updateData {
                    it.copy(
                        translateConfig =
                            it.translateConfig.copy(
                                autoTranslateExcludedLanguages = emptyList(),
                            ),
                    )
                }

                preTranslationService.enqueueStatuses(listOf(savedStatus), allowLongText = false)

                val completedTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = savedStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Completed }
                    }
                assertEquals("requeue source (${Locale.language})", completedTranslation.payload?.content?.raw)
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineAcceptsAiSkippedTranslationResult() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), SkippingOnDeviceAI()),
                    coroutineScope = scope,
                )
            try {
                val accountKey = MicroBlogKey(id = "account-ai-skipped", host = "test.social")
                val post =
                    createPost(
                        accountType = AccountType.Specific(accountKey),
                        user = profile(MicroBlogKey("user-ai-skipped", "test.social"), "User"),
                        statusKey = MicroBlogKey("status-ai-skipped", "test.social"),
                        text = "already target language",
                    )
                val loader =
                    FakeLoader("home") { request ->
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(
                                    data = listOf(post),
                                    nextKey = null,
                                )
                            }

                            is PagingRequest.Append -> {
                                error("No append expected")
                            }

                            is PagingRequest.Prepend -> {
                                error("No prepend expected")
                            }
                        }
                    }
                val mediator =
                    TimelineRemoteMediator(
                        loader = loader,
                        database = db,
                        allowLongText = false,
                        preTranslationService = preTranslationService,
                    )

                val mediatorResult =
                    mediator.load(
                        loadType = LoadType.REFRESH,
                        state =
                            PagingState(
                                pages = emptyList(),
                                anchorPosition = null,
                                config = PagingConfig(pageSize = 20),
                                leadingPlaceholderCount = 0,
                            ),
                    )
                assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

                val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
                assertNotNull(savedStatus)
                val translation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = savedStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Skipped }
                    }
                assertEquals(TranslationStatus.Skipped, translation.status)
                assertEquals("same_language", translation.statusReason)
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun homeTimelineSkipsPreTranslationForNonTranslatableOnlyPosts() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = CoroutineScope(Dispatchers.Unconfined),
                )
            val accountKey = MicroBlogKey(id = "account-emoji-only", host = "test.social")
            val post =
                createPost(
                    accountType = AccountType.Specific(accountKey),
                    user = profile(MicroBlogKey("user-emoji-only", "test.social"), "User"),
                    statusKey = MicroBlogKey("status-emoji-only", "test.social"),
                    text = "😀🎉✨   #tag https://example.com",
                )
            val loader =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(post),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val mediator =
                TimelineRemoteMediator(
                    loader = loader,
                    database = db,
                    allowLongText = false,
                    preTranslationService = preTranslationService,
                )

            val mediatorResult =
                mediator.load(
                    loadType = LoadType.REFRESH,
                    state =
                        PagingState(
                            pages = emptyList(),
                            anchorPosition = null,
                            config = PagingConfig(pageSize = 20),
                            leadingPlaceholderCount = 0,
                        ),
                )
            assertTrue(mediatorResult is androidx.paging.RemoteMediator.MediatorResult.Success)

            val savedStatus = db.statusDao().get(post.statusKey, AccountType.Specific(accountKey)).first()
            assertNotNull(savedStatus)
            val translation =
                db
                    .translationDao()
                    .find(
                        entityType = TranslationEntityType.Status,
                        entityKey = savedStatus.id,
                        targetLanguage = Locale.language,
                    ).filterNotNull()
                    .first()
            assertEquals(TranslationStatus.Skipped, translation.status)
            assertEquals("non_translatable_only", translation.statusReason)
        }

    @Test
    fun platformTranslationSkipsProviderAndPreferenceChangeUsesProviderCache() =
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig =
                        aiPreTranslateConfig().copy(
                            preferPlatformTranslation = true,
                        ),
                )
            }
            var providerCalls = 0
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val service: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = scope,
                    batchTranslator = { _, _, _, sourceDocument, targetLanguage, _ ->
                        providerCalls += 1
                        completedTranslationJson(sourceDocument, targetLanguage)
                    },
                )
            try {
                val accountKey = MicroBlogKey("platform-account", "x.com")
                val post =
                    createPost(
                        accountType = AccountType.Specific(accountKey),
                        user = profile(MicroBlogKey("platform-user", "x.com"), "User"),
                        statusKey = MicroBlogKey("platform-status", "x.com"),
                        text = "source content",
                    ).copy(
                        platformType = PlatformType.xQt,
                        content =
                            UiTranslatableText(
                                original = "source content".toUiPlainText(),
                                translation = "platform translation".toUiPlainText(listOf(Locale.language)),
                            ),
                    )
                val status =
                    TimelinePagingMapper
                        .toDb(post, pagingKey = "home")
                        .status.status.data

                service.enqueueStatuses(listOf(status))

                val platformTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = status.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Completed }
                    }
                assertEquals(0, providerCalls)
                assertEquals("platform translation", platformTranslation.payload?.content?.raw)

                appDataStore.appSettingsStore.updateData {
                    it.copy(
                        translateConfig = it.translateConfig.copy(preferPlatformTranslation = false),
                    )
                }
                service.enqueueStatuses(listOf(status))

                val providerTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = status.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first {
                                it.status == TranslationStatus.Completed &&
                                    it.sourceHash != platformTranslation.sourceHash
                            }
                    }
                assertEquals(1, providerCalls)
                assertNotEquals(platformTranslation.sourceHash, providerTranslation.sourceHash)
                assertEquals("source content (${Locale.language})", providerTranslation.payload?.content?.raw)
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }

    @Test
    fun platformTranslationStillHonorsExcludedSourceLanguages() =
        runTest {
            val excludedLanguage = nonTargetLanguageTag()
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig =
                        aiPreTranslateConfig().copy(
                            preferPlatformTranslation = true,
                            autoTranslateExcludedLanguages = listOf(excludedLanguage),
                        ),
                )
            }
            var providerCalls = 0
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val service: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = scope,
                    batchTranslator = { _, _, _, sourceDocument, targetLanguage, _ ->
                        providerCalls += 1
                        completedTranslationJson(sourceDocument, targetLanguage)
                    },
                )
            try {
                val accountKey = MicroBlogKey("excluded-platform-account", "x.com")
                val post =
                    createPost(
                        accountType = AccountType.Specific(accountKey),
                        user = profile(MicroBlogKey("excluded-platform-user", "x.com"), "User"),
                        statusKey = MicroBlogKey("excluded-platform-status", "x.com"),
                        text = "source content",
                    ).copy(
                        platformType = PlatformType.xQt,
                        sourceLanguages = persistentListOf(excludedLanguage),
                        content =
                            UiTranslatableText(
                                original = "source content".toUiPlainText(),
                                translation = "platform translation".toUiPlainText(listOf(Locale.language)),
                            ),
                    )
                val status =
                    TimelinePagingMapper
                        .toDb(post, pagingKey = "home")
                        .status.status.data

                service.enqueueStatuses(listOf(status))

                val translation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = status.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Skipped }
                    }
                assertEquals(0, providerCalls)
                assertEquals(PreTranslationStoreSupport.SKIPPED_EXCLUDED_LANGUAGE_REASON, translation.statusReason)
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }

    @Test
    fun preTranslationBatchDocumentAllowsMissingTargetLanguageInResponse() {
        val document =
            """{"version":1,"items":[]}""".decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )

        assertEquals("", document.targetLanguage)
        assertTrue(document.items.isEmpty())
    }

    @Test
    fun preTranslationServiceMarksStaleInFlightTranslationsAsFailedOnStartup() {
        runTest {
            db.translationDao().insert(
                dev.dimension.flare.data.database.cache.model.DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey = "status:stale-in-flight",
                    targetLanguage = Locale.language,
                    sourceHash = "hash-stale",
                    status = TranslationStatus.Translating,
                    updatedAt = 1L,
                ),
            )

            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

            OnlinePreTranslationService(
                database = db,
                appDataStore = appDataStore,
                aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                coroutineScope = scope,
            )

            yield()

            val cleaned =
                db.translationDao().get(
                    entityType = TranslationEntityType.Status,
                    entityKey = "status:stale-in-flight",
                    targetLanguage = Locale.language,
                )
            assertNotNull(cleaned)
            assertEquals(TranslationStatus.Failed, cleaned.status)
            assertEquals("stale_in_flight", cleaned.statusReason)

            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun queuedPreTranslationWritesPendingBeforeExecutionStarts() {
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), BlockingOnDeviceAI(started, release)),
                    coroutineScope = scope,
                )
            try {
                val accountKey = MicroBlogKey(id = "account-pending-queue", host = "test.social")
                val accountType = AccountType.Specific(accountKey)
                val firstStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-pending-1", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-pending-1", "test.social"),
                                text = "first source",
                            ),
                            pagingKey = "home",
                        ).status.status.data
                val secondStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-pending-2", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-pending-2", "test.social"),
                                text = "second source",
                            ),
                            pagingKey = "home",
                        ).status.status.data

                preTranslationService.enqueueStatuses(listOf(firstStatus), allowLongText = false)
                withTimeout(5_000) {
                    started.await()
                }

                preTranslationService.enqueueStatuses(listOf(secondStatus), allowLongText = false)

                val pendingTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = secondStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Pending }
                    }
                assertEquals(TranslationStatus.Pending, pendingTranslation.status)
            } finally {
                release.complete(Unit)
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }

    @Test
    fun providerSwitchCancelsOldQueueAndLetsNewProviderCompleteImmediately() {
        runTest {
            val appDataStore = AppDataStore(fileStorage)
            appDataStore.appSettingsStore.updateData {
                it.copy(
                    language = Locale.language,
                    translateConfig = aiPreTranslateConfig(),
                    aiConfig =
                        AppSettings.AiConfig(
                            type = AppSettings.AiConfig.Type.OnDevice,
                        ),
                )
            }
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val preTranslationService: PreTranslationService =
                OnlinePreTranslationService(
                    database = db,
                    appDataStore = appDataStore,
                    aiCompletionService = AiCompletionService(OpenAIService(), TestOnDeviceAI()),
                    coroutineScope = scope,
                    batchTranslator = { settings, _, _, sourceDocument, targetLanguage, _ ->
                        when (settings.translateConfig.provider) {
                            AppSettings.TranslateConfig.Provider.AI -> {
                                started.complete(Unit)
                                withContext(NonCancellable) {
                                    release.await()
                                }
                                finished.complete(Unit)
                                completedTranslationJson(
                                    document = sourceDocument,
                                    targetLanguage = targetLanguage,
                                )
                            }

                            AppSettings.TranslateConfig.Provider.GoogleWeb -> {
                                completedTranslationJson(
                                    document = sourceDocument,
                                    targetLanguage = targetLanguage,
                                )
                            }

                            else -> {
                                error("Unexpected provider in provider switch test")
                            }
                        }
                    },
                )
            try {
                val accountKey = MicroBlogKey(id = "account-provider-switch", host = "test.social")
                val accountType = AccountType.Specific(accountKey)
                val firstStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-provider-switch-1", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-provider-switch-1", "test.social"),
                                text = "first source",
                            ),
                            pagingKey = "home",
                        ).status.status.data
                val secondStatus =
                    TimelinePagingMapper
                        .toDb(
                            createPost(
                                accountType = accountType,
                                user = profile(MicroBlogKey("user-provider-switch-2", "test.social"), "User"),
                                statusKey = MicroBlogKey("status-provider-switch-2", "test.social"),
                                text = "second source",
                            ),
                            pagingKey = "home",
                        ).status.status.data

                preTranslationService.enqueueStatuses(listOf(firstStatus), allowLongText = false)
                withTimeout(5_000) {
                    started.await()
                }

                appDataStore.appSettingsStore.updateData {
                    it.copy(
                        translateConfig =
                            it.translateConfig.copy(
                                provider = AppSettings.TranslateConfig.Provider.GoogleWeb,
                            ),
                    )
                }
                withTimeout(5_000) {
                    db
                        .translationDao()
                        .find(
                            entityType = TranslationEntityType.Status,
                            entityKey = firstStatus.id,
                            targetLanguage = Locale.language,
                        ).first { it == null }
                }

                preTranslationService.enqueueStatuses(listOf(secondStatus), allowLongText = false)

                val secondTranslation =
                    withTimeout(5_000) {
                        db
                            .translationDao()
                            .find(
                                entityType = TranslationEntityType.Status,
                                entityKey = secondStatus.id,
                                targetLanguage = Locale.language,
                            ).filterNotNull()
                            .first { it.status == TranslationStatus.Completed }
                    }
                assertEquals("second source (${Locale.language})", secondTranslation.payload?.content?.raw)

                release.complete(Unit)
                withTimeout(5_000) {
                    finished.await()
                }
                yield()

                val firstTranslation =
                    db.translationDao().get(
                        entityType = TranslationEntityType.Status,
                        entityKey = firstStatus.id,
                        targetLanguage = Locale.language,
                    )
                assertNull(firstTranslation)
            } finally {
                release.complete(Unit)
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }

    @Test
    fun refreshDeduplicatesSamePostReturnedByMultipleSubTimelines() =
        runTest {
            val accountKey = MicroBlogKey("timeline", "mastodon.example")
            val accountType = AccountType.Specific(accountKey)
            val user = profile(MicroBlogKey("user", "mastodon.example"), "User")
            val duplicatedPost =
                createPost(
                    accountType = accountType,
                    user = user,
                    statusKey = MicroBlogKey("same", "mastodon.example"),
                    text = "duplicate",
                )

            val first =
                FakeLoader("home") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(duplicatedPost),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val second =
                FakeLoader("list") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(duplicatedPost),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(1, result.data.size)
            assertEquals(duplicatedPost.itemKey, result.data.single().itemKey)
        }

    @Test
    fun refreshKeepsSamePostFromDifferentAccountsAsSeparateItems() =
        runTest {
            val firstAccount = AccountType.Specific(MicroBlogKey("timeline-a", "mastodon.example"))
            val secondAccount = AccountType.Specific(MicroBlogKey("timeline-b", "mastodon.example"))
            val sharedStatusKey = MicroBlogKey("same", "mastodon.example")

            val firstPost =
                createPost(
                    accountType = firstAccount,
                    user = profile(MicroBlogKey("user-a", "mastodon.example"), "User A"),
                    statusKey = sharedStatusKey,
                    text = "duplicate",
                )
            val secondPost =
                createPost(
                    accountType = secondAccount,
                    user = profile(MicroBlogKey("user-b", "mastodon.example"), "User B"),
                    statusKey = sharedStatusKey,
                    text = "duplicate",
                )

            val first =
                FakeLoader("home_a") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(firstPost),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }
            val second =
                FakeLoader("home_b") { request ->
                    when (request) {
                        PagingRequest.Refresh -> {
                            PagingResult(
                                data = listOf(secondPost),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Append -> {
                            error("No append expected")
                        }

                        is PagingRequest.Prepend -> {
                            error("No prepend expected")
                        }
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(first, second))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(2, result.data.size)
            assertEquals(
                setOf(firstPost.itemKey, secondPost.itemKey),
                result.data.map { it.itemKey }.toSet(),
            )
        }

    private class FakeLoader(
        override val pagingKey: String,
        override val supportPrepend: Boolean = false,
        override val collapseReplyChains: Boolean = true,
        private val onLoad: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        val requests = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests += request
            return onLoad(request)
        }
    }

    private fun feed(
        url: String,
        createdAtEpochMs: Long,
    ) = UiTimelineV2.Feed(
        title = url.substringAfterLast('/'),
        description = null,
        url = url,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs).toUi(),
        source =
            UiTimelineV2.Feed.Source(
                name = "test",
                icon = null,
            ),
        accountType = AccountType.Guest,
    )

    private fun profile(
        key: MicroBlogKey,
        name: String,
    ): UiProfile =
        UiProfile(
            key = key,
            handle =
                UiHandle(
                    raw = key.id,
                    host = key.host,
                ),
            avatar = "https://${key.host}/${key.id}.png",
            nameInternal = name.toUiPlainText(),
            platformType = PlatformType.Mastodon,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun createPost(
        accountType: AccountType,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
        parents: List<UiTimelineV2.Post> = emptyList(),
    ): UiTimelineV2.Post {
        val references =
            parents
                .lastOrNull()
                ?.let {
                    persistentListOf(
                        UiTimelineV2.Post.Reference(
                            statusKey = it.statusKey,
                            type = ReferenceType.Reply,
                        ),
                    )
                } ?: persistentListOf()
        return UiTimelineV2.Post(
            platformType = PlatformType.Mastodon,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            content = UiTranslatableText(text.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = references,
            clickEvent = ClickEvent.Noop,
            accountType = accountType,
        )
    }

    private fun timelinePostItem(
        post: UiTimelineV2.Post,
        inlineParents: List<UiTimelineV2.Post>,
    ): UiTimelineV2.TimelinePostItem {
        val references =
            (
                post.references +
                    inlineParents
                        .lastOrNull()
                        ?.let {
                            UiTimelineV2.Post.Reference(
                                statusKey = it.statusKey,
                                type = ReferenceType.Reply,
                            )
                        }.let(::listOfNotNull)
            ).distinctBy { it.type to it.statusKey }
        return UiTimelineV2.TimelinePostItem(
            post = post.copy(references = references.toPersistentList()),
            presentation =
                UiTimelineV2.PostPresentation(
                    inlineParents = inlineParents.toPersistentList(),
                ),
        )
    }
}

private class TestOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return completedTranslationTemplate(document, targetLanguage)
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private class BlockingOnDeviceAI(
    private val started: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        started.complete(Unit)
        release.await()
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return completedTranslationTemplate(document, targetLanguage)
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private class SkippingOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val document =
            source.decodeJson(
                dev.dimension.flare.data.translation.PreTranslationBatchDocument
                    .serializer(),
            )
        return skippedTranslationTemplate(document, "same_language")
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
}

private fun completedTranslationJson(
    document: dev.dimension.flare.data.translation.PreTranslationBatchDocument,
    targetLanguage: String,
): String =
    document
        .copy(
            items =
                document.items.map { item ->
                    item.copy(
                        status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Completed,
                        payload = requireNotNull(item.payload).translated(targetLanguage),
                        reason = null,
                    )
                },
        ).encodeJson(
            dev.dimension.flare.data.translation.PreTranslationBatchDocument
                .serializer(),
        )

private fun completedTranslationTemplate(
    document: dev.dimension.flare.data.translation.PreTranslationBatchDocument,
    targetLanguage: String,
): String =
    dev.dimension.flare.data.translation.AiPlaceholderTranslationSupport
        .buildPromptTemplate(
            document.copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Completed,
                            payload = requireNotNull(item.payload).translated(targetLanguage),
                            reason = null,
                        )
                    },
            ),
        )

private fun skippedTranslationTemplate(
    document: dev.dimension.flare.data.translation.PreTranslationBatchDocument,
    reason: String,
): String =
    dev.dimension.flare.data.translation.AiPlaceholderTranslationSupport
        .buildPromptTemplate(
            document.copy(
                items =
                    document.items.map { item ->
                        item.copy(
                            status = dev.dimension.flare.data.translation.PreTranslationBatchItemStatus.Skipped,
                            payload = null,
                            reason = reason,
                        )
                    },
            ),
        )

private fun dev.dimension.flare.data.translation.PreTranslationBatchPayload.translated(
    targetLanguage: String,
): dev.dimension.flare.data.translation.PreTranslationBatchPayload =
    dev.dimension.flare.data.translation.PreTranslationBatchPayload(
        content = content?.translated(targetLanguage),
        contentWarning = contentWarning?.translated(targetLanguage),
        title = title?.translated(targetLanguage),
        description = description?.translated(targetLanguage),
    )

private fun TranslationDocument.translated(targetLanguage: String): TranslationDocument =
    copy(
        blocks =
            blocks.map { block ->
                block.copy(
                    tokens =
                        block.tokens.map { token ->
                            if (token.kind == TranslationTokenKind.Translatable) {
                                token.copy(text = "${token.text} ($targetLanguage)")
                            } else {
                                token
                            }
                        },
                )
            },
    )

private fun nonTargetLanguageTag(): String {
    val target = requireNotNull(canonicalTranslationLanguage(Locale.language))
    return listOf("fr-FR", "de-DE", "es-ES", "ja-JP", "zh-CN")
        .first { candidate ->
            canonicalTranslationLanguage(candidate) != target
        }
}
