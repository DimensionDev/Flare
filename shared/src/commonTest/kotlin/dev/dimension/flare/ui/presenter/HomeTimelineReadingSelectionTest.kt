package dev.dimension.flare.ui.presenter

import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.guestMastodonHomeTimelineTab
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.TimelineReadingRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeTimelineReadingSelectionTest : RobolectricTest() {
    @Test
    fun enablingWhileTheSelectedHomeTabRemainsStationaryPersistsThatTab() = checkSelection(initiallyEnabled = false)

    @Test
    fun coldEntryKeepsThePreviouslySavedTab() = checkSelection(initiallyEnabled = true)

    private fun checkSelection(initiallyEnabled: Boolean) =
        runTest {
            val root = createTestRootPath()
            val db =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val appDb =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val fileStorage = OkioFileStorage(createTestFileSystem(), root)
            val store = AppDataStore(fileStorage)
            val reading = TimelineReadingRepository(db, backgroundScope)
            val resolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
            val settings = SettingsRepository(fileStorage, store, resolver, reading)
            val accounts = AccountRepository(appDb, backgroundScope, store, db, testPlatformRegistry())
            settings.updateTabSettingsV2 {
                copy(homeSlots = listOf("first", "second").map { resolver.toSlot(guestMastodonHomeTimelineTab).copy(id = it) })
            }
            if (initiallyEnabled) {
                settings.updateAppSettings { copy(rememberHomeTimelineReadingPosition = true) }
                reading.enterHome { true }
                reading.selectTab("second")
            }
            startKoin {
                modules(
                    module {
                        testSingle { settings }
                        testSingle { accounts }
                        testSingle { reading }
                    },
                )
            }
            var job: kotlinx.coroutines.Job? = null
            try {
                val presenter = HomeTimelineWithTabsPresenter()
                var state: HomeTimelineWithTabsPresenter.State? = null
                job = launch { moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { state = it } }
                advanceUntilIdle()
                assertTrue(assertNotNull(state).selectionLoaded)
                if (!initiallyEnabled) {
                    assertEquals(false, reading.enabled.value)
                    // Existing native and Android wrappers call this on selected-ID changes only.
                    state.selectTab("second")
                    advanceUntilIdle()
                    assertEquals(null, db.timelineReadingSessionDao().selectedTab().first())
                    settings.updateAppSettings { copy(rememberHomeTimelineReadingPosition = true) }
                    // The Home entry path applies the new setting without changing the selected ID.
                    reading.enterHome { settings.appSettings.first().rememberHomeTimelineReadingPosition }
                    advanceUntilIdle()
                    assertTrue(assertNotNull(state).selectionLoaded)
                    assertEquals(true, reading.enabled.value)
                    assertEquals("second", state.selectedTabId)
                }
                assertEquals("second", state.selectedTabId)
                settings.updateAppSettings { copy(rememberHomeTimelineReadingPosition = false) }
                reading.enterHome { false }
                advanceUntilIdle()
                state.selectTab("first")
                advanceUntilIdle()
                settings.updateAppSettings { copy(rememberHomeTimelineReadingPosition = true) }
                reading.enterHome { true }
                advanceUntilIdle()
                assertEquals("first", state.selectedTabId)
            } finally {
                job?.cancel()
                stopKoin()
                db.close()
                appDb.close()
                deleteTestRootPath(root)
            }
        }
}
