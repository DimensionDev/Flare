package dev.dimension.flare.feature.agent.common

import kotlinx.serialization.Serializable

@Serializable
public data class AgentTrace(
    public val phase: AgentPhase,
    public val detail: String? = null,
    public val toolKey: AgentToolKey? = null,
)

@Serializable
public enum class AgentPhase {
    LoadingPostContext,
    PostContextLoaded,
    PreparingImages,
    ImagesUnsupportedFallback,
    AgentStarted,
    StrategyStarted,
    StrategyCompleted,
    SubgraphStarted,
    SubgraphCompleted,
    SubgraphFailed,
    AskingModel,
    ModelResponseReceived,
    StreamingStarted,
    StreamingResponse,
    StreamingCompleted,
    StreamingFailed,
    RunningStep,
    StepCompleted,
    StepFailed,
    ToolCallStarted,
    ToolCallCompleted,
    ToolValidationFailed,
    ToolCallFailed,
    AgentCompleted,
    AgentFailed,
    AgentClosing,
}

@Serializable
public enum class AgentToolKey {
    LoadStatusContextStarted,
    LoadStatusContextCompleted,
    LoadStatusContextValidationFailed,
    LoadStatusContextFailed,
    SearchPostsStarted,
    SearchPostsCompleted,
    SearchPostsValidationFailed,
    SearchPostsFailed,
    SearchUsersStarted,
    SearchUsersCompleted,
    SearchUsersValidationFailed,
    SearchUsersFailed,
}
