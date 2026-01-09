package dev.dimension.flare.ui.presenter.settings

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.presenter.ImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportAppDatabasePresenterTest {
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setup() {
        val db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        this.db = db

        startKoin {
            modules(
                module {
                    single { db }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun testSuccessfulImportOfValidData() =
        runTest {
            // Given
            val account =
                DbAccount(
                    account_key = MicroBlogKey("user", "example.com"),
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 123456789L,
                )

            val application =
                DbApplication(
                    host = "example.com",
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                )

            val keywordFilter =
                DbKeywordFilter(
                    keyword = "spam",
                    for_timeline = 1,
                    for_notification = 1,
                    for_search = 0,
                    expired_at = 0,
                )

            val searchHistory =
                DbSearchHistory(
                    search = "kotlin",
                    created_at = 123456789L,
                )

            val rssSource =
                DbRssSources(
                    url = "https://example.com/feed",
                    title = "Example Feed",
                    icon = null,
                    lastUpdate = 0,
                )

            val export =
                AppDatabaseExport(
                    accounts = listOf(account),
                    applications = listOf(application),
                    keywordFilters = listOf(keywordFilter),
                    searchHistories = listOf(searchHistory),
                    rssSources = listOf(rssSource),
                )

            val jsonContent = export.encodeJson()

            // When
            val presenter = ImportAppDatabasePresenter(jsonContent)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            finalState.import()
            
            advanceUntilIdle()
            job.cancel()

            // Then
            val accounts = db.accountDao().allAccounts().first()
            assertEquals(1, accounts.size)
            assertEquals(account, accounts.first())

            val applications = db.applicationDao().allApplication().first()
            assertEquals(1, applications.size)
            assertEquals(application, applications.first())

            val keywordFilters = db.keywordFilterDao().selectAll().first()
            assertEquals(1, keywordFilters.size)
            assertEquals(keywordFilter, keywordFilters.first())

            val searchHistories = db.searchHistoryDao().select().first()
            assertEquals(1, searchHistories.size)
            assertEquals(searchHistory, searchHistories.first())

            val rssSources = db.rssSourceDao().getAll().first()
            assertEquals(1, rssSources.size)
            assertEquals(rssSource.url, rssSources.first().url)
        }

    @Test
    fun testHandlingOfMalformedJson() =
        runTest {
            // Given
            val malformedJson = "{invalid json content"

            // When
            val presenter = ImportAppDatabasePresenter(malformedJson)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            
            // Then
            assertFailsWith<Exception> {
                finalState.import()
            }
            
            job.cancel()
        }

    @Test
    fun testHandlingOfInvalidJsonStructure() =
        runTest {
            // Given - valid JSON but wrong structure
            val invalidStructureJson = """{"wrong": "structure", "no": "expected fields"}"""

            // When
            val presenter = ImportAppDatabasePresenter(invalidStructureJson)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            
            // Should not throw - missing fields should default to empty lists
            finalState.import()
            
            advanceUntilIdle()
            job.cancel()

            // Then - verify database is empty (no data imported)
            val accounts = db.accountDao().allAccounts().first()
            assertEquals(0, accounts.size)
        }

    @Test
    fun testHandlingOfDuplicateRecords() =
        runTest {
            // Given - insert initial data
            val account =
                DbAccount(
                    account_key = MicroBlogKey("user", "example.com"),
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 123456789L,
                )
            
            db.accountDao().insert(account)

            val application =
                DbApplication(
                    host = "example.com",
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                )
            
            db.applicationDao().insert(application)

            // When - try to import the same data
            val export =
                AppDatabaseExport(
                    accounts = listOf(account),
                    applications = listOf(application),
                    keywordFilters = emptyList(),
                    searchHistories = emptyList(),
                    rssSources = emptyList(),
                )

            val jsonContent = export.encodeJson()
            val presenter = ImportAppDatabasePresenter(jsonContent)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            
            // Import should handle duplicates (either replace or skip depending on DAO implementation)
            finalState.import()
            
            advanceUntilIdle()
            job.cancel()

            // Then - verify no duplicate entries
            val accounts = db.accountDao().allAccounts().first()
            assertEquals(1, accounts.size)

            val applications = db.applicationDao().allApplication().first()
            assertEquals(1, applications.size)
        }

    @Test
    fun testImportWithEmptyData() =
        runTest {
            // Given
            val export =
                AppDatabaseExport(
                    accounts = emptyList(),
                    applications = emptyList(),
                    keywordFilters = emptyList(),
                    searchHistories = emptyList(),
                    rssSources = emptyList(),
                )

            val jsonContent = export.encodeJson()

            // When
            val presenter = ImportAppDatabasePresenter(jsonContent)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            finalState.import()
            
            advanceUntilIdle()
            job.cancel()

            // Then
            val accounts = db.accountDao().allAccounts().first()
            assertEquals(0, accounts.size)

            val applications = db.applicationDao().allApplication().first()
            assertEquals(0, applications.size)

            val keywordFilters = db.keywordFilterDao().selectAll().first()
            assertEquals(0, keywordFilters.size)

            val searchHistories = db.searchHistoryDao().select().first()
            assertEquals(0, searchHistories.size)

            val rssSources = db.rssSourceDao().getAll().first()
            assertEquals(0, rssSources.size)
        }

    @Test
    fun testImportMultipleRecords() =
        runTest {
            // Given
            val account1 =
                DbAccount(
                    account_key = MicroBlogKey("user1", "example.com"),
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 123456789L,
                )

            val account2 =
                DbAccount(
                    account_key = MicroBlogKey("user2", "example.org"),
                    credential_json = "{}",
                    platform_type = PlatformType.Misskey,
                    last_active = 987654321L,
                )

            val export =
                AppDatabaseExport(
                    accounts = listOf(account1, account2),
                    applications = emptyList(),
                    keywordFilters = emptyList(),
                    searchHistories = emptyList(),
                    rssSources = emptyList(),
                )

            val jsonContent = export.encodeJson()

            // When
            val presenter = ImportAppDatabasePresenter(jsonContent)

            val states = mutableListOf<ImportState>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            
            val finalState = states.last()
            finalState.import()
            
            advanceUntilIdle()
            job.cancel()

            // Then
            val accounts = db.accountDao().allAccounts().first()
            assertEquals(2, accounts.size)
            assertTrue(accounts.contains(account1))
            assertTrue(accounts.contains(account2))
        }
}
