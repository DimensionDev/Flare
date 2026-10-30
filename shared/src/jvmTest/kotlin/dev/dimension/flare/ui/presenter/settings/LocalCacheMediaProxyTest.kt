package dev.dimension.flare.ui.presenter.settings

import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.DbUserHistory
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.createSampleUser
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCacheMediaProxyTest {
    @Test
    fun historyRestoresTheRecordedAccountAfterDatabaseRoundTrip() = checkPersistedProfile(search = false)

    @Test
    fun accountlessSearchRestoresGlobalScopeAfterDatabaseRoundTrip() = checkPersistedProfile(search = true)

    private fun checkPersistedProfile(search: Boolean): Unit =
        runBlocking {
            val main = Executors.newSingleThreadExecutor { Thread(it, "local-history-ui") }.asCoroutineDispatcher()
            Dispatchers.setMain(main)
            val root = createTestRootPath()
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val app =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(
                        createDatabaseDriver(),
                    ).setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val cache =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(
                        createDatabaseDriver(),
                    ).setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val scope = CoroutineScope(SupervisorJob() + main)
            try {
                val viewer = MicroBlogKey("viewer", "example.social")
                val text =
                    "Cached User".toUiPlainText().copy(
                        renderRuns =
                            persistentListOf(
                                RenderContent.Text(persistentListOf(RenderRun.Image("https://example.com/emoji.png", ":emoji:"))),
                            ),
                    )
                val profile = createSampleUser().copy(nameInternal = text, description = text).withProxy(ProxyRef.forAccount(viewer))
                cache.userDao().insert(DbUser(profile.key, "Cached User", "cached-user", profile.key.host, profile))
                cache.userDao().insertHistory(DbUserHistory(profile.key, AccountType.Specific(viewer), 1))
                val persisted =
                    cache
                        .userDao()
                        .getByKeys(listOf(profile.key))
                        .single()
                        .content
                assertEquals(ProxyRef.Unbound, persisted.avatar?.proxy)
                assertEquals(
                    ProxyRef.Unbound,
                    persisted.name.imageMedias
                        .single()
                        .proxy,
                )
                val accounts =
                    AccountRepository(
                        app,
                        scope,
                        AppDataStore(storage),
                        cache,
                        ProxyRepository(storage),
                        testPlatformRegistry(),
                        dev.dimension.flare.data.repository.DraftRepository(
                            app,
                            dev.dimension.flare.data.repository
                                .DraftMediaStore(
                                    dev.dimension.flare.data.io
                                        .InMemoryFileStorage(),
                                ),
                        ),
                    )
                startKoin {
                    modules(
                        module {
                            testSingle { cache }
                            testSingle { accounts }
                        },
                    )
                }
                val presenter = LocalCacheSearchPresenter()
                val models = MutableStateFlow<LocalCacheSearchPresenter.State?>(null)
                scope.launch { moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it } }
                val initial =
                    withTimeout(5_000) {
                        models.filterNotNull().first { (it.userHistory as? PagingState.Success)?.itemCount == 1 }
                    }
                val state =
                    if (search) {
                        withContext(main) { initial.setQuery("Cached User") }
                        withTimeout(5_000) { models.filterNotNull().first { (it.searchUser as? PagingState.Success)?.itemCount == 1 } }
                    } else {
                        initial
                    }
                val items = (if (search) state.searchUser else state.userHistory) as PagingState.Success<UiProfile>
                val restored = assertNotNull(items.peek(0))
                val expected = if (search) ProxyRef.Global else ProxyRef.forAccount(viewer)
                assertEquals(expected, restored.avatar?.proxy)
                assertEquals(expected, restored.banner?.proxy)
                assertEquals(
                    expected,
                    restored.name.imageMedias
                        .single()
                        .proxy,
                )
                assertEquals(
                    expected,
                    restored.description
                        ?.imageMedias
                        ?.single()
                        ?.proxy,
                )
            } finally {
                scope.coroutineContext.job.cancelAndJoin()
                stopKoin()
                cache.close()
                app.close()
                Dispatchers.resetMain()
                main.close()
                deleteTestRootPath(root)
            }
        }
}
