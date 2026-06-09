package dev.dimension.flare.feature.agent.common

internal data class AgentVisibleResult(
    val text: String,
    val inputRequest: AgentInputRequest?,
) {
    fun hasVisibleContent(attachments: List<AgentConversationAttachment>): Boolean =
        text.isNotBlank() || inputRequest != null || attachments.isNotEmpty()
}

internal fun resolveAgentVisibleResult(
    text: String,
    inputRequest: AgentInputRequest?,
): AgentVisibleResult {
    val visibleText = text.cleanAgentVisibleText()
    return AgentVisibleResult(
        text = visibleText,
        inputRequest = inputRequest,
    )
}
