package dev.dimension.flare.data.repository

import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountRepositoryTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    private lateinit var appDatabase: AppDatabase
    private lateinit var cacheDatabase: CacheDatabase
    private lateinit var appDataStore: AppDataStore

    private val accountKey = MicroBlogKey(id = "alice", host = "example.social")

    @BeforeTest
    fun setup() {
        appDatabase =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        cacheDatabase =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        appDataStore = AppDataStore(fileStorage)
    }

    @AfterTest
    fun tearDown() {
        appDatabase.close()
        cacheDatabase.close()
        deleteTestRootPath(root)
    }

    @Test
    fun allAccountsDoesNotReEmitWhenActiveTimestampChanges() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val emissions = Channel<List<UiAccount>>(Channel.UNLIMITED)
            val job =
                launch {
                    repository.allAccounts.collect {
                        emissions.send(it.toList())
                    }
                }

            try {
                val initial = emissions.receive()
                assertEquals(listOf(accountKey), initial.map { it.accountKey })

                repository.setActiveAccount(accountKey).join()

                val duplicate = withTimeoutOrNull(100) { emissions.receive() }
                assertNull(
                    duplicate,
                    "allAccounts emitted again even though account keys and sort order did not change",
                )
            } finally {
                job.cancel()
            }
        }

    @Test
    fun accountProfileFetchIsNotRepeatedWhenAccountTableUpdateDoesNotChangeAccountList() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val profileCache = MutableStateFlow<String?>(null)
            val fetches = Channel<Unit>(Channel.UNLIMITED)
            var fetchCount = 0

            fun profileFlow(account: UiAccount) =
                Cacheable(
                    fetchSource = {
                        fetchCount += 1
                        fetches.send(Unit)
                        profileCache.value = account.accountKey.id
                    },
                    cacheSource = {
                        profileCache.filterNotNull()
                    },
                ).toUi()

            val accountProfiles =
                repository
                    .allAccounts
                    .map { accounts ->
                        accounts.map(::profileFlow)
                    }.combineLatestFlowLists()

            val job =
                launch {
                    accountProfiles.collect()
                }

            try {
                fetches.receive()
                assertEquals(1, fetchCount)

                repository.setActiveAccount(accountKey).join()

                val duplicateFetch = withTimeoutOrNull(100) { fetches.receive() }
                assertNull(
                    duplicateFetch,
                    "Profile fetch was triggered again after an account table update that did not change the account list",
                )
                assertEquals(1, fetchCount)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun accountProfileFetchIsNotRestartedWhileCacheIsEmptyWhenAccountListReEmits() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val emptyProfileCache = MutableStateFlow<String?>(null)
            val fetches = Channel<Unit>(Channel.UNLIMITED)
            var fetchCount = 0

            fun loadingProfileFlow(account: UiAccount) =
                Cacheable(
                    fetchSource = {
                        fetchCount += 1
                        fetches.send(Unit)
                        awaitCancellation()
                    },
                    cacheSource = {
                        emptyProfileCache.filterNotNull()
                    },
                ).toUi()

            val accountProfiles =
                repository
                    .allAccounts
                    .map { accounts ->
                        accounts.map(::loadingProfileFlow)
                    }.combineLatestFlowLists()

            val job =
                launch {
                    accountProfiles.collect()
                }

            try {
                fetches.receive()
                assertEquals(1, fetchCount)

                repository.setActiveAccount(accountKey).join()

                val restartedFetch = withTimeoutOrNull(100) { fetches.receive() }
                assertNull(
                    restartedFetch,
                    "Profile fetch was restarted while the profile cache was empty and the account list did not change",
                )
                assertEquals(1, fetchCount)
            } finally {
                job.cancel()
            }
        }

    private fun createRepository(scope: CoroutineScope): AccountRepository =
        AccountRepository(
            appDatabase = appDatabase,
            coroutineScope = scope,
            appDataStore = appDataStore,
            cacheDatabase = cacheDatabase,
            platformRegistry = testPlatformRegistry(),
        )

    private suspend fun insertAccount() {
        appDatabase.accountDao().insert(
            DbAccount(
                account_key = accountKey,
                credential_json = "{}",
                platform_type = PlatformType.Mastodon,
                last_active = 1L,
                sort_id = 0L,
            ),
        )
    }
}
