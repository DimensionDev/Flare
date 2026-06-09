package dev.dimension.flare.feature.agent.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AgentVisibleResultTest {
    @Test
    fun confirmationProseDoesNotCreateInputRequest() {
        val text = "检测到 mastodon.social 可以添加为 Mastodon 趋势订阅源。我来帮你先保存这个订阅源，请你确认后续操作。"

        val result = resolveAgentVisibleResult(text, inputRequest = null)

        assertEquals(text, result.text)
        assertEquals(null, result.inputRequest)
        assertTrue(result.hasVisibleContent(emptyList()))
    }

    @Test
    fun blankTextWithInputRequestKeepsInputRequestVisible() {
        val request =
            AgentInputRequest(
                requestId = "subscription-save:MASTODON_TRENDS:mastodon.social",
                localizedPrompt = AgentUiStrings.text(AgentLocalizedTextKey.SubscriptionSaveConfirmationMessage, "MASTODON_TRENDS", "Mastodon trends", "mastodon.social", "mastodon.social", "", ""),
                options =
                    listOf(
                        AgentInputRequest.Option(
                            id = "confirm",
                            localizedLabel = AgentUiStrings.text(AgentLocalizedTextKey.ConfirmSaveSubscription),
                            value = "确认保存",
                        ),
                    ),
            )

        val result = resolveAgentVisibleResult("", inputRequest = request)

        assertEquals("", result.text)
        assertEquals(request, result.inputRequest)
        assertTrue(result.hasVisibleContent(emptyList()))
    }

    @Test
    fun blankTextWithoutInputRequestHasNoVisibleContent() {
        val result = resolveAgentVisibleResult("""{"tool_call_id":"1","tool_name":"noop"}""", inputRequest = null)

        assertEquals("", result.text)
        assertEquals(null, result.inputRequest)
        assertFalse(result.hasVisibleContent(emptyList()))
    }
}
