package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.Serializable

internal sealed interface AgentConversationEvent<out Content, out Trace> {
    data class ContentLoaded<Content>(
        val content: Content,
    ) : AgentConversationEvent<Content, Nothing>

    data class Trace<Trace>(
        val trace: Trace,
    ) : AgentConversationEvent<Nothing, Trace>

    data class Result(
        val text: String,
        val attachments: List<AgentConversationAttachment> = emptyList(),
        val inputRequest: AgentInputRequest? = null,
    ) : AgentConversationEvent<Nothing, Nothing>
}

@Serializable
public data class AgentInputRequest(
    val requestId: String,
    val prompt: String,
    val options: List<Option>,
    val allowFreeText: Boolean = true,
    val freeTextPlaceholder: String? = null,
    val postPreview: UiTimelineV2.Post? = null,
) {
    @Serializable
    public data class Option(
        val id: String,
        val label: String,
        val value: String,
        val submit: Boolean = true,
        val userPreview: UiProfile? = null,
        val postPreview: UiTimelineV2.Post? = null,
    )
}
