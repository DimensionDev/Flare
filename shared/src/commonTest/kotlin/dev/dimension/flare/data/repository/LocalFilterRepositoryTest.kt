package dev.dimension.flare.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.ui.model.UiKeywordFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class LocalFilterRepositoryTest {

    private lateinit var appDatabase: AppDatabase
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setUp() {
        appDatabase = Room
            .inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
    }


    @Test
    fun `add keyword filter on add`() = runTest(testDispatcher) {

        val keywordFilter = UiKeywordFilter(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val repo = createRepository()

        repo.add(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val resultKeywordFilter = repo.getAllFlow().first().first()
        assertEquals(keywordFilter, resultKeywordFilter)
    }

    @Test
    fun `update keyword filter on update`() = runTest(testDispatcher) {
        val keywordFilter = UiKeywordFilter(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val repo = createRepository()

        repo.add(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val resultKeywordFilter = repo.getAllFlow().first().first()
        assertEquals(keywordFilter, resultKeywordFilter)

        repo.update(
            keyword = "test",
            forTimeline = false,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val updatedResult = repo.getAllFlow().first().first()
        assertFalse(updatedResult.forTimeline)

    }

    @Test
    fun `delete keyword filter on delete and delete all on clear`() = runTest(testDispatcher) {
        val keywordFilter = UiKeywordFilter(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val repo = createRepository()

        repo.add(
            keyword = "test",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        val resultKeywordFilter = repo.getAllFlow().first().first()
        assertEquals(keywordFilter, resultKeywordFilter)

        repo.delete("test")

        val newResult = repo.getAllFlow().first()
        assertEquals(emptyList(), newResult)

        repo.add(
            keyword = "test-1",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        repo.add(
            keyword = "test-2",
            forTimeline = true,
            forNotification = true,
            forSearch = true,
            expiredAt = null
        )

        repo.clear()

        val clearAllResult = repo.getAllFlow().first()
        assertEquals(emptyList(), clearAllResult)
    }

    private fun createRepository(): LocalFilterRepository {
        return LocalFilterRepository(
            database = appDatabase,
            coroutineScope = testScope
        )
    }


}