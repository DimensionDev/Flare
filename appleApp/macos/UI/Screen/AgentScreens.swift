import FlareAppleCore
import FlareAppleUI
import Foundation
@preconcurrency import KotlinSharedUI
import SwiftUI

struct AgentChatHistoryScreen: View {
    @ObservedObject private var windowCoordinator: MacAgentWindowCoordinator
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())
    @State private var selectedConversationId: String?
    @State private var activeDetailRoute: Route?
    @State private var activeDetailConversationId: String?
    let onNavigate: (Route) -> Void

    init(
        windowCoordinator: MacAgentWindowCoordinator = .shared,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.windowCoordinator = windowCoordinator
        self.onNavigate = onNavigate
    }

    var body: some View {
        NavigationSplitView {
            AgentChatHistoryList(
                rooms: rooms,
                selectedConversationId: $selectedConversationId
            ) { room in
                AgentChatHistoryRow(room: room)
                    .tag(room.id)
                    .contextMenu {
                        deleteButton(for: room)
                    }
            }
            .listStyle(.sidebar)
            .navigationTitle("agent_history_title")
            .navigationSplitViewColumnWidth(min: 220, ideal: 280, max: 360)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        createNewConversation()
                    } label: {
                        Image(fontAwesome: .plus)
                    }
                    .help(String(localized: "agent_chat_title", bundle: .main))
                    .accessibilityLabel(Text("agent_chat_title", bundle: .main))
                }
            }
        } detail: {
            if let activeDetailRoute,
               selectedConversationId == activeDetailConversationId {
                activeDetailRoute.view(
                    onNavigate: onNavigate,
                    goBack: {}
                )
                .id(activeDetailConversationId)
            } else if let selectedConversationId {
                AgentChatScreen(
                    conversationId: selectedConversationId,
                    initialMessage: nil,
                    onNavigate: onNavigate
                )
                .id(selectedConversationId)
            } else {
                AgentChatHistoryDetailPlaceholder {
                    createNewConversation()
                }
            }
        }
        .onAppear(perform: reconcileSelection)
        .onAppear {
            if let request = windowCoordinator.request {
                openAgentRoute(request.route)
            }
        }
        .onChange(of: roomIds) { _, _ in
            reconcileSelection()
        }
        .onChange(of: selectedConversationId) { _, newValue in
            if newValue != activeDetailConversationId {
                activeDetailRoute = nil
                activeDetailConversationId = nil
            }
        }
        .onChange(of: windowCoordinator.request?.id) { _, _ in
            if let request = windowCoordinator.request {
                openAgentRoute(request.route)
            }
        }
    }

    private var rooms: [AgentChatRoom] {
        Array(presenter.state.rooms)
    }

    private var roomIds: [String] {
        rooms.map(\.id)
    }

    private func createNewConversation() {
        openAgentRoute(.agentChat(Route.newGenericChatConversationId(), nil))
    }

    private func delete(_ room: AgentChatRoom) {
        if selectedConversationId == room.id {
            selectedConversationId = nil
            activeDetailRoute = nil
            activeDetailConversationId = nil
        }
        presenter.state.delete(conversationId: room.id)
    }

    private func deleteButton(for room: AgentChatRoom) -> some View {
        Button(role: .destructive) {
            delete(room)
        } label: {
            Label {
                Text("delete")
            } icon: {
                Image(fontAwesome: .trash)
            }
        }
    }

    private func reconcileSelection() {
        if let selectedConversationId,
           roomIds.contains(selectedConversationId) || selectedConversationId.isPendingAgentConversationId {
            return
        }

        selectedConversationId = roomIds.first
    }

    private func openAgentRoute(_ route: Route) {
        guard route.isAgentWindowRoute else {
            return
        }

        if route == .agentHistory {
            activeDetailRoute = nil
            activeDetailConversationId = nil
            reconcileSelection()
            return
        }

        guard let conversationId = route.agentConversationId else {
            return
        }

        activeDetailConversationId = conversationId
        activeDetailRoute = route
        selectedConversationId = conversationId
    }
}

struct AgentChatScreen: View {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var presenter: KotlinPresenter<GenericChatPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
//        ScrollView {
//            LazyVStack {
//                PagingView(data: presenter.state.messages) { item in
//                    Text(item.id)
//                } loadingContent: {
//                    ProgressView()
//                }
//            }
//        }
        AgentChatView(
            messages: presenter.state.messages,
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                openInMainWindow(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    openInMainWindow(route)
                }
            }
        )
        .navigationTitle(presenter.state.room.title.isEmpty ? String(localized: "agent_chat_title", bundle: .main) : presenter.state.room.title)
    }

    private func openInMainWindow(_ route: Route) {
        MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
    }
}

extension AgentChatScreen {
    init(
        conversationId: String,
        initialMessage: String?,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        let normalizedInitialMessage = initialMessage?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: GenericChatPresenter(
                    conversationId: conversationId,
                    initialMessage: normalizedInitialMessage?.isEmpty == false ? normalizedInitialMessage : nil
                )
            )
        )
    }
}

struct LocalHistoryAgentScreen: View {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var presenter: KotlinPresenter<LocalHistoryAgentPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: presenter.state.messages,
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                openInMainWindow(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    openInMainWindow(route)
                }
            }
        )
        .navigationTitle(String(localized: "local_history_title", bundle: .main))
    }

    private func openInMainWindow(_ route: Route) {
        MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
    }
}

extension LocalHistoryAgentScreen {
    init(
        conversationId: String,
        query: String?,
        target: String,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        let normalizedQuery = query?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: LocalHistoryAgentPresenter(
                    conversationId: conversationId,
                    query: normalizedQuery?.isEmpty == false ? normalizedQuery : nil,
                    target: LocalHistoryAgentTarget.companion.fromRouteValue(value: target)
                )
            )
        )
    }
}

struct StatusInsightScreen: View {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var presenter: KotlinPresenter<StatusInsightPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: presenter.state.messages,
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "status_insight_analyzing", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                openInMainWindow(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    openInMainWindow(route)
                }
            }
        )
        .navigationTitle(String(localized: "status_insight_title", bundle: .main))
    }

    private func openInMainWindow(_ route: Route) {
        MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
    }
}

extension StatusInsightScreen {
    init(
        accountType: AccountType,
        statusKey: MicroBlogKey,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.onNavigate = onNavigate
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

struct ProfileInsightScreen: View {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var presenter: KotlinPresenter<ProfileInsightPresenterState>
    let accountType: AccountType
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: presenter.state.messages,
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "profile_insight_analyzing", defaultValue: "Analyzing this profile...", bundle: .main),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder", bundle: .main),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                openInMainWindow(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    openInMainWindow(route)
                }
            }
        )
        .navigationTitle(String(localized: "profile_insight_title", defaultValue: "Profile insight", bundle: .main))
    }

    private func openInMainWindow(_ route: Route) {
        MacMainWindowCoordinator.shared.open(route: route, openWindow: openWindow)
    }
}

extension ProfileInsightScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey,
        onNavigate: @escaping (Route) -> Void = { _ in }
    ) {
        self.accountType = accountType
        self.onNavigate = onNavigate
        self._presenter = .init(
            wrappedValue: .init(
                presenter: ProfileInsightPresenter(
                    accountType: accountType,
                    userKey: userKey
                )
            )
        )
    }
}

private func localizedAgentPresentationString(
    _ key: String,
    fallback: String,
    arguments: [String] = []
) -> String {
    let value = Bundle.main.localizedString(forKey: key, value: fallback, table: nil)
    guard !arguments.isEmpty else { return value }
    return String(format: value, arguments: arguments.map { $0 as CVarArg })
}

extension AgentTrace {
    var localizedLabel: String {
        if let toolKey {
            return toolKey.localizedLabel
        }

        return switch phase {
        case .loadingPostContext:
            localizedAgentPresentationString("status_insight_trace_loading_post_context", fallback: "Loading post context")
        case .postContextLoaded:
            localizedAgentPresentationString("status_insight_trace_post_context_loaded", fallback: "Post context loaded")
        case .preparingImages:
            localizedAgentPresentationString("status_insight_trace_preparing_images", fallback: "Preparing images")
        case .imagesUnsupportedFallback:
            localizedAgentPresentationString("status_insight_trace_images_unsupported_fallback", fallback: "Images are not supported, using text fallback")
        case .agentStarted:
            localizedAgentPresentationString("status_insight_trace_agent_started", fallback: "Agent started")
        case .strategyStarted:
            localizedAgentPresentationString("status_insight_trace_strategy_started", fallback: "Strategy started")
        case .strategyCompleted:
            localizedAgentPresentationString("status_insight_trace_strategy_completed", fallback: "Strategy completed")
        case .subgraphStarted:
            localizedAgentPresentationString("status_insight_trace_subgraph_started", fallback: "Subgraph started")
        case .subgraphCompleted:
            localizedAgentPresentationString("status_insight_trace_subgraph_completed", fallback: "Subgraph completed")
        case .subgraphFailed:
            localizedAgentPresentationString("status_insight_trace_subgraph_failed", fallback: "Subgraph failed")
        case .askingModel:
            localizedAgentPresentationString("status_insight_trace_asking_model", fallback: "Asking model %@", arguments: [detail ?? ""])
        case .modelResponseReceived:
            localizedAgentPresentationString("status_insight_trace_model_response_received", fallback: "Model response received")
        case .streamingStarted:
            localizedAgentPresentationString("status_insight_trace_streaming_started", fallback: "Streaming started %@", arguments: [detail ?? ""])
        case .streamingResponse:
            localizedAgentPresentationString("status_insight_trace_streaming_response", fallback: "Streaming response")
        case .streamingCompleted:
            localizedAgentPresentationString("status_insight_trace_streaming_completed", fallback: "Streaming completed")
        case .streamingFailed:
            localizedAgentPresentationString("status_insight_trace_streaming_failed", fallback: "Streaming failed")
        case .runningStep:
            localizedAgentPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .stepCompleted:
            localizedAgentPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .stepFailed:
            localizedAgentPresentationString("status_insight_trace_step_failed", fallback: "Step failed")
        case .toolCallStarted:
            detail ?? localizedAgentPresentationString("status_insight_trace_running_step", fallback: "Running step")
        case .toolCallCompleted:
            detail ?? localizedAgentPresentationString("status_insight_trace_step_completed", fallback: "Step completed")
        case .toolValidationFailed:
            detail ?? localizedAgentPresentationString("status_insight_trace_tool_validation_failed", fallback: "Tool validation failed")
        case .toolCallFailed:
            detail ?? localizedAgentPresentationString("status_insight_trace_tool_call_failed", fallback: "Tool call failed")
        case .agentCompleted:
            localizedAgentPresentationString("status_insight_trace_agent_completed", fallback: "Agent completed")
        case .agentFailed:
            localizedAgentPresentationString("status_insight_trace_agent_failed", fallback: "Agent failed")
        case .agentClosing:
            localizedAgentPresentationString("status_insight_trace_agent_closing", fallback: "Agent closing")
        }
    }
}

private extension AgentToolKey {
    var localizedLabel: String {
        return switch self {
        case .loadStatusContextStarted:
            localizedAgentPresentationString("status_insight_trace_loading_post_context", fallback: "Loading status context")
        case .loadStatusContextCompleted:
            localizedAgentPresentationString("status_insight_trace_post_context_loaded", fallback: "Loaded status context")
        case .loadStatusContextValidationFailed:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_validation_failed", fallback: "Status context validation failed")
        case .loadStatusContextFailed:
            localizedAgentPresentationString("status_insight_trace_tool_load_status_context_failed", fallback: "Failed to load status context")
        case .searchPostsStarted, .searchUsersStarted:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_started", fallback: "Searching statuses")
        case .searchPostsCompleted, .searchUsersCompleted:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_completed", fallback: "Search completed")
        case .searchPostsValidationFailed, .searchUsersValidationFailed:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_validation_failed", fallback: "Search validation failed")
        case .searchPostsFailed, .searchUsersFailed:
            localizedAgentPresentationString("status_insight_trace_tool_search_status_failed", fallback: "Search failed")
        }
    }
}

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}
