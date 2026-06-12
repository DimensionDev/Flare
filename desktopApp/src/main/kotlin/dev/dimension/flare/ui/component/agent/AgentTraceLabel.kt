package dev.dimension.flare.ui.component.agent

import androidx.compose.runtime.Composable
import dev.dimension.flare.Res
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentToolKey
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.status_insight_trace_agent_closing
import dev.dimension.flare.status_insight_trace_agent_completed
import dev.dimension.flare.status_insight_trace_agent_failed
import dev.dimension.flare.status_insight_trace_agent_started
import dev.dimension.flare.status_insight_trace_asking_model
import dev.dimension.flare.status_insight_trace_images_unsupported_fallback
import dev.dimension.flare.status_insight_trace_loading_post_context
import dev.dimension.flare.status_insight_trace_model_response_received
import dev.dimension.flare.status_insight_trace_post_context_loaded
import dev.dimension.flare.status_insight_trace_preparing_images
import dev.dimension.flare.status_insight_trace_running_step
import dev.dimension.flare.status_insight_trace_step_completed
import dev.dimension.flare.status_insight_trace_step_failed
import dev.dimension.flare.status_insight_trace_strategy_completed
import dev.dimension.flare.status_insight_trace_strategy_started
import dev.dimension.flare.status_insight_trace_streaming_completed
import dev.dimension.flare.status_insight_trace_streaming_failed
import dev.dimension.flare.status_insight_trace_streaming_response
import dev.dimension.flare.status_insight_trace_streaming_started
import dev.dimension.flare.status_insight_trace_subgraph_completed
import dev.dimension.flare.status_insight_trace_subgraph_failed
import dev.dimension.flare.status_insight_trace_subgraph_started
import dev.dimension.flare.status_insight_trace_tool_call_completed
import dev.dimension.flare.status_insight_trace_tool_call_failed
import dev.dimension.flare.status_insight_trace_tool_call_started
import dev.dimension.flare.status_insight_trace_tool_load_status_context_completed
import dev.dimension.flare.status_insight_trace_tool_load_status_context_failed
import dev.dimension.flare.status_insight_trace_tool_load_status_context_started
import dev.dimension.flare.status_insight_trace_tool_load_status_context_validation_failed
import dev.dimension.flare.status_insight_trace_tool_search_status_completed
import dev.dimension.flare.status_insight_trace_tool_search_status_failed
import dev.dimension.flare.status_insight_trace_tool_search_status_started
import dev.dimension.flare.status_insight_trace_tool_search_status_validation_failed
import dev.dimension.flare.status_insight_trace_tool_validation_failed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AgentTrace.label(): String =
    toolKey?.label()
        ?: when (phase) {
            AgentPhase.LoadingPostContext -> {
                stringResource(Res.string.status_insight_trace_loading_post_context)
            }

            AgentPhase.PostContextLoaded -> {
                stringResource(Res.string.status_insight_trace_post_context_loaded)
            }

            AgentPhase.PreparingImages -> {
                stringResource(Res.string.status_insight_trace_preparing_images)
            }

            AgentPhase.ImagesUnsupportedFallback -> {
                stringResource(Res.string.status_insight_trace_images_unsupported_fallback)
            }

            AgentPhase.AgentStarted -> {
                stringResource(Res.string.status_insight_trace_agent_started)
            }

            AgentPhase.StrategyStarted -> {
                stringResource(Res.string.status_insight_trace_strategy_started)
            }

            AgentPhase.StrategyCompleted -> {
                stringResource(Res.string.status_insight_trace_strategy_completed)
            }

            AgentPhase.SubgraphStarted -> {
                stringResource(Res.string.status_insight_trace_subgraph_started)
            }

            AgentPhase.SubgraphCompleted -> {
                stringResource(Res.string.status_insight_trace_subgraph_completed)
            }

            AgentPhase.SubgraphFailed -> {
                stringResource(Res.string.status_insight_trace_subgraph_failed)
            }

            AgentPhase.AskingModel -> {
                stringResource(
                    Res.string.status_insight_trace_asking_model,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ModelResponseReceived -> {
                stringResource(Res.string.status_insight_trace_model_response_received)
            }

            AgentPhase.StreamingStarted -> {
                stringResource(
                    Res.string.status_insight_trace_streaming_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.StreamingResponse -> {
                stringResource(Res.string.status_insight_trace_streaming_response)
            }

            AgentPhase.StreamingCompleted -> {
                stringResource(Res.string.status_insight_trace_streaming_completed)
            }

            AgentPhase.StreamingFailed -> {
                stringResource(Res.string.status_insight_trace_streaming_failed)
            }

            AgentPhase.RunningStep -> {
                stringResource(Res.string.status_insight_trace_running_step)
            }

            AgentPhase.StepCompleted -> {
                stringResource(Res.string.status_insight_trace_step_completed)
            }

            AgentPhase.StepFailed -> {
                stringResource(Res.string.status_insight_trace_step_failed)
            }

            AgentPhase.ToolCallStarted -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallCompleted -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_completed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolValidationFailed -> {
                stringResource(
                    Res.string.status_insight_trace_tool_validation_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallFailed -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.AgentCompleted -> {
                stringResource(Res.string.status_insight_trace_agent_completed)
            }

            AgentPhase.AgentFailed -> {
                stringResource(Res.string.status_insight_trace_agent_failed)
            }

            AgentPhase.AgentClosing -> {
                stringResource(Res.string.status_insight_trace_agent_closing)
            }
        }

@Composable
private fun AgentToolKey.label(): String =
    when (this) {
        AgentToolKey.LoadStatusContextStarted -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_started)
        }

        AgentToolKey.LoadStatusContextCompleted -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_completed)
        }

        AgentToolKey.LoadStatusContextValidationFailed -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_validation_failed)
        }

        AgentToolKey.LoadStatusContextFailed -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_failed)
        }

        AgentToolKey.SearchPostsStarted,
        AgentToolKey.SearchUsersStarted,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_started)
        }

        AgentToolKey.SearchPostsCompleted,
        AgentToolKey.SearchUsersCompleted,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_completed)
        }

        AgentToolKey.SearchPostsValidationFailed,
        AgentToolKey.SearchUsersValidationFailed,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_validation_failed)
        }

        AgentToolKey.SearchPostsFailed,
        AgentToolKey.SearchUsersFailed,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_failed)
        }
    }
