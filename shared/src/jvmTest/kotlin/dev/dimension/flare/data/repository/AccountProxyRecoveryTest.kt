package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import androidx.room3.Room
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.app.model.DbSearchHistory
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmojiHistory
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.settings.ImportAppDatabasePresenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountProxyRecoveryTest {
    private val root = createTestRootPath()
    private val storage = OkioFileStorage(createTestFileSystem(), root)
    private val app =
        Room
            .memoryDatabaseBuilder<AppDatabase>()
            .setDriver(
                createDatabaseDriver(),
            ).setQueryCoroutineContext(Dispatchers.IO)
            .build()
    private val cache =
        Room
            .memoryDatabaseBuilder<CacheDatabase>()
            .setDriver(
                createDatabaseDriver(),
            ).setQueryCoroutineContext(Dispatchers.IO)
            .build()
    private val store = FailingStore()
    private val proxy = ProxyRepository(store)
    private val scopes = mutableListOf<CoroutineScope>()
    private val deleted = MicroBlogKey("deleted", "example.social")
    private val other = MicroBlogKey("other", "example.social")

    @AfterTest
    fun close(): Unit =
        runBlocking {
            scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
            stopKoin()
            cache.close()
            app.close()
            deleteTestRootPath(root)
        }

    @Test
    fun recoveryReadFailureDoesNotCancelSharedScopeOrLaterLogin(): Unit =
        runBlocking {
            store.failRead = true
            val (accounts, scope) = repository()
            accounts.withAccountChanges { }
            assertTrue(scope.isActive)
            accounts.addAccount(UiAccount(other, "Mastodon"), "credentials").join()
            assertNotNull(app.accountDao().getAccount(other))
        }

    @Test
    fun failedCleanupRetainsDeletionAndAllowsUnrelatedLogin(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            app.accountDao().insert(row(deleted))
            cache.close()
            val (accounts, scope) = repository()
            accounts.withAccountChanges { }
            assertTrue(scope.isActive)
            assertNotNull(app.accountDao().getAccount(deleted), "Do not discard the account before cleanup succeeds")
            assertTrue(deleted.toString() in store.state.value.removedAccounts)
            accounts.addAccount(UiAccount(other, "Mastodon"), "credentials").join()
            assertNotNull(app.accountDao().getAccount(other))
        }

    @Test
    fun recoveryCleansCacheEvenAfterAccountRowWasDeleted(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            val type = AccountType.Specific(deleted)
            cache.emojiDao().insertHistory(DbEmojiHistory(type, "stale", 1L))
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            assertNull(app.accountDao().getAccount(deleted))
            assertEquals(emptyList(), cache.emojiDao().getHistory(type))
            assertTrue(deleted.toString() in store.state.value.removedAccounts, "Old media must stay blocked")
        }

    @Test
    fun failedImportRollsBackAllAccountsAndCanBeRetried(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            app.accountDao().insert(row(other))
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            install(accounts)
            val backup =
                AppDatabaseExport(
                    accounts = listOf(row(other).copy(credential_json = "{\"new\":true}"), row(deleted)),
                ).encodeJson()
            store.failWrite = true
            assertFailsWith<IOException> { ImportAppDatabasePresenter(backup).import() }
            assertNull(app.accountDao().getAccount(deleted))
            assertEquals("{}", app.accountDao().getAccount(other)?.credential_json)
            assertTrue(deleted.toString() in store.state.value.removedAccounts)

            store.failWrite = false
            ImportAppDatabasePresenter(backup).import()
            val (restarted, _) = repository()
            restarted.withAccountChanges { }
            assertNotNull(app.accountDao().getAccount(deleted))
            assertEquals("{\"new\":true}", app.accountDao().getAccount(other)?.credential_json)
        }

    @Test
    fun importWithoutAccountsDoesNotDependOnProxyStorage(): Unit =
        runBlocking {
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            install(accounts)
            store.failRead = true
            store.failWrite = true
            val history = DbSearchHistory("keep this search", 1L)
            ImportAppDatabasePresenter(AppDatabaseExport(searchHistories = listOf(history)).encodeJson()).import()
            assertEquals(listOf(history), app.searchHistoryDao().select().first())
        }

    @Test
    fun cancellationDuringRestorationStillCommitsAccountAndPolicyTogether(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            install(accounts)
            val writing = CompletableDeferred<Unit>()
            val finishWrite = CompletableDeferred<Unit>()
            store.beforeWrite = {
                writing.complete(Unit)
                finishWrite.await()
            }
            val job =
                launch {
                    ImportAppDatabasePresenter(AppDatabaseExport(accounts = listOf(row(deleted))).encodeJson()).import()
                }
            try {
                withTimeout(5_000) { writing.await() }
                job.cancel()
            } finally {
                finishWrite.complete(Unit)
                job.join()
            }
            assertNotNull(app.accountDao().getAccount(deleted))
            assertTrue(deleted.toString() !in store.state.value.removedAccounts)
        }

    @Test
    fun failedLoginRestoreDoesNotLeaveAccountForDeletionOnRestart(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            store.failWrite = true
            accounts.addAccount(UiAccount(deleted, "Mastodon"), "new credentials").join()
            assertNull(app.accountDao().getAccount(deleted))
            assertTrue(deleted.toString() in store.state.value.removedAccounts)
        }

    @Test
    fun importCannotClearDeletionWhenCleanupStillFails(): Unit =
        runBlocking {
            proxy.removeAccount(deleted)
            app.accountDao().insert(row(deleted))
            cache.close()
            val (accounts, _) = repository()
            accounts.withAccountChanges { }
            install(accounts)
            val backup = AppDatabaseExport(accounts = listOf(row(deleted).copy(credential_json = "{\"new\":true}"))).encodeJson()
            assertFailsWith<Exception> { ImportAppDatabasePresenter(backup).import() }
            assertTrue(deleted.toString() in store.state.value.removedAccounts)
            assertEquals("{}", app.accountDao().getAccount(deleted)?.credential_json)
        }

    private fun row(key: MicroBlogKey) = DbAccount(key, "{}", "Mastodon", 1L)

    private fun repository(): Pair<AccountRepository, CoroutineScope> {
        val scope = CoroutineScope(Job() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
        scopes += scope
        return AccountRepository(app, scope, AppDataStore(storage), cache, proxy, testPlatformRegistry()) to scope
    }

    private fun install(accounts: AccountRepository) {
        startKoin {
            modules(
                module {
                    testSingle { app }
                    testSingle { proxy }
                    testSingle { accounts }
                },
            )
        }
    }

    private class FailingStore : DataStore<ProxySettings> {
        val state = MutableStateFlow(ProxySettings())
        var failRead = false
        var failWrite = false
        var beforeWrite: suspend () -> Unit = { }
        override val data =
            flow {
                if (failRead) {
                    failRead = false
                    throw IOException("Injected read failure")
                }
                emitAll(state)
            }

        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings {
            if (failWrite) throw IOException("Injected write failure")
            beforeWrite()
            return transform(state.value).also { state.value = it }
        }
    }
}
