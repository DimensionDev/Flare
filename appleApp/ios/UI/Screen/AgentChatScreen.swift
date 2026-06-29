import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct AgentChatScreen: View {
    @StateObject private var presenter: KotlinPresenter<GenericChatPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: presenter.state.messages,
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
        .navigationTitle(presenter.state.room.title.isEmpty ? String(localized: "agent_chat_title") : presenter.state.room.title)
        .navigationBarTitleDisplayMode(.inline)
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

private func agentRoute(for user: UiProfile) -> Route? {
    guard let event = user.clickEvent as? ClickEventDeeplink else {
        return nil
    }
    return Route.fromDeepLink(url: event.url)
}
