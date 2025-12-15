@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)

package dev.dimension.flare.ui.presenter.settings

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.takeSuccess
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportAccountPresenterTest {
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
    fun testImportAccount() =
        runTest {
            // Given
            val data =
                "1f8b08000000000000006352e692e0622d2d4e2d3214e2cf4d2c2ec94fc9" +
                    "cfd32bce4fce4ccc1162aaae95605078c1aecd2508516324c491549c5da997585" +
                    "0202458ad549c5a5c9c999fa764a5949894ac542bc1a470811f0078af96a051000000"
            val presenter = ImportAccountPresenter(data, coroutineContext)

            var result: List<MicroBlogKey>? = null
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect { state ->
                        result = state.importedAccount.takeSuccess()
                    }
                }

            advanceUntilIdle()
            job.cancel()

            // Assert
            val expectedKeys =
                listOf(
                    MicroBlogKey("user1", "mastodon.social"),
                    MicroBlogKey("user2", "bsky.app"),
                )

            assertEquals(expectedKeys, result)

            // Verify DB insertion
            val storedAccounts = db.accountDao().allAccounts().first()
            assertEquals(2, storedAccounts.size)
            assertTrue(storedAccounts.any { it.account_key == expectedKeys[0] })
            assertTrue(storedAccounts.any { it.account_key == expectedKeys[1] })
        }
}
