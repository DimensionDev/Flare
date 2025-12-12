package dev.dimension.flare.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class AccountRepositoryTest {

    private lateinit var appDatabase: AppDatabase
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var appDataStore: AppDataStore
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    val accountKey = MicroBlogKey("test-id", "test-host.com")
    val credential = UiAccount.Mastodon.Credential(
        instance = "test-host.com",
        accessToken = "test-token",
    )
    val account = UiAccount.Mastodon(
        accountKey = accountKey,
        instance = "test-host.com"
    )

    @BeforeTest
    fun setUp() {

        appDatabase = Room
            .inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()

        cacheDatabase = Room
            .inMemoryDatabaseBuilder<CacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()


        val platformPathProducer = PlatformPathProducer()
        appDataStore = AppDataStore(platformPathProducer)
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
        cacheDatabase.close()
    }



    @Test
    fun `should insert account on add account`() = runTest(testDispatcher) {

        val repo = createRepository()

        repo.addAccount(
            account = account,
            credential = credential
        ).join()

        val  addedAccount = repo.onAdded.first()
        assertEquals(accountKey, addedAccount.accountKey)

    }


    @Test
    fun `should set active account on add account`() = runTest(testDispatcher) {

        val accountKey = MicroBlogKey("test-id", "test-host.com")

        val repo = createRepository()

        repo.addAccount(
            account = account,
            credential = credential
        ).join()

        repo.setActiveAccount(accountKey).join()


        val activeAccount = repo.activeAccount.first()
        assertIs<UiState.Success<UiAccount>>(activeAccount)

        assertEquals(accountKey, activeAccount.data.accountKey)
    }

    @Test
    fun `should delete account on delete account`() = runTest(testDispatcher) {

        val accountKey = MicroBlogKey("test-id", "test-host.com")

        val repo = createRepository()

        repo.addAccount(
            account = account,
            credential = credential
        ).join()

        val currentlyAddedAccount = repo.allAccounts.first()
        assertEquals(1, currentlyAddedAccount.size)

        repo.delete(accountKey).join()

        val recentlyRemovedKey = repo.onRemoved.first()
        assertEquals(accountKey, recentlyRemovedKey)

        val deletedAccount = repo.allAccounts.first()
        assertEquals(0, deletedAccount.size)
    }





    private fun createRepository(): AccountRepository {
        return AccountRepository(
            appDatabase = appDatabase,
            coroutineScope = testScope,
            appDataStore = appDataStore,
            cacheDatabase = cacheDatabase
        )
    }

}