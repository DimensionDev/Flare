package dev.dimension.flare.ui.presenter.settings

import dev.dimension.flare.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAgentEnabledPresenterTest {
    @Test
    fun isAiAgentEnabled_requiresAgentAndOpenAIModel() {
        assertTrue(
            AppSettings
                .AiConfig(
                    agent = true,
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "",
                            apiKey = "",
                            model = "gpt-4.1",
                        ),
                ).isAiAgentEnabled(),
        )

        assertFalse(
            AppSettings
                .AiConfig(
                    agent = false,
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "",
                            apiKey = "",
                            model = "gpt-4.1",
                        ),
                ).isAiAgentEnabled(),
        )

        assertFalse(
            AppSettings
                .AiConfig(
                    agent = true,
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "",
                            apiKey = "",
                            model = " ",
                        ),
                ).isAiAgentEnabled(),
        )

        assertFalse(
            AppSettings
                .AiConfig(
                    agent = true,
                    type = AppSettings.AiConfig.Type.OnDevice,
                ).isAiAgentEnabled(),
        )
    }
}
