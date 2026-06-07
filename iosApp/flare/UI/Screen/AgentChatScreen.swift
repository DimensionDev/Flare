import SwiftUI
import KotlinSharedUI

struct AgentChatScreen: View {
    @StateObject private var presenter: KotlinPresenter<GenericChatPresenterState>

    var body: some View {
        AgentChatView(
            messages: Array(presenter.state.messages),
            input: presenter.state.input,
            isRunning: presenter.state.isRunning,
            canSend: presenter.state.canSend,
            error: presenter.state.error,
            runningTrace: String(localized: "agent_chat_thinking"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            messageText: { $0.text },
            isUserMessage: { message in
                String(describing: type(of: message)).contains("User")
            },
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            leadingContent: {
                AnyView(
                    ForEach(Array(presenter.state.statusInsightPosts.enumerated()), id: \.offset) { _, post in
                        StatusInsightPostPreview(post: post)
                    }
                )
            }
        )
        .navigationTitle(presenter.state.title?.isEmpty == false ? presenter.state.title! : String(localized: "agent_chat_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

extension AgentChatScreen {
    init(conversationId: String, initialMessage: String?) {
        let normalizedInitialMessage = initialMessage?.trimmingCharacters(in: .whitespacesAndNewlines)
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
