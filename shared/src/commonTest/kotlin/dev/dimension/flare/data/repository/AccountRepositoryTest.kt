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
import dev.dimension.flare.model.UnsupportedPlatformException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

                repository.setActiveAccount(accountKey)

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

                repository.setActiveAccount(accountKey)

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

                repository.setActiveAccount(accountKey)

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

    @Test
    fun unavailableAccountCanBeDisplayedExportedAndDeletedWithoutDatasource() =
        runTest {
            val repository = createRepository(this)
            val unavailableKey = MicroBlogKey(id = "alice", host = "testnet.example")
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = unavailableKey,
                    credential_json = "{\"token\":\"kept\"}",
                    platformId = "TestNet",
                    last_active = 10L,
                    sort_id = 3L,
                ),
            )

            val account = repository.allAccounts.first().single()
            assertEquals("TestNet", account.platformDisplayName)
            assertEquals(dev.dimension.flare.ui.model.UiIcon.World, account.platformIcon)
            assertFalse(account.platformAvailable)
            assertFailsWith<UnsupportedPlatformException> {
                repository.getOrCreateDataSource(account)
            }
            val activationError =
                assertFailsWith<UnsupportedPlatformException> {
                    repository.setActiveAccount(unavailableKey)
                }
            assertEquals("TestNet", activationError.platformId)
            assertEquals("{\"token\":\"kept\"}", appDatabase.accountDao().getAccount(unavailableKey)?.credential_json)

            repository.delete(unavailableKey).join()
            assertNull(appDatabase.accountDao().getAccount(unavailableKey))
        }

    @Test
    fun activeAccountSkipsNewerUnavailableAccounts() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = MicroBlogKey("future", "testnet.example"),
                    credential_json = "{}",
                    platformId = "TestNet",
                    last_active = 100L,
                    sort_id = 1L,
                ),
            )

            val state = repository.activeAccount.first()
            assertEquals(accountKey, assertIs<dev.dimension.flare.ui.model.UiState.Success<UiAccount>>(state).data.accountKey)
        }

    @Test
    fun allUnavailableAccountsHaveExplicitState() =
        runTest {
            val repository = createRepository(this)
            appDatabase.accountDao().insert(
                DbAccount(
                    account_key = MicroBlogKey("future", "testnet.example"),
                    credential_json = "{}",
                    platformId = "TestNet",
                    last_active = 100L,
                ),
            )

            val state = repository.activeAccount.first()
            val error = assertIs<dev.dimension.flare.ui.model.UiState.Error<UiAccount>>(state).throwable
            assertEquals(listOf("TestNet"), assertIs<NoAvailableAccountException>(error).platformIds)
        }

    @Test
    fun reloginDoesNotPublishANewAccountEvent() =
        runTest {
            val repository = createRepository(this)
            val events = Channel<AccountChange>(Channel.UNLIMITED)
            val collection = launch { repository.accountChanges.collect(events::send) }
            val alice = UiAccount(accountKey, "Mastodon")
            val bob = UiAccount(MicroBlogKey("bob", "example.social"), "Mastodon")

            try {
                repository.addAccount(alice, "first").join()
                assertEquals(alice.accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)
                repository.addAccount(bob, "second").join()
                assertEquals(bob.accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)

                repository.addAccount(alice.copy(platformId = "Pixelfed"), "refreshed").join()

                assertNull(withTimeoutOrNull(100) { events.receive() })
                val stored = appDatabase.accountDao().getAccount(accountKey)
                assertEquals("Pixelfed", stored?.platformId)
                assertEquals("\"refreshed\"", stored?.credential_json)
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun deletingAndReaddingTheSameAccountPublishesBothEventsAgain() =
        runTest {
            val repository = createRepository(this)
            val events = Channel<AccountChange>(Channel.UNLIMITED)
            val collection = launch { repository.accountChanges.collect(events::send) }
            val account = UiAccount(accountKey, "Mastodon")

            try {
                repository.addAccount(account, "first").join()
                assertEquals(accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)

                repository.delete(accountKey).join()
                assertEquals(accountKey, assertIs<AccountChange.Removed>(events.receive()).accountKey)

                repository.addAccount(account, "second").join()
                assertEquals(accountKey, assertIs<AccountChange.Added>(events.receive()).account.accountKey)
            } finally {
                collection.cancel()
            }
        }

    @Test
    fun accountChangesAreBufferedAndKeepMutationOrder() =
        runTest {
            val repository = createRepository(this)
            val alice = UiAccount(accountKey, "Mastodon")
            val bob = UiAccount(MicroBlogKey("bob", "example.social"), "Mastodon")

            repository.addAccount(alice, "first").join()
            repository.addAccount(bob, "second").join()
            repository.delete(accountKey).join()
            repository.addAccount(alice, "third").join()

            val changes = repository.accountChanges.take(4).toList()
            assertEquals(
                listOf(
                    "added:${alice.accountKey}",
                    "added:${bob.accountKey}",
                    "removed:${alice.accountKey}",
                    "added:${alice.accountKey}",
                ),
                changes.map { change ->
                    when (change) {
                        is AccountChange.Added -> "added:${change.account.accountKey}"
                        is AccountChange.Removed -> "removed:${change.accountKey}"
                    }
                },
            )
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
                platformId = "Mastodon",
                last_active = 1L,
                sort_id = 0L,
            ),
        )
    }
}
