package dev.dimension.flare.ui.presenter

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.HomeTimelineRefreshSessions
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePageItem
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TabSettingsV2
import dev.dimension.flare.data.model.tab.TimelineLoaderFactory
import dev.dimension.flare.data.model.tab.TimelineMergePolicy
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.remoteLoaderFactory
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.unavailableAccountService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class TimelineSharedCacheTest {
    @Test fun aPrependSourceStillRefreshesWhenOpenedFromAnOrdinaryEntry() = ordinaryRefresh(prepend = true)

    @Test fun ordinarySingleSourcePreservesHomeRowsAndSession() = ordinaryRefresh()

    @Test fun ordinaryMixedTimeUsesTheHomeRankScale() = ordinaryRefresh(mixed = true)

    @Test fun disabledReadingKeepsOrdinaryRefreshSemantics() = ordinaryRefresh(remember = false)

    @Test fun removingAnAccountCancelsItsPendingHomeResolution() = ordinaryRefresh(missingFirst = true, removeAccount = true)

    @Test fun aBrokenHomeLoaderDoesNotBlockAnotherSharedSource() = ordinaryRefresh(brokenFirst = true)

    @Test fun aMissingHomeAccountDoesNotBlockAnotherSharedSource() = ordinaryRefresh(missingFirst = true)

    @Test fun anUnrelatedHomeSourceDoesNotChangeOrdinaryRefresh() = ordinaryRefresh(shared = false)

    private fun ordinaryRefresh(
        prepend: Boolean = false,
        mixed: Boolean = false,
        remember: Boolean = true,
        shared: Boolean = true,
        missingFirst: Boolean = false,
        removeAccount: Boolean = false,
        brokenFirst: Boolean = false,
    ) = runBlocking {
        val main = Executors.newSingleThreadExecutor { Thread(it, "timeline-ui") }.asCoroutineDispatcher()
        Dispatchers.setMain(main)
        val root = createTestRootPath()
        val storage = OkioFileStorage(createTestFileSystem(), root)
        val cache =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(
                    createDatabaseDriver(),
                ).setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val app =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(
                    createDatabaseDriver(),
                ).setQueryCoroutineContext(Dispatchers.IO)
                .build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val headRequests = AtomicInteger()
            var response = PagingResult<UiTimelineV2>(listOf(post(10)), nextKey = "tail", previousKey = if (prepend) "head" else null)
            val leaf =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "featured_shared"
                    override val supportPrepend = prepend
                    override val refreshGapOncePerSession = true

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        if (request is PagingRequest.Append) return PagingResult(emptyList())
                        headRequests.incrementAndGet()
                        return response
                    }
                }
            val spec =
                TimelineSpec(
                    id = "test-shared",
                    title = UiStrings.Home,
                    icon = UiIcon.Home.asType(),
                    serializer = TimelineSpec.AccountBasedData.serializer(),
                    targetId = { "source" },
                    loaderFactory = remoteLoaderFactory { leaf },
                )
            val resolving = CompletableDeferred<Unit>()
            val unavailable =
                spec.copy(
                    id = "missing",
                    loaderFactory =
                        TimelineLoaderFactory {
                            _,
                            _,
                            ->
                            flow {
                                resolving.complete(Unit)
                                awaitCancellation()
                            }
                        },
                )
            val broken = spec.copy(id = "broken", loaderFactory = remoteLoaderFactory { error("Broken loader configuration") })
            val unrelated =
                spec.copy(
                    id = "unrelated",
                    loaderFactory =
                        remoteLoaderFactory {
                            object : CacheableRemoteLoader<UiTimelineV2> by leaf {
                                override val pagingKey = "other"
                            }
                        },
                )
            val runtime =
                PlatformRuntimeData(platformSpecs = emptyList(), extraTimelineSpecs = listOf(spec, unavailable, unrelated, broken))
            val resolver = TimelineResolver(runtime, unavailableAccountService())
            val appData = AppDataStore(storage)
            appData.appSettingsStore.updateData { it.copy(rememberHomeTimelinePosition = remember, mxgaEnabled = false) }
            val settings = SettingsRepository(storage, appData, resolver, cache)
            val factory = TimelinePresenterFactory(resolver, settings)
            val sessions = HomeTimelineRefreshSessions()
            startKoin {
                modules(
                    module {
                        testSingle { cache }
                        testSingle { app }
                        testSingle { appData }
                        testSingle { settings }
                        testSingle { factory }
                        testSingle { sessions }
                        testSingle<CoroutineScope> { scope }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                        testSingle { LocalFilterRepository(app, scope) }
                        testSingle { MxgaRepository(storage, settings, scope) }
                        testSingle<PlatformRegistry> { testPlatformRegistry() }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }
            val single = spec.source(TimelineSpec.AccountBasedData(MicroBlogKey("home", "example.invalid"))).toSlot()
            val slot =
                if (mixed) {
                    TimelineSlot(
                        id = "mixed",
                        content = TimelineSlotContent.Group(listOf(single), mergePolicy = TimelineMergePolicy.Time),
                    )
                } else {
                    single
                }
            app.accountDao().insert(DbAccount(MicroBlogKey("home", "example.invalid"), "{}", "xQt", 0))
            val missing = unavailable.source(TimelineSpec.AccountBasedData(MicroBlogKey("missing", "example.invalid"))).toSlot()
            if (removeAccount) app.accountDao().insert(DbAccount(MicroBlogKey("missing", "example.invalid"), "{}", "xQt", 0))
            val brokenSlot = broken.source(TimelineSpec.AccountBasedData(MicroBlogKey("home", "example.invalid"))).toSlot()
            val other = unrelated.source(TimelineSpec.AccountBasedData(MicroBlogKey("home", "example.invalid"))).toSlot()
            settings.updateTabSettingsV2 {
                TabSettingsV2(
                    homeSlots =
                        (if (brokenFirst) listOf(brokenSlot) else emptyList()) + (if (missingFirst) listOf(missing) else emptyList()) +
                            (if (shared) listOf(slot) else listOf(other)),
                )
            }
            val tab = resolver.toTabItem(slot)
            val loader = factory.create(tab, true).loader.first() as CacheableRemoteLoader<UiTimelineV2>
            val state = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(20), 0)
            val home = TimelineRemoteMediator(loader, cache, false, retainHistory = true)
            home.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(20)), nextKey = "gap", previousKey = if (prepend) "head" else null)
            home.load(LoadType.REFRESH, state)

            suspend fun rows() = cache.pagingTimelineDao().getTimelinePage(loader.pagingKey, 0, 100).map { it.statusData.content }
            val gap = rows().filterIsInstance<UiTimelineV2.Gap>().single()
            sessions.forTimeline(tab.id, loader.pagingKey).refreshedSources += leaf.pagingKey
            response = PagingResult(listOf(post(30)), nextKey = "new-tail", previousKey = if (prepend) "head" else null)
            headRequests.set(0)
            val models = MutableStateFlow<TimelineState?>(null)
            val ordinary = factory.create(tab)
            scope.launch(Dispatchers.Main) {
                moleculeFlow(RecompositionMode.Immediate) { ordinary.body() }.collect { models.value = it }
            }
            if (removeAccount) {
                withTimeout(10_000) { resolving.await() }
                app.accountDao().delete(MicroBlogKey("missing", "example.invalid"))
            }
            val updated =
                withTimeout(10_000) {
                    models.filterNotNull().first { model ->
                        val items = model.listState as? dev.dimension.flare.common.PagingState.Success
                        items != null && (0 until items.itemCount).any { items.peek(it)?.statusKey?.id == "30" }
                    }
                }
            assertEquals(1, headRequests.get(), "Opening a shared cache must perform exactly one head request")
            assertEquals(null, updated.reading, "Ordinary entry must not acquire UI restoration")
            val result = rows()
            if (remember && shared) {
                assertEquals(listOf("30", "20", "10"), result.filterIsInstance<UiTimelineV2.Post>().map { it.statusKey.id })
                assertEquals(
                    listOf(gap),
                    result.filterIsInstance<UiTimelineV2.Gap>(),
                    "Existing gap and once-per-process session must survive",
                )
            } else {
                assertEquals(listOf("30"), result.map { it.statusKey.id })
            }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            stopKoin()
            cache.close()
            app.close()
            Dispatchers.resetMain()
            main.close()
            deleteTestRootPath(root)
        }
    }

    private fun post(id: Int) =
        UiTimelineV2.Post(
            platformId = "xQt",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText("Post $id".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id.toString(), "example.invalid"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(id.toLong()).toUi(),
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
}
