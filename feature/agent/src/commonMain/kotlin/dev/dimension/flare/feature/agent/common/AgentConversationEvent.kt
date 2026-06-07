package dev.dimension.flare.feature.agent.common

internal sealed interface AgentConversationEvent<out Content, out Trace> {
    data class ContentLoaded<Content>(
        val content: Content,
    ) : AgentConversationEvent<Content, Nothing>

    data class Trace<Trace>(
        val trace: Trace,
    ) : AgentConversationEvent<Nothing, Trace>

    data class Result(
        val text: String,
    ) : AgentConversationEvent<Nothing, Nothing>
}
