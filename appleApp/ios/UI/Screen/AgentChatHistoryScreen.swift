import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import AppleFontAwesome

struct AgentChatHistoryScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AgentChatHistoryPresenter())

    var body: some View {
        Group {
            if presenter.state.rooms.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("agent_history_empty")
                    } icon: {
                        Image(fontAwesome: .robot)
                    }
                }
            } else {
                List {
                    ForEach(presenter.state.rooms, id: \.id) { room in
                        NavigationLink(value: Route.agentChat(room.id, nil)) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(verbatim: room.title)
                                    .font(.headline)
                                    .lineLimit(2)

                                Text(Date(timeIntervalSince1970: TimeInterval(room.updatedAt) / 1000), style: .relative)
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
                    Image(fontAwesome: .plus)
                }
                .accessibilityLabel(Text("agent_chat_title"))
            }
        }
    }
}
