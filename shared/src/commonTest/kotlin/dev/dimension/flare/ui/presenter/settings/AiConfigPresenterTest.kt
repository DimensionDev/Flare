package dev.dimension.flare.ui.presenter.settings

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiConfigPresenterTest {
    private val basic = AppSettings.AiConfig.Type.OpenAI("https://basic.example/", "key", "light", "low", "{\"seed\":1}")

    @Test
    fun independentAgentStartsFromBasicAndEditsStayIsolated() {
        val shared = AppSettings.AiConfig(type = basic)
        val independent = shared.withAgentConfigInheritance(false)
        assertEquals(basic, independent.agentType)

        val edited = independent.updateOpenAIConfig(agentConfig = true) { copy(model = "strong", reasoningEffort = "high") }
        assertEquals(basic, edited.type)
        assertEquals(basic.copy(model = "strong", reasoningEffort = "high"), edited.agentType)
        val changedBasic = edited.updateOpenAIConfig(agentConfig = false) { copy(apiKey = "new-basic-key") }
        assertEquals(edited.agentType, changedBasic.agentType)
        assertEquals(basic.copy(apiKey = "new-basic-key"), changedBasic.type)
    }

    @Test
    fun returningToSharedSettingsFollowsLaterBasicEdits() {
        val independent = AppSettings.AiConfig(type = basic, agentType = basic.copy(model = "strong"))
        val shared = independent.withAgentConfigInheritance(true).updateOpenAIConfig(agentConfig = false) { copy(model = "new-light") }
        assertNull(shared.agentType)
        assertEquals(basic.copy(model = "new-light"), shared.effectiveAgentType)
    }

    @Test
    fun onDeviceBasicCanCreateAnIndependentRemoteAgent() {
        val independent = AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OnDevice).withAgentConfigInheritance(false)
        assertEquals(AppSettings.AiConfig.Type.OnDevice, independent.type)
        assertEquals(AppSettings.AiConfig.Type.OpenAI("", "", ""), independent.agentType)
        assertEquals(independent, independent.withAgentConfigInheritance(false))
    }

    @Test
    fun normalizeExcludedLanguages_trimsSplitsAndDeduplicatesByCanonicalLanguage() {
        assertEquals(
            listOf("en-US", "zh-CN", "ja"),
            normalizeExcludedLanguages(
                listOf(
                    " en-US ",
                    "en-GB",
                    "zh-CN, zh-Hans",
                    "\nja\n",
                ),
            ),
        )
    }
}
