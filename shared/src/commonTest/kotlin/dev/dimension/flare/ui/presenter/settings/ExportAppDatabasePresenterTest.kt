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
import dev.dimension.flare.ui.presenter.ExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAppDatabasePresenterTest {
    private lateinit var db: AppDatabase
    private val json = Json { ignoreUnknownKeys = true }

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
    fun testExport() =
        runTest {
            // Given
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

            val keywordFilter =
                DbKeywordFilter(
                    keyword = "spam",
                    for_timeline = 1,
                    for_notification = 1,
                    for_search = 0,
                    expired_at = 0,
                )
            db.keywordFilterDao().insert(keywordFilter)

            val searchHistory =
                DbSearchHistory(
                    search = "kotlin",
                    created_at = 123456789L,
                )
            db.searchHistoryDao().insert(searchHistory)

            val rssSource =
                DbRssSources(
                    url = "https://example.com/feed",
                    title = "Example Feed",
                    icon = null,
                    lastUpdate = 0,
                )
            db.rssSourceDao().insert(rssSource)

            val presenter = ExportAppDatabasePresenter()

            val states = mutableListOf<ExportState>()
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
            val jsonString = finalState.export()

            val export = json.decodeFromString<AppDatabaseExport>(jsonString)

            assertEquals(1, export.accounts.size)
            val exportedAccount = export.accounts.first()
            assertEquals(account.account_key, exportedAccount.account_key)
            assertEquals(account.platform_type, exportedAccount.platform_type)
            assertEquals(account.last_active, exportedAccount.last_active)
            // credential_json should not be exported for security reasons

            assertEquals(1, export.applications.size)
            val exportedApplication = export.applications.first()
            assertEquals(application.host, exportedApplication.host)
            assertEquals(application.platform_type, exportedApplication.platform_type)
            assertEquals(application.has_pending_oauth_request, exportedApplication.has_pending_oauth_request)
            // credential_json should not be exported for security reasons

            assertEquals(1, export.keywordFilters.size)
            assertEquals(keywordFilter, export.keywordFilters.first())

            assertEquals(1, export.searchHistories.size)
            assertEquals(searchHistory, export.searchHistories.first())

            assertEquals(1, export.rssSources.size)
            assertEquals(rssSource.url, export.rssSources.first().url)
        }
}
