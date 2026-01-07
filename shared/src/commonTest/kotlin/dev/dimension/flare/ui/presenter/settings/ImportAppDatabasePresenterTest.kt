package dev.dimension.flare.ui.presenter.settings

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.data.database.app.model.DbKeywordFilter
import dev.dimension.flare.data.database.app.model.DbRssSources
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportAppDatabasePresenterTest {
    private lateinit var db: AppDatabase
    private val json = Json { prettyPrint = true }

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
    fun testImport() =
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

            val exportData =
                AppDatabaseExport(
                    accounts = listOf(account),
                    applications = listOf(application),
                    keywordFilters = listOf(keywordFilter),
                    searchHistories = listOf(searchHistory),
                    rssSources = listOf(rssSource),
                )
            val jsonString = json.encodeToString(exportData)

            val presenter = ImportAppDatabasePresenter(jsonString)

            val states = mutableListOf<UiState<Unit>>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            job.cancel()

            val finalState = states.last()
            assertTrue(finalState.isSuccess)

            // Verify DB contents
            assertEquals(
                account,
                db
                    .accountDao()
                    .allAccounts()
                    .first()
                    .first(),
            )
            assertEquals(
                application,
                db
                    .applicationDao()
                    .allApplication()
                    .first()
                    .first(),
            )
            assertEquals(
                keywordFilter,
                db
                    .keywordFilterDao()
                    .selectAll()
                    .first()
                    .first(),
            )
            assertEquals(
                searchHistory,
                db
                    .searchHistoryDao()
                    .select()
                    .first()
                    .first(),
            )
            // Note: RssSource id is auto-generated, so we compare other fields or ignore id logic if strictly checking object equality might fail on ID.
            // But here we are comparing the object we pulled. Let's check URL as it is unique.
            val fetchedRss =
                db
                    .rssSourceDao()
                    .getAll()
                    .first()
                    .first()
            assertEquals(rssSource.url, fetchedRss.url)
            assertEquals(rssSource.title, fetchedRss.title)
        }

    @Test
    fun testImportOverwrite() =
        runTest {
            // Given existing data
            val oldAccount =
                DbAccount(
                    account_key = MicroBlogKey("user", "example.com"),
                    credential_json = "{old}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 0L,
                )
            db.accountDao().insert(oldAccount)

            // New data to overwrite
            val newAccount =
                DbAccount(
                    account_key = MicroBlogKey("user", "example.com"),
                    credential_json = "{new}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 123456789L,
                )

            val exportData = AppDatabaseExport(accounts = listOf(newAccount))
            val jsonString = json.encodeToString(exportData)

            val presenter = ImportAppDatabasePresenter(jsonString)

            val states = mutableListOf<UiState<Unit>>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            job.cancel()

            val finalState = states.last()
            assertTrue(finalState.isSuccess)

            val fetchedAccounts = db.accountDao().allAccounts().first()
            assertEquals(1, fetchedAccounts.size)
            val fetchedAccount = fetchedAccounts.first()
            assertEquals(newAccount.credential_json, fetchedAccount.credential_json)
            assertEquals(newAccount.last_active, fetchedAccount.last_active)
        }
}
