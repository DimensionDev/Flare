package dev.dimension.flare.ui.presenter

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import androidx.room3.Room
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePageItem
import dev.dimension.flare.data.datasource.microblog.paging.TimelineRemoteMediator
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.LoginExpiredException
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
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
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
import kotlinx.coroutines.flow.flowOf
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
class TimelineGapCompletionTest {
    @Test
    fun workerPresenterDeliversSuccessOnMainAndIgnoresConcurrentActivation() = completion(fail = false)

    @Test
    fun workerPresenterDeliversFailureOnMainAndReportsExpiredLogin() = completion(fail = true)

    @Test
    fun cancelingThePresenterReleasesThePendingButtonOnMain() = completion(fail = false, canceled = true)

    private fun completion(
        fail: Boolean,
        canceled: Boolean = false,
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
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val appData = AppDataStore(storage)
            appData.appSettingsStore.updateData {
                it.copy(rememberHomeTimelinePosition = true, refreshHomeTimelineOnLaunch = false, mxgaEnabled = false)
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
            var response = PagingResult<UiTimelineV2>(listOf(post(10)))
            var filling = false
            val requests = AtomicInteger()
            val loader =
                object : CacheableRemoteLoader<UiTimelineV2> {
                    override val pagingKey = "completion"

                    override suspend fun load(
                        pageSize: Int,
                        request: PagingRequest,
                    ): PagingResult<UiTimelineV2> {
                        if (filling) {
                            requests.incrementAndGet()
                            entered.complete(Unit)
                            release.await()
                            if (fail) throw LoginExpiredException(MicroBlogKey("me", "example.invalid"), "xQt")
                            return PagingResult(listOf(post(2)))
                        }
                        return response
                    }
                }
            val seed = TimelineRemoteMediator(loader, cache, false, rememberPosition = true)
            val state = PagingState<OffsetFromStartPagingKey, TimelinePageItem>(emptyList(), null, PagingConfig(20), 0)
            seed.load(LoadType.REFRESH, state)
            response = PagingResult(listOf(post(1)), nextKey = "gap")
            seed.load(LoadType.REFRESH, state)
            val presenter = TimelinePresenter(tabId = "home", loader = flowOf(loader), isHomeTimeline = true)
            val gap =
                withTimeout(10_000) {
                    presenter
                        .createPager(scope)
                        .asSnapshot()
                        .filterIsInstance<UiTimelineV2.Gap>()
                        .single()
                }
            filling = true
            val uiThread = withContext(Dispatchers.Main) { Thread.currentThread() }
            val callback = CompletableDeferred<Pair<Thread, List<String>>>()
            val duplicate = CompletableDeferred<Unit>()
            withContext(Dispatchers.IO) {
                gap.onActivate!!.invoke { keys -> callback.complete(Thread.currentThread() to keys) }
            }
            withTimeout(5_000) { entered.await() }
            withContext(Dispatchers.Main) { gap.onActivate!!.invoke { duplicate.complete(Unit) } }
            if (canceled) scope.cancel() else release.complete(Unit)
            val delivered = withTimeout(5_000) { callback.await() }
            assertSame(uiThread, delivered.first)
            assertEquals(fail || canceled, delivered.second.isEmpty())
            assertEquals(1, requests.get())
            assertTrue(!duplicate.isCompleted)
            if (fail) {
                val notification = withTimeout(5_000) { notified.await() }
                assertSame(uiThread, notification.first)
                assertEquals(Message.LoginExpired, notification.second)
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
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
