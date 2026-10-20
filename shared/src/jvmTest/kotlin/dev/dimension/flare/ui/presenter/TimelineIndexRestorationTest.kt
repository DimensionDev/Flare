package dev.dimension.flare.ui.presenter

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePageItem
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.HomeTimelinePosition
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
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
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class TimelineIndexRestorationTest {
    @Test fun restoresAnIndexBeyondTheFirstLocalPageWithoutNetwork() = startup(57)

    @Test fun anOutOfRangeIndexFallsBackWithoutNetwork() = startup(999)

    @Test fun launchRefreshWaitsForTheNativeRestore() = startup(57, refresh = true)

    @Test fun fullyFilteredCacheDoesNotLeaveRestorationWaitingForever() = startup(57, hideAll = true)

    @Test fun emptyCacheFallsBackThenLoadsNormally() = startup(57, count = 0)

    private fun startup(
        index: Int,
        count: Int = 80,
        refresh: Boolean = false,
        hideAll: Boolean = false,
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
            val appData = AppDataStore(storage)
            appData.appSettingsStore.updateData {
                it.copy(rememberHomeTimelinePosition = true, refreshHomeTimelineOnLaunch = refresh, mxgaEnabled = false)
            }
            val settings =
                SettingsRepository(storage, appData, TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()), cache)
            val notified = CompletableDeferred<Pair<Thread, Message>>()
            val notifications =
                object : InAppNotification {
                    override fun onProgress(
                        message: Message,
                        progress: Int,
                        total: Int,
                    ) = Unit

                    override fun onSuccess(message: Message) = Unit

                    override fun onError(
                        message: Message,
                        throwable: Throwable,
                    ) {
                        notified.complete(Thread.currentThread() to message)
                    }
                }
            startKoin {
                modules(
                    module {
                        testSingle { cache }
                        testSingle { appData }
                        testSingle { settings }
                        testSingle<CoroutineScope> { scope }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                        testSingle { LocalFilterRepository(app, scope) }
                        testSingle { MxgaRepository(storage, settings, scope) }
                        testSingle<PlatformRegistry> { testPlatformRegistry() }
                        testSingle<InAppNotification> { notifications }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }
            appData.homeTimelinePreferences.updateData {
                it.copy(positions = mapOf("home" to HomeTimelinePosition("startup", TimelineReadingPosition(index, -17.5), 1)))
            }
            cache.connect {
                saveToDatabase(cache, TimelinePagingMapper.toDb((0 until count).map(::post), "startup"))
                cache.pagingTimelineDao().insertPagingKey(DbPagingKey("startup", nextKey = "remote"))
            }
            if (hideAll) LocalFilterRepository(app, scope).add("Post", true, false, false, null, false).join()
            val requests = AtomicInteger()
            val network = CompletableDeferred<Unit>()
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "startup"

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        requests.incrementAndGet()
                        network.complete(Unit)
                        return PagingResult(listOf(post(100)))
                    }
                }
            val presenter = TimelinePresenter(tabId = "home", loader = flowOf(loader), isHomeTimeline = true)
            val models = MutableStateFlow<TimelineState?>(null)
            scope.launch(Dispatchers.Main) {
                moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it }
            }
            if (hideAll || count == 0) {
                withTimeout(10_000) { network.await() }
                val finished =
                    withTimeout(5_000) { models.filterNotNull().first { it.reading != null && it.reading?.restorePosition == null } }
                assertEquals(null, finished.reading?.restorePosition)
            } else {
                val ready =
                    withTimeout(10_000) {
                        models.filterNotNull().first { model ->
                            val items = model.listState as? dev.dimension.flare.common.PagingState.Success
                            val target = model.reading?.restorePosition
                            target != null && items != null && target.index < items.itemCount
                        }
                    }
                val target = checkNotNull(ready.reading?.restorePosition)
                assertEquals(if (index < count) index else 0, target.index)
                assertEquals(if (index < count) -17.5 else 0.0, target.offset)
                assertEquals(0, requests.get(), "Cache prefix loading must not call the network")
                withContext(Dispatchers.Main) { ready.reading?.restorationFinished() }
                withTimeout(5_000) { models.filterNotNull().first { it.reading?.restorePosition == null } }
                if (refresh) withTimeout(5_000) { network.await() }
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
