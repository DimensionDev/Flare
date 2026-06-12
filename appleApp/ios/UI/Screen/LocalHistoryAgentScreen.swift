import SwiftUI
import KotlinSharedUI
import FlareAppleCore

struct LocalHistoryAgentScreen: View {
    @StateObject private var presenter: KotlinPresenter<LocalHistoryAgentPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: presenter.state.room.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { post in
                onNavigate(.statusDetail(post.accountType, post.statusKey))
            },
            onUserClick: { user in
                if let route = agentRoute(for: user) {
                    onNavigate(route)
                }
            }
        )
        .navigationTitle(String(localized: "local_history_title"))
        .navigationBarTitleDisplayMode(.inline)
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

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}
