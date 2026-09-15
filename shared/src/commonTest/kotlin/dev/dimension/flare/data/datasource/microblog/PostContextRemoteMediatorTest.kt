package dev.dimension.flare.data.datasource.microblog

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.ContextPageUpdate
import dev.dimension.flare.data.datasource.microblog.paging.ContextUpdate
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.PostContextLoader
import dev.dimension.flare.data.datasource.microblog.paging.PostContextRemoteMediator
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePageItem
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.toContextUpdate
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant
import dev.dimension.flare.common.PagingState as UiPagingState

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class PostContextRemoteMediatorTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private val accountKey = MicroBlogKey("account", "example.com")
    private val mainKey = key("main")
    private val pagingKey = "context"
    private val state = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(20), 0)

    @BeforeTest
    fun setup() {
        startKoin { modules(module { testSingle<PlatformFormatter> { TestFormatter() } }) }
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
    }

    @Test
    fun stagedRefreshKeepsLongCacheAndLoadsRepliesWithoutAnEdgeHint() =
        runTest {
            val cached = listOf("parent", "main") + (1..60).map { "old-$it" }
            seed(cached)
            val replyStarted = CompletableDeferred<Unit>()
            val replyResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            val mediator =
                PostContextRemoteMediator(
                    loader(
                        update = { request, result, initial ->
                            if (request == PagingRequest.Refresh) {
                                ContextUpdate(result.data)
                            } else {
                                result.toContextUpdate(mainKey, PagingRequest.Refresh, initial)
                            }
                        },
                    ) { request ->
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(listOf(post("main", "fresh main")), nextKey = "first")
                            }

                            is PagingRequest.Append -> {
                                assertEquals("first", request.nextKey)
                                replyStarted.complete(Unit)
                                replyResponse.await()
                            }

                            is PagingRequest.Prepend -> {
                                error("Unexpected request")
                            }
                        }
                    },
                    db,
                )
            val refresh = async { mediator.load(LoadType.REFRESH, state) }
            replyStarted.await()
            assertEquals(cached, rows())
            assertEquals("fresh main", text("main"))
            assertIs<LoadState.NotLoading>(mediator.loadStates.value.refresh)
            assertIs<LoadState.Loading>(mediator.loadStates.value.append)
            replyResponse.complete(PagingResult(listOf(post("new-parent"), post("main"), post("new-reply"))))
            assertIs<RemoteMediator.MediatorResult.Success>(refresh.await())
            assertEquals(listOf("new-parent", "main", "new-reply"), rows())
            assertEquals(LoadState.NotLoading(true), mediator.loadStates.value.append)
        }

    @Test
    fun independentSidesPublishAndRetryOnlyTheFailedFirstPage() =
        runTest {
            seed(listOf("old-parent", "main", "old-reply"))
            val requests = mutableListOf<PagingRequest>()
            val beforeResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            val afterResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            val beforeStarted = CompletableDeferred<Unit>()
            val afterStarted = CompletableDeferred<Unit>()
            var failAfter = true
            val mediator =
                PostContextRemoteMediator(
                    loader(
                        update = { request, result, initial ->
                            if (request == PagingRequest.Refresh) {
                                ContextUpdate(result.data)
                            } else {
                                result.toContextUpdate(mainKey, request, initial)
                            }
                        },
                    ) { request ->
                        requests += request
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(listOf(post("main")), nextKey = "first-after", previousKey = "first-before")
                            }

                            is PagingRequest.Prepend -> {
                                beforeStarted.complete(Unit)
                                beforeResponse.await()
                            }

                            is PagingRequest.Append -> {
                                afterStarted.complete(Unit)
                                if (failAfter) {
                                    afterResponse.await()
                                    error("Replies unavailable")
                                }
                                PagingResult(emptyList())
                            }
                        }
                    },
                    db,
                )
            val refresh = async { mediator.load(LoadType.REFRESH, state) }
            beforeStarted.await()
            afterStarted.await()
            beforeResponse.complete(PagingResult(listOf(post("new-parent"), post("main", "updated main")), previousKey = "more-before"))
            mediator.loadStates.first { it.prepend is LoadState.NotLoading }
            assertEquals(listOf("new-parent", "main", "old-reply"), rows())
            assertIs<LoadState.Loading>(mediator.loadStates.value.append)
            afterResponse.complete(PagingResult(emptyList()))
            assertIs<RemoteMediator.MediatorResult.Error>(refresh.await())
            assertEquals("cached-after", db.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey)
            assertEquals("more-before", db.pagingTimelineDao().getPagingKey(pagingKey)?.prevKey)
            assertIs<LoadState.Error>(mediator.loadStates.value.append)
            failAfter = false
            mediator.prepareRetry()
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            assertEquals(listOf("new-parent", "main"), rows())
            assertEquals(1, requests.count { it == PagingRequest.Refresh })
            assertEquals(1, requests.count { it is PagingRequest.Prepend })
            assertEquals(2, requests.count { it == PagingRequest.Append("first-after") })
            assertNull(db.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey)
            assertEquals("more-before", db.pagingTimelineDao().getPagingKey(pagingKey)?.prevKey)
        }

    @Test
    fun paginationUpdatesExistingPostsWithoutMovingOrDuplicatingThemAndClearsBothCursors() =
        runTest {
            val mediator =
                PostContextRemoteMediator(
                    loader { request ->
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(
                                    listOf(post("parent"), post("main"), post("reply")),
                                    nextKey = "after",
                                    previousKey = "before",
                                )
                            }

                            is PagingRequest.Append -> {
                                PagingResult(
                                    listOf(
                                        post("parent", "edited parent"),
                                        post("main", "edited main"),
                                        post("reply", "edited reply"),
                                        post("last"),
                                    ),
                                )
                            }

                            is PagingRequest.Prepend -> {
                                PagingResult(listOf(post("first"), post("parent", "new parent"), post("main", "new main")))
                            }
                        }
                    },
                    db,
                )
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            assertEquals(listOf("parent", "main", "reply"), rows())
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.APPEND, state))
            assertEquals(listOf("parent", "main", "reply", "last"), rows())
            assertEquals("edited parent", text("parent"))
            assertEquals("edited main", text("main"))
            assertEquals("edited reply", text("reply"))
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.PREPEND, state))
            assertEquals(listOf("first", "parent", "main", "reply", "last"), rows())
            assertEquals("new main", text("main"))
            assertNull(db.pagingTimelineDao().getPagingKey(pagingKey)?.prevKey)
            assertNull(db.pagingTimelineDao().getPagingKey(pagingKey)?.nextKey)
        }

    @Test
    fun responseCanReplaceBothSidesAndUpdatePostsOutsideTheirMembership() =
        runTest {
            seed(listOf("parent", "main", "reply"))
            val mediator =
                PostContextRemoteMediator(
                    loader(update = { _, result, _ ->
                        ContextUpdate(
                            posts = result.data,
                            before = ContextPageUpdate(emptyList(), cursor = null, replace = true),
                            after = ContextPageUpdate(listOf(key("parent")), cursor = null, replace = true),
                        )
                    }) { PagingResult(listOf(post("main"), post("parent"), post("reply", "updated removed reply"))) },
                    db,
                )
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            assertEquals(listOf("main", "parent"), rows())
            assertEquals("updated removed reply", text("reply"))
        }

    @Test
    fun lateResponseFromPreviousRefreshCannotOverwriteNewContext() =
        runTest {
            seed(listOf("main", "cached"))
            val firstStarted = CompletableDeferred<Unit>()
            val firstResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            var calls = 0
            val mediator =
                PostContextRemoteMediator(
                    loader {
                        if (calls++ == 0) {
                            firstStarted.complete(Unit)
                            firstResponse.await()
                        } else {
                            PagingResult(listOf(post("main", "new main"), post("new reply")))
                        }
                    },
                    db,
                )
            val oldRefresh = async { mediator.load(LoadType.REFRESH, state) }
            firstStarted.await()
            assertIs<RemoteMediator.MediatorResult.Success>(mediator.load(LoadType.REFRESH, state))
            firstResponse.complete(PagingResult(listOf(post("main", "old main"), post("old reply"))))
            oldRefresh.await()
            assertEquals(listOf("main", "new reply"), rows())
            assertEquals("new main", text("main"))
            assertFalse(mediator.loadStates.value.append is LoadState.Loading)
        }

    @Test
    fun slowerPrependCannotRollBackPostsUpdatedByALaterAppend() =
        runTest {
            val beforeStarted = CompletableDeferred<Unit>()
            val beforeResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            val mediator =
                PostContextRemoteMediator(
                    loader { request ->
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(listOf(post("main")), nextKey = "after", previousKey = "before")
                            }

                            is PagingRequest.Prepend -> {
                                beforeStarted.complete(Unit)
                                beforeResponse.await()
                            }

                            is PagingRequest.Append -> {
                                PagingResult(listOf(post("main", "new main"), post("reply")))
                            }
                        }
                    },
                    db,
                )
            mediator.load(LoadType.REFRESH, state)
            val prepend = async { mediator.load(LoadType.PREPEND, state) }
            beforeStarted.await()
            mediator.load(LoadType.APPEND, state)
            assertIs<LoadState.Loading>(mediator.loadStates.value.prepend)
            beforeResponse.complete(PagingResult(listOf(post("parent"), post("main", "old main"))))
            assertIs<RemoteMediator.MediatorResult.Success>(prepend.await())
            assertEquals(listOf("parent", "main", "reply"), rows())
            assertEquals("new main", text("main"))
        }

    @Test
    fun failedSideCanRetryWhileTheOtherSideIsStillLoading() =
        runTest {
            seed(listOf("old-parent", "main", "old-reply"))
            val beforeResponse = CompletableDeferred<PagingResult<UiTimelineV2>>()
            var afterCalls = 0
            val mediator =
                PostContextRemoteMediator(
                    loader(update = { request, result, initial ->
                        if (request == PagingRequest.Refresh) {
                            ContextUpdate(result.data)
                        } else {
                            result.toContextUpdate(mainKey, request, initial)
                        }
                    }) { request ->
                        when (request) {
                            PagingRequest.Refresh -> {
                                PagingResult(listOf(post("main")), nextKey = "after", previousKey = "before")
                            }

                            is PagingRequest.Prepend -> {
                                beforeResponse.await()
                            }

                            is PagingRequest.Append -> {
                                if (afterCalls++ == 0) error("reply failed")
                                PagingResult(listOf(post("reply")))
                            }
                        }
                    },
                    db,
                )
            val refresh = async { mediator.load(LoadType.REFRESH, state) }
            mediator.loadStates.first { it.append is LoadState.Error }
            mediator.prepareRetry()
            mediator.loadStates.first { it.append is LoadState.NotLoading }
            assertIs<LoadState.Loading>(mediator.loadStates.value.prepend)
            assertEquals(listOf("old-parent", "main", "reply"), rows())
            beforeResponse.complete(PagingResult(listOf(post("parent"))))
            assertIs<RemoteMediator.MediatorResult.Success>(refresh.await())
            assertEquals(listOf("parent", "main", "reply"), rows())
            assertEquals(2, afterCalls)
        }

    @Test
    fun pagerPublishesTheUpdatedMainPostBeforeTheSlowRepliesFinish() =
        runTest {
            seed(listOf("parent", "main", "cached-reply"))
            val replies = CompletableDeferred<PagingResult<UiTimelineV2>>()
            val mediator =
                PostContextRemoteMediator(
                    loader(update = { request, result, initial ->
                        if (request == PagingRequest.Refresh) {
                            ContextUpdate(result.data)
                        } else {
                            result.toContextUpdate(mainKey, PagingRequest.Refresh, initial)
                        }
                    }) { request ->
                        if (request == PagingRequest.Refresh) {
                            PagingResult(listOf(post("main", "fresh main")), nextKey = "first")
                        } else {
                            replies.await()
                        }
                    },
                    db,
                )
            val pageCache = TimelineDbPageCache()
            val pager =
                Pager(
                    config = offsetPagingConfig,
                    remoteMediator = mediator,
                    pagingSourceFactory = { OffsetFromStartPagingSource(TimelineDbPageLoader(db, pagingKey, pageCache)) },
                )
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val states = MutableStateFlow<UiPagingState<TimelinePageItem>>(UiPagingState.Loading())
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) {
                        val loading by mediator.loadStates.collectAsState()
                        pager.flow.collectAsLazyPagingItems().toPagingState(loading)
                    }.collect { states.value = it }
                }
            try {
                val pending =
                    states.filterIsInstance<UiPagingState.Success<TimelinePageItem>>().first {
                        it
                            .peek(1)
                            ?.baseItem
                            ?.asTimelinePostItem()
                            ?.post
                            ?.content
                            ?.original
                            ?.raw == "fresh main"
                    }
                assertEquals(
                    "cached-reply",
                    pending
                        .peek(2)
                        ?.baseItem
                        ?.statusKey
                        ?.id,
                )
                assertIs<LoadState.Loading>(pending.appendState)
                replies.complete(PagingResult(listOf(post("parent"), post("main", "fresh main"), post("new-reply"))))
                states.filterIsInstance<UiPagingState.Success<TimelinePageItem>>().first {
                    it
                        .peek(2)
                        ?.baseItem
                        ?.statusKey
                        ?.id == "new-reply"
                }
            } finally {
                job.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }

    private fun loader(
        update: (PagingRequest, PagingResult<UiTimelineV2>, Boolean) -> ContextUpdate = { request, result, initial ->
            result.toContextUpdate(mainKey, request, initial)
        },
        response: suspend (PagingRequest) -> PagingResult<UiTimelineV2>,
    ): PostContextLoader =
        object : PostContextLoader {
            override val statusKey = mainKey
            override val accountKey = this@PostContextRemoteMediatorTest.accountKey
            override val pagingKey = this@PostContextRemoteMediatorTest.pagingKey
            override val supportPrepend = true

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ) = response(request)

            override fun contextUpdate(
                request: PagingRequest,
                result: PagingResult<UiTimelineV2>,
                initial: Boolean,
            ) = update(request, result, initial)
        }

    private suspend fun seed(ids: List<String>) {
        val anchor = ids.indexOf("main")
        saveToDatabase(db, TimelinePagingMapper.toDb(ids.map { post(it) }, pagingKey, ids.indices.map { (it - anchor).toLong() }))
        db.pagingTimelineDao().insertPagingKey(DbPagingKey(pagingKey, nextKey = "cached-after", prevKey = "cached-before"))
    }

    private suspend fun rows() = db.pagingTimelineDao().getTimelinePage(pagingKey, 0, 100).map { it.statusData.statusKey.id }

    private suspend fun text(id: String) =
        (
            db
                .statusDao()
                .get(key(id), AccountType.Specific(accountKey))
                .first()
                ?.content as UiTimelineV2.Post
        ).content.original.raw

    private fun key(id: String) = MicroBlogKey(id, "example.com")

    private fun post(
        id: String,
        text: String = id,
    ) = UiTimelineV2.Post(
        platformId = "Mastodon",
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText(text.toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = key(id),
        card = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z").toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        references = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(accountKey),
    )
}
