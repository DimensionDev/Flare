package dev.dimension.flare.ui.presenter.home

import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingKey
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.guestMastodonHomeTimelineTab
import dev.dimension.flare.data.model.tab.readingSources
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.TimelineReadingRepository
import dev.dimension.flare.data.translation.NoopPreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.unavailableAccountService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimelinePresenterLifecycleTest : RobolectricTest() {
    @Test
    fun readingObserversEndWhenTheModeOrPagerCollectionChanges() =
        runPresenterTest { database, repository, sourceKey ->
            val scope = this
            val presenter = TimelinePresenter("home", flowOf(Loader()), isHomeTimeline = true, readingSourceKey = sourceKey)
            val firstKey = MutableStateFlow<String?>(null)
            val paging =
                object : PagingDataPresenter<UiTimelineV2>(coroutineContext) {
                    override suspend fun presentPagingDataEvent(event: PagingDataEvent<UiTimelineV2>) {
                        firstKey.value = snapshot().items.firstOrNull()?.itemKey
                    }
                }

            suspend fun awaitKey(predicate: (String) -> Boolean): String =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) { firstKey.filterNotNull().first(predicate) }
                }
            val collection = scope.launch { presenter.createPager(scope).collectLatest(paging::collectFrom) }
            var previousKey = awaitKey { it.startsWith("reading/") }
            val dao = database.timelineReadingSessionDao()
            assertEquals(1, dao.snapshots(assertNotNull(dao.get("home")).key).size)
            repeat(2) {
                repository.enterHome { false }
                awaitKey { it.startsWith("ordinary_") }
                assertNull(dao.get("home"))
                repository.enterHome { true }
                val displayedKey = awaitKey { it.startsWith("reading/") && it != previousKey }
                val snapshots = dao.snapshots(assertNotNull(dao.get("home")).key)
                assertEquals(1, snapshots.size, "Only the current reading branch may retain a snapshot")
                assertTrue(displayedKey.startsWith(snapshots.single().activeKey))
                previousKey = displayedKey
            }
            val record = assertNotNull(dao.get("home"))
            val retainedKey = dao.snapshots(record.key).single().key
            collection.cancelAndJoin()
            // The owner scope stays alive: ending collection must release its reader independently.
            val reopened = assertNotNull(repository.acquire(record))
            assertEquals(retainedKey, reopened.key, "A released snapshot must be reused without cloning another reader")
            repository.release(reopened)
        }

    @Test
    fun startupFailureOnAnInitializedEmptyTimelineCanBeRefreshed() = refreshFailureCanRecover(initialized = true)

    @Test
    fun firstLoadFailureCanBeRetriedThroughPaging() = refreshFailureCanRecover(initialized = false)

    private fun refreshFailureCanRecover(initialized: Boolean) =
        runPresenterTest { database, repository, sourceKey ->
            val record = assertNotNull(repository.open("home", sourceKey))
            val seed = assertNotNull(repository.acquire(record))
            if (initialized) database.pagingTimelineDao().insertPagingKey(DbPagingKey(seed.activeKey))
            repository.release(seed)
            val loader =
                Loader().apply {
                    beforeLoad = { if (requests.value == 1) error("offline") }
                }
            val presenter = TimelinePresenter("home", flowOf(loader), isHomeTimeline = true, readingSourceKey = sourceKey)
            val states = MutableStateFlow<TimelineState?>(null)
            launch {
                moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { states.value = it }
            }

            suspend fun awaitState(predicate: (TimelineState) -> Boolean): TimelineState =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) { states.filterNotNull().first(predicate) }
                }
            val failed =
                awaitState {
                    loader.requests.value == 1 && it.readingState?.isRefreshing == false &&
                        (it.listState is PagingState.Empty || it.listState is PagingState.Error)
                }
            assertEquals(seed, database.timelineReadingSessionDao().snapshot(seed.key))
            assertNull(failed.readingState?.position)
            if (initialized) {
                assertTrue(failed.listState is PagingState.Empty, "Startup refresh must leave the initialized empty pager alive")
                failed.refresh()
            } else {
                assertTrue(failed.listState is PagingState.Error)
                (failed.listState as PagingState.Error).onRetry()
            }
            val recovered = awaitState { it.listState is PagingState.Success }
            assertEquals(2, loader.requests.value)
            assertNull(recovered.readingState?.position, "Recovery must not request a scroll")
            assertEquals(1, database.timelineReadingSessionDao().snapshots(record.key).size)
        }

    private fun runPresenterTest(test: suspend CoroutineScope.(CacheDatabase, TimelineReadingRepository, String) -> Unit) =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val root = createTestRootPath()
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val appDatabase =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val lifetime = SupervisorJob(coroutineContext[Job])
            val scope = CoroutineScope(coroutineContext + lifetime)
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val store = AppDataStore(storage)
            val repository = TimelineReadingRepository(database, scope)
            val resolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
            val settings = SettingsRepository(storage, store, resolver, repository)
            settings.updateTabSettingsV2 {
                copy(homeSlots = listOf(resolver.toSlot(guestMastodonHomeTimelineTab).copy(id = "home")))
            }
            val sourceKey =
                settings.homeTimelineTabs
                    .first()
                    .readingSources()
                    .getValue("home")
            startKoin {
                modules(
                    module {
                        testSingle { database }
                        testSingle { store }
                        testSingle { settings }
                        testSingle { repository }
                        testSingle<PreTranslationService> { NoopPreTranslationService }
                        testSingle { LocalFilterRepository(appDatabase, scope) }
                        testSingle { MxgaRepository(storage, settings, scope) }
                    },
                )
            }
            try {
                repository.enterHome { true }
                scope.test(database, repository, sourceKey)
            } finally {
                lifetime.cancelAndJoin()
                Dispatchers.resetMain()
                stopKoin()
                database.close()
                appDatabase.close()
                deleteTestRootPath(root)
            }
        }

    private class Loader : CacheableRemoteLoader<UiTimelineV2> {
        override val pagingKey = "ordinary"
        val requests = MutableStateFlow(0)
        var beforeLoad: suspend () -> Unit = {}

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            requests.value += 1
            beforeLoad()
            return PagingResult<UiTimelineV2>(
                data =
                    listOf(
                        UiTimelineV2.Post(
                            platformId = "xQt",
                            images = persistentListOf(),
                            sensitive = false,
                            contentWarning = null,
                            user = null,
                            content = UiTranslatableText("post".toUiPlainText()),
                            actions = persistentListOf(),
                            poll = null,
                            statusKey = MicroBlogKey("post", "x.com"),
                            card = null,
                            createdAt = Instant.fromEpochMilliseconds(1).toUi(),
                            references = persistentListOf(),
                            clickEvent = ClickEvent.Noop,
                            accountType = AccountType.Guest,
                        ),
                    ),
                endOfPaginationReached = true,
            )
        }
    }
}
