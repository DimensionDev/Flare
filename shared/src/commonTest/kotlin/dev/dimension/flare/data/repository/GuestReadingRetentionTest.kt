package dev.dimension.flare.data.repository

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.guestMastodonHomeTimelineTab
import dev.dimension.flare.data.model.tab.readingSourceKey
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.testPlatformRuntimeData
import dev.dimension.flare.unavailableAccountService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestReadingRetentionTest : RobolectricTest() {
    @Test
    fun guestBookmarkSurvivesStartupCleanupUntilReplacedOrDisabled() =
        runTest {
            val root = createTestRootPath()
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            try {
                val reading = TimelineReadingRepository(database, backgroundScope)
                val storage = OkioFileStorage(createTestFileSystem(), root)
                val resolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
                val settings =
                    SettingsRepository(
                        fileStorage = storage,
                        appDataStore = AppDataStore(storage),
                        timelineResolver = resolver,
                        readingRepository = reading,
                    )
                assertTrue(settings.homeTimelineTabs.first().isEmpty())
                reading.enterHome { true }
                val guest = guestMastodonHomeTimelineTab
                val record = assertNotNull(reading.acquire(assertNotNull(reading.open(guest.id, guest.readingSourceKey))))
                val saved = record.copy(anchorId = "saved-post", anchorSortId = 42L, offset = -25.0)
                reading.saveSnapshot(record, saved, remember = true)
                reading.selectTab(guest.id)

                // Startup account cleanup also updates settings when no home slots changed.
                settings.updateTabSettingsV2 { copy(homeSlots = homeSlots) }
                assertEquals(saved, database.timelineReadingSessionDao().snapshot(record.key))
                assertEquals(guest.id, reading.selectedTab.first())

                settings.updateTabSettingsV2 {
                    copy(homeSlots = listOf(resolver.toSlot(guest).copy(id = "configured-home")))
                }
                assertNull(database.timelineReadingSessionDao().get(guest.id))
                assertNull(reading.selectedTab.first())

                settings.updateTabSettingsV2 { copy(homeSlots = emptyList()) }
                assertNotNull(reading.open(guest.id, guest.readingSourceKey))
                reading.selectTab(guest.id)
                reading.enterHome { false }
                assertNull(database.timelineReadingSessionDao().get(guest.id))
                assertNull(database.timelineReadingSessionDao().selectedTab().first())
            } finally {
                database.close()
                deleteTestRootPath(root)
            }
        }
}
