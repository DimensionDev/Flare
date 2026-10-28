package dev.dimension.flare.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class AiConfigScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The application supplies this dependency before instrumentation starts.
    private val settings by lazy { requireNotNull(GlobalContext.get().getOrNull<SettingsRepository>()) }
    private lateinit var originalConfig: AppSettings.AiConfig

    @Before
    fun saveConfig() {
        originalConfig = currentConfig()
    }

    @After
    fun restoreConfig() {
        setConfig(originalConfig)
    }

    @Test
    fun onDeviceBaseShowsOneBasicSectionAndSeparateAgentControls() {
        setConfig(AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OnDevice, tldr = true))
        showScreen()

        composeRule.onAllNodesWithText(label(R.string.settings_ai_config_basic_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(label(R.string.settings_ai_config_enable_tldr)).assertCountEquals(1)
        composeRule.onNodeWithText(label(R.string.settings_ai_config_agent_title)).assertExists()
        composeRule.onAllNodesWithText(label(R.string.settings_ai_config_enable_agent)).assertCountEquals(1)
        composeRule.onNodeWithText(label(R.string.settings_ai_config_use_shared)).performScrollTo().performClick()

        composeRule.waitUntil(5_000) { currentConfig().agentType != null }
        assertEquals(AppSettings.AiConfig.Type.OnDevice, currentConfig().type)
        assertEquals(AppSettings.AiConfig.Type.OpenAI("", "", ""), currentConfig().agentType)
    }

    @Test
    fun editingEachModelKeepsTheOtherConfigurationUnchanged() {
        val basic = AppSettings.AiConfig.Type.OpenAI("", "", "light-model", "low", "{\"seed\":1}")
        val agent = AppSettings.AiConfig.Type.OpenAI("", "", "strong-model", "high", "{\"seed\":2}")
        setConfig(AppSettings.AiConfig(type = basic, agentType = agent))
        showScreen()

        editModel("strong-model", "updated-agent")
        composeRule.waitUntil(5_000) { currentConfig().agentType?.model == "updated-agent" }
        assertEquals(basic, currentConfig().type)
        assertEquals(agent.copy(model = "updated-agent"), currentConfig().agentType)

        editModel("light-model", "updated-basic")
        composeRule.waitUntil(5_000) { (currentConfig().type as? AppSettings.AiConfig.Type.OpenAI)?.model == "updated-basic" }
        assertEquals(basic.copy(model = "updated-basic"), currentConfig().type)
        assertEquals(agent.copy(model = "updated-agent"), currentConfig().agentType)
    }

    @Test
    fun agentConnectionUsesCurrentValueAndCancelKeepsBothConfigurationsUnchanged() {
        val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/v1/", "", "basic-model")
        val agent = AppSettings.AiConfig.Type.OpenAI("https://agent.example/v1/", "", "agent-model")
        setConfig(AppSettings.AiConfig(type = basic, agentType = agent))
        showScreen()

        composeRule.onNodeWithText(agent.serverUrl).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals(agent.serverUrl)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("https://changed.example/v1/")
        composeRule.onNodeWithText(label(android.R.string.cancel)).performClick()
        assertEquals(basic, currentConfig().type)
        assertEquals(agent, currentConfig().agentType)

        composeRule.onNodeWithText(agent.serverUrl).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals(agent.serverUrl)
        composeRule.onNode(hasSetTextAction()).performTextReplacement("https://changed.example/v1/")
        composeRule.onNodeWithText(label(android.R.string.ok)).performClick()
        composeRule.waitUntil(5_000) { currentConfig().agentType?.serverUrl == "https://changed.example/v1/" }
        assertEquals(basic, currentConfig().type)
        assertEquals(agent.copy(serverUrl = "https://changed.example/v1/"), currentConfig().agentType)
    }

    private fun showScreen() {
        composeRule.setContent {
            MaterialTheme {
                AiConfigScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun editModel(
        current: String,
        updated: String,
    ) {
        composeRule.onNodeWithText(current).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 }
        composeRule.onNode(hasSetTextAction()).performTextReplacement(updated)
        composeRule.onNodeWithText(label(android.R.string.ok)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun currentConfig() = runBlocking { settings.appSettings.first().aiConfig }

    private fun setConfig(config: AppSettings.AiConfig) {
        runBlocking { settings.updateAppSettings { copy(aiConfig = config) } }
    }
}
