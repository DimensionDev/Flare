package dev.dimension.flare.feature.agent.runtime

public sealed interface AgentAvailability {
    public data object Available : AgentAvailability

    public data class Unavailable(
        val reason: Reason,
    ) : AgentAvailability

    public enum class Reason {
        OnDeviceAiUnsupported,
        MissingOpenAIEndpoint,
        MissingOpenAIApiKey,
        MissingOpenAIModel,
    }
}
