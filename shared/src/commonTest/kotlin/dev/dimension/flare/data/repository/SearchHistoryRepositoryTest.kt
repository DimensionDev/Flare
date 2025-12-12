package dev.dimension.flare.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.ui.model.UiSearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class SearchHistoryRepositoryTest {

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
    fun `should reflect correct data when add, delete search history`() = runTest(testDispatcher) {
        val keyword = "test-keyword"

        val repo = createRepository()

        repo.addSearchHistory(keyword).join()

        val addedResult = repo.allSearchHistory.first().toImmutableList().first()
        assertEquals(keyword, addedResult.keyword)

        repo.deleteSearchHistory(keyword)
        val deletedResult = repo.allSearchHistory.first().toImmutableList()
        assertEquals(emptyList(), deletedResult)

        repo.addSearchHistory(keyword)
        repo.addSearchHistory(keyword + "2")

        val addedTwiceResult = repo.allSearchHistory.first().toImmutableList()
        assertEquals(2, addedTwiceResult.size)

        repo.deleteAllSearchHistory()
        val deletedAllResult = repo.allSearchHistory.first().toImmutableList()
        assertEquals(emptyList(), deletedAllResult)
    }

    private fun createRepository(): SearchHistoryRepository {
        return SearchHistoryRepository(
            appDatabase,
            testScope
        )
    }
}