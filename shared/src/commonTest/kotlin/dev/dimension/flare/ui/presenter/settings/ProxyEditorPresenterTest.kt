package dev.dimension.flare.ui.presenter.settings

import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxyProtocol
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyEditorPresenterTest : RobolectricTest() {
    @Test
    fun editorSavesAndTestsWithoutObservingSettingsOrRequiringAccounts() =
        runTest {
            var settings = ProxySettings()
            var settingsReads = 0
            val repository =
                ProxyRepository(
                    object : DataStore<ProxySettings> {
                        override val data =
                            flow {
                                settingsReads++
                                emit(settings)
                            }

                        override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                            transform(settings).also { settings = it }
                    },
                )
            startKoin { modules(module { testSingle { repository } }) }
            val presenter = ProxyEditorPresenter()
            val models = MutableStateFlow<ProxyEditorState?>(null)
            val job = launch { moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.collect { models.value = it } }
            try {
                withContext(Dispatchers.Default) {
                    val states = models.filterNotNull()
                    val profile = ProxyProfile("work", "Work", ProxyProtocol.SOCKS5, "proxy.example", 1080)
                    states.first().test(profile.copy(host = ""))
                    val failed = states.first { it.testState is UiState.Error }
                    assertEquals(ProxySettingsError.INVALID_HOST, failed.error)
                    assertNull(failed.saveState)
                    failed.save(profile)
                    val saved = states.first { it.saveState is UiState.Success }
                    assertNull(saved.testState)
                    assertNull(saved.error)
                    assertEquals(profile, settings.profiles.single())
                    assertEquals(0, settingsReads)
                }
            } finally {
                job.cancelAndJoin()
                stopKoin()
            }
        }
}
