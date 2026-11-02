package dev.dimension.flare.ui.presenter

import androidx.room3.Room
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.ForwardingFileSystem
import okio.Path
import okio.Source
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeSelectionTest {
    @Test
    fun onlyDisablingReadingClearsTheSavedTab() =
        runBlocking {
            val root = createTestRootPath()
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val cache =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            try {
                val data = AppDataStore(storage)
                val settings =
                    SettingsRepository(
                        storage,
                        data,
                        TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()),
                        cache,
                    )
                data.homeTimelinePreferences.updateData { it.copy(selectedTabId = "saved-tab") }
                settings.updateAppSettings { copy(rememberHomeTimelinePosition = true) }
                assertEquals(
                    "saved-tab",
                    data.homeTimelinePreferences.data
                        .first()
                        .selectedTabId,
                )
                settings.updateAppSettings { copy(rememberHomeTimelinePosition = false) }
                assertNull(
                    data.homeTimelinePreferences.data
                        .first()
                        .selectedTabId,
                )
            } finally {
                cache.close()
                deleteTestRootPath(root)
            }
        }

    @Test
    fun unreadableOptionalPreferencesDoNotBlockHome() =
        runBlocking {
            val outcomes = mutableListOf<Boolean>()
            for (enabled in listOf(false, true)) {
                val root = createTestRootPath()
                var accesses = 0
                val fs =
                    object : ForwardingFileSystem(createTestFileSystem()) {
                        override fun source(file: Path): Source {
                            if (file.name == "home_timeline_preferences.pb") {
                                accesses++
                                throw IOException("unreadable preferences")
                            }
                            return super.source(file)
                        }
                    }
                val storage = OkioFileStorage(fs, root)
                val appDb =
                    Room
                        .memoryDatabaseBuilder<AppDatabase>()
                        .setDriver(
                            createDatabaseDriver(),
                        ).setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                val cacheDb =
                    Room
                        .memoryDatabaseBuilder<CacheDatabase>()
                        .setDriver(
                            createDatabaseDriver(),
                        ).setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                try {
                    val appData = AppDataStore(storage)
                    appData.appSettingsStore.updateData { it.copy(rememberHomeTimelinePosition = enabled) }
                    val resolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
                    val settings = SettingsRepository(storage, appData, resolver, cacheDb)
                    val accounts =
                        AccountRepository(
                            appDb,
                            appScope,
                            appData,
                            cacheDb,
                            dev.dimension.flare.data.repository
                                .ProxyRepository(storage),
                            testPlatformRegistry(),
                            dev.dimension.flare.data.repository.DraftRepository(
                                appDb,
                                dev.dimension.flare.data.repository
                                    .DraftMediaStore(
                                        dev.dimension.flare.data.io
                                            .InMemoryFileStorage(),
                                    ),
                            ),
                        )
                    // Existing prerequisites have finished; only the added preferences barrier remains cold.
                    settings.homeTimelineTabs.first()
                    accounts.allAccounts.first()
                    startKoin {
                        modules(
                            module {
                                testSingle<AppDataStore> { appData }
                                testSingle<SettingsRepository> { settings }
                                testSingle<AccountRepository> { accounts }
                            },
                        )
                    }
                    val outcome =
                        runCatching {
                            withTimeoutOrNull(
                                5_000,
                            ) { HomeTimelineWithTabsPresenter().moleculeFlow.first { it.tabState is UiState.Success } }
                        }
                    val ready = outcome.getOrNull()
                    outcomes += ready != null
                    if (!enabled) assertEquals(0, accesses, "Disabled reading must not open the preferences file")
                    settings.updateAppSettings { copy(rememberHomeTimelinePosition = false) }
                    assertEquals(
                        false,
                        appData.appSettingsStore.data
                            .first()
                            .rememberHomeTimelinePosition,
                    )
                } finally {
                    stopKoin()
                    appScope.cancel()
                    appDb.close()
                    cacheDb.close()
                    deleteTestRootPath(root)
                }
            }
            assertTrue(outcomes.all { it }, "Disabled reading must not block Home on a failed preferences read")
        }
}
