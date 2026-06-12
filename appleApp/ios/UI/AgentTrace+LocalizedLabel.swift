import Foundation
import FlareAppleCore
import KotlinSharedUI

extension AgentTrace {
    var localizedLabel: String {
        if let toolKey {
            return toolKey.localizedLabel
        }

        return switch phase {
        case .loadingPostContext:
            localizedPresentationString("status_insight_trace_loading_post_context", fallback: "Loading post context")
        case .postContextLoaded:
            localizedPresentationString("status_insight_trace_post_context_loaded", fallback: "Post context loaded")
        case .preparingImages:
            localizedPresentationString("status_insight_trace_preparing_images", fallback: "Preparing images")
        case .imagesUnsupportedFallback:
            localizedPresentationString("status_insight_trace_images_unsupported_fallback", fallback: "Images are not supported, using text fallback")
        case .agentStarted:
            localizedPresentationString("status_insight_trace_agent_started", fallback: "Agent started")
        case .strategyStarted:
            localizedPresentationString("status_insight_trace_strategy_started", fallback: "Strategy started")
        case .strategyCompleted:
            localizedPresentationString("status_insight_trace_strategy_completed", fallback: "Strategy completed")
        case .subgraphStarted:
            localizedPresentationString("status_insight_trace_subgraph_started", fallback: "Subgraph started")
        case .subgraphCompleted:
            localizedPresentationString("status_insight_trace_subgraph_completed", fallback: "Subgraph completed")
        case .subgraphFailed:
            localizedPresentationString("status_insight_trace_subgraph_failed", fallback: "Subgraph failed")
        case .askingModel:
            localizedPresentationString("status_insight_trace_asking_model", fallback: "Asking model %@", arguments: [detail ?? ""])
        case .modelResponseReceived:
            localizedPresentationString("status_insight_trace_model_response_received", fallback: "Model response received")
        case .streamingStarted:
            localizedPresentationString("status_insight_trace_streaming_started", fallback: "Streaming started %@", arguments: [detail ?? ""])
        case .streamingResponse:
            localizedPresentationString("status_insight_trace_streaming_response", fallback: "Streaming response")
        case .streamingCompleted:
            localizedPresentationString("status_insight_trace_streaming_completed", fallback: "Streaming completed")
        case .streamingFailed:
            localizedPresentationString("status_insight_trace_streaming_failed", fallback: "Streaming failed")
        case .runningStep:
            localizedPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .stepCompleted:
            localizedPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .stepFailed:
            localizedPresentationString("status_insight_trace_step_failed", fallback: "Step failed")
        case .toolCallStarted:
            detail ?? localizedPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .toolCallCompleted:
            detail ?? localizedPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .toolValidationFailed:
            detail ?? localizedPresentationString("status_insight_trace_tool_validation_failed", fallback: "Tool validation failed")
        case .toolCallFailed:
            detail ?? localizedPresentationString("status_insight_trace_tool_call_failed", fallback: "Tool call failed")
        case .agentCompleted:
            localizedPresentationString("status_insight_trace_agent_completed", fallback: "Agent completed")
        case .agentFailed:
            localizedPresentationString("status_insight_trace_agent_failed", fallback: "Agent failed")
        case .agentClosing:
            localizedPresentationString("status_insight_trace_agent_closing", fallback: "Agent closing")
        }
    }
}

private extension AgentToolKey {
    var localizedLabel: String {
        return switch self {
        case .loadStatusContextStarted:
            localizedPresentationString("status_insight_trace_tool_load_status_context_started", fallback: "Loading status context")
        case .loadStatusContextCompleted:
            localizedPresentationString("status_insight_trace_tool_load_status_context_completed", fallback: "Loaded status context")
        case .loadStatusContextValidationFailed:
            localizedPresentationString("status_insight_trace_tool_load_status_context_validation_failed", fallback: "Status context validation failed")
        case .loadStatusContextFailed:
            localizedPresentationString("status_insight_trace_tool_load_status_context_failed", fallback: "Failed to load status context")
        case .searchPostsStarted, .searchUsersStarted:
            localizedPresentationString("status_insight_trace_tool_search_status_started", fallback: "Searching statuses")
        case .searchPostsCompleted, .searchUsersCompleted:
            localizedPresentationString("status_insight_trace_tool_search_status_completed", fallback: "Search completed")
        case .searchPostsValidationFailed, .searchUsersValidationFailed:
            localizedPresentationString("status_insight_trace_tool_search_status_validation_failed", fallback: "Search validation failed")
        case .searchPostsFailed, .searchUsersFailed:
            localizedPresentationString("status_insight_trace_tool_search_status_failed", fallback: "Search failed")
        }
    }
}
