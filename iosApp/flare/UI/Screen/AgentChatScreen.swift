import SwiftUI
import KotlinSharedUI

struct AgentChatScreen: View {
    @StateObject private var presenter: KotlinPresenter<GenericChatPresenterState>
    let onNavigate: (Route) -> Void

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            input: presenter.state.input,
            isRunning: presenter.state.isRunning,
            canSend: presenter.state.canSend,
            error: presenter.state.error,
            runningTrace: presenter.state.currentTrace?.localizedLabel ?? String(localized: "agent_chat_thinking"),
            inputRequest: presenter.state.inputRequest,
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            messageText: { $0.text },
            messageLocalizedText: { $0.localizedText },
            messageParts: { Array($0.parts) },
            messageInputRequest: { $0.inputRequest },
            messageInputRequestSelected: { $0.inputRequestSelected },
            messageInputRequestSelectedOptionId: { $0.inputRequestSelectedOptionId },
            isUserMessage: { message in
                String(describing: type(of: message)).contains("User")
            },
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
            },
            leadingContent: {
                AnyView(
                    ForEach(Array(presenter.state.statusInsightPosts.enumerated()), id: \.offset) { _, post in
                        StatusInsightPostPreview(
                            post: post,
                            onClick: {
                                onNavigate(.statusDetail(post.accountType, post.statusKey))
                            }
                        )
                    }
                )
            }
        )
        .navigationTitle(presenter.state.title?.isEmpty == false ? presenter.state.title! : String(localized: "agent_chat_title"))
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
