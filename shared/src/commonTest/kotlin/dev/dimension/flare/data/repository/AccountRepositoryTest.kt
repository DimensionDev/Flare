package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
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
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.UnsupportedPlatformException
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.home.RememberedAccountSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
    private val otherAccountKey = MicroBlogKey(id = "bob", host = "example.social")

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
    fun startupCompletesInterruptedDeletionAndAllowsRelogin() =
        runTest {
            insertAccount()
            val proxy = ProxyRepository(fileStorage)
            proxy.selectAccount(accountKey, ProxyPolicy(ProxyMode.DIRECT))
            appDataStore.composeConfigData.updateData { it.copy(lastAccounts = listOf(accountKey)) }
            proxy.removeAccount(accountKey)
            val repository = createRepository(this, proxy)
            val other = MicroBlogKey("other", "example.social")
            repository.addAccount(UiAccount(other, "Mastodon"), "unaffected credentials")
            assertNull(appDatabase.accountDao().getAccount(accountKey))
            assertEquals(
                emptyList(),
                appDataStore.composeConfigData.data
                    .first()
                    .lastAccounts,
            )
            assertEquals(setOf(accountKey.toString()), proxy.settings.first().removedAccounts)

            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credentials")
            assertEquals(accountKey, repository.find(accountKey)?.accountKey)
            assertFalse(accountKey.toString() in proxy.settings.first().removedAccounts)
        }

    @Test
    fun reloginWaitsForPendingDeletionRecovery() =
        runTest {
            insertAccount()
            val proxy = ProxyRepository(fileStorage)
            proxy.removeAccount(accountKey)
            val repository = createRepository(this, proxy)
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credentials")
            assertEquals(accountKey, repository.find(accountKey)?.accountKey)
            assertFalse(accountKey.toString() in proxy.settings.first().removedAccounts)
        }

    @Test
    fun accountAdditionIsPublishedBeforeTheNextAccountMutationStarts() =
        runTest {
            val store = ControlledProxyStore(ProxySettings(removedAccounts = setOf(accountKey.toString())))
            val repository = createRepository(this, ProxyRepository(store))
            repository.withAccountChanges { }
            val writeEntered = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            store.beforeWrite = {
                writeEntered.complete(Unit)
                releaseWrite.await()
            }
            val events = Channel<String>(Channel.UNLIMITED)
            val observer =
                launch(Dispatchers.Unconfined) {
                    repository.onAdded.collect { events.send("added") }
                }
            val addition = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "restored") }
            val nextMutation =
                launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
                    repository.withAccountChanges { events.send("mutation-start") }
                }
            try {
                writeEntered.await()
                // Run the queued mutation during unlock, exposing an announcement sent after the lock was released.
                nextMutation.start()
                assertNull(events.tryReceive().getOrNull(), "An account must not be announced before restoration commits")
                assertFalse(nextMutation.isCompleted, "The next mutation must wait for restoration")

                releaseWrite.complete(Unit)
                addition.join()
                nextMutation.join()

                assertEquals(listOf("added", "mutation-start"), List(2) { events.tryReceive().getOrNull() })
                assertEquals(accountKey, repository.find(accountKey)?.accountKey)
                assertEquals(emptySet(), store.settings.value.removedAccounts)
            } finally {
                releaseWrite.complete(Unit)
                addition.cancelAndJoin()
                nextMutation.cancelAndJoin()
                observer.cancelAndJoin()
            }
        }

    @Test
    fun failedAccountRestorationDoesNotPublishAdded() =
        runTest {
            val removed = setOf(accountKey.toString())
            val store = ControlledProxyStore(ProxySettings(removedAccounts = removed))
            val repository = createRepository(this, ProxyRepository(store))
            repository.withAccountChanges { }
            val failure = IllegalStateException("Proxy restoration failed")
            store.beforeWrite = { throw failure }
            val events = Channel<UiAccount>(Channel.UNLIMITED)
            val observer =
                launch(Dispatchers.Unconfined) {
                    repository.onAdded.collect { events.send(it) }
                }
            try {
                val thrown =
                    assertFailsWith<IllegalStateException> {
                        repository.addAccount(UiAccount(accountKey, "Mastodon"), "restored")
                    }
                assertEquals(failure.message, thrown.message)
                assertNull(appDatabase.accountDao().getAccount(accountKey))
                assertEquals(removed, store.settings.value.removedAccounts)
                assertNull(events.tryReceive().getOrNull(), "A rolled-back account must not be announced")
            } finally {
                observer.cancelAndJoin()
            }
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

            repository.delete(unavailableKey)
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
    fun deletingAccountClearsItsSavedNotificationSelectionEvenWhenThePageIsClosed() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = accountKey) }

            repository.delete(accountKey)

            assertNull(
                appDataStore.notificationPreferences.data
                    .first()
                    .selectedAccountKey,
            )
            assertNull(appDatabase.accountDao().getAccount(accountKey))
        }

    @Test
    fun deletingAnotherAccountPreservesTheSavedNotificationSelection() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val savedAccount = MicroBlogKey("bob", "another.social")
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = savedAccount) }

            repository.delete(accountKey)

            assertEquals(
                savedAccount,
                appDataStore.notificationPreferences.data
                    .first()
                    .selectedAccountKey,
            )
        }

    @Test
    fun deletingAccountClearsItsSavedDiscoverSelectionEvenWhenThePageIsClosed() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = accountKey) }

            repository.delete(accountKey)

            assertNull(
                appDataStore.discoverPreferences.data
                    .first()
                    .selectedAccountKey,
            )
            assertNull(appDatabase.accountDao().getAccount(accountKey))
        }

    @Test
    fun deletingAnotherAccountPreservesTheSavedDiscoverSelection() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            val savedAccount = MicroBlogKey("bob", "another.social")
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = savedAccount) }

            repository.delete(accountKey)

            assertEquals(
                savedAccount,
                appDataStore.discoverPreferences.data
                    .first()
                    .selectedAccountKey,
            )
        }

    @Test
    fun notificationSelectionSavedDuringDeletionDoesNotReturnAfterReaddingAccount() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.notificationPreferences, accountKey, expectedSaved = null)
        }

    @Test
    fun discoverSelectionSavedDuringDeletionDoesNotReturnAfterReaddingAccount() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.discoverPreferences, accountKey, expectedSaved = null)
        }

    @Test
    fun notificationChoiceOfAnotherAccountDuringDeletionIsPreserved() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.notificationPreferences, otherAccountKey, expectedSaved = otherAccountKey)
        }

    @Test
    fun discoverChoiceOfAnotherAccountDuringDeletionIsPreserved() =
        runTest {
            checkSelectionSavedDuringDeletion(appDataStore.discoverPreferences, otherAccountKey, expectedSaved = otherAccountKey)
        }

    private suspend fun CoroutineScope.checkSelectionSavedDuringDeletion(
        preferences: DataStore<AccountSelectionPreferences>,
        selectedDuringDeletion: MicroBlogKey,
        expectedSaved: MicroBlogKey?,
    ) {
        val repository = createRepository(this)
        insertAccount()
        appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
        val keys = repository.allAccounts.first().map { it.accountKey }
        val openPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        openPage.updateAccounts(keys)
        openPage.restore()
        openPage.selectAccount(otherAccountKey)
        val closedPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        closedPage.updateAccounts(keys)
        closedPage.restore()
        closedPage.selectAccount(accountKey)
        preferences.updateData { it }

        val concurrentSelection =
            launch(Dispatchers.Unconfined) {
                assertEquals(accountKey, repository.onRemoved.first())
                openPage.selectAccount(selectedDuringDeletion)
                // The page closes after this click; only the app-scoped save remains.
            }
        try {
            repository.delete(accountKey)
            concurrentSelection.join()

            assertNull(repository.find(accountKey))
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "readded")
            assertEquals(expectedSaved, preferences.updateData { it }.selectedAccountKey)
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(otherAccountKey, reopened.selectedAccountKey.first())
        } finally {
            concurrentSelection.cancel()
        }
    }

    @Test
    fun lateNotificationSelectionCannotRestoreADeletedAccount() =
        runTest {
            checkLatePageSelection(appDataStore.notificationPreferences)
        }

    @Test
    fun lateDiscoverSelectionCannotRestoreADeletedAccount() =
        runTest {
            checkLatePageSelection(appDataStore.discoverPreferences)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.checkLatePageSelection(preferences: DataStore<AccountSelectionPreferences>) {
        val repository = createRepository(this)
        insertAccount()
        appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
        preferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
        val openPage =
            RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                repository.find(it) !=
                    null
            })
        openPage.restore()
        val removalObserved = CompletableDeferred<Unit>()
        val deliverRemoval = CompletableDeferred<Unit>()
        val saveEntered = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val pendingSave =
            launch(start = CoroutineStart.LAZY) {
                preferences.updateData {
                    saveEntered.complete(Unit)
                    releaseSave.await()
                    it
                }
            }
        val page =
            launch {
                repository.allAccounts.collect { accounts ->
                    val keys = accounts.map { it.accountKey }
                    if (accountKey !in keys) {
                        removalObserved.complete(Unit)
                        deliverRemoval.await()
                    }
                    openPage.updateAccounts(keys)
                }
            }
        try {
            openPage.selectedAccountKey.first { it == otherAccountKey }
            repository.delete(accountKey)
            removalObserved.await()
            assertNull(repository.find(accountKey))
            assertEquals(otherAccountKey, preferences.updateData { it }.selectedAccountKey)

            pendingSave.start()
            saveEntered.await()
            openPage.selectAccount(accountKey)
            page.cancelAndJoin()

            val readded = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "readded") }
            runCurrent()
            releaseSave.complete(Unit)
            pendingSave.join()
            readded.join()
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(otherAccountKey, reopened.selectedAccountKey.first())
        } finally {
            releaseSave.complete(Unit)
            pendingSave.cancelAndJoin()
            page.cancelAndJoin()
            deliverRemoval.complete(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deletionPreservesAFreshSelectionAfterReaddingTheSameAccount() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
            appDataStore.notificationPreferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
            appDataStore.discoverPreferences.updateData { it.copy(selectedAccountKey = otherAccountKey) }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val pendingNotificationSave =
                launch {
                    appDataStore.notificationPreferences.updateData {
                        entered.complete(Unit)
                        release.await()
                        it
                    }
                }
            entered.await()
            try {
                val deletion = launch { repository.delete(accountKey) }
                repository.allAccounts.first { accounts -> accounts.none { it.accountKey == accountKey } }
                val readded = launch { repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credential") }
                runCurrent()
                // Re-login may wait for removal to finish instead of overlapping its cleanup.
                if (!readded.isCompleted) {
                    release.complete(Unit)
                    deletion.join()
                }
                readded.join()
                val currentKeys = repository.allAccounts.first().map { it.accountKey }
                assertEquals(listOf(otherAccountKey, accountKey), currentKeys)
                val newPage =
                    RememberedAccountSelection(appDataStore.discoverPreferences, this, repository.accountMutationMutex, accountExists = {
                        repository.find(it) !=
                            null
                    })
                newPage.updateAccounts(currentKeys)
                newPage.restore()
                newPage.selectAccount(accountKey)
                assertEquals(accountKey, appDataStore.discoverPreferences.updateData { it }.selectedAccountKey)
                release.complete(Unit)
                deletion.join()
                val reopened =
                    RememberedAccountSelection(appDataStore.discoverPreferences, this, repository.accountMutationMutex, accountExists = {
                        repository.find(it) !=
                            null
                    })
                reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
                reopened.restore()
                assertEquals(accountKey, reopened.selectedAccountKey.first())
            } finally {
                release.complete(Unit)
                pendingNotificationSave.join()
            }
        }

    @Test
    fun delayedAccountRemovalDoesNotClearANewSelectionAfterReaddingAccount() =
        runTest {
            val repository = createRepository(this)
            insertAccount()
            appDatabase.accountDao().insert(DbAccount(otherAccountKey, "{}", "Mastodon", last_active = 2L, sort_id = 1L))
            val preferences = appDataStore.discoverPreferences
            val oldPage =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            oldPage.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            oldPage.restore()
            oldPage.selectAccount(accountKey)
            preferences.updateData { it }
            repository.delete(accountKey)
            val delayedRemoval = repository.allAccounts.first().map { it.accountKey }
            repository.addAccount(UiAccount(accountKey, "Mastodon"), "new credential")
            val newPage =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) !=
                        null
                })
            newPage.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            newPage.restore()
            newPage.selectAccount(accountKey)
            preferences.updateData { it }

            oldPage.updateAccounts(delayedRemoval)
            val reopened =
                RememberedAccountSelection(preferences, this, repository.accountMutationMutex, accountExists = {
                    repository.find(it) != null
                })
            reopened.updateAccounts(repository.allAccounts.first().map { it.accountKey })
            reopened.restore()
            assertEquals(accountKey, reopened.selectedAccountKey.first())
        }

    @Test
    fun reloginDoesNotPublishANewAccountEvent() =
        runTest {
            val repository = createRepository(this)
            val events = Channel<UiAccount>(Channel.UNLIMITED)
            val collection = launch { repository.onAdded.collect(events::send) }
            val alice = UiAccount(accountKey, "Mastodon")
            val bob = UiAccount(MicroBlogKey("bob", "example.social"), "Mastodon")

            try {
                repository.addAccount(alice, "first")
                assertEquals(alice.accountKey, events.receive().accountKey)
                repository.addAccount(bob, "second")
                assertEquals(bob.accountKey, events.receive().accountKey)

                repository.addAccount(alice.copy(platformId = "Pixelfed"), "refreshed")

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
            val added = Channel<UiAccount>(Channel.UNLIMITED)
            val removed = Channel<MicroBlogKey>(Channel.UNLIMITED)
            val addedCollection = launch { repository.onAdded.collect(added::send) }
            val removedCollection = launch { repository.onRemoved.collect(removed::send) }
            val account = UiAccount(accountKey, "Mastodon")

            try {
                repository.addAccount(account, "first")
                assertEquals(accountKey, added.receive().accountKey)

                repository.delete(accountKey)
                assertEquals(accountKey, removed.receive())

                repository.addAccount(account, "second")
                assertEquals(accountKey, added.receive().accountKey)
            } finally {
                addedCollection.cancel()
                removedCollection.cancel()
            }
        }

    private class ControlledProxyStore(
        initial: ProxySettings,
    ) : DataStore<ProxySettings> {
        val settings = MutableStateFlow(initial)
        override val data = settings
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings {
            val updated = transform(settings.value)
            beforeWrite()
            settings.value = updated
            return updated
        }
    }

    private fun createRepository(
        scope: CoroutineScope,
        proxy: ProxyRepository = ProxyRepository(fileStorage),
    ): AccountRepository =
        AccountRepository(
            appDatabase = appDatabase,
            coroutineScope = scope,
            appDataStore = appDataStore,
            cacheDatabase = cacheDatabase,
            proxyRepository = proxy,
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
