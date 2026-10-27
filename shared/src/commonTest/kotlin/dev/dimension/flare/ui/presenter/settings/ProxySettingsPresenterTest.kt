package dev.dimension.flare.ui.presenter.settings

import androidx.datastore.core.DataStore
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
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxySettingsPresenterTest : RobolectricTest() {
    @Test
    fun operationsUseUiStateAndPolicySelectionKeepsThePageInteractive() =
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
            val settings = MutableStateFlow(ProxySettings())
            val pauseWrites = MutableStateFlow(false)
            val writes = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
            val proxy =
                ProxyRepository(
                    object : DataStore<ProxySettings> {
                        override val data = settings

                        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings {
                            if (pauseWrites.value) {
                                val release = CompletableDeferred<Unit>()
                                writes.send(release)
                                release.await()
                            }
                            return transform(settings.value).also { settings.value = it }
                        }
                    },
                )
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
            val editor = ProxyEditorPresenter()
            val models = MutableStateFlow<ProxySettingsState?>(null)
            val editorModels = MutableStateFlow<ProxyEditorState?>(null)
            val job =
                launch {
                    moleculeFlow(RecompositionMode.Immediate) { presenter.body() to editor.body() }.collect { (page, dialog) ->
                        models.value = page
                        editorModels.value = dialog
                    }
                }
            try {
                withContext(Dispatchers.Default) {
                    val states = models.filterNotNull()
                    val editorStates = editorModels.filterNotNull()
                    pauseWrites.value = true
                    for (account in listOf(null, MicroBlogKey("alice", "example.social"))) {
                        states.first { it.settings.takeSuccess() != null }.select(account, ProxyPolicy(ProxyMode.DIRECT))
                        val release = writes.receive()
                        val disabledPage =
                            try {
                                withTimeoutOrNull(200) { states.first { it.operation is UiState.Loading } }
                            } finally {
                                release.complete(Unit)
                            }
                        states.first {
                            it.operation !is UiState.Loading &&
                                it.settings
                                    .takeSuccess()
                                    ?.policy(account)
                                    ?.mode == ProxyMode.DIRECT
                        }
                        assertNull(disabledPage, "Changing one proxy policy must not disable the whole settings list")
                    }
                    val profile = ProxyProfile("work", "Work", ProxyProtocol.HTTPS, "proxy.example", 443)
                    editorStates.first().save(profile)
                    val release = writes.receive()
                    try {
                        val saving = editorStates.first { it.saveState is UiState.Loading }
                        assertNull(saving.testState)
                        assertTrue(models.value!!.operation !is UiState.Loading)
                    } finally {
                        release.complete(Unit)
                    }
                    editorStates.first { it.saveState is UiState.Success }
                    states.first {
                        it.settings
                            .takeSuccess()
                            ?.profiles
                            ?.contains(profile) == true
                    }
                    pauseWrites.value = false
                    val invalid = profile.copy(id = "draft", name = "", host = "")
                    editorStates.first().save(invalid)
                    val saveFailure = editorStates.first { it.saveState is UiState.Error }
                    assertEquals(ProxySettingsError.NAME_REQUIRED, saveFailure.error)
                    assertNull(models.value!!.error)
                    saveFailure.test(invalid.copy(name = "Draft"))
                    val testFailure = editorStates.first { it.testState is UiState.Error }
                    assertNull(testFailure.saveState)
                    assertEquals(ProxySettingsError.INVALID_HOST, testFailure.error)
                    assertNull(models.value!!.error)
                    states.first().select(null, ProxyPolicy(ProxyMode.PROXY, "missing"))
                    val selectionFailure = states.first { it.operation is UiState.Error }
                    assertEquals(ProxySettingsError.INVALID_POLICY, selectionFailure.error)
                    assertEquals(ProxySettingsError.INVALID_HOST, editorModels.value!!.error)
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
