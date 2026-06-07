import SwiftUI
import KotlinSharedUI

struct AgentChatHistoryScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())

    var body: some View {
        Group {
            if presenter.state.conversations.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("agent_history_empty")
                    } icon: {
                        Image("fa-robot")
                    }
                }
            } else {
                List {
                    ForEach(presenter.state.conversations, id: \.id) { conversation in
                        NavigationLink(value: Route.agentChat(conversation.id, nil)) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(verbatim: conversation.title)
                                    .font(.headline)
                                    .lineLimit(2)

                                DateTimeText(data: conversation.updatedAt)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
        }
        .navigationTitle("agent_history_title")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: Route.agentChat(Route.newGenericChatConversationId(), nil)) {
                    Image("fa-plus")
                }
                .accessibilityLabel(Text("agent_chat_title"))
            }
        }
    }
}
