package dev.dimension.flare.ui.presenter.settings

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.translation.image.ImageTranslationModelDiscovery
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ImageTranslationSettingsPresenterTest : RobolectricTest() {
    @Test
    fun leavingSharedConfigurationPrefillsPersistedEmptyFieldsAndUsesLatestBasicSettings() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val root = createTestRootPath()
            val store = AppDataStore(OkioFileStorage(createTestFileSystem(), root))
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1", "basic-key", "basic-image")
            val agent = AppSettings.AiConfig.Type.OpenAI("https://agent.example/v1", "agent-key", "agent-model")
            val image =
                ImageTranslationConfig(ImageTranslationProvider.OpenAIImages, useSharedAIConfig = true)
                    .withValue("baseUrl", "")
                    .withValue("apiKey", "")
                    .withValue("model", "")
                    .withValue("prompt", "Keep the image prompt")
                    .withValue("inputFormat", "Multipart")
            val discovery = ImageTranslationModelDiscovery(OpenAIService { HttpClient(MockEngine { respond("""{"data":[]}""") }) })
            startKoin {
                modules(
                    module {
                        testSingle { store }
                        testSingle<CoroutineScope> { scope }
                        testSingle { discovery }
                    },
                )
            }
            try {
                store.appSettingsStore.updateData {
                    it.copy(aiConfig = AppSettings.AiConfig(type = basic, agentType = agent), imageTranslationConfig = image)
                }
                val presenter = ImageTranslationSettingsPresenter()
                val states = moleculeFlow(RecompositionMode.Immediate) { presenter.body() }.stateIn(scope)
                val shared = states.first { it.useSharedConfig && it.config.value("model") == basic.model }
                shared.setUseSharedConfig(false)
                val detached = states.first { it.config.provider == ImageTranslationProvider.OpenAIImages && !it.useSharedConfig }
                assertEquals(basic.serverUrl, detached.config.value("baseUrl"))
                assertEquals(basic.apiKey, detached.config.value("apiKey"))
                assertEquals(basic.model, detached.config.value("model"))
                detached.setField("model", "custom-image")
                val custom = states.first { it.config.value("model") == "custom-image" }
                custom.setUseSharedConfig(true)
                states.first { it.useSharedConfig }
                val latest = basic.copy(serverUrl = "https://latest.example/v1", apiKey = "latest-key", model = "latest-image")
                store.appSettingsStore.updateData { it.copy(aiConfig = it.aiConfig.copy(type = latest)) }
                states.first { it.useSharedConfig && it.config.value("model") == latest.model }
                // A callback from an older render must copy the latest persisted basic settings.
                shared.setUseSharedConfig(false)
                val copied = states.first { !it.useSharedConfig }
                assertEquals(latest.serverUrl, copied.config.value("baseUrl"))
                assertEquals(latest.apiKey, copied.config.value("apiKey"))
                assertEquals(latest.model, copied.config.value("model"))
                assertEquals("Keep the image prompt", copied.config.value("prompt"))
                assertEquals("Multipart", copied.config.value("inputFormat"))
                assertEquals(
                    AppSettings.AiConfig(type = latest, agentType = agent),
                    store.appSettingsStore.data
                        .first()
                        .aiConfig,
                )
            } finally {
                scope.cancel()
                stopKoin()
                deleteTestRootPath(root)
                Dispatchers.resetMain()
            }
        }
}
