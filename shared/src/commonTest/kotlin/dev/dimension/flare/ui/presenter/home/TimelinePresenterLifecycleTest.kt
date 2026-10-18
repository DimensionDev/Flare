package dev.dimension.flare.ui.presenter.home

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.TimelineHistoryStore
import dev.dimension.flare.data.datastore.AppDataStore
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
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertTrue

class TimelinePresenterLifecycleTest : RobolectricTest() {
    @Test
    fun disablingBeforeSettingsPublishDoesNotEndThePresenterSubscription() =
        runTest {
            val root = createTestRootPath()
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val cache =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val app =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val store = TimelineHistoryStore(cache)
            val dataStore = AppDataStore(storage)
            val settings =
                SettingsRepository(
                    storage,
                    dataStore,
                    TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()),
                    store,
                )
            settings.updateAppSettings { copy(rememberHomeTimelinePosition = true, mxgaEnabled = false) }
            val filters = LocalFilterRepository(app, backgroundScope)
            val mxga = MxgaRepository(storage, settings, backgroundScope)
            startKoin {
                modules(
                    module {
                        testSingle { cache }
                        testSingle { dataStore }
                        testSingle { settings }
                        testSingle { store }
                        testSingle { filters }
                        testSingle { mxga }
                        testSingle { testPlatformRegistry() }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                    },
                )
            }
            try {
                val source =
                    object : CacheableRemoteLoader<UiTimelineV2> {
                        override val pagingKey = "lifecycle"

                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> = PagingResult()
                    }
                val emissions = Channel<Unit>(Channel.UNLIMITED)
                val job =
                    backgroundScope.launch(Dispatchers.Default) {
                        TimelinePresenter("tab", flowOf(source), isHomeTimeline = true)
                            .createPager(backgroundScope)
                            .collect { emissions.send(Unit) }
                    }

                suspend fun awaitPage() = withContext(Dispatchers.Default) { withTimeout(10_000) { emissions.receive() } }
                awaitPage()
                // Reproduce the real update's ordering, allowing the invalidation to
                // reach the presenter before DataStore publishes remember=false.
                store.setEnabled(false)
                withContext(Dispatchers.Default) { kotlinx.coroutines.delay(100) }
                assertTrue(job.isActive, "Disabling must not terminate the settings subscription")
                settings.updateAppSettings { copy(rememberHomeTimelinePosition = false) }
                awaitPage()
                while (emissions.tryReceive().isSuccess) { /* Drain the disabled generation. */ }
                settings.updateAppSettings { copy(rememberHomeTimelinePosition = true) }
                awaitPage()
                assertTrue(job.isActive)
                job.cancel()
            } finally {
                stopKoin()
                cache.close()
                app.close()
                deleteTestRootPath(root)
            }
        }
}
