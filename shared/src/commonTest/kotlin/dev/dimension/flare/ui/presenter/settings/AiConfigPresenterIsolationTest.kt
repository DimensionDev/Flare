package dev.dimension.flare.ui.presenter.settings

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.di.startKoin
import dev.dimension.flare.di.testSingle
import dev.dimension.flare.ui.model.UiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiConfigPresenterIsolationTest : RobolectricTest() {
    @Test
    fun twoPresentersInOneCompositionKeepIndependentState() = checkIsolation(sharedComposition = true)

    @Test
    fun twoPresenterCompositionsKeepIndependentState() = checkIsolation(sharedComposition = false)

    private fun checkIsolation(sharedComposition: Boolean) =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val root = createTestRootPath()
            val dataStore = AppDataStore(OkioFileStorage(createTestFileSystem(), root))
            var scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/", "basic-key", "light", "low", "{\"seed\":1}")
            val agent = AppSettings.AiConfig.Type.OpenAI("https://agent.example/", "agent-key", "strong", "high", "{\"seed\":2}")
            val service =
                OpenAIService {
                    HttpClient(
                        MockEngine { request ->
                            respond("""{"data":[{"id":"${request.url.host}:${request.headers[HttpHeaders.Authorization]}"}]}""")
                        },
                    )
                }
            startKoin {
                modules(
                    module {
                        testSingle { dataStore }
                        testSingle { service }
                        testSingle<OnDeviceAI> {
                            object : OnDeviceAI {
                                override suspend fun isAvailable() = true

                                override suspend fun translate(
                                    source: String,
                                    targetLanguage: String,
                                    prompt: String,
                                ): String? = error("Unused")

                                override suspend fun tldr(
                                    source: String,
                                    targetLanguage: String,
                                    prompt: String,
                                ): String? = error("Unused")
                            }
                        }
                    },
                )
            }
            try {
                dataStore.appSettingsStore.updateData { it.copy(aiConfig = AppSettings.AiConfig(type = basic, agentType = agent)) }
                var states = presenterStates(scope, sharedComposition)
                val initial = states.first { (base, override) -> base.hasConfig(basic) && override.hasConfig(agent) }
                assertFalse(initial.first.isAgentConfig)
                assertTrue(initial.second.isAgentConfig)
                assertFalse(initial.second.useSharedConfig)
                val loaded = states.first { (base, override) -> base.hasModels(basic) && override.hasModels(agent) }
                assertEquals(listOf(AiTypeOption.OnDevice, AiTypeOption.OpenAI), loaded.first.supportedTypes)
                assertEquals(listOf(AiTypeOption.OpenAI), loaded.second.supportedTypes)

                // Dialog callbacks may outlive their snapshot; interleaved saves must use the latest stored config.
                val updatedBasic =
                    basic.copy(
                        serverUrl = "https://new-basic.example/",
                        apiKey = "new-basic-key",
                        model = "new-light",
                        reasoningEffort = "medium",
                        extraBody = "{\"seed\":3}",
                    )
                val updatedAgent =
                    agent.copy(
                        serverUrl = "https://new-agent.example/",
                        apiKey = "new-agent-key",
                        model = "new-strong",
                        reasoningEffort = "low",
                        extraBody = "{\"seed\":4}",
                    )
                initial.first.setOpenAIServerUrl(updatedBasic.serverUrl)
                initial.second.setOpenAIServerUrl(updatedAgent.serverUrl)
                initial.second.setOpenAIApiKey(updatedAgent.apiKey)
                initial.first.setOpenAIApiKey(updatedBasic.apiKey)
                initial.first.setOpenAIModel(updatedBasic.model)
                initial.second.setOpenAIModel(updatedAgent.model)
                initial.second.setOpenAIReasoningEffort(AiReasoningEffortOption.Low)
                initial.first.setOpenAIReasoningEffort(AiReasoningEffortOption.Medium)
                initial.first.setOpenAIExtraBody(updatedBasic.extraBody)
                initial.second.setOpenAIExtraBody(updatedAgent.extraBody)
                states.first { (base, override) -> base.hasConfig(updatedBasic) && override.hasConfig(updatedAgent) }
                states.first { (base, override) -> base.hasModels(updatedBasic) && override.hasModels(updatedAgent) }
                assertEquals(
                    AppSettings.AiConfig(type = updatedBasic, agentType = updatedAgent),
                    dataStore.appSettingsStore.data
                        .first()
                        .aiConfig,
                )

                scope.cancel()
                scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
                states = presenterStates(scope, sharedComposition)
                val restored = states.first { (base, override) -> base.hasConfig(updatedBasic) && override.hasConfig(updatedAgent) }
                restored.second.setUseSharedConfig(true)
                val inherited = states.first { (_, override) -> override.useSharedConfig && override.hasConfig(updatedBasic) }
                assertNull(
                    dataStore.appSettingsStore.data
                        .first()
                        .aiConfig.agentType,
                )
                inherited.first.setOpenAIModel("inherited-model")
                val changedBasic = updatedBasic.copy(model = "inherited-model")
                val following = states.first { (base, override) -> base.hasConfig(changedBasic) && override.hasConfig(changedBasic) }
                following.second.setUseSharedConfig(false)
                val independent = states.first { (_, override) -> !override.useSharedConfig && override.hasConfig(changedBasic) }
                independent.second.setOpenAIModel("independent-again")
                states.first { (base, override) ->
                    base.hasConfig(changedBasic) &&
                        override.hasConfig(changedBasic.copy(model = "independent-again"))
                }
                assertEquals(
                    changedBasic,
                    dataStore.appSettingsStore.data
                        .first()
                        .aiConfig.type,
                )
            } finally {
                scope.cancel()
                stopKoin()
                Dispatchers.resetMain()
                deleteTestRootPath(root)
            }
        }

    private suspend fun presenterStates(
        scope: CoroutineScope,
        sharedComposition: Boolean,
    ) = if (sharedComposition) {
        val basic = AiConfigPresenter()
        val agent = AiConfigPresenter(agentConfig = true)
        moleculeFlow(RecompositionMode.Immediate) { basic.body() to agent.body() }
    } else {
        val basic = AiConfigPresenter()
        val agent = AiConfigPresenter(agentConfig = true)
        combine(
            moleculeFlow(RecompositionMode.Immediate) { basic.body() },
            moleculeFlow(RecompositionMode.Immediate) { agent.body() },
        ) { base, override -> base to override }
    }.stateIn(scope)

    private fun AiConfigPresenter.State.hasConfig(config: AppSettings.AiConfig.Type.OpenAI) =
        openAIServerUrl == config.serverUrl && openAIApiKey == config.apiKey && openAIModel == config.model &&
            openAIReasoningEffort.value == config.reasoningEffort && openAIExtraBody == config.extraBody

    private fun AiConfigPresenter.State.hasModels(config: AppSettings.AiConfig.Type.OpenAI) =
        (openAIModels as? UiState.Success)?.data ==
            listOf("${config.serverUrl.removePrefix("https://").removeSuffix("/")}:Bearer ${config.apiKey}")
}
