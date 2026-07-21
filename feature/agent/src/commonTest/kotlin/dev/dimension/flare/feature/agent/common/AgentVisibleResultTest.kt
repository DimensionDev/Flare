package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
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
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun metadataTextWithInputRequestBuildsVisibleInputRequest() {
        val request =
            AgentPendingInputRequest(
                requestId = "subscription-save:MASTODON_TRENDS:mastodon.social",
                options =
                    listOf(
                        AgentPendingInputRequest.Option(
                            id = "confirm",
                            value = "确认保存",
                        ),
                    ),
            )
        val text =
            """
            <!-- flare-agent-actions {"requestId":"subscription-save:MASTODON_TRENDS:mastodon.social","options":[{"id":"confirm","label":"保存","buttonType":"Primary"}]} -->
            """.trimIndent()

        val result = resolveAgentVisibleResult(text, inputRequest = request)

        assertEquals("", result.text)
        val inputRequest = result.inputRequest
        assertTrue(inputRequest != null)
        assertEquals("保存", inputRequest.options.single().label)
        assertEquals(AgentInputRequestOptionButtonType.Primary, inputRequest.options.single().buttonType)
    }

    @Test
    fun buttonInputRequestWithoutAiMetadataIsDropped() {
        val request =
            AgentPendingInputRequest(
                requestId = "subscription-save:MASTODON_TRENDS:mastodon.social",
                options =
                    listOf(
                        AgentPendingInputRequest.Option(
                            id = "confirm",
                            value = "confirmed=true",
                        ),
                    ),
            )

        val result = resolveAgentVisibleResult("", inputRequest = request)

        assertEquals("", result.text)
        assertEquals(null, result.inputRequest)
    }

    @Test
    fun previewInputRequestWithoutAiMetadataStillCreatesActions() {
        val post = createTestPost()
        val request =
            AgentPendingInputRequest(
                requestId = "post-selection:sample",
                options =
                    listOf(
                        AgentPendingInputRequest.Option(
                            id = "post:sample",
                            value = "event=post_selected",
                            postPreview = post,
                        ),
                    ),
            )

        val result = resolveAgentVisibleResult("我找到了多个可能的用户，请选择一个。", inputRequest = request)

        assertEquals("我找到了多个可能的用户，请选择一个。", result.text)
        val inputRequest = result.inputRequest
        assertTrue(inputRequest != null)
        val option = inputRequest.options.single()
        assertEquals("", option.label)
        assertEquals(AgentInputRequestOptionButtonType.Secondary, option.buttonType)
        assertEquals(post, option.postPreview)
    }

    @Test
    fun blankTextWithoutInputRequestHasNoVisibleContent() {
        val result = resolveAgentVisibleResult("""{"tool_call_id":"1","tool_name":"noop"}""", inputRequest = null)

        assertEquals("", result.text)
        assertEquals(null, result.inputRequest)
        assertFalse(result.text.isNotBlank() || result.inputRequest != null)
    }
}

private fun createTestPost(): UiTimelineV2.Post =
    UiTimelineV2.Post(
        platformType = PlatformType.Mastodon,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = null,
        content = UiTranslatableText("content".toUiPlainText()),
        actions = persistentListOf(),
        poll = null,
        statusKey = MicroBlogKey("sample", "example.social"),
        card = null,
        createdAt =
            kotlin.time.Clock.System
                .now()
                .toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(MicroBlogKey("viewer", "example.social")),
    )
