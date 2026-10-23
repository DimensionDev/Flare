package dev.dimension.flare.ui.presenter.settings

import androidx.room3.Room
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.AppDatabaseExport
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImportedAccountProxyTest {
    @Test
    fun importedAccountCanUseNetworkingAfterItWasDeleted(): Unit =
        runBlocking {
            val root = createTestRootPath()
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val app =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val cache =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val proxy = ProxyRepository(storage)
            val accounts = AccountRepository(app, scope, AppDataStore(storage), cache, proxy, testPlatformRegistry())
            val key = MicroBlogKey("deleted-then-imported", "example.social")
            val row = DbAccount(key, "{}", "Mastodon", 1L)
            startKoin {
                modules(
                    module {
                        testSingle { app }
                        testSingle { proxy }
                    },
                )
            }
            try {
                app.accountDao().insert(row)
                val backup = AppDatabaseExport(accounts = listOf(row)).encodeJson()
                proxy.selectGlobal(ProxyPolicy(ProxyMode.DIRECT))
                assertEquals(ProxyMode.DIRECT, ProxyNetwork.snapshot(ProxyRef.Account(key)).connection.mode)

                accounts.delete(key).join()
                assertNull(app.accountDao().getAccount(key))
                assertFailsWith<IllegalStateException> { ProxyNetwork.snapshot(ProxyRef.Account(key)) }

                val invalid = AppDatabaseExport(accounts = listOf(row.copy(platformId = "bad/id"))).encodeJson()
                assertFailsWith<IllegalArgumentException> { ImportAppDatabasePresenter(invalid).import() }
                assertNull(app.accountDao().getAccount(key))
                assertFailsWith<IllegalStateException> { ProxyNetwork.snapshot(ProxyRef.Account(key)) }

                ImportAppDatabasePresenter(backup).import()
                assertNotNull(app.accountDao().getAccount(key), "Import must actually restore the account row")
                assertEquals(ProxyMode.DIRECT, ProxyNetwork.snapshot(ProxyRef.Account(key)).connection.mode)
                proxy.selectAccount(key, ProxyPolicy(ProxyMode.DIRECT))
                proxy.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                ImportAppDatabasePresenter(backup).import()
                assertEquals(ProxyMode.DIRECT, ProxyNetwork.snapshot(ProxyRef.Account(key)).connection.mode)
            } finally {
                proxy.restoreAccount(key)
                proxy.selectGlobal(ProxyPolicy(ProxyMode.SYSTEM))
                scope.coroutineContext.job.cancelAndJoin()
                stopKoin()
                cache.close()
                app.close()
                deleteTestRootPath(root)
            }
        }
}
