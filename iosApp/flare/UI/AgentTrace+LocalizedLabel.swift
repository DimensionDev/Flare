import Foundation
import KotlinSharedUI

extension AgentTrace {
    var localizedLabel: String {
        if let toolKey {
            return toolKey.localizedLabel
        }

        switch phase {
        case .loadingPostContext:
            return String(localized: "status_insight_trace_loading_post_context")
        case .postContextLoaded:
            return String(localized: "status_insight_trace_post_context_loaded")
        case .preparingImages:
            return String(localized: "status_insight_trace_preparing_images")
        case .imagesUnsupportedFallback:
            return String(localized: "status_insight_trace_images_unsupported_fallback")
        case .agentStarted:
            return String(localized: "status_insight_trace_agent_started")
        case .strategyStarted:
            return String(localized: "status_insight_trace_strategy_started")
        case .strategyCompleted:
            return String(localized: "status_insight_trace_strategy_completed")
        case .subgraphStarted:
            return String(localized: "status_insight_trace_subgraph_started")
        case .subgraphCompleted:
            return String(localized: "status_insight_trace_subgraph_completed")
        case .subgraphFailed:
            return String(localized: "status_insight_trace_subgraph_failed")
        case .askingModel:
            return String(format: String(localized: "status_insight_trace_asking_model"), detail ?? "")
        case .modelResponseReceived:
            return String(localized: "status_insight_trace_model_response_received")
        case .streamingStarted:
            return String(format: String(localized: "status_insight_trace_streaming_started"), detail ?? "")
        case .streamingResponse:
            return String(localized: "status_insight_trace_streaming_response")
        case .streamingCompleted:
            return String(localized: "status_insight_trace_streaming_completed")
        case .streamingFailed:
            return String(localized: "status_insight_trace_streaming_failed")
        case .runningStep:
            return String(localized: "status_insight_trace_running_step")
        case .stepCompleted:
            return String(localized: "status_insight_trace_step_completed")
        case .stepFailed:
            return String(localized: "status_insight_trace_step_failed")
        case .toolCallStarted:
            return detail ?? String(localized: "status_insight_trace_running_step")
        case .toolCallCompleted:
            return detail ?? String(localized: "status_insight_trace_step_completed")
        case .toolValidationFailed:
            return detail ?? String(localized: "status_insight_trace_tool_validation_failed")
        case .toolCallFailed:
            return detail ?? String(localized: "status_insight_trace_tool_call_failed")
        case .agentCompleted:
            return String(localized: "status_insight_trace_agent_completed")
        case .agentFailed:
            return String(localized: "status_insight_trace_agent_failed")
        case .agentClosing:
            return String(localized: "status_insight_trace_agent_closing")
        }
    }
}

private extension AgentToolKey {
    var localizedLabel: String {
        switch self {
        case .loadStatusContextStarted:
            return String(localized: "status_insight_trace_tool_load_status_context_started")
        case .loadStatusContextCompleted:
            return String(localized: "status_insight_trace_tool_load_status_context_completed")
        case .loadStatusContextValidationFailed:
            return String(localized: "status_insight_trace_tool_load_status_context_validation_failed")
        case .loadStatusContextFailed:
            return String(localized: "status_insight_trace_tool_load_status_context_failed")
        case .searchPostsStarted, .searchUsersStarted:
            return String(localized: "status_insight_trace_tool_search_status_started")
        case .searchPostsCompleted, .searchUsersCompleted:
            return String(localized: "status_insight_trace_tool_search_status_completed")
        case .searchPostsValidationFailed, .searchUsersValidationFailed:
            return String(localized: "status_insight_trace_tool_search_status_validation_failed")
        case .searchPostsFailed, .searchUsersFailed:
            return String(localized: "status_insight_trace_tool_search_status_failed")
        }
    }
}
