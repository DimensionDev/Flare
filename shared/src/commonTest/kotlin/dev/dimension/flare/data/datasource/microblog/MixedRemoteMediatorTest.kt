package dev.dimension.flare.data.datasource.microblog

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MixedRemoteMediatorTest : RobolectricTest() {
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
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
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
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/a_refresh", 1000L)),
                                nextKey = "a_next",
                            )

                        is PagingRequest.Append -> {
                            assertEquals("a_next", request.nextKey)
                            PagingResult(
                                data = listOf(feed("https://example.com/a_append", 900L)),
                                nextKey = null,
                            )
                        }

                        is PagingRequest.Prepend -> error("Prepend should not be requested here")
                    }
                }

            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/b_refresh", 2000L)),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("Second mediator should be filtered out before append")
                        is PagingRequest.Prepend -> error("Prepend should not be requested here")
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
                        PagingRequest.Refresh ->
                            PagingResult(
                                data = listOf(feed("https://example.com/healthy", 3000L)),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mediator = MixedRemoteMediator(db, listOf(failing, healthy))
            val result = mediator.load(pageSize = 20, request = PagingRequest.Refresh)

            assertNull(result.nextKey)
            assertEquals(1, failing.requests.size)
            assertEquals(1, healthy.requests.size)
            assertEquals(1, result.data.size)
            assertEquals("https://example.com/healthy", (result.data.first() as UiTimelineV2.Feed).url)
            assertNotNull(db.pagingTimelineDao().getPagingKey("failing"))
            assertNotNull(db.pagingTimelineDao().getPagingKey("healthy"))
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun refreshWithMultipleItemsPerSubPersistsSortedOrderInDatabase() =
        runTest {
            val first =
                FakeLoader("a") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/a_3000", 3000L),
                                        feed("https://example.com/a_1000", 1000L),
                                    ),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }
            val second =
                FakeLoader("b") { request ->
                    when (request) {
                        PagingRequest.Refresh ->
                            PagingResult(
                                data =
                                    listOf(
                                        feed("https://example.com/b_4000", 4000L),
                                        feed("https://example.com/b_2000", 2000L),
                                    ),
                                nextKey = null,
                            )

                        is PagingRequest.Append -> error("No append expected")
                        is PagingRequest.Prepend -> error("No prepend expected")
                    }
                }

            val mixed = MixedRemoteMediator(db, listOf(first, second))
            val timelineRemoteMediator = TimelineRemoteMediator(loader = mixed, database = db)

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

    private class FakeLoader(
        override val pagingKey: String,
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
        openInBrowser = false,
        accountType = AccountType.Guest,
    )
}
