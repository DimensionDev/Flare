@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)

package dev.dimension.flare.ui.presenter.settings

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAccountPresenterTest {
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
    fun testExportAccount() =
        runTest {
            // Given
            val accountKey1 = MicroBlogKey("user1", "mastodon.social")
            val account1 =
                DbAccount(
                    account_key = accountKey1,
                    credential_json = "{}",
                    platform_type = PlatformType.Mastodon,
                    last_active = 1000L,
                )

            val accountKey2 = MicroBlogKey("user2", "bsky.app")
            val account2 =
                DbAccount(
                    account_key = accountKey2,
                    credential_json = "{\"session\":\"abc\"}",
                    platform_type = PlatformType.Bluesky,
                    last_active = 2000L,
                )

            db.accountDao().insert(listOf(account1, account2))

            val exportKeys = listOf(accountKey1, accountKey2).toImmutableList()
            val presenter = ExportAccountPresenter(exportKeys, coroutineContext)

            var result: String? = null
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect { state ->
                        result = state.data.takeSuccess()
                    }
                }

            advanceUntilIdle()
            job.cancel()

            assertEquals(
                result,
                "1f8b08000000000000006352e692e0622d2d4e2d3214e2cf4d2c2ec94fc9cfd32bce4fce4ccc1162aaae95605078c1aecd2508516324c491549c5da9975850202458ad549c5a5c9c999fa764a5949894ac542bc1a470811f0078af96a051000000",
            )
        }
}
