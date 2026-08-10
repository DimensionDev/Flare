package dev.dimension.flare.feature.agent.presenter

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentMessagePlatformCompatibilityTest {
    @Test
    fun legacyPlatformFieldRoundTripsUnknownLegalId() {
        val message: AgentMessagePart = AgentMessagePart.PostCard(testPost("TestNet"))

        val legacyJson = message.encodeJson(AgentMessagePart.serializer())
        assertTrue(legacyJson.contains("\"platformType\":\"TestNet\""))
        assertFalse(legacyJson.contains("\"platformId\""))

        val decoded: AgentMessagePart = legacyJson.decodeJson()
        assertEquals("TestNet", assertIs<AgentMessagePart.PostCard>(decoded).post.platformId)
        assertEquals(legacyJson, decoded.encodeJson(AgentMessagePart.serializer()))
    }

    private fun testPost(platformId: String): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = platformId,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText("fixture".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey("status", "testnet.example"),
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
            accountType = AccountType.Specific(MicroBlogKey("viewer", "testnet.example")),
        )
}
