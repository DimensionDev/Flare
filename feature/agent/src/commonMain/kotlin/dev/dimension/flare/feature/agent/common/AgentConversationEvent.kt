package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
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
        val parts: List<AgentMessagePart> = emptyList(),
        val inputRequest: AgentInputRequest? = null,
    ) : AgentConversationEvent<Nothing, Nothing>
}

@Serializable
public enum class AgentInputRequestOptionButtonType {
    Primary,
    Secondary,
    Destructive,
    Cancel,
}

@Serializable
public data class AgentInputRequest(
    val requestId: String,
    val options: List<Option>,
    val allowFreeText: Boolean = true,
    val postPreview: UiTimelineV2.Post? = null,
    val userPreview: UiProfile? = null,
) {
    @Serializable
    public data class Option(
        val id: String,
        val label: String,
        val buttonType: AgentInputRequestOptionButtonType,
        val value: String,
        val submit: Boolean = true,
        val userPreview: UiProfile? = null,
        val postPreview: UiTimelineV2.Post? = null,
    )
}

internal data class AgentPendingInputRequest(
    val requestId: String,
    val options: List<Option>,
    val allowFreeText: Boolean = true,
    val postPreview: UiTimelineV2.Post? = null,
    val userPreview: UiProfile? = null,
) {
    data class Option(
        val id: String,
        val value: String,
        val submit: Boolean = true,
        val userPreview: UiProfile? = null,
        val postPreview: UiTimelineV2.Post? = null,
    )
}
