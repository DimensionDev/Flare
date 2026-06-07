import SwiftUI
import KotlinSharedUI

struct StatusInsightSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var presenter: KotlinPresenter<StatusInsightPresenterState>

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            input: presenter.state.input,
            isRunning: presenter.state.isRunning,
            canSend: presenter.state.canSend,
            error: presenter.state.error,
            runningTrace: presenter.state.currentTrace?.localizedLabel ?? String(localized: "status_insight_analyzing"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            messageText: { $0.text },
            isUserMessage: { message in
                String(describing: type(of: message)).contains("User")
            },
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            leadingContent: {
                AnyView(
                    Group {
                        if let post = presenter.state.post {
                            StatusInsightPostPreview(post: post)
                        }
                    }
                )
            }
        )
        .navigationTitle(String(localized: "status_insight_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(
                    role: .cancel
                ) {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
        }
    }
}

extension StatusInsightSheet {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey
    ) {
        self._presenter = .init(
            wrappedValue: .init(
                presenter: StatusInsightPresenter(
                    accountType: accountType,
                    statusKey: statusKey
                )
            )
        )
    }
}

struct StatusInsightPostPreview: View {
    @Environment(\.timelineAppearance) private var timelineAppearance
    let post: UiTimelineV2.Post

    var body: some View {
        StatusView(
            data: post,
            isQuote: true,
            showMedia: false,
            maxLine: 3,
            showExpandTextButton: false,
            forceHideActions: true,
            showTranslate: false,
            showParents: false
        )
        .padding(12)
        .environment(\.timelineAppearance, timelineAppearance.withStatusInsightPreviewDefaults())
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(.separator), lineWidth: 1)
        )
    }
}

struct StatusInsightCurrentTrace: View {
    let trace: String

    var body: some View {
        HStack(spacing: 8) {
            Image("fa-robot")
            Text(verbatim: trace)
                .font(.body)
                .shimmeringText()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

private struct ShimmeringTextModifier: ViewModifier {
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .foregroundStyle(.secondary)
            .overlay {
                GeometryReader { proxy in
                    LinearGradient(
                        colors: [
                            .secondary.opacity(0.35),
                            .primary,
                            .secondary.opacity(0.35),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: max(proxy.size.width * 0.65, 120))
                    .offset(x: phase * proxy.size.width)
                }
                .mask(content)
            }
            .onAppear {
                phase = -1
                withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                    phase = 1.4
                }
            }
    }
}

private extension View {
    func shimmeringText() -> some View {
        modifier(ShimmeringTextModifier())
    }
}

private extension AgentTrace {
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

private extension TimelineAppearance {
    func withStatusInsightPreviewDefaults() -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: false,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: true,
            expandMediaSize: false,
            videoAutoplay: .never,
            showLinkPreview: false,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: .hidden,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}
