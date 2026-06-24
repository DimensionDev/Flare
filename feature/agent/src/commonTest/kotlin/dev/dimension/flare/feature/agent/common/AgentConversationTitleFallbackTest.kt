package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.feature.agent.database.model.DbAgentConversation
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AgentConversationTitleFallbackTest {
    @Test
    fun statusInsightSourcePromptUsesPostContent() {
        val title =
            """
            Analyze this social post for the user.
            Return Markdown supported by compose-richtext.

            Post:
            platform: XQT
            statusKey: 2069812565253963993@x.com
            authorName: tanpo
            content: 男子校の「姫」
            cardTitle:
            """.trimIndent()
                .agentInsightSourceFallbackTitle(
                    "status-insight:specific_781622748@x.com:2069812565253963993@x.com",
                )

        assertEquals("男子校の「姫」", title)
    }

    @Test
    fun profileInsightSourcePromptUsesDisplayName() {
        val title =
            """
            Analyze this social profile for the user.

            Profile:
            platform: XQT
            userKey: 781622748@x.com
            displayName: tanpo
            handle: @H_npocya@x.com
            """.trimIndent()
                .agentInsightSourceFallbackTitle(
                    "profile-insight:specific_781622748@x.com:781622748@x.com",
                )

        assertEquals("tanpo", title)
    }

    @Test
    fun technicalConversationIdCanBeReplacedByFallbackTitle() {
        val conversationId = "status-insight:specific_781622748@x.com:2069812565253963993@x.com"
        val conversation =
            DbAgentConversation(
                conversationId = conversationId,
                title = conversationId,
                titleGenerated = false,
                createdAt = 1,
                updatedAt = 1,
                errorMessage = null,
            )

        assertEquals("男子校の「姫」", conversation.agentTitleOrFallback(conversationId, "男子校の「姫」"))
    }

    @Test
    fun generatedTitleIsNotReplacedByFallbackTitle() {
        val conversation =
            DbAgentConversation(
                conversationId = "status-insight:specific_781622748@x.com:2069812565253963993@x.com",
                title = "伊法年齡引熱議",
                titleGenerated = true,
                createdAt = 1,
                updatedAt = 1,
                errorMessage = null,
            )

        assertEquals("伊法年齡引熱議", conversation.agentTitleOrFallback(conversation.conversationId, "男子校の「姫」"))
    }

    @Test
    fun normalFallbackTitleIsNotReplacedByAnotherFallbackTitle() {
        val conversation =
            DbAgentConversation(
                conversationId = "status-insight:specific_781622748@x.com:2069812565253963993@x.com",
                title = "男子校の「姫」",
                titleGenerated = false,
                createdAt = 1,
                updatedAt = 1,
                errorMessage = null,
            )

        assertEquals("男子校の「姫」", conversation.agentTitleOrFallback(conversation.conversationId, "tanpo"))
    }
}
