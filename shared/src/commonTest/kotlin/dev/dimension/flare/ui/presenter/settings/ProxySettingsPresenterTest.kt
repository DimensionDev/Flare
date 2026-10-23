package dev.dimension.flare.ui.presenter.settings

import androidx.room3.Room
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.model.ProxyMode
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.testPlatformRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxySettingsPresenterTest : RobolectricTest() {
    @Test
    fun editorFailuresStaySeparateFromPageActions() =
        runTest {
            val root = createTestRootPath()
            val storage = OkioFileStorage(createTestFileSystem(), root)
            val app =
                Room
                    .memoryDatabaseBuilder<AppDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val cache =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val proxy = ProxyRepository(storage)
            val accounts = AccountRepository(app, scope, AppDataStore(storage), cache, proxy, testPlatformRegistry())
            startKoin {
                modules(
                    module {
                        testSingle { proxy }
                        testSingle { accounts }
                    },
                )
            }
            val presenter = ProxySettingsPresenter()
            val models = MutableStateFlow<ProxySettingsState?>(null)
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it }
                }
            try {
                withContext(Dispatchers.Default) {
                    val states = models.filterNotNull()
                    val invalid = ProxyProfile("draft", "", ProxyProtocol.HTTPS, "", 443)
                    states.first { it.settings != null }.save(invalid) { error("Invalid draft must not be saved") }
                    val saveFailure = states.first { !it.busy && it.editorError != null }
                    assertNull(saveFailure.error)
                    assertEquals(ProxySettingsError.NAME_REQUIRED, saveFailure.editorError)
                    saveFailure.clearEditorError()
                    states.first { it.editorError == null }.test(invalid, fromEditor = true)
                    val testFailure = states.first { !it.busy && it.editorError != null }
                    assertNull(testFailure.error)
                    testFailure.clearEditorError()
                    states.first { it.editorError == null }.test(invalid, fromEditor = false)
                    val pageTestFailure = states.first { !it.busy && it.error != null }
                    assertNull(pageTestFailure.editorError)
                    pageTestFailure.select(null, ProxyPolicy(ProxyMode.PROXY, "missing"))
                    val selectionFailure = states.first { !it.busy && it.error != null && it.error != pageTestFailure.error }
                    assertNull(selectionFailure.editorError)
                    assertEquals(ProxySettingsError.INVALID_POLICY, selectionFailure.error)
                }
            } finally {
                job.cancelAndJoin()
                scope.cancel()
                stopKoin()
                cache.close()
                app.close()
                deleteTestRootPath(root)
            }
        }
}
